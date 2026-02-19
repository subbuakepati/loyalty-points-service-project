package com.example.loyalty;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import com.github.tomakehurst.wiremock.WireMockServer;



@ExtendWith(VertxExtension.class)
@Epic("Loyalty Points Service")
@Feature("Points Quote")
class PointsQuoteComponentTest {

  static WireMockServer fx;
  static WireMockServer promo;

    @BeforeAll
    static void startMocks() {

        fx = new WireMockServer(0);
        promo = new WireMockServer(0);
        fx.start();
        promo.start();

        // default stubs
        fx.stubFor(get(urlEqualTo("/fx/USD"))
                .willReturn(okJson("{\"rate\":3.67}")));

        promo.stubFor(get(urlEqualTo("/promo/SUMMER25"))
                .willReturn(okJson("{\"bonusPercent\":25,\"expiresSoon\":true}")));
    }

    @AfterAll
    static void stopMocks() {
        fx.stop();
        promo.stop();
    }


    @BeforeEach
  void setSysProps() {
    // Reset stubs to defaults before each test to avoid cross-test contamination
    fx.resetAll();
    promo.resetAll();
    fx.stubFor(get(urlEqualTo("/fx/USD"))
            .willReturn(okJson("{\"rate\":3.67}")));
    promo.stubFor(get(urlEqualTo("/promo/SUMMER25"))
            .willReturn(okJson("{\"bonusPercent\":25,\"expiresSoon\":true}")));

    System.setProperty("fx.host", "localhost");
    System.setProperty("fx.port", String.valueOf(fx.port()));
    System.setProperty("promo.host", "localhost");
    System.setProperty("promo.port", String.valueOf(promo.port()));
    System.setProperty("promo.timeoutMs", "200");
    System.setProperty("http.port", "0");
  }

