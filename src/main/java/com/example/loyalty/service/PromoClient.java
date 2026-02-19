package com.example.loyalty.service;

import io.vertx.core.Future;

public interface PromoClient {

  Future<PromoResult> getPromo(String code);
}
