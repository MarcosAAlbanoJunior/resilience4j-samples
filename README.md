# Resilience4j Patterns - Comprehensive Sample Project

A complete demonstration of Resilience4j patterns in Spring Boot, showcasing resilience strategies for building fault-tolerant distributed systems.

## 🎯 Project Goals

This project serves as a practical reference for implementing resilience patterns using Resilience4j. Each pattern includes:
- Real-world use cases
- Multiple implementation strategies
- Configuration examples
- Testing scenarios
- Best practices and anti-patterns

## 📚 Available Patterns

### [🔄 Retry Patterns](RETRY_README.md)
Handle transient failures with intelligent retry strategies.

**Implementations:**
- Basic Retry - Fixed interval retries
- Exception Predicate Retry - Conditional retry based on HTTP status codes (429, 500, 408)
- Result Predicate Retry - Conditional retry based on response content (GENERATING status)
- Custom Interval Retry - Dynamic wait times per error

**Best for:** Network hiccups, temporary service unavailability, rate limiting, polling async operations

---

### [⚡ Circuit Breaker](CIRCUIT_BREAKER_README.md)
Prevent cascade failures by stopping requests to failing services.

**Implementations:**
- Basic Circuit Breaker with Retry - Combined pattern for maximum resilience
- Standalone Circuit Breaker - Pure circuit breaker protection
- State monitoring and metrics - Real-time circuit state tracking

**Best for:** Protecting against downstream failures, preventing resource exhaustion, failing fast when service is down

---

### [🚦 Rate Limiter](RATE_LIMITER_README.md)
Control the rate of requests to protect services from overload.

**Implementations:**
- Basic Rate Limiter - Fail-fast approach (5 req/10s, timeout: 0s)
- Rate Limiter with Wait - Queue requests and wait for permission (3 req/10s, timeout: 5s)
- Per-User Rate Limiter - Dynamic instances with independent quotas per user (5 req/15s per user)

**Best for:** API throttling, resource protection, quota management, multi-tenant systems, fair usage policies

---

### [🛡️ Bulkhead](BULKHEAD_README.md)
Isolate resources to prevent failures from cascading across your system.

**Implementations:**
- Semaphore Bulkhead - Lightweight concurrency control (3 concurrent calls, 2s wait)
- Thread Pool Bulkhead - Complete thread isolation (2-4 threads, queue: 5)

**Best for:** Multi-tenant systems, external API isolation, preventing thread starvation, resource protection, mixed workload separation

---

### [⏱️ Time Limiter](TIME_LIMITER_README.md)
Control execution time and prevent indefinite waits.

**Implementations:**
- Stoppable Time Limiter - Cancels task on timeout (2s timeout, cancel-running-future: true)
- Unstoppable Time Limiter - Task continues after timeout (2s timeout, cancel-running-future: false)

**Best for:** Long-running operations, preventing resource blocking, SLA compliance, external API calls with timeouts

---

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- Spring Boot 3.5.x

### Running the Application

```bash
# Clone the repository
git clone https://github.com/MarcosAAlbanoJunior/resilience4j-samples.git
cd resilience4j-samples

# Run with Maven
mvn spring-boot:run

# Or build and run
mvn clean package
java -jar target/resilience4j-samples-0.0.1-SNAPSHOT.jar
```

The application starts on port `8085`.

### 📖 API Documentation

Access the interactive Swagger UI to explore and test all available endpoints:
```
http://localhost:8085/swagger-ui/index.html
```

The Swagger interface provides:
- Complete API documentation
- Interactive endpoint testing
- Request/response examples
- Schema definitions

### Quick Test

```bash
# Test exception predicate retry
curl "http://localhost:8085/api/retry/with-throw-predicate?scenario=500-500-ok"

# Test result predicate retry (polling)
curl "http://localhost:8085/api/retry/with-result-predicate?scenario=generating-generating-activated"

# Test thread pool bulkhead
curl "http://localhost:8085/api/bulkhead/threadpool"

# Test time limiter stoppable
curl "http://localhost:8085/api/time-limiter/stoppable?scenario=ok"

# Test time limiter unstoppable
curl "http://localhost:8085/api/time-limiter/unstoppable?scenario=wait3"

# Check application health
curl http://localhost:8085/actuator/health
```

