
# Loyalty Points Quote Service (Vert.x Java)

## Overview
This project implements a small HTTP service that calculates loyalty points for a flight booking request.

The focus of this assessment is:

- Clean service architecture
- Resilient integration with external dependencies
- Component-level testing using real HTTP interactions
- Observability and documentation suitable for production-style services

---

## Endpoint

### POST `/v1/points/quote`

### Request example122
```json
{
  "fareAmount": 1234.50,
  "currency": "USD",
  "cabinClass": "ECONOMY",
  "customerTier": "SILVER",
  "promoCode": "SUMMER25"
}
```

### Response example (aligned to assessment sample)
```json
{
  "basePoints": 1234,
  "tierBonus": 185,
  "promoBonus": 308,
  "totalPoints": 1727,
  "effectiveFxRate": 3.67,
  "warnings": ["PROMO_EXPIRES_SOON"]
}
```

### Note on sample alignment
The assessment sample keeps `basePoints` aligned directly to the fare amount while still returning an FX rate.

To match the sample exactly:
- `basePoints = floor(fareAmount)`
- FX service is still invoked to produce `effectiveFxRate`
- This allows resilience paths and external integrations to be tested while keeping output aligned with the provided example.

---

## Architecture

```
HTTP Route
     |
     v
PointsService
   |        \
   v         v
FxClient   PromoClient
```

### Design principles
- Business logic isolated in `PointsService`
- External integrations behind interfaces
- Constructor-based dependency injection
- Asynchronous execution using Vert.x Futures
- HTTP layer contains no business logic

This enables:
- Easy stubbing in component tests
- Clear separation of concerns
- Deterministic testing of resilience scenarios

---

## Business Rules Implemented

- Base points derived from fare amount
- Tier bonus:
  - NONE = 0%
  - SILVER = 15%
  - GOLD = 30%
  - PLATINUM = 50%
- Promo bonus retrieved from external promo service
- Warning added when promo is near expiry
- Total points capped at **50,000**
- Validation:
  - Fare must be > 0
  - Supported currencies only
  - Valid cabin class required

---

## Resilience Strategy

### FX Service
- Wrapped in Vert.x CircuitBreaker
- Retries once on failure
- Protects against transient downstream failures

### Promo Service
- Timeout-based protection
- Failure results in graceful fallback (no promo bonus)
- Booking flow continues without failing the request

This mirrors real-world airline loyalty behaviour where promo failures must not block bookings.

---

## Health Endpoints

```
GET /health/live   → OK
GET /health/ready  → READY
```

---

## Running the Service

```bash
mvn clean test
java -jar target/loyalty-points-service-1.0.0.jar
```
---

## Component Testing Strategy

Component tests run the full Vert.x application and verify behaviour via HTTP.

### Test setup
- Vert.x starts on a **random port**
- FX and Promo services are stubbed using WireMock
- No internal mocks are used

### Covered scenarios

| Scenario | Verified |
|---|---|
| Happy path | ✅ |
| Validation errors | ✅ |
| Tier calculation | ✅ |
| Promo expiry warning | ✅ |
| Points cap (50k) | ✅ |
| FX retry success | ✅ |
| Promo timeout fallback | ✅ |

Tests assert:
- HTTP status codes
- Response headers
- JSON body content
- Downstream interaction counts

---

## Pact Consumer Tests (Bonus)

Consumer-driven contract tests verify the expected contract with the Promo service.

Generated pact files:
```
target/pacts/
```

These ensure provider compatibility without requiring a running provider service.

---

## Test Report & Coverage

Run tests:

```bash
mvn clean test
```

Generate and open Allure report in browser:

```bash
allure serve target/allure-results
```

Alternatively, using the Maven plugin:

```bash
mvn allure:report
```

This generates the report in `target/allure-report`.

Coverage requirement:
- Branch coverage ≥ 80%
- Build fails if threshold is not met

---

## Evidence Screenshots

See:

```
ScreenRecordingComponenttests.mov
ComponenetTestReport.png
```

Screenshots include:
- Test execution summary
- Pact output evidence

---

## Summary

This implementation demonstrates:

- Clean Vert.x service architecture
- Resilient external integrations
- Deterministic component testing
- Contract testing via Pact
- Production-style observability and documentation
