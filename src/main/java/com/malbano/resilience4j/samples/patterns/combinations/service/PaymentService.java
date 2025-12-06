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