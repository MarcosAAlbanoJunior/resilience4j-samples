package com.malbano.resilience4j.samples.retry.controller;

import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.retry.service.BasicRetryService;
import com.malbano.resilience4j.samples.retry.service.CustomIntervalRetryService;
import com.malbano.resilience4j.samples.retry.service.PredicateExceptionRetryService;
import com.malbano.resilience4j.samples.retry.service.PredicateResultRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retry")
@RequiredArgsConstructor
public class RetryController {

    private final BasicRetryService basicRetryService;
    private final PredicateExceptionRetryService predicateExceptionRetryService;
    private final PredicateResultRetryService predicateResultRetryService;
    private final CustomIntervalRetryService customIntervalRetryService;

    /**
     * Demonstrates basic retry mechanism with fixed wait duration.
     * Retries on any exception up to max-attempts configured.
     *
     * @param scenario Sequence of status codes separated by hyphen.
     *                 Available codes: ok, 429, 400, 404, 500, 503, timeout
     * @return List of products
     *
     * @apiNote Examples:
     *   ok Immediate success
     *   500-500-ok Fails twice with 500, succeeds on third attempt
     *   429-500-ok Error 429, then 500, then success
     *   timeout-ok Timeout error, then success
     */
    @GetMapping
    public ResponseEntity<List<Product>> basicRetry(@RequestParam String scenario) {
        return ResponseEntity.ok(basicRetryService.basicRetryExample(scenario));
    }

    /**
     * Demonstrates conditional retry based on HTTP status codes using a predicate.
     * Only retries on specific status codes: 429 (Too Many Requests), 500 (Internal Server Error) and 408(Timeout).
     * Other errors will trigger fallback immediately without retry.
     *
     * @param scenario Sequence of status codes separated by hyphen.
     *                 Available codes: ok, 429, 400, 404, 500, 503, timeout
     * @return List of products
     *
     * @apiNote Examples:
     *   ok Immediate success
     *   500-500-ok Fails twice with 500, succeeds on third attempt
     *   429-500-ok Error 429, then 500, then success
     *   timeout-ok Timeout error, then success
     */
    @GetMapping("/with-throw-predicate")
    public ResponseEntity<List<Product>> retryWithThrowPredicate(@RequestParam String scenario) {
        return ResponseEntity.ok(predicateExceptionRetryService.retryOnHttpStatus(scenario));
    }

    /**
     * Demonstrates conditional retry based on response content using a result predicate.
     * Retries when product status is "GENERATING", stops when "ACTIVATED".
     * Useful for polling async operations where HTTP 200 is returned but operation is incomplete.
     *
     * @param scenario Sequence of statuses separated by hyphen (generating or activated)
     * @return Product with current status
     *
     * @apiNote Examples:
     *   activated - Immediate success
     *   generating-activated - Retry once, success on 2nd attempt
     *   generating-generating-activated - Retry twice, success on 3rd attempt
     *   generating-generating-generating - Max retries exhausted, returns last result
     *
     * @implNote Fallback only triggers on exceptions, not when max retries exhausted.
     */
    @GetMapping("/with-result-predicate")
    public ResponseEntity<Product> retryWithResultPredicate(@RequestParam String scenario) {
        return ResponseEntity.ok(predicateResultRetryService.retryOnResult(scenario));
    }

    /**
     * Demonstrates custom retry intervals based on HTTP status codes.
     * Each status code can have a different wait duration before the next retry attempt.
     *
     * @param scenario Sequence of status codes separated by hyphen.
     *                 Available codes: ok, 429, 400, 404, 500, 503, timeout
     * @return List of products
     *
     * @apiNote Examples:
     *   ok Immediate success
     *   500-500-ok Fails twice with 500, succeeds on third attempt
     *   429-500-ok Error 429, then 500, then success
     *   timeout-ok Timeout error, then success
     */
    @GetMapping("/with-custom-interval")
    public ResponseEntity<List<Product>> retryWithCustomInterval(@RequestParam String scenario) {
        return ResponseEntity.ok(customIntervalRetryService.retryWithCustomInterval(scenario));
    }
}