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

/**
 * Service demonstrating TimeLimiter with cancel-running-future = true (stoppable).
 * <p>
 * This implementation shows how to handle timeout scenarios where the background task
 * should be cancelled/interrupted when the timeout threshold is exceeded.
 * </p>
 *
 * @author Marcos Albano
 * @see TimeLimiterUnstoppableService for the opposite behavior (non-cancellable tasks)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeLimiterStoppableService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating TimeLimiter with cancel-running-future = true (stoppable).<br>
     * When timeout occurs, the future is cancelled and thread is interrupted.<br>
     * Configuration: application-time-limiter.yml -> resilience4j.timelimiter.instances.api-product-stoppable<br>
     * TimeLimiter behavior in this example:<br>
     * - Timeout: 2 seconds<br>
     * - cancel-running-future: true (thread is cancelled on timeout)<br>
     * - Throws TimeoutException when operation exceeds 2 seconds<br>
     * - Background task is cancelled/interrupted on timeout<br>
     * Use case: Operations that should be stopped if they take too long (resource conservation).<br>
     * Best practice: Recommended for most scenarios to avoid resource leaks.<br>
     */
    @TimeLimiter(name = "api-product-stoppable", fallbackMethod = "fallbackMethod")
    public CompletableFuture<List<Product>> getWithStoppableCall(String param) {
        log.info("Fetching products with stoppable time limiter (cancel-running-future=true)");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async call for param: {}", param);
            try {
                List<Product> products = productsApiClient.products(param);
                log.info("Async call completed successfully");
                return products;
            } catch (Exception e) {
                log.error("Error in async call", e);
                throw e;
            }
        });
    }

    /**
     * Fallback called when time limit is exceeded.
     * Note: With cancel-running-future=true, the original task is cancelled/interrupted.
     */
    private CompletableFuture<List<Product>> fallbackMethod(String param, Exception e) {
        log.warn("Time limiter fallback triggered for param '{}': {}", param, e.getMessage());
        log.warn("Note: Original task was cancelled/interrupted (cancel-running-future=true)");
        throw new ResponseStatusException(
                HttpStatus.REQUEST_TIMEOUT,
                "Request timeout: Operation took longer than 2 seconds"
        );
    }
}