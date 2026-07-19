package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.SparePartStockLogResponse;
import com.xniu.rental.asset.dto.SparePartTransferRequest;
import com.xniu.rental.asset.dto.StoreSparePartStockResponse;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/spare-parts")
public class MerchantSparePartController {

    private final MaintenanceService maintenanceService;

    public MerchantSparePartController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/store-stocks")
    public ApiResponse<List<StoreSparePartStockResponse>> listStoreStocks(
        @RequestParam(required = false) Long partId,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(maintenanceService.listStoreStocks(partId, merchantId, storeId));
    }

    @GetMapping("/logs")
    public ApiResponse<List<SparePartStockLogResponse>> listLogs(
        @RequestParam(required = false) Long partId,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(maintenanceService.listStockLogs(partId, merchantId, storeId));
    }

    @PostMapping("/transfer")
    public ApiResponse<List<StoreSparePartStockResponse>> transfer(@Valid @RequestBody SparePartTransferRequest request) {
        return ApiResponse.ok(maintenanceService.transferStoreStock(request));
    }
}
