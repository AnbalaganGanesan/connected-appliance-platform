package com.example.connectedappliance.bootstrap;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;

import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.UpdateCollectionStateCommand;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.metrics.application.collectnow.CollectNowService;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker-free unit tests for ReviewFixturesRunner.
 *
 * <p>Tests fixture definitions, first-run collection behavior, duplicate-restart safety,
 * mixed new-and-duplicate behavior, unexpected failure policy, and structural constraints.
 * No Spring Boot context, no database, no Docker required.
 */
@ExtendWith(MockitoExtension.class)
class ReviewFixturesRunnerTest {

    @Mock
    private ApplianceRegistrationService registrationService;

    @Mock
    private ApplianceCollectionConfigurationService configurationService;

    @Mock
    private CollectNowService collectNowService;

    @InjectMocks
    private ReviewFixturesRunner runner;

    // ── Fixture definition checks ─────────────────────────────────────────────────

    @Test
    void fixtureDefinitions_totalCountIs20() throws Exception {
        assertThat(seedAppliances()).hasSize(20);
    }

    @Test
    void fixtureDefinitions_alphaCountIs10() throws Exception {
        long count = seedAppliances().stream()
                .filter(s -> "mock-alpha".equals(vendorKey(s))).count();
        assertThat(count).isEqualTo(10);
    }

    @Test
    void fixtureDefinitions_betaCountIs10() throws Exception {
        long count = seedAppliances().stream()
                .filter(s -> "mock-beta".equals(vendorKey(s))).count();
        assertThat(count).isEqualTo(10);
    }

    @Test
    void fixtureDefinitions_allVendorRefPairsAreUnique() throws Exception {
        List<?> seeds = seedAppliances();
        long distinct = seeds.stream()
                .map(s -> vendorKey(s) + ":" + externalRef(s))
                .distinct().count();
        assertThat(distinct).isEqualTo(seeds.size());
    }

    @Test
    void fixtureDefinitions_allIntervalsAreInValidRange() throws Exception {
        for (Object seed : seedAppliances()) {
            assertThat(intervalSeconds(seed))
                    .as("interval for %s", externalRef(seed))
                    .isBetween(5, 86_400);
        }
    }

    @Test
    void fixtureDefinitions_totalCollectionCountIs40() throws Exception {
        int total = seedAppliances().stream().mapToInt(ReviewFixturesRunnerTest::collectTimes).sum();
        assertThat(total).isEqualTo(40);
    }

    @Test
    void fixtureDefinitions_pauseCountIs2() throws Exception {
        long paused = seedAppliances().stream()
                .filter(ReviewFixturesRunnerTest::pause).count();
        assertThat(paused).isEqualTo(2);
    }

    // ── First successful run ──────────────────────────────────────────────────────

    @Test
    void firstRun_registersAllTwentyFixtures() throws Exception {
        setupSuccessfulRun();
        runner.run(null);
        verify(registrationService, times(20)).register(any());
    }

    @Test
    void firstRun_collectNowCalledFortyTimes() throws Exception {
        setupSuccessfulRun();
        runner.run(null);
        verify(collectNowService, times(40)).collectNow(any());
    }

    @Test
    void firstRun_collectNowUsesRegisteredApplianceIds() throws Exception {
        List<UUID> registered = new ArrayList<>();
        when(registrationService.register(any())).thenAnswer(inv -> {
            UUID id = UUID.randomUUID();
            registered.add(id);
            return activeAppliance(id);
        });
        when(collectNowService.collectNow(any())).thenReturn(dummyAttempt());
        when(configurationService.updateCollectionState(any()))
                .thenReturn(activeAppliance(UUID.randomUUID()));

        runner.run(null);

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(collectNowService, times(40)).collectNow(captor.capture());
        assertThat(captor.getAllValues()).allMatch(registered::contains);
    }

    @Test
    void firstRun_pauseCalledExactlyTwiceWithPausedState() throws Exception {
        setupSuccessfulRun();
        runner.run(null);

        ArgumentCaptor<UpdateCollectionStateCommand> captor =
                ArgumentCaptor.forClass(UpdateCollectionStateCommand.class);
        verify(configurationService, times(2)).updateCollectionState(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UpdateCollectionStateCommand::collectionState)
                .containsOnly(CollectionState.PAUSED);
    }

