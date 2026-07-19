package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.SparePartRequest;
import com.xniu.rental.asset.dto.SparePartResponse;
import com.xniu.rental.asset.dto.SparePartStockAdjustRequest;
import com.xniu.rental.asset.dto.SparePartStockLogResponse;
import com.xniu.rental.asset.dto.StoreSparePartStockResponse;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/spare-parts")
public class AdminSparePartController {

    private final MaintenanceService maintenanceService;

    public AdminSparePartController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ApiResponse<List<SparePartResponse>> listParts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(maintenanceService.listParts(keyword, status));
    }

    @PostMapping
    public ApiResponse<SparePartResponse> createPart(@Valid @RequestBody SparePartRequest request) {
        return ApiResponse.ok(maintenanceService.createPart(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SparePartResponse> updatePart(@PathVariable Long id, @Valid @RequestBody SparePartRequest request) {
        return ApiResponse.ok(maintenanceService.updatePart(id, request));
    }

    @PostMapping("/{id}/inbound")
    public ApiResponse<SparePartResponse> inbound(@PathVariable Long id, @Valid @RequestBody SparePartStockAdjustRequest request) {
        return ApiResponse.ok(maintenanceService.inbound(id, request));
    }

    @PostMapping("/{id}/purchase")
    public ApiResponse<SparePartResponse> purchase(@PathVariable Long id, @Valid @RequestBody SparePartStockAdjustRequest request) {
        return ApiResponse.ok(maintenanceService.purchase(id, request));
    }

    @PostMapping("/{id}/buyback")
    public ApiResponse<SparePartResponse> buyback(@PathVariable Long id, @Valid @RequestBody SparePartStockAdjustRequest request) {
        return ApiResponse.ok(maintenanceService.buyback(id, request));
    }

    @PostMapping("/{id}/adjust")
    public ApiResponse<SparePartResponse> adjust(@PathVariable Long id, @Valid @RequestBody SparePartStockAdjustRequest request) {
        return ApiResponse.ok(maintenanceService.adjust(id, request));
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
}
