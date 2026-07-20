package com.xniu.rental.voucher.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.voucher.dto.VoucherExceptionRequest;
import com.xniu.rental.voucher.dto.VoucherResponse;
import com.xniu.rental.voucher.dto.VoucherVerificationAmountRequest;
import com.xniu.rental.voucher.dto.XianyuVoucherIssueRequest;
import com.xniu.rental.voucher.service.VoucherService;
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
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {

    private final VoucherService voucherService;

    public AdminVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ApiResponse<List<VoucherResponse>> list(
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long userAccountId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(voucherService.listAdmin(platform, status, userAccountId, storeId));
    }

    @PostMapping("/xianyu-codes")
    public ApiResponse<VoucherResponse> issueXianyuCode(@Valid @RequestBody XianyuVoucherIssueRequest request) {
        return ApiResponse.ok(voucherService.issueXianyuCode(request));
    }

    @PostMapping("/{id}/exception")
    public ApiResponse<VoucherResponse> markException(@PathVariable Long id, @Valid @RequestBody VoucherExceptionRequest request) {
        return ApiResponse.ok(voucherService.markException(id, request.reason()));
    }

    @PostMapping("/{id}/verification-amount")
    public ApiResponse<VoucherResponse> updateVerificationAmount(
        @PathVariable Long id,
        @Valid @RequestBody VoucherVerificationAmountRequest request
    ) {
        return ApiResponse.ok(voucherService.updateAdminVerificationAmount(id, request));
    }
}
