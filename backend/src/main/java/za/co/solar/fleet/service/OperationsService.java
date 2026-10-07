package za.co.solar.fleet.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class OperationsService {
    private static final Set<String> COST_CATEGORIES = Set.of(
            "INVERTER", "PANELS", "BATTERY", "INSTALLATION", "LABOUR", "ENGINEERING",
            "ELECTRICAL", "TRANSPORT", "MAINTENANCE", "REPLACEMENT", "MONITORING", "OTHER");

    private final TenantRepository tenants;
    private final ProjectRepository projects;
    private final SiteRepository sites;
    private final DeviceRepository devices;
    private final IntegrationAccountRepository accounts;
    private final ProjectCostRepository costs;
    private final AuditEventRepository audit;
    private final AlarmRepository alarms;
    private final TelemetryRepository telemetry;
    private final boolean demoMode;
    private final UUID configuredTenantId;

    public OperationsService(TenantRepository tenants, ProjectRepository projects, SiteRepository sites,
                             DeviceRepository devices, IntegrationAccountRepository accounts,
                             ProjectCostRepository costs, AuditEventRepository audit,
                             AlarmRepository alarms, TelemetryRepository telemetry,
                             @org.springframework.beans.factory.annotation.Value("${platform.demo-data:true}") boolean demoMode,
                             @org.springframework.beans.factory.annotation.Value("${platform.tenant-id:}") String tenantId) {
        this.tenants = tenants;
        this.projects = projects;
        this.sites = sites;
        this.devices = devices;
        this.accounts = accounts;
        this.costs = costs;
        this.audit = audit;
        this.alarms = alarms;
        this.telemetry = telemetry;
        this.demoMode = demoMode;
        this.configuredTenantId = tenantId.isBlank() ? null : UUID.fromString(tenantId);
    }

    @Transactional
    public SiteDto createSite(UUID projectId, SiteRequest request) {
        Tenant tenant = currentTenant();
        Project project = projectForTenant(projectId, tenant.id);
        Site site = new Site();
        site.tenant = tenant;
        site.project = project;
        site.name = request.name().trim();
        site.address = trimToNull(request.address());
        site.province = trimToNull(request.province());
        site.municipality = trimToNull(request.municipality());
        site.ratedPvKw = request.ratedPvKw();
        site.ratedInverterKw = request.ratedInverterKw();
        sites.save(site);
        record(tenant, project, null, "SITE_CREATED", "Added site " + site.name);
        return new SiteDto(site.id, site.name, site.province, site.municipality, project.id, project.name);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Tenant tenant = currentTenant();
        List<Device> fleet = tenantDevices(tenant.id);
        Map<UUID, TelemetryPoint> latest = latestTelemetry(tenant.id);
        BigDecimal pv = BigDecimal.ZERO;
        BigDecimal energy = BigDecimal.ZERO;
        boolean hasPvData = false;
        boolean hasEnergyData = false;
        for (Device device : fleet) {
            TelemetryPoint point = latest.get(device.id);
            if (point != null) {
                if (isFresh(point.timestamp) && point.pvPowerW != null) {
                    pv = pv.add(point.pvPowerW);
                    hasPvData = true;
                }
                if (isFresh(point.timestamp) && point.pvEnergyTodayKwh != null) {
                    energy = energy.add(point.pvEnergyTodayKwh);
                    hasEnergyData = true;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sites", sites.findByTenantIdOrderByName(tenant.id).size());
        result.put("projects", projects.findByTenantIdOrderByName(tenant.id).size());
        result.put("inverters", fleet.stream().filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED).count());
        result.put("online", fleet.stream().filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED && healthLabel(d).equals("Healthy")).count());
        result.put("offline", fleet.stream().filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED && d.status == DeviceStatus.OFFLINE).count());
        result.put("faulted", fleet.stream().filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED && d.status == DeviceStatus.ALARM).count());
        result.put("retired", fleet.stream().filter(d -> d.lifecycleStatus == LifecycleStatus.RETIRED).count());
        result.put("alarms", alarms.countByDeviceTenantIdAndClearedAtIsNull(tenant.id));
        result.put("pvPowerW", hasPvData ? pv : null);
        result.put("energyTodayKwh", hasEnergyData ? energy : null);
        result.put("demoMode", demoMode);
        result.put("attention", attentionItems(tenant.id, fleet));
        result.put("recentActivity", audit.findTop50ByTenantIdOrderByOccurredAtDesc(tenant.id).stream().limit(8).map(this::auditDto).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listProjects() {
        Tenant tenant = currentTenant();
        List<Site> allSites = sites.findByTenantIdOrderByName(tenant.id);
        List<Device> allDevices = tenantDevices(tenant.id);
        Map<UUID, TelemetryPoint> latest = latestTelemetry(tenant.id);
        Map<UUID, BigDecimal> spend = totalsByProject(tenant.id);
        return projects.findByTenantIdOrderByName(tenant.id).stream()
                .map(p -> projectSummary(p, allSites, allDevices, latest, spend.getOrDefault(p.id, BigDecimal.ZERO))).toList();
    }

    @Transactional(readOnly = true)
    public ProjectSummary getProject(UUID id) {
        Tenant tenant = currentTenant();
        Project project = projectForTenant(id, tenant.id);
        return projectSummary(project, sites.findByTenantIdOrderByName(tenant.id),
                tenantDevices(tenant.id), latestTelemetry(tenant.id),
                totalsByProject(tenant.id).getOrDefault(project.id, BigDecimal.ZERO));
    }

    @Transactional
    public ProjectSummary createProject(ProjectRequest request) {
        Tenant tenant = currentTenant();
        String name = request.name().trim();
        if (projects.existsByTenantIdAndNameIgnoreCase(tenant.id, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project with this name already exists.");
        }
        Project project = new Project();
        project.tenant = tenant;
        applyProject(project, request);
        projects.save(project);
        record(tenant, project, null, "PROJECT_CREATED", "Project created: " + project.name);
        return getProject(project.id);
    }

    @Transactional
    public ProjectSummary updateProject(UUID id, ProjectRequest request) {
        Tenant tenant = currentTenant();
        Project project = projectForTenant(id, tenant.id);
        String name = request.name().trim();
        if (!project.name.equalsIgnoreCase(name) && projects.existsByTenantIdAndNameIgnoreCase(tenant.id, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project with this name already exists.");
        }
        String requestedCurrency = normalizeCurrency(request.currency());
        if (!project.currency.equals(requestedCurrency) && costs.existsByProjectId(project.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Project currency cannot be changed after costs have been recorded.");
        }
        applyProject(project, request);
        record(tenant, project, null, "PROJECT_UPDATED", "Project updated: " + project.name);
        return getProject(id);
    }

    @Transactional(readOnly = true)
    public List<SiteDto> listSites() {
        Tenant tenant = currentTenant();
        return sites.findByTenantIdOrderByName(tenant.id).stream().map(s -> new SiteDto(
                s.id, s.name, s.province, s.municipality, s.project == null ? null : s.project.id,
                s.project == null ? "Unassigned" : s.project.name)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceDto> listDevices(boolean includeRetired) {
        Tenant tenant = currentTenant();
        List<Device> fleet = tenantDevices(tenant.id);
        Map<UUID, TelemetryPoint> latest = latestTelemetry(tenant.id);
        return fleet.stream()
                .filter(d -> includeRetired || d.lifecycleStatus != LifecycleStatus.RETIRED)
                .map(d -> deviceDto(d, latest.get(d.id))).toList();
    }

    @Transactional(readOnly = true)
    public DeviceDto getDevice(UUID id) {
        Tenant tenant = currentTenant();
        Device device = deviceForTenant(id, tenant.id);
        return deviceDto(device, latestTelemetry(tenant.id).get(device.id));
    }

    @Transactional
    public DeviceDto setMaintenance(UUID id, boolean maintenance) {
        Tenant tenant = currentTenant();
        Device device = deviceForTenant(id, tenant.id);
        if (device.lifecycleStatus == LifecycleStatus.RETIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A retired inverter cannot be placed in maintenance.");
        }
        LifecycleStatus next = maintenance ? LifecycleStatus.MAINTENANCE : LifecycleStatus.ACTIVE;
        if (device.lifecycleStatus != next) {
            device.lifecycleStatus = next;
            record(tenant, device.site.project, device, "INVERTER_LIFECYCLE_CHANGED",
                    (maintenance ? "Placed inverter " : "Returned inverter ") + device.serialNumber +
                            (maintenance ? " in maintenance" : " to active service"));
        }
        return deviceDto(device, latestTelemetry(tenant.id).get(device.id));
    }

    @Transactional
    public DeviceDto createDevice(DeviceRequest request) {
        Tenant tenant = currentTenant();
        Site site = sites.findByIdAndTenantId(request.siteId(), tenant.id)
                .orElseThrow(() -> notFound("Site"));
        if (devices.existsByTenantIdAndBrandAndSerialNumber(tenant.id, request.brand(), request.serialNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An inverter with this manufacturer and serial number already exists.");
        }
        IntegrationAccount account = accounts.findFirstByTenantIdAndBrandAndEnabledTrue(tenant.id, request.brand())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No integration is configured for this manufacturer."));
        Device device = new Device();
        device.site = site;
        device.tenant = tenant;
        device.integrationAccount = account;
        device.brand = request.brand();
        device.model = request.model().trim();
        device.serialNumber = request.serialNumber().trim();
        device.externalDeviceId = trimToNull(request.externalDeviceId());
        device.ratedPowerKw = request.ratedPowerKw();
        device.firmwareVersion = trimToNull(request.firmwareVersion());
        device.installedAt = request.installedAt();
        device.warrantyUntil = request.warrantyUntil();
        device.purchaseCost = request.purchaseCost();
        device.notes = trimToNull(request.notes());
        device.status = DeviceStatus.UNKNOWN;
        device.lifecycleStatus = LifecycleStatus.ACTIVE;
        devices.save(device);
        syncPurchaseCost(device);
        record(tenant, site.project, device, "INVERTER_ADDED", "Added " + device.brand + " inverter " + device.serialNumber);
        return deviceDto(device, null);
    }

    @Transactional
    public DeviceDto updateDevice(UUID id, DeviceRequest request) {
        Tenant tenant = currentTenant();
        Device device = deviceForTenant(id, tenant.id);
        Site site = sites.findByIdAndTenantId(request.siteId(), tenant.id)
                .orElseThrow(() -> notFound("Site"));
        if (devices.existsByTenantIdAndBrandAndSerialNumber(tenant.id, request.brand(), request.serialNumber().trim())
                && !(device.brand == request.brand() && device.serialNumber.equals(request.serialNumber().trim()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An inverter with this manufacturer and serial number already exists.");
        }
        IntegrationAccount account = accounts.findFirstByTenantIdAndBrandAndEnabledTrue(tenant.id, request.brand())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No integration is configured for this manufacturer."));
        if (device.purchaseCost != null && request.purchaseCost() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The purchase cost is recorded in the project cost ledger and cannot be removed from the equipment form.");
        }
        device.site = site;
        device.tenant = tenant;
        device.integrationAccount = account;
        device.brand = request.brand();
        device.model = request.model().trim();
        device.serialNumber = request.serialNumber().trim();
        device.externalDeviceId = trimToNull(request.externalDeviceId());
        device.ratedPowerKw = request.ratedPowerKw();
        device.firmwareVersion = trimToNull(request.firmwareVersion());
        device.installedAt = request.installedAt();
        device.warrantyUntil = request.warrantyUntil();
        device.purchaseCost = request.purchaseCost();
        device.notes = trimToNull(request.notes());
        syncPurchaseCost(device);
        record(tenant, site.project, device, "INVERTER_UPDATED", "Updated inverter " + device.serialNumber);
        return deviceDto(device, latestTelemetry(tenant.id).get(device.id));
    }

    @Transactional
    public DeviceDto retireDevice(UUID id, RetireRequest request) {
        Tenant tenant = currentTenant();
        Device device = deviceForTenant(id, tenant.id);
        if (device.lifecycleStatus == LifecycleStatus.RETIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This inverter is already retired.");
        }
        device.lifecycleStatus = LifecycleStatus.RETIRED;
        device.retiredAt = request.retiredAt() == null ? Instant.now() : request.retiredAt().atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).toInstant();
        device.retirementReason = trimToNull(request.reason());
        device.retirementNotes = trimToNull(request.notes());
        String retirementDetails = device.retirementReason == null ? "" : ": " + device.retirementReason;
        if (device.retirementNotes != null) {
            retirementDetails += " - " + device.retirementNotes;
        }
        String summary = "Removed inverter " + device.serialNumber + " from the active fleet" + retirementDetails;
        record(tenant, device.site.project, device, "INVERTER_RETIRED", summary.substring(0, Math.min(500, summary.length())));
        return deviceDto(device, latestTelemetry(tenant.id).get(device.id));
    }

    @Transactional
    public DeviceDto restoreDevice(UUID id) {
        Tenant tenant = currentTenant();
        Device device = deviceForTenant(id, tenant.id);
        if (device.lifecycleStatus != LifecycleStatus.RETIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This inverter is already in the active fleet.");
        }
        device.lifecycleStatus = LifecycleStatus.ACTIVE;
        record(tenant, device.site.project, device, "INVERTER_RESTORED", "Restored inverter " + device.serialNumber + " to the fleet");
        return deviceDto(device, latestTelemetry(tenant.id).get(device.id));
    }

    @Transactional(readOnly = true)
    public List<CostDto> listCosts(UUID projectId) {
        Tenant tenant = currentTenant();
        projectForTenant(projectId, tenant.id);
        return costs.findByProjectIdOrderByCostDateDesc(projectId).stream().map(this::costDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CostDto> listAllCosts() {
        Tenant tenant = currentTenant();
        return costs.findByProjectTenantIdOrderByCostDateDesc(tenant.id).stream().map(this::costDto).toList();
    }

    @Transactional
    public CostDto addCost(UUID projectId, CostRequest request) {
        Tenant tenant = currentTenant();
        Project project = projectForTenant(projectId, tenant.id);
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        if (!COST_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported cost category.");
        }
        if ("DEVICE_PURCHASE".equalsIgnoreCase(trimToNull(request.reference()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That cost reference is reserved for equipment purchase records.");
        }
        Site site = request.siteId() == null ? null : sites.findByIdAndTenantId(request.siteId(), tenant.id)
                .orElseThrow(() -> notFound("Site"));
        if (site != null && (site.project == null || !site.project.id.equals(project.id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected site is not part of this project.");
        }
        Device device = request.deviceId() == null ? null : deviceForTenant(request.deviceId(), tenant.id);
        if (device != null && (device.site.project == null || !device.site.project.id.equals(project.id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected inverter is not part of this project.");
        }
        if (device != null && site != null && !device.site.id.equals(site.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected inverter does not belong to the selected site.");
        }
        ProjectCost cost = new ProjectCost();
        cost.project = project;
        cost.site = site;
        cost.device = device;
        cost.category = category;
        cost.description = request.description().trim();
        cost.quantity = request.quantity();
        cost.unitCost = request.unitCost();
        cost.total = MoneyMath.costTotal(request.quantity(), request.unitCost());
        if (cost.total.precision() - cost.total.scale() > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The calculated cost exceeds the supported amount.");
        }
        cost.currency = project.currency;
        cost.costDate = request.costDate() == null ? LocalDate.now() : request.costDate();
        cost.supplier = trimToNull(request.supplier());
        cost.reference = trimToNull(request.reference());
        cost.notes = trimToNull(request.notes());
        costs.save(cost);
        record(tenant, project, device, "COST_ADDED",
                "Added " + cost.category.toLowerCase(Locale.ROOT) + " cost " + cost.currency + " " + cost.total);
        return costDto(cost);
    }

    @Transactional(readOnly = true)
    public List<AlarmDto> listAlarms(boolean includeCleared) {
        Tenant tenant = currentTenant();
        List<Alarm> result = includeCleared
                ? alarms.findTop100ByDeviceTenantIdOrderByOccurredAtDesc(tenant.id)
                : alarms.findTop100ByDeviceTenantIdAndClearedAtIsNullOrderByOccurredAtDesc(tenant.id);
        return result
                .stream().map(a -> alarmDto(a)).toList();
    }

    @Transactional
    public AlarmDto acknowledgeAlarm(UUID id) {
        Tenant tenant = currentTenant();
        Alarm alarm = alarms.findByIdAndDeviceTenantId(id, tenant.id).orElseThrow(() -> notFound("Alarm"));
        if (alarm.clearedAt != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This alarm is already cleared.");
        if (alarm.acknowledgedAt == null) {
            alarm.acknowledgedAt = Instant.now();
            record(tenant, alarm.device.site.project, alarm.device, "ALARM_ACKNOWLEDGED", "Acknowledged alarm " + alarm.code);
        }
        return alarmDto(alarm);
    }

    @Transactional
    public AlarmDto clearAlarm(UUID id) {
        Tenant tenant = currentTenant();
        Alarm alarm = alarms.findByIdAndDeviceTenantId(id, tenant.id).orElseThrow(() -> notFound("Alarm"));
        if (alarm.clearedAt == null) {
            alarm.clearedAt = Instant.now();
            record(tenant, alarm.device.site.project, alarm.device, "ALARM_CLEARED", "Cleared alarm " + alarm.code);
        }
        return alarmDto(alarm);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> activity(UUID projectId) {
        Tenant tenant = currentTenant();
        if (projectId == null) return audit.findTop50ByTenantIdOrderByOccurredAtDesc(tenant.id).stream().map(this::auditDto).toList();
        projectForTenant(projectId, tenant.id);
        return audit.findTop50ByTenantIdAndProjectIdOrderByOccurredAtDesc(tenant.id, projectId).stream().map(this::auditDto).toList();
    }

    @Transactional(readOnly = true)
    public ProjectReport report(UUID projectId) {
        ProjectSummary project = getProject(projectId);
        List<Alarm> projectAlarms = alarms.findTop501ByDeviceSiteProjectIdOrderByOccurredAtDesc(projectId);
        boolean alarmsTruncated = projectAlarms.size() > 500;
        return new ProjectReport(project, listCosts(projectId),
                projectAlarms.stream().limit(500).map(this::alarmDto).toList(),
                activity(projectId), alarmsTruncated);
    }

    @Transactional
    public ProjectReport generateReport(UUID projectId) {
        Tenant tenant = currentTenant();
        Project project = projectForTenant(projectId, tenant.id);
        record(tenant, project, null, "REPORT_GENERATED", "Generated project report: " + project.name);
        return report(projectId);
    }

    @Transactional
    public ReportRunDto generateReport(String type, UUID projectId) {
        Tenant tenant = currentTenant();
        String normalizedType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!Set.of("FLEET", "ALARMS", "COSTS", "SAVINGS", "PROJECT").contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported report type.");
        }
        Project project = projectId == null ? null : projectForTenant(projectId, tenant.id);
        if (project == null && Set.of("COSTS", "SAVINGS", "PROJECT").contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a project for this report.");
        }
        String summary = "Generated " + normalizedType.toLowerCase(Locale.ROOT) + " report" +
                (project == null ? "" : ": " + project.name);
        record(tenant, project, null, "REPORT_GENERATED", summary);
        return new ReportRunDto(normalizedType, project == null ? null : project.id, Instant.now());
    }

    private ProjectSummary projectSummary(Project project, List<Site> allSites, List<Device> allDevices,
                                          Map<UUID, TelemetryPoint> latest, BigDecimal actualSpend) {
        List<Site> projectSites = allSites.stream().filter(s -> s.project != null && s.project.id.equals(project.id)).toList();
        Set<UUID> siteIds = new HashSet<>();
        projectSites.forEach(s -> siteIds.add(s.id));
        List<Device> projectDevices = allDevices.stream().filter(d -> siteIds.contains(d.site.id)).toList();
        List<Device> active = projectDevices.stream().filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED).toList();
        BigDecimal pv = BigDecimal.ZERO;
        BigDecimal energy = BigDecimal.ZERO;
        boolean hasPvData = false;
        boolean hasEnergyData = false;
        for (Device device : active) {
            TelemetryPoint point = latest.get(device.id);
            if (point != null) {
                if (isFresh(point.timestamp) && point.pvPowerW != null) {
                    pv = pv.add(point.pvPowerW);
                    hasPvData = true;
                }
                if (isFresh(point.timestamp) && point.pvEnergyTodayKwh != null) {
                    energy = energy.add(point.pvEnergyTodayKwh);
                    hasEnergyData = true;
                }
            }
        }
        BigDecimal dailySavings = project.importTariff == null || !hasEnergyData ? null :
                energy.multiply(project.importTariff).setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualSavings = dailySavings == null ? null :
                dailySavings.multiply(BigDecimal.valueOf(365)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roi = annualSavings == null || actualSpend.signum() == 0 ? null :
                annualSavings.divide(actualSpend, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal payback = annualSavings == null || annualSavings.signum() == 0 ? null :
                actualSpend.divide(annualSavings, 1, RoundingMode.HALF_UP);
        return new ProjectSummary(project.id, project.name, project.customer, project.location, project.status.name(),
                project.description, project.startDate, project.commissioningDate, project.budget, project.capacityKw,
                project.importTariff, project.exportTariff, project.currency, project.notes, projectSites.size(),
                active.size(), projectDevices.size() - active.size(),
                active.stream().filter(d -> healthLabel(d).equals("Healthy")).count(),
                active.stream().filter(d -> d.status == DeviceStatus.OFFLINE).count(),
                active.stream().filter(d -> d.status == DeviceStatus.ALARM).count(),
                hasPvData ? pv : null, hasEnergyData ? energy : null, actualSpend,
                project.budget == null ? null : project.budget.subtract(actualSpend).setScale(2, RoundingMode.HALF_UP),
                project.budget == null || project.budget.signum() == 0 ? null :
                        actualSpend.divide(project.budget, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                dailySavings, annualSavings, roi, payback,
                project.importTariff == null ? "Add an import tariff to estimate savings." :
                        !hasEnergyData ? "No current daily-energy readings are available." :
                                "Estimated from today's PV energy × import tariff; assumes all PV offsets grid imports. Export revenue is excluded.");
    }

    private List<Map<String, Object>> attentionItems(UUID tenantId, List<Device> fleet) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Alarm> openAlarms = alarms.findTop100ByDeviceTenantIdAndClearedAtIsNullOrderByOccurredAtDesc(tenantId);
        Set<UUID> criticalDevices = new HashSet<>();
        openAlarms.stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.severity))
                .forEach(a -> criticalDevices.add(a.device.id));
        for (Device device : fleet) {
            if (device.lifecycleStatus == LifecycleStatus.RETIRED) continue;
            if (device.lifecycleStatus == LifecycleStatus.MAINTENANCE) {
                items.add(attention(device, "Equipment in maintenance", "maintenance"));
            } else if (!criticalDevices.contains(device.id)) {
                if (device.status == DeviceStatus.ALARM) items.add(attention(device, "Fault reported", "fault"));
                else if (device.status == DeviceStatus.OFFLINE) items.add(attention(device, "Inverter is offline", "offline"));
                else if (device.lastSeenAt == null || device.lastSeenAt.isBefore(Instant.now().minusSeconds(15 * 60))) {
                    items.add(attention(device, device.lastSeenAt == null ? "No communication yet" : "Telemetry is stale", "stale"));
                }
            }
            if (device.integrationAccount.lastError != null) items.add(attention(device, "Integration sync error", "integration"));
            if (device.warrantyUntil != null && !device.warrantyUntil.isAfter(LocalDate.now().plusDays(60))
                    && !device.warrantyUntil.isBefore(LocalDate.now())) {
                items.add(attention(device, "Warranty expires soon", "warranty"));
            }
        }
        openAlarms.stream()
                .filter(a -> "CRITICAL".equalsIgnoreCase(a.severity) && a.acknowledgedAt == null)
                .forEach(a -> items.add(Map.of("deviceId", a.device.id, "serialNumber", a.device.serialNumber,
                        "projectId", a.device.site.project == null ? "" : a.device.site.project.id.toString(),
                        "project", a.device.site.project == null ? "Unassigned" : a.device.site.project.name,
                        "message", a.message, "type", "alarm", "severity", a.severity)));
        Map<UUID, BigDecimal> spend = totalsByProject(tenantId);
        for (Project project : projects.findByTenantIdOrderByName(tenantId)) {
            BigDecimal actual = spend.getOrDefault(project.id, BigDecimal.ZERO);
            if (project.budget != null && actual.compareTo(project.budget) > 0) {
                items.add(Map.of("projectId", project.id.toString(), "project", project.name,
                        "message", "Project budget exceeded by " + project.currency + " " + actual.subtract(project.budget).setScale(2, RoundingMode.HALF_UP),
                        "type", "budget"));
            }
        }
        Map<String, Integer> order = Map.of("alarm", 0, "fault", 1, "offline", 2, "integration", 3,
                "stale", 4, "maintenance", 5, "warranty", 6, "budget", 7);
        items.sort(Comparator.comparingInt(item -> order.getOrDefault(String.valueOf(item.get("type")), 8)));
        return items.stream().limit(30).toList();
    }

    private Map<String, Object> attention(Device d, String message, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("deviceId", d.id);
        item.put("projectId", d.site.project == null ? "" : d.site.project.id.toString());
        item.put("serialNumber", d.serialNumber);
        item.put("project", d.site.project == null ? "Unassigned" : d.site.project.name);
        item.put("message", message);
        item.put("type", type);
        item.put("lastSeenAt", d.lastSeenAt);
        return item;
    }

    private Map<UUID, TelemetryPoint> latestTelemetry(UUID tenantId) {
        Map<UUID, TelemetryPoint> latest = new HashMap<>();
        telemetry.findLatestForTenant(tenantId).forEach(t -> latest.put(t.device.id, t));
        return latest;
    }

    private List<Device> tenantDevices(UUID tenantId) {
        return devices.findFleetByTenantId(tenantId);
    }

    private Map<UUID, BigDecimal> totalsByProject(UUID tenantId) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        costs.totalsByProject(tenantId).forEach(row -> totals.put((UUID) row[0], (BigDecimal) row[1]));
        return totals;
    }

    private DeviceDto deviceDto(Device d, TelemetryPoint point) {
        IntegrationAccount account = d.integrationAccount;
        String integrationStatus = account == null ? "Not configured" :
                account.lastError != null ? "Sync error" :
                        account.mode == IntegrationMode.DEMO ? "Demo data" :
                                account.lastSyncAt == null ? "Unverified" : "Connected";
        return new DeviceDto(d.id, d.site.id, d.site.name,
                d.site.project == null ? null : d.site.project.id,
                d.site.project == null ? "Unassigned" : d.site.project.name, d.brand.name(),
                d.model, d.serialNumber, d.externalDeviceId, d.firmwareVersion,
                d.ratedPowerKw, healthLabel(d), d.lifecycleStatus.name(), d.lastSeenAt,
                d.installedAt, d.warrantyUntil, d.purchaseCost, d.retiredAt, d.retirementReason, d.retirementNotes,
                d.notes, account == null ? "NONE" : account.mode.name(), integrationStatus,
                account == null ? null : account.lastSyncAt, account == null ? null : account.lastError,
                point == null ? null : point.timestamp, point != null && isFresh(point.timestamp),
                point == null ? null : point.pvPowerW,
                point == null ? null : point.pvEnergyTodayKwh, point == null ? null : point.batterySoc,
                point == null ? null : point.batteryPowerW, point == null ? null : point.gridImportW,
                point == null ? null : point.gridExportW, point == null ? null : point.loadPowerW,
                point == null ? null : point.inverterTemperatureC, point == null ? null : point.inverterEfficiencyPct,
                point == null ? null : point.faultCode);
    }

    private String healthLabel(Device device) {
        if (device.lifecycleStatus == LifecycleStatus.MAINTENANCE) return "Maintenance";
        if (device.status == DeviceStatus.ONLINE && (device.lastSeenAt == null ||
                device.lastSeenAt.isBefore(Instant.now().minusSeconds(15 * 60)))) return "Warning";
        return switch (device.status) {
            case ONLINE -> "Healthy";
            case OFFLINE -> "Offline";
            case ALARM -> "Fault";
            case UNKNOWN -> "Unknown";
        };
    }

    private boolean isFresh(Instant timestamp) {
        return TelemetryFreshness.isFresh(timestamp, Instant.now());
    }

    private CostDto costDto(ProjectCost c) {
        return new CostDto(c.id, c.project.id, c.site == null ? null : c.site.id,
                c.site == null ? null : c.site.name, c.device == null ? null : c.device.id,
                c.device == null ? null : c.device.serialNumber, c.category, c.description,
                c.quantity, c.unitCost, c.total, c.currency, c.costDate, c.supplier, c.reference, c.notes);
    }

    private void syncPurchaseCost(Device device) {
        Optional<ProjectCost> existing = costs.findFirstByDeviceIdAndReference(device.id, "DEVICE_PURCHASE");
        if (device.purchaseCost == null) return;
        ProjectCost cost = existing.orElseGet(ProjectCost::new);
        if (cost.id == null) {
            cost.project = device.site.project;
            if (cost.project == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assign the site to a project before recording an inverter purchase cost.");
            }
            cost.site = device.site;
            cost.device = device;
            cost.category = "INVERTER";
            cost.description = "Inverter purchase - " + device.serialNumber;
            cost.quantity = BigDecimal.ONE;
            cost.reference = "DEVICE_PURCHASE";
            cost.currency = cost.project.currency;
            cost.costDate = device.installedAt == null ? LocalDate.now() : device.installedAt;
        }
        cost.unitCost = device.purchaseCost;
        cost.total = MoneyMath.costTotal(cost.quantity, cost.unitCost);
        costs.save(cost);
    }

    private AlarmDto alarmDto(Alarm a) {
        return new AlarmDto(a.id, a.device.id, a.device.brand.name(), a.device.serialNumber,
                a.device.site.project == null ? null : a.device.site.project.id,
                a.device.site.project == null ? "Unassigned" : a.device.site.project.name,
                a.device.site.id, a.device.site.name, a.severity, a.code, a.message, a.occurredAt,
                a.lastSeenAt, a.acknowledgedAt, a.clearedAt);
    }

    private AuditDto auditDto(AuditEvent event) {
        return new AuditDto(event.id, event.action, event.summary, event.occurredAt,
                event.project == null ? null : event.project.id, event.project == null ? null : event.project.name,
                event.device == null ? null : event.device.id, event.device == null ? null : event.device.serialNumber);
    }

    private void applyProject(Project p, ProjectRequest r) {
        p.name = r.name().trim();
        p.customer = trimToNull(r.customer());
        p.location = trimToNull(r.location());
        p.status = r.status() == null ? ProjectStatus.OPERATIONAL : r.status();
        p.description = trimToNull(r.description());
        p.startDate = r.startDate();
        p.commissioningDate = r.commissioningDate();
        p.budget = r.budget();
        p.capacityKw = r.capacityKw();
        p.importTariff = r.importTariff();
        p.exportTariff = r.exportTariff();
        p.currency = normalizeCurrency(r.currency());
        if (!p.currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a three-letter ISO code.");
        }
        p.notes = trimToNull(r.notes());
    }

    private void record(Tenant tenant, Project project, Device device, String action, String summary) {
        AuditEvent event = new AuditEvent();
        event.tenant = tenant;
        event.project = project;
        event.device = device;
        event.action = action;
        event.summary = summary;
        audit.save(event);
    }

    private Project projectForTenant(UUID id, UUID tenantId) {
        return projects.findByIdAndTenantId(id, tenantId).orElseThrow(() -> notFound("Project"));
    }

    private Device deviceForTenant(UUID id, UUID tenantId) {
        return devices.findByIdAndTenantId(id, tenantId).orElseThrow(() -> notFound("Inverter"));
    }

    private Tenant currentTenant() {
        if (configuredTenantId != null) {
            return tenants.findById(configuredTenantId).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Configured operator tenant was not found."));
        }
        if (!demoMode) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operator tenant is not configured.");
        return tenants.findFirstByOrderByCreatedAtAsc().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No operator account is configured."));
    }

    private ResponseStatusException notFound(String label) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found.");
    }

    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "ZAR" : currency.trim().toUpperCase(Locale.ROOT);
    }

    public record ProjectRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 200) String customer,
            @jakarta.validation.constraints.Size(max = 300) String location,
            ProjectStatus status,
            @jakarta.validation.constraints.Size(max = 2000) String description,
            LocalDate startDate, LocalDate commissioningDate,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 14, fraction = 2) BigDecimal budget,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 9, fraction = 3) BigDecimal capacityKw,
            @jakarta.validation.constraints.DecimalMin("0.0000") @jakarta.validation.constraints.Digits(integer = 8, fraction = 4) BigDecimal importTariff,
            @jakarta.validation.constraints.DecimalMin("0.0000") @jakarta.validation.constraints.Digits(integer = 8, fraction = 4) BigDecimal exportTariff,
            @jakarta.validation.constraints.Size(max = 3) String currency,
            @jakarta.validation.constraints.Size(max = 2000) String notes) {}

    public record SiteRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 500) String address,
            @jakarta.validation.constraints.Size(max = 100) String province,
            @jakarta.validation.constraints.Size(max = 200) String municipality,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 9, fraction = 3) BigDecimal ratedPvKw,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 9, fraction = 3) BigDecimal ratedInverterKw) {}

    public record DeviceRequest(
            @jakarta.validation.constraints.NotNull UUID siteId,
            @jakarta.validation.constraints.NotNull Brand brand,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String model,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String serialNumber,
            @jakarta.validation.constraints.Size(max = 200) String externalDeviceId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.001") @jakarta.validation.constraints.Digits(integer = 9, fraction = 3) BigDecimal ratedPowerKw,
            @jakarta.validation.constraints.Size(max = 200) String firmwareVersion,
            LocalDate installedAt, LocalDate warrantyUntil,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal purchaseCost,
            @jakarta.validation.constraints.Size(max = 2000) String notes) {}

    public record RetireRequest(@jakarta.validation.constraints.Size(max = 500) String reason, LocalDate retiredAt,
                                @jakarta.validation.constraints.Size(max = 2000) String notes) {}
    public record CostRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String category,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String description,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value = "0.001") @jakarta.validation.constraints.Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value = "0.00") @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal unitCost,
            UUID siteId, UUID deviceId, LocalDate costDate,
            @jakarta.validation.constraints.Size(max = 200) String supplier,
            @jakarta.validation.constraints.Size(max = 200) String reference,
            @jakarta.validation.constraints.Size(max = 2000) String notes) {}

    public record SiteDto(UUID id, String name, String province, String municipality, UUID projectId, String project) {}
    public record ProjectSummary(UUID id, String name, String customer, String location, String status, String description,
            LocalDate startDate, LocalDate commissioningDate, BigDecimal budget, BigDecimal capacityKw,
            BigDecimal importTariff, BigDecimal exportTariff, String currency, String notes,
            int siteCount, int activeInverters, int retiredInverters, long online, long offline, long faulted,
            BigDecimal pvPowerW, BigDecimal energyTodayKwh, BigDecimal actualSpend, BigDecimal remainingBudget,
            BigDecimal budgetUtilisationPercent, BigDecimal estimatedDailySavings, BigDecimal estimatedAnnualSavings,
            BigDecimal estimatedAnnualRoiPercent, BigDecimal estimatedPaybackYears, String savingsBasis) {}
    public record DeviceDto(UUID id, UUID siteId, String site, UUID projectId, String project, String brand,
            String model, String serialNumber, String externalDeviceId, String firmwareVersion, BigDecimal ratedPowerKw,
            String status, String lifecycleStatus, Instant lastSeenAt, LocalDate installedAt, LocalDate warrantyUntil,
            BigDecimal purchaseCost, Instant retiredAt, String retirementReason, String retirementNotes, String notes, String integrationMode,
            String integrationStatus, Instant lastSyncAt, String integrationError, Instant telemetryAt, boolean telemetryFresh,
            BigDecimal pvPowerW, BigDecimal pvEnergyTodayKwh, BigDecimal batterySoc, BigDecimal batteryPowerW,
            BigDecimal gridImportW, BigDecimal gridExportW, BigDecimal loadPowerW,
            BigDecimal inverterTemperatureC, BigDecimal inverterEfficiencyPct, String faultCode) {}
    public record CostDto(UUID id, UUID projectId, UUID siteId, String site, UUID deviceId, String device,
            String category, String description, BigDecimal quantity, BigDecimal unitCost, BigDecimal total,
            String currency, LocalDate costDate, String supplier, String reference, String notes) {}
    public record AlarmDto(UUID id, UUID deviceId, String brand, String serialNumber, UUID projectId, String project,
            UUID siteId, String site, String severity, String code, String message, Instant occurredAt,
            Instant lastSeenAt, Instant acknowledgedAt, Instant clearedAt) {}
    public record AuditDto(UUID id, String action, String summary, Instant occurredAt, UUID projectId, String project,
            UUID deviceId, String device) {}
    public record ProjectReport(ProjectSummary project, List<CostDto> costs, List<AlarmDto> alarms,
                                List<AuditDto> activity, boolean alarmsTruncated) {}
    public record ReportRunDto(String type, UUID projectId, Instant generatedAt) {}
}
