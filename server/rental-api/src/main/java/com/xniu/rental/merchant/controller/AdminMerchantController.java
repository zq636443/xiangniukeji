package com.xniu.rental.merchant.controller;

import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.merchant.dto.EmployeeRequest;
import com.xniu.rental.merchant.dto.EmployeeResponse;
import com.xniu.rental.merchant.dto.MerchantRequest;
import com.xniu.rental.merchant.dto.MerchantResponse;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.dto.StoreResponse;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.service.MerchantService;
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
@RequestMapping("/api/admin")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/merchants")
    public ApiResponse<List<MerchantResponse>> listMerchants(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(merchantService.listMerchants(keyword));
    }

    @PostMapping("/merchants")
    public ApiResponse<MerchantResponse> createMerchant(@Valid @RequestBody MerchantRequest request) {
        return ApiResponse.ok(merchantService.createMerchant(request));
    }

    @PutMapping("/merchants/{id}")
    public ApiResponse<MerchantResponse> updateMerchant(@PathVariable Long id, @Valid @RequestBody MerchantRequest request) {
        return ApiResponse.ok(merchantService.updateMerchant(id, request));
    }

    @PutMapping("/merchants/{id}/status")
    public ApiResponse<MerchantResponse> updateMerchantStatus(@PathVariable Long id, @RequestParam MerchantStatus status) {
        return ApiResponse.ok(merchantService.updateMerchantStatus(id, status));
    }

    @GetMapping("/stores")
    public ApiResponse<List<StoreResponse>> listStores(
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(merchantService.listStores(merchantId, keyword));
    }

    @PostMapping("/stores")
    public ApiResponse<StoreResponse> createStore(@Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok(merchantService.createStore(request));
    }

    @PutMapping("/stores/{id}")
    public ApiResponse<StoreResponse> updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok(merchantService.updateStore(id, request));
    }

    @DeleteMapping("/stores/{id}")
    public ApiResponse<Void> deleteStore(@PathVariable Long id) {
        merchantService.deleteStore(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/stores/{id}/status")
    public ApiResponse<StoreResponse> updateStoreStatus(@PathVariable Long id, @RequestParam StoreStatus status) {
        return ApiResponse.ok(merchantService.updateStoreStatus(id, status));
    }

    @PostMapping("/stores/{id}/qrcode")
    public ApiResponse<StoreResponse> regenerateStoreQrcode(@PathVariable Long id) {
        return ApiResponse.ok(merchantService.regenerateStoreQrcode(id));
    }

    @GetMapping("/employees")
    public ApiResponse<List<EmployeeResponse>> listEmployees(@RequestParam Long merchantId) {
        return ApiResponse.ok(merchantService.listEmployees(merchantId));
    }

    @PostMapping("/employees")
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.ok(merchantService.createEmployee(request));
    }

    @PutMapping("/employees/{id}/status")
    public ApiResponse<EmployeeResponse> updateEmployeeStatus(@PathVariable Long id, @RequestParam AccountStatus status) {
        return ApiResponse.ok(merchantService.updateEmployeeStatus(id, status));
    }
}
