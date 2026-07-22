package com.example.connectedappliance.appliance.domain;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplianceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant DUE_AT = Instant.parse("2026-07-21T10:00:30Z");

    @Test
    void constructsValidActiveApplianceAndPreservesSuppliedValues() {
        Arguments values = validActiveArguments();
        values.displayName = "  Kitchen appliance  ";
        values.description = "  Reviewer description  ";
        values.externalReference = "  Device/Aa-001  ";

        Appliance appliance = values.create();

        assertThat(appliance.id()).isEqualTo(values.id);
        assertThat(appliance.displayName()).isEqualTo(values.displayName);
        assertThat(appliance.description()).isEqualTo(values.description);
        assertThat(appliance.vendorKey()).isEqualTo(values.vendorKey);
        assertThat(appliance.externalReference()).isEqualTo(values.externalReference);
        assertThat(appliance.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(appliance.collectionIntervalSeconds()).isEqualTo(values.intervalSeconds);
        assertThat(appliance.nextCollectionDueAt()).isEqualTo(values.nextDueAt);
        assertThat(appliance.consecutiveFailureCount()).isEqualTo(values.failureCount);
        assertThat(appliance.lastCollectionStatus()).isEqualTo(values.lastStatus);
        assertThat(appliance.version()).isEqualTo(values.version);
        assertThat(appliance.createdAt()).isEqualTo(values.createdAt);
        assertThat(appliance.updatedAt()).isEqualTo(values.updatedAt);
    }

    @Test
    void constructsValidPausedApplianceWithNullableDescriptionAndDueTime() {
        Arguments values = validActiveArguments();
        values.description = null;
        values.collectionState = CollectionState.PAUSED;
        values.nextDueAt = null;

        Appliance appliance = values.create();

        assertThat(appliance.description()).isNull();
        assertThat(appliance.collectionState()).isEqualTo(CollectionState.PAUSED);
        assertThat(appliance.nextCollectionDueAt()).isNull();
    }

    @Test
    void exposesExactlyTheApprovedOperationalEnumValues() {
        assertThat(CollectionState.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "PAUSED");
        assertThat(LastCollectionStatus.values())
                .extracting(Enum::name)
                .containsExactly("NEVER_ATTEMPTED", "SUCCESS", "PARTIAL_SUCCESS", "FAILED");
    }

    @Test
    void rejectsInvalidIdentityAndDisplayName() {
        Arguments values = validActiveArguments();
        values.id = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.displayName = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.displayName = "   ";
        assertInvalid(values);

        values = validActiveArguments();
        values.displayName = "d".repeat(101);
        assertInvalid(values);
    }

    @Test
    void enforcesDescriptionLengthWithoutNormalizingContent() {
        Arguments values = validActiveArguments();
        values.description = "d".repeat(500);
        assertThat(values.create().description()).hasSize(500);

        values = validActiveArguments();
        values.description = "d".repeat(501);
        assertInvalid(values);
    }

    @Test
    void rejectsInvalidVendorKeys() {
        Arguments values = validActiveArguments();
        values.vendorKey = null;
        assertNullRejected(values);

        for (String invalid : new String[] {"", "Mock-Alpha", "mock_alpha", "mock alpha"}) {
            values = validActiveArguments();
            values.vendorKey = invalid;
            assertInvalid(values);
        }

        values = validActiveArguments();
        values.vendorKey = "v".repeat(51);
        assertInvalid(values);
    }

    @Test
    void rejectsInvalidExternalReferencesAndPreservesValidOpaqueReference() {
        Arguments values = validActiveArguments();
        values.externalReference = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.externalReference = "   ";
        assertInvalid(values);

        values = validActiveArguments();
        values.externalReference = "r".repeat(129);
        assertInvalid(values);

        values = validActiveArguments();
        values.externalReference = "  MixedCase/Reference  ";
        assertThat(values.create().externalReference()).isEqualTo("  MixedCase/Reference  ");
    }

    @Test
    void rejectsInvalidOperationalValues() {
        Arguments values = validActiveArguments();
        values.collectionState = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.intervalSeconds = 4;
        assertInvalid(values);

        values = validActiveArguments();
        values.intervalSeconds = 86_401;
        assertInvalid(values);

        values = validActiveArguments();
        values.failureCount = -1;
        assertInvalid(values);

        values = validActiveArguments();
        values.lastStatus = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.version = -1;
        assertInvalid(values);
    }

    @Test
    void acceptsInclusiveIntervalBoundsAndNonNegativeCounters() {
        Arguments values = validActiveArguments();
        values.intervalSeconds = 5;
        values.failureCount = 0;
        values.version = 0;
        assertThat(values.create().collectionIntervalSeconds()).isEqualTo(5);

        values = validActiveArguments();
        values.intervalSeconds = 86_400;
        values.failureCount = 3;
        values.version = 4;
        Appliance appliance = values.create();
        assertThat(appliance.collectionIntervalSeconds()).isEqualTo(86_400);
        assertThat(appliance.consecutiveFailureCount()).isEqualTo(3);
        assertThat(appliance.version()).isEqualTo(4);
    }

    @Test
    void rejectsInvalidTimestampAndDueStateCombinations() {
        Arguments values = validActiveArguments();
        values.createdAt = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.updatedAt = null;
        assertNullRejected(values);

        values = validActiveArguments();
        values.updatedAt = values.createdAt.minusNanos(1);
        assertInvalid(values);

        values = validActiveArguments();
        values.nextDueAt = null;
        assertInvalid(values);

        values = validActiveArguments();
        values.collectionState = CollectionState.PAUSED;
        assertInvalid(values);
    }

    @Test
    void replacesDisplayNameDescriptionBothFieldsAndClearsDescription() {
        Appliance original = validActiveArguments().create();
        Instant firstChange = CREATED_AT.plusSeconds(1);

        Appliance displayChanged = original.replaceMetadata(
                "Updated appliance", original.description(), firstChange);
        assertThat(displayChanged.displayName()).isEqualTo("Updated appliance");
        assertThat(displayChanged.description()).isEqualTo(original.description());

        Appliance descriptionChanged = original.replaceMetadata(
                original.displayName(), "Updated description", firstChange);
        assertThat(descriptionChanged.displayName()).isEqualTo(original.displayName());
        assertThat(descriptionChanged.description()).isEqualTo("Updated description");

        Appliance bothChanged = original.replaceMetadata(
                "Both updated", "Both description", firstChange);
        assertThat(bothChanged.displayName()).isEqualTo("Both updated");
        assertThat(bothChanged.description()).isEqualTo("Both description");

        Appliance cleared = original.replaceMetadata(
                original.displayName(), null, firstChange);
        assertThat(cleared.description()).isNull();
    }

    @Test
    void identicalMetadataReturnsSameInstanceAndPreservesTimestampAndVersion() {
        Appliance original = validActiveArguments().create();

        Appliance result = original.replaceMetadata(
                original.displayName(), original.description(), CREATED_AT.plusSeconds(30));

        assertThat(result).isSameAs(original);
        assertThat(result.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.version()).isEqualTo(original.version());
    }

    @Test
    void realMetadataChangeUsesSuppliedTimeAndPreservesEveryOtherField() {
        Appliance original = validActiveArguments().create();
        Instant changedAt = CREATED_AT.plusSeconds(20);

        Appliance changed = original.replaceMetadata("Changed", "Changed description", changedAt);

        assertThat(changed.id()).isEqualTo(original.id());
        assertThat(changed.vendorKey()).isEqualTo(original.vendorKey());
        assertThat(changed.externalReference()).isEqualTo(original.externalReference());
        assertThat(changed.collectionState()).isEqualTo(original.collectionState());
        assertThat(changed.collectionIntervalSeconds())
                .isEqualTo(original.collectionIntervalSeconds());
        assertThat(changed.nextCollectionDueAt()).isEqualTo(original.nextCollectionDueAt());
        assertThat(changed.consecutiveFailureCount())
                .isEqualTo(original.consecutiveFailureCount());
        assertThat(changed.lastCollectionStatus()).isEqualTo(original.lastCollectionStatus());
        assertThat(changed.createdAt()).isEqualTo(original.createdAt());
        assertThat(changed.version()).isEqualTo(original.version());
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
    }

    @Test
    void metadataReplacementValidatesLimitsWithoutTrimmingInput() {
        Appliance original = validActiveArguments().create();
        Instant changedAt = CREATED_AT.plusSeconds(1);

        Appliance unnormalized = original.replaceMetadata(
                "  Stored exactly  ", "  Description exactly  ", changedAt);
        assertThat(unnormalized.displayName()).isEqualTo("  Stored exactly  ");
        assertThat(unnormalized.description()).isEqualTo("  Description exactly  ");

        assertThatThrownBy(() -> original.replaceMetadata(null, null, changedAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> original.replaceMetadata("   ", null, changedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> original.replaceMetadata("x".repeat(101), null, changedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> original.replaceMetadata("Valid", "x".repeat(501), changedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> original.replaceMetadata("Valid", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void activeIntervalChangeRecalculatesDueTimeAndPreservesUnrelatedState() {
        Appliance original = validActiveArguments().create();
        Instant changedAt = CREATED_AT.plusSeconds(20);

        Appliance changed = original.replaceCollectionInterval(60, changedAt);

        assertThat(changed.collectionIntervalSeconds()).isEqualTo(60);
        assertThat(changed.nextCollectionDueAt()).isEqualTo(changedAt.plusSeconds(60));
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
        assertUnchangedIdentityAndCollectionHistory(original, changed);
    }

    @Test
    void pausedIntervalChangeKeepsDueTimeNull() {
        Arguments values = validActiveArguments();
        values.collectionState = CollectionState.PAUSED;
        values.nextDueAt = null;
        Appliance original = values.create();
        Instant changedAt = CREATED_AT.plusSeconds(20);

        Appliance changed = original.replaceCollectionInterval(60, changedAt);

        assertThat(changed.collectionIntervalSeconds()).isEqualTo(60);
        assertThat(changed.collectionState()).isEqualTo(CollectionState.PAUSED);
        assertThat(changed.nextCollectionDueAt()).isNull();
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
        assertUnchangedIdentityAndCollectionHistory(original, changed);
    }

    @Test
    void identicalIntervalIsAnExactNoOpForActiveAndPausedAppliances() {
        Appliance active = validActiveArguments().create();
        Arguments pausedValues = validActiveArguments();
        pausedValues.collectionState = CollectionState.PAUSED;
        pausedValues.nextDueAt = null;
        Appliance paused = pausedValues.create();

        assertThat(active.replaceCollectionInterval(
                        active.collectionIntervalSeconds(), CREATED_AT.plusSeconds(20)))
                .isSameAs(active);
        assertThat(paused.replaceCollectionInterval(
                        paused.collectionIntervalSeconds(), CREATED_AT.plusSeconds(20)))
                .isSameAs(paused);
        assertThat(active.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(paused.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void intervalReplacementRejectsInvalidBoundsAndChangeTimes() {
        Appliance original = validActiveArguments().create();

        assertThat(original.replaceCollectionInterval(5, CREATED_AT)
                        .collectionIntervalSeconds())
                .isEqualTo(5);
        assertThat(original.replaceCollectionInterval(86_400, CREATED_AT)
                        .collectionIntervalSeconds())
                .isEqualTo(86_400);

        assertThatThrownBy(() -> original.replaceCollectionInterval(4, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> original.replaceCollectionInterval(86_401, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> original.replaceCollectionInterval(60, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> original.replaceCollectionInterval(
                        60, CREATED_AT.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pauseAndResumeApplyExactDueTimeSemanticsAndPreserveInterval() {
        Appliance active = validActiveArguments().create();
        Instant pausedAt = CREATED_AT.plusSeconds(20);

        Appliance paused = active.replaceCollectionState(CollectionState.PAUSED, pausedAt);

        assertThat(paused.collectionState()).isEqualTo(CollectionState.PAUSED);
        assertThat(paused.nextCollectionDueAt()).isNull();
        assertThat(paused.collectionIntervalSeconds())
                .isEqualTo(active.collectionIntervalSeconds());
        assertThat(paused.updatedAt()).isEqualTo(pausedAt);
        assertUnchangedIdentityAndCollectionHistory(active, paused);

        Instant resumedAt = pausedAt.plusSeconds(10);
        Appliance resumed = paused.replaceCollectionState(CollectionState.ACTIVE, resumedAt);
        assertThat(resumed.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(resumed.nextCollectionDueAt()).isEqualTo(resumedAt);
        assertThat(resumed.collectionIntervalSeconds())
                .isEqualTo(active.collectionIntervalSeconds());
        assertThat(resumed.updatedAt()).isEqualTo(resumedAt);
        assertUnchangedIdentityAndCollectionHistory(paused, resumed);
    }

    @Test
    void identicalStateIsAnExactNoOpAndStateReplacementValidatesInputs() {
        Appliance active = validActiveArguments().create();
        Arguments pausedValues = validActiveArguments();
        pausedValues.collectionState = CollectionState.PAUSED;
        pausedValues.nextDueAt = null;
        Appliance paused = pausedValues.create();

        assertThat(active.replaceCollectionState(
                        CollectionState.ACTIVE, CREATED_AT.plusSeconds(20)))
                .isSameAs(active);
        assertThat(paused.replaceCollectionState(
                        CollectionState.PAUSED, CREATED_AT.plusSeconds(20)))
                .isSameAs(paused);
        assertThatThrownBy(() -> active.replaceCollectionState(null, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> active.replaceCollectionState(
                        CollectionState.PAUSED, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> active.replaceCollectionState(
                        CollectionState.PAUSED, CREATED_AT.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesNoMutationEqualityOrFrameworkSurface() {
        assertThat(Appliance.class.getDeclaredAnnotations()).isEmpty();
        assertThat(Arrays.stream(Appliance.class.getDeclaredFields())
                        .flatMap(field -> Arrays.stream(field.getDeclaredAnnotations())))
                .isEmpty();

        assertThat(Arrays.stream(Appliance.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(Method::getName))
                .containsExactlyInAnyOrder(
                        "id",
                        "displayName",
                        "description",
                        "vendorKey",
                        "externalReference",
                        "collectionState",
                        "collectionIntervalSeconds",
                        "nextCollectionDueAt",
                        "consecutiveFailureCount",
                        "lastCollectionStatus",
                        "version",
                        "createdAt",
                        "updatedAt",
                        "replaceMetadata",
                        "replaceCollectionInterval",
                        "replaceCollectionState")
                .doesNotContain("equals", "hashCode")
                .noneMatch(name -> name.startsWith("set"))
                .doesNotContain(
                        "changeInterval",
                        "pause",
                        "resume",
                        "finalizeCollection");
    }

    private void assertUnchangedIdentityAndCollectionHistory(
            Appliance original, Appliance changed) {
        assertThat(changed.id()).isEqualTo(original.id());
        assertThat(changed.displayName()).isEqualTo(original.displayName());
        assertThat(changed.description()).isEqualTo(original.description());
        assertThat(changed.vendorKey()).isEqualTo(original.vendorKey());
        assertThat(changed.externalReference()).isEqualTo(original.externalReference());
        assertThat(changed.consecutiveFailureCount())
                .isEqualTo(original.consecutiveFailureCount());
        assertThat(changed.lastCollectionStatus()).isEqualTo(original.lastCollectionStatus());
        assertThat(changed.version()).isEqualTo(original.version());
        assertThat(changed.createdAt()).isEqualTo(original.createdAt());
    }

    private Arguments validActiveArguments() {
        return new Arguments();
    }

    private void assertNullRejected(Arguments values) {
        assertThatThrownBy(values::create).isInstanceOf(NullPointerException.class);
    }

    private void assertInvalid(Arguments values) {
        assertThatThrownBy(values::create).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Arguments {
        private UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private String displayName = "Kitchen appliance";
        private String description = "Reviewer appliance";
        private String vendorKey = "mock-alpha";
        private String externalReference = "Device/Aa-001";
        private CollectionState collectionState = CollectionState.ACTIVE;
        private int intervalSeconds = 30;
        private Instant nextDueAt = DUE_AT;
        private int failureCount = 0;
        private LastCollectionStatus lastStatus = LastCollectionStatus.NEVER_ATTEMPTED;
        private long version = 0;
        private Instant createdAt = CREATED_AT;
        private Instant updatedAt = CREATED_AT;

        private Appliance create() {
            return new Appliance(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    intervalSeconds,
                    nextDueAt,
                    failureCount,
                    lastStatus,
                    version,
                    createdAt,
                    updatedAt);
        }
    }
}
