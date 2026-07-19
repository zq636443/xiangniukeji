package com.xniu.rental.pay.controller;

import com.xniu.rental.pay.service.FundAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pay/alipay/fund-auth")
public class AlipayFundAuthNotifyController {

    private final FundAuthService fundAuthService;

    public AlipayFundAuthNotifyController(FundAuthService fundAuthService) {
        this.fundAuthService = fundAuthService;
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        return fundAuthService.handleNotify(params) ? "success" : "failure";
    }
}
