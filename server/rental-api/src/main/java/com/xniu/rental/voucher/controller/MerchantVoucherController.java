package com.xniu.rental.voucher.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.voucher.dto.VoucherExceptionRequest;
import com.xniu.rental.voucher.dto.VoucherResponse;
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
@RequestMapping("/api/merchant/vouchers")
public class MerchantVoucherController {

    private final VoucherService voucherService;

    public MerchantVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ApiResponse<List<VoucherResponse>> list(
        @RequestParam Long storeId,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(voucherService.listMerchant(storeId, platform, status));
    }

    @PostMapping("/{id}/exception")
    public ApiResponse<VoucherResponse> markException(@PathVariable Long id, @Valid @RequestBody VoucherExceptionRequest request) {
        return ApiResponse.ok(voucherService.markException(id, request.reason()));
    }
}
