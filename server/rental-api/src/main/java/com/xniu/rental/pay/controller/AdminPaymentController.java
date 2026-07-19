package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.PaymentCallbackResponse;
import com.xniu.rental.pay.dto.PaymentRefundRequest;
import com.xniu.rental.pay.dto.PaymentResponse;
import com.xniu.rental.pay.service.PaymentService;
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
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<List<PaymentResponse>> listPayments(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long billId,
        @RequestParam(required = false) Long orderId
    ) {
        return ApiResponse.ok(paymentService.listPayments(status, billId, orderId));
    }

    @GetMapping("/callbacks")
    public ApiResponse<List<PaymentCallbackResponse>> listCallbacks() {
        return ApiResponse.ok(paymentService.listCallbacks());
    }

    @PostMapping("/{id}/query")
    public ApiResponse<PaymentResponse> queryAndSync(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.queryAndSync(id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refund(@PathVariable Long id, @Valid @RequestBody PaymentRefundRequest request) {
        return ApiResponse.ok(paymentService.refund(id, request.refundAmount()));
    }
}
