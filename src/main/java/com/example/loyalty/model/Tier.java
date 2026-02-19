package com.example.loyalty.model;

public enum Tier {
  NONE(0.0),
  SILVER(0.15),
  GOLD(0.30),
  PLATINUM(0.50);

  public final double bonusRate;

  Tier(double bonusRate) {
    this.bonusRate = bonusRate;
  }
}
