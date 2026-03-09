package com.shopping.order.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${app.services.product-service.url}")
public interface ProductServiceClient {

    @GetMapping("/api/products/{productId}")
    ProductDto getProduct(@PathVariable("productId") UUID productId);
}
