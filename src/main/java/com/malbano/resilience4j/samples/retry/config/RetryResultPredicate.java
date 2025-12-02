package com.malbano.resilience4j.samples.retry.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import com.malbano.resilience4j.samples.commum.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.function.Predicate;

@Slf4j
public class RetryResultPredicate implements Predicate<Product> {

    @Override
    public boolean test(Product result) {
        log.info("=== PREDICATE CALLED ===");

        if (result == null) {
            log.info("Not an Product response - No retry");
            return false;
        }

        String productStatus = result.getStatus();

        boolean shouldRetry = productStatus.equals("GENERATING");

        log.info("Product Status: {} - Should retry: {}", productStatus, shouldRetry);
        return shouldRetry;
    }
}
