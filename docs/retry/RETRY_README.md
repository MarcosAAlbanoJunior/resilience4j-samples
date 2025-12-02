# 🔄 Retry Patterns with Resilience4j

Complete guide to implementing retry patterns in Spring Boot using Resilience4j. This documentation covers four different retry strategies with real-world examples and best practices.

## 📋 Table of Contents

- [Overview](#overview)
- [When to Use Retry](#when-to-use-retry)
- [Implementations](#implementations)
- [Configuration](#configuration)
- [Testing](#testing)
- [Best Practices](#best-practices)
- [Common Pitfalls](#common-pitfalls)

---

## Overview

Retry patterns handle transient failures by automatically re-attempting failed operations. Essential for building resilient distributed systems.

### Key Concepts

- **Max Attempts**: Maximum number of retry attempts (including initial call)
- **Wait Duration**: Time between retry attempts
- **Predicate**: Condition that determines if retry should occur
- **Fallback**: Alternative action when retries exhausted
- **Exponential Backoff**: Progressively increasing wait times

---

## When to Use Retry

### ✅ Good Use Cases

- Network hiccups and temporary connection issues
- Rate limiting (HTTP 429 responses)
- Temporary service unavailability
- Database deadlocks
- Async operation polling

### ❌ Bad Use Cases

- Permanent failures (404, 400)
- Authentication errors (401, 403)
- Non-idempotent operations
- Client-side validation errors
- Service permanently down (use Circuit Breaker instead)

---

## Implementations

## 1. Basic Retry

Simple retry with fixed wait duration. Retries on any exception.

### Configuration

```yaml
resilience4j:
  retry:
    instances:
      basic-retry:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.lang.Exception
```

### Implementation

```java
@Retry(name = "basic-retry", fallbackMethod = "fallbackMethod")
public List<Product> basicRetryExample(String scenario) {
    return productsApiClient.products(scenario);
}

private List<Product> fallbackMethod(String scenario, Exception e) {
    return List.of();
}
```

### Usage

```bash
curl "http://localhost:8085/api/retry?scenario=500-500-ok"
```

---

## 2. Exception Predicate Retry

Conditional retry based on HTTP status codes. Only retries specific transient errors.

### Configuration

```yaml
resilience4j:
  retry:
    instances:
      retry-on-status:
        max-attempts: 3
        wait-duration: 500ms
        retry-exception-predicate: com.malbano.resilience4j.samples.retry.config.RetryExceptionPredicate
```

### Predicate

```java
public class RetryExceptionPredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable throwable) {
        if (!(throwable instanceof HttpStatusException httpException)) {
            return false;
        }
        
        HttpStatus status = httpException.getHttpStatus();
        return HttpStatus.TOO_MANY_REQUESTS.equals(status) ||      // 429
               HttpStatus.INTERNAL_SERVER_ERROR.equals(status) ||   // 500
               HttpStatus.REQUEST_TIMEOUT.equals(status);           // 408
    }
}
```

### Retry Matrix

| HTTP Status | Retry? | Reason |
|-------------|--------|--------|
| 429 | ✅ Yes | Rate limiting |
| 500 | ✅ Yes | Server error |
| 408 | ✅ Yes | Timeout |
| 503 | ❌ No | Service down |
| 400 | ❌ No | Client error |
| 404 | ❌ No | Not found |

### Usage

```bash
curl "http://localhost:8085/api/retry/with-throw-predicate?scenario=429-500-ok"
```

---

## 3. Result Predicate Retry

Conditional retry based on response content. Perfect for polling async operations.

### Configuration

```yaml
resilience4j:
  retry:
    instances:
      retry-on-result:
        max-attempts: 3
        wait-duration: 500ms
        result-predicate: com.malbano.resilience4j.samples.retry.config.RetryResultPredicate
```

### Predicate

```java
public class RetryResultPredicate implements Predicate<Product> {
    @Override
    public boolean test(Product result) {
        if (result == null) {
            return false;
        }
        
        return "GENERATING".equals(result.getStatus());
    }
}
```

### Status Matrix

| Status | Retry? | Reason |
|--------|--------|--------|
| GENERATING | ✅ Yes | Still processing |
| ACTIVATED | ❌ No | Complete |
| FAILED | ❌ No | Failed |

### ⚠️ Important Behavior

**Fallback is ONLY called on exceptions, NOT when max retries exhausted.**

```java
// Max attempts exhausted with GENERATING status:
Product result = service.retryOnResult("generating-generating-generating");
// result.getStatus() == "GENERATING" (NOT null, fallback NOT called)

// Exception thrown:
Product result = service.retryOnResult("error");
// result == null (fallback WAS called)
```

### Usage

```bash
# Immediate success
curl "http://localhost:8085/api/retry/with-result-predicate?scenario=activated"

# Retry twice, succeed on third
curl "http://localhost:8085/api/retry/with-result-predicate?scenario=generating-generating-activated"

# Max retries exhausted, returns last result
curl "http://localhost:8085/api/retry/with-result-predicate?scenario=generating-generating-generating"
```

### Use Cases

- Document generation polling
- Batch job status checking
- Cloud resource provisioning
- File processing status
- Any HTTP 200 response where operation isn't complete

---

## 4. Custom Interval Retry

Dynamic wait times based on error type. Each status gets different retry strategy.

### Configuration

```yaml
resilience4j:
  retry:
    instances:
      retry-with-custom-interval:
        max-attempts: 4
        interval-bi-function: com.malbano.resilience4j.samples.retry.config.HttpStatusRetryInterval
```

### Interval Function

```java
public class HttpStatusRetryInterval implements IntervalBiFunction<HttpStatus> {
    @Override
    public Long apply(Integer attemptNumber, Either<Throwable, HttpStatus> either) {
        Throwable throwable = either.getLeft();
        
        if (throwable instanceof HttpStatusException httpException) {
            return getDelayForStatusCode(httpException.getHttpStatus().value(), attemptNumber);
        }
        
        return 1000L * (long) Math.pow(2, attemptNumber - 1);
    }

    private long getDelayForStatusCode(int statusCode, int attemptNumber) {
        return switch (statusCode) {
            case 429 -> 5000L;  // 5s - respect rate limit
            case 503 -> 7000L;  // 7s - service recovering
            case 500, 502, 504 -> 1000L * (long) Math.pow(2, attemptNumber - 1); // Exponential
            default -> 2000L;   // 2s - default
        };
    }
}
```

### Interval Matrix

| Status | Strategy | Wait Times | Reason |
|--------|----------|------------|--------|
| 429 | Fixed | 5s, 5s, 5s | Respect rate limit |
| 503 | Fixed | 7s, 7s, 7s | Service recovery |
| 500 | Exponential | 1s, 2s, 4s | Progressive backoff |
| 502 | Exponential | 1s, 2s, 4s | Upstream issue |
| Others | Fixed | 2s, 2s, 2s | Default |

### Usage

```bash
# Rate limit - 5s waits
curl "http://localhost:8085/api/retry/with-custom-interval?scenario=429-429-ok"

# Server errors - exponential
curl "http://localhost:8085/api/retry/with-custom-interval?scenario=500-500-ok"
```

---

## Configuration

### Complete Config

```yaml
resilience4j:
  retry:
    instances:
      basic-retry:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: false
        retry-exceptions:
          - java.lang.Exception

      retry-on-status:
        max-attempts: 3
        wait-duration: 500ms
        retry-exception-predicate: com.malbano.resilience4j.samples.retry.config.RetryExceptionPredicate

      retry-on-result:
        max-attempts: 3
        wait-duration: 500ms
        result-predicate: com.malbano.resilience4j.samples.retry.config.RetryResultPredicate

      retry-with-custom-interval:
        max-attempts: 4
        interval-bi-function: com.malbano.resilience4j.samples.retry.config.HttpStatusRetryInterval
```

---

## Testing

### Unit Test Examples

```java
@SpringBootTest
class PredicateResultRetryServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private PredicateResultRetryService service;

    @Test
    void shouldRetryOnGeneratingStatus() {
        Product generating = Product.builder().status("GENERATING").build();
        Product activated = Product.builder().status("ACTIVATED").build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(generating)
                .thenReturn(activated);

        Product result = service.retryOnResult("generating-activated");

        verify(productsApiClient, times(2)).productByStatus(any());
        assertThat(result.getStatus()).isEqualTo("ACTIVATED");
    }

    @Test
    void shouldReturnLastResultWhenMaxAttemptsExhausted() {
        Product generating = Product.builder().status("GENERATING").build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(generating);

        Product result = service.retryOnResult("generating-generating-generating");

        verify(productsApiClient, times(3)).productByStatus(any());
        assertThat(result.getStatus()).isEqualTo("GENERATING"); // not null
    }
}
```

---

## Best Practices

### 1. Choose Right Strategy

```java
// ✅ Good: Specific codes
@Retry(name = "retry-on-status")

// ❌ Bad: All exceptions
@Retry(name = "basic-retry")
```

### 2. Reasonable Attempts

```yaml
# ✅ Good
max-attempts: 3

# ❌ Bad
max-attempts: 10
```

### 3. Always Implement Fallback

```java
// ✅ Good
@Retry(name = "retry", fallbackMethod = "fallbackMethod")
public List<Product> getProducts() {
    return api.products();
}

private List<Product> fallbackMethod(Exception e) {
    return getCachedProducts();
}
```

### 4. Use Result Predicates for Polling

```java
// ✅ Good
@Retry(name = "retry-on-result")
public Document checkStatus() {
    return api.getDocument();
}
```

### 5. Respect Rate Limits

```java
// ✅ Good
case 429 -> 5000L; // 5 seconds

// ❌ Bad
case 429 -> 100L; // Too fast
```

---

## Common Pitfalls

### 1. Retrying Permanent Failures

```java
// ❌ Bad: Retries 404
@Retry(name = "basic-retry")

// ✅ Good: Selective retry
@Retry(name = "retry-on-status")
```

### 2. Too Many Attempts

```yaml
# ❌ Bad
max-attempts: 20

# ✅ Good
max-attempts: 3
```

### 3. Misunderstanding Result Predicate Fallback

```java
// ❌ Problem: Expecting fallback on max retries
// Fallback NOT called when predicate fails

// ✅ Solution: Fallback only on exceptions
// Max retries: returns last result
// Exception: calls fallback
```

### 4. No Exponential Backoff

```yaml
# ❌ Bad
wait-duration: 100ms

# ✅ Good
enable-exponential-backoff: true
```

---

## Quick Reference

### Comparison

| Feature | Basic | Exception | Result | Custom |
|---------|-------|-----------|--------|--------|
| Complexity | Low | Medium | Medium | High |
| Use Case | Simple | HTTP codes | Polling | Complex |
| Fallback on Exception | ✅ | ✅ | ✅ | ✅ |
| Fallback on Max Retries | ✅ | ✅ | ❌ | ✅ |
| HTTP 200 Retry | ❌ | ❌ | ✅ | ❌ |

### Endpoints

```bash
GET /api/retry?scenario=500-500-ok
GET /api/retry/with-throw-predicate?scenario=429-500-ok
GET /api/retry/with-result-predicate?scenario=generating-activated
GET /api/retry/with-custom-interval?scenario=429-429-ok
```

### Monitor Metrics

```bash
curl http://localhost:8085/actuator/retries
```

---

## Resources

- [Resilience4j Retry Docs](https://resilience4j.readme.io/docs/retry)
- [Microsoft Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)
- [AWS Exponential Backoff](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)

---

**Next:** [Main README](../../README.md) | Experiment with scenarios | Monitor metrics