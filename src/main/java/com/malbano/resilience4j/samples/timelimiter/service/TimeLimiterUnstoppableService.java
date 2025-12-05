package com.malbano.resilience4j.samples.timelimiter.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeLimiterUnstoppableService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating TimeLimiter with cancel-running-future = false (unstoppable).<br>
     * When timeout occurs, the future continues running in background even after timeout exception.<br>
     * Configuration: application-time-limiter.yml -> resilience4j.timelimiter.instances.api-product-unstoppable<br>
     * TimeLimiter behavior in this example:<br>
     * - Timeout: 2 seconds<br>
     * - cancel-running-future: false (thread continues after timeout)<br>
     * - Throws TimeoutException when operation exceeds 2 seconds<br>
     * - Background task continues running even after timeout<br>
     * Use case: Operations that should complete even if client times out (logging, auditing, etc).<br>
     * Warning: Can lead to resource accumulation if many requests timeout.<br>
     */
    @TimeLimiter(name = "api-product-unstoppable", fallbackMethod = "fallbackMethod")
    public CompletableFuture<List<Product>> getWithUnstoppableCall(String param) {
        log.info("Fetching products with unstoppable time limiter (cancel-running-future=false)");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async call for param: {}", param);
            try {
                List<Product> products = productsApiClient.products(param);
                log.info("Async call completed successfully");
                log.info("return: {}", products.toString());
                return products;
            } catch (Exception e) {
                log.error("Error in async call", e);
                throw e;
            }
        });
    }

    /**
     * Fallback called when time limit is exceeded.
     * Note: With cancel-running-future=false, the original task continues running in background.
     */
    private CompletableFuture<List<Product>> fallbackMethod(String param, Exception e) {
        log.warn("Time limiter fallback triggered for param '{}': {}", param, e.getMessage());
        log.warn("Note: Original task continues running in background (cancel-running-future=false)");
        throw new ResponseStatusException(
                HttpStatus.REQUEST_TIMEOUT,
                "Request timeout: Operation took longer than 2 seconds but still running in background"
        );
    }
}