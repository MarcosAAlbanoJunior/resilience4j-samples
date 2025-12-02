package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.function.Predicate;

@Slf4j
public class RetryExceptionPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        log.info("=== PREDICATE CALLED ===");
        log.info("Exception type: {}", throwable.getClass().getName());

        if (!(throwable instanceof HttpStatusException httpException)) {
            log.info("Not an HttpStatusException - No retry");
            return false;
        }

        HttpStatus status = httpException.getHttpStatus();

        boolean shouldRetry = HttpStatus.TOO_MANY_REQUESTS.equals(status) ||
                HttpStatus.INTERNAL_SERVER_ERROR.equals(status) ||
                HttpStatus.REQUEST_TIMEOUT.equals(status);

        log.info("HTTP Status: {} - Should retry: {}", status, shouldRetry);
        return shouldRetry;
    }
}
