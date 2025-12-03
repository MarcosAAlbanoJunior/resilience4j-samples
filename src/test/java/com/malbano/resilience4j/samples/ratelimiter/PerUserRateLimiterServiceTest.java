package com.malbano.resilience4j.samples.ratelimiter;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.ratelimiter.service.PerUserRateLimiterService;
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
@DisplayName("PerUserRateLimiterService Tests")
class PerUserRateLimiterServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private PerUserRateLimiterService perUserRateLimiterService;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        // Remove all user-specific rate limiters to reset state
        rateLimiterRegistry.remove("user-user1");
        rateLimiterRegistry.remove("user-user2");
        rateLimiterRegistry.remove("user-user3");
        
        // Recreate the default-per-user config matching application-rate-limiter.yml
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(15))
                .timeoutDuration(Duration.ZERO)
                .build();
        
        // Add the config to registry so it can be used by dynamically created rate limiters
        rateLimiterRegistry.addConfiguration("default-per-user", config);
    }

    @Test
    @DisplayName("Should return products when user rate limit is not exceeded")
    void shouldReturnProducts_whenUserRateLimitNotExceeded() {
        // Given
        String userId = "user1";
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).description("test").build()));

        // When
        List<Product> result = perUserRateLimiterService.getProductsByUser(userId);

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ResponseStatusException when user rate limit is exceeded")
    void shouldThrowResponseStatusException_whenUserRateLimitExceeded() {
        // Given
        String userId = "user1";
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make requests up to the limit (5 requests per 30 seconds)
        for (int i = 0; i < 5; i++) {
            perUserRateLimiterService.getProductsByUser(userId);
        }

        // Then - 6th request should trigger rate limiter exception
        assertThatThrownBy(() -> perUserRateLimiterService.getProductsByUser(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rate limit exceeded for user " + userId);

        // Verify that the API was called 5 times (not 6, because rate limiter blocked the 6th)
        verify(productsApiClient, times(5)).products(any());
    }

    @Test
    @DisplayName("Should have independent rate limits for different users")
    void shouldHaveIndependentRateLimits_forDifferentUsers() {
        // Given
        String user1 = "user1";
        String user2 = "user2";
        
        Product product1 = Product.builder().id(1).description("Product for user1").build();
        Product product2 = Product.builder().id(2).description("Product for user2").build();
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(product1))
                .thenReturn(List.of(product2));

        // When - Make 5 requests for user1 (exhaust limit)
        for (int i = 0; i < 5; i++) {
            perUserRateLimiterService.getProductsByUser(user1);
        }

        // Then - user1 should be blocked
        assertThatThrownBy(() -> perUserRateLimiterService.getProductsByUser(user1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rate limit exceeded for user " + user1);

        // But user2 should still work (independent quota)
        List<Product> resultUser2 = perUserRateLimiterService.getProductsByUser(user2);
        assertThat(resultUser2).isNotEmpty();
    }

    @Test
    @DisplayName("Should allow multiple users to make requests within their limits")
    void shouldAllowMultipleUsers_withinTheirLimits() {
        // Given
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Each user makes 3 requests (within limit of 5)
        for (int i = 0; i < 3; i++) {
            perUserRateLimiterService.getProductsByUser(user1);
            perUserRateLimiterService.getProductsByUser(user2);
            perUserRateLimiterService.getProductsByUser(user3);
        }

        // Then - All requests should succeed
        // Total: 3 users * 3 requests = 9 API calls
        verify(productsApiClient, times(9)).products(any());
    }

    @Test
    @DisplayName("Should return products successfully within user rate limit")
    void shouldReturnProducts_whenWithinUserRateLimit() {
        // Given
        String userId = "user1";
        Product product1 = Product.builder().id(1).description("Product 1").build();
        Product product2 = Product.builder().id(2).description("Product 2").build();
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(product1))
                .thenReturn(List.of(product2));

        // When - Make 2 requests within limit
        List<Product> result1 = perUserRateLimiterService.getProductsByUser(userId);
        List<Product> result2 = perUserRateLimiterService.getProductsByUser(userId);

        // Then
        verify(productsApiClient, times(2)).products(any());
        assertThat(result1).hasSize(1);
        assertThat(result2).hasSize(1);
        assertThat(result1.get(0).getId()).isEqualTo(1);
        assertThat(result2.get(0).getId()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should create dynamic rate limiter instance for each unique user")
    void shouldCreateDynamicRateLimiterInstance_forEachUser() {
        // Given
        String user1 = "user1";
        String user2 = "user2";
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        // When - Make requests for different users
        perUserRateLimiterService.getProductsByUser(user1);
        perUserRateLimiterService.getProductsByUser(user2);

        // Then - Verify that separate rate limiter instances exist
        assertThat(rateLimiterRegistry.find("user-" + user1)).isPresent();
        assertThat(rateLimiterRegistry.find("user-" + user2)).isPresent();
        
        // And they should have independent metrics
        assertThat(rateLimiterRegistry.rateLimiter("user-" + user1).getMetrics().getAvailablePermissions())
                .isEqualTo(4); // 5 - 1 used
        assertThat(rateLimiterRegistry.rateLimiter("user-" + user2).getMetrics().getAvailablePermissions())
                .isEqualTo(4); // 5 - 1 used
    }
}