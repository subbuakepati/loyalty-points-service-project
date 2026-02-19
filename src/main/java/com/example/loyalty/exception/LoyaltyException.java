package com.example.loyalty.exception;

public abstract class LoyaltyException extends RuntimeException {
  private final int httpStatus;

  protected LoyaltyException(String message, int httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  protected LoyaltyException(String message, int httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }
}
