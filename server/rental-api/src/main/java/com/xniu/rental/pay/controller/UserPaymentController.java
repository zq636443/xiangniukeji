package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.AlipayTradeCreateResponse;
import com.xniu.rental.pay.dto.PaymentCreateRequest;
import com.xniu.rental.pay.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/payments")
public class UserPaymentController {

    private final PaymentService paymentService;

    public UserPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/alipay-trade")
    public ApiResponse<AlipayTradeCreateResponse> createAlipayTrade(@Valid @RequestBody PaymentCreateRequest request) {
        return ApiResponse.ok(paymentService.createAlipayTrade(request.billId()));
    }
}
