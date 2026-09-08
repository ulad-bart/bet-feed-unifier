package com.sporty.provider.beta;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import com.sporty.domain.StandardMessage;
import com.sporty.domain.StandardOddsChangeMessage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProviderBetaMapperTest {

  private final ProviderBetaMapper mapper = new ProviderBetaMapper();

  @Test
  void standardize_oddsRequest_mapsToStandardOddsChangeMessage() {
    ProviderBetaOddsChangeRequest request = new ProviderBetaOddsChangeRequest(
        "ev456", new ProviderBetaOdds(new BigDecimal("1.95"), new BigDecimal("3.2"), new BigDecimal("4.0")));

    StandardMessage result = mapper.standardize(request);

    assertThat(result).isInstanceOf(StandardOddsChangeMessage.class);
    StandardOddsChangeMessage oddsChange = (StandardOddsChangeMessage) result;

    assertThat(oddsChange.eventId()).isEqualTo("ev456");
    assertThat(oddsChange.provider()).isEqualTo(FeedProviderId.PROVIDER_BETA);
    assertThat(oddsChange.market()).isEqualTo("1X2");
    assertThat(oddsChange.odds())
        .containsEntry(Outcome.HOME, new BigDecimal("1.95"))
        .containsEntry(Outcome.DRAW, new BigDecimal("3.2"))
        .containsEntry(Outcome.AWAY, new BigDecimal("4.0"));
  }

  @Test
  void standardize_settlementRequest_mapsToStandardBetSettlementMessage() {
    ProviderBetaSettlementRequest request = new ProviderBetaSettlementRequest("ev456", "away");

    StandardMessage result = mapper.standardize(request);

    assertThat(result).isInstanceOf(StandardBetSettlementMessage.class);
    StandardBetSettlementMessage settlement = (StandardBetSettlementMessage) result;

    assertThat(settlement.eventId()).isEqualTo("ev456");
    assertThat(settlement.provider()).isEqualTo(FeedProviderId.PROVIDER_BETA);
    assertThat(settlement.market()).isEqualTo("1X2");
    assertThat(settlement.outcome()).isEqualTo(Outcome.AWAY);
  }
}
