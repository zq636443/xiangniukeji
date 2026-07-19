package com.xniu.rental.pay.controller;

import com.xniu.rental.pay.service.PaymentService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pay/alipay")
public class AlipayNotifyController {

    private final PaymentService paymentService;

    public AlipayNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        return paymentService.handleAlipayNotify(params) ? "success" : "failure";
    }
}