  @Test
  @Severity(SeverityLevel.CRITICAL)
  @Description("Verifies the full happy-path quote calculation with FX, tier bonus, and promo")
  void happyPath_matchesSampleNumbers(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), ar -> {
      assertThat(ar.succeeded()).isTrue();
      int port = Integer.parseInt(System.getProperty("http.actualPort"));

      WebClient client = WebClient.create(vertx);
      client.post(port, "localhost", "/v1/points/quote")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(new JsonObject()
          .put("fareAmount", 1234.50)
          .put("currency", "USD")
          .put("cabinClass", "ECONOMY")
          .put("customerTier", "SILVER")
          .put("promoCode", "SUMMER25"), res -> ctx.verify(() -> {
            assertThat(res.succeeded()).isTrue();
            assertThat(res.result().statusCode()).isEqualTo(200);
            assertThat(res.result().getHeader("Content-Type")).contains("application/json");
            assertThat(res.result().getHeader("Cache-Control")).isEqualTo("no-store");

            JsonObject body = res.result().bodyAsJsonObject();
            assertThat(body.getInteger("basePoints")).isEqualTo(1234);
            assertThat(body.getInteger("tierBonus")).isEqualTo(185);
            assertThat(body.getInteger("promoBonus")).isEqualTo(308);
            assertThat(body.getInteger("totalPoints")).isEqualTo(1727);
            assertThat(body.getDouble("effectiveFxRate")).isEqualTo(3.67);
            assertThat(body.getJsonArray("warnings")).contains("PROMO_EXPIRES_SOON");

            ctx.completeNow();
          }));
    });
  }

  @Test
  @Severity(SeverityLevel.NORMAL)
  @Description("Rejects a quote request when fare amount is zero or negative")
  void validation_invalidFare_rejected(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), ar -> {
      int port = Integer.parseInt(System.getProperty("http.actualPort"));
      WebClient.create(vertx)
        .post(port, "localhost", "/v1/points/quote")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(new JsonObject()
          .put("fareAmount", 0)
          .put("currency", "USD")
          .put("cabinClass", "ECONOMY")
          .put("customerTier", "SILVER"), res -> ctx.verify(() -> {
            assertThat(res.result().statusCode()).isEqualTo(400);
            assertThat(res.result().getHeader("Content-Type")).contains("application/json");
            assertThat(res.result().bodyAsJsonObject().getString("message")).contains("Invalid fare");
            ctx.completeNow();
          }));
    });
  }

  @Test
  @Severity(SeverityLevel.NORMAL)
  @Description("Rejects a quote request when currency code is unsupported")
  void validation_invalidCurrency_rejected(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), ar -> {
      int port = Integer.parseInt(System.getProperty("http.actualPort"));
      WebClient.create(vertx)
        .post(port, "localhost", "/v1/points/quote")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(new JsonObject()
          .put("fareAmount", 10)
          .put("currency", "ZZZ")
          .put("cabinClass", "ECONOMY")
          .put("customerTier", "SILVER"), res -> ctx.verify(() -> {
            assertThat(res.result().statusCode()).isEqualTo(400);
            assertThat(res.result().getHeader("Content-Type")).contains("application/json");
            assertThat(res.result().bodyAsJsonObject().getString("message")).contains("Invalid currency");
            ctx.completeNow();
          }));
    });
  }

  @Test
  @Severity(SeverityLevel.CRITICAL)
  @Description("Verifies total points are capped at 50,000")
  void cap_totalPoints_cappedAt50k(Vertx vertx, VertxTestContext ctx) {
    // BIG fare to trigger cap (base=floor(fareAmount))
    vertx.deployVerticle(new MainVerticle(), ar -> {
      int port = Integer.parseInt(System.getProperty("http.actualPort"));
      WebClient.create(vertx)
        .post(port, "localhost", "/v1/points/quote")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(new JsonObject()
          .put("fareAmount", 1000000)
          .put("currency", "USD")
          .put("cabinClass", "BUSINESS")
          .put("customerTier", "PLATINUM")
          .put("promoCode", "SUMMER25"), res -> ctx.verify(() -> {
            JsonObject body = res.result().bodyAsJsonObject();
            assertThat(body.getInteger("totalPoints")).isEqualTo(50000);
            ctx.completeNow();
          }));
    });
  }

  @Test
  @Severity(SeverityLevel.CRITICAL)
  @Description("Verifies promo service timeout triggers graceful fallback with zero promo bonus")
  void resilience_promoTimeout_fallsBack(Vertx vertx, VertxTestContext ctx) {
      // Delay promo beyond timeout
      promo.resetAll();
      promo.stubFor(get(urlEqualTo("/promo/SUMMER25"))
              .willReturn(aResponse()
                      .withFixedDelay(800)
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("""
        {"bonusPercent":25,"expiresSoon":true}
      """)));

      vertx.deployVerticle(new MainVerticle(), ar -> {
          assertThat(ar.succeeded()).isTrue();

          int port = Integer.parseInt(System.getProperty("http.actualPort"));
          WebClient.create(vertx)
                  .post(port, "localhost", "/v1/points/quote")
                  .putHeader("Content-Type", "application/json")
                  .sendJsonObject(new JsonObject()
                                  .put("fareAmount", 1234.50)
                                  .put("currency", "USD")
                                  .put("cabinClass", "ECONOMY")
                                  .put("customerTier", "SILVER")
                                  .put("promoCode", "SUMMER25"),
                          res -> ctx.verify(() -> {
                              assertThat(res.succeeded()).isTrue();
                              assertThat(res.result().statusCode()).isEqualTo(200);
                              assertThat(res.result().getHeader("Content-Type")).contains("application/json");

                              JsonObject body = res.result().bodyAsJsonObject();

                              // promo fallback => promoBonus = 0 and no warning
                              assertThat(body.getInteger("promoBonus")).isEqualTo(0);
                              assertThat(body.getJsonArray("warnings")).isEmpty();

                              ctx.completeNow();
                          }));
      });
  }


  @Test
  @Severity(SeverityLevel.CRITICAL)
  @Description("Verifies FX client retries on failure and succeeds on second attempt")
  void resilience_fxRetry_succeedsOnSecondAttempt(Vertx vertx, VertxTestContext ctx) {
    fx.resetAll();
    // first call fails, second succeeds
    fx.stubFor(get(urlEqualTo("/fx/USD"))
      .inScenario("fx")
      .whenScenarioStateIs(STARTED)
      .willReturn(serverError())
      .willSetStateTo("second"));

    fx.stubFor(get(urlEqualTo("/fx/USD"))
      .inScenario("fx")
      .whenScenarioStateIs("second")
            .willReturn(okJson("{\"rate\":3.67}")));


    vertx.deployVerticle(new MainVerticle(), ar -> {
      int port = Integer.parseInt(System.getProperty("http.actualPort"));
      WebClient.create(vertx)
        .post(port, "localhost", "/v1/points/quote")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(new JsonObject()
          .put("fareAmount", 1234.50)
          .put("currency", "USD")
          .put("cabinClass", "ECONOMY")
          .put("customerTier", "SILVER")
          .put("promoCode", "SUMMER25"), res -> ctx.verify(() -> {
            assertThat(res.result().statusCode()).isEqualTo(200);
            assertThat(res.result().bodyAsJsonObject().getDouble("effectiveFxRate")).isEqualTo(3.67);
            fx.verify(2, getRequestedFor(urlEqualTo("/fx/USD")));
            ctx.completeNow();
          }));
    });
  }
}
