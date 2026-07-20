package com.xniu.rental.order.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.order.dto.RenewalRunRequest;
import com.xniu.rental.order.dto.RenewalRunResponse;
import com.xniu.rental.order.service.OrderRenewalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/order-renewals")
public class AdminOrderRenewalController {

    private final OrderRenewalService orderRenewalService;

    public AdminOrderRenewalController(OrderRenewalService orderRenewalService) {
        this.orderRenewalService = orderRenewalService;
    }

    @PostMapping("/run")
    public ApiResponse<RenewalRunResponse> runDueRenewals(@RequestBody(required = false) RenewalRunRequest request) {
        return ApiResponse.ok(orderRenewalService.runDueRenewals(
            request == null ? null : request.limit(),
            request == null ? null : request.remark()
        ));
    }
}
