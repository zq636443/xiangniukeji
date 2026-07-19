package com.xniu.rental.product.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.product.dto.StoreSkuResponse;
import com.xniu.rental.product.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/products")
public class UserProductController {

    private final ProductService productService;

    public UserProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/stores/{storeCode}")
    public ApiResponse<List<StoreSkuResponse>> listStoreProducts(@PathVariable String storeCode) {
        return ApiResponse.ok(productService.listUserStoreProducts(storeCode));
    }

    @GetMapping("/store-skus/{id}")
    public ApiResponse<StoreSkuResponse> getStoreProduct(@PathVariable Long id) {
        return ApiResponse.ok(productService.getUserStoreProduct(id));
    }
}
