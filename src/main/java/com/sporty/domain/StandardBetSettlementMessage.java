package com.sporty.domain;

import java.time.Instant;

public record StandardBetSettlementMessage(
    String eventId,
    FeedProviderId provider,
    Instant receivedAt,
    String market,
    Outcome outcome
) implements StandardMessage {}
