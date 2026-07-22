package com.example.connectedappliance.appliance.api;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Full replacement of an Appliance collection interval. */
public record UpdateCollectionIntervalRequest(
        @NotNull @Min(5) @Max(86_400)
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        Integer collectionIntervalSeconds) {

    /** Prevents Jackson scalar coercion from accepting strings or decimal numbers as integers. */
    static final class StrictIntegerDeserializer extends JsonDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}
