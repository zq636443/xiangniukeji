package com.xniu.rental.order.controller;

import com.xniu.rental.bill.dto.BillResponse;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.merchant.service.MerchantService;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.service.SettlementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

    private final OrderService orderService;
    private final BillService billService;
    private final SettlementService settlementService;
    private final MerchantService merchantService;

    public MerchantOrderController(OrderService orderService, BillService billService, SettlementService settlementService, MerchantService merchantService) {
        this.orderService = orderService;
        this.billService = billService;
        this.settlementService = settlementService;
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
        @RequestParam Long storeId,
        @RequestParam(required = false) String status
    ) {
        merchantService.getMyStore(storeId);
        return ApiResponse.ok(orderService.listMerchantOrders(storeId, status));
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
