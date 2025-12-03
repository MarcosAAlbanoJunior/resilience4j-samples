package com.malbano.resilience4j.samples.ratelimiter;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.ratelimiter.service.BasicRateLimiterService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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
@DisplayName("BasicRateLimiterService Tests")
class BasicRateLimiterServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private BasicRateLimiterService basicRateLimiterService;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        // Remove and recreate the rate limiter to reset its state completely
        rateLimiterRegistry.remove("basic-rate-limiter");
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();
        rateLimiterRegistry.rateLimiter("basic-rate-limiter", config);
    }

    @Test
    @DisplayName("Should return products when rate limit is not exceeded")
    void shouldReturnProducts_whenRateLimitNotExceeded() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).description("test").build()));

        // When
        List<Product> result = basicRateLimiterService.basicRateLimit();

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ResponseStatusException when rate limit is exceeded")
    void shouldThrowResponseStatusException_whenRateLimitExceeded() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make requests up to the limit (5 requests per 10 seconds)
        for (int i = 0; i < 5; i++) {
            basicRateLimiterService.basicRateLimit();
        }

        // Then - 6th request should trigger rate limiter fallback
        assertThatThrownBy(() -> basicRateLimiterService.basicRateLimit())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rate limit exceeded");

        // Verify that the API was called 5 times (not 6, because rate limiter blocked the 6th)
        verify(productsApiClient, times(5)).products(any());
    }

    @Test
    @DisplayName("Should call fallback method when RequestNotPermitted is thrown")
    void shouldCallFallback_whenRequestNotPermitted() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Exhaust the rate limit
        for (int i = 0; i < 5; i++) {
            basicRateLimiterService.basicRateLimit();
        }

        // Then - Next request should trigger fallback
        assertThatThrownBy(() -> basicRateLimiterService.basicRateLimit())
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

        // When - Make 2 requests within limit
        List<Product> result1 = basicRateLimiterService.basicRateLimit();
        List<Product> result2 = basicRateLimiterService.basicRateLimit();

        // Then
        verify(productsApiClient, times(2)).products(any());
        assertThat(result1).hasSize(1);
        assertThat(result2).hasSize(1);
        assertThat(result1.get(0).getId()).isEqualTo(1);
        assertThat(result2.get(0).getId()).isEqualTo(2);
    }
}