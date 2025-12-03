package com.malbano.resilience4j.samples.ratelimiter.controller;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.ratelimiter.service.BasicRateLimiterService;
import com.malbano.resilience4j.samples.ratelimiter.service.PerUserRateLimiterService;
import com.malbano.resilience4j.samples.ratelimiter.service.WaitRateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rate-limiter")
@RequiredArgsConstructor
@Tag(name = "RateLimiter", description = "Demonstrates different Resilience4j Rate Limiter patterns")
public class RateLimiterController {

    private final PerUserRateLimiterService perUserRateLimiterService;
    private final BasicRateLimiterService basicRateLimiterService;
    private final WaitRateLimiterService waitRateLimiterService;

    @Operation(
            summary = "Basic rate limiter - fails immediately when limit exceeded",
            description = "Demonstrates basic rate limiter that fails fast without waiting.\n" +
                    "Protects the endpoint from being overwhelmed by limiting requests per time window.\n\n" +
                    "**Configuration:**\n" +
                    "- Limit: 5 requests per 10 seconds\n" +
                    "- Timeout: 0s (fails immediately if limit exceeded)\n" +
                    "- Behavior: Returns HTTP 429 (Too Many Requests) when limit is reached\n\n" +
                    "**Use Case:** External API rate limiting where fast failure is preferred.\n\n" +
                    "**How to Test:**\n" +
                    "1. Make 5 consecutive requests → All succeed\n" +
                    "2. Make the 6th request within 10 seconds → Returns 429 error\n" +
                    "3. Wait 10 seconds → Limit resets, requests succeed again\n\n" +
                    "**Example Scenarios:**\n" +
                    "- Requests 1-5: Success (200 OK)\n" +
                    "- Request 6: Rate limit exceeded (429)\n" +
                    "- After 10s: Limit resets"
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded. Please try again later.")
    @GetMapping("/basic")
    public ResponseEntity<List<Product>> basicRateLimit() {
        return ResponseEntity.ok(basicRateLimiterService.basicRateLimit());
    }

    @Operation(
            summary = "Rate limiter with wait - waits up to configured timeout for permission",
            description = "Demonstrates rate limiter that waits for available quota instead of failing immediately.\n" +
                    "More tolerant approach suitable for internal calls where slight delays are acceptable.\n\n" +
                    "**Configuration:**\n" +
                    "- Limit: 3 requests per 10 seconds\n" +
                    "- Timeout: 5s (waits up to 5 seconds for permission)\n" +
                    "- Behavior: Queues requests and waits if limit is reached\n\n" +
                    "**Use Case:** Internal service calls where waiting is acceptable to avoid errors.\n\n" +
                    "**How to Test:**\n" +
                    "1. Make 3 consecutive requests → All succeed immediately\n" +
                    "2. Make the 4th request within 10 seconds → Waits up to 5s for permission\n" +
                    "3. If permission granted within timeout → Success\n" +
                    "4. If timeout expires → Returns 429 error\n\n" +
                    "**Example Scenarios:**\n" +
                    "- Requests 1-3: Success immediately (200 OK)\n" +
                    "- Request 4 within 10s: Waits for next available slot\n" +
                    "  - If slot available within 5s: Success (200 OK)\n" +
                    "  - If 5s timeout expires: Rate limit exceeded (429)\n" +
                    "- After 10s: Limit resets"
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded after waiting. Please try again later.")
    @GetMapping("/with-wait")
    public ResponseEntity<List<Product>> rateLimitWithWait() {
        return ResponseEntity.ok(waitRateLimiterService.rateLimitWithWait());
    }

    @Operation(
            summary = "Per-user rate limiter - creates dynamic rate limiter instance for each user",
            description = "Demonstrates dynamic rate limiter creation based on user identification.\n" +
                    "Each user gets their own independent rate limiter instance with separate quota.\n\n" +
                    "**Configuration:**\n" +
                    "- Limit: 5 requests per 15 seconds per user\n" +
                    "- Timeout: 0s (fails immediately if user's limit exceeded)\n" +
                    "- Behavior: Tracks limits independently for each unique user ID\n\n" +
                    "**Use Case:** Multi-tenant applications where each user should have their own rate limit.\n\n" +
                    "**How to Test:**\n" +
                    "1. Send 5 requests with header `X-USER-ID: user1` → All succeed\n" +
                    "2. Send 6th request with `X-USER-ID: user1` → Returns 429 (user1's limit reached)\n" +
                    "3. Send request with `X-USER-ID: user2` → Succeeds (user2 has separate quota)\n" +
                    "4. Wait 15 seconds → user1's limit resets\n\n" +
                    "**Example Scenarios:**\n" +
                    "- User1 requests 1-5: Success (200 OK)\n" +
                    "- User1 request 6: Rate limit exceeded for user1 (429)\n" +
                    "- User2 request 1: Success (200 OK) - independent quota\n" +
                    "- User3 request 1: Success (200 OK) - independent quota\n" +
                    "- After 30s: All users' limits reset\n\n" +
                    "**Implementation Note:**\n" +
                    "Rate limiter instances are created dynamically using the pattern `user-{userId}`.\n" +
                    "All instances use the `default-per-user` configuration from application.yml."
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded for this user. Please try again later.")
    @GetMapping("/per-user")
    public ResponseEntity<List<Product>> perUserRateLimit(
            @Parameter(
                    description = "Unique user identifier for rate limiting.\n" +
                            "Each user ID gets an independent rate limit quota.",
                    example = "user1",
                    required = true
            )
            @RequestHeader("X-USER-ID") String userId
    ) {
        return ResponseEntity.ok(perUserRateLimiterService.getProductsByUser(userId));
    }
}