    @Test
    void firstRun_zeroCollectionFixturesAreNotCollected() throws Exception {
        // Stub registrations returning 20 appliances in order so paused IDs are known.
        // Indices 9 and 19 are the two fixtures with collectTimes=0 and pause=true.
        List<Appliance> all = stubOrderedRegistrations();
        when(collectNowService.collectNow(any())).thenReturn(dummyAttempt());
        when(configurationService.updateCollectionState(any())).thenReturn(all.get(0));

        runner.run(null);

        UUID pausedAlpha = all.get(9).id();
        UUID pausedBeta  = all.get(19).id();
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(collectNowService, times(40)).collectNow(captor.capture());
        assertThat(captor.getAllValues()).doesNotContain(pausedAlpha, pausedBeta);
    }

    @Test
    void firstRun_pauseOccursAfterAllCollectionCallsForThatFixture() throws Exception {
        List<Appliance> all = stubOrderedRegistrations();
        when(collectNowService.collectNow(any())).thenReturn(dummyAttempt());
        when(configurationService.updateCollectionState(any())).thenReturn(all.get(0));

        runner.run(null);

        var order = inOrder(collectNowService, configurationService);
        order.verify(collectNowService, atLeastOnce()).collectNow(any());
        order.verify(configurationService, atLeastOnce()).updateCollectionState(any());
    }

    // ── Duplicate restart ─────────────────────────────────────────────────────────

    @Test
    void duplicateRestart_allSkipped_runCompletesNormally() throws Exception {
        when(registrationService.register(any())).thenThrow(new DuplicateApplianceException());

        runner.run(null); // must not throw

        verify(collectNowService, never()).collectNow(any());
        verify(configurationService, never()).updateCollectionState(any());
    }

    // ── Mixed new and duplicate ───────────────────────────────────────────────────

    @Test
    void mixedRun_newAppliancesAreInitializedDuplicatesAreSkipped() throws Exception {
        // Fixture 0 (alpha-seed-fridge-001): collectTimes=3 — succeeds
        // Fixture 1 (alpha-seed-fridge-002): collectTimes=2 — duplicate, skip
        // Fixture 2 (alpha-seed-tv-001):    collectTimes=3 — succeeds
        // Remaining 17 — all duplicates
        UUID id0 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registrationService.register(any()))
                .thenReturn(activeAppliance(id0))
                .thenThrow(new DuplicateApplianceException())
                .thenReturn(activeAppliance(id2))
                .thenThrow(new DuplicateApplianceException()); // all remaining
        // None of fixtures 0 or 2 have pause=true, so configurationService is not called.
        when(collectNowService.collectNow(any())).thenReturn(dummyAttempt());

        runner.run(null); // must not throw despite duplicate exceptions

