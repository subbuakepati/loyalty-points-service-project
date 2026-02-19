package com.example.loyalty.service;

import com.example.loyalty.exception.ValidationException;
import com.example.loyalty.model.*;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class PointsService {
  private static final Logger log = LoggerFactory.getLogger(PointsService.class);
  private final int cap;

  private final FxClient fxClient;
  private final PromoClient promoClient;

  public PointsService(FxClient fxClient, PromoClient promoClient) {
    this(fxClient, promoClient, 50_000);
  }

  public PointsService(FxClient fxClient, PromoClient promoClient, int cap) {
    this.fxClient = fxClient;
    this.promoClient = promoClient;
    this.cap = cap;
  }

  public Future<QuoteResponse> quote(QuoteRequest req) {
    validate(req);
    log.info("Processing quote: currency={}, tier={}, cabin={}, fare={}",
        req.currency, req.customerTier, req.cabinClass, req.fareAmount);

    return fxClient.getRate(req.currency)
            .compose(rate -> {
              log.debug("FX rate retrieved: currency={}, rate={}", req.currency, rate);
              int base = (int) Math.floor(req.fareAmount);

              Tier tier = Tier.valueOf(req.customerTier);
              int tierBonus = (int) Math.floor(base * tier.bonusRate);

              return promoClient.getPromo(req.promoCode)
                      .recover(err -> {
                        log.warn("Promo fallback due to: {}", err.toString());
                        return Future.succeededFuture(new PromoResult(0, false));
                      })
                      .map(promo -> build(base, tierBonus, promo, rate));
            });
  }

  private QuoteResponse build(int base, int tierBonus, PromoResult promo, double rate) {
    int promoBonus = promo.bonusPercent > 0 ? (base * promo.bonusPercent) / 100 : 0;
    int total = Math.min(cap, base + tierBonus + promoBonus);

    if (total == cap) {
      log.info("Points capped at {}", cap);
    }

    QuoteResponse res = new QuoteResponse();
    res.basePoints = base;
    res.tierBonus = tierBonus;
    res.promoBonus = promoBonus;
    res.totalPoints = total;
    res.effectiveFxRate = rate;
    res.warnings = promo.expiresSoon ? List.of("PROMO_EXPIRES_SOON") : List.of();

    log.info("Quote result: base={}, tierBonus={}, promoBonus={}, total={}",
        base, tierBonus, promoBonus, total);
    return res;
  }

  private void validate(QuoteRequest r) {
    if (r == null) throw new ValidationException("Missing body");
    if (r.fareAmount <= 0) throw new ValidationException("Invalid fare");

    if (r.currency == null || !Set.of("USD", "EUR", "INR").contains(r.currency))
      throw new ValidationException("Invalid currency");

    if (r.cabinClass == null || !Set.of("ECONOMY", "BUSINESS", "FIRST").contains(r.cabinClass))
      throw new ValidationException("Invalid cabin");

    if (r.customerTier == null) throw new ValidationException("Invalid tier");
    try { Tier.valueOf(r.customerTier); }
    catch (Exception e) { throw new ValidationException("Invalid tier"); }
  }
}
