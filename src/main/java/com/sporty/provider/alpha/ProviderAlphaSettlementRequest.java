package com.sporty.provider.alpha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record ProviderAlphaSettlementRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotBlank @Pattern(regexp = "^(1|X|2)$") @JsonProperty("outcome") String outcome
) implements ProviderAlphaMessage {}
