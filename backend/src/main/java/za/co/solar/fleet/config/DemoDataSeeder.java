package za.co.solar.fleet.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.repository.*;
import java.math.BigDecimal;

@Configuration
public class DemoDataSeeder {
    @Bean
    CommandLineRunner seed(TenantRepository tenants, SiteRepository sites,
                           IntegrationAccountRepository accounts, DeviceRepository devices,
                           @Value("${platform.demo-data:true}") boolean enabled) {
        return args -> {
            if (!enabled || tenants.count() > 0) return;
            Tenant tenant = new Tenant(); tenant.name = "Demo Solar Operator"; tenants.save(tenant);
            int n = 0;
            for (Brand brand : Brand.values()) {
                Site site = new Site(); site.tenant = tenant; site.name = brand.name()+" Demo Site";
                site.address = "South Africa"; site.province = "Western Cape"; site.municipality = "Demo Municipality";
                site.latitude = BigDecimal.valueOf(-33.9249 + n * 0.01); site.longitude = BigDecimal.valueOf(18.4241 + n * 0.01);
                site.ratedPvKw = BigDecimal.valueOf(8+n); site.ratedInverterKw = BigDecimal.valueOf(5+n);
                sites.save(site);
                IntegrationAccount ia = new IntegrationAccount(); ia.tenant = tenant; ia.brand = brand;
                ia.mode = IntegrationMode.DEMO; ia.name = brand.name()+" Demo Integration"; ia.enabled = true;
                ia.baseUrl = null; accounts.save(ia);
                Device d = new Device(); d.site = site; d.integrationAccount = ia; d.brand = brand;
                d.model = brand.name()+" Hybrid Inverter"; d.serialNumber = brand.name()+"-DEMO-SN-"+(n+1);
                d.externalDeviceId = brand.name()+"-DEMO-"+(n+1); d.firmwareVersion = "demo-fw-1.0";
                d.ratedPowerKw = BigDecimal.valueOf(5+n); d.status = DeviceStatus.ONLINE; devices.save(d);
                n++;
            }
        };
    }
}
