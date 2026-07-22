package com.xniu.rental.order.controller;

import com.xniu.rental.asset.dto.AssetChangeResponse;
import com.xniu.rental.asset.dto.AssetHandoverResponse;
import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.order.dto.OrderBatchImportRequest;
import com.xniu.rental.order.dto.OrderBatchImportResponse;
import com.xniu.rental.order.dto.OrderCancelRequest;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderExceptionRequest;
import com.xniu.rental.order.dto.OrderLeaseBonusRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.service.OrderBatchImportService;
import com.xniu.rental.order.service.OrderCreationService;
import com.xniu.rental.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderCreationService orderCreationService;
    private final OrderBatchImportService orderBatchImportService;
    private final AssetFulfillmentService assetFulfillmentService;

    public AdminOrderController(
        OrderService orderService,
        OrderCreationService orderCreationService,
        OrderBatchImportService orderBatchImportService,
        AssetFulfillmentService assetFulfillmentService
    ) {
        this.orderService = orderService;
        this.orderCreationService = orderCreationService;
        this.orderBatchImportService = orderBatchImportService;
        this.assetFulfillmentService = assetFulfillmentService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) Long userAccountId,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(orderService.listOrders(status, storeId, userAccountId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(id));
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderCreationService.createAdminOrder(request));
    }

    @PostMapping("/batch-import")
    public ApiResponse<OrderBatchImportResponse> batchImport(@Valid @RequestBody OrderBatchImportRequest request) {
        return ApiResponse.ok(orderBatchImportService.batchImportAdmin(request));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<OrderResponse> transition(@PathVariable Long id, @Valid @RequestBody OrderTransitionRequest request) {
        return ApiResponse.ok(orderService.transition(id, request));
    }

    @PostMapping("/{id}/lease-bonuses")
    public ApiResponse<OrderResponse> grantLeaseBonus(
        @PathVariable Long id,
        @Valid @RequestBody OrderLeaseBonusRequest request
    ) {
        return ApiResponse.ok(orderService.grantLeaseBonus(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, @Valid @RequestBody OrderCancelRequest request) {
        return ApiResponse.ok(orderService.cancel(id, request));
    }

    @PostMapping("/{id}/exception")
    public ApiResponse<OrderResponse> markException(@PathVariable Long id, @Valid @RequestBody OrderExceptionRequest request) {
        return ApiResponse.ok(orderService.markException(id, request));
    }

    @PostMapping("/{id}/pickup-assets")
    public ApiResponse<AssetHandoverResponse> pickupAssets(@PathVariable Long id, @RequestBody AssetPickupRequest request) {
        return ApiResponse.ok(assetFulfillmentService.pickup(id, request));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<AssetHandoverResponse> shipWithoutPayment(@PathVariable Long id, @RequestBody AssetPickupRequest request) {
        return ApiResponse.ok(assetFulfillmentService.shipWithoutPayment(id, request));
    }

    @PostMapping("/{id}/replace-asset")
    public ApiResponse<AssetChangeResponse> replaceAsset(@PathVariable Long id, @Valid @RequestBody AssetReplaceRequest request) {
        return ApiResponse.ok(assetFulfillmentService.replaceAsset(id, request));
    }

    @PostMapping("/{id}/return-assets")
    public ApiResponse<AssetHandoverResponse> returnAssets(@PathVariable Long id, @RequestBody AssetReturnRequest request) {
        return ApiResponse.ok(assetFulfillmentService.returnAssets(id, request));
    }
}
