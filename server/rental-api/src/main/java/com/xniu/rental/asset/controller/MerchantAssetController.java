package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetBatchImportRequest;
import com.xniu.rental.asset.dto.AssetBatchImportResponse;
import com.xniu.rental.asset.dto.AssetDetailResponse;
import com.xniu.rental.asset.dto.AssetInvestorOptionResponse;
import com.xniu.rental.asset.dto.AssetRequest;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.AssetTypeResponse;
import com.xniu.rental.asset.dto.AssetUpdateRequest;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.asset.service.AssetTypeService;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/assets")
public class MerchantAssetController {

    private final AssetService assetService;
    private final AssetTypeService assetTypeService;
    private final MaintenanceService maintenanceService;

    public MerchantAssetController(AssetService assetService, AssetTypeService assetTypeService, MaintenanceService maintenanceService) {
        this.assetService = assetService;
        this.assetTypeService = assetTypeService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/types")
    public ApiResponse<List<AssetTypeResponse>> listTypes() {
        return ApiResponse.ok(assetTypeService.listTypes(false));
    }

    @GetMapping("/investors")
    public ApiResponse<List<AssetInvestorOptionResponse>> listInvestorOptions() {
        return ApiResponse.ok(assetService.listMerchantInvestorOptions());
    }

    @GetMapping("/stores/{storeId}")
    public ApiResponse<List<AssetResponse>> listStoreAssets(@PathVariable Long storeId) {
        return ApiResponse.ok(assetService.listMerchantStoreAssets(storeId));
    }

    @PostMapping("/stores/{storeId}")
    public ApiResponse<AssetResponse> createStoreAsset(
        @PathVariable Long storeId,
        @Valid @RequestBody AssetRequest request
    ) {
        return ApiResponse.ok(assetService.createMerchantAsset(storeId, request));
    }

    @PutMapping("/stores/{storeId}/{id}")
    public ApiResponse<AssetResponse> updateStoreAsset(
        @PathVariable Long storeId,
        @PathVariable Long id,
        @Valid @RequestBody AssetUpdateRequest request
    ) {
        return ApiResponse.ok(assetService.updateMerchantAsset(storeId, id, request));
    }

    @DeleteMapping("/stores/{storeId}/{id}")
    public ApiResponse<Void> deleteStoreAsset(@PathVariable Long storeId, @PathVariable Long id) {
        assetService.deleteMerchantAsset(storeId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/stores/{storeId}/batch-import")
    public ApiResponse<AssetBatchImportResponse> batchImport(
        @PathVariable Long storeId,
        @Valid @RequestBody AssetBatchImportRequest request
    ) {
        return ApiResponse.ok(assetService.batchImportMerchantAssets(storeId, request));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<AssetDetailResponse> getAssetDetail(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.getAssetDetail(id));
    }
}
