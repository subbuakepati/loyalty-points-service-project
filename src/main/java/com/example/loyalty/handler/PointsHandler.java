package com.example.loyalty.handler;

import com.example.loyalty.exception.LoyaltyException;
import com.example.loyalty.exception.ValidationException;
import com.example.loyalty.model.ErrorResponse;
import com.example.loyalty.model.QuoteRequest;
import com.example.loyalty.service.PointsService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointsHandler {
  private static final Logger log = LoggerFactory.getLogger(PointsHandler.class);
  private final PointsService service;

  public PointsHandler(PointsService service) {
    this.service = service;
  }

  public void handle(RoutingContext ctx) {
    try {
      QuoteRequest req = ctx.body().asPojo(QuoteRequest.class);
      service.quote(req)
        .onSuccess(res -> ctx.response()
          .putHeader("Content-Type", "application/json")
          .putHeader("Cache-Control", "no-store")
          .setStatusCode(200)
          .end(Json.encode(res)))
        .onFailure(err -> handleError(ctx, err));
    } catch (Exception e) {
      handleError(ctx, e);
    }
  }

  private void handleError(RoutingContext ctx, Throwable err) {
    int status;
    String error;

    if (err instanceof ValidationException || err instanceof IllegalArgumentException) {
      status = 400;
      error = "Validation Error";
    } else if (err instanceof LoyaltyException le) {
      status = le.getHttpStatus();
      error = switch (status) {
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        default -> "Server Error";
      };
    } else {
      status = 500;
      error = "Internal Server Error";
      log.error("Unexpected error processing request", err);
    }

    String correlationId = ctx.get("correlationId");
    String path = ctx.request().path();
    ErrorResponse errorResponse = ErrorResponse.of(error, err.getMessage(), status, path, correlationId);

    ctx.response()
      .setStatusCode(status)
      .putHeader("Content-Type", "application/json")
      .end(Json.encode(errorResponse));
  }
}
