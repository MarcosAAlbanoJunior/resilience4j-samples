package com.malbano.resilience4j.samples.ratelimiter.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicRateLimiterService {

    private final ProductsApiClient productsApiClient;

/**
 * Service demonstrating basic rate limiter with immediate failure when limit is exceeded.<br>
 * Protects the endpoint from being overwhelmed by limiting the number of requests in a time window.<br>
 * Configuration: application-rate-limiter.yml -> resilience4j.ratelimiter.instances.basic-rate-limiter<br>
 * Rate limiter behavior in this example:<br>
 * - Allows 5 requests per 10-second window<br>
 * - Fails immediately (timeout-duration: 0s) when limit is exceeded<br>
 * - Does NOT wait for permission - rejects requests instantly<br>
 * - Throws ResponseStatusException with HTTP 429 (Too Many Requests) when limit reached<br>
 * - Resets the limit counter every 10 seconds (limit-refresh-period)<br>
 * Use case: External API rate limiting where fast failure is preferred over waiting.<br>
 * Suitable for public-facing APIs where you want to protect backend resources<br>
 * and provide immediate feedback to clients about rate limit violations.<br>
*/
    @RateLimiter(name = "basic-rate-limiter", fallbackMethod = "fallbackMethod")
    public List<Product> basicRateLimit() {
        log.info("Fetching products with basic rate limiter");

        List<Product> products = productsApiClient.products("ok");
        log.info("Successfully fetched {} products", products.size());
        return products;
    }

    /**
     * Fallback called when rate limit is exceeded.
     */
    private List<Product> fallbackMethod(Exception e) {
        log.warn("Rate limiter fallback triggered: {}", e.getMessage());
        throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please try again later."
        );
    }
}