package com.sporty.publish;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sporty.domain.FeedProviderId;
import com.sporty.domain.Outcome;
import com.sporty.domain.StandardBetSettlementMessage;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LoggingMessagePublisherTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final LoggingMessagePublisher publisher = new LoggingMessagePublisher(OBJECT_MAPPER);
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(LoggingMessagePublisher.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void publish_logsValidJsonMatchingStandardizedSchema() throws Exception {
    StandardBetSettlementMessage message = new StandardBetSettlementMessage(
        "ev123", FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2", Outcome.HOME);

    publisher.publish(message);

    assertThat(appender.list).hasSize(1);
    String logMessage = appender.list.getFirst().getFormattedMessage();
    String json = logMessage.substring(logMessage.indexOf('{'));
    JsonNode node = OBJECT_MAPPER.readTree(json);

    assertThat(node.get("eventId").asString()).isEqualTo("ev123");
    assertThat(node.get("messageType").asString()).isEqualTo("BET_SETTLEMENT");
    assertThat(node.get("outcome").asString()).isEqualTo("1");
  }
}
