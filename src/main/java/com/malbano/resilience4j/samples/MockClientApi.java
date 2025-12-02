package com.malbano.resilience4j.samples;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.malbano.resilience4j.samples.commum.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal-api/products")
@RequiredArgsConstructor
public class MockClientApi {

    private static final List<Product> PRODUCTS = List.of(
            Product.builder().id(1).description("Product HeadSet 01").status("ACTIVATED").build(),
            Product.builder().id(2).description("Product Keyboard 02").status("ACTIVATED").build(),
            Product.builder().id(3).description("Product Mouse 03").status("ACTIVATED").build());

    private final AtomicInteger globalAttemptCounter = new AtomicInteger(0);
    private final AtomicInteger statusAttemptCounter = new AtomicInteger(0);

    @SneakyThrows
    @GetMapping
    public ResponseEntity<List<Product>> products(@RequestParam("type") final String type) {
        String[] sequence = type.split("-");

        int attemptNumber = globalAttemptCounter.getAndIncrement();

        if (attemptNumber >= sequence.length) {
            log.info("Sequence completed, resetting counter and returning success");
            globalAttemptCounter.set(0);
            return ResponseEntity.ok(PRODUCTS);
        }

        String currentStatus = sequence[attemptNumber];
        log.info("Global Attempt {} - Returning status: {}", attemptNumber + 1, currentStatus);

        switch (currentStatus) {
        case "ok":
            log.info("SUCCESS OK ");
            break;
        case "429":
            log.error("error 429");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        case "400":
            log.error("error 400");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        case "404":
            log.error("error 404");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        case "500":
            log.error("error 500");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        case "503":
            log.error("error 503");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        case "timeout":
            log.error("time out");
            Thread.sleep(Duration.ofSeconds(10).toMillis());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
        default:
            log.error("Internal server error ");
            throw new RuntimeException("Internal server error");
        }
        globalAttemptCounter.set(0);
        return ResponseEntity.ok(PRODUCTS);
    }

    @GetMapping("/by-status")
    public ResponseEntity<Product> productsByStatus(@RequestParam("scenario") final String scenario) {
        String[] statusSequence;
        if (StringUtils.isNotBlank(scenario)) {
            statusSequence = scenario.split("-");
        }
        else {
            statusSequence = new String[]{"activated"};
        }

        int attemptNumber = statusAttemptCounter.getAndIncrement();

        // If we've exhausted the sequence, reset and return final status
        if (attemptNumber >= statusSequence.length) {
            log.info("Status sequence completed, resetting counter");
            statusAttemptCounter.set(0);
            // Return activated products as final state
            return ResponseEntity.ok(createProductsWithStatus("ACTIVATED"));
        }

        String currentProductStatus = statusSequence[attemptNumber].toUpperCase();
        log.info("Status Attempt {} - Returning products with status: {}", attemptNumber + 1, currentProductStatus);

        Product productsWithStatus = createProductsWithStatus(currentProductStatus);

        // Reset counter if we reached the last item and it's a success state
        if (attemptNumber == statusSequence.length - 1 || currentProductStatus.equals("ACTIVATED")) {
            statusAttemptCounter.set(0);
        }

        return ResponseEntity.ok(productsWithStatus);
    }

    private Product createProductsWithStatus(String status) {
        return Product.builder().id(1).description("Product HeadSet 01").status(status).build();
    }

}