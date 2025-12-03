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
public class WaitRateLimiterService {

    private final ProductsApiClient productsApiClient;

    /**
     * Service demonstrating rate limiter with waiting mechanism for permission.<br>
     * More tolerant approach that waits for available quota before failing.<br>
     * Configuration: application-rate-limiter.yml -> resilience4j.ratelimiter.instances.rate-limiter-with-wait<br>
     * Rate limiter behavior in this example:<br>
     * - Allows 3 requests per 10-second window<br>
     * - Waits up to 5 seconds (timeout-duration: 5s) for permission when limit is exceeded<br>
     * - If permission becomes available within timeout → request proceeds<br>
     * - If timeout expires without permission → throws ResponseStatusException with HTTP 429<br>
     * - Resets the limit counter every 10 seconds (limit-refresh-period)<br>
     * - Thread blocks while waiting, queuing the request until permission is granted<br>
     * Use case: Internal service-to-service communication where waiting is acceptable<br>
     * and you want to maximize successful requests rather than failing fast.<br>
     */
    @RateLimiter(name = "rate-limiter-with-wait", fallbackMethod = "fallbackMethod")
    public List<Product> rateLimitWithWait() {
        log.info("Fetching products with rate limiter (with wait)");

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