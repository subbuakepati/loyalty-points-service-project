package com.example.loyalty.model;

import java.util.List;

public class QuoteResponse {
  public int basePoints;
  public int tierBonus;
  public int promoBonus;
  public int totalPoints;
  public double effectiveFxRate;
  public List<String> warnings;
}
