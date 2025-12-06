package com.malbano.resilience4j.samples.patterns.combinations.controller;

import com.malbano.resilience4j.samples.commum.model.PaymentResponse;
import com.malbano.resilience4j.samples.patterns.combinations.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/combinations/payment")
@RequiredArgsConstructor
@Tag(name = "Pattern Combination: Retry + CircuitBreaker + TimeLimiter",
        description = "Demonstrates production-grade resilience patterns working together")
public class PaymentCombinationController {

    private final PaymentService paymentService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @SneakyThrows
    @Operation(
            summary = "Process payment with complete resilience protection",
            description = """
            This endpoint demonstrates how three resilience patterns work together to handle failures gracefully.
            
            EXECUTION FLOW ORDER:
            
            1. TimeLimiter (executes first)
               - Controls the TOTAL execution time for all retry attempts combined
               - Maximum duration: 7 seconds for the entire operation
               - If exceeded, cancels the execution and triggers fallback
            
            2. CircuitBreaker (executes second)
               - Monitors the service health by tracking failure patterns
               - Opens when 60% or more of the last 5 calls fail
               - When OPEN, immediately rejects calls without attempting them
               - Stays open for 10 seconds before attempting recovery
               - In HALF_OPEN state, allows 2 test calls to check if service recovered
               - Slow calls (taking more than 4 seconds) are counted as failures
            
            3. Retry (executes last)
               - Attempts to recover from transient failures
               - Maximum attempts: 3 (1 initial call + 2 retries)
               - Wait duration between attempts: 1 second
               - Only retries on server errors (500, 503, etc) and timeouts
               - Does NOT retry on client errors (400 Bad Request) or rate limits (429)
            
            TIME BUDGET CALCULATION:
            
            Total available time: 7 seconds
            
            Scenario breakdown examples:
            
            Example 1: "500-500-ok"
            - Attempt 1: HTTP 500 error (fast, ~0.1s)
            - Wait: 1 second
            - Attempt 2: HTTP 500 error (fast, ~0.1s)
            - Wait: 1 second
            - Attempt 3: HTTP 200 success (fast, ~0.1s)
            Total time: ~2.3 seconds (within 7s limit) - SUCCESS
            
            Example 2: "500-wait6"
            - Attempt 1: HTTP 500 error (fast, ~0.1s)
            - Wait: 1 second
            - Attempt 2: Slow call taking 6 seconds
            Total time: 7.1 seconds
            Result: TIMEOUT - exceeds 7 second limit and triggers fallback
            
            Example 3: "500-500-500"
            - Attempt 1: HTTP 500 error (fast, ~0.1s)
            - Wait: 1 second
            - Attempt 2: HTTP 500 error (fast, ~0.1s)
            - Wait: 1 second
            - Attempt 3: HTTP 500 error (fast, ~0.1s)
            Total time: ~2.3 seconds
            Result: All 3 attempts exhausted - triggers circuit breaker fallback
            
            CIRCUIT BREAKER BEHAVIOR:
            
            The circuit breaker needs a minimum of 5 calls to start evaluating patterns.
            
            Opening the circuit:
            - Call 1 with "503-503-503": All 3 retry attempts fail - counted as 1 failed call
            - Call 2 with "503-503-503": All 3 retry attempts fail - counted as 1 failed call
            - Call 3 with "503-503-503": All 3 retry attempts fail - counted as 1 failed call
            - Call 4 with any scenario: Still executes (only 3 failures so far)
            - Call 5 with any scenario: Still executes (only 3 or 4 failures)
            - Call 6 onwards: If failure rate reaches 60% (3 out of 5), CIRCUIT OPENS
            - Once OPEN: All subsequent calls are immediately rejected without reaching the gateway
            
            Circuit recovery:
            - After 10 seconds in OPEN state, automatically transitions to HALF_OPEN
            - Allows 2 test calls through
            - If both succeed: Circuit closes, normal operation resumes
            - If any fails: Circuit opens again for another 10 seconds
            
            Slow call detection:
            - Calls taking more than 4 seconds are considered slow
            - If 50% or more calls are slow, circuit can open
            - Example: "wait6" scenario (takes 6s) counts as a slow call
            
            RETRY LOGIC DETAILS:
            
            Maximum 3 attempts means:
            - Initial call (attempt 1)
            - First retry (attempt 2) after 1 second wait
            - Second retry (attempt 3) after another 1 second wait
            
            IMPORTANT: Retry only happens on failures. Success responses (even slow ones like wait3 or wait6) 
            do NOT trigger retries - they return immediately as successful.
            
            Retriable failures:
            - HTTP 500 (Internal Server Error)
            - HTTP 503 (Service Unavailable)
            - Timeout exceptions
            
            Non-retriable failures:
            - HTTP 400 (Bad Request) - indicates client error, retry won't help
            - HTTP 429 (Too Many Requests) - not configured to retry
            
            FALLBACK CHAIN:
            
            When failures occur:
            1. If retries are exhausted -> Circuit Breaker fallback is triggered
            2. Circuit Breaker fallback returns PENDING status with "Gateway temporarily unavailable" message
            3. If timeout occurs before retries complete -> TimeLimiter fallback takes precedence
            4. TimeLimiter fallback returns PENDING status with "Processing time exceeded SLA" message
            
            TIME CALCULATION EXAMPLES:
            
            Fast failure scenario "500-ok":
            Time = 0.1s (fail) + 1s (wait) + 0.1s (success) = 1.2s
            
            Slow success scenario "wait6":
            Time = 6s (success, NO retry triggered because it succeeded)
            Result: SUCCESS (within 7s limit, marked as slow call)
            
            Slow success scenario "wait3":
            Time = 3s (success, NO retry triggered)
            Result: SUCCESS (within 7s limit, not marked as slow since threshold is 4s)
            
            Mixed failure + slow scenario "500-500-wait6":
            Time = 0.1s (fail) + 1s (wait) + 0.1s (fail) + 1s (wait) + 6s (success) = 8.2s
            Result: TIMEOUT (exceeds 7s limit, TimeLimiter fallback triggered)
            
            All failures scenario "500-500-500":
            Time = 0.1s + 1s + 0.1s + 1s + 0.1s = 2.3s
            Result: Circuit Breaker fallback (retry exhausted)
            
            TESTING RECOMMENDATIONS:
            
            1. Use the reset parameter or call POST /reset before each test to clear circuit state
            2. Monitor circuit state with GET /status endpoint
            3. Remember that circuit breaker requires 5 calls minimum to evaluate patterns
            4. After 3 failed calls, the circuit is still CLOSED - you need at least 5-6 calls total
            5. Each endpoint call counts as ONE call for circuit breaker, regardless of retry attempts
            6. Test timeout scenarios carefully - account for wait durations between retries
            7. Observe logs to understand the exact execution flow
            
            AVAILABLE TEST SCENARIOS:
            
            Success scenarios:
            - ok: Immediate success
            - 500-ok: One retry then success
            - 500-500-ok: Two retries then success
            - wait3: Slow success (3s) - no retry, not counted as slow call
            - wait6: Very slow success (6s) - no retry, counted as slow call
            
            Timeout scenarios:
            - 500-wait6: Retry + slow call = ~7.1s exceeds limit
            - 500-500-wait6: Two retries + slow call = ~8.2s exceeds limit
            
            Circuit breaker scenarios:
            - 503-503-503: Use this scenario for 3-5 calls to build failure history
            - After 5-6 failed calls total, circuit will open (60% threshold reached)
            - Once OPEN, any scenario will be blocked immediately
            
            Slow call scenarios:
            - wait6: Single slow call (6s) - success but marked as slow (>4s threshold)
            - wait3: Moderately slow (3s) - success, NOT marked as slow (<4s threshold)
           
            """
    )
    @ApiResponse(responseCode = "200", description = "Payment processed or fallback response returned")
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @Parameter(
                    description = "Test scenario sequence using: ok, 500, 503, wait3, wait6, 429, 400",
                    example = "500-500-ok"
            )
            @RequestParam String scenario,

