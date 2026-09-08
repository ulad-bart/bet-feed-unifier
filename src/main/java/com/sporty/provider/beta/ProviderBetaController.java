package com.sporty.provider.beta;

import com.sporty.domain.StandardMessage;
import com.sporty.publish.MessagePublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderBetaController {

  private final ProviderBetaMapper mapper;
  private final MessagePublisher messagePublisher;

  public ProviderBetaController(ProviderBetaMapper mapper, MessagePublisher messagePublisher) {
    this.mapper = mapper;
    this.messagePublisher = messagePublisher;
  }

  @PostMapping("/provider-beta/feed")
  public ResponseEntity<Void> ingest(@Valid @RequestBody ProviderBetaMessage message) {
    StandardMessage standardMessage = mapper.standardize(message);
    messagePublisher.publish(standardMessage);
    return ResponseEntity.accepted().build();
  }
}
