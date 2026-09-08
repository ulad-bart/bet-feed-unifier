package com.sporty.domain;

import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record StandardOddsChangeMessage(
    String eventId,
    FeedProviderId provider,
    Instant receivedAt,
    String market,
    @JsonSerialize(keyUsing = OutcomeKeySerializer.class) Map<Outcome, BigDecimal> odds
) implements StandardMessage {}
