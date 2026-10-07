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
                           za.co.solar.fleet.repository.ProjectRepository projects,
                           za.co.solar.fleet.repository.ProjectCostRepository costs,
                           za.co.solar.fleet.repository.AuditEventRepository audit,
                           @Value("${platform.demo-data:true}") boolean enabled) {
        return args -> {
            if (!enabled || tenants.count() > 0) return;
            Tenant tenant = new Tenant(); tenant.name = "Demo Solar Operator"; tenants.save(tenant);
            Project project = new Project();
            project.tenant = tenant;
            project.name = "Demo Solar Portfolio";
            project.customer = "Demo Customer";
            project.location = "Western Cape, South Africa";
            project.status = ProjectStatus.OPERATIONAL;
            project.description = "Sample operating project with a mix of inverter conditions.";
            project.budget = new BigDecimal("2400000.00");
            project.capacityKw = new BigDecimal("125.000");
            project.importTariff = new BigDecimal("3.2500");
            project.currency = "ZAR";
            projects.save(project);
            int n = 0;
            for (Brand brand : Brand.values()) {
                Site site = new Site(); site.tenant = tenant; site.name = brand.name()+" Demo Site";
                site.project = project;
                site.address = "South Africa"; site.province = "Western Cape"; site.municipality = "Demo Municipality";
                site.latitude = BigDecimal.valueOf(-33.9249 + n * 0.01); site.longitude = BigDecimal.valueOf(18.4241 + n * 0.01);
                site.ratedPvKw = BigDecimal.valueOf(8+n); site.ratedInverterKw = BigDecimal.valueOf(5+n);
                sites.save(site);
                IntegrationAccount ia = new IntegrationAccount(); ia.tenant = tenant; ia.brand = brand;
                ia.mode = IntegrationMode.DEMO; ia.name = brand.name()+" Demo Integration"; ia.enabled = true;
                ia.baseUrl = null; accounts.save(ia);
                Device d = new Device(); d.site = site; d.integrationAccount = ia; d.brand = brand;
                d.tenant = tenant;
                d.model = brand.name()+" Hybrid Inverter"; d.serialNumber = brand.name()+"-DEMO-SN-"+(n+1);
                d.externalDeviceId = brand.name()+"-DEMO-"+(n+1); d.firmwareVersion = "demo-fw-1.0";
                d.ratedPowerKw = BigDecimal.valueOf(5+n); d.status = DeviceStatus.ONLINE;
                if (n == 9) {
                    d.lifecycleStatus = LifecycleStatus.RETIRED;
                    d.retiredAt = java.time.Instant.now().minusSeconds(86400);
                    d.retirementReason = "Demo retired equipment";
                }
                devices.save(d);
                n++;
            }
            ProjectCost equipment = new ProjectCost();
            equipment.project = project; equipment.category = "INVERTER"; equipment.description = "Demo inverter procurement";
            equipment.quantity = BigDecimal.ONE; equipment.unitCost = new BigDecimal("85000.00");
            equipment.total = new BigDecimal("85000.00"); equipment.currency = "ZAR";
            equipment.costDate = java.time.LocalDate.now().minusDays(45);
            costs.save(equipment);
            ProjectCost installation = new ProjectCost();
            installation.project = project; installation.category = "INSTALLATION"; installation.description = "Demo electrical installation";
            installation.quantity = BigDecimal.ONE; installation.unitCost = new BigDecimal("42000.00");
            installation.total = new BigDecimal("42000.00"); installation.currency = "ZAR";
            installation.costDate = java.time.LocalDate.now().minusDays(30);
            costs.save(installation);
            AuditEvent event = new AuditEvent();
            event.tenant = tenant; event.project = project; event.action = "PROJECT_CREATED";
            event.summary = "Demo operating project created"; audit.save(event);
        };
    }
}
