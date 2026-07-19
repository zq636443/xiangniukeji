package com.xniu.rental.externalorder.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCompleteRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderTerminateRequest;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
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
@RequestMapping("/api/admin/external-orders")
public class AdminExternalRentalOrderController {

    private final ExternalRentalOrderService externalRentalOrderService;

    public AdminExternalRentalOrderController(ExternalRentalOrderService externalRentalOrderService) {
        this.externalRentalOrderService = externalRentalOrderService;
    }

    @GetMapping
    public ApiResponse<List<ExternalRentalOrderResponse>> listOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) String sourcePlatform,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(externalRentalOrderService.listOrders(status, storeId, sourcePlatform, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExternalRentalOrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(externalRentalOrderService.getOrder(id));
    }

    @PostMapping
    public ApiResponse<ExternalRentalOrderResponse> createOrder(@Valid @RequestBody ExternalRentalOrderCreateRequest request) {
        return ApiResponse.ok(externalRentalOrderService.createOrder(request));
    }

    @PostMapping("/batch-import")
    public ApiResponse<ExternalRentalOrderBatchImportResponse> batchImport(@Valid @RequestBody ExternalRentalOrderBatchImportRequest request) {
        return ApiResponse.ok(externalRentalOrderService.batchImport(request));
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
