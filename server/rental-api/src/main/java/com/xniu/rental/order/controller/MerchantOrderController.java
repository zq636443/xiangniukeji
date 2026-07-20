package com.xniu.rental.order.controller;

import com.xniu.rental.bill.dto.BillResponse;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.merchant.service.MerchantService;
import com.xniu.rental.order.dto.OrderBatchImportRequest;
import com.xniu.rental.order.dto.OrderBatchImportResponse;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.service.OrderBatchImportService;
import com.xniu.rental.order.service.OrderCreationService;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.service.SettlementService;
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
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

    private final OrderService orderService;
    private final BillService billService;
    private final OrderCreationService orderCreationService;
    private final OrderBatchImportService orderBatchImportService;
    private final SettlementService settlementService;
    private final MerchantService merchantService;

    public MerchantOrderController(
        OrderService orderService,
        BillService billService,
        OrderCreationService orderCreationService,
        OrderBatchImportService orderBatchImportService,
        SettlementService settlementService,
        MerchantService merchantService
    ) {
        this.orderService = orderService;
        this.billService = billService;
        this.orderCreationService = orderCreationService;
        this.orderBatchImportService = orderBatchImportService;
        this.settlementService = settlementService;
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
        @RequestParam Long storeId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        merchantService.getMyStore(storeId);
        return ApiResponse.ok(orderService.listMerchantOrders(storeId, status, keyword));
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderCreationService.createMerchantOrder(request));
    }

    @PostMapping("/stores/{storeId}/batch-import")
    public ApiResponse<OrderBatchImportResponse> batchImport(
        @PathVariable Long storeId,
        @Valid @RequestBody OrderBatchImportRequest request
    ) {
        merchantService.getMyStore(storeId);
        return ApiResponse.ok(orderBatchImportService.batchImportMerchant(storeId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getMerchantOrder(id));
    }

    @GetMapping("/{id}/bills")
    public ApiResponse<List<BillResponse>> listBills(@PathVariable Long id) {
        orderService.getMerchantOrder(id);
        return ApiResponse.ok(billService.listMerchantOrderBills(id));
    }

    @GetMapping("/{id}/settlement")
    public ApiResponse<SettlementSnapshotResponse> getSettlement(@PathVariable Long id) {
        var order = orderService.getMerchantOrder(id);
        return ApiResponse.ok(settlementService.getMerchantOrderSnapshot(order.id(), order.settlementSnapshotId(), order.merchantId(), order.storeId()));
    }
}
