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
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolBulkheadService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating Thread Pool Bulkhead pattern.<br>
     * Executes operations in isolated thread pool to prevent thread starvation.<br>
     * Configuration: application-bulkhead.yml -> resilience4j.thread-pool-bulkhead.instances.thread-pool-bulkhead<br>
     * Bulkhead behavior in this example:<br>
     * - Uses dedicated thread pool with 2 core threads, max 4 threads<br>
     * - Queue capacity: 10 waiting tasks<br>
     * - If pool + queue full → throws BulkheadFullException<br>
     * - Executes in separate thread (NOT calling thread)<br>
     * - Returns CompletableFuture for async processing<br>
     * Use case: Isolating slow external API calls, preventing one service<br>
     * from consuming all application threads.<br>
     */
    @Bulkhead(name = "thread-pool-bulkhead", fallbackMethod = "fallbackMethod", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<List<Product>> getProductsWithThreadPool() {
        log.info("Processing request with thread pool bulkhead - Thread: {}", 
                 Thread.currentThread().getName());
        
        return CompletableFuture.supplyAsync(() -> {
            log.info("Executing in thread pool - Thread: {}", 
                     Thread.currentThread().getName());
            
            // Simulate external API call
            simulateExternalCall();
            
            List<Product> products = productsApiClient.products("ok");
            log.info("Successfully fetched {} products", products.size());
            return products;
        });
    }

    private void simulateExternalCall() {
        try {
            Thread.sleep(3000); // Simulate slow external API
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<List<Product>> fallbackMethod(Exception e) {
        log.warn("Thread Pool bulkhead fallback triggered: {}", e.getMessage());
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service is at maximum capacity. Please try again later."
        );
    }
}