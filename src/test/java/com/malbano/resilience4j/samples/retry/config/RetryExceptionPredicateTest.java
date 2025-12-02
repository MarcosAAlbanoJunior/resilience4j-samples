package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RetryExceptionPredicateTest {

    private final RetryExceptionPredicate predicate = new RetryExceptionPredicate();

    @Test
    void shouldReturnTrueForTooManyRequests() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS);

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isTrue();
    }

    @Test
    void shouldReturnTrueForInternalServerError() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isTrue();
    }

    @Test
    void shouldReturnTrueForRequestTimeout() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.REQUEST_TIMEOUT);

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isTrue();
    }

    @Test
    void shouldReturnFalseForForbidden() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.FORBIDDEN);

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isFalse();
    }

    @Test
    void shouldReturnFalseForBadRequest() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.BAD_REQUEST);

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isFalse();
    }

    @Test
    void shouldReturnFalseForGenericException() {

        RuntimeException ex = new RuntimeException("Boom");

        boolean shouldRetry = predicate.test(ex);

        assertThat(shouldRetry).isFalse();
    }
}

