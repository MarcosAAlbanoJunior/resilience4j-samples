# 🛡️ Bulkhead Pattern - Resilience4j Implementation Guide

## 📋 Table of Contents
- [Overview](#-overview)
- [When to Use](#-when-to-use)
- [Implementations](#-implementations)
- [Configuration](#-configuration)
- [Testing Scenarios](#-testing-scenarios)
- [Best Practices](#-best-practices)
- [Monitoring](#-monitoring)

---

## 🎯 Overview

The Bulkhead pattern isolates resources to prevent failures in one part of your system from cascading to others. Named after ship compartments that prevent the entire vessel from sinking if one section is breached.

### Key Concepts

**Problem:** A single slow or failing service can consume all available threads, starving other services.

**Solution:** Isolate critical operations in separate resource pools (bulkheads), ensuring failures remain contained.

### Real-World Analogy

Think of a restaurant with different sections:
- **Main dining area** - Regular customers
- **VIP section** - Premium customers
- **Delivery orders** - Takeout service

If delivery orders overwhelm the kitchen, the VIP section should still receive excellent service. Each section has dedicated resources (staff, tables) - this is bulkheading.

---

## ⚡ When to Use

### ✅ Perfect For

- **Multi-tenant systems** - Isolate each tenant's workload
- **External API calls** - Prevent slow APIs from blocking everything
- **Mixed workloads** - Separate critical from non-critical operations
- **Resource protection** - Prevent thread pool starvation
- **Microservices** - Isolate calls to different downstream services

### ❌ Avoid When

- Single-threaded applications
- Operations that require shared state
- Low-traffic scenarios where overhead isn't justified
- When latency is more critical than isolation

---

## 🔧 Implementations

Resilience4j provides two bulkhead implementations:

### 1. Semaphore Bulkhead (Lightweight)

**How it works:**
- Uses Java semaphores to limit concurrent calls
- Executes in the **calling thread**
- Minimal overhead, fast performance
- Simple concurrency control

**Configuration:**
```yaml
resilience4j:
  bulkhead:
    instances:
      semaphore-bulkhead:
        max-concurrent-calls: 3      # Max 3 simultaneous calls
        max-wait-duration: 2s        # Wait up to 2s for permission
```

**Use case:** Fast operations where thread isolation isn't critical.

**Example:**
```java
@Bulkhead(name = "semaphore-bulkhead", fallbackMethod = "fallbackMethod")
public List<Product> getProductsWithSemaphore() {
    log.info("Executing with semaphore bulkhead - Thread: {}", 
             Thread.currentThread().getName());
    return productsApiClient.products("ok");
}
```

**Behavior:**
- ✅ Requests 1-3: Execute immediately
- ⏳ Request 4: Waits up to 2s for a slot
- ❌ Request 5+: Rejected with `BulkheadFullException`

---

### 2. Thread Pool Bulkhead (Complete Isolation)

**How it works:**
- Uses dedicated thread pool for execution
- Executes in **separate threads** (NOT calling thread)
- Complete resource isolation
- Queue support for waiting tasks
- Returns `CompletableFuture` for async handling

**Configuration:**
```yaml
resilience4j:
  thread-pool-bulkhead:
    instances:
      thread-pool-bulkhead:
        core-thread-pool-size: 2     # 2 threads always alive
        max-thread-pool-size: 4      # Max 4 threads
        queue-capacity: 5            # 5 tasks can wait in queue
        keep-alive-duration: 20ms    # Extra thread lifetime
```

**Use case:** Slow operations, external API calls, operations that might block.

**Example:**
```java
@Bulkhead(name = "thread-pool-bulkhead", 
          fallbackMethod = "fallbackMethod", 
          type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<List<Product>> getProductsWithThreadPool() {
    log.info("Processing with thread pool bulkhead - Thread: {}", 
             Thread.currentThread().getName());
    
    return CompletableFuture.supplyAsync(() -> {
        log.info("Executing in thread pool - Thread: {}", 
                 Thread.currentThread().getName());
        simulateExternalCall(); // Slow operation
        return productsApiClient.products("ok");
    });
}
```

**Capacity calculation:**
```
Total capacity = max-thread-pool-size + queue-capacity
Example: 4 threads + 5 queue = 9 concurrent requests
```

**Behavior:**
- ✅ Requests 1-2: Execute immediately (core threads)
- ✅ Requests 3-4: Create additional threads
- ⏳ Requests 5-9: Wait in queue
- ❌ Request 10+: Rejected with `BulkheadFullException`

---

## 📊 Comparison: Semaphore vs Thread Pool

| Feature | Semaphore Bulkhead | Thread Pool Bulkhead |
|---------|-------------------|---------------------|
| **Execution** | Calling thread | Dedicated thread pool |
| **Overhead** | Very low | Higher (thread management) |
| **Isolation** | Concurrency control only | Complete thread isolation |
| **Return Type** | Synchronous | CompletableFuture (async) |
| **Queue Support** | No (just wait timeout) | Yes (configurable capacity) |
| **Best For** | Fast operations | Slow/blocking operations |
| **Resource Usage** | Minimal | Higher (dedicated threads) |
| **Thread Safety** | Caller's responsibility | Built-in isolation |

### Decision Guide

**Choose Semaphore when:**
- Operations are fast (< 100ms)
- You want minimal overhead
- Thread isolation isn't critical
- Simple concurrency limiting is enough

**Choose Thread Pool when:**
- Operations are slow (> 1s)
- Calling external APIs
- Need complete thread isolation
- Want async execution
- Have unpredictable execution times

---

## ⚙️ Configuration

### Semaphore Bulkhead Properties

```yaml
resilience4j:
  bulkhead:
    instances:
      my-semaphore:
        max-concurrent-calls: 5         # Max simultaneous executions
        max-wait-duration: 1s           # Wait time for permission
```

**Key parameters:**
- `max-concurrent-calls`: Maximum parallel executions (default: 25)
- `max-wait-duration`: How long to wait for permission (default: 0s = fail immediately)

### Thread Pool Bulkhead Properties

```yaml
resilience4j:
  thread-pool-bulkhead:
    instances:
      my-threadpool:
        core-thread-pool-size: 2        # Always-alive threads
        max-thread-pool-size: 10        # Maximum threads
        queue-capacity: 100             # Queue size for waiting tasks
        keep-alive-duration: 20ms       # Extra thread lifetime when idle
```

**Key parameters:**
- `core-thread-pool-size`: Minimum threads kept alive (default: Runtime.availableProcessors())
- `max-thread-pool-size`: Maximum threads allowed (default: Runtime.availableProcessors())
- `queue-capacity`: Queue size for waiting requests (default: 100)
- `keep-alive-duration`: How long extra threads stay alive when idle (default: 20ms)

### Configuration Tips

**For High-Throughput APIs:**
```yaml
max-thread-pool-size: 50
queue-capacity: 200
core-thread-pool-size: 20
```

**For External API Calls:**
```yaml
max-thread-pool-size: 10
queue-capacity: 50
core-thread-pool-size: 5
```

**For Critical Operations:**
```yaml
max-concurrent-calls: 3
max-wait-duration: 0s  # Fail fast
```

---

## 🧪 Testing Scenarios

### Scenario 1: Within Limit (Success)
```bash
# Semaphore: 1-3 concurrent requests succeed
curl "http://localhost:8085/api/bulkhead/semaphore"

# Thread Pool: 1-9 requests succeed (4 threads + 5 queue)
curl "http://localhost:8085/api/bulkhead/threadpool"
```

**Expected:** ✅ Immediate execution

---

### Scenario 2: At Capacity (Queued)
```bash
# Send requests up to capacity
# Semaphore: Request waits up to max-wait-duration
# Thread Pool: Request waits in queue
```

**Expected:** ⏳ Request queued, then executed

---

### Scenario 3: Over Capacity (Rejected)
```bash
# Send more requests than capacity
# Semaphore: > 3 concurrent
# Thread Pool: > 9 concurrent
```

**Expected:** ❌ `BulkheadFullException` → Fallback triggered

**Response:**
```json
{
  "error": "Service is at maximum capacity",
  "status": 503,
  "message": "Please try again later"
}
```

---

### Scenario 4: Mixed Load
```bash
# Simulate realistic traffic pattern
for i in {1..20}; do
  curl "http://localhost:8085/api/bulkhead/threadpool" &
done
```

**Expected:**
- First 9 requests: Accepted
- Remaining 11: Rejected with fallback

---

## 🎯 Best Practices

### 1. Choose the Right Type

```java
// ✅ GOOD: Thread pool for slow external calls
@Bulkhead(name = "external-api", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<Response> callExternalApi() {
    return externalClient.call();
}

// ✅ GOOD: Semaphore for fast internal operations
@Bulkhead(name = "cache-access")
public Data getCachedData() {
    return cache.get(key);
}

// ❌ BAD: Semaphore for slow blocking operations
@Bulkhead(name = "slow-operation") // Should be THREADPOOL
public Result slowBlockingCall() {
    return blockingClient.call(); // Blocks calling thread!
}
```

### 2. Always Implement Fallbacks

```java
@Bulkhead(name = "products", fallbackMethod = "fallbackProducts")
public List<Product> getProducts() {
    return apiClient.fetchProducts();
}

private List<Product> fallbackProducts(BulkheadFullException e) {
    log.warn("Bulkhead full, returning cached data");
    return cacheService.getCachedProducts(); // Degraded mode
}
```

### 3. Size Thread Pools Appropriately

**Formula for thread pool sizing:**
```
Optimal threads = (Expected latency / Avg response time) × Target throughput
```

**Example:**
- Expected latency: 2 seconds
- Average response time: 500ms
- Target: 100 req/s
- Optimal threads: (2 / 0.5) × 100 = 400 threads (too high!)

**Practical approach:**
```yaml
# Start conservative
core-thread-pool-size: 5
max-thread-pool-size: 20
queue-capacity: 100

# Monitor and adjust based on:
# - Thread utilization metrics
# - Queue depth
# - Rejection rate
# - Response times
```

### 4. Set Realistic Timeouts

```yaml
# Short timeout for fail-fast
max-wait-duration: 0s  # Don't wait, fail immediately

# Reasonable wait for user requests
max-wait-duration: 2s  # User tolerance threshold

# Longer wait for batch operations
max-wait-duration: 10s # Less time-sensitive
```

### 5. Monitor Bulkhead Health

```java
@Component
public class BulkheadMetrics {
    
    @Autowired
    private BulkheadRegistry bulkheadRegistry;
    
    @Scheduled(fixedRate = 10000)
    public void logMetrics() {
        bulkheadRegistry.getAllBulkheads().forEach(bulkhead -> {
            Bulkhead.Metrics metrics = bulkhead.getMetrics();
            log.info("Bulkhead: {}, Available: {}, Max: {}", 
                bulkhead.getName(),
                metrics.getAvailableConcurrentCalls(),
                metrics.getMaxAllowedConcurrentCalls()
            );
        });
    }
}
```

### 6. Combine with Other Patterns

```java
// Bulkhead + Circuit Breaker + Retry
@Bulkhead(name = "external-api", type = Bulkhead.Type.THREADPOOL)
@CircuitBreaker(name = "external-api")
@Retry(name = "external-api")
public CompletableFuture<Response> resilientCall() {
    return externalClient.call();
}
```

**Execution order:** Retry → CircuitBreaker → Bulkhead → Method

---

## 📈 Monitoring

### Actuator Endpoints

```bash
# Bulkhead metrics
curl http://localhost:8085/actuator/bulkheads

# Thread pool bulkhead metrics
curl http://localhost:8085/actuator/threadpoolbulkheads

# Detailed metrics
curl http://localhost:8085/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls
curl http://localhost:8085/actuator/metrics/resilience4j.bulkhead.max.allowed.concurrent.calls
```

### Key Metrics to Monitor

**Semaphore Bulkhead:**
- `available_concurrent_calls` - Free slots
- `max_allowed_concurrent_calls` - Total capacity
- Rejection rate - How often capacity is exceeded

**Thread Pool Bulkhead:**
- `core_pool_size` - Minimum threads
- `pool_size` - Current threads
- `queue_capacity` - Queue size
- `queue_depth` - Current queue usage
- `thread_pool_size` - Active threads
- Rejection rate - Overflow frequency

### Sample Metrics Response

```json
{
  "name": "thread-pool-bulkhead",
  "coreThreadPoolSize": 2,
  "maximumPoolSize": 4,
  "queueCapacity": 5,
  "queueDepth": 2,
  "threadPoolSize": 4,
  "remainingQueueCapacity": 3
}
```

### Logging

```yaml
logging:
  level:
    io.github.resilience4j.bulkhead: DEBUG
```

**Sample logs:**
```
DEBUG - Bulkhead 'thread-pool-bulkhead' permitted call
DEBUG - Bulkhead 'thread-pool-bulkhead' rejected call
INFO  - Executing in thread pool - Thread: bulkhead-thread-pool-bulkhead-1
```

---

## 🚨 Common Pitfalls

### 1. ❌ Wrong Bulkhead Type

```java
// BAD: Using semaphore for slow blocking operation
@Bulkhead(name = "external-api") // Default is SEMAPHORE
public Response callSlowApi() {
    return slowApiClient.call(); // Blocks calling thread!
}

// GOOD: Use thread pool for isolation
@Bulkhead(name = "external-api", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<Response> callSlowApi() {
    return CompletableFuture.supplyAsync(() -> slowApiClient.call());
}
```

### 2. ❌ No Fallback Method

```java
// BAD: No graceful degradation
@Bulkhead(name = "products")
public List<Product> getProducts() {
    return apiClient.fetch(); // Users see 503 errors!
}

// GOOD: Provide fallback
@Bulkhead(name = "products", fallbackMethod = "fallbackProducts")
public List<Product> getProducts() {
    return apiClient.fetch();
}

private List<Product> fallbackProducts(BulkheadFullException e) {
    return cacheService.getCached(); // Degrade gracefully
}
```

### 3. ❌ Undersized Thread Pool

```yaml
# BAD: Too small for load
thread-pool-bulkhead:
  instances:
    api-calls:
      max-thread-pool-size: 2  # Only 2 threads for 100 req/s!
      queue-capacity: 5        # Queue fills immediately

# GOOD: Sized for expected load
thread-pool-bulkhead:
  instances:
    api-calls:
      max-thread-pool-size: 20
      queue-capacity: 100
```

### 4. ❌ Not Returning CompletableFuture

```java
// BAD: Thread pool bulkhead must return CompletableFuture
@Bulkhead(name = "api", type = Bulkhead.Type.THREADPOOL)
public List<Product> getProducts() { // Wrong return type!
    return apiClient.fetch();
}

// GOOD: Async return type
@Bulkhead(name = "api", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<List<Product>> getProducts() {
    return CompletableFuture.supplyAsync(() -> apiClient.fetch());
}
```

### 5. ❌ Sharing Bulkhead Across Unrelated Operations

```java
// BAD: Same bulkhead for different services
@Bulkhead(name = "shared") // Don't share!
public Response callServiceA() { }

@Bulkhead(name = "shared") // Same bulkhead!
public Response callServiceB() { }
// Problem: ServiceA failure affects ServiceB availability

// GOOD: Separate bulkheads
@Bulkhead(name = "service-a")
public Response callServiceA() { }

@Bulkhead(name = "service-b")
public Response callServiceB() { }
```

---

## 🎓 Real-World Examples

### Multi-Tenant SaaS

```java
@Service
public class TenantService {
    
    // Premium tier: More resources
    @Bulkhead(name = "premium-tenant", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Report> generatePremiumReport(String tenantId) {
        return reportGenerator.generate(tenantId);
    }
    
    // Standard tier: Limited resources
    @Bulkhead(name = "standard-tenant", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Report> generateStandardReport(String tenantId) {
        return reportGenerator.generate(tenantId);
    }
}
```

```yaml
thread-pool-bulkhead:
  instances:
    premium-tenant:
      max-thread-pool-size: 20
      queue-capacity: 100
    standard-tenant:
      max-thread-pool-size: 5
      queue-capacity: 20
```

### E-commerce Platform

```java
@Service
public class OrderService {
    
    // Critical: Payment processing (small, dedicated pool)
    @Bulkhead(name = "payment-processing", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<PaymentResult> processPayment(Order order) {
        return paymentGateway.charge(order);
    }
    
    // Non-critical: Email notifications (larger pool, can fail)
    @Bulkhead(name = "notifications", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Void> sendNotification(Order order) {
        return emailService.send(order);
    }
}
```

---

## 📚 Additional Resources

- [Resilience4j Bulkhead Documentation](https://resilience4j.readme.io/docs/bulkhead)
- [Release It! - Bulkhead Pattern](https://pragprog.com/titles/mnee2/)
- [Spring Boot Resilience4j Integration](https://resilience4j.readme.io/docs/getting-started-3)

---

## 🎯 Summary

**Bulkhead Pattern in one sentence:**
> Isolate resources into separate pools to prevent failures from cascading across your system.

**Key Takeaways:**
- ✅ Use **Semaphore** for fast operations
- ✅ Use **Thread Pool** for slow/blocking operations
- ✅ Always implement **fallback methods**
- ✅ Size pools based on **actual load patterns**
- ✅ **Monitor metrics** continuously
- ✅ Combine with **Circuit Breaker** for robust protection

**Next Steps:**
1. Implement bulkhead for your slowest operations
2. Monitor metrics and adjust sizing
3. Add fallback strategies
4. Combine with circuit breaker for critical paths

---

*Ready to prevent cascade failures? Start with Thread Pool Bulkhead for your external API calls! 🚀*