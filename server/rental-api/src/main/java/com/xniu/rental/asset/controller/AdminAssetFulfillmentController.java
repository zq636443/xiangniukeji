package com.xniu.rental.asset.controller;

import com.xniu.rental.asset.dto.AssetChangeResponse;
import com.xniu.rental.asset.dto.AssetHandoverResponse;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/asset-fulfillments")
public class AdminAssetFulfillmentController {

    private final AssetFulfillmentService assetFulfillmentService;

    public AdminAssetFulfillmentController(AssetFulfillmentService assetFulfillmentService) {
        this.assetFulfillmentService = assetFulfillmentService;
    }

    @GetMapping("/handovers")
    public ApiResponse<List<AssetHandoverResponse>> listHandovers(
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) String handoverType
    ) {
        return ApiResponse.ok(assetFulfillmentService.listHandovers(orderId, storeId, handoverType));
    }

    @GetMapping("/changes")
    public ApiResponse<List<AssetChangeResponse>> listChanges(
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(assetFulfillmentService.listChanges(orderId, storeId));
    }
}
