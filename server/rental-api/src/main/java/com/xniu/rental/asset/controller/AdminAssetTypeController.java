package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetTypeRequest;
import com.xniu.rental.asset.dto.AssetTypeResponse;
import com.xniu.rental.asset.service.AssetTypeService;
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
@RequestMapping("/api/admin/asset-types")
public class AdminAssetTypeController {

    private final AssetTypeService assetTypeService;

    public AdminAssetTypeController(AssetTypeService assetTypeService) {
        this.assetTypeService = assetTypeService;
    }

    @GetMapping
    public ApiResponse<List<AssetTypeResponse>> listTypes(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return ApiResponse.ok(assetTypeService.listTypes(enabledOnly));
    }

    @PostMapping
    public ApiResponse<AssetTypeResponse> createType(@Valid @RequestBody AssetTypeRequest request) {
        return ApiResponse.ok(assetTypeService.createType(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetTypeResponse> updateType(@PathVariable Long id, @Valid @RequestBody AssetTypeRequest request) {
        return ApiResponse.ok(assetTypeService.updateType(id, request));
    }
}
