package com.sporty.provider.beta;

import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import com.sporty.domain.StandardMessage;
import com.sporty.domain.StandardOddsChangeMessage;
import com.sporty.provider.FeedNormalizer;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ProviderBetaFeedNormalizer implements FeedNormalizer<ProviderBetaMessage> {

  @Override
  public StandardMessage normalize(ProviderBetaMessage raw) {
    return switch (raw) {
      case ProviderBetaOddsChangeRequest req -> new StandardOddsChangeMessage(
          req.eventId(), FeedProviderId.PROVIDER_BETA, Instant.now(), StandardMessage.MARKET_1X2,
          Map.of(Outcome.HOME, req.odds().home(), Outcome.DRAW, req.odds().draw(), Outcome.AWAY, req.odds().away()));
      case ProviderBetaSettlementRequest req -> new StandardBetSettlementMessage(
          req.eventId(), FeedProviderId.PROVIDER_BETA, Instant.now(), StandardMessage.MARKET_1X2, mapResult(req.result()));
    };
  }

  private static Outcome mapResult(String result) {
    return switch (result) {
      case "home" -> Outcome.HOME;
      case "draw" -> Outcome.DRAW;
      case "away" -> Outcome.AWAY;
      default -> throw new IllegalArgumentException("Unknown ProviderBeta result: " + result); // unreachable post-@Pattern
    };
  }
}
