package com.malbano.resilience4j.samples.retry;

import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.model.Product;
import com.malbano.resilience4j.samples.retry.service.PredicateResultRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("PredicateResultRetryService Tests")
class PredicateResultRetryServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private PredicateResultRetryService predicateResultRetryService;

    @Test
    @DisplayName("Should return success on first attempt when product status is ACTIVATED")
    void shouldNotRetry_whenFirstAttemptReturnsActivatedStatus() {
        // Given
        Product activatedProduct = Product.builder()
                .id(1)
                .status("ACTIVATED")
                .description("Product ready")
                .build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(activatedProduct);

        // When
        Product result = predicateResultRetryService.retryOnResult("activated");

        // Then
        verify(productsApiClient, times(1)).productByStatus(any());
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVATED");
    }

    @Test
    @DisplayName("Should retry twice and succeed when status changes from GENERATING to ACTIVATED")
    void shouldRetryAndSucceed_whenStatusChangesFromGeneratingToActivated() {
        // Given
        Product generatingProduct = Product.builder()
                .id(1)
                .status("GENERATING")
                .description("Product being generated")
                .build();

        Product activatedProduct = Product.builder()
                .id(1)
                .status("ACTIVATED")
                .description("Product ready")
                .build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(generatingProduct)
                .thenReturn(generatingProduct)
                .thenReturn(activatedProduct);

        // When
        Product result = predicateResultRetryService.retryOnResult("generating-generating-activated");

        // Then
        verify(productsApiClient, times(3)).productByStatus(any());
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVATED");
    }

    @Test
    @DisplayName("Should retry and call fallback when max attempts exhausted with GENERATING status")
    void shouldCallFallback_whenMaxAttemptsExhaustedWithGeneratingStatus() {
        // Given
        Product generatingProduct = Product.builder()
                .id(1)
                .status("GENERATING")
                .description("Product still generating")
                .build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(generatingProduct);

        // When
        Product result = predicateResultRetryService.retryOnResult("generating-generating-generating");

        // Then
        verify(productsApiClient, times(3)).productByStatus(any()); // maxAttempts=3
        assertThat(result.getStatus()).isEqualTo("GENERATING"); // not call fallback because is not an exception
    }

    @Test
    @DisplayName("Should retry once when first attempt is GENERATING and second is ACTIVATED")
    void shouldRetryOnce_whenFirstGeneratingSecondActivated() {
        // Given
        Product generatingProduct = Product.builder()
                .id(1)
                .status("GENERATING")
                .description("Product being generated")
                .build();

        Product activatedProduct = Product.builder()
                .id(1)
                .status("ACTIVATED")
                .description("Product ready")
                .build();

        when(productsApiClient.productByStatus(any()))
                .thenReturn(generatingProduct)
                .thenReturn(activatedProduct);

        // When
        Product result = predicateResultRetryService.retryOnResult("generating-activated");

        // Then
        verify(productsApiClient, times(2)).productByStatus(any());
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVATED");
    }

    @Test
    @DisplayName("Should handle exception and call fallback")
    void shouldCallFallback_whenExceptionOccurs() {
        // Given
        when(productsApiClient.productByStatus(any()))
                .thenThrow(new RuntimeException("API Error"));

        // When
        Product result = predicateResultRetryService.retryOnResult("error");

        // Then
        verify(productsApiClient, times(3)).productByStatus(any());
        assertThat(result).isNull(); // Fallback return null
    }
}