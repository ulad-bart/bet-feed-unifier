package com.sporty.provider.beta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record ProviderBetaOddsChangeRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotNull @Valid @JsonProperty("odds") ProviderBetaOdds odds
) implements ProviderBetaMessage {}
