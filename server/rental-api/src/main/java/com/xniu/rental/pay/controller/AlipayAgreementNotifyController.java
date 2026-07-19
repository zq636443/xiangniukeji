package com.xniu.rental.pay.controller;

import com.xniu.rental.pay.service.AgreementDeductService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pay/alipay/agreement")
public class AlipayAgreementNotifyController {

    private final AgreementDeductService agreementDeductService;

    public AlipayAgreementNotifyController(AgreementDeductService agreementDeductService) {
        this.agreementDeductService = agreementDeductService;
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        return agreementDeductService.handleAgreementNotify(params) ? "success" : "failure";
    }
}
