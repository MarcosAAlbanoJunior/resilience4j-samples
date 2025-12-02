package com.malbano.resilience4j.samples.commum.client;

import com.malbano.resilience4j.samples.commum.model.Product;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ProductsApiClient.CLIENT_NAME, url = "${client.api.url-base}")
public interface ProductsApiClient {

    String CLIENT_NAME = "productApiClient";

    @GetMapping("${client.api.path-products}")
    List<Product> products(@RequestParam("type") final String type);

    @GetMapping("${client.api.path-products}/by-status")
    Product productByStatus(@RequestParam("scenario") final String scenario);

}