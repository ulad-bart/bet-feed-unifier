package com.sporty.provider;

import com.sporty.domain.StandardMessage;

public interface FeedProvider<T> {
  StandardMessage standardize(T raw);
}
