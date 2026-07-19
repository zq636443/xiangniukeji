package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetDetailResponse;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/assets")
public class MerchantAssetController {

    private final AssetService assetService;
    private final MaintenanceService maintenanceService;

    public MerchantAssetController(AssetService assetService, MaintenanceService maintenanceService) {
        this.assetService = assetService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/stores/{storeId}")
    public ApiResponse<List<AssetResponse>> listStoreAssets(@PathVariable Long storeId) {
        return ApiResponse.ok(assetService.listMerchantStoreAssets(storeId));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<AssetDetailResponse> getAssetDetail(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.getAssetDetail(id));
    }
}
