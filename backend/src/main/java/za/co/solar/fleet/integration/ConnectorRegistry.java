package za.co.solar.fleet.integration;

import org.springframework.stereotype.Component;
import za.co.solar.fleet.domain.Brand;
import za.co.solar.fleet.domain.IntegrationAccount;
import za.co.solar.fleet.domain.IntegrationMode;
import java.util.EnumMap;
import java.util.Map;

@Component
public class ConnectorRegistry {
    private final Map<Brand, InverterConnector> demo = new EnumMap<>(Brand.class);
    private final Map<Brand, InverterConnector> live = new EnumMap<>(Brand.class);

    public ConnectorRegistry() {
        int i = 0;
        for (Brand brand : Brand.values()) demo.put(brand, new DemoBrandConnector(brand, ++i));
        live.put(Brand.LUXPOWER, new LuxPowerConnector());
        live.put(Brand.ECT_UNVERIFIED, new EctConnector());
        live.put(Brand.SOLARMAN, new SolarmanConnector());
        live.put(Brand.SUNGROW, new SungrowConnector());
        live.put(Brand.SOLIS, new SolisConnector());
        live.put(Brand.SOLAREDGE, new SolarEdgeConnector());
        live.put(Brand.VICTRON, new VictronConnector());
        live.put(Brand.FRONIUS, new FroniusConnector());
        live.put(Brand.SOLAX, new SolaxConnector());
        live.put(Brand.SIGENERGY, new SigenergyConnector());
    }

    public InverterConnector forBrand(Brand brand) {
        InverterConnector c = demo.get(brand);
        if (c == null) throw new IllegalArgumentException("No demo connector registered for " + brand);
        return c;
    }

    public InverterConnector forAccount(IntegrationAccount account) {
        Map<Brand, InverterConnector> source = account.mode == IntegrationMode.LIVE ? live : demo;
        InverterConnector c = source.get(account.brand);
        if (c == null) throw new IllegalArgumentException("No connector registered for " + account.brand);
        return c;
    }
}
