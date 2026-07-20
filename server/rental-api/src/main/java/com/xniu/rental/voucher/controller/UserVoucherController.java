package com.xniu.rental.voucher.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.voucher.dto.VoucherPrepareRequest;
import com.xniu.rental.voucher.dto.VoucherResponse;
import com.xniu.rental.voucher.dto.VoucherVerificationAmountRequest;
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
@RequestMapping("/api/user/vouchers")
public class UserVoucherController {

    private final VoucherService voucherService;

    public UserVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ApiResponse<List<VoucherResponse>> listMine(
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(voucherService.listMine(platform, status));
    }

    @PostMapping("/prepare")
    public ApiResponse<VoucherResponse> prepare(@Valid @RequestBody VoucherPrepareRequest request) {
        return ApiResponse.ok(voucherService.prepare(request));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<VoucherResponse> verify(@PathVariable Long id) {
        return ApiResponse.ok(voucherService.verify(id));
    }

    @PostMapping("/{id}/verification-amount")
    public ApiResponse<VoucherResponse> updateVerificationAmount(
        @PathVariable Long id,
        @Valid @RequestBody VoucherVerificationAmountRequest request
    ) {
        return ApiResponse.ok(voucherService.updateMineVerificationAmount(id, request));
    }

    @PostMapping("/{id}/consume")
    public ApiResponse<VoucherResponse> consume(@PathVariable Long id) {
        return ApiResponse.ok(voucherService.consume(id));
    }
}
