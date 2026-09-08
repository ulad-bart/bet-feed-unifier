package com.sporty.provider.alpha;

import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import com.sporty.domain.StandardMessage;
import com.sporty.domain.StandardOddsChangeMessage;
import com.sporty.provider.FeedProvider;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ProviderAlphaMapper implements FeedProvider<ProviderAlphaMessage> {

  @Override
  public StandardMessage standardize(ProviderAlphaMessage raw) {
    return switch (raw) {
      case ProviderAlphaOddsChangeRequest req -> new StandardOddsChangeMessage(
          req.eventId(), FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2",
          Map.of(Outcome.HOME, req.values().one(), Outcome.DRAW, req.values().draw(), Outcome.AWAY, req.values().two()));
      case ProviderAlphaSettlementRequest req -> new StandardBetSettlementMessage(
          req.eventId(), FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2", Outcome.fromCode(req.outcome()));
    };
  }
}
