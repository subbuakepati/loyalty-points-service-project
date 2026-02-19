package com.example.loyalty.config;

public record ServiceConfig(
    String fxHost,
    int fxPort,
    String promoHost,
    int promoPort,
    long promoTimeoutMs,
    int httpPort,
    int pointsCap,
    int cbMaxFailures,
    long cbTimeoutMs,
    long cbResetTimeoutMs,
    long fxRetryDelayMs
) {
  public static ServiceConfig fromSystemProperties() {
    return new ServiceConfig(
        System.getProperty("fx.host", "localhost"),
        Integer.parseInt(System.getProperty("fx.port", "8081")),
        System.getProperty("promo.host", "localhost"),
        Integer.parseInt(System.getProperty("promo.port", "8082")),
        Long.parseLong(System.getProperty("promo.timeoutMs", "300")),
        Integer.parseInt(System.getProperty("http.port", "8080")),
        Integer.parseInt(System.getProperty("points.cap", "50000")),
        Integer.parseInt(System.getProperty("cb.maxFailures", "2")),
        Long.parseLong(System.getProperty("cb.timeoutMs", "800")),
        Long.parseLong(System.getProperty("cb.resetTimeoutMs", "3000")),
        Long.parseLong(System.getProperty("fx.retryDelayMs", "100"))
    );
  }
}
