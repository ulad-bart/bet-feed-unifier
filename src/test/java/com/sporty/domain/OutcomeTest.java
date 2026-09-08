package com.sporty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutcomeTest {

  @Test
  void code_returnsCanonicalString() {
    assertThat(Outcome.HOME.code()).isEqualTo("1");
    assertThat(Outcome.DRAW.code()).isEqualTo("X");
    assertThat(Outcome.AWAY.code()).isEqualTo("2");
  }

  @Test
  void fromCode_returnsMatchingOutcome() {
    assertThat(Outcome.fromCode("1")).isEqualTo(Outcome.HOME);
    assertThat(Outcome.fromCode("X")).isEqualTo(Outcome.DRAW);
    assertThat(Outcome.fromCode("2")).isEqualTo(Outcome.AWAY);
  }

  @Test
  void fromCode_unknownCode_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> Outcome.fromCode("Y")).isInstanceOf(IllegalArgumentException.class);
  }
}
