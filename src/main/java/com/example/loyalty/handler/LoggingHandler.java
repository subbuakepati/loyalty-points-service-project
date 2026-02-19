package com.example.loyalty.handler;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

public class LoggingHandler {
  private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);
  private static final String CORRELATION_HEADER = "X-Correlation-ID";

  public void handle(RoutingContext ctx) {
    String correlationId = ctx.request().getHeader(CORRELATION_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    MDC.put("correlationId", correlationId);
    ctx.put("correlationId", correlationId);
    ctx.response().putHeader(CORRELATION_HEADER, correlationId);

    long start = System.currentTimeMillis();
    String method = ctx.request().method().name();
    String path = ctx.request().path();

    log.info("Request started: {} {}", method, path);

    ctx.addEndHandler(v -> {
      long duration = System.currentTimeMillis() - start;
      int status = ctx.response().getStatusCode();
      log.info("Request completed: {} {} status={} duration={}ms", method, path, status, duration);
      MDC.remove("correlationId");
    });

    ctx.next();
  }
}
