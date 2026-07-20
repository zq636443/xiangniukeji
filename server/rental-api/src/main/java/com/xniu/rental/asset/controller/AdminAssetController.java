package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetBatchImportRequest;
import com.xniu.rental.asset.dto.AssetBatchImportResponse;
import com.xniu.rental.asset.dto.AssetInvestorChangeRequest;
import com.xniu.rental.asset.dto.AssetDetailResponse;
import com.xniu.rental.asset.dto.AssetLogResponse;
import com.xniu.rental.asset.dto.AssetRequest;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.AssetStatusRequest;
import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.service.AssetService;
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
@RequestMapping("/api/admin/assets")
public class AdminAssetController {

    private final AssetService assetService;
    private final MaintenanceService maintenanceService;

    public AdminAssetController(AssetService assetService, MaintenanceService maintenanceService) {
        this.assetService = assetService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ApiResponse<List<AssetResponse>> listAssets(
        @RequestParam(required = false) Long investorId,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) String assetType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(assetService.listAssets(investorId, merchantId, storeId, assetType, status, keyword));
    }

    @PostMapping
    public ApiResponse<AssetResponse> createAsset(@Valid @RequestBody AssetRequest request) {
        return ApiResponse.ok(assetService.createAsset(request));
    }

    @PostMapping("/batch-import")
    public ApiResponse<AssetBatchImportResponse> batchImport(@Valid @RequestBody AssetBatchImportRequest request) {
        return ApiResponse.ok(assetService.batchImportAssets(request));
    }

    @PutMapping("/{id}/transfer")
    public ApiResponse<AssetResponse> transferAsset(@PathVariable Long id, @Valid @RequestBody AssetTransferRequest request) {
        return ApiResponse.ok(assetService.transferAsset(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AssetResponse> updateAssetStatus(@PathVariable Long id, @Valid @RequestBody AssetStatusRequest request) {
        return ApiResponse.ok(assetService.updateAssetStatus(id, request));
    }

    @PutMapping("/{id}/investor")
    public ApiResponse<AssetResponse> changeInvestor(@PathVariable Long id, @Valid @RequestBody AssetInvestorChangeRequest request) {
        return ApiResponse.ok(assetService.changeInvestor(id, request));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<AssetLogResponse>> listAssetLogs(@PathVariable Long id) {
        return ApiResponse.ok(assetService.listAssetLogs(id));
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<AssetDetailResponse> getAssetDetail(@PathVariable Long id) {
        return ApiResponse.ok(maintenanceService.getAssetDetail(id));
    }
}
