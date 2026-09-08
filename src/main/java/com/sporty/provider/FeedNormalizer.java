package com.sporty.provider;

import com.sporty.domain.StandardMessage;

public interface FeedNormalizer<T> {
  StandardMessage normalize(T raw);
}
