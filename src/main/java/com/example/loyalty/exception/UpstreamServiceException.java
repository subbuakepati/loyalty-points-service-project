package com.example.loyalty.exception;

public class UpstreamServiceException extends LoyaltyException {
  private final String serviceName;

  public UpstreamServiceException(String serviceName, String message, Throwable cause) {
    super(message, 502, cause);
    this.serviceName = serviceName;
  }

  public String getServiceName() {
    return serviceName;
  }
}
