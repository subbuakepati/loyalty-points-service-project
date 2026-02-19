package com.example.loyalty.service;

import com.example.loyalty.exception.ValidationException;
import com.example.loyalty.model.QuoteRequest;
import com.example.loyalty.model.QuoteResponse;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsServiceTest {

  @Mock
  private FxClient fxClient;

  @Mock
  private PromoClient promoClient;

  private PointsService service;

  @BeforeEach
  void setUp() {
    service = new PointsService(fxClient, promoClient);
  }

  @Test
  void happyPath_calculatesCorrectly() {
    when(fxClient.getRate("USD")).thenReturn(Future.succeededFuture(3.67));
    when(promoClient.getPromo("SUMMER25"))
        .thenReturn(Future.succeededFuture(new PromoResult(25, true)));

    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 1234.50;
    req.currency = "USD";
    req.cabinClass = "ECONOMY";
    req.customerTier = "SILVER";
    req.promoCode = "SUMMER25";

    QuoteResponse res = service.quote(req).result();

    assertThat(res.basePoints).isEqualTo(1234);
    assertThat(res.tierBonus).isEqualTo(185);
    assertThat(res.promoBonus).isEqualTo(308);
    assertThat(res.totalPoints).isEqualTo(1727);
    assertThat(res.effectiveFxRate).isEqualTo(3.67);
    assertThat(res.warnings).containsExactly("PROMO_EXPIRES_SOON");
  }

  @Test
  void validation_invalidFare_throwsValidationException() {
    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 0;
    req.currency = "USD";
    req.cabinClass = "ECONOMY";
    req.customerTier = "SILVER";

    assertThatThrownBy(() -> service.quote(req))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid fare");
  }

  @Test
  void validation_invalidCurrency_throwsValidationException() {
    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 100;
    req.currency = "ZZZ";
    req.cabinClass = "ECONOMY";
    req.customerTier = "SILVER";

    assertThatThrownBy(() -> service.quote(req))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid currency");
  }

  @Test
  void validation_invalidTier_throwsValidationException() {
    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 100;
    req.currency = "USD";
    req.cabinClass = "ECONOMY";
    req.customerTier = "DIAMOND";

    assertThatThrownBy(() -> service.quote(req))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid tier");
  }

  @Test
  void validation_invalidCabin_throwsValidationException() {
    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 100;
    req.currency = "USD";
    req.cabinClass = "PREMIUM";
    req.customerTier = "SILVER";

    assertThatThrownBy(() -> service.quote(req))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid cabin");
  }

  @Test
  void promoFallback_onFailure_returnsZeroBonus() {
    when(fxClient.getRate("USD")).thenReturn(Future.succeededFuture(1.0));
    when(promoClient.getPromo("BADCODE"))
        .thenReturn(Future.failedFuture(new RuntimeException("promo down")));

    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 1000;
    req.currency = "USD";
    req.cabinClass = "ECONOMY";
    req.customerTier = "NONE";
    req.promoCode = "BADCODE";

    QuoteResponse res = service.quote(req).result();

    assertThat(res.promoBonus).isEqualTo(0);
    assertThat(res.warnings).isEmpty();
  }

  @Test
  void pointsCap_enforcedAt50000() {
    when(fxClient.getRate("USD")).thenReturn(Future.succeededFuture(1.0));
    when(promoClient.getPromo("SUMMER25"))
        .thenReturn(Future.succeededFuture(new PromoResult(25, false)));

    QuoteRequest req = new QuoteRequest();
    req.fareAmount = 1_000_000;
    req.currency = "USD";
    req.cabinClass = "BUSINESS";
    req.customerTier = "PLATINUM";
    req.promoCode = "SUMMER25";

    QuoteResponse res = service.quote(req).result();

    assertThat(res.totalPoints).isEqualTo(50_000);
  }

  @Test
  void validation_nullBody_throwsValidationException() {
    assertThatThrownBy(() -> service.quote(null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Missing body");
  }
}
