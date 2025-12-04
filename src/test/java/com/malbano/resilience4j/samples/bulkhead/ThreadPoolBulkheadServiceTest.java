package com.malbano.resilience4j.samples.bulkhead;

import com.malbano.resilience4j.samples.bulkhead.service.ThreadPoolBulkheadService;
import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("ThreadPoolBulkheadService Tests")
class ThreadPoolBulkheadServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private ThreadPoolBulkheadService threadPoolBulkheadService;

    @BeforeEach
    void setUp() {
        reset(productsApiClient);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        Thread.sleep(4000);
    }

    @Test
    @DisplayName("Should succeed when single async request is within bulkhead limit")
    void shouldSucceed_whenSingleAsyncRequestWithinLimit() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        // When
        CompletableFuture<List<Product>> future = threadPoolBulkheadService.getProductsWithThreadPool();
        List<Product> result = future.get();

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should succeed when 4 concurrent requests (exactly at max thread pool size)")
    void shouldSucceed_whenFourConcurrentRequests() throws InterruptedException {
        // Given: max-thread-pool-size: 4
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(4);
        List<CompletableFuture<List<Product>>> futures = new ArrayList<>();

        // When: Submit 4 concurrent async requests
        for (int i = 0; i < 4; i++) {
            CompletableFuture<List<Product>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return threadPoolBulkheadService.getProductsWithThreadPool().join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(15, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        verify(productsApiClient, times(4)).products(any());

        for (CompletableFuture<List<Product>> future : futures) {
            List<Product> result = future.join();
            assertThat(result).hasSize(1);
        }
    }

    @Test
    @DisplayName("Should queue requests when thread pool is full (up to queue capacity)")
    void shouldQueueRequests_whenThreadPoolFull() throws InterruptedException {
        // Given: max-thread-pool-size: 4, queue-capacity: 5 = total 9
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(9);
        List<CompletableFuture<List<Product>>> futures = new ArrayList<>();

        // When: Submit 9 requests (4 in pool + 5 in queue)
        for (int i = 0; i < 9; i++) {
            CompletableFuture<List<Product>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return threadPoolBulkheadService.getProductsWithThreadPool().join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(20, TimeUnit.SECONDS);

        // Then: All 9 should succeed
        assertThat(completed).isTrue();
        verify(productsApiClient, times(9)).products(any());

        for (CompletableFuture<List<Product>> future : futures) {
            List<Product> result = future.join();
            assertThat(result).hasSize(1);
        }
    }

    @Test
    @DisplayName("Should reject when pool and queue are full (10+ requests)")
    void shouldReject_whenPoolAndQueueFull() throws InterruptedException {
        // Given: capacity = 4 threads + 5 queue = 9 total
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        // When: Submit 15 requests (6 should be rejected)
        for (int i = 0; i < 15; i++) {
            CompletableFuture<?> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                try {
                    return threadPoolBulkheadService.getProductsWithThreadPool().join();
                } catch (Exception e) {
                    exceptions.add(e);
                    throw e;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        Thread.sleep(8000); // Wait for execution

        // Then: At least 6 requests should have been rejected
        assertThat(exceptions).hasSizeGreaterThanOrEqualTo(6);
        assertThat(exceptions)
                .anyMatch(e -> e.getCause() instanceof ResponseStatusException);
    }

    @Test
    @DisplayName("Should handle sequential async requests successfully")
    void shouldSucceed_whenSequentialAsyncRequests() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        // When: Execute 5 async requests sequentially
        for (int i = 0; i < 5; i++) {
            CompletableFuture<List<Product>> future = threadPoolBulkheadService.getProductsWithThreadPool();
            List<Product> result = future.get();
            assertThat(result).hasSize(1);
        }

        // Then
        verify(productsApiClient, times(5)).products(any());
    }

    @Test
    @DisplayName("Should trigger fallback when bulkhead is full")
    void shouldTriggerFallback_whenBulkheadFull() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // When: Fill the bulkhead completely
        for (int i = 0; i < 10; i++) {
            CompletableFuture<?> future = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return threadPoolBulkheadService.getProductsWithThreadPool().join();
                } catch (Exception e) {
                    return null;
                }
            });
            futures.add(future);
        }

        startLatch.countDown();
        Thread.sleep(500); // Ensure threads are occupied

        // Then: Additional request should trigger fallback
        assertThatThrownBy(() -> {
            CompletableFuture<List<Product>> future = threadPoolBulkheadService.getProductsWithThreadPool();
            future.join();
        }).hasCauseInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Should maintain isolation across multiple batches")
    void shouldMaintainIsolation_acrossMultipleBatches() throws ExecutionException, InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        // When: Execute three batches of 3 requests each
        for (int batch = 0; batch < 3; batch++) {
            List<CompletableFuture<List<Product>>> batchFutures = new ArrayList<>();
            
            for (int i = 0; i < 3; i++) {
                batchFutures.add(threadPoolBulkheadService.getProductsWithThreadPool());
            }

            // Wait for batch to complete
            for (CompletableFuture<List<Product>> future : batchFutures) {
                List<Product> result = future.get();
                assertThat(result).hasSize(1);
            }
        }

        // Then: All 9 requests should have succeeded
        verify(productsApiClient, times(9)).products(any());
    }

    @Test
    @DisplayName("Should use core threads first, then create additional threads up to max")
    void shouldUseCoreThreadsFirst_thenCreateAdditional() throws InterruptedException {
        // Given: core-thread-pool-size: 2, max-thread-pool-size: 4
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(4);

        // When: Submit 4 requests
        for (int i = 0; i < 4; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    threadPoolBulkheadService.getProductsWithThreadPool().join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(15, TimeUnit.SECONDS);

        // Then: All 4 should succeed (2 core + 2 additional threads)
        assertThat(completed).isTrue();
        verify(productsApiClient, times(4)).products(any());
    }
}