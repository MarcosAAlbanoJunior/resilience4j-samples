package com.malbano.resilience4j.samples.retry;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.retry.service.CustomIntervalRetryService;
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
@DisplayName("Custom Interval retry Tests")
class CustomRetryServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private CustomIntervalRetryService customIntervalRetryService;

    @Test
    @DisplayName("Should retry on 429 and succeed on third attempt")
    void shouldRetryOnTooManyRequestsAndSucceedOnThirdAttempt() {

        when(productsApiClient.products(any()))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(List.of(Product.builder().id(1).build()));

        List<Product> result =
                customIntervalRetryService.retryWithCustomInterval("429-429-ok");

        verify(productsApiClient, times(3)).products(any());
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should trigger fallback after all attempts fail")
    void shouldTriggerFallbackAfterAllRetriesFail() {

        when(productsApiClient.products(any()))
                .thenThrow(new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenThrow(new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS));

        List<Product> result =
                customIntervalRetryService.retryWithCustomInterval("mixed-errors");

        verify(productsApiClient, times(4)).products(any());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not retry when first attempt succeeds")
    void shouldNotRetryWhenFirstAttemptSucceeds() {

        when(productsApiClient.products(any()))
                .thenReturn(List.of(Product.builder().id(1).build()));

        List<Product> result =
                customIntervalRetryService.retryWithCustomInterval("ok");

        verify(productsApiClient, times(1)).products(any());
        assertThat(result).isNotEmpty();
    }
}