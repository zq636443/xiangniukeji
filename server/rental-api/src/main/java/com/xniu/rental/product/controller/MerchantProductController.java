package com.xniu.rental.product.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.product.dto.StoreSkuResponse;
import com.xniu.rental.product.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/products")
public class MerchantProductController {

    private final ProductService productService;

    public MerchantProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/store-skus")
    public ApiResponse<List<StoreSkuResponse>> listStoreSkus(@RequestParam Long storeId) {
        return ApiResponse.ok(productService.listMerchantStoreSkus(storeId));
    }
}
