package com.example.loyalty.service.impl;

import com.example.loyalty.config.ServiceConfig;
import com.example.loyalty.service.PromoClient;
import com.example.loyalty.service.PromoResult;
import io.vertx.core.*;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpPromoClient implements PromoClient {
  private static final Logger log = LoggerFactory.getLogger(HttpPromoClient.class);

  private final Vertx vertx;
  private final WebClient client;
  private final int port;
  private final String host;
  private final long timeoutMs;

  public HttpPromoClient(Vertx vertx, String host, int port, long timeoutMs) {
    this.vertx = vertx;
    this.client = WebClient.create(vertx);
    this.host = host;
    this.port = port;
    this.timeoutMs = timeoutMs;
    log.info("Promo client initialized: host={}, port={}, timeout={}ms", host, port, timeoutMs);
  }

  public HttpPromoClient(Vertx vertx, ServiceConfig config) {
    this(vertx, config.promoHost(), config.promoPort(), config.promoTimeoutMs());
  }

  @Override
  public Future<PromoResult> getPromo(String code) {

    if (code == null || code.isBlank()) {
      log.debug("No promo code provided, skipping promo lookup");
      return Future.succeededFuture(new PromoResult(0, false));
    }

    log.debug("Looking up promo code={}", code);
    Promise<PromoResult> promise = Promise.promise();

    long timerId = vertx.setTimer(timeoutMs, id -> {
      if (!promise.future().isComplete()) {
        log.warn("Promo lookup timed out after {}ms for code={}, falling back to zero bonus", timeoutMs, code);
        promise.complete(new PromoResult(0, false));
      }
    });

    client.get(port, host, "/promo/" + code)
            .send(ar -> {
              vertx.cancelTimer(timerId);

              if (promise.future().isComplete()) {
                return;
              }

              if (ar.succeeded() && ar.result().statusCode() == 200) {
                var body = ar.result().bodyAsJsonObject();
                PromoResult result = new PromoResult(
                        body.getInteger("bonusPercent", 0),
                        body.getBoolean("expiresSoon", false)
                );
                log.debug("Promo result for code={}: bonusPercent={}, expiresSoon={}",
                    code, result.bonusPercent, result.expiresSoon);
                promise.complete(result);
              } else {
                log.warn("Promo lookup failed for code={}, falling back to zero bonus", code);
                promise.complete(new PromoResult(0, false));
              }
            });

    return promise.future();
  }
}
