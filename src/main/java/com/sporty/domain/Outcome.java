package com.sporty.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Outcome {
  HOME("1"),
  DRAW("X"),
  AWAY("2");

  private final String code;

  Outcome(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public static Outcome fromCode(String code) {
    for (Outcome outcome : values()) {
      if (outcome.code.equals(code)) {
        return outcome;
      }
    }
    throw new IllegalArgumentException("Unknown outcome code: " + code);
  }
}