## 🎯 Pattern Selection Guide

Choose the right pattern based on your failure scenario:

| Scenario | Pattern | Why |
|----------|---------|-----|
| Temporary network issues | **Retry** | Issues typically resolve quickly |
| Service is down | **Circuit Breaker** | Prevent wasting resources on known failures |
| Too many concurrent requests | **Bulkhead** | Isolate and limit resource usage |
| API rate limiting | **Rate Limiter** | Control request rate proactively |
| Slow responses | **Time Limiter** | Prevent indefinite waits |
| Complex failure scenarios | **Combination** | Multiple patterns work together |

## 📧 Configuration

### Application Configuration

Main configuration in `application.yml`:

```yaml
server:
  port: 8085

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,retries,circuitbreakers,ratelimiters,bulkheads
```

### Pattern-Specific Configuration

Each pattern has its own configuration file:
- `application-retry.yml` - Retry configurations
- `application-circuit-breaker.yml` - Circuit breaker configurations
- `application-rate-limiter.yml` - Rate limiter configurations
- `application-bulkhead.yml` - Bulkhead configurations
- `application-time-limiter.yml` - Time limiter configurations
- ... and more

## 📊 Monitoring & Observability

### Actuator Endpoints

Access pattern metrics and health information:

```bash
# Application health
curl http://localhost:8085/actuator/health

# Retry metrics
curl http://localhost:8085/actuator/retries

# Circuit breaker metrics
curl http://localhost:8085/actuator/circuitbreakers

# Rate limiter metrics
curl http://localhost:8085/actuator/ratelimiters

# Bulkhead metrics
curl http://localhost:8085/actuator/bulkheads

# Thread pool bulkhead metrics
curl http://localhost:8085/actuator/threadpoolbulkheads

# Time limiter metrics
curl http://localhost:8085/actuator/timelimiters

# All metrics
curl http://localhost:8085/actuator/metrics
```

### Logging

Debug logging is enabled for Resilience4j:

```yaml
logging:
  level:
    com.malbano.resilience4j.samples: DEBUG
    io.github.resilience4j: DEBUG
```

## 🧪 Testing

### Mock API

The project includes a mock external API (`MockClientApi`) that simulates various failure scenarios:

**Available scenarios:**
- `ok` - Success response
- `500` - Internal server error
- `503` - Service unavailable
- `429` - Too many requests
- `400` - Bad request
- `404` - Not found
- `timeout` - Request timeout
- `generating` - Product with GENERATING status (triggers retry)
- `activated` - Product with ACTIVATED status (success)

**Scenario format:** `status1-status2-status3`

```bash
# Example: Fail twice with 500, then succeed
curl "http://localhost:8085/api/retry/with-throw-predicate?scenario=500-500-ok"

# Example: Poll async operation until ready
curl "http://localhost:8085/api/retry/with-result-predicate?scenario=generating-activated"
```

### HTTP Files

Use the provided `.http` files in your IDE (IntelliJ, VS Code with REST Client extension) for quick testing.

## 🎓 Learning Path

**Recommended order for learning:**

1. **Start with Retry** - Simplest pattern, foundation for understanding resilience
2. **Circuit Breaker** - Builds on retry concepts, adds state management
3. **Rate Limiter** - Proactive protection, different mindset
4. **Bulkhead** - Resource isolation, concurrent requests
5. **Time Limiter** - Timeout control, complements other patterns
6. **Combining Patterns** - Real-world scenarios using multiple patterns

## 🏆 Best Practices

### General Principles

