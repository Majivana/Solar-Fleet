package za.co.solar.fleet.service;

import org.springframework.stereotype.Service;
import za.co.solar.fleet.domain.DeviceStatus;
import za.co.solar.fleet.repository.DeviceRepository;
import za.co.solar.fleet.repository.TelemetryRepository;
import za.co.solar.fleet.repository.AlarmRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

@Service
public class DashboardService {
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final AlarmRepository alarms;
    private final boolean demoMode;
    public DashboardService(DeviceRepository devices, TelemetryRepository telemetry, AlarmRepository alarms,
                            @Value("${platform.demo-data:true}") boolean demoMode) {
        this.devices = devices; this.telemetry = telemetry; this.alarms = alarms; this.demoMode = demoMode;
    }

    public Map<String,Object> summary() {
        var all = devices.findAll();
        BigDecimal pv = BigDecimal.ZERO, load = BigDecimal.ZERO, battery = BigDecimal.ZERO;
        for (var d: all) {
            var points = telemetry.findTop100ByDeviceIdOrderByTimestampDesc(d.id);
            if (!points.isEmpty()) {
                var p = points.get(0);
                pv = pv.add(nvl(p.pvPowerW)); load = load.add(nvl(p.loadPowerW)); battery = battery.add(nvl(p.batteryPowerW));
            }
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("sites", all.stream().map(d -> d.site.id).distinct().count());
        out.put("inverters", all.size());
        out.put("online", devices.countByStatus(DeviceStatus.ONLINE));
        out.put("alarms", alarms.countByClearedAtIsNull());
        out.put("pvPowerW", pv); out.put("loadPowerW", load); out.put("batteryPowerW", battery);
        out.put("demoMode", demoMode);
        return out;
    }
    private BigDecimal nvl(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }
}