        // Only fixtures 0 (×3) and 2 (×3) produce collect-now calls
        verify(collectNowService, times(6)).collectNow(any());

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(collectNowService, times(6)).collectNow(captor.capture());
        assertThat(captor.getAllValues()).allMatch(id -> id.equals(id0) || id.equals(id2));
    }

    // ── Unexpected failure policy — fail startup immediately ─────────────────────

    @Test
    void unexpectedExceptionFromCollectNow_propagatesAndFailsRun() throws Exception {
        when(registrationService.register(any())).thenReturn(activeAppliance(UUID.randomUUID()));
        when(collectNowService.collectNow(any()))
                .thenThrow(new RuntimeException("simulated vendor error"));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated vendor error");
    }

    @Test
    void unexpectedExceptionFromRegistration_propagatesAndFailsRun() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new RuntimeException("simulated DB error"));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated DB error");
    }

    // ── Profile and structural constraints ───────────────────────────────────────

    @Test
    void runnerClass_isAnnotatedWithReviewFixturesProfile() {
        Profile profile = ReviewFixturesRunner.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("review-fixtures");
    }

    @Test
    void runnerClass_hasNoTransactionalAnnotation() {
        boolean classLevel = java.util.Arrays.stream(ReviewFixturesRunner.class.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Transactional"));
        assertThat(classLevel).as("@Transactional on class").isFalse();
    }

    @Test
    void runMethod_hasNoTransactionalAnnotation() throws Exception {
        var method = ReviewFixturesRunner.class.getDeclaredMethod("run", ApplicationArguments.class);
        boolean methodLevel = java.util.Arrays.stream(method.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Transactional"));
        assertThat(methodLevel).as("@Transactional on run()").isFalse();
    }

    @Test
    void runnerClass_hasNoScheduledAnnotation() {
        boolean scheduled = java.util.Arrays.stream(ReviewFixturesRunner.class.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Scheduled"));
        assertThat(scheduled).as("@Scheduled on class").isFalse();
    }

    @Test
    void runnerFields_containNoJpaOrRepositoryInfrastructureTypes() {
        for (java.lang.reflect.Field f : ReviewFixturesRunner.class.getDeclaredFields()) {
            String pkg = f.getType().getPackageName();
            assertThat(pkg)
                    .as("field %s has unexpected infrastructure type", f.getName())
                    .doesNotStartWith("jakarta.persistence")
                    .doesNotStartWith("org.springframework.data")
                    .doesNotStartWith("org.springframework.jdbc");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Accesses the private SEED_APPLIANCES list via reflection. */
    @SuppressWarnings("unchecked")
    private static List<Object> seedAppliances() throws Exception {
        Field field = ReviewFixturesRunner.class.getDeclaredField("SEED_APPLIANCES");
        field.setAccessible(true);
        return (List<Object>) field.get(null);
    }

    private static String vendorKey(Object seed) {
        return invoke(seed, "vendorKey");
    }

    private static String externalRef(Object seed) {
        return invoke(seed, "externalReference");
    }

    private static int collectTimes(Object seed) {
        return invoke(seed, "collectTimes");
    }

    private static boolean pause(Object seed) {
        return invoke(seed, "pause");
    }

    private static int intervalSeconds(Object seed) {
        return invoke(seed, "intervalSeconds");
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object obj, String method) {
        try {
            return (T) obj.getClass().getMethod(method).invoke(obj);
        } catch (Exception e) {
            throw new RuntimeException("reflection failed for " + method, e);
        }
    }

    /** Builds a minimal valid ACTIVE Appliance for mock return values. */
    private static Appliance activeAppliance(UUID id) {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        return new Appliance(
                id, "Test Appliance", null, "mock-alpha", "unit-test-ref",
                CollectionState.ACTIVE, 30, t.plusSeconds(30), 0,
                LastCollectionStatus.NEVER_ATTEMPTED, 0, t, t);
    }

    /** Builds a minimal valid CollectionAttempt for mock return values. */
    private static CollectionAttempt dummyAttempt() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        return new CollectionAttempt(
                UUID.randomUUID(), UUID.randomUUID(),
                CollectionTrigger.MANUAL, CollectionOutcome.SUCCESS,
                t, t, 2, List.of(), null, t.plusSeconds(30));
    }

    /** Configures all three services for a straightforward successful run. */
    private void setupSuccessfulRun() {
        Appliance dummy = activeAppliance(UUID.randomUUID());
        when(registrationService.register(any())).thenReturn(dummy);
        when(collectNowService.collectNow(any())).thenReturn(dummyAttempt());
        when(configurationService.updateCollectionState(any())).thenReturn(dummy);
    }

    /**
     * Stubs registration to return 20 distinct Appliances in SEED_APPLIANCES order.
     * Index 9 → alpha-seed-oven-002 (pause=true, collectTimes=0).
     * Index 19 → beta-seed-dish-002 (pause=true, collectTimes=0).
     */
    private List<Appliance> stubOrderedRegistrations() {
        List<Appliance> appliances = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            appliances.add(activeAppliance(UUID.randomUUID()));
        }
        OngoingStubbing<Appliance> stub =
                when(registrationService.register(any())).thenReturn(appliances.get(0));
        for (int i = 1; i < 20; i++) {
            stub = stub.thenReturn(appliances.get(i));
        }
        return appliances;
    }
}
