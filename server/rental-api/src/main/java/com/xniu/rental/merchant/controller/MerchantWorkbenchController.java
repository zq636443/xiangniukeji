package com.xniu.rental.merchant.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.merchant.dto.StoreResponse;
import com.xniu.rental.merchant.service.MerchantService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/workbench")
public class MerchantWorkbenchController {

    private final MerchantService merchantService;

    public MerchantWorkbenchController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/stores")
    public ApiResponse<List<StoreResponse>> listMyStores() {
        return ApiResponse.ok(merchantService.listMyStores());
    }

    @GetMapping("/stores/{id}")
    public ApiResponse<StoreResponse> getMyStore(@PathVariable Long id) {
        return ApiResponse.ok(merchantService.getMyStore(id));
    }
}
