package com.sporty.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "messageType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StandardOddsChangeMessage.class, name = "ODDS_CHANGE"),
    @JsonSubTypes.Type(value = StandardBetSettlementMessage.class, name = "BET_SETTLEMENT")
})
public sealed interface StandardMessage permits StandardOddsChangeMessage, StandardBetSettlementMessage {
  String eventId();

  FeedProviderId provider();

  Instant receivedAt();

  String market();
}
