package com.malbano.resilience4j.samples.ratelimiter.service;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerUserRateLimiterService {

    private final ProductsApiClient productsApiClient;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Service demonstrating dynamic per-user rate limiting.<br>
     * Creates and manages independent rate limiter instances for each unique user.<br>
     * Configuration: application-rate-limiter.yml -> resilience4j.ratelimiter.configs.default-per-user<br>
     * Rate limiter behavior in this example:<br>
     * - Creates a separate rate limiter instance for each user ID (pattern: "user-{userId}")<br>
     * - Each user gets independent quota: 5 requests per 15-second window<br>
     * - Fails immediately (timeout-duration: 0s) when user's limit is exceeded<br>
     * - Uses programmatic API (acquirePermission) instead of annotation-based approach<br>
     * - One user reaching their limit does NOT affect other users<br>
     * - Rate limiter instances are created on-demand and cached in the registry<br>
     * - Throws ResponseStatusException with HTTP 429 (Too Many Requests) when user's limit reached<br>
     * Use case: Multi-tenant applications or APIs where each user/tenant should have<br>
     * their own independent rate limit quota. Prevents one user from consuming all<br>
     * available resources and ensures fair usage distribution across all users.<br>
     * Common scenarios: SaaS platforms, public APIs with API keys, user-specific quotas.<br>
     */
    public List<Product> getProductsByUser(String userId) {
        String limiterName = "user-" + userId;

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(
                limiterName,
                "default-per-user"
        );

        log.info("trying get permission for user: {}", userId);

        boolean permitted = rateLimiter.acquirePermission();

        if (!permitted) {
            log.error("Rate limit exceeded for user %s".formatted(userId));
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded for user %s. Please try again later.".formatted(userId)
            );
        }

        List<Product> products = productsApiClient.products("ok");

        log.info("Successfully fetched {} products", products.size());
        return products;
    }
}