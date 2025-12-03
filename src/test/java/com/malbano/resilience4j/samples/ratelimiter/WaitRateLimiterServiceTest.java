package com.malbano.resilience4j.samples.ratelimiter;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.ratelimiter.service.WaitRateLimiterService;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("WaitRateLimiterService Tests")
class WaitRateLimiterServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private WaitRateLimiterService waitRateLimiterService;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        // Remove and recreate the rate limiter to reset its state completely
        rateLimiterRegistry.remove("rate-limiter-with-wait");
        
        // Recreate with explicit config matching application-rate-limiter.yml
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(3)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        
        rateLimiterRegistry.rateLimiter("rate-limiter-with-wait", config);
    }

    @Test
    @DisplayName("Should return products when rate limit is not exceeded")
    void shouldReturnProducts_whenRateLimitNotExceeded() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).description("test").build()));

        // When
        List<Product> result = waitRateLimiterService.rateLimitWithWait();

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ResponseStatusException when rate limit is exceeded and timeout expires")
    void shouldThrowResponseStatusException_whenRateLimitExceededAndTimeoutExpires() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make requests up to the limit (3 requests per 10 seconds)
        for (int i = 0; i < 3; i++) {
            waitRateLimiterService.rateLimitWithWait();
        }

        // Then - 4th request should wait for timeout (5s) and then trigger fallback
        assertThatThrownBy(() -> waitRateLimiterService.rateLimitWithWait())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rate limit exceeded");

        // Verify that the API was called 3 times (not 4, because rate limiter blocked the 4th)
        verify(productsApiClient, times(3)).products(any());
    }

    @Test
    @DisplayName("Should wait for permission when rate limit is temporarily exceeded")
    void shouldWaitForPermission_whenRateLimitTemporarilyExceeded() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make 3 requests (exhaust limit)
        for (int i = 0; i < 3; i++) {
            waitRateLimiterService.rateLimitWithWait();
        }

        // Sleep for 6 seconds to allow refresh period to reset (6s + 5s on timeout)
        Thread.sleep(6000);

        // Then - Next request should succeed after waiting for refresh
        List<Product> result = waitRateLimiterService.rateLimitWithWait();
        
        assertThat(result).isNotEmpty();
        verify(productsApiClient, times(4)).products(any());
    }

    @Test
    @DisplayName("Should call fallback method when RequestNotPermitted is thrown")
    void shouldCallFallback_whenRequestNotPermitted() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Exhaust the rate limit
        for (int i = 0; i < 3; i++) {
            waitRateLimiterService.rateLimitWithWait();
        }

        // Then - Next request should trigger fallback after timeout
        assertThatThrownBy(() -> waitRateLimiterService.rateLimitWithWait())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rate limit exceeded. Please try again later.");
    }

    @Test
    @DisplayName("Should return products successfully within rate limit")
    void shouldReturnProducts_whenWithinRateLimit() {
        // Given
        Product product1 = Product.builder().id(1).description("Product 1").build();
        Product product2 = Product.builder().id(2).description("Product 2").build();
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(product1))
                .thenReturn(List.of(product2));

        // When - Make 2 requests within limit (limit is 3)
        List<Product> result1 = waitRateLimiterService.rateLimitWithWait();
        List<Product> result2 = waitRateLimiterService.rateLimitWithWait();

        // Then
        verify(productsApiClient, times(2)).products(any());
        assertThat(result1).hasSize(1);
        assertThat(result2).hasSize(1);
        assertThat(result1.getFirst().getId()).isEqualTo(1);
        assertThat(result2.getFirst().getId()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should allow exactly 3 requests within the limit period")
    void shouldAllowExactlyThreeRequests_withinLimitPeriod() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make exactly 3 requests (the limit)
        for (int i = 0; i < 3; i++) {
            List<Product> result = waitRateLimiterService.rateLimitWithWait();
            assertThat(result).isNotEmpty();
        }

        // Then
        verify(productsApiClient, times(3)).products(any());
    }

    @Test
    @DisplayName("Should have timeout duration of 5 seconds configured")
    void shouldHaveTimeoutDurationConfigured() {
        // Given
        var rateLimiter = rateLimiterRegistry.rateLimiter("rate-limiter-with-wait");

        // When
        Duration timeoutDuration = rateLimiter.getRateLimiterConfig().getTimeoutDuration();

        // Then
        assertThat(timeoutDuration).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should have limit of 3 requests per 10 seconds configured")
    void shouldHaveLimitConfigured() {
        // Given
        var rateLimiter = rateLimiterRegistry.rateLimiter("rate-limiter-with-wait");

        // When
        int limitForPeriod = rateLimiter.getRateLimiterConfig().getLimitForPeriod();
        Duration refreshPeriod = rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod();

        // Then
        assertThat(limitForPeriod).isEqualTo(3);
        assertThat(refreshPeriod).isEqualTo(Duration.ofSeconds(10));
    }
}