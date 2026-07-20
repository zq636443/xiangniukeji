package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetChangeResponse;
import com.xniu.rental.asset.dto.AssetHandoverResponse;
import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantAssetFulfillmentController {

    private final AssetFulfillmentService assetFulfillmentService;

    public MerchantAssetFulfillmentController(AssetFulfillmentService assetFulfillmentService) {
        this.assetFulfillmentService = assetFulfillmentService;
    }

    @PostMapping("/{orderId}/pickup-assets")
    public ApiResponse<AssetHandoverResponse> pickup(@PathVariable Long orderId, @RequestBody AssetPickupRequest request) {
        return ApiResponse.ok(assetFulfillmentService.pickup(orderId, request));
    }

    @PostMapping("/{orderId}/ship")
    public ApiResponse<AssetHandoverResponse> shipWithoutPayment(@PathVariable Long orderId, @RequestBody AssetPickupRequest request) {
        return ApiResponse.ok(assetFulfillmentService.shipWithoutPayment(orderId, request));
    }

    @PostMapping("/{orderId}/replace-asset")
    public ApiResponse<AssetChangeResponse> replaceAsset(@PathVariable Long orderId, @Valid @RequestBody AssetReplaceRequest request) {
        return ApiResponse.ok(assetFulfillmentService.replaceAsset(orderId, request));
    }

    @PostMapping("/{orderId}/return-assets")
    public ApiResponse<AssetHandoverResponse> returnAssets(@PathVariable Long orderId, @RequestBody AssetReturnRequest request) {
        return ApiResponse.ok(assetFulfillmentService.returnAssets(orderId, request));
    }
}
