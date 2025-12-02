package com.malbano.resilience4j.samples.circuitbreak.controller;

import com.malbano.resilience4j.samples.circuitbreak.service.CircuitBreakService;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/circuit-break")
@RequiredArgsConstructor
public class CircuitBreakController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CircuitBreakService circuitBreakService;

    @Operation(
            summary = "Demonstrates Circuit Breaker in action",
            description = """
              ⚠️ How to test this endpoint:\n
              - 1st call with success=true → returns 200 OK
              - 2nd call with success=false → returns ERROR (400)
              - 3rd call with success=false → returns ERROR (400) and enters OPEN state because more than 66% failed in the last 3 calls
              - 4th call with success=true → returns ERROR (400) because Circuit Breaker is OPEN
        
              After 10 seconds:\n
              - The Circuit Breaker enters HALF-OPEN state
              - If the next 2 calls succeed → it returns to CLOSED
              - If any call fails → it goes back to OPEN
              \nSee the circuit break status at the endpoint circuit-break/status
            """
    )
    @GetMapping
    public ResponseEntity<List<Product>> circuitBreak(@RequestParam Boolean success) {
        return ResponseEntity.ok(circuitBreakService.getProducts(success));
    }

    @Operation(
            summary = "Returns the current state of a Circuit Breaker",
            description = """
                Provide the Circuit Breaker configured in your application.\n

                Response includes:\n
                - Current state (CLOSED, OPEN, HALF_OPEN)
                - Failure count
                - Success count
                - Failure rate
                - Last state transition timestamp
                """
    )
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
