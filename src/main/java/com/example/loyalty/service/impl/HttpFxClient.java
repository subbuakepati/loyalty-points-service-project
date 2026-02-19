package com.example.loyalty.service.impl;

import com.example.loyalty.config.ServiceConfig;
import com.example.loyalty.exception.UpstreamServiceException;
import com.example.loyalty.service.FxClient;
import io.vertx.core.*;
import io.vertx.circuitbreaker.*;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpFxClient implements FxClient {
  private static final Logger log = LoggerFactory.getLogger(HttpFxClient.class);

  private final Vertx vertx;
  private final WebClient client;
  private final CircuitBreaker cb;
  private final int port;
  private final String host;
  private final long retryDelayMs;

  public HttpFxClient(Vertx vertx, String host, int port) {
    this(vertx, host, port, 2, 800, 3000, 100);
  }

  public HttpFxClient(Vertx vertx, ServiceConfig config) {
    this(vertx, config.fxHost(), config.fxPort(),
        config.cbMaxFailures(), config.cbTimeoutMs(), config.cbResetTimeoutMs(), config.fxRetryDelayMs());
  }

  private HttpFxClient(Vertx vertx, String host, int port,
                        int maxFailures, long timeoutMs, long resetTimeoutMs, long retryDelayMs) {
    this.vertx = vertx;
    this.client = WebClient.create(vertx);
    this.host = host;
    this.port = port;
    this.retryDelayMs = retryDelayMs;
    this.cb = CircuitBreaker.create("fx-cb", vertx,
            new CircuitBreakerOptions()
                    .setMaxFailures(maxFailures)
                    .setTimeout(timeoutMs)
                    .setResetTimeout(resetTimeoutMs)
    );
    log.info("FX client initialized: host={}, port={}, maxFailures={}, timeout={}ms, resetTimeout={}ms",
        host, port, maxFailures, timeoutMs, resetTimeoutMs);
  }

  @Override
  public Future<Double> getRate(String currency) {
    log.debug("Fetching FX rate for currency={}", currency);
    return attempt(currency, 0);
  }

  private Future<Double> attempt(String currency, int n) {
    return cb.<Double>execute(promise ->
            client.get(port, host, "/fx/" + currency)
                    .send(ar -> {
                      if (ar.succeeded() && ar.result().statusCode() == 200) {
                        promise.complete(ar.result().bodyAsJsonObject().getDouble("rate"));
                      } else {
                        promise.fail(ar.cause() != null ? ar.cause() : new RuntimeException("FX call failed"));
                      }
                    })
    ).recover(err -> {
      if (n == 0) {
        log.warn("FX call failed for currency={}, retrying after {}ms: {}", currency, retryDelayMs, err.getMessage());
        Promise<Double> p = Promise.promise();
        vertx.setTimer(retryDelayMs, t -> attempt(currency, 1).onComplete(p));
        return p.future();
      }
      log.error("FX call failed after retry for currency={}: {}", currency, err.getMessage());
      return Future.failedFuture(
          new UpstreamServiceException("fx-service", "FX rate lookup failed for " + currency, err));
    });
  }
}
