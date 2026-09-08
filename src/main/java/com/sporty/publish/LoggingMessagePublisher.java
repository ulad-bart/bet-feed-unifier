package com.sporty.publish;

import com.sporty.domain.StandardMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class LoggingMessagePublisher implements MessagePublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingMessagePublisher.class);

  private final ObjectMapper objectMapper;

  public LoggingMessagePublisher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(StandardMessage message) {
    log.info("Publishing standardized message: {}", objectMapper.writeValueAsString(message));
  }
}
