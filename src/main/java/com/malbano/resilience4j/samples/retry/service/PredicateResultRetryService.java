package com.malbano.resilience4j.samples.retry.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredicateResultRetryService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating conditional retry based on response content (not exceptions).<br>
     * Uses a result predicate to determine which response values should trigger retry.<br>
     * Configuration: application-retry.yml -> resilience4j.retry.instances.retry-on-result<br>
     * Predicate Config: com.malbano.resilience4j.samples.retry.config.ResultRetryPredicate<br>
     * Retry behavior in this example:<br>
     * - Retries when response contains products with intermediate status: GENERATING<br>
     * - Does NOT retry when products have final statuses: ACTIVATED<br>
     * - HTTP 200 response is returned, but content indicates operation is not yet complete<br>
     * - Uses fixed wait duration between attempts<br>
     * - Falls back to empty list when max attempts exhausted or failure status detected<br>
     * Use case: Polling async operations, waiting for background jobs, document generation, or any scenario where<br>
     * the operation succeeds (HTTP 200) but the resource is not yet in the desired final state<br>
     */
    @Retry(name = "retry-on-result", fallbackMethod = "fallbackMethod")
    public Product retryOnResult(String scenario) {
        log.info("Attempting to fetch products with scenario: {} at {}", scenario, LocalDateTime.now());

        try {
            Product products = productsApiClient.productByStatus(scenario);
            log.info("Successfully fetched product");
            return products;
        } catch (Exception e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw e;
        }
    }


    /**
     * Fallback method invoked when an exception occurs during retry attempts.<br>
     * <b>Important:</b> This fallback is ONLY triggered when an exception is thrown,
     * not when max retry attempts are exhausted due to result predicate evaluation.
     */
    private Product fallbackMethod(String scenario, Exception e) {
        log.error("Fallback triggered for scenario: {} - Error: {}", scenario, e.getMessage());
        return null;
    }
}