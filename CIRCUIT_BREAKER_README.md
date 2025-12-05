# ⚡ Circuit Breaker Pattern with Resilience4j

Complete guide to implementing Circuit Breaker pattern in Spring Boot using Resilience4j. Protect your services from cascading failures and provide graceful degradation.

## 📋 Table of Contents

- [Overview](#overview)
- [When to Use Circuit Breaker](#when-to-use-circuit-breaker)
- [Circuit Breaker States](#circuit-breaker-states)
- [Implementation](#implementation)
- [Configuration](#configuration)
- [Combining with Retry](#combining-with-retry)
- [Monitoring](#monitoring)
- [Best Practices](#best-practices)
- [Common Pitfalls](#common-pitfalls)

---

## Overview

Circuit Breaker acts like an electrical circuit breaker - it monitors failures and "opens" to prevent further calls to a failing service, allowing it time to recover.

### Key Concepts

- **States**: CLOSED (normal), OPEN (blocking), HALF_OPEN (testing)
- **Sliding Window**: Tracks recent calls to calculate failure rate
- **Failure Threshold**: Percentage that triggers circuit opening
- **Wait Duration**: Time circuit stays open before testing recovery
- **Fallback**: Alternative response when circuit is open

---

## When to Use Circuit Breaker

### ✅ Good Use Cases

- Protecting against downstream service failures
- Preventing cascading failures across microservices
- Failing fast when service is known to be down
- Giving failing services time to recover
- Preventing resource exhaustion from repeated failed calls

### ❌ Bad Use Cases

- Single isolated service with no dependencies
- Operations that must always be attempted
- When you need every request to reach the service
- Short-lived applications or scripts
- Services with SLA requirements that prohibit blocking

---

## Circuit Breaker States

The Circuit Breaker has **3 states** that work like a traffic light:

### 1️⃣ CLOSED 🟢 (Normal Operation)

**Meaning**: Circuit is functioning normally

- ✅ All requests pass through to the service
- 📊 Circuit Breaker monitors and counts failures
- 🎯 Calculates failure rate in sliding window
- ⚠️ If failure rate ≥ threshold → Opens circuit

**Example**:
```
State: CLOSED 🟢
Calls: [✅ ✅ ❌ ✅]
Failure rate: 25% (below 50% threshold)
Action: Stay CLOSED
```

---

### 2️⃣ OPEN 🔴 (Blocking)

**Meaning**: Circuit is open - service has too many failures

- ❌ NO requests reach the service
- ⚡ Calls go directly to fallback (immediate response)
- ⏰ Waits for `wait-duration-in-open-state`
- 🔄 After wait duration → automatically transitions to HALF_OPEN

**Example**:
```
State: OPEN 🔴
Action: Block ALL calls
Duration: 5 seconds
↓
All requests → Fallback (without calling service)
↓
After 5s → Transition to HALF_OPEN 🟡
```

---

### 3️⃣ HALF_OPEN 🟡 (Testing Recovery)

**Meaning**: Circuit is testing if service has recovered

- 🧪 Allows limited number of test calls
- 🎯 In our config: 2 test calls allowed
- ✅ If all tests succeed → Back to CLOSED 🟢
- ❌ If ANY test fails → Back to OPEN 🔴

**Example - Success**:
```
State: HALF_OPEN 🟡
Test 1: ✅ Success
Test 2: ✅ Success
↓
State: CLOSED 🟢 (service recovered!)
```

**Example - Failure**:
```
State: HALF_OPEN 🟡
Test 1: ✅ Success
Test 2: ❌ Failure
↓
State: OPEN 🔴 (service still down)
Wait another 5 seconds...
```

---

## Implementation

### Basic Circuit Breaker

```java
@Service
@RequiredArgsConstructor
public class CircuitBreakService {

    private final ProductsApiClient productsApiClient;

    @CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
    public List<Product> getProducts(Boolean success) {
        log.info("Fetching products from API");
        String param = success ? "ok" : "429";
        
        try {
            List<Product> products = productsApiClient.products(param);
            log.info("Successfully fetched {} products", products.size());
            return products;
        } catch (HttpStatusException e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw e;
        }
    }

    private List<Product> fallbackMethod(Boolean success, Exception e) {
        log.warn("Circuit breaker fallback triggered - Reason: {}", e.getMessage());
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Service temporarily unavailable due to circuit breaker"
        );
    }
}
```

### Controller with State Monitoring

```java
@RestController
@RequestMapping("/api/circuit-break")
@RequiredArgsConstructor
public class CircuitBreakController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CircuitBreakService circuitBreakService;

    @GetMapping
    public ResponseEntity<List<Product>> circuitBreak(@RequestParam Boolean success) {
        return ResponseEntity.ok(circuitBreakService.getProducts(success));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("products-cb");

        Map<String, Object> response = new HashMap<>();
        response.put("name", "products-cb");
        response.put("state", circuitBreaker.getState().name());
        response.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
        response.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
        response.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        response.put("numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls());

        return ResponseEntity.ok(response);
    }
}
```

---

## Configuration

### Complete Configuration Explained

```yaml
resilience4j:
  circuitbreaker:
    instances:
      products-cb:
        # Register circuit breaker in Spring Boot Actuator for monitoring
        register-health-indicator: true
        
        # Number of calls in the sliding window to calculate failure rate
        # Smaller = faster response to failures (good for demos/tests)
        sliding-window-size: 4
        
        # Minimum calls required before circuit can open
        # Prevents opening on too little data
        minimum-number-of-calls: 2
        
        # Number of test calls allowed in HALF_OPEN state
        # If all succeed → CLOSED, if any fails → OPEN
        permitted-number-of-calls-in-half-open-state: 2
        
        # Time circuit stays OPEN before testing recovery
        # Shorter = faster recovery attempts (good for tests)
        wait-duration-in-open-state: 5s
        
        # Percentage of failures that triggers circuit opening
        # 50 = opens when 50% or more calls fail
        failure-rate-threshold: 50
        
        # Percentage of slow calls that triggers opening
        # 100 = only opens if ALL calls are slow
        slow-call-rate-threshold: 100
        
        # Duration considered "slow"
        # Calls taking longer than this count as slow
        slow-call-duration-threshold: 2s
        
        # Automatically transition from OPEN to HALF_OPEN
        # true = automatic recovery testing (recommended)
        automatic-transition-from-open-to-half-open-enabled: true
```

### Configuration for Different Scenarios

**Test/Demo (Current)**:
```yaml
sliding-window-size: 4
minimum-number-of-calls: 2
wait-duration-in-open-state: 5s
```
- Fast response
- Quick recovery
- Easy to demonstrate

**Production - Critical Service**:
```yaml
sliding-window-size: 10
minimum-number-of-calls: 5
wait-duration-in-open-state: 30s
failure-rate-threshold: 30
```
- More sensitive (opens at 30%)
- More data before opening
- Longer recovery time

**Production - Tolerant Service**:
```yaml
sliding-window-size: 100
minimum-number-of-calls: 50
wait-duration-in-open-state: 60s
failure-rate-threshold: 70
```
- Less sensitive (opens at 70%)
- Requires more failures
- Longer recovery window

---

## Combining with Retry

**CRITICAL**: Annotation order matters!

### ✅ Correct Order

```java
@Retry(name = "basic-retry")  // Executes FIRST
@CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
public List<Product> getProducts(String scenario) {
    return productsApiClient.products(scenario);
}
```

**Flow**:
```
Request
  ↓
@Retry: Tries 3 times (500ms between attempts)
  ↓
All 3 attempts complete (success or failure)
  ↓
@CircuitBreaker: Records 1 call result
  ↓
Evaluates if circuit should open
```

### ❌ Wrong Order

```java
@CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
@Retry(name = "basic-retry")  // NEVER executes!
public List<Product> getProducts(String scenario) {
    return productsApiClient.products(scenario);
}
```

**Problem**: Circuit Breaker intercepts first failure and calls fallback immediately. Retry never gets a chance to run.

### Example Service with Both Patterns

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductsApiClient productsApiClient;

    /**
     * Combines Retry and Circuit Breaker for maximum resilience.
     * 
     * Execution order:
     * 1. Retry attempts up to 3 times with 500ms between attempts
     * 2. Circuit Breaker sees the final result after all retries
     * 3. Circuit opens if 50% of calls (after retries) fail
     * 
     * When circuit is OPEN:
     * - No retry attempts made
     * - Calls go directly to fallback
     */
    @Retry(name = "basic-retry")
    @CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
    public List<Product> getProducts(String scenario) {
        log.info("Fetching products with scenario: {}", scenario);
        
        try {
            List<Product> products = productsApiClient.products(scenario);
            log.info("Successfully fetched {} products", products.size());
            return products;
        } catch (Exception e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fallback executed in two scenarios:
     * 
     * 1. After all retry attempts exhausted:
     *    - Retry tried 3 times, all failed
     *    - Circuit Breaker records the failure
     *    - Fallback is called
     * 
     * 2. Circuit is already OPEN:
     *    - Previous failures opened the circuit
     *    - Call blocked immediately (no retry)
     *    - Fallback called directly
     */
    private List<Product> fallbackMethod(String scenario, Exception e) {
        log.warn("Circuit breaker fallback: {} - {}", scenario, e.getMessage());
        return List.of(); // Return empty list or cached data
    }
}
```

---

## Monitoring

### Actuator Health Endpoint

```bash
curl http://localhost:8085/actuator/health

# Response:
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "products-cb": {
          "status": "UP",
          "state": "CLOSED",
          "failureRate": "0.0%",
          "slowCallRate": "0.0%",
          "bufferedCalls": 4,
          "failedCalls": 0,
          "slowCalls": 0,
          "notPermittedCalls": 0
        }
      }
    }
  }
}
```

### Custom Status Endpoint

```bash
curl http://localhost:8085/api/circuit-break/status

# Response:
{
  "name": "products-cb",
  "state": "CLOSED",
  "failureRate": 25.0,
  "numberOfFailedCalls": 1,
  "numberOfSuccessfulCalls": 3,
  "numberOfBufferedCalls": 4
}
```

### Circuit Breaker Metrics

```bash
curl http://localhost:8085/actuator/metrics/resilience4j.circuitbreaker.calls

# Provides detailed metrics on circuit breaker calls
```

---

## Testing Scenarios

### Scenario 1: Normal Operation (Circuit Stays CLOSED)

```bash
# Call 1 - Success
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK, products returned
# State: CLOSED 🟢

# Call 2 - Success
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK, products returned
# State: CLOSED 🟢 (0% failure rate)
```

### Scenario 2: Circuit Opens Due to Failures

```bash
# Call 1 - Fail
curl "http://localhost:8085/api/circuit-break?success=false"
# Response: 400 Bad Request (after 3 retry attempts)
# State: CLOSED 🟢 (50% failure rate - not yet opened)

# Call 2 - Fail
curl "http://localhost:8085/api/circuit-break?success=false"
# Response: 400 Bad Request
# State: OPEN 🔴 (100% failure rate in window of 2 calls)

# Call 3 - Success attempt but circuit is OPEN
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 400 Bad Request (fallback, API not called)
# State: OPEN 🔴 (blocked by circuit)
```

### Scenario 3: Circuit Recovery

```bash
# Circuit is OPEN, wait 5 seconds...
sleep 5

# State automatically transitions to HALF_OPEN 🟡

# Test Call 1 - Success
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK
# State: HALF_OPEN 🟡 (test 1/2 passed)

# Test Call 2 - Success
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK
# State: CLOSED 🟢 (recovered! all tests passed)

# Call 4 - Normal operation resumed
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK
# State: CLOSED 🟢
```

### Scenario 4: Failed Recovery

```bash
# Circuit is OPEN, wait 5 seconds...
sleep 5

# State: HALF_OPEN 🟡

# Test Call 1 - Success
curl "http://localhost:8085/api/circuit-break?success=true"
# Response: 200 OK
# State: HALF_OPEN 🟡

# Test Call 2 - Fail
curl "http://localhost:8085/api/circuit-break?success=false"
# Response: 400 Bad Request
# State: OPEN 🔴 (back to open, service still failing)

# Need to wait another 5 seconds before next test...
```

---

## Best Practices

### 1. Always Implement Fallback

```java
// ✅ Good: Provides graceful degradation
@CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
public List<Product> getProducts() {
    return api.getProducts();
}

private List<Product> fallbackMethod(Exception e) {
    return getCachedProducts(); // Return cached data
}

// ❌ Bad: No fallback
@CircuitBreaker(name = "products-cb")
public List<Product> getProducts() {
    return api.getProducts(); // User sees raw error
}
```

### 2. Set Appropriate Thresholds

```yaml
# ✅ Good: Based on service behavior
failure-rate-threshold: 50  # Opens at 50% failures
sliding-window-size: 10     # Enough data to be meaningful

# ❌ Bad: Too sensitive
failure-rate-threshold: 10  # Opens too easily
sliding-window-size: 2      # Not enough data
```

### 3. Configure Proper Wait Duration

```yaml
# ✅ Good: Gives service time to recover
wait-duration-in-open-state: 30s  # Production

# ❌ Bad: Tests recovery too frequently
wait-duration-in-open-state: 1s   # Hammers failing service
```

### 4. Monitor Circuit State

```java
// ✅ Good: Provides observability
@GetMapping("/status")
public CircuitBreakerStatus getStatus() {
    var cb = circuitBreakerRegistry.circuitBreaker("products-cb");
    return new CircuitBreakerStatus(
        cb.getState(),
        cb.getMetrics().getFailureRate()
    );
}
```

### 5. Combine with Retry Correctly

```java
// ✅ Good: Retry before Circuit Breaker
@Retry(name = "retry")
@CircuitBreaker(name = "cb", fallbackMethod = "fallback")

// ❌ Bad: Circuit Breaker before Retry
@CircuitBreaker(name = "cb", fallbackMethod = "fallback")
@Retry(name = "retry")  // Never executes
```

### 6. Use Slow Call Detection

```yaml
# ✅ Good: Opens on slow responses too
slow-call-rate-threshold: 50
slow-call-duration-threshold: 3s

# ❌ Bad: Only considers failures
slow-call-rate-threshold: 100  # Ignores slow calls
```

### 7. Test All States

```java
@Test
void shouldOpenCircuit() {
    // Test CLOSED → OPEN transition
}

@Test
void shouldBlockWhenOpen() {
    // Test OPEN state behavior
}

@Test
void shouldRecoverToHalfOpen() {
    // Test OPEN → HALF_OPEN transition
}

@Test
void shouldCloseAfterSuccessfulTests() {
    // Test HALF_OPEN → CLOSED transition
}
```

---

## Common Pitfalls

### 1. Wrong Annotation Order

```java
// ❌ Bad: Circuit Breaker prevents Retry
@CircuitBreaker(name = "cb")
@Retry(name = "retry")

// ✅ Good: Retry happens first
@Retry(name = "retry")
@CircuitBreaker(name = "cb")
```

### 2. No Fallback Implementation

```java
// ❌ Bad: Users see errors
@CircuitBreaker(name = "cb")

// ✅ Good: Graceful degradation
@CircuitBreaker(name = "cb", fallbackMethod = "fallback")
```

### 3. Too Small Sliding Window

```yaml
# ❌ Bad: Not enough data
sliding-window-size: 2

# ✅ Good: Meaningful sample size
sliding-window-size: 10
```

### 4. Not Monitoring State

```java
// ❌ Bad: No visibility
// Circuit opens and you don't know

// ✅ Good: Monitoring endpoint
@GetMapping("/status")
public CircuitState getState() {
    return circuitBreaker.getState();
}
```

### 5. Ignoring Slow Calls

```yaml
# ❌ Bad: Only fails on errors
slow-call-rate-threshold: 100

# ✅ Good: Opens on slow calls too
slow-call-rate-threshold: 50
slow-call-duration-threshold: 2s
```

### 6. Too Short Wait Duration

```yaml
# ❌ Bad: Doesn't give service time to recover
wait-duration-in-open-state: 1s

# ✅ Good: Reasonable recovery time
wait-duration-in-open-state: 10s  # Tests
wait-duration-in-open-state: 30s  # Production
```

### 7. Same Config for All Services

```yaml
# ❌ Bad: One size fits all
circuitbreaker:
  configs:
    default:
      failure-rate-threshold: 50

# ✅ Good: Per-service tuning
circuitbreaker:
  instances:
    critical-service:
      failure-rate-threshold: 30  # More sensitive
    tolerant-service:
      failure-rate-threshold: 70  # Less sensitive
```

---

## Quick Reference

### State Transitions

```
CLOSED 🟢
  ↓ (failure rate ≥ 50%)
OPEN 🔴
  ↓ (after 5 seconds)
HALF_OPEN 🟡
  ↓ (2 successful tests)
CLOSED 🟢

or

HALF_OPEN 🟡
  ↓ (any test fails)
OPEN 🔴
```

### Configuration Summary

| Property | Test Value | Production Value |
|----------|------------|------------------|
| `sliding-window-size` | 4 | 10-100 |
| `minimum-number-of-calls` | 2 | 5-50 |
| `failure-rate-threshold` | 50 | 30-70 |
| `wait-duration-in-open-state` | 5s | 30-60s |
| `slow-call-duration-threshold` | 2s | 3-10s |

### Endpoints

```bash
# Test circuit breaker
GET /api/circuit-break?success={true|false}

# Check circuit state
GET /api/circuit-break/status

# Actuator health
GET /actuator/health

# Circuit breaker metrics
GET /actuator/circuitbreakers
```

---

## Resources

- [Resilience4j Circuit Breaker Docs](https://resilience4j.readme.io/docs/circuitbreaker)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Microsoft - Circuit Breaker Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)

---

**Next**: [Main README](README.md) | [Retry Patterns](RETRY_README.md) | Test the endpoints | Monitor metrics