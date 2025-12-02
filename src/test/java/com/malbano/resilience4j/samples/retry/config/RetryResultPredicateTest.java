package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryResultPredicate Tests")
class RetryResultPredicateTest {

    private final RetryResultPredicate predicate = new RetryResultPredicate();

    @Test
    @DisplayName("Should return true when product status is GENERATING")
    void shouldReturnTrue_whenStatusIsGenerating() {
        // Given
        Product product = Product.builder()
                .id(1)
                .status("GENERATING")
                .description("Product being generated")
                .build();

        // When
        boolean shouldRetry = predicate.test(product);

        // Then
        assertThat(shouldRetry).isTrue();
    }

    @Test
    @DisplayName("Should return false when product status is ACTIVATED")
    void shouldReturnFalse_whenStatusIsActivated() {
        // Given
        Product product = Product.builder()
                .id(1)
                .status("ACTIVATED")
                .description("Product ready")
                .build();

        // When
        boolean shouldRetry = predicate.test(product);

        // Then
        assertThat(shouldRetry).isFalse();
    }

    @Test
    @DisplayName("Should return false when product status is FAILED")
    void shouldReturnFalse_whenStatusIsFailed() {
        // Given
        Product product = Product.builder()
                .id(1)
                .status("FAILED")
                .description("Product generation failed")
                .build();

        // When
        boolean shouldRetry = predicate.test(product);

        // Then
        assertThat(shouldRetry).isFalse();
    }

    @Test
    @DisplayName("Should return false when product is null")
    void shouldReturnFalse_whenProductIsNull() {
        // When
        boolean shouldRetry = predicate.test(null);

        // Then
        assertThat(shouldRetry).isFalse();
    }

    @Test
    @DisplayName("Should return false when product status is empty")
    void shouldReturnFalse_whenStatusIsEmpty() {
        // Given
        Product product = Product.builder()
                .id(1)
                .status("")
                .description("Empty status")
                .build();

        // When
        boolean shouldRetry = predicate.test(product);

        // Then
        assertThat(shouldRetry).isFalse();
    }
}