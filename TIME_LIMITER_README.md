# ⏱️ Time Limiter Pattern

Control execution time and prevent indefinite waits in your Spring Boot applications using Resilience4j Time Limiter.

## 📋 Table of Contents

- [Overview](#-overview)
- [When to Use](#-when-to-use)
- [Implementations](#-implementations)
- [Configuration](#-configuration)
- [How to Test](#-how-to-test)
- [Best Practices](#-best-practices)
- [Common Pitfalls](#-common-pitfalls)
- [Monitoring](#-monitoring)
- [Real-World Examples](#-real-world-examples)

## 🎯 Overview

Time Limiter is a Resilience4j pattern that prevents operations from running indefinitely by enforcing time constraints. It wraps asynchronous operations and ensures they complete within a specified timeout, throwing a `TimeoutException` if the operation exceeds the configured duration.

### Key Features

- ⏰ **Timeout Control** - Set maximum execution time for operations
- 🛑 **Cancellation Options** - Choose whether to cancel running tasks on timeout
- 🔄 **Fallback Support** - Handle timeouts gracefully with fallback methods
- 📊 **Metrics Integration** - Monitor timeout rates and operation durations
- 🔌 **Async Operations** - Works with CompletableFuture for non-blocking execution

### How It Works

```
Client Request → Time Limiter → Async Operation
                      ↓
              [2 seconds timeout]
                      ↓
    ┌─────────────────┴─────────────────┐
    │                                   │
 Success                             Timeout
    │                                   │
    ↓                                   ↓
Return Result                    Fallback Method
                                        ↓
                                 HTTP 408 Timeout
```

## 🎯 When to Use

### ✅ Use Time Limiter When

- Calling external APIs with unpredictable response times
- Implementing SLA compliance (e.g., "all requests must respond within 2s")
- Preventing thread starvation from slow operations
- Protecting against hanging database queries
- Controlling resource consumption in async operations
- Building responsive user interfaces that can't wait indefinitely

### ❌ Don't Use Time Limiter When

- Operations are synchronous and blocking (use plain timeouts instead)
- Time requirements are highly variable and unpredictable
- You need to wait for critical operations to complete no matter how long
- The operation cannot be interrupted safely

## 🔧 Implementations

### 1. Stoppable Time Limiter (Recommended)

**Configuration:** `cancel-running-future = true`

When timeout occurs, the background task is **cancelled/interrupted**.

#### Implementation

```java
@Service
@RequiredArgsConstructor
public class TimeLimiterStoppableService {
    
    private final ProductsApiClient productsApiClient;

    @TimeLimiter(name = "api-product-stoppable", fallbackMethod = "fallbackMethod")
    public CompletableFuture<List<Product>> getWithStoppableCall(String param) {
        return CompletableFuture.supplyAsync(() -> {
            List<Product> products = productsApiClient.products(param);
            return products;
        });
    }

    private CompletableFuture<List<Product>> fallbackMethod(String param, Exception e) {
        throw new ResponseStatusException(
            HttpStatus.REQUEST_TIMEOUT,
            "Request timeout: Operation took longer than 2 seconds"
        );
    }
}
```

#### Configuration

```yaml
resilience4j:
  timelimiter:
    instances:
      api-product-stoppable:
        timeout-duration: 2s
        cancel-running-future: true  # Cancels the task on timeout
```

#### Behavior

| Aspect | Behavior |
|--------|----------|
| **On Timeout** | Task is cancelled/interrupted |
| **Resource Usage** | ✅ Resources freed immediately |
| **Thread State** | Thread is interrupted |
| **Use Case** | Most operations (recommended) |
| **Best For** | Regular API calls, user-facing operations |

#### Test Endpoint

```bash
# Success - completes within 2s
curl "http://localhost:8085/api/time-limiter/stoppable?scenario=ok"

# Timeout - exceeds 2s, task is cancelled
curl "http://localhost:8085/api/time-limiter/stoppable?scenario=wait3"
```

---

### 2. Unstoppable Time Limiter

**Configuration:** `cancel-running-future = false`

When timeout occurs, the background task **continues running**.

#### Implementation

```java
@Service
@RequiredArgsConstructor
public class TimeLimiterUnstoppableService {
    
    private final ProductsApiClient productsApiClient;

    @TimeLimiter(name = "api-product-unstoppable", fallbackMethod = "fallbackMethod")
    public CompletableFuture<List<Product>> getWithUnstoppableCall(String param) {
        return CompletableFuture.supplyAsync(() -> {
            List<Product> products = productsApiClient.products(param);
            // This completes even if client times out
            return products;
        });
    }

    private CompletableFuture<List<Product>> fallbackMethod(String param, Exception e) {
        throw new ResponseStatusException(
            HttpStatus.REQUEST_TIMEOUT,
            "Request timeout: Operation took longer than 2 seconds but still running in background"
        );
    }
}
```

#### Configuration

```yaml
resilience4j:
  timelimiter:
    instances:
      api-product-unstoppable:
        timeout-duration: 2s
        cancel-running-future: false  # Task continues after timeout
```

#### Behavior

| Aspect | Behavior |
|--------|----------|
| **On Timeout** | Task continues in background |
| **Resource Usage** | ⚠️ Resources held until completion |
| **Thread State** | Thread keeps running |
| **Use Case** | Critical operations that must complete |
| **Best For** | Audit logs, financial transactions, data consistency |

#### Test Endpoint

```bash
# Success - completes within 2s
curl "http://localhost:8085/api/time-limiter/unstoppable?scenario=ok"

# Timeout - exceeds 2s, but task completes in background (check logs!)
curl "http://localhost:8085/api/time-limiter/unstoppable?scenario=wait3"
```

**Important:** With unstoppable mode, check the application logs after timeout - you'll see the operation completed successfully even though the client received a 408 error.

---

## 📊 Configuration Details

### Complete Configuration Example

```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 2s
        cancel-running-future: true
    instances:
      fast-api:
        base-config: default
        timeout-duration: 1s
      
      slow-api:
        base-config: default
        timeout-duration: 5s
        cancel-running-future: false
      
      critical-operation:
        timeout-duration: 10s
        cancel-running-future: false
```

### Configuration Properties

| Property | Description | Default | Recommended |
|----------|-------------|---------|-------------|
| `timeout-duration` | Max time to wait for operation | 1s | 2-5s (based on SLA) |
| `cancel-running-future` | Cancel task on timeout | true | true (most cases) |

### Choosing Timeout Duration

```
Timeout = P95 Response Time + Safety Margin

Example:
- P95 = 1.5s
- Safety Margin = 0.5s
- Timeout = 2s
```

## 🧪 How to Test

### Available Scenarios

| Scenario | Response Time | Expected Result |
|----------|---------------|-----------------|
| `ok` | Immediate (< 100ms) | ✅ 200 OK - Returns products |
| `wait3` | 3 seconds | ⏱️ 408 Timeout - Exceeds 2s limit |

### Test Commands

```bash
# 1. Stoppable - Success
curl "http://localhost:8085/api/time-limiter/stoppable?scenario=ok"
# Expected: 200 OK with products

# 2. Stoppable - Timeout (task cancelled)
curl "http://localhost:8085/api/time-limiter/stoppable?scenario=wait3"
# Expected: 408 Timeout
# Check logs: Task was cancelled

# 3. Unstoppable - Success
curl "http://localhost:8085/api/time-limiter/unstoppable?scenario=ok"
# Expected: 200 OK with products

# 4. Unstoppable - Timeout (task continues)
curl "http://localhost:8085/api/time-limiter/unstoppable?scenario=wait3"
# Expected: 408 Timeout
# Check logs: Task completed in background!
```

### Testing with .http Files

```http
### Test Stoppable Time Limiter - Success
GET http://localhost:8085/api/time-limiter/stoppable?scenario=ok

### Test Stoppable Time Limiter - Timeout
GET http://localhost:8085/api/time-limiter/stoppable?scenario=wait3

### Test Unstoppable Time Limiter - Success
GET http://localhost:8085/api/time-limiter/unstoppable?scenario=ok

### Test Unstoppable Time Limiter - Timeout (check logs!)
GET http://localhost:8085/api/time-limiter/unstoppable?scenario=wait3
```

### Verifying Behavior

**For Stoppable Mode:**
```bash
# After timeout, check logs - you should see:
# "Original task was cancelled/interrupted"
```

**For Unstoppable Mode:**
```bash
# After timeout, wait 3+ seconds and check logs - you should see:
# "Original task continues running in background"
# "Async call completed successfully"
```

## 🏆 Best Practices

### 1. Choose the Right Mode

```java
// ✅ Use Stoppable for most cases
@TimeLimiter(name = "regular-api", fallbackMethod = "handleTimeout")
public CompletableFuture<Response> callApi() {
    return CompletableFuture.supplyAsync(() -> apiClient.call());
}

// ✅ Use Unstoppable for critical operations
@TimeLimiter(name = "audit-log", fallbackMethod = "handleTimeout")
public CompletableFuture<Void> writeAuditLog() {
    return CompletableFuture.supplyAsync(() -> {
        auditService.log(event); // Must complete
        return null;
    });
}
```

### 2. Always Implement Fallbacks

```java
private CompletableFuture<List<Product>> fallbackMethod(String param, Exception e) {
    log.warn("Timeout occurred for param: {}", param);
    
    // Option 1: Return cached data
    return CompletableFuture.completedFuture(cache.get(param));
    
    // Option 2: Return empty result
    return CompletableFuture.completedFuture(Collections.emptyList());
    
    // Option 3: Throw custom exception
    throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Operation timed out");
}
```

### 3. Set Realistic Timeouts

```yaml
# ❌ Too short - will timeout too often
timeout-duration: 100ms

# ✅ Based on metrics - P95 + margin
timeout-duration: 2s

# ❌ Too long - defeats the purpose
timeout-duration: 30s
```

### 4. Monitor and Adjust

```java
// Use metrics to tune timeouts
@Scheduled(fixedRate = 60000)
public void monitorTimeouts() {
    TimeLimiterRegistry registry = timeLimiterRegistry;
    registry.getAllTimeLimiters().forEach(timeLimiter -> {
        Metrics metrics = timeLimiter.getMetrics();
        log.info("TimeLimiter {}: timeouts={}", 
            timeLimiter.getName(), 
            metrics.getNumberOfFailedCalls()
        );
    });
}
```

### 5. Combine with Other Patterns

```java
// Time Limiter + Retry + Circuit Breaker
@Retry(name = "external-api")
@CircuitBreaker(name = "external-api")
@TimeLimiter(name = "external-api")
public CompletableFuture<Response> resilientCall() {
    return CompletableFuture.supplyAsync(() -> apiClient.call());
}
```

## ⚠️ Common Pitfalls

### 1. ❌ Using Blocking Operations

```java
// ❌ WRONG - Blocks the thread
@TimeLimiter(name = "api")
public CompletableFuture<Response> badExample() {
    Response response = apiClient.blockingCall(); // Blocks!
    return CompletableFuture.completedFuture(response);
}

// ✅ CORRECT - Truly async
@TimeLimiter(name = "api")
public CompletableFuture<Response> goodExample() {
    return CompletableFuture.supplyAsync(() -> apiClient.call());
}
```

### 2. ❌ Not Handling InterruptedException

```java
// ❌ WRONG - Ignores interruption
return CompletableFuture.supplyAsync(() -> {
    try {
        return slowOperation();
    } catch (InterruptedException e) {
        // Ignoring interruption!
        return null;
    }
});

// ✅ CORRECT - Properly handles interruption
return CompletableFuture.supplyAsync(() -> {
    try {
        return slowOperation();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore flag
        throw new RuntimeException("Operation interrupted", e);
    }
});
```

### 3. ❌ Using Unstoppable for Everything

```java
// ❌ WRONG - Resource leak risk
resilience4j:
  timelimiter:
    configs:
      default:
        cancel-running-future: false  # Bad default!

// ✅ CORRECT - Use stoppable as default
resilience4j:
  timelimiter:
    configs:
      default:
        cancel-running-future: true  # Good default
    instances:
      critical-ops:
        cancel-running-future: false  # Only where needed
```

### 4. ❌ Setting Timeout = SLA

```java
// ❌ WRONG - No margin for processing
timeout-duration: 2s  // SLA is exactly 2s

// ✅ CORRECT - Leave margin
timeout-duration: 1.8s  // SLA is 2s, timeout at 1.8s
```

## 📊 Monitoring

### Actuator Endpoints

```bash
# Time limiter metrics
curl http://localhost:8085/actuator/timelimiters

# Specific time limiter
curl http://localhost:8085/actuator/timelimiters/api-product-stoppable

# Prometheus metrics
curl http://localhost:8085/actuator/prometheus | grep timelimiter
```

### Key Metrics

```yaml
# Number of successful calls within timeout
resilience4j_timelimiter_calls_seconds_count{name="api-product-stoppable",outcome="success"}

# Number of timed out calls
resilience4j_timelimiter_calls_seconds_count{name="api-product-stoppable",outcome="timeout"}

# Call duration percentiles
resilience4j_timelimiter_calls_seconds{name="api-product-stoppable",quantile="0.95"}
```

### Logging Configuration

```yaml
logging:
  level:
    com.malbano.resilience4j.samples.timelimiter: DEBUG
    io.github.resilience4j.timelimiter: DEBUG
```

### Log Output Examples

**Stoppable Mode - Timeout:**
```
Fetching products with stoppable time limiter (cancel-running-future=true)
Starting async call for param: wait3
Time limiter fallback triggered for param 'wait3': TimeoutException
Note: Original task was cancelled/interrupted (cancel-running-future=true)
```

**Unstoppable Mode - Timeout:**
```
Fetching products with unstoppable time limiter (cancel-running-future=false)
Starting async call for param: wait3
Time limiter fallback triggered for param 'wait3': TimeoutException
Note: Original task continues running in background (cancel-running-future=false)
Async call completed successfully  # ← Appears later, after timeout!
```

## 🌍 Real-World Examples

### Example 1: External API with SLA

```java
@Service
public class PaymentService {
    
    @TimeLimiter(name = "payment-gateway", fallbackMethod = "paymentTimeout")
    public CompletableFuture<PaymentResult> processPayment(Payment payment) {
        return CompletableFuture.supplyAsync(() -> {
            // Must respond within 3s per SLA
            return paymentGateway.charge(payment);
        });
    }
    
    private CompletableFuture<PaymentResult> paymentTimeout(Payment payment, Exception e) {
        // Return pending status, process async
        asyncPaymentQueue.add(payment);
        return CompletableFuture.completedFuture(
            PaymentResult.pending("Payment processing, check status later")
        );
    }
}
```

```yaml
resilience4j:
  timelimiter:
    instances:
      payment-gateway:
        timeout-duration: 2.5s  # SLA is 3s, timeout at 2.5s
        cancel-running-future: false  # Let payment complete
```

### Example 2: Database Query Timeout

```java
@Service
public class ReportService {
    
    @TimeLimiter(name = "complex-report", fallbackMethod = "reportTimeout")
    public CompletableFuture<Report> generateReport(ReportRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            return reportRepository.complexQuery(request);
        });
    }
    
    private CompletableFuture<Report> reportTimeout(ReportRequest request, Exception e) {
        // Queue for async generation
        reportQueue.add(request);
        return CompletableFuture.completedFuture(
            Report.queued("Report queued, will be emailed when ready")
        );
    }
}
```

### Example 3: Microservice Communication

```java
@Service
public class OrderService {
    
    @TimeLimiter(name = "inventory-check", fallbackMethod = "inventoryTimeout")
    @Retry(name = "inventory-check")
    @CircuitBreaker(name = "inventory-check")
    public CompletableFuture<InventoryStatus> checkInventory(String productId) {
        return CompletableFuture.supplyAsync(() -> {
            return inventoryClient.check(productId);
        });
    }
    
    private CompletableFuture<InventoryStatus> inventoryTimeout(String productId, Exception e) {
        // Fallback to cached status
        return CompletableFuture.completedFuture(
            inventoryCache.getOrDefault(productId, InventoryStatus.UNKNOWN)
        );
    }
}
```

### Example 4: User-Facing API

```java
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    @GetMapping
    public CompletableFuture<SearchResults> search(@RequestParam String query) {
        return searchService.search(query)
            .orTimeout(500, TimeUnit.MILLISECONDS)  // Hard limit for UX
            .exceptionally(e -> SearchResults.partial("Search timed out, showing partial results"));
    }
}
```

## 🔄 Pattern Comparison

### Time Limiter vs Circuit Breaker

| Aspect | Time Limiter | Circuit Breaker |
|--------|--------------|-----------------|
| **Purpose** | Prevent hanging | Prevent cascade failures |
| **Trigger** | Time exceeds threshold | Failure rate exceeds threshold |
| **Scope** | Single request | Multiple requests |
| **State** | Stateless | Stateful (OPEN/CLOSED/HALF_OPEN) |
| **Recovery** | Immediate | After wait duration |

### Time Limiter vs Timeout

| Aspect | Time Limiter | Plain Timeout |
|--------|--------------|---------------|
| **Type** | Async (CompletableFuture) | Can be sync or async |
| **Cancellation** | Configurable | Depends on implementation |
| **Metrics** | Built-in | Manual |
| **Fallback** | Integrated | Manual |
| **Retry Integration** | Seamless | Manual |

## 📚 Additional Resources

- [Resilience4j TimeLimiter Documentation](https://resilience4j.readme.io/docs/timeout)
- [CompletableFuture JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
- [Reactive Programming Guide](https://www.reactivemanifesto.org/)

## 🎯 Quick Reference

### Decision Matrix

| Requirement | Configuration |
|-------------|---------------|
| Most operations | `cancel-running-future: true` |
| Critical operations (audit, transactions) | `cancel-running-future: false` |
| Fast APIs (< 1s SLA) | `timeout-duration: 500ms-800ms` |
| Slow APIs (2-5s SLA) | `timeout-duration: 1.5s-4s` |
| User-facing operations | `timeout-duration: 1s-2s` |
| Background jobs | `timeout-duration: 5s-30s` |

### Testing Checklist

- [ ] Test success within timeout
- [ ] Test timeout scenario
- [ ] Test fallback execution
- [ ] Verify task cancellation (stoppable)
- [ ] Verify task completion (unstoppable)
- [ ] Check metrics collection
- [ ] Verify logging output
- [ ] Test under load
- [ ] Test with other patterns (Retry, Circuit Breaker)

---

**Next Steps:**
1. Explore [Retry Pattern](RETRY_README.md) for handling transient failures
2. Learn about [Circuit Breaker](CIRCUIT_BREAKER_README.md) for preventing cascade failures
3. Study [Bulkhead Pattern](BULKHEAD_README.md) for resource isolation
4. See [Main README](README.md) for combining patterns

---

*For questions or contributions, please refer to the main project README.*