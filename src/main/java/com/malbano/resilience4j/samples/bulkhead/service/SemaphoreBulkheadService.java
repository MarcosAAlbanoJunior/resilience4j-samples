package com.malbano.resilience4j.samples.bulkhead.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemaphoreBulkheadService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating Semaphore-based Bulkhead pattern.<br>
     * Limits concurrent access to prevent resource exhaustion.<br>
     * Configuration: application-bulkhead.yml -> resilience4j.bulkhead.instances.semaphore-bulkhead<br>
     * Bulkhead behavior in this example:<br>
     * - Allows maximum 3 concurrent calls<br>
     * - Additional calls wait up to 2 seconds for permission<br>
     * - If timeout expires → throws BulkheadFullException<br>
     * - Does NOT use separate thread pool (same calling thread)<br>
     * Use case: Protecting CPU-intensive operations, database connections,<br>
     * or any shared resource with limited capacity.<br>
     */
    @Bulkhead(name = "semaphore-bulkhead", type = Bulkhead.Type.SEMAPHORE)
    public List<Product> getProductsWithSemaphore() {
        log.info("Processing request with semaphore bulkhead - Thread: {}", 
                 Thread.currentThread().getName());
        
        // Simulate heavy processing
        simulateHeavyProcessing();
        
        List<Product> products = productsApiClient.products("ok");
        log.info("Successfully fetched {} products", products.size());
        return products;
    }

    private void simulateHeavyProcessing() {
        try {
            Thread.sleep(3000); // Simulate 2s processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Product> fallbackMethod(Exception e) {
        log.warn("Semaphore bulkhead fallback triggered: {}", e.getMessage());
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service is at maximum capacity. Please try again later."
        );
    }
}