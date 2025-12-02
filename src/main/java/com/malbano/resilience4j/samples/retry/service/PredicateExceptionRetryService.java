package com.malbano.resilience4j.samples.retry.service;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredicateExceptionRetryService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating conditional retry based on HTTP status codes.<br>
     * Uses a predicate to determine which exceptions should trigger retry.<br>
     * Configuration: application-retry.yml -> resilience4j.retry.instances.retry-on-status<br>
     * Predicate Config: com.malbano.resilience4j.samples.retry.config.RetryExceptionPredicate<br>
     * Retry behavior in this example:<br>
     * - Retries ONLY on specific HTTP status codes: 429 (Too Many Requests), 500 (Internal Server Error) and 408(Timeout)<br>
     * - Does NOT retry on: 400, 404, 503, or other status codes<br>
     * - Uses fixed wait duration between attempts<br>
     * - Falls back to empty list when non-retryable error occurs or max attempts exhausted<br>
     * Use case: Selective retry for transient errors while failing fast on client errors or unavailable services<br>
     */
    @Retry(name = "retry-on-status", fallbackMethod = "fallbackMethod")
    public List<Product> retryOnHttpStatus(String scenario) {
        log.info("Attempting to fetch products with scenario: {} at {}", scenario, LocalDateTime.now());

        try {
            List<Product> products = productsApiClient.products(scenario);
            log.info("Successfully fetched {} products", products.size());
            return products;
        } catch (Exception e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw e;
        }
    }

    private List<Product> fallbackMethod(String scenario, Exception e) {
        log.error("Fallback triggered for scenario: {} - Error: {}", scenario, e.getMessage());
        return List.of();
    }
}