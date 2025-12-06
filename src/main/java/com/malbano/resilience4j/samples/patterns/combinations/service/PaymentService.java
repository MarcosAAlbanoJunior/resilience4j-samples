package com.malbano.resilience4j.samples.patterns.combinations.service;

import com.malbano.resilience4j.samples.commum.client.PaymentGatewayClient;
import com.malbano.resilience4j.samples.commum.model.PaymentRequest;
import com.malbano.resilience4j.samples.commum.model.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGatewayClient paymentGatewayClient;

    /**
     * Processes payments combining three resilience patterns in layers.<br>
     * Configuration: application-payment-example.yml<br>
     * <br>
     * Execution order (aspect-order):<br>
     * 1. TimeLimiter (order=1) - Controls maximum execution time (7s)<br>
     * 2. CircuitBreaker (order=2) - Prevents calls when gateway is unstable<br>
     * 3. Retry (order=3) - Retries on transient failures<br>
     * <br>
     * TimeLimiter:<br>
     * - 7 seconds timeout per attempt<br>
     * - Cancels execution if limit is exceeded<br>
     * - Fallback returns payment with PENDING status<br>
     * <br>
     * CircuitBreaker:<br>
     * - Sliding window of 5 calls<br>
     * - Opens circuit if 60% of calls fail<br>
     * - Remains open for 10 seconds<br>
     * - Allows 2 calls in half-open state to test recovery<br>
     * - Considers slow calls (>4s) as failure if 50% threshold is reached<br>
     * <br>
     * Retry:<br>
     * - Maximum of 3 attempts<br>
     * - Fixed interval of 1 second between attempts<br>
     * - Retries on: HttpServerErrorException, TimeoutException, RetryableException<br>
     * - Ignores: BadRequest (client error, no point in retrying)<br>
     */
    @TimeLimiter(name = "paymentTimeout", fallbackMethod = "timeoutFallback")
    @CircuitBreaker(name = "paymentCircuit", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "paymentRetry")
    public CompletableFuture<PaymentResponse> processPayment(String scenario, BigDecimal amount, String correlationId) {
        long startTime = System.currentTimeMillis();

        log.info("⏱️  TimeLimiter started | Correlation-ID: {} | Scenario: {} | Amount: {} | Max duration: 7s",
                correlationId, scenario, amount);

        return CompletableFuture.supplyAsync(() -> {
            PaymentRequest request = PaymentRequest.builder()
                    .customerId("CUST-12345")
                    .amount(amount)
                    .currency("BRL")
                    .paymentMethod("CREDIT_CARD")
                    .build();

            PaymentResponse response = paymentGatewayClient.processPayment(correlationId, request, scenario);

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Payment successful | Correlation-ID: {} | Duration: {}ms | Transaction: {}",
                    correlationId, duration, response.getTransactionId());

            return response;
        });
    }

    private CompletableFuture<PaymentResponse> timeoutFallback(
            String scenario,
            BigDecimal amount,
            Exception ex) {

        log.error("⏰ TimeLimiter fallback triggered | Scenario: {} | Reason: Execution exceeded 7s timeout | Error: {}",
                scenario, ex.getClass().getSimpleName());

        return CompletableFuture.completedFuture(
                PaymentResponse.builder()
                        .transactionId("TXN-TIMEOUT-" + System.currentTimeMillis())
                        .status("PENDING")
                        .amount(amount)
                        .message("Payment queued for retry - Processing time exceeded SLA")
                        .build()
        );
    }

    private CompletableFuture<PaymentResponse> circuitBreakerFallback(
            String scenario,
            BigDecimal amount,
            Exception ex) {

        log.error("🔴 Circuit Breaker fallback triggered | Scenario: {} | Reason: Too many failures detected | Error: {}",
                scenario, ex.getClass().getSimpleName());

        return CompletableFuture.completedFuture(
                PaymentResponse.builder()
                        .transactionId("TXN-CIRCUIT-OPEN-" + System.currentTimeMillis())
                        .status("PENDING")
                        .amount(amount)
                        .message("Payment queued - Gateway temporarily unavailable (circuit open)")
                        .build()
        );
    }
}