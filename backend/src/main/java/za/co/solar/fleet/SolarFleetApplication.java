package za.co.solar.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SolarFleetApplication {
    public static void main(String[] args) {
        SpringApplication.run(SolarFleetApplication.class, args);
    }
}
