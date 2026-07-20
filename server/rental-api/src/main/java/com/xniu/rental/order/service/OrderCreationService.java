package com.xniu.rental.order.service;

import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationService {

    private final OrderService orderService;
    private final BillService billService;

    public OrderCreationService(OrderService orderService, BillService billService) {
        this.orderService = orderService;
        this.billService = billService;
    }

    @Transactional
    public OrderResponse createAdminOrder(OrderCreateRequest request) {
        var order = orderService.createOrder(request);
        billService.generatePlan(order.id(), "总部新建订单生成账单");
        return order;
    }

    @Transactional
    public OrderResponse createMerchantOrder(OrderCreateRequest request) {
        var order = orderService.createMerchantOrder(request);
        billService.generatePlanForMerchant(order.id(), "商户新建订单生成账单");
        return order;
    }
}
