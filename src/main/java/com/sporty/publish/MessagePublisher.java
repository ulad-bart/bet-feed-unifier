package com.sporty.publish;

import com.sporty.domain.StandardMessage;

public interface MessagePublisher {
  void publish(StandardMessage message);
}