            @Parameter(description = "Payment amount in BRL", example = "150.00")
            @RequestParam(defaultValue = "150.00") BigDecimal amount,

            @Parameter(description = "Reset circuit breaker before processing")
            @RequestParam(defaultValue = "false") boolean reset
    ) {
        if (reset) {
            resetCircuitBreaker();
        }

        String correlationId = UUID.randomUUID().toString();

        CompletableFuture<PaymentResponse> future = paymentService.processPayment(scenario, amount, correlationId);
        return ResponseEntity.ok(future.get());
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Reset circuit breaker to initial state",
            description = """
                    Resets the payment circuit breaker to CLOSED state and clears all metrics.
                    Useful when testing different scenarios sequentially.
                    
                    When to use: Between different test runs to avoid interference from previous failures.
                    """
    )
    public ResponseEntity<Map<String, String>> reset() {
        resetCircuitBreaker();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Circuit breaker reset to CLOSED state");
        response.put("circuit", "paymentCircuit");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get circuit breaker current state and metrics",
            description = """
                    Returns real-time metrics about the payment circuit breaker.
                    
                    Circuit States:
                    - CLOSED: Normal operation, requests flow through
                    - OPEN: Circuit is blocking requests (too many failures detected)
                    - HALF_OPEN: Testing if service recovered (allowing limited test calls)
                    
                    Key Metrics:
                    - Failure rate must reach 60% to open circuit
                    - Minimum 5 calls required before evaluation
                    - Slow calls (more than 4 seconds) count as failures
                    - Circuit stays open for 10 seconds before testing recovery
                    """
    )
    public ResponseEntity<Map<String, Object>> status() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentCircuit");
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        Map<String, Object> status = new HashMap<>();
        status.put("state", cb.getState().name());
        status.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
        status.put("slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()));
        status.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        status.put("failedCalls", metrics.getNumberOfFailedCalls());
        status.put("slowCalls", metrics.getNumberOfSlowCalls());
        status.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());

        Map<String, Object> config = new HashMap<>();
        config.put("slidingWindowSize", 5);
        config.put("minimumCalls", 5);
        config.put("failureThreshold", "60%");
        config.put("slowCallThreshold", "4s");
        config.put("openStateDuration", "10s");
        status.put("configuration", config);

        Map<String, String> interpretation = new HashMap<>();
        interpretation.put("evaluation",
                metrics.getNumberOfBufferedCalls() < 5
                        ? "Not enough calls yet (need 5 minimum)"
                        : "Circuit is being evaluated");
        interpretation.put("health",
                metrics.getFailureRate() >= 60
                        ? "Unhealthy - Circuit should be OPEN"
                        : "Healthy - Circuit should be CLOSED");
        status.put("interpretation", interpretation);

        return ResponseEntity.ok(status);
    }

    private void resetCircuitBreaker() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentCircuit");
        cb.transitionToClosedState();
        cb.reset();
        log.info("🔄 Circuit breaker reset to CLOSED state for testing");
    }
}