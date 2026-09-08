package com.sporty.provider.beta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record ProviderBetaSettlementRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotBlank @Pattern(regexp = "^(home|draw|away)$") @JsonProperty("result") String result
) implements ProviderBetaMessage {}
