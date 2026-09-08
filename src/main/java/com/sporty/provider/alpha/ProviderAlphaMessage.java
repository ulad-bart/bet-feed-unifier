package com.sporty.provider.alpha;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "msg_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProviderAlphaOddsChangeRequest.class, name = "odds_update"),
    @JsonSubTypes.Type(value = ProviderAlphaSettlementRequest.class, name = "settlement")
})
sealed interface ProviderAlphaMessage permits ProviderAlphaOddsChangeRequest, ProviderAlphaSettlementRequest {}
