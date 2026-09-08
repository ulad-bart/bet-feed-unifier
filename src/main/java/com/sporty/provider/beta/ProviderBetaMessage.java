package com.sporty.provider.beta;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProviderBetaOddsChangeRequest.class, name = "ODDS"),
    @JsonSubTypes.Type(value = ProviderBetaSettlementRequest.class, name = "SETTLEMENT")
})
sealed interface ProviderBetaMessage permits ProviderBetaOddsChangeRequest, ProviderBetaSettlementRequest {}
