package com.malbano.resilience4j.samples.retry.service;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicRetryService {

    private final ProductsApiClient fakeExternalApiClient;

    /**
     * Service demonstrating basic retry mechanism with fixed wait duration.
     * Configuration: application-retry.yml -> resilience4j.retry.instances.basic-retry
     * Retry behavior:
     * - Retries on any exception thrown by the external API
     * - Uses fixed wait duration between attempts
     * - No exponential backoff
     * - Falls back to empty list after max attempts exhausted
     */
    @Retry(name = "basic-retry", fallbackMethod = "fallbackMethod")
    public List<Product> basicRetryExample(String scenario) {
        log.info("Starting basic retry with scenario: {}", scenario);
        return fakeExternalApiClient.products(scenario);
    }

    private List<Product> fallbackMethod(String scenario, Exception e) {
        log.error("Fallback triggered for scenario: {} - Error: {}", scenario, e.getMessage());
        return List.of();
    }
}