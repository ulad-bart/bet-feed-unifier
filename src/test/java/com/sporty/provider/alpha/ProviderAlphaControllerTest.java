package com.sporty.provider.alpha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import com.sporty.domain.StandardMessage;
import com.sporty.domain.StandardOddsChangeMessage;
import com.sporty.publish.MessagePublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProviderAlphaController.class)
@Import(ProviderAlphaMapper.class)
class ProviderAlphaControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MessagePublisher messagePublisher;

  @Test
  void oddsChangeRequest_returnsAcceptedAndPublishesStandardOddsChangeMessage() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "odds_update",
                  "event_id": "ev123",
                  "values": { "1": 2.0, "X": 3.1, "2": 3.8 }
                }
                """))
        .andExpect(status().isAccepted());

    ArgumentCaptor<StandardMessage> captor = ArgumentCaptor.forClass(StandardMessage.class);
    verify(messagePublisher).publish(captor.capture());
    StandardOddsChangeMessage published = (StandardOddsChangeMessage) captor.getValue();
    assertThat(published.eventId()).isEqualTo("ev123");
    assertThat(published.provider()).isEqualTo(FeedProviderId.PROVIDER_ALPHA);
    assertThat(published.market()).isEqualTo("1X2");
    assertThat(published.odds())
        .containsEntry(Outcome.HOME, new BigDecimal("2.0"))
        .containsEntry(Outcome.DRAW, new BigDecimal("3.1"))
        .containsEntry(Outcome.AWAY, new BigDecimal("3.8"));
    assertThat(published.receivedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
  }

  @Test
  void settlementRequest_returnsAcceptedAndPublishesStandardBetSettlementMessage() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "settlement",
                  "event_id": "ev123",
                  "outcome": "1"
                }
                """))
        .andExpect(status().isAccepted());

    ArgumentCaptor<StandardMessage> captor = ArgumentCaptor.forClass(StandardMessage.class);
    verify(messagePublisher).publish(captor.capture());
    StandardBetSettlementMessage published = (StandardBetSettlementMessage) captor.getValue();
    assertThat(published.eventId()).isEqualTo("ev123");
    assertThat(published.provider()).isEqualTo(FeedProviderId.PROVIDER_ALPHA);
    assertThat(published.market()).isEqualTo("1X2");
    assertThat(published.outcome()).isEqualTo(Outcome.HOME);
    assertThat(published.receivedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
  }

  @Test
  void missingEventId_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "odds_update",
                  "values": { "1": 2.0, "X": 3.1, "2": 3.8 }
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void oddsValueNotGreaterThanOne_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "odds_update",
                  "event_id": "ev123",
                  "values": { "1": 1.0, "X": 3.1, "2": 3.8 }
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void outcomeOutsideAllowedValues_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "settlement",
                  "event_id": "ev123",
                  "outcome": "3"
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void unrecognizedMsgType_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "msg_type": "cancellation",
                  "event_id": "ev123"
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void malformedJsonBody_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-alpha/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void duplicateSettlementForSameEventId_bothReturnAcceptedAndBothPublish() throws Exception {
    String payload = """
        {
          "msg_type": "settlement",
          "event_id": "ev123",
          "outcome": "1"
        }
        """;

    mockMvc.perform(post("/provider-alpha/feed").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());
    mockMvc.perform(post("/provider-alpha/feed").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());

    verify(messagePublisher, times(2)).publish(any());
  }
}
