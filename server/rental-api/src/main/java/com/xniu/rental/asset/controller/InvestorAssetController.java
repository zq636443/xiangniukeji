package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetDetailResponse;
import com.xniu.rental.asset.dto.AssetMerchantOptionResponse;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.AssetStatusRequest;
import com.xniu.rental.asset.dto.AssetStoreOptionResponse;
import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.dto.AssetTypeResponse;
import com.xniu.rental.asset.dto.InvestorAssetRequest;
import com.xniu.rental.asset.dto.InvestorAssetUpdateRequest;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.asset.service.AssetTypeService;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.common.ApiResponse;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investor/assets")
public class InvestorAssetController {

    private final AssetService assetService;
    private final AssetTypeService assetTypeService;
    private final MaintenanceService maintenanceService;

    public InvestorAssetController(
        AssetService assetService,
        AssetTypeService assetTypeService,
        MaintenanceService maintenanceService
    ) {
        this.assetService = assetService;
        this.assetTypeService = assetTypeService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ApiResponse<List<AssetResponse>> listMyAssets() {
        return ApiResponse.ok(assetService.listInvestorAssets());
    }

    @GetMapping("/merchants")
    public ApiResponse<List<AssetMerchantOptionResponse>> listMerchants() {
        return ApiResponse.ok(assetService.listInvestorMerchantOptions());
    }

    @GetMapping("/stores")
    public ApiResponse<List<AssetStoreOptionResponse>> listStores() {
        return ApiResponse.ok(assetService.listInvestorStoreOptions());
    }

    @GetMapping("/types")
    public ApiResponse<List<AssetTypeResponse>> listTypes() {
        return ApiResponse.ok(assetTypeService.listTypes(true));
    }

    @PostMapping
    public ApiResponse<AssetResponse> createAsset(@Valid @RequestBody InvestorAssetRequest request) {
        return ApiResponse.ok(assetService.createInvestorAsset(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetResponse> updateAsset(
        @PathVariable Long id,
        @Valid @RequestBody InvestorAssetUpdateRequest request
    ) {
        return ApiResponse.ok(assetService.updateInvestorAsset(id, request));
    }

    @PutMapping("/{id}/transfer")
    public ApiResponse<AssetResponse> transferAsset(
        @PathVariable Long id,
        @Valid @RequestBody AssetTransferRequest request
    ) {
        return ApiResponse.ok(assetService.transferInvestorAsset(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AssetResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody AssetStatusRequest request
    ) {
        return ApiResponse.ok(assetService.updateInvestorAssetStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteInvestorAsset(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<AssetDetailResponse> getAssetDetail(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.getAssetDetail(id));
    }
}
