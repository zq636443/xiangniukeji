package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.FundAuthCreateRequest;
import com.xniu.rental.pay.dto.FundAuthCreateResponse;
import com.xniu.rental.pay.dto.FundAuthResponse;
import com.xniu.rental.pay.service.FundAuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/fund-auths")
public class UserFundAuthController {

    private final FundAuthService fundAuthService;

    public UserFundAuthController(FundAuthService fundAuthService) {
        this.fundAuthService = fundAuthService;
    }

    @GetMapping
    public ApiResponse<List<FundAuthResponse>> listAuths(@RequestParam(required = false) Long orderId) {
        return ApiResponse.ok(fundAuthService.listUserAuths(orderId));
    }

    @PostMapping
    public ApiResponse<FundAuthCreateResponse> createAuth(@Valid @RequestBody FundAuthCreateRequest request) {
        return ApiResponse.ok(fundAuthService.createUserFundAuth(request.orderId(), request.authAmount()));
    }
}
