package com.example.loyalty.service;

public class PromoResult {
    public final int bonusPercent;
    public final boolean expiresSoon;

    public PromoResult(int bonusPercent, boolean expiresSoon) {
        this.bonusPercent = bonusPercent;
        this.expiresSoon = expiresSoon;
    }
}
