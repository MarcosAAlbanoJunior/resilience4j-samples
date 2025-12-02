package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class HttpStatusRetryInterval implements IntervalBiFunction<HttpStatus> {

    @Override
    public Long apply(Integer attemptNumber, Either<Throwable, HttpStatus> either) {
        long delay;

        Throwable throwable = either.getLeft();

        if (throwable instanceof HttpStatusException httpStatusException) {
            HttpStatus httpStatus = httpStatusException.getHttpStatus();
            int statusCode = httpStatus.value();

            delay = getDelayForStatusCode(statusCode, attemptNumber);

            log.info("Attempt #{}, HttpStatusException HTTP {}: Next retry in {}ms",
                    attemptNumber, statusCode, delay);
        } else {
            delay = 1000L * (long) Math.pow(2, attemptNumber - 1);
            log.info("Attempt #{}, Exception {}: Next retry in {}ms",
                    attemptNumber, throwable.getClass().getSimpleName(), delay);
        }

        delay = Math.min(delay, 15000L);
        return delay;
    }

    private long getDelayForStatusCode(int statusCode, int attemptNumber) {
        return switch (statusCode) {
            case 429 -> // Too Many Requests
                    5000L; // 5 seconds
            case 503 -> // Service Unavailable
                    7000L; // 7 seconds
            case 500, 502, 504 -> // Server errors
                    1000L * (long) Math.pow(2, attemptNumber - 1); // Exponential backoff
            default -> 2000L; // 2 seconds
        };
    }
}