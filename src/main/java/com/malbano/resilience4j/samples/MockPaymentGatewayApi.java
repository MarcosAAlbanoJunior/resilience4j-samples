package com.malbano.resilience4j.samples;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.malbano.resilience4j.samples.commum.model.PaymentRequest;
import com.malbano.resilience4j.samples.commum.model.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal-api/payment")
@RequiredArgsConstructor
public class MockPaymentGatewayApi {

    private final ConcurrentHashMap<String, AtomicInteger> requestCounters = new ConcurrentHashMap<>();

    /**
     * Simulates payment gateway behavior for resilience testing.
     *
     * IMPORTANT: Each unique request (identified by X-Correlation-ID header) maintains its own execution sequence.
     * This allows multiple concurrent calls with the same scenario without interference.
     *
     * Available scenarios:
     * - ok: immediate success
     * - 500-ok: one failure then success (tests retry)
     * - 500-500-ok: two failures then success (tests retry)
     * - 503-503-503: three failures (tests circuit breaker)
     * - wait3-ok: slow response (3s) then success (tests timeout threshold)
     * - wait6: very slow (6s) - triggers TimeLimiter cancellation
     * - 429-ok: rate limit then success (tests retry on rate limit)
     * - 400: bad request - should NOT retry
     *
     * How it works with X-Correlation-ID:
     * - Request with correlation ID "req-123" and scenario "500-500-ok":
     *   Call 1 (retry 1): returns 500
     *   Call 2 (retry 2): returns 500
     *   Call 3 (retry 3): returns 200 (ok)
     *
     * - Meanwhile, another request with correlation ID "req-456" and same scenario "500-500-ok":
     *   Has completely independent counter and sequence
     */
    @SneakyThrows
    @PostMapping("/charge")
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody PaymentRequest request,
            @RequestParam(value = "scenario", defaultValue = "ok") String scenario,
            @RequestHeader(value = "X-Correlation-ID") String correlationId) {

        String counterKey = correlationId + ":" + scenario;

        AtomicInteger counter = requestCounters.computeIfAbsent(counterKey, k -> new AtomicInteger(0));

        String[] sequence = scenario.split("-");
        int attemptNumber = counter.getAndIncrement();

        if (attemptNumber >= sequence.length) {
            log.info("Correlation-ID '{}' with scenario '{}' completed sequence, resetting",
                    correlationId, scenario);
            counter.set(0);
            attemptNumber = 0;
        }

        String currentStatus = sequence[attemptNumber];
        log.info("Correlation-ID: {} | Scenario: {} | Attempt: {} | Status: {}",
                correlationId, scenario, attemptNumber + 1, currentStatus);

        switch (currentStatus) {
            case "ok":
                log.info("✅ PAYMENT SUCCESS | Correlation-ID: {}", correlationId);
                return ResponseEntity.ok(createSuccessResponse(request));

            case "wait3":
                log.warn("🌐 PAYMENT SLOW (3s) | Correlation-ID: {}", correlationId);
                Thread.sleep(3000);
                return ResponseEntity.ok(createSuccessResponse(request));

            case "wait6":
                log.warn("🌐 PAYMENT VERY SLOW (6s) | Correlation-ID: {}", correlationId);
                Thread.sleep(6000);
                return ResponseEntity.ok(createSuccessResponse(request));

            case "429":
                log.error("🚫 PAYMENT RATE LIMIT | Correlation-ID: {}", correlationId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(createErrorResponse(request, "Too many requests"));

            case "500":
                log.error("💥 PAYMENT GATEWAY ERROR | Correlation-ID: {}", correlationId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(createErrorResponse(request, "Internal server error"));

            case "503":
                log.error("🔴 PAYMENT GATEWAY UNAVAILABLE | Correlation-ID: {}", correlationId);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(createErrorResponse(request, "Service temporarily unavailable"));

            case "400":
                log.error("⚠️ PAYMENT INVALID REQUEST | Correlation-ID: {}", correlationId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse(request, "Invalid payment data"));

            default:
                log.warn("Unknown status '{}' in scenario '{}', returning success | Correlation-ID: {}",
                        currentStatus, scenario, correlationId);
                return ResponseEntity.ok(createSuccessResponse(request));
        }
    }

    private PaymentResponse createSuccessResponse(PaymentRequest request) {
        return PaymentResponse.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .status("APPROVED")
                .amount(request.getAmount())
                .message("Payment processed successfully")
                .build();
    }

    private PaymentResponse createErrorResponse(PaymentRequest request, String message) {
        return PaymentResponse.builder()
                .transactionId("TXN-FAILED-" + System.currentTimeMillis())
                .status("FAILED")
                .amount(request.getAmount())
                .message(message)
                .build();
    }
}