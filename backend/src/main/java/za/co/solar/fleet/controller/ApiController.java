package za.co.solar.fleet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.repository.*;
import za.co.solar.fleet.service.DashboardService;
import java.time.Instant;
import java.util.*;
import za.co.solar.fleet.integration.ConnectorCapabilities;
import za.co.solar.fleet.integration.ConnectorRegistry;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final DashboardService dashboard;
    private final SiteRepository sites;
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final AlarmRepository alarms;
    private final ConnectorRegistry connectors;

    public ApiController(DashboardService dashboard, SiteRepository sites, DeviceRepository devices,
                         TelemetryRepository telemetry, AlarmRepository alarms, ConnectorRegistry connectors) {
        this.dashboard = dashboard; this.sites = sites; this.devices = devices; this.telemetry = telemetry; this.alarms = alarms; this.connectors = connectors;
    }

    @GetMapping("/dashboard/summary") public Map<String,Object> dashboard() { return dashboard.summary(); }

    @GetMapping("/integrations/catalog")
    public List<Map<String,Object>> integrationCatalog() {
        return Arrays.stream(Brand.values()).map(b -> {
            ConnectorCapabilities c = connectors.forBrand(b).capabilities();
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("brand", b.name()); row.put("telemetry", c.telemetry()); row.put("history", c.history());
            row.put("alarms", c.alarms()); row.put("remoteControl", c.remoteControl()); row.put("push", c.push());
            return row;
        }).toList();
    }
    @GetMapping("/sites") public List<SiteDto> sites() {
        if (sites.count() == 0) return List.of();
        UUID tenantId = sites.findAll().get(0).tenant.id;
        return sites.findByTenantIdOrderByName(tenantId).stream().map(SiteDto::from).toList();
    }
    @GetMapping("/devices") public List<DeviceDto> devices() { return devices.findAll().stream().map(DeviceDto::from).toList(); }
    @GetMapping("/devices/{id}/telemetry") public ResponseEntity<List<TelemetryDto>> telemetry(@PathVariable UUID id) {
        return devices.findById(id).map(d -> ResponseEntity.ok(telemetry.findTop100ByDeviceIdOrderByTimestampDesc(id).stream().map(TelemetryDto::from).toList()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/alarms") public List<AlarmDto> alarms() { return alarms.findTop100ByClearedAtIsNullOrderByOccurredAtDesc().stream().map(AlarmDto::from).toList(); }

    record SiteDto(UUID id, String name, String province, String municipality, int inverterCount) {
        static SiteDto from(Site s) { return new SiteDto(s.id, s.name, s.province, s.municipality, 0); }
    }
    record DeviceDto(UUID id, String site, String brand, String model, String serialNumber, String status, Instant lastSeenAt) {
        static DeviceDto from(Device d) { return new DeviceDto(d.id, d.site.name, d.brand.name(), d.model, d.serialNumber, d.status.name(), d.lastSeenAt); }
    }
    record TelemetryDto(Instant timestamp, Object pvPowerW, Object batterySoc, Object batteryPowerW, Object loadPowerW, String status) {
        static TelemetryDto from(TelemetryPoint t) { return new TelemetryDto(t.timestamp, t.pvPowerW, t.batterySoc, t.batteryPowerW, t.loadPowerW, t.status == null ? null : t.status.name()); }
    }
    record AlarmDto(UUID id, String brand, String serialNumber, String severity, String code, String message, Instant occurredAt) {
        static AlarmDto from(Alarm a) { return new AlarmDto(a.id, a.device.brand.name(), a.device.serialNumber, a.severity, a.code, a.message, a.occurredAt); }
    }
}
