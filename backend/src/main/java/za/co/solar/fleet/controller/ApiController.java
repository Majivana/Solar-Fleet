package za.co.solar.fleet.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.integration.ConnectorCapabilities;
import za.co.solar.fleet.integration.ConnectorRegistry;
import za.co.solar.fleet.repository.TelemetryRepository;
import za.co.solar.fleet.service.OperationsService;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final OperationsService operations;
    private final ConnectorRegistry connectors;
    private final TelemetryRepository telemetry;

    public ApiController(OperationsService operations, ConnectorRegistry connectors, TelemetryRepository telemetry) {
        this.operations = operations;
        this.connectors = connectors;
        this.telemetry = telemetry;
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboard() { return operations.dashboard(); }

    @GetMapping("/integrations/catalog")
    public List<Map<String, Object>> integrationCatalog() {
        return Arrays.stream(Brand.values()).map(brand -> {
            ConnectorCapabilities capabilities = connectors.forBrand(brand).capabilities();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("brand", brand.name());
            row.put("telemetry", capabilities.telemetry());
            row.put("history", capabilities.history());
            row.put("alarms", capabilities.alarms());
            row.put("remoteControl", capabilities.remoteControl());
            row.put("push", capabilities.push());
            row.put("liveConfigured", false);
            return row;
        }).toList();
    }

    @GetMapping("/sites")
    public List<OperationsService.SiteDto> sites() { return operations.listSites(); }

    @GetMapping("/projects")
    public List<OperationsService.ProjectSummary> projects() { return operations.listProjects(); }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationsService.ProjectSummary createProject(@Valid @RequestBody OperationsService.ProjectRequest request) {
        return operations.createProject(request);
    }

    @GetMapping("/projects/{id}")
    public OperationsService.ProjectSummary project(@PathVariable UUID id) { return operations.getProject(id); }

    @PutMapping("/projects/{id}")
    public OperationsService.ProjectSummary updateProject(@PathVariable UUID id,
                                                           @Valid @RequestBody OperationsService.ProjectRequest request) {
        return operations.updateProject(id, request);
    }

    @PostMapping("/projects/{id}/sites")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationsService.SiteDto createSite(@PathVariable UUID id,
                                                 @Valid @RequestBody OperationsService.SiteRequest request) {
        return operations.createSite(id, request);
    }

    @GetMapping("/projects/{id}/costs")
    public List<OperationsService.CostDto> costs(@PathVariable UUID id) { return operations.listCosts(id); }

    @GetMapping("/costs")
    public List<OperationsService.CostDto> allCosts() { return operations.listAllCosts(); }

    @PostMapping("/projects/{id}/costs")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationsService.CostDto addCost(@PathVariable UUID id,
                                              @Valid @RequestBody OperationsService.CostRequest request) {
        return operations.addCost(id, request);
    }

    @GetMapping("/projects/{id}/report")
    public OperationsService.ProjectReport report(@PathVariable UUID id) { return operations.report(id); }

    @PostMapping("/projects/{id}/report/generate")
    public OperationsService.ProjectReport generateReport(@PathVariable UUID id) { return operations.generateReport(id); }

    @PostMapping("/reports/generate")
    public OperationsService.ReportRunDto generateReport(@RequestBody ReportRequest request) {
        return operations.generateReport(request.type(), request.projectId());
    }

    @GetMapping("/activity")
    public List<OperationsService.AuditDto> activity(@RequestParam(required = false) UUID projectId) {
        return operations.activity(projectId);
    }

    @GetMapping("/devices")
    public List<OperationsService.DeviceDto> devices(@RequestParam(defaultValue = "false") boolean includeRetired) {
        return operations.listDevices(includeRetired);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationsService.DeviceDto createDevice(@Valid @RequestBody OperationsService.DeviceRequest request) {
        return operations.createDevice(request);
    }

    @GetMapping("/devices/{id}")
    public OperationsService.DeviceDto device(@PathVariable UUID id) { return operations.getDevice(id); }

    @PutMapping("/devices/{id}")
    public OperationsService.DeviceDto updateDevice(@PathVariable UUID id,
                                                     @Valid @RequestBody OperationsService.DeviceRequest request) {
        return operations.updateDevice(id, request);
    }

    @PostMapping("/devices/{id}/retire")
    public OperationsService.DeviceDto retireDevice(@PathVariable UUID id,
                                                     @Valid @RequestBody OperationsService.RetireRequest request) {
        return operations.retireDevice(id, request);
    }

    @PostMapping("/devices/{id}/restore")
    public OperationsService.DeviceDto restoreDevice(@PathVariable UUID id) { return operations.restoreDevice(id); }

    @PostMapping("/devices/{id}/maintenance")
    public OperationsService.DeviceDto setMaintenance(@PathVariable UUID id, @RequestBody MaintenanceRequest request) {
        return operations.setMaintenance(id, request.maintenance());
    }

    @GetMapping("/devices/{id}/telemetry")
    public ResponseEntity<List<TelemetryDto>> telemetry(@PathVariable UUID id) {
        operations.getDevice(id);
        List<TelemetryDto> points = telemetry.findTop100ByDeviceIdOrderByTimestampDesc(id)
                .stream().map(TelemetryDto::from).toList();
        return ResponseEntity.ok(points);
    }

    @GetMapping("/alarms")
    public List<OperationsService.AlarmDto> alarms(@RequestParam(defaultValue = "true") boolean includeCleared) {
        return operations.listAlarms(includeCleared);
    }

    @PostMapping("/alarms/{id}/acknowledge")
    public OperationsService.AlarmDto acknowledgeAlarm(@PathVariable UUID id) { return operations.acknowledgeAlarm(id); }

    @PostMapping("/alarms/{id}/clear")
    public OperationsService.AlarmDto clearAlarm(@PathVariable UUID id) { return operations.clearAlarm(id); }

    record TelemetryDto(Instant timestamp, Object pvPowerW, Object pvEnergyTodayKwh, Object batterySoc,
                        Object batteryPowerW, Object gridImportW, Object gridExportW, Object loadPowerW,
                        Object inverterTemperatureC, Object inverterEfficiencyPct, String faultCode, String status) {
        static TelemetryDto from(TelemetryPoint t) {
            return new TelemetryDto(t.timestamp, t.pvPowerW, t.pvEnergyTodayKwh, t.batterySoc, t.batteryPowerW,
                    t.gridImportW, t.gridExportW, t.loadPowerW, t.inverterTemperatureC,
                    t.inverterEfficiencyPct, t.faultCode, t.status == null ? null : t.status.name());
        }
    }

    record ReportRequest(String type, UUID projectId) {}
    record MaintenanceRequest(boolean maintenance) {}
}
