package com.sporty.provider.beta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record ProviderBetaOdds(
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("home") BigDecimal home,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("draw") BigDecimal draw,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("away") BigDecimal away
) {}
