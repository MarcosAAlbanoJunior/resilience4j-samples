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
public class CustomIntervalRetryService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating retry with dynamic intervals based on HTTP status codes.<br>
     * Each status code can have a different wait strategy before the next retry attempt.<br>
     * Configuration: application.yml -> resilience4j.retry.instances.retry-with-custom-interval<br>
     * Class config: com.malbano.resilience4j.samples.retry.config.HttpStatusRetryInterval<br>
     * Retry behavior in this example:<br>
     * - HTTP 500 (Internal Server Error): Exponential backoff strategy<br>
     * - HTTP 503 (Service Unavailable): Fixed 7 seconds wait<br>
     * - HTTP 429 (Too Many Requests): Fixed 5 seconds wait<br>
     * - Other errors: Default interval or no retry depending on configuration<br>
     * Use case: Optimized retry timing for different types of server errors.<br>
     * Allows respecting rate limits and giving overloaded servers more time to recover.<br>
     */
    @Retry(name = "retry-with-custom-interval", fallbackMethod = "fallbackMethod")
    public List<Product> retryWithCustomInterval(String scenario) {
        log.info("Executing retry-with-custom-interval...");
        return productsApiClient.products(scenario);
    }

    private List<Product> fallbackMethod(String scenario, Exception e) {
        log.error("Fallback triggered for scenario: {} - Error: {}", scenario, e.getMessage());
        return List.of();
    }
}