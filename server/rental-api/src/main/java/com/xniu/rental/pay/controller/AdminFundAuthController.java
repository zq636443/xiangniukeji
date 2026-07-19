package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.FundAuthCaptureRequest;
import com.xniu.rental.pay.dto.FundAuthNotifyResponse;
import com.xniu.rental.pay.dto.FundAuthOperationResponse;
import com.xniu.rental.pay.dto.FundAuthResponse;
import com.xniu.rental.pay.dto.FundAuthUnfreezeRequest;
import com.xniu.rental.pay.service.FundAuthService;
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
@RequestMapping("/api/admin/fund-auths")
public class AdminFundAuthController {

    private final FundAuthService fundAuthService;

    public AdminFundAuthController(FundAuthService fundAuthService) {
        this.fundAuthService = fundAuthService;
    }

    @GetMapping
    public ApiResponse<List<FundAuthResponse>> listAuths(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long userAccountId
    ) {
        return ApiResponse.ok(fundAuthService.listAdminAuths(status, orderId, userAccountId));
    }

    @GetMapping("/{id}/operations")
    public ApiResponse<List<FundAuthOperationResponse>> listOperations(@PathVariable Long id) {
        return ApiResponse.ok(fundAuthService.listOperations(id));
    }

    @GetMapping("/notifies")
    public ApiResponse<List<FundAuthNotifyResponse>> listNotifies() {
        return ApiResponse.ok(fundAuthService.listNotifies());
    }

    @PostMapping("/{id}/query")
    public ApiResponse<FundAuthResponse> queryAndSync(@PathVariable Long id) {
        return ApiResponse.ok(fundAuthService.queryAndSync(id));
    }

    @PostMapping("/{id}/capture")
    public ApiResponse<FundAuthResponse> capture(@PathVariable Long id, @Valid @RequestBody FundAuthCaptureRequest request) {
        return ApiResponse.ok(fundAuthService.capture(id, request));
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<FundAuthResponse> unfreeze(@PathVariable Long id, @Valid @RequestBody FundAuthUnfreezeRequest request) {
        return ApiResponse.ok(fundAuthService.unfreeze(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<FundAuthResponse> cancel(@PathVariable Long id, @RequestBody(required = false) FundAuthUnfreezeRequest request) {
        return ApiResponse.ok(fundAuthService.cancel(id, request == null ? null : request.remark()));
    }
}