1. **Start simple** - Begin with basic patterns, add complexity as needed
2. **Monitor everything** - Use Actuator endpoints and logging
3. **Test failure scenarios** - Don't just test happy paths
4. **Implement fallbacks** - Always have a degradation strategy
5. **Document configuration** - Explain why you chose specific values
6. **Combine patterns wisely** - Some patterns work better together

### Configuration Tips

- Set realistic timeouts based on SLAs
- Use exponential backoff for retries
- Configure circuit breaker thresholds based on traffic patterns
- Size bulkhead thread pools appropriately for expected load
- Monitor and adjust based on real-world behavior
- Consider business impact when setting limits

### Anti-Patterns to Avoid

❌ Using same configuration for all services  
❌ Not implementing fallback methods  
❌ Retrying non-idempotent operations without safeguards  
❌ Setting retry attempts too high  
❌ Not monitoring pattern effectiveness  
❌ Combining too many patterns without understanding interactions  
❌ Using semaphore bulkhead for slow blocking operations  
❌ Undersizing thread pools in bulkhead pattern

## 📄 Pattern Combinations

Real-world systems often use multiple patterns together:

### Common Combinations

**Retry + Circuit Breaker**
```
Retry for transient failures → Circuit breaker for persistent failures
```

**Rate Limiter + Bulkhead**
```
Rate limiter controls request rate → Bulkhead isolates resources
```

**Time Limiter + Retry**
```
Time limiter prevents hanging → Retry handles timeouts
```

**Bulkhead + Circuit Breaker + Retry**
```
Bulkhead isolates → Circuit breaker fails fast → Retry recovers
```

**All Patterns Together**
```
Rate Limiter → Bulkhead → Circuit Breaker → Retry → Time Limiter
```

### Example: Comprehensive Protection

```java
@Service
public class ResilientService {
    
    @RateLimiter(name = "external-api")           // Control request rate
    @Bulkhead(name = "external-api",              // Isolate resources
              type = Bulkhead.Type.THREADPOOL)
    @CircuitBreaker(name = "external-api")        // Fail fast when down
    @Retry(name = "external-api")                 // Retry transient failures
    @TimeLimiter(name = "external-api")           // Prevent hanging
    public CompletableFuture<Response> callExternalApi() {
        return externalClient.call();
    }
}
```

**Execution order:** Retry → CircuitBreaker → RateLimiter → Bulkhead → TimeLimiter → Method

## 📖 Additional Resources

### Resilience4j Documentation
- [Official Documentation](https://resilience4j.readme.io/)
- [GitHub Repository](https://github.com/resilience4j/resilience4j)
- [Spring Boot Integration](https://resilience4j.readme.io/docs/getting-started-3)

### Pattern References
- [Microsoft - Cloud Design Patterns](https://docs.microsoft.com/en-us/azure/architecture/patterns/)
- [Martin Fowler - CircuitBreaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Release It! by Michael Nygard](https://pragprog.com/titles/mnee2/release-it-second-edition/)

### Spring Boot & Observability
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Metrics](https://micrometer.io/docs)

## 🤝 Contributing

This is a sample project for educational purposes. Contributions are welcome:

1. Fork the repository
2. Create a feature branch
3. Add your pattern implementation
4. Include comprehensive documentation
5. Submit a pull request


---

## 🚀 Ready to Start?

1. **Read the pattern documentation** - Check RETRY_README.md, CIRCUIT_BREAKER_README.md, RATE_LIMITER_README.md, BULKHEAD_README.md, TIME_LIMITER_README.md
2. **Run the application** and test the endpoints
3. **Experiment with scenarios** using the mock API
4. **Monitor the metrics** through Actuator
5. **Apply to your projects** with confidence

---

**Note:** This is a demonstration project showcasing Resilience4j capabilities. In production environments, always:
- Adjust configurations based on your specific requirements
- Monitor pattern effectiveness continuously
- Consider your SLAs and business requirements
- Test thoroughly under realistic load conditions
- Document your resilience strategy

For detailed implementation guides, see the pattern-specific README files in the project root.