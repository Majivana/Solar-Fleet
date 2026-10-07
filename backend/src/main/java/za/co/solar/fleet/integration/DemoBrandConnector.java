package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class DemoBrandConnector implements InverterConnector {
    private final Brand brand;
    private final int index;
    public DemoBrandConnector(Brand brand, int index) { this.brand = brand; this.index = index; }
    @Override public Brand brand() { return brand; }
    @Override public ConnectorResult<List<RemoteDevice>> discoverDevices(IntegrationAccount account) {
        return ConnectorResult.ok(List.of(new RemoteDevice(
                brand.name()+"-DEMO-"+index,
                brand.name()+"-SN-"+index,
                brand.name()+" Hybrid Inverter",
                "demo-fw-1.0",
                5.0 + index)));
    }
    @Override public ConnectorResult<NormalizedTelemetry> readTelemetry(IntegrationAccount account, RemoteDevice device) {
        if (index == 5) return ConnectorResult.fail("Demo connection check failed. Review the integration configuration.");
        double pv = 4200 + (index * 550);
        double load = 3000 + (index * 310);
        double battery = pv - load - 250;
        String raw = "{\"demo\":true,\"brand\":\""+brand+"\",\"device\":\""+device.serialNumber()+"\"}";
        return ConnectorResult.ok(new NormalizedTelemetry(
                Instant.now().minusSeconds(index == 2 ? 27 * 60 : index == 4 ? 36 * 60 : 0),
                bd(pv), bd(25.5 + index), bd(4200 + index*150),
                bd(61 + index*3), bd(51.2), bd(battery / 51.2), bd(battery), bd(31 + index),
                bd(230), bd(50.0), bd(250), bd(50), bd(0), bd(load), bd(17.8), bd(41 + index),
                bd(94.0), index == 2 ? DeviceStatus.OFFLINE : index == 3 ? DeviceStatus.ALARM : DeviceStatus.ONLINE,
                index == 3 ? "DEMO_FAULT_01" : null, null, raw));
    }
    @Override public ConnectorResult<List<RemoteAlarm>> readAlarms(IntegrationAccount account, RemoteDevice device, Instant from, Instant to) {
        if (index == 3) return ConnectorResult.ok(List.of(new RemoteAlarm("CRITICAL", "DEMO_FAULT_01",
                "Inverter temperature is above the expected operating range.", Instant.now().minusSeconds(900))));
        return ConnectorResult.ok(List.of());
    }
    private static BigDecimal bd(double v) { return BigDecimal.valueOf(v); }
}
