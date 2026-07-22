package com.example.connectedappliance.metrics.application.control;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VendorFailureClassifierTest {

    private final VendorFailureClassifier classifier = new VendorFailureClassifier();

    @ParameterizedTest
    @EnumSource(VendorFailureCategory.class)
    void mapsEveryTypedVendorCategoryAndPreservesSanitizedMessage(
            VendorFailureCategory vendorCategory) {
        Integer retryAfter = vendorCategory == VendorFailureCategory.RATE_LIMITED ? 45 : null;
        VendorMetricException exception = new VendorMetricException(
                vendorCategory,
                "Approved sanitized vendor failure.",
                retryAfter,
                List.of());

        ClassifiedVendorFailure classified = classifier.classify(exception);

        assertThat(classified.failure().category().name()).isEqualTo(vendorCategory.name());
        assertThat(classified.failure().message())
                .isEqualTo("Approved sanitized vendor failure.");
        assertThat(classified.failure().retryAfterSeconds()).isEqualTo(retryAfter);
    }

    @Test
    void convertsWarningsInOrderAndReturnsImmutableList() {
        VendorMetricException exception = new VendorMetricException(
                VendorFailureCategory.INVALID_DATA,
                "No usable readings.",
                null,
                List.of(
                        VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC),
                        VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE),
                        VendorMetricWarning.forCode(VendorMetricWarningCode.INCOMPATIBLE_UNIT)));

        ClassifiedVendorFailure classified = classifier.classify(exception);

        assertThat(classified.warnings())
                .extracting(CollectionWarning::code)
                .containsExactly("UNKNOWN_METRIC", "MALFORMED_VALUE", "INCOMPATIBLE_UNIT");
        assertThatThrownBy(() -> classified.warnings().add(
                        new CollectionWarning("EXTRA", "not allowed")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsExecutionTimeoutAndWaitingInterruptionToStableFailures() {
        ClassifiedVendorFailure timeout = classifier.classify(
                new TimeoutException("executor internals"));
        ClassifiedVendorFailure interrupted = classifier.classify(
                new InterruptedException("thread internals"));

        assertThat(timeout.failure().category())
                .isEqualTo(CollectionFailureCategory.TIMEOUT);
        assertThat(timeout.failure().message())
                .isEqualTo(VendorFailureClassifier.TIMEOUT_MESSAGE);
        assertThat(timeout.failure().retryAfterSeconds()).isNull();
        assertThat(timeout.warnings()).isEmpty();
        assertThat(interrupted.failure().category())
                .isEqualTo(CollectionFailureCategory.TRANSIENT);
        assertThat(interrupted.failure().message())
                .isEqualTo(VendorFailureClassifier.TEMPORARY_MESSAGE);
        assertThat(interrupted.warnings()).isEmpty();
    }

    @Test
    void sanitizesUnexpectedThrowableAndRejectsNull() {
        String sensitive = "SQL password=secret vendor-key external-reference";

        ClassifiedVendorFailure unexpected =
                classifier.classify(new IllegalArgumentException(sensitive));

        assertThat(unexpected.failure().category())
                .isEqualTo(CollectionFailureCategory.UNEXPECTED);
        assertThat(unexpected.failure().message())
                .isEqualTo(VendorFailureClassifier.UNEXPECTED_MESSAGE)
                .doesNotContain(sensitive, "IllegalArgumentException");
        assertThat(unexpected.warnings()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> classifier.classify(null));
    }

    @Test
    void classifiedValueDefensivelyCopiesWarningsAndRejectsNullElements() {
        List<CollectionWarning> mutable = new java.util.ArrayList<>();
        mutable.add(new CollectionWarning("FIRST", "first"));
        ClassifiedVendorFailure classified = new ClassifiedVendorFailure(
                new com.example.connectedappliance.metrics.domain.CollectionFailure(
                        CollectionFailureCategory.TRANSIENT, "temporary", null),
                mutable);

        mutable.clear();

        assertThat(classified.warnings()).extracting(CollectionWarning::code)
                .containsExactly("FIRST");
        assertThatThrownBy(() -> new ClassifiedVendorFailure(
                        classified.failure(), java.util.Arrays.asList((CollectionWarning) null)))
                .isInstanceOf(NullPointerException.class);
    }
}
