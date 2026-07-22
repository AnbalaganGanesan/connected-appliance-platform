package com.example.connectedappliance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.connectedappliance.bootstrap.DatabaseIndependentTestSupport;

@SpringBootTest(properties = DatabaseIndependentTestSupport.AUTO_CONFIGURATION_EXCLUSIONS)
class ConnectedAppliancePlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}
