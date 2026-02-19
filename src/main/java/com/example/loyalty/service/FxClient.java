package com.example.loyalty.service;

import io.vertx.core.Future;

public interface FxClient {
  Future<Double> getRate(String currency);
}
