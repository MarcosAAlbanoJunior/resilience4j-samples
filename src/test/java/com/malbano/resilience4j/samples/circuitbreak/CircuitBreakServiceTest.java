package com.malbano.resilience4j.samples.circuitbreak;

import com.malbano.resilience4j.samples.circuitbreak.service.CircuitBreakService;
import com.malbano.resilience4j.samples.commum.client.ProductsApiClient;
import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import com.malbano.resilience4j.samples.commum.model.Product;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("CircuitBreakService Tests")
class CircuitBreakServiceTest {

    @MockitoBean
    private ProductsApiClient productsApiClient;

    @Autowired
    private CircuitBreakService circuitBreakService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("products-cb").reset();
        reset(productsApiClient);
    }

    @Test
    @DisplayName("Should successfully fetch products when API responds correctly")
    void shouldSuccessfullyFetchProductsWhenApiRespondsCorrectly() {
        
        when(productsApiClient.products("ok"))
                .thenReturn(List.of(
                        Product.builder().id(1).build(),
                        Product.builder().id(2).build()
                ));

        List<Product> result = circuitBreakService.getProducts(true);

        verify(productsApiClient, times(1)).products("ok");
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should throw exception on first failure before circuit opens")
    void shouldThrowExceptionOnFirstFailureBeforeCircuitOpens() {
        
        when(productsApiClient.products("429"))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> circuitBreakService.getProducts(false))
                .isInstanceOf(ResponseStatusException.class);

        verify(productsApiClient, times(1)).products("429");
    }

    @Test
    @DisplayName("Should open circuit after failure rate threshold is reached")
    void shouldOpenCircuitAfterFailureRateThresholdIsReached() {
        
        when(productsApiClient.products("429"))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS));

        for (int i = 0; i < 3; i++) {
            try {
                circuitBreakService.getProducts(false);
            } catch (Exception e) {
            }
        }

        assertThatThrownBy(() -> circuitBreakService.getProducts(false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Service temporarily unavailable");

        verify(productsApiClient, times(3)).products("429");
    }

    @Test
    @DisplayName("Should allow calls when circuit is closed and some succeed")
    void shouldAllowCallsWhenCircuitIsClosedAndSomeSucceed() {
        
        when(productsApiClient.products("ok"))
                .thenReturn(List.of(Product.builder().id(1).build()));
        when(productsApiClient.products("429"))
                .thenThrow(new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS));

        circuitBreakService.getProducts(true);
        circuitBreakService.getProducts(true);

        try {
            circuitBreakService.getProducts(false);
        } catch (Exception e) {
        }

        List<Product> result = circuitBreakService.getProducts(true);

        verify(productsApiClient, times(3)).products("ok");
        verify(productsApiClient, times(1)).products("429");
        assertThat(result).hasSize(1);
    }
}