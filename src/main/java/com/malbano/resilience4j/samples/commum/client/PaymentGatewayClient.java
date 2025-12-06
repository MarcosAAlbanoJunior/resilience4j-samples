package com.malbano.resilience4j.samples.commum.client;

import com.malbano.resilience4j.samples.commum.model.PaymentRequest;
import com.malbano.resilience4j.samples.commum.model.PaymentResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = PaymentGatewayClient.CLIENT_NAME, url = "${client.api.url-base}")
public interface PaymentGatewayClient {

    String CLIENT_NAME = "paymentGatewayClient";

    @PostMapping("${client.api.path-payment}/charge")
    PaymentResponse processPayment(
            @RequestHeader(name = "X-Correlation-ID") String correlationId,
            @RequestBody PaymentRequest request,
            @RequestParam(value = "scenario", defaultValue = "ok") String scenario
    );
}