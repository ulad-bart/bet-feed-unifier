package com.sporty.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StandardMessageJacksonTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void oddsChangeMessage_serializesMessageTypeAsOddsChange() throws Exception {
    StandardOddsChangeMessage message = new StandardOddsChangeMessage(
        "ev123", FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2",
        Map.of(Outcome.HOME, new BigDecimal("2.0")));

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

    assertThat(json.get("messageType").asText()).isEqualTo("ODDS_CHANGE");
  }

  @Test
  void betSettlementMessage_serializesMessageTypeAsBetSettlement() throws Exception {
    StandardBetSettlementMessage message = new StandardBetSettlementMessage(
        "ev123", FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2", Outcome.HOME);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

    assertThat(json.get("messageType").asText()).isEqualTo("BET_SETTLEMENT");
  }

  @Test
  void oddsChangeMessage_serializesOddsMapWithCanonicalStringKeys() throws Exception {
    StandardOddsChangeMessage message = new StandardOddsChangeMessage(
        "ev123", FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2",
        Map.of(Outcome.HOME, new BigDecimal("2.0"), Outcome.DRAW, new BigDecimal("3.1"), Outcome.AWAY, new BigDecimal("3.8")));

    JsonNode odds = objectMapper.readTree(objectMapper.writeValueAsString(message)).get("odds");

    assertThat(odds.has("1")).isTrue();
    assertThat(odds.has("X")).isTrue();
    assertThat(odds.has("2")).isTrue();
    assertThat(odds.get("1").decimalValue()).isEqualByComparingTo("2.0");
    assertThat(odds.get("X").decimalValue()).isEqualByComparingTo("3.1");
    assertThat(odds.get("2").decimalValue()).isEqualByComparingTo("3.8");
  }

  @Test
  void betSettlementMessage_serializesOutcomeAsCanonicalString() throws Exception {
    StandardBetSettlementMessage message = new StandardBetSettlementMessage(
        "ev123", FeedProviderId.PROVIDER_BETA, Instant.now(), "1X2", Outcome.AWAY);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

    assertThat(json.get("outcome").asText()).isEqualTo("2");
  }
}
