package com.example.connectedappliance.bootstrap;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "test.fixture")
public class TestFixtureProperties {

    @NotBlank
    private String name = "default-fixture";

    @Min(1)
    private int maximumItems = 1;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaximumItems() {
        return maximumItems;
    }

    public void setMaximumItems(int maximumItems) {
        this.maximumItems = maximumItems;
    }
}
