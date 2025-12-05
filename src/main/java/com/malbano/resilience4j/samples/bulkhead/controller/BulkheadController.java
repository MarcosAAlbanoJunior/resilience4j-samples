package com.malbano.resilience4j.samples.bulkhead.controller;

import com.malbano.resilience4j.samples.bulkhead.controller.dto.BulkheadExecutionReport;
import com.malbano.resilience4j.samples.bulkhead.controller.dto.RequestExecution;
import com.malbano.resilience4j.samples.bulkhead.service.*;
import com.malbano.resilience4j.samples.bulkhead.testloader.BulkheadReportGenerator;
import com.malbano.resilience4j.samples.bulkhead.testloader.BulkheadTestExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bulkhead")
@Tag(name = "Bulkhead", description = "Demonstrates different Resilience4j Bulkhead patterns")
public class BulkheadController {

    private final SemaphoreBulkheadService semaphoreBulkheadService;
    private final ThreadPoolBulkheadService threadPoolBulkheadService;
    private final BulkheadTestExecutor testExecutor;
    private final BulkheadReportGenerator reportGenerator;

    @Operation(
            summary = "Semaphore Bulkhead - limits concurrent calls",
            description = "Demonstrates semaphore-based bulkhead that limits concurrent executions.\n\n" +
                    "**How to Test:**\n" +
                    "Set `requests` parameter to see how bulkhead handles concurrent load:\n" +
                    "- requests=3 → All succeed (within limit)\n" +
                    "- requests=5 → Some fail (exceeds limit)\n" +
                    "- requests=10 → Many failures (way over limit)\n\n" +
                    "**Response shows:**\n" +
                    "- Configuration from application-bulkhead.yml\n" +
                    "- Which requests succeeded/failed\n" +
                    "- Execution time for each request\n" +
                    "- Thread name used\n" +
                    "- Failure reason (if failed)\n\n" +
                    "**Example:**\n" +
                    "```bash\n" +
                    "curl 'http://localhost:8085/api/bulkhead/semaphore?requests=5'\n" +
                    "```"
    )
    @ApiResponse(responseCode = "200", description = "Execution report with detailed statistics")
    @GetMapping("/semaphore")
    public ResponseEntity<BulkheadExecutionReport> semaphoreBulkhead(
            @Parameter(description = "Number of concurrent requests to simulate (1-20)", example = "5")
            @RequestParam(defaultValue = "3") @Min(1) @Max(20) Integer requests
    ) {
        log.info("Starting Semaphore Bulkhead test with {} concurrent requests", requests);

        long startTime = System.currentTimeMillis();

        // Execute test load
        List<RequestExecution> executions = testExecutor.executeTestLoad(
                requests,
                semaphoreBulkheadService::getProductsWithSemaphore,
                10 // max wait 10 seconds
        );

        long totalDuration = System.currentTimeMillis() - startTime;

        // Generate report with config from YAML
        BulkheadExecutionReport report = reportGenerator.generateSemaphoreReport(
                requests,
                totalDuration,
                executions
        );

        return ResponseEntity.ok(report);
    }

    @Operation(
            summary = "Thread Pool Bulkhead - isolated thread pool",
            description = "Demonstrates thread pool bulkhead that executes in dedicated isolated threads.\n\n" +
                    "**Difference from Semaphore:**\n" +
                    "- Semaphore: Uses calling thread (http-nio-*)\n" +
                    "- Thread Pool: Uses dedicated threads (bulkhead-*)\n\n" +
                    "**How to Test:**\n" +
                    "Set `requests` parameter to see thread pool behavior:\n" +
                    "- requests=2 → Use core threads\n" +
                    "- requests=4 → Create additional threads (up to max)\n" +
                    "- requests=6 → Some queued\n" +
                    "- requests=10 → Pool + queue full, rejections\n\n" +
                    "**Response shows:**\n" +
                    "- Configuration from application-bulkhead.yml\n" +
                    "- Thread pool thread names (notice: NOT http-nio-*)\n" +
                    "- Queuing behavior\n" +
                    "- Async execution timing\n\n" +
                    "**Example:**\n" +
                    "```bash\n" +
                    "curl 'http://localhost:8085/api/bulkhead/thread-pool?requests=6'\n" +
                    "```"
    )
    @ApiResponse(responseCode = "200", description = "Execution report with thread pool statistics")
    @GetMapping("/thread-pool")
    public ResponseEntity<BulkheadExecutionReport> threadPoolBulkhead(
            @Parameter(description = "Number of concurrent requests to simulate (1-20)", example = "6")
            @RequestParam(defaultValue = "4") @Min(1) @Max(20) Integer requests
    ) {
        log.info("Starting Thread Pool Bulkhead test with {} concurrent requests", requests);

        long startTime = System.currentTimeMillis();

        // Execute async test load
        List<RequestExecution> executions = testExecutor.executeAsyncTestLoad(
                requests,
                () -> threadPoolBulkheadService.getProductsWithThreadPool(),
                15 // max wait 15 seconds
        );

        long totalDuration = System.currentTimeMillis() - startTime;

        // Generate report with config from YAML
        BulkheadExecutionReport report = reportGenerator.generateThreadPoolReport(
                requests,
                totalDuration,
                executions
        );

        return ResponseEntity.ok(report);
    }
}