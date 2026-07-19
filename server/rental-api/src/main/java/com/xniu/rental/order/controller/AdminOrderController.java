package com.xniu.rental.order.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.order.dto.OrderCancelRequest;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderExceptionRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.OrderTransitionRequest;
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

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) Long userAccountId
    ) {
        return ApiResponse.ok(orderService.listOrders(status, storeId, userAccountId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(id));
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.createOrder(request));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<OrderResponse> transition(@PathVariable Long id, @Valid @RequestBody OrderTransitionRequest request) {
        return ApiResponse.ok(orderService.transition(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, @Valid @RequestBody OrderCancelRequest request) {
        return ApiResponse.ok(orderService.cancel(id, request));
    }

    @PostMapping("/{id}/exception")
    public ApiResponse<OrderResponse> markException(@PathVariable Long id, @Valid @RequestBody OrderExceptionRequest request) {
        return ApiResponse.ok(orderService.markException(id, request));
    }
}
