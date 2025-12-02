package com.malbano.resilience4j.samples.retry.controller;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.retry.service.BasicRetryService;
import com.malbano.resilience4j.samples.retry.service.CustomIntervalRetryService;
import com.malbano.resilience4j.samples.retry.service.PredicateExceptionRetryService;
import com.malbano.resilience4j.samples.retry.service.PredicateResultRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retry")
@RequiredArgsConstructor
@Tag(name = "Retry", description = "Demonstrates different Resilience4j retry patterns")
public class RetryController {

    private final BasicRetryService basicRetryService;
    private final PredicateExceptionRetryService predicateExceptionRetryService;
    private final PredicateResultRetryService predicateResultRetryService;
    private final CustomIntervalRetryService customIntervalRetryService;

    @Operation(
            summary = "Basic retry mechanism with fixed wait duration",
            description = "Demonstrates basic retry mechanism with fixed wait duration.\n" +
                    "Retries on any exception up to max-attempts configured.\n\n" +
                    "**Examples:**\n" +
                    "- ok → Immediate success\n" +
                    "- 500-500-ok → Fails twice with 500, succeeds on third attempt\n" +
                    "- 429-500-ok → Error 429, then 500, then success\n" +
                    "- timeout-ok → Timeout error, then success"
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @GetMapping
    public ResponseEntity<List<Product>> basicRetry(
            @Parameter(
                    description = "Sequence of status codes separated by hyphen.\n" +
                            "Available codes: ok, 429, 400, 404, 500, 503, timeout",
                    example = "500-500-ok"
            )
            @RequestParam String scenario
    ) {
        return ResponseEntity.ok(basicRetryService.basicRetryExample(scenario));
    }

    @Operation(
            summary = "Conditional retry based on HTTP status codes using a predicate",
            description = "Demonstrates conditional retry based on HTTP status codes using a predicate.\n" +
                    "Only retries on specific status codes: 429 (Too Many Requests), 500 (Internal Server Error) and 408 (Timeout).\n" +
                    "Other errors will trigger fallback immediately without retry.\n\n" +
                    "**Examples:**\n" +
                    "- ok → Immediate success\n" +
                    "- 500-500-ok → Fails twice with 500, succeeds on third attempt\n" +
                    "- 429-500-ok → Error 429, then 500, then success\n" +
                    "- 503-500-ok → Stop on error 503 and call FallBack method\n" +
                    "- timeout-ok → Timeout error, then success"
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @GetMapping("/with-throw-predicate")
    public ResponseEntity<List<Product>> retryWithThrowPredicate(
            @Parameter(
                    description = "Sequence of status codes separated by hyphen.\n" +
                            "Available codes: ok, 429, 400, 404, 500, 503, timeout",
                    example = "429-500-ok"
            )
            @RequestParam String scenario
    ) {
        return ResponseEntity.ok(predicateExceptionRetryService.retryOnHttpStatus(scenario));
    }

    @Operation(
            summary = "Conditional retry based on response content using a result predicate",
            description = "Demonstrates conditional retry based on response content using a result predicate.\n" +
                    "Retries when product status is 'GENERATING', stops when 'ACTIVATED'.\n" +
                    "Useful for polling async operations where HTTP 200 is returned but operation is incomplete.\n\n" +
                    "**Examples:**\n" +
                    "- activated → Immediate success\n" +
                    "- generating-activated → Retry once, success on 2nd attempt\n" +
                    "- generating-generating-activated → Retry twice, success on 3rd attempt\n" +
                    "- generating-generating-generating → Max retries exhausted, returns last result\n\n" +
                    "**Note:** Fallback only triggers on exceptions, not when max retries exhausted."
    )
    @ApiResponse(responseCode = "200", description = "Product with current status returned successfully")
    @GetMapping("/with-result-predicate")
    public ResponseEntity<Product> retryWithResultPredicate(
            @Parameter(
                    description = "Sequence of statuses separated by hyphen (generating or activated)",
                    example = "generating-activated"
            )
            @RequestParam String scenario
    ) {
        return ResponseEntity.ok(predicateResultRetryService.retryOnResult(scenario));
    }

    @Operation(
            summary = "Custom retry intervals based on HTTP status codes",
            description = "Demonstrates custom retry intervals based on HTTP status codes.\n" +
                    "Each status code can have a different wait duration before the next retry attempt.\n\n" +
                    "**Examples:**\n" +
                    "- ok → Immediate success\n" +
                    "- 500-500-ok → Fails twice with 500, succeeds on third attempt\n" +
                    "- 429-500-ok → Error 429, then 500, then success\n" +
                    "- timeout-ok → Timeout error, then success"
    )
    @ApiResponse(responseCode = "200", description = "List of products returned successfully")
    @GetMapping("/with-custom-interval")
    public ResponseEntity<List<Product>> retryWithCustomInterval(
            @Parameter(
                    description = "Sequence of status codes separated by hyphen.\n" +
                            "Available codes: ok, 429, 400, 404, 500, 503, timeout",
                    example = "timeout-ok"
            )
            @RequestParam String scenario
    ) {
        return ResponseEntity.ok(customIntervalRetryService.retryWithCustomInterval(scenario));
    }
}