package com.example.connectedappliance.bootstrap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.RegisterApplianceCommand;
import com.example.connectedappliance.appliance.application.UpdateCollectionStateCommand;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.metrics.application.collectnow.CollectNowService;

/**
 * Optional reviewer fixtures. Active only when the {@code review-fixtures} Spring profile is
 * enabled. Registers 10 Mock Alpha and 10 Mock Beta appliances (20 total), triggers 40 manual
 * collection attempts across the 18 ACTIVE appliances, and pauses 2 appliances — producing
 * exactly 80 metric samples and 0 warnings on first startup so that listing, filtering,
 * pagination, and metric-history APIs return meaningful data immediately.
 *
 * <p>Idempotency: each appliance is skipped when its {@code vendorKey + externalReference}
 * already exists. After a fully completed first run, restarting is safe and leaves the
 * database unchanged. After a partial first run, already-registered appliances are skipped
 * and their missing collection attempts are not reconstructed.
 *
 * <p>Activate with:
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,review-fixtures}
 */
@Component
@Profile("review-fixtures")
public final class ReviewFixturesRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewFixturesRunner.class);

    /**
     * Describes one seed appliance. {@code collectTimes} controls how many manual collections
     * are triggered on first registration. {@code pause} pauses the appliance after registration
     * without triggering any collection — demonstrating the PAUSED state and null due-time.
     */
    private record SeedAppliance(
            String displayName,
            String description,
            String vendorKey,
            String externalReference,
            int intervalSeconds,
            int collectTimes,
            boolean pause) {}

    /**
     * 10 Mock Alpha appliances (direct name and unit pass-through: reports in Celsius and Watts)
     * and 10 Mock Beta appliances (unit-conversion vendor: Fahrenheit to Celsius, kW to W).
     * One appliance per vendor is seeded in PAUSED state to demonstrate state filtering.
     *
     * <p>Alpha collection counts:  3+2+3+2+3+2+2+2+1+0 = 20 attempts, 40 samples.
     * Beta collection counts:   3+2+3+2+3+2+2+2+1+0 = 20 attempts, 40 samples.
     * Grand total: 40 attempts, 80 samples, 0 warnings (both vendors in SUCCESS mode).
     */
    private static final List<SeedAppliance> SEED_APPLIANCES = List.of(

            // ── Mock Alpha ────────────────────────────────────────────────────────────────────
            // Alpha reports temp_c (Celsius) and power_w (Watts) — stored as-is after normalization.
            new SeedAppliance("Samsung Smart Refrigerator", "Alpha-vendor kitchen fridge",
                    "mock-alpha", "alpha-seed-fridge-001", 30, 3, false),
            new SeedAppliance("LG Smart Refrigerator", "Alpha-vendor pantry fridge",
                    "mock-alpha", "alpha-seed-fridge-002", 60, 2, false),
            new SeedAppliance("Sony Bravia Smart TV", "Alpha-vendor living room television",
                    "mock-alpha", "alpha-seed-tv-001", 120, 3, false),
            new SeedAppliance("LG OLED Television", "Alpha-vendor bedroom television",
                    "mock-alpha", "alpha-seed-tv-002", 90, 2, false),
            new SeedAppliance("Daikin Air Conditioner", "Alpha-vendor main floor AC unit",
                    "mock-alpha", "alpha-seed-ac-001", 300, 3, false),
            new SeedAppliance("Mitsubishi HVAC Unit", "Alpha-vendor upstairs HVAC system",
                    "mock-alpha", "alpha-seed-ac-002", 180, 2, false),
            new SeedAppliance("Bosch Front Loader Washer", "Alpha-vendor laundry washing machine",
                    "mock-alpha", "alpha-seed-washer-001", 600, 2, false),
            new SeedAppliance("Samsung Smart Dryer", "Alpha-vendor laundry dryer",
                    "mock-alpha", "alpha-seed-dryer-001", 240, 2, false),
            new SeedAppliance("Siemens Smart Oven", "Alpha-vendor kitchen oven",
                    "mock-alpha", "alpha-seed-oven-001", 120, 1, false),
            // Paused — demonstrates PAUSED collectionState and null nextCollectionDueAt
            new SeedAppliance("Bosch Oven Combo", "Alpha-vendor paused oven",
                    "mock-alpha", "alpha-seed-oven-002", 300, 0, true),

            // ── Mock Beta ─────────────────────────────────────────────────────────────────────
            // Beta reports temperature_f (Fahrenheit) and power_kw (kilowatts).
            // Platform normalises: 71.6°F → 22.000000 CELSIUS, 0.150 kW → 150.000000 WATT.
            new SeedAppliance("Whirlpool Smart Fridge", "Beta-vendor kitchen refrigerator",
                    "mock-beta", "beta-seed-fridge-001", 30, 3, false),
            new SeedAppliance("GE French Door Fridge", "Beta-vendor garage refrigerator",
                    "mock-beta", "beta-seed-fridge-002", 60, 2, false),
            new SeedAppliance("Samsung QLED TV", "Beta-vendor home theatre television",
                    "mock-beta", "beta-seed-tv-001", 120, 3, false),
            new SeedAppliance("Hisense ULED Television", "Beta-vendor games room television",
                    "mock-beta", "beta-seed-tv-002", 90, 2, false),
            new SeedAppliance("Carrier Central AC", "Beta-vendor central air conditioning",
                    "mock-beta", "beta-seed-ac-001", 300, 3, false),
            new SeedAppliance("Trane XR HVAC", "Beta-vendor basement HVAC system",
                    "mock-beta", "beta-seed-ac-002", 180, 2, false),
            new SeedAppliance("Whirlpool Top Loader", "Beta-vendor laundry washing machine",
                    "mock-beta", "beta-seed-washer-001", 600, 2, false),
            new SeedAppliance("Maytag Smart Dryer", "Beta-vendor laundry dryer",
                    "mock-beta", "beta-seed-dryer-001", 240, 2, false),
            new SeedAppliance("Bosch Dishwasher", "Beta-vendor kitchen dishwasher",
                    "mock-beta", "beta-seed-dish-001", 120, 1, false),
            // Paused — demonstrates PAUSED collectionState and null nextCollectionDueAt
            new SeedAppliance("Miele Dishwasher Pro", "Beta-vendor paused dishwasher",
                    "mock-beta", "beta-seed-dish-002", 300, 0, true)
    );

    private final ApplianceRegistrationService registrationService;
    private final ApplianceCollectionConfigurationService configurationService;
    private final CollectNowService collectNowService;

    public ReviewFixturesRunner(
            ApplianceRegistrationService registrationService,
            ApplianceCollectionConfigurationService configurationService,
            CollectNowService collectNowService) {
        this.registrationService = registrationService;
        this.configurationService = configurationService;
        this.collectNowService = collectNowService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("review-fixtures: seeding {} appliances", SEED_APPLIANCES.size());
        int registered = 0;
        int skipped = 0;

        for (SeedAppliance seed : SEED_APPLIANCES) {
            try {
                Appliance appliance = registrationService.register(new RegisterApplianceCommand(
                        seed.displayName(),
                        seed.description(),
                        seed.vendorKey(),
                        seed.externalReference(),
                        seed.intervalSeconds()));

                for (int i = 0; i < seed.collectTimes(); i++) {
                    collectNowService.collectNow(appliance.id());
                }

                if (seed.pause()) {
                    configurationService.updateCollectionState(
                            new UpdateCollectionStateCommand(appliance.id(), CollectionState.PAUSED));
                }

                registered++;
                log.debug("review-fixtures: seeded [{}] vendor={} collections={} paused={}",
                        seed.externalReference(), seed.vendorKey(),
                        seed.collectTimes(), seed.pause());

            } catch (DuplicateApplianceException e) {
                skipped++;
                log.debug("review-fixtures: skipped [{}] — already registered",
                        seed.externalReference());
            }
        }

        log.info("review-fixtures: complete — registered={} skipped={} total={}",
                registered, skipped, SEED_APPLIANCES.size());
    }
}
