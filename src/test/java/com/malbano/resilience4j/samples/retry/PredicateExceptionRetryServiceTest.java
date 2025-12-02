package com.malbano.resilience4j.samples.retry;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.retry.service.PredicateExceptionRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("PredicateRetryService Tests")
class PredicateExceptionRetryServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private PredicateExceptionRetryService predicateExceptionRetryService;

    @Test
    @DisplayName("Should return success on first attempt")
    void shouldNotRetry_whenFirstAttemptSucceeds() {

        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        List<Product> result = predicateExceptionRetryService.retryOnHttpStatus("ok");

        verify(productsApiClient, times(1)).products(any());
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should retry twice and succeed on the third attempt")
    void shouldReturnProducts_whenFirstTwoAttemptsFailAndThirdSucceeds() {
        when(productsApiClient.products(any()))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .thenThrow(new HttpStatusException(HttpStatus.REQUEST_TIMEOUT))
                .thenReturn(List.of(Product.builder().id(1).description("teste").build()));

        List<Product> result = predicateExceptionRetryService.retryOnHttpStatus("429-timeout-ok");

        // Then
        verify(productsApiClient, times(3)).products(any());
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should retry once and call fallback on the second attempt")
    void shouldCallFallback_whenFirstAttemptFailAndSecondFailWithFalsePredicate() {
        when(productsApiClient.products(any()))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .thenThrow(new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        List<Product> result = predicateExceptionRetryService.retryOnHttpStatus("429-503-ok");

        verify(productsApiClient, times(2)).products(any());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should call fallback when all retry attempts fail")
    void shouldCallFallback_whenAllRetryAttemptsFail() {
        when(productsApiClient.products(any()))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .thenThrow(new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS));

        List<Product> result = predicateExceptionRetryService.retryOnHttpStatus("429-timeout-429");

        verify(productsApiClient, times(3)).products(any());
        assertThat(result).isEmpty();
    }
}