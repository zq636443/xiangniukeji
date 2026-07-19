package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetMaintenanceRequest;
import com.xniu.rental.asset.dto.AssetMaintenanceResponse;
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
@RequestMapping("/api/merchant/maintenances")
public class MerchantMaintenanceController {

    private final MaintenanceService maintenanceService;

    public MerchantMaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ApiResponse<List<AssetMaintenanceResponse>> listMaintenances(
        @RequestParam(required = false) Long assetId,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(maintenanceService.listMaintenances(assetId, orderId, storeId));
    }

    @PostMapping
    public ApiResponse<AssetMaintenanceResponse> createMaintenance(@Valid @RequestBody AssetMaintenanceRequest request) {
        return ApiResponse.ok(maintenanceService.createMaintenance(request));
    }
}
