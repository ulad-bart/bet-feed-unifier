package com.sporty.provider.alpha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record ProviderAlphaOdds(
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("1") BigDecimal one,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("X") BigDecimal draw,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("2") BigDecimal two
) {}
