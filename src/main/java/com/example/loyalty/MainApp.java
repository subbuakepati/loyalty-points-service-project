package com.example.loyalty;

import io.vertx.core.Vertx;

public final class MainApp {
  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new MainVerticle());
  }
}
