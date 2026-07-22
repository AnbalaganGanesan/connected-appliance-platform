package com.example.connectedappliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.connectedappliance")
public class ConnectedAppliancePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectedAppliancePlatformApplication.class, args);
    }
}
