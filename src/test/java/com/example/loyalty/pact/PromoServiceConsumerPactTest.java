package com.example.loyalty.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.loyalty.service.PromoResult;
import com.example.loyalty.service.impl.HttpPromoClient;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "promo-service", port = "0") // random port
@Epic("Loyalty Points Service")
@Feature("Promo Service Contract")
class PromoServiceConsumerPactTest {

    private static Vertx vertx;

    @BeforeAll
    static void setup() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void teardown() {
        vertx.close();
    }

    @Pact(consumer = "loyalty-points-service")
    public V4Pact promoHappyPath(PactDslWithProvider builder) {
        PactDslJsonBody body = new PactDslJsonBody()
                .integerType("bonusPercent", 25)
                .booleanType("expiresSoon", true);

        return builder
                .given("Promo code SUMMER25 exists and is near expiry")
                .uponReceiving("GET promo for SUMMER25")
                .path("/promo/SUMMER25")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(body)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "promoHappyPath")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verifies promo client correctly parses promo response per consumer contract")
    void promoClient_parsesPromo(MockServer mockServer) {
        HttpPromoClient client = new HttpPromoClient(
                vertx, "localhost", mockServer.getPort(), 300
        );

        PromoResult res = client.getPromo("SUMMER25")
                .toCompletionStage().toCompletableFuture().join();

        assertThat(res.bonusPercent).isEqualTo(25);
        assertThat(res.expiresSoon).isTrue();
    }
}
