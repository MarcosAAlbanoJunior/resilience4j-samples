package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import io.github.resilience4j.core.functions.Either;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpStatusRetryIntervalTest {

    private final HttpStatusRetryInterval interval = new HttpStatusRetryInterval();

    @Test
    void shouldReturn5SecondsFor429() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS);

        long delay = interval.apply(1, Either.left(ex));

        assertThat(delay).isEqualTo(5000L);
    }

    @Test
    void shouldReturn7SecondsFor503() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE);

        long delay = interval.apply(1, Either.left(ex));

        assertThat(delay).isEqualTo(7000L);
    }

    @Test
    void shouldApplyExponentialBackoffFor500() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        long delay = interval.apply(3, Either.left(ex)); // 2^(3-1) = 4

        assertThat(delay).isEqualTo(4000L);
    }

    @Test
    void shouldApplyExponentialBackoffForGenericException() {

        RuntimeException ex = new RuntimeException("Boom");

        long delay = interval.apply(2, Either.left(ex)); // 2^(2-1) = 2

        assertThat(delay).isEqualTo(2000L);
    }

    @Test
    void shouldCapDelayAt15Seconds() {

        HttpStatusException ex =
                new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        long delay = interval.apply(10, Either.left(ex)); // more than 15 seconds

        assertThat(delay).isEqualTo(15000L);
    }
}
