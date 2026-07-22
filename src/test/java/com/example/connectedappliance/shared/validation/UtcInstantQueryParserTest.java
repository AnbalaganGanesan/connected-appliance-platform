package com.example.connectedappliance.shared.validation;

import java.time.Instant;
import com.example.connectedappliance.shared.error.RequestValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtcInstantQueryParserTest {

    private final UtcInstantQueryParser parser = new UtcInstantQueryParser();

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-07-21T10:00:00Z",
            "2026-07-21T10:00:00.123Z",
            "2026-07-21T10:00:00.123456Z",
            "1969-12-31T23:59:59.999999Z"
    })
    void acceptsOnlyExactUppercaseZInstantRepresentations(String value) {
        assertThat(parser.parseRequired(value, "from")).isEqualTo(Instant.parse(value));
    }

    @Test
    void mapsMissingAndBlankValuesToSanitizedRequiredValidation() {
        for (String value : new String[] {null, "", " ", "\t"}) {
            assertThatThrownBy(() -> parser.parseRequired(value, "from"))
                    .isInstanceOf(RequestValidationException.class)
                    .hasMessage("VALIDATION_ERROR")
                    .satisfies(exception -> assertThat(
                                    ((RequestValidationException) exception).errors().get(0).code())
                            .isEqualTo("REQUIRED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-07-21T10:00:00",
            "2026-07-21T10:00:00+00:00",
            "2026-07-21T15:30:00+05:30",
            "2026-07-21T10:00:00z",
            "2026-07-21",
            "2026-02-30T10:00:00Z",
            "2026-07-21T25:00:00Z",
            "not-a-timestamp"
    })
    void rejectsMalformedOrNonUtcValuesWithoutEchoingRawInput(String value) {
        assertThatThrownBy(() -> parser.parseRequired(value, "to"))
                .isInstanceOf(RequestValidationException.class)
                .hasMessage("VALIDATION_ERROR")
                .satisfies(exception -> {
                    RequestValidationException validation =
                            (RequestValidationException) exception;
                    assertThat(validation.errors().get(0).field()).isEqualTo("to");
                    assertThat(validation.errors().get(0).code()).isEqualTo("INVALID_FORMAT");
                    assertThat(validation.toString()).doesNotContain(value);
                });
    }
}
