package com.xniu.rental.externalorder.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCompleteRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderTerminateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingAdjustmentRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingBatchRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingBatchResultResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingConfirmRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingPreviewResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingRevisionResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderRenewalResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.service.ExternalOrderManualRenewalService;
import com.xniu.rental.externalorder.service.ExternalOrderRenewalPricingService;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/external-orders")
public class AdminExternalRentalOrderController {

    private final ExternalRentalOrderService externalRentalOrderService;
    private final ExternalOrderRenewalPricingService pricingService;
    private final ExternalOrderManualRenewalService manualRenewalService;

    public AdminExternalRentalOrderController(
        ExternalRentalOrderService externalRentalOrderService,
        ExternalOrderRenewalPricingService pricingService,
        ExternalOrderManualRenewalService manualRenewalService
    ) {
        this.externalRentalOrderService = externalRentalOrderService;
        this.pricingService = pricingService;
        this.manualRenewalService = manualRenewalService;
    }

    @GetMapping
    public ApiResponse<List<ExternalRentalOrderResponse>> listOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) String sourcePlatform,
        @RequestParam(required = false) Long storeSkuId,
        @RequestParam(required = false) Long packageId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rentStartedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime rentStartedTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedReturnFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedReturnTo,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(externalRentalOrderService.listOrders(
            status, storeId, sourcePlatform, storeSkuId, packageId, rentStartedFrom, rentStartedTo,
            expectedReturnFrom, expectedReturnTo, keyword
        ));
    }

    @GetMapping("/renewals")
    public ApiResponse<List<ExternalOrderRenewalResponse>> listRenewals(
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(externalRentalOrderService.listRenewals(storeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExternalRentalOrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(externalRentalOrderService.getOrder(id));
    }

    @PostMapping
    public ApiResponse<ExternalRentalOrderResponse> createOrder(@Valid @RequestBody ExternalRentalOrderCreateRequest request) {
        return ApiResponse.ok(externalRentalOrderService.createOrder(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExternalRentalOrderResponse> updateOrder(
        @PathVariable Long id,
        @Valid @RequestBody ExternalRentalOrderUpdateRequest request
    ) {
        return ApiResponse.ok(externalRentalOrderService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrder(@PathVariable Long id) {
        externalRentalOrderService.deleteOrder(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch-import")
    public ApiResponse<ExternalRentalOrderBatchImportResponse> batchImport(@Valid @RequestBody ExternalRentalOrderBatchImportRequest request) {
        return ApiResponse.ok(externalRentalOrderService.batchImport(request));
    }

    @GetMapping("/{id}/renewal-pricing-revisions")
    public ApiResponse<List<ExternalOrderPricingRevisionResponse>> listPricingRevisions(@PathVariable Long id) {
        return ApiResponse.ok(pricingService.list(id));
    }

    @PostMapping("/{id}/renewal-pricing-adjustments")
    public ApiResponse<ExternalOrderPricingRevisionResponse> adjustPricing(
        @PathVariable Long id,
        @Valid @RequestBody ExternalOrderPricingAdjustmentRequest request
    ) {
        return ApiResponse.ok(pricingService.adjust(id, request));
    }

    @PostMapping("/{id}/manual-renewals")
    public ApiResponse<ExternalOrderRenewalResponse> createManualRenewal(
        @PathVariable Long id,
        @Valid @RequestBody ExternalOrderManualRenewalRequest request
    ) {
        return ApiResponse.ok(manualRenewalService.create(id, request));
    }

    @PostMapping("/renewal-pricing-revisions/{revisionId}/confirm")
    public ApiResponse<ExternalOrderPricingRevisionResponse> confirmPricing(
        @PathVariable Long revisionId,
        @Valid @RequestBody ExternalOrderPricingConfirmRequest request
    ) {
        return ApiResponse.ok(pricingService.confirm(revisionId, request));
    }

    @PostMapping("/renewal-pricing/batch-preview")
    public ApiResponse<ExternalOrderPricingPreviewResponse> previewBatchPricing(
        @Valid @RequestBody ExternalOrderPricingBatchRequest request
    ) {
        return ApiResponse.ok(pricingService.previewBatch(request));
    }

    @PostMapping("/renewal-pricing/batch-adjust")
    public ApiResponse<ExternalOrderPricingBatchResultResponse> adjustBatchPricing(
        @Valid @RequestBody ExternalOrderPricingBatchRequest request
    ) {
        return ApiResponse.ok(pricingService.adjustBatch(request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ExternalRentalOrderResponse> complete(@PathVariable Long id, @RequestBody(required = false) ExternalRentalOrderCompleteRequest request) {
        return ApiResponse.ok(externalRentalOrderService.complete(id, request));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<ExternalRentalOrderResponse> terminate(@PathVariable Long id, @Valid @RequestBody ExternalRentalOrderTerminateRequest request) {
        return ApiResponse.ok(externalRentalOrderService.terminate(id, request));
    }
}
