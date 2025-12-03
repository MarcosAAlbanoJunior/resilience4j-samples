# 🚦 Rate Limiter Pattern with Resilience4j

Complete guide to implementing Rate Limiter pattern in Spring Boot using Resilience4j. Control request rates to protect services from overload and ensure fair resource usage.

## 📋 Table of Contents

- [Overview](#overview)
- [When to Use Rate Limiter](#when-to-use-rate-limiter)
- [Implementation Strategies](#implementation-strategies)
- [Configuration](#configuration)
- [Best Practices](#best-practices)
- [Common Pitfalls](#common-pitfalls)
- [Testing Scenarios](#testing-scenarios)

---

## Overview

Rate Limiter controls the number of requests allowed within a specific time window, protecting services from being overwhelmed and ensuring fair resource distribution.

### Key Concepts

- **Limit for Period**: Maximum number of requests allowed in a time window
- **Refresh Period**: Time window duration after which the limit resets
- **Timeout Duration**: How long to wait for permission (0s = fail immediately)
- **Permission**: Token required to make a request
- **Dynamic Instances**: Create separate rate limiters per user/tenant

---

## When to Use Rate Limiter

### ✅ Good Use Cases

- Protecting public APIs from abuse or DDoS attacks
- Enforcing API quotas per user/tenant/API key
- Preventing resource exhaustion from too many concurrent requests
- Implementing fair usage policies in multi-tenant systems
- Throttling requests to downstream services
- Controlling costs for pay-per-call external APIs

### ❌ Bad Use Cases

- Internal services with complete trust
- When all requests must be processed immediately
- Single-user applications
- Systems where request rejection is unacceptable
- Services with guaranteed capacity for all requests
- When latency is more critical than protection

---

## Implementation Strategies

### 1️⃣ Basic Rate Limiter (Fail-Fast)

**Concept**: Reject requests immediately when limit is exceeded

```java
@RateLimiter(name = "basic-rate-limiter", fallbackMethod = "fallbackMethod")
public List<Product> basicRateLimit() {
    return productsApiClient.products("ok");
}

private List<Product> fallbackMethod(Exception e) {
    throw new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "Rate limit exceeded. Please try again later."
    );
}
```

**Configuration**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      basic-rate-limiter:
        limit-for-period: 5          # 5 requests
        limit-refresh-period: 10s     # per 10 seconds
        timeout-duration: 0s          # fail immediately
```

**Use Case**: External APIs where fast feedback is critical
- Public REST APIs
- Customer-facing endpoints
- Rate limiting for security

**Behavior**:
- ✅ Requests 1-5: All succeed
- ❌ Request 6: Fails with HTTP 429
- ⏰ After 10s: Counter resets

---

### 2️⃣ Rate Limiter with Wait

**Concept**: Wait for permission instead of failing immediately

```java
@RateLimiter(name = "rate-limiter-with-wait", fallbackMethod = "fallbackMethod")
public List<Product> rateLimitWithWait() {
    return productsApiClient.products("ok");
}

private List<Product> fallbackMethod(Exception e) {
    throw new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "Rate limit exceeded. Please try again later."
    );
}
```

**Configuration**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      rate-limiter-with-wait:
        limit-for-period: 3          # 3 requests
        limit-refresh-period: 10s     # per 10 seconds
        timeout-duration: 5s          # wait up to 5s
```

**Use Case**: Internal service-to-service communication
- Background jobs
- Async processing
- Non-critical operations
- When waiting is acceptable

**Behavior**:
- ✅ Requests 1-3: All succeed immediately
- ⏳ Request 4: Waits up to 5s for permission
    - If permission granted within 5s → Success
    - If 5s timeout expires → Fails with HTTP 429
- ⏰ After 10s: Counter resets

**Comparison with Basic**:
| Feature | Basic | With Wait |
|---------|-------|-----------|
| Timeout | 0s (immediate) | 5s (waits) |
| Success Rate | Lower | Higher |
| Response Time | Faster | May be delayed |
| Use Case | External APIs | Internal services |

---

### 3️⃣ Per-User Rate Limiter (Dynamic)

**Concept**: Each user gets independent quota

```java
public List<Product> getProductsByUser(String userId) {
    String limiterName = "user-" + userId;
    
    // Get or create rate limiter for this user
    RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(
        limiterName,
        "default-per-user"
    );
    
    // Try to acquire permission
    boolean permitted = rateLimiter.acquirePermission();
    
    if (!permitted) {
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded for user " + userId
        );
    }
    
    return productsApiClient.products("ok");
}
```

**Configuration**:
```yaml
resilience4j:
  ratelimiter:
    configs:
      default-per-user:
        limit-for-period: 5          # 5 requests
        limit-refresh-period: 15s     # per 15 seconds
        timeout-duration: 0s          # per user
```

**Use Case**: Multi-tenant applications
- SaaS platforms
- Public APIs with API keys
- User-specific quotas
- Fair usage policies

**Key Features**:
- ✅ Each user has independent quota
- 🎯 One user hitting limit doesn't affect others
- 🔄 Rate limiters created dynamically on-demand
- 💾 Instances cached in registry
- 📊 Separate metrics per user

**Example Flow**:
```
User1: [✅ ✅ ✅ ✅ ✅] → ❌ (limit reached)
User2: [✅ ✅] → ✅ (still has quota)
User3: [✅] → ✅ (still has quota)

After 15s: All users' quotas reset
```

---

## Configuration

### Complete Configuration Explained

```yaml
resilience4j:
  ratelimiter:
    # Shared configurations (templates)
    configs:
      default-per-user:
        # Maximum requests allowed per period
        limit-for-period: 5
        
        # Time window for the limit (resets after this)
        limit-refresh-period: 15s
        
        # How long to wait for permission
        # 0s = fail immediately (fail-fast)
        # >0s = wait up to this duration
        timeout-duration: 0s
        
        # Register in Spring Boot Actuator
        register-health-indicator: false
    
    # Named instances
    instances:
      basic-rate-limiter:
        limit-for-period: 5
        limit-refresh-period: 10s
        timeout-duration: 0s
      
      rate-limiter-with-wait:
        limit-for-period: 3
        limit-refresh-period: 10s
        timeout-duration: 5s
```

### Configuration for Different Scenarios

**API Rate Limiting (Public)**:
```yaml
basic-rate-limiter:
  limit-for-period: 100
  limit-refresh-period: 60s    # 100 req/min
  timeout-duration: 0s         # fail immediately
```

**Internal Service Protection**:
```yaml
internal-limiter:
  limit-for-period: 50
  limit-refresh-period: 10s    # 50 req/10s = 300 req/min
  timeout-duration: 2s         # small wait acceptable
```

**Per-User Quota (Freemium)**:
```yaml
free-tier:
  limit-for-period: 10
  limit-refresh-period: 3600s  # 10 req/hour
  timeout-duration: 0s

premium-tier:
  limit-for-period: 1000
  limit-refresh-period: 3600s  # 1000 req/hour
  timeout-duration: 0s
```

**Aggressive Throttling**:
```yaml
aggressive-limiter:
  limit-for-period: 1
  limit-refresh-period: 1s     # 1 req/sec
  timeout-duration: 0s
```

---

## Best Practices

### 1. Choose Appropriate Timeout Strategy

```java
// ✅ Good: Fail-fast for external APIs
@RateLimiter(name = "public-api")  // timeout: 0s
public Response handle() { }

// ✅ Good: Wait for internal services
@RateLimiter(name = "internal-api")  // timeout: 2s
public Response handle() { }

// ❌ Bad: Waiting on public APIs (poor UX)
@RateLimiter(name = "public-api")  // timeout: 10s
public Response handle() { }
```

### 2. Always Implement Fallback

```java
// ✅ Good: Clear error message
@RateLimiter(name = "api", fallbackMethod = "fallback")
public Response handle() { }

private Response fallback(Exception e) {
    return Response.tooManyRequests()
        .header("X-RateLimit-Reset", getResetTime())
        .body("Rate limit exceeded");
}

// ❌ Bad: No fallback
@RateLimiter(name = "api")
public Response handle() { }  // Generic error to user
```

### 3. Include Rate Limit Headers

```java
// ✅ Good: Informative headers
@GetMapping("/api/data")
public ResponseEntity<Data> getData() {
    var limiter = registry.rateLimiter("api");
    var metrics = limiter.getMetrics();
    
    return ResponseEntity.ok()
        .header("X-RateLimit-Limit", "100")
        .header("X-RateLimit-Remaining", 
                String.valueOf(metrics.getAvailablePermissions()))
        .header("X-RateLimit-Reset", getResetTime())
        .body(data);
}
```

### 4. Use Different Limits for Different Operations

```yaml
# ✅ Good: Operation-specific limits
ratelimiter:
  instances:
    read-operations:
      limit-for-period: 100
    write-operations:
      limit-for-period: 10
    expensive-operations:
      limit-for-period: 1
```

### 5. Monitor Rate Limiter Metrics

```java
// ✅ Good: Expose metrics endpoint
@GetMapping("/api/rate-limit/status")
public RateLimiterStatus getStatus() {
    var limiter = registry.rateLimiter("api");
    return RateLimiterStatus.builder()
        .availablePermissions(limiter.getMetrics().getAvailablePermissions())
        .numberOfWaitingThreads(limiter.getMetrics().getNumberOfWaitingThreads())
        .build();
}
```

### 6. Graceful Degradation

```java
// ✅ Good: Return cached data when rate limited
private List<Product> fallback(Exception e) {
    log.warn("Rate limit exceeded, returning cached data");
    return cacheService.getCachedProducts();
}

// ❌ Bad: Just throw error
private List<Product> fallback(Exception e) {
    throw new RuntimeException("Too many requests");
}
```

### 7. Document Rate Limits in API

```java
// ✅ Good: Clear documentation
@Operation(
    summary = "Get products",
    description = "Rate limit: 100 requests per minute per user"
)
@ApiResponse(responseCode = "429", description = "Rate limit exceeded")
@GetMapping("/products")
public List<Product> getProducts() { }
```

---

## Common Pitfalls

### 1. Using Same Config for All Endpoints

```yaml
# ❌ Bad: One size fits all
ratelimiter:
  configs:
    default:
      limit-for-period: 10

# ✅ Good: Specific per endpoint
ratelimiter:
  instances:
    expensive-endpoint:
      limit-for-period: 1
    cheap-endpoint:
      limit-for-period: 100
```

### 2. Not Providing User Feedback

```java
// ❌ Bad: Generic error
throw new Exception("Error");

// ✅ Good: Clear message with reset time
throw new ResponseStatusException(
    HttpStatus.TOO_MANY_REQUESTS,
    "Rate limit exceeded. Limit resets at " + resetTime
);
```

### 3. Forgetting to Reset Test Limiters

```java
// ❌ Bad: Tests interfere with each other
@Test
void test1() { callApi(); callApi(); callApi(); }
@Test  
void test2() { callApi(); } // May fail due to test1

// ✅ Good: Reset between tests
@BeforeEach
void setUp() {
    rateLimiterRegistry.remove("test-limiter");
    rateLimiterRegistry.rateLimiter("test-limiter", config);
}
```

### 4. Long Timeout on Public APIs

```yaml
# ❌ Bad: Users wait 30s for error
public-api:
  timeout-duration: 30s

# ✅ Good: Fast failure
public-api:
  timeout-duration: 0s
```

### 5. Not Considering Clock Skew

```java
// ❌ Bad: Hardcoded timestamps
"Reset-At": "2024-01-01T10:00:00Z"

// ✅ Good: Relative time
"Reset-After": "10"  // seconds
```

### 6. Ignoring Per-User Limits

```java
// ❌ Bad: Global limit shared by all
@RateLimiter(name = "global")
public Response handle() { }
// One bad actor can exhaust limit for everyone

// ✅ Good: Per-user limits
public Response handle(@RequestHeader("X-User-ID") String userId) {
    RateLimiter limiter = registry.rateLimiter("user-" + userId, config);
    // Each user has independent quota
}
```

### 7. Not Monitoring Rejections

```java
// ❌ Bad: Silent failures
@RateLimiter(name = "api")
public Response handle() { }

// ✅ Good: Monitor rejections
private Response fallback(Exception e) {
    metrics.incrementCounter("rate_limit_rejections");
    log.warn("Rate limit exceeded");
    throw new ResponseStatusException(TOO_MANY_REQUESTS);
}
```

---

## Testing Scenarios

### Test Basic Rate Limiter

```bash
# Scenario: Exhaust limit and verify 429
for i in {1..6}; do
  echo "Request $i:"
  curl http://localhost:8085/api/rate-limiter/basic
  echo
done

# Expected:
# Requests 1-5: 200 OK
# Request 6: 429 Too Many Requests

# Wait 10 seconds
sleep 10

# Request 7: 200 OK (limit reset)
curl http://localhost:8085/api/rate-limiter/basic
```

### Test Wait Behavior

```bash
# Scenario: Test waiting mechanism
for i in {1..4}; do
  echo "Request $i ($(date +%T)):"
  curl http://localhost:8085/api/rate-limiter/with-wait
  echo
done

# Expected:
# Requests 1-3: Success immediately
# Request 4: Waits up to 5s, then:
#   - Success if refresh period passed
#   - 429 if timeout expired
```

### Test Per-User Rate Limiting

```bash
# Scenario: Independent user quotas
echo "User1 requests:"
for i in {1..6}; do
  curl -H "X-USER-ID: user1" \
    http://localhost:8085/api/rate-limiter/per-user
done
# User1: 5 success, 1 failure

echo "User2 requests:"
curl -H "X-USER-ID: user2" \
  http://localhost:8085/api/rate-limiter/per-user
# User2: Success (independent quota)
```

### Test Concurrent Requests

```bash
# Scenario: Multiple simultaneous requests
for i in {1..10}; do
  curl http://localhost:8085/api/rate-limiter/basic &
done
wait

# Expected: First 5 succeed, rest fail
```

### Monitor Rate Limiter Status

```bash
# Check Actuator metrics
curl http://localhost:8085/actuator/health

# Check available permissions
curl http://localhost:8085/actuator/ratelimiters
```

---

## Quick Reference

### Configuration Properties

| Property | Description | Example |
|----------|-------------|---------|
| `limit-for-period` | Max requests in window | 100 |
| `limit-refresh-period` | Time window duration | 60s |
| `timeout-duration` | Wait time for permission | 0s or 5s |
| `register-health-indicator` | Enable Actuator monitoring | true |

### HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| 200 | Success | Within limit |
| 429 | Too Many Requests | Limit exceeded |

### Response Headers (Recommended)

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1609459200
Retry-After: 10
```

### Endpoints

```bash
# Basic rate limiter (fail-fast)
GET /api/rate-limiter/basic

# Rate limiter with wait
GET /api/rate-limiter/with-wait

# Per-user rate limiter
GET /api/rate-limiter/per-user
Header: X-USER-ID: user123

# Health check
GET /actuator/health

# Rate limiter metrics
GET /actuator/ratelimiters
```

---

## Resources

- [Resilience4j Rate Limiter Docs](https://resilience4j.readme.io/docs/ratelimiter)
- [RFC 6585 - HTTP 429 Status Code](https://tools.ietf.org/html/rfc6585)
- [IETF Draft - RateLimit Header Fields](https://datatracker.ietf.org/doc/html/draft-polli-ratelimit-headers)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

---

**Next**: [Main README](README.md) | [Retry Patterns](RETRY_README.md) | [Circuit Breaker](CIRCUIT_BREAKER_README.md) | Test the endpoints