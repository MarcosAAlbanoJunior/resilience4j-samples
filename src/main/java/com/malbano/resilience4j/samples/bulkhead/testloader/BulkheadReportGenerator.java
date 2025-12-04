package com.malbano.resilience4j.samples.bulkhead.testloader;

import com.malbano.resilience4j.samples.bulkhead.controller.dto.BulkheadConfiguration;
import com.malbano.resilience4j.samples.bulkhead.controller.dto.BulkheadExecutionReport;
import com.malbano.resilience4j.samples.bulkhead.controller.dto.RequestExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulkheadReportGenerator {

    @Value("${resilience4j.bulkhead.instances.semaphore-bulkhead.max-concurrent-calls:3}")
    private int semaphoreMaxConcurrentCalls;

    @Value("${resilience4j.bulkhead.instances.semaphore-bulkhead.max-wait-duration:2s}")
    private String semaphoreMaxWaitDuration;

    @Value("${resilience4j.thread-pool-bulkhead.instances.thread-pool-bulkhead.core-thread-pool-size:2}")
    private int threadPoolCoreSize;

    @Value("${resilience4j.thread-pool-bulkhead.instances.thread-pool-bulkhead.max-thread-pool-size:4}")
    private int threadPoolMaxSize;

    @Value("${resilience4j.thread-pool-bulkhead.instances.thread-pool-bulkhead.queue-capacity:10}")
    private int threadPoolQueueCapacity;

    public BulkheadExecutionReport generateSemaphoreReport(
            int totalRequests,
            long totalDurationMs,
            List<RequestExecution> executions
    ) {
        executions.sort(Comparator.comparing(RequestExecution::getRequestId));

        long succeeded = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        long failed = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();

        return BulkheadExecutionReport.builder()
                .bulkheadType("SEMAPHORE")
                .configuration(BulkheadConfiguration.builder()
                        .maxConcurrentCalls(semaphoreMaxConcurrentCalls)
                        .maxWaitDuration(semaphoreMaxWaitDuration)
                        .build())
                .totalRequests(totalRequests)
                .succeeded((int) succeeded)
                .failed((int) failed)
                .totalDurationMs(totalDurationMs)
                .executions(executions)
                .summary(generateSemaphoreSummary(totalRequests, succeeded, failed, executions))
                .build();
    }

    public BulkheadExecutionReport generateThreadPoolReport(
            int totalRequests,
            long totalDurationMs,
            List<RequestExecution> executions
    ) {
        executions.sort(Comparator.comparing(RequestExecution::getRequestId));

        long succeeded = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        long failed = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();

        return BulkheadExecutionReport.builder()
                .bulkheadType("THREADPOOL")
                .configuration(BulkheadConfiguration.builder()
                        .coreThreadPoolSize(threadPoolCoreSize)
                        .maxThreadPoolSize(threadPoolMaxSize)
                        .queueCapacity(threadPoolQueueCapacity)
                        .build())
                .totalRequests(totalRequests)
                .succeeded((int) succeeded)
                .failed((int) failed)
                .totalDurationMs(totalDurationMs)
                .executions(executions)
                .summary(generateThreadPoolSummary(totalRequests, succeeded, failed, executions))
                .build();
    }

    private String generateSemaphoreSummary(int total, long succeeded, long failed, List<RequestExecution> executions) {
        if (failed == 0) {
            return String.format("✅ All %d requests succeeded! Bulkhead limit not reached.", total);
        } else if (succeeded == 0) {
            return String.format("❌ All %d requests failed! Bulkhead was completely overwhelmed.", total);
        }

        // Analyze failure pattern
        String failurePattern = analyzeFailurePattern(executions, semaphoreMaxConcurrentCalls, 0);

        return String.format("⚠️ Mixed results: %d succeeded, %d failed.\n" +
                        "Bulkhead limit (%d concurrent calls) was exceeded.\n%s",
                succeeded, failed, semaphoreMaxConcurrentCalls, failurePattern);
    }

    private String generateThreadPoolSummary(int total, long succeeded, long failed, List<RequestExecution> executions) {
        if (failed == 0) {
            return String.format("✅ All %d requests succeeded! Bulkhead limit not reached.", total);
        } else if (succeeded == 0) {
            return String.format("❌ All %d requests failed! Bulkhead was completely overwhelmed.", total);
        }

        // Calculate capacity
        int totalCapacity = threadPoolMaxSize + threadPoolQueueCapacity;

        // Analyze failure pattern
        String failurePattern = analyzeFailurePattern(executions, threadPoolMaxSize, threadPoolQueueCapacity);

        return String.format("⚠️ Mixed results: %d succeeded, %d failed.\n" +
                        "Capacity: %d threads + %d queue = %d total slots\n%s",
                succeeded, failed, threadPoolMaxSize, threadPoolQueueCapacity, totalCapacity, failurePattern);
    }

    /**
     * Analyzes which requests failed and identifies the pattern
     */
    private String analyzeFailurePattern(List<RequestExecution> executions, int maxThreads, int queueCapacity) {
        List<Integer> failedRequestIds = executions.stream()
                .filter(e -> "FAILED".equals(e.getStatus()))
                .map(RequestExecution::getRequestId)
                .sorted()
                .collect(Collectors.toList());

        if (failedRequestIds.isEmpty()) {
            return "";
        }

        int totalCapacity = queueCapacity == 0 ? maxThreads : maxThreads + queueCapacity;
        int expectedFirstFailure = totalCapacity + 1;

        // Check if failures are sequential from expected point
        boolean isSequentialFromEnd = true;
        for (int i = 0; i < failedRequestIds.size(); i++) {
            if (failedRequestIds.get(i) != expectedFirstFailure + i) {
                isSequentialFromEnd = false;
                break;
            }
        }

        if (isSequentialFromEnd) {
            return String.format("📊 Requests %d-%d were rejected (exceeded capacity of %d).",
                    expectedFirstFailure, failedRequestIds.get(failedRequestIds.size() - 1), totalCapacity);
        } else {
            // If not sequential, show which ones failed
            String failedIds = failedRequestIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return String.format("📊 Failed requests: [%s] - Pattern indicates race condition or timing variance.", failedIds);
        }
    }
}