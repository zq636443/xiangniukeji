package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.AgreementSignRequest;
import com.xniu.rental.pay.dto.AgreementSignResponse;
import com.xniu.rental.pay.service.AgreementDeductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/agreements")
public class UserAgreementController {

    private final AgreementDeductService agreementDeductService;

    public UserAgreementController(AgreementDeductService agreementDeductService) {
        this.agreementDeductService = agreementDeductService;
    }

    @PostMapping("/sign")
    public ApiResponse<AgreementSignResponse> createSign(@Valid @RequestBody AgreementSignRequest request) {
        return ApiResponse.ok(agreementDeductService.createAgreementSign(request.orderId(), request.maxSingleAmount()));
    }
}
