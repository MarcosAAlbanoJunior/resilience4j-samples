package com.malbano.resilience4j.samples.timelimiter.controller;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.timelimiter.service.TimeLimiterStoppableService;
import com.malbano.resilience4j.samples.timelimiter.service.TimeLimiterUnstoppableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/time-limiter")
@RequiredArgsConstructor
@Tag(name = "TimeLimiter", description = "Demonstrates different Resilience4j Time Limiter patterns")
public class TimeLimiterController {

    private final TimeLimiterStoppableService timeLimiterStoppableService;
    private final TimeLimiterUnstoppableService timeLimiterUnstoppableService;

    @Operation(
            summary = "Stoppable TimeLimiter - cancels running future on timeout",
            description = "Demonstrates TimeLimiter with cancel-running-future = true.\n" +
                    "When timeout occurs, the background task is cancelled/interrupted.\n\n" +
                    "**Configuration:**\n" +
                    "- Timeout: 2 seconds\n" +
                    "- cancel-running-future: true (task is cancelled on timeout)\n" +
                    "- Behavior: Interrupts the background thread when timeout occurs\n\n" +
                    "**Use Case:** Most common scenario - prevents resource leaks by cancelling long-running operations.\n\n" +
                    "**How to Test:**\n" +
                    "1. Use scenario parameter to control API response time\n" +
                    "2. Use 'ok' scenario → Returns immediately (< 2s) → Success (200 OK)\n" +
                    "3. Use 'wait3' scenario → Waits 3 seconds (> 2s timeout) → Timeout (408) and task is cancelled\n" +
                    "4. Check application logs to see task cancellation\n\n" +
                    "**Example Scenarios:**\n" +
                    "- scenario=ok: Returns immediately (200 OK)\n" +
                    "- scenario=wait3: Times out after 2s (408 Timeout), task cancelled\n\n" +
                    "**Behavior:**\n" +
                    "✓ Resource efficient - cancelled tasks free up resources\n" +
                    "✓ Recommended for most use cases\n" +
                    "✓ Prevents accumulation of zombie threads\n\n" +
                    "**Logs on Timeout:**\n" +
                    "You will see 'Original task was cancelled/interrupted' in logs.\n" +
                    "The async operation will NOT complete after timeout."
    )
    @ApiResponse(responseCode = "200", description = "Products returned successfully within time limit")
    @ApiResponse(responseCode = "408", description = "Request timeout - operation took longer than 2 seconds (task was cancelled)")
    @GetMapping("/stoppable")
    public ResponseEntity<List<Product>> stoppableTimeLimiter(
            @Parameter(
                    description = "Scenario to control API response time.\n" +
                            "**Parameters:**\n" +
                            "- ok: Returns immediately (success)\n" +
                            "- wait3: Waits 3 seconds (triggers timeout and task is cancelled)",
                    example = "ok",
                    required = true
            )
            @RequestParam("scenario") String scenario
    ) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(timeLimiterStoppableService.getWithStoppableCall(scenario).get());
    }

    @Operation(
            summary = "Unstoppable TimeLimiter - does NOT cancel running future on timeout",
            description = "Demonstrates TimeLimiter with cancel-running-future = false.\n" +
                    "When timeout occurs, the background task continues running.\n\n" +
                    "**Configuration:**\n" +
                    "- Timeout: 2 seconds\n" +
                    "- cancel-running-future: false (task continues after timeout)\n" +
                    "- Behavior: Background thread continues even after client receives timeout\n\n" +
                    "**Use Case:** Operations that MUST complete even if client times out (audit logs, critical writes, etc.).\n\n" +
                    "**How to Test:**\n" +
                    "1. Use scenario parameter to control API response time\n" +
                    "2. Use 'fast' scenario → Returns quickly (< 2s) → Success (200 OK)\n" +
                    "3. Use 'slow' scenario → Takes > 2s → Client gets timeout (408) BUT task continues\n" +
                    "4. **IMPORTANT:** Check application logs to see task completion even after timeout\n\n" +
                    "**Example Scenarios:**\n" +
                    "- scenario=fast: Returns immediately (200 OK)\n" +
                    "- scenario=slow: Client gets timeout (408), but task completes in background\n\n" +
                    "**Behavior:**\n" +
                    "⚠️ Warning: Can accumulate resources if many requests timeout\n" +
                    "✓ Useful for: audit logs, financial transactions, data consistency operations\n" +
                    "✗ Not recommended for: regular API calls, user-facing operations\n\n" +
                    "**Logs on Timeout:**\n" +
                    "You will see 'Original task continues running in background' in logs.\n" +
                    "After some time, you will see 'Async call completed successfully' in logs,\n" +
                    "meaning the operation finished even though the client received a timeout error.\n\n" +
                    "**Important Note:**\n" +
                    "The client receives HTTP 408 timeout, but if you check the application logs,\n" +
                    "you will see the background task completed successfully. This is the expected behavior."
    )
    @ApiResponse(responseCode = "200", description = "Products returned successfully within time limit")
    @ApiResponse(responseCode = "408", description = "Request timeout - operation took longer than 2 seconds (but task continues in background - check logs)")
    @GetMapping("/unstoppable")
    public ResponseEntity<List<Product>> unstoppableTimeLimiter(
            @Parameter(
                    description = "Scenario to control API response time.\n" +
                            "**Parameters:**\n" +
                            "- ok: Returns immediately (success)\n" +
                            "- wait3: Waits 3 seconds (triggers timeout but task continues in background)",
                    example = "ok",
                    required = true
            )
            @RequestParam("scenario") String scenario
    ) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(timeLimiterUnstoppableService.getWithUnstoppableCall(scenario).get());
    }
}