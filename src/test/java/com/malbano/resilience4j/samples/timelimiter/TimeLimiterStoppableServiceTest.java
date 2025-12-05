package com.malbano.resilience4j.samples.timelimiter;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.timelimiter.service.TimeLimiterStoppableService;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("TimeLimiterStoppableService Tests")
class TimeLimiterStoppableServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private TimeLimiterStoppableService timeLimiterStoppableService;

    @Autowired
    private TimeLimiterRegistry timeLimiterRegistry;

    @BeforeEach
    void setUp() {
        // Remove and recreate the time limiter to reset its state completely
        timeLimiterRegistry.remove("api-product-stoppable");
        
        // Recreate with explicit config matching application-time-limiter.yml
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build();
        
        timeLimiterRegistry.timeLimiter("api-product-stoppable", config);
    }

    @Test
    @DisplayName("Should return products when operation completes within time limit")
    void shouldReturnProducts_whenOperationCompletesWithinTimeLimit() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).description("test").build()));

        // When
        CompletableFuture<List<Product>> result = timeLimiterStoppableService.getWithStoppableCall("test");

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result.get()).isNotEmpty();
        assertThat(result.get()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ResponseStatusException when operation exceeds timeout")
    void shouldThrowResponseStatusException_whenOperationExceedsTimeout() {
        // Given
        when(productsApiClient.products(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000); // Sleep for 3 seconds (exceeds 2s timeout)
                    return List.of(Product.builder().id(1).build());
                });

        // When/Then
        assertThatThrownBy(() -> {
            CompletableFuture<List<Product>> future = timeLimiterStoppableService.getWithStoppableCall("test");
            future.get(); // Wait for completion
        })
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Request timeout");

        // Verify that the API was called
        verify(productsApiClient, times(1)).products(any());
    }

    @Test
    @DisplayName("Should cancel running future when timeout occurs")
    void shouldCancelRunningFuture_whenTimeoutOccurs() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000); // Sleep for 5 seconds (exceeds timeout)
                    return List.of(Product.builder().id(1).build());
                });

        // When
        CompletableFuture<List<Product>> future = timeLimiterStoppableService.getWithStoppableCall("test");
        
        // Wait for timeout to occur
        Thread.sleep(2500);

        // Then
        assertThat(future).isCompletedExceptionally();
        
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Should call fallback method when timeout is exceeded")
    void shouldCallFallback_whenTimeoutExceeded() {
        // Given
        when(productsApiClient.products(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000); // Exceeds 2s timeout
                    return List.of(Product.builder().id(1).build());
                });

        // When/Then
        assertThatThrownBy(() -> {
            CompletableFuture<List<Product>> future = timeLimiterStoppableService.getWithStoppableCall("test");
            future.get();
        })
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Request timeout: Operation took longer than 2 seconds");
    }

    @Test
    @DisplayName("Should return products successfully within time limit with multiple products")
    void shouldReturnProducts_whenWithinTimeLimit() throws ExecutionException, InterruptedException {
        // Given
        Product product1 = Product.builder().id(1).description("Product 1").build();
        Product product2 = Product.builder().id(2).description("Product 2").build();
        
        when(productsApiClient.products(any()))
                .thenReturn(List.of(product1, product2));

        // When
        CompletableFuture<List<Product>> result = timeLimiterStoppableService.getWithStoppableCall("test");

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0).getId()).isEqualTo(1);
        assertThat(result.get().get(1).getId()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should complete successfully when operation takes exactly 2 seconds")
    void shouldCompleteSuccessfully_whenOperationTakesExactlyTwoSeconds() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(1900); // Just under 2 seconds
                    return List.of(Product.builder().id(1).description("Fast product").build());
                });

        // When
        CompletableFuture<List<Product>> result = timeLimiterStoppableService.getWithStoppableCall("test");

        // Then
        assertThat(result.get()).isNotEmpty();
        assertThat(result.get()).hasSize(1);
        verify(productsApiClient, times(1)).products(any());
    }

    @Test
    @DisplayName("Should have timeout duration of 2 seconds configured")
    void shouldHaveTimeoutDurationConfigured() {
        // Given
        var timeLimiter = timeLimiterRegistry.timeLimiter("api-product-stoppable");

        // When
        Duration timeoutDuration = timeLimiter.getTimeLimiterConfig().getTimeoutDuration();

        // Then
        assertThat(timeoutDuration).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Should have cancel-running-future set to true")
    void shouldHaveCancelRunningFutureEnabled() {
        // Given
        var timeLimiter = timeLimiterRegistry.timeLimiter("api-product-stoppable");

        // When
        boolean cancelRunningFuture = timeLimiter.getTimeLimiterConfig().shouldCancelRunningFuture();

        // Then
        assertThat(cancelRunningFuture).isTrue();
    }

    @Test
    @DisplayName("Should handle multiple concurrent requests within time limit")
    void shouldHandleMultipleConcurrentRequests_withinTimeLimit() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()))
                .thenReturn(List.of(Product.builder().id(2).build()))
                .thenReturn(List.of(Product.builder().id(3).build()));

        // When
        CompletableFuture<List<Product>> result1 = timeLimiterStoppableService.getWithStoppableCall("test1");
        CompletableFuture<List<Product>> result2 = timeLimiterStoppableService.getWithStoppableCall("test2");
        CompletableFuture<List<Product>> result3 = timeLimiterStoppableService.getWithStoppableCall("test3");

        // Then
        assertThat(result1.get()).hasSize(1);
        assertThat(result2.get()).hasSize(1);
        assertThat(result3.get()).hasSize(1);
        verify(productsApiClient, times(3)).products(any());
    }
}