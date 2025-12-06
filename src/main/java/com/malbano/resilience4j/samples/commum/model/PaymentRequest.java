// PaymentRequest.java
package com.malbano.resilience4j.samples.commum.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
}