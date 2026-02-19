package com.example.loyalty.exception;

public class ValidationException extends LoyaltyException {

  public ValidationException(String message) {
    super(message, 400);
  }
}
