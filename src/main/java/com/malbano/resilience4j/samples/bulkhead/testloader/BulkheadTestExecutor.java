package com.malbano.resilience4j.samples.bulkhead.testloader;

import com.malbano.resilience4j.samples.bulkhead.controller.dto.RequestExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BulkheadTestExecutor {

    /**
     * Executes multiple async (CompletableFuture) requests with proper ordering.
     * Uses sequential submission with small delays to ensure deterministic order.
     */
    public List<RequestExecution> executeAsyncTestLoad(
            int numberOfRequests,
            Supplier<CompletableFuture<?>> asyncFunction,
            long maxWaitSeconds
    ) {
        ConcurrentHashMap<Integer, RequestExecution> executionMap = new ConcurrentHashMap<>();
        CountDownLatch completionLatch = new CountDownLatch(numberOfRequests);
        long testStartTime = System.currentTimeMillis();

        // Use a scheduled executor to submit requests in order with small delays
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ExecutorService executorService = Executors.newFixedThreadPool(
                Math.min(numberOfRequests, 20)
        );

        try {
            for (int i = 0; i < numberOfRequests; i++) {
                final int requestId = i + 1;
                final long delay = i * 10L; // 10ms delay between each submission

                scheduler.schedule(() -> {
                    executorService.submit(() ->
                            executeAsyncRequest(requestId, asyncFunction, executionMap,
                                    testStartTime, completionLatch)
                    );
                }, delay, TimeUnit.MILLISECONDS);
            }

            boolean completed = completionLatch.await(maxWaitSeconds, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Async test execution timeout");
            }

            Thread.sleep(100);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Async test execution interrupted", e);
        } finally {
            scheduler.shutdown();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return executionMap.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(java.util.Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Executes multiple concurrent requests with sequential submission.
     * Ensures requests are submitted in order to test bulkhead behavior predictably.
     */
    public List<RequestExecution> executeTestLoad(
            int numberOfRequests,
            Supplier<Object> testFunction,
            long maxWaitSeconds
    ) {
        ConcurrentHashMap<Integer, RequestExecution> executionMap = new ConcurrentHashMap<>();
        CountDownLatch completionLatch = new CountDownLatch(numberOfRequests);
        long testStartTime = System.currentTimeMillis();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ExecutorService executorService = Executors.newFixedThreadPool(
                Math.min(numberOfRequests, 20)
        );

        try {
            for (int i = 0; i < numberOfRequests; i++) {
                final int requestId = i + 1;
                final long delay = i * 10L;

                scheduler.schedule(() -> {
                    executorService.submit(() ->
                            executeRequest(requestId, testFunction, executionMap,
                                    testStartTime, completionLatch)
                    );
                }, delay, TimeUnit.MILLISECONDS);
            }

            boolean completed = completionLatch.await(maxWaitSeconds, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Test execution timeout - not all requests completed");
            }

            Thread.sleep(100);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Test execution interrupted", e);
        } finally {
            scheduler.shutdown();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return executionMap.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(java.util.Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private void executeRequest(
            int requestId,
            Supplier<Object> testFunction,
            ConcurrentHashMap<Integer, RequestExecution> executionMap,
            long testStartTime,
            CountDownLatch completionLatch
    ) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            log.info("Request #{} starting on thread: {}", requestId, threadName);
            testFunction.get();

            long duration = System.currentTimeMillis() - startTime;
            executionMap.put(requestId,
                    createSuccessExecution(requestId, threadName, duration, startTime - testStartTime));

            log.info("Request #{} completed in {}ms", requestId, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            executionMap.put(requestId,
                    createFailureExecution(requestId, threadName, duration, startTime - testStartTime, e));

            log.warn("Request #{} failed after {}ms: {}", requestId, duration, e.getMessage());
        } finally {
            completionLatch.countDown();
        }
    }

    private void executeAsyncRequest(
            int requestId,
            Supplier<CompletableFuture<?>> asyncFunction,
            ConcurrentHashMap<Integer, RequestExecution> executionMap,
            long testStartTime,
            CountDownLatch completionLatch
    ) {
        long startTime = System.currentTimeMillis();
        String callingThread = Thread.currentThread().getName();

        log.info("Request #{} attempting to start on thread: {}", requestId, callingThread);

        try {
            asyncFunction.get()
                    .thenAccept(result -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String executionThread = Thread.currentThread().getName();

                        RequestExecution execution = RequestExecution.builder()
                                .requestId(requestId)
                                .status("SUCCESS")
                                .threadName(executionThread)
                                .callingThread(callingThread)
                                .durationMs(duration)
                                .startTime(startTime - testStartTime)
                                .build();

                        executionMap.put(requestId, execution);
                        log.info("Request #{} completed in {}ms on thread: {}", requestId, duration, executionThread);
                    })
                    .exceptionally(ex -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String errorReason = extractErrorReason(ex);

                        RequestExecution execution = RequestExecution.builder()
                                .requestId(requestId)
                                .status("FAILED")
                                .threadName(callingThread)
                                .durationMs(duration)
                                .startTime(startTime - testStartTime)
                                .errorReason(errorReason)
                                .errorType(ex.getClass().getSimpleName())
                                .build();

                        executionMap.put(requestId, execution);
                        log.warn("Request #{} failed after {}ms: {}", requestId, duration, errorReason);
                        return null;
                    })
                    .whenComplete((result, ex) -> completionLatch.countDown())
                    .join();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorReason = extractErrorReason(e);

            RequestExecution execution = RequestExecution.builder()
                    .requestId(requestId)
                    .status("FAILED")
                    .threadName(callingThread)
                    .durationMs(duration)
                    .startTime(startTime - testStartTime)
                    .errorReason(errorReason)
                    .errorType(e.getClass().getSimpleName())
                    .build();

            executionMap.put(requestId, execution);
            log.warn("Request #{} failed after {}ms: {}", requestId, duration, errorReason);
            completionLatch.countDown();
        }
    }

    private RequestExecution createSuccessExecution(int requestId, String threadName, long duration, long startTime) {
        return RequestExecution.builder()
                .requestId(requestId)
                .status("SUCCESS")
                .threadName(threadName)
                .durationMs(duration)
                .startTime(startTime)
                .build();
    }

    private RequestExecution createFailureExecution(
            int requestId,
            String threadName,
            long duration,
            long startTime,
            Exception e
    ) {
        return RequestExecution.builder()
                .requestId(requestId)
                .status("FAILED")
                .threadName(threadName)
                .durationMs(duration)
                .startTime(startTime)
                .errorReason(extractErrorReason(e))
                .errorType(e.getClass().getSimpleName())
                .build();
    }

    private String extractErrorReason(Throwable e) {
        if (e.getMessage() != null) {
            if (e.getMessage().contains("Bulkhead")) {
                return "Bulkhead is full - Maximum concurrent calls reached";
            } else if (e.getMessage().contains("timeout")) {
                return "Wait timeout expired - No slot available within timeout";
            } else if (e.getMessage().contains("rejected")) {
                return "Request rejected - Thread pool and queue are full";
            } else if (e.getMessage().contains("503")) {
                return "Service unavailable - Bulkhead capacity exceeded";
            }
        }
        return e.getMessage() != null ? e.getMessage() : "Unknown error";
    }
}