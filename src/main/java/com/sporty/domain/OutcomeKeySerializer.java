package com.sporty.domain;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

// Jackson doesn't honor @JsonValue for enum Map keys (only plain values) — jackson-databind #1535.
public final class OutcomeKeySerializer extends ValueSerializer<Outcome> {

  @Override
  public void serialize(Outcome value, JsonGenerator gen, SerializationContext context) {
    gen.writeName(value.code());
  }
}
