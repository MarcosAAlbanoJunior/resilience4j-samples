package com.malbano.resilience4j.samples.bulkhead;

import com.malbano.resilience4j.samples.bulkhead.service.SemaphoreBulkheadService;
import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("SemaphoreBulkheadService Tests")
class SemaphoreBulkheadServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private SemaphoreBulkheadService semaphoreBulkheadService;

    @Test
    @DisplayName("Should succeed when single request is within bulkhead limit")
    void shouldSucceed_whenSingleRequestWithinLimit() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        // When
        List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();

        // Then
        verify(productsApiClient, times(1)).products(any());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should succeed when 3 concurrent requests (exactly at limit)")
    void shouldSucceed_whenThreeConcurrentRequests() throws InterruptedException {
        // Given: max-concurrent-calls: 3
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(3);
        List<Future<List<Product>>> futures = new ArrayList<>();

        // When: Submit 3 concurrent requests
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();
                    completionLatch.countDown();
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));
        }

        startLatch.countDown(); // Release all threads
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        verify(productsApiClient, times(3)).products(any());
        
        for (Future<List<Product>> future : futures) {
            try {
                List<Product> result = future.get();
                assertThat(result).hasSize(1);
            } catch (ExecutionException e) {
                throw new AssertionError("Request should not have failed", e);
            }
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Should fail when 4 concurrent requests exceed bulkhead limit")
    void shouldFail_whenFourConcurrentRequestsExceedLimit() throws InterruptedException {
        // Given: max-concurrent-calls: 3, max-wait-duration: 2s
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(4);
        List<Future<List<Product>>> futures = new ArrayList<>();
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: Submit 4 concurrent requests
        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();
                    completionLatch.countDown();
                    return result;
                } catch (Exception e) {
                    exceptions.add(e);
                    completionLatch.countDown();
                    throw e;
                }
            }));
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(15, TimeUnit.SECONDS);

        // Then
        assertThat(completed).isTrue();
        assertThat(exceptions).hasSizeGreaterThanOrEqualTo(1);
        assertThat(exceptions.get(0)).isInstanceOf(BulkheadFullException.class);

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle sequential requests successfully")
    void shouldSucceed_whenSequentialRequests() {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        // When: Execute 5 requests sequentially (not concurrently)
        for (int i = 0; i < 5; i++) {
            List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();
            assertThat(result).hasSize(1);
        }

        // Then
        verify(productsApiClient, times(5)).products(any());
    }

    @Test
    @DisplayName("Should allow new request after previous request completes")
    void shouldSucceed_whenNewRequestAfterPreviousCompletes() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // When: First 3 requests occupy all slots
        CountDownLatch firstBatchLatch = new CountDownLatch(3);
        List<Future<List<Product>>> firstBatch = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            firstBatch.add(executor.submit(() -> {
                List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();
                firstBatchLatch.countDown();
                return result;
            }));
        }

        // Wait for first batch to complete
        boolean firstCompleted = firstBatchLatch.await(10, TimeUnit.SECONDS);
        assertThat(firstCompleted).isTrue();

        // Then: 4th request should succeed (slots are now free)
        List<Product> result = semaphoreBulkheadService.getProductsWithSemaphore();
        assertThat(result).hasSize(1);

        verify(productsApiClient, times(4)).products(any());
        executor.shutdown();
    }

    @Test
    @DisplayName("Should wait up to 2 seconds for available slot before failing")
    void shouldWaitAndFail_whenNoSlotAvailableWithinTimeout() throws InterruptedException {
        // Given: max-wait-duration: 2s
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // When: Occupy all 3 slots with long-running requests
        List<Future<List<Product>>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return semaphoreBulkheadService.getProductsWithSemaphore();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));
        }

        startLatch.countDown();
        Thread.sleep(500); // Ensure first 3 requests have acquired slots

        // Then: 4th request should fail after waiting ~2 seconds
        long startTime = System.currentTimeMillis();
        assertThatThrownBy(() -> semaphoreBulkheadService.getProductsWithSemaphore())
                .isInstanceOf(BulkheadFullException.class)
                .hasMessageContaining("Bulkhead");
        
        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isGreaterThanOrEqualTo(2000).isLessThan(3000);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should reject immediately when bulkhead is full and max-wait-duration is exceeded")
    void shouldRejectImmediately_whenBulkheadFullAfterWaiting() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: Submit 10 concurrent requests (7 should fail)
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    semaphoreBulkheadService.getProductsWithSemaphore();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }));
        }

        startLatch.countDown();
        Thread.sleep(8000); // Wait for all to complete (3s processing + 2s timeout)

        // Then: At least 7 requests should have failed
        assertThat(exceptions).hasSizeGreaterThanOrEqualTo(7);
        assertThat(exceptions)
                .allMatch(e -> e instanceof BulkheadFullException);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should maintain concurrency limit across multiple batches")
    void shouldMaintainLimit_acrossMultipleBatches() throws InterruptedException {
        // Given
        when(productsApiClient.products(any()))
                .thenReturn(List.of(
                        Product.builder().id(1).description("Product 1").build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(6);
        
        // When: Execute two batches of 3 requests each
        CountDownLatch firstBatchLatch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                semaphoreBulkheadService.getProductsWithSemaphore();
                firstBatchLatch.countDown();
            });
        }
        firstBatchLatch.await(10, TimeUnit.SECONDS);

        CountDownLatch secondBatchLatch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                semaphoreBulkheadService.getProductsWithSemaphore();
                secondBatchLatch.countDown();
            });
        }
        secondBatchLatch.await(10, TimeUnit.SECONDS);

        // Then: All 6 requests should have succeeded
        verify(productsApiClient, times(6)).products(any());

        executor.shutdown();
    }
}