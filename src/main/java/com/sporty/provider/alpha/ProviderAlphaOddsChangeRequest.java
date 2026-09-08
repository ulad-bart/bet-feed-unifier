package com.sporty.provider.alpha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record ProviderAlphaOddsChangeRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotNull @Valid @JsonProperty("values") ProviderAlphaOdds values
) implements ProviderAlphaMessage {}
