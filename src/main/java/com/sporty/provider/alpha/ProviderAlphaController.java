package com.sporty.provider.alpha;

import com.sporty.domain.StandardMessage;
import com.sporty.publish.MessagePublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderAlphaController {

  private final ProviderAlphaMapper mapper;
  private final MessagePublisher messagePublisher;

  public ProviderAlphaController(ProviderAlphaMapper mapper, MessagePublisher messagePublisher) {
    this.mapper = mapper;
    this.messagePublisher = messagePublisher;
  }

  @PostMapping("/provider-alpha/feed")
  public ResponseEntity<Void> ingest(@Valid @RequestBody ProviderAlphaMessage message) {
    StandardMessage standardMessage = mapper.standardize(message);
    messagePublisher.publish(standardMessage);
    return ResponseEntity.accepted().build();
  }
}
