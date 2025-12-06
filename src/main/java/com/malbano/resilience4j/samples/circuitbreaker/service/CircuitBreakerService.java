package com.malbano.resilience4j.samples.circuitbreaker.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final ProductsApiClient productsApiClient;

    @CircuitBreaker(name = "products-cb", fallbackMethod = "fallbackMethod")
    public List<Product> getProducts(Boolean success) {
        log.info("Fetching products from API with success: {}", success);
        String param = success ? "ok" : "429";
        
        try {
            List<Product> products = productsApiClient.products(param);
            
            log.info("Successfully fetched {} products", products.size());
            return products;
        } catch (HttpStatusException e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fallback called when:
     * - Circuit is OPEN (too many failures)
     * - Circuit is HALF_OPEN and call fails
     * - All retry attempts exhausted
     */
    private List<Product> fallbackMethod(Boolean success, Exception e) {
        log.warn("Circuit breaker fallback triggered for scenario: {} - Reason: {}",
                success, e.getMessage());
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Service temporarily unavailable due to circuit breaker"
        );
    }
}