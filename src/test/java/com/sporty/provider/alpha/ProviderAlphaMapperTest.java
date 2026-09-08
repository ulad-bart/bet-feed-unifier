package com.sporty.provider.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import com.sporty.domain.StandardMessage;
import com.sporty.domain.StandardOddsChangeMessage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProviderAlphaMapperTest {

  private final ProviderAlphaMapper mapper = new ProviderAlphaMapper();

  @Test
  void standardize_oddsChangeRequest_mapsToStandardOddsChangeMessage() {
    ProviderAlphaOddsChangeRequest request = new ProviderAlphaOddsChangeRequest(
        "ev123", new ProviderAlphaOdds(new BigDecimal("2.0"), new BigDecimal("3.1"), new BigDecimal("3.8")));

    StandardMessage result = mapper.standardize(request);

    assertThat(result).isInstanceOf(StandardOddsChangeMessage.class);
    StandardOddsChangeMessage oddsChange = (StandardOddsChangeMessage) result;

    assertThat(oddsChange.eventId()).isEqualTo("ev123");
    assertThat(oddsChange.provider()).isEqualTo(FeedProviderId.PROVIDER_ALPHA);
    assertThat(oddsChange.market()).isEqualTo("1X2");
    assertThat(oddsChange.odds())
        .containsEntry(Outcome.HOME, new BigDecimal("2.0"))
        .containsEntry(Outcome.DRAW, new BigDecimal("3.1"))
        .containsEntry(Outcome.AWAY, new BigDecimal("3.8"));
  }

  @Test
  void standardize_settlementRequest_mapsToStandardBetSettlementMessage() {
    ProviderAlphaSettlementRequest request = new ProviderAlphaSettlementRequest("ev123", "1");

    StandardMessage result = mapper.standardize(request);

    assertThat(result).isInstanceOf(StandardBetSettlementMessage.class);
    StandardBetSettlementMessage settlement = (StandardBetSettlementMessage) result;

    assertThat(settlement.eventId()).isEqualTo("ev123");
    assertThat(settlement.provider()).isEqualTo(FeedProviderId.PROVIDER_ALPHA);
    assertThat(settlement.market()).isEqualTo("1X2");
    assertThat(settlement.outcome()).isEqualTo(Outcome.HOME);
  }
}
