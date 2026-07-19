package com.xniu.rental.order.controller;

import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.UserOrderCreateResponse;
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
@RequestMapping("/api/user/orders")
public class UserOrderController {

    private final OrderService orderService;
    private final BillService billService;

    public UserOrderController(OrderService orderService, BillService billService) {
        this.orderService = orderService;
        this.billService = billService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(@RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.listUserOrders(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getUserOrder(id));
    }

    @PostMapping
    public ApiResponse<UserOrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        var order = orderService.createUserOrder(request);
        var bills = billService.generatePlanForUser(order.id(), "用户下单生成账单").bills();
        return ApiResponse.ok(new UserOrderCreateResponse(order, bills));
    }
}
