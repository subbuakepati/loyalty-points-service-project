package com.example.loyalty.model;

import java.time.Instant;

public record ErrorResponse(
    String error,
    String message,
    int status,
    String path,
    String timestamp,
    String correlationId
) {
  public static ErrorResponse of(String error, String message, int status, String path, String correlationId) {
    return new ErrorResponse(error, message, status, path, Instant.now().toString(), correlationId);
  }
}
