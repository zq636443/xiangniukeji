package com.xniu.rental.product.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.product.dto.CategoryRequest;
import com.xniu.rental.product.dto.CategoryResponse;
import com.xniu.rental.product.dto.PackageRequest;
import com.xniu.rental.product.dto.PackageResponse;
import com.xniu.rental.product.dto.SkuRequest;
import com.xniu.rental.product.dto.SkuResponse;
import com.xniu.rental.product.dto.StoreSkuBatchPublishRequest;
import com.xniu.rental.product.dto.StoreSkuRequest;
import com.xniu.rental.product.dto.StoreSkuResponse;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(productService.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(productService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(productService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/skus")
    public ApiResponse<List<SkuResponse>> listSkus(@RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(productService.listSkus(categoryId));
    }

    @PostMapping("/skus")
    public ApiResponse<SkuResponse> createSku(@Valid @RequestBody SkuRequest request) {
        return ApiResponse.ok(productService.createSku(request));
    }

    @PutMapping("/skus/{id}")
    public ApiResponse<SkuResponse> updateSku(@PathVariable Long id, @Valid @RequestBody SkuRequest request) {
        return ApiResponse.ok(productService.updateSku(id, request));
    }

    @DeleteMapping("/skus/{id}")
    public ApiResponse<Void> deleteSku(@PathVariable Long id) {
        productService.deleteSku(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/packages")
    public ApiResponse<List<PackageResponse>> listPackages(@RequestParam(required = false) Long skuId) {
        return ApiResponse.ok(productService.listPackages(skuId));
    }

    @PostMapping("/packages")
    public ApiResponse<PackageResponse> createPackage(@Valid @RequestBody PackageRequest request) {
        return ApiResponse.ok(productService.createPackage(request));
    }

    @PutMapping("/packages/{id}")
    public ApiResponse<PackageResponse> updatePackage(@PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        return ApiResponse.ok(productService.updatePackage(id, request));
    }

    @DeleteMapping("/packages/{id}")
    public ApiResponse<Void> deletePackage(@PathVariable Long id) {
        productService.deletePackage(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/store-skus")
    public ApiResponse<List<StoreSkuResponse>> listStoreSkus(
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) Long skuId,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(productService.listStoreSkus(storeId, skuId, status));
    }

    @PostMapping("/store-skus")
    public ApiResponse<StoreSkuResponse> publishStoreSku(@Valid @RequestBody StoreSkuRequest request) {
        return ApiResponse.ok(productService.publishStoreSku(request));
    }

    @PutMapping("/store-skus/{id}")
    public ApiResponse<StoreSkuResponse> updateStoreSku(@PathVariable Long id, @Valid @RequestBody StoreSkuRequest request) {
        return ApiResponse.ok(productService.updateStoreSku(id, request));
    }

    @PostMapping("/store-skus/batch")
    public ApiResponse<List<StoreSkuResponse>> batchPublish(@Valid @RequestBody StoreSkuBatchPublishRequest request) {
        return ApiResponse.ok(productService.batchPublish(request));
    }

    @PutMapping("/store-skus/{id}/status")
    public ApiResponse<StoreSkuResponse> updateStoreSkuStatus(@PathVariable Long id, @RequestParam StoreSkuStatus status) {
        return ApiResponse.ok(productService.updateStoreSkuStatus(id, status));
    }

    @DeleteMapping("/store-skus/{id}")
    public ApiResponse<Void> deleteStoreSku(@PathVariable Long id) {
        productService.deleteStoreSku(id);
        return ApiResponse.ok(null);
    }
}
