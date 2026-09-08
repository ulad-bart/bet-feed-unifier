package com.sporty.provider.beta;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

@WebMvcTest(controllers = ProviderBetaController.class)
@Import(ProviderBetaMapper.class)
class ProviderBetaControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MessagePublisher messagePublisher;

  @Test
  void oddsRequest_returnsAcceptedAndPublishesStandardOddsChangeMessage() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("odds-change.json")))
        .andExpect(status().isAccepted());

    ArgumentCaptor<StandardMessage> captor = ArgumentCaptor.forClass(StandardMessage.class);
    verify(messagePublisher).publish(captor.capture());
    StandardOddsChangeMessage published = (StandardOddsChangeMessage) captor.getValue();

    assertThat(published.eventId()).isEqualTo("ev456");
    assertThat(published.provider()).isEqualTo(FeedProviderId.PROVIDER_BETA);
    assertThat(published.market()).isEqualTo("1X2");
    assertThat(published.odds())
        .containsEntry(Outcome.HOME, new BigDecimal("1.95"))
        .containsEntry(Outcome.DRAW, new BigDecimal("3.2"))
        .containsEntry(Outcome.AWAY, new BigDecimal("4.0"));
    assertThat(published.receivedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
  }

  @Test
  void settlementRequest_returnsAcceptedAndPublishesStandardBetSettlementMessage() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("settlement.json")))
        .andExpect(status().isAccepted());

    ArgumentCaptor<StandardMessage> captor = ArgumentCaptor.forClass(StandardMessage.class);
    verify(messagePublisher).publish(captor.capture());
    StandardBetSettlementMessage published = (StandardBetSettlementMessage) captor.getValue();

    assertThat(published.eventId()).isEqualTo("ev456");
    assertThat(published.provider()).isEqualTo(FeedProviderId.PROVIDER_BETA);
    assertThat(published.market()).isEqualTo("1X2");
    assertThat(published.outcome()).isEqualTo(Outcome.AWAY);
    assertThat(published.receivedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
  }

  @Test
  void missingEventId_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("missing-event-id.json")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void oddsValueNotGreaterThanOne_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("odds-not-greater-than-one.json")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void resultOutsideAllowedValues_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("invalid-result.json")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void unrecognizedType_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("unrecognized-type.json")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void malformedJsonBody_returnsBadRequestAndDoesNotPublish() throws Exception {
    mockMvc.perform(post("/provider-beta/feed")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("malformed.json")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(messagePublisher);
  }

  @Test
  void duplicateSettlementForSameEventId_bothReturnAcceptedAndBothPublish() throws Exception {
    String payload = fixture("settlement.json");

    mockMvc.perform(post("/provider-beta/feed").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());
    mockMvc.perform(post("/provider-beta/feed").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());

    verify(messagePublisher, times(2)).publish(any());
  }

  private static String fixture(String fileName) throws Exception {
    ClassPathResource resource = new ClassPathResource("provider/beta/" + fileName);

    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
  }
}
