package com.example.loyalty.exception;

public class ServiceUnavailableException extends LoyaltyException {

  public ServiceUnavailableException(String message) {
    super(message, 503);
  }
}
