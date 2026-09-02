package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.settlement.dto.BatteryPayableResponse;
import com.xniu.rental.settlement.dto.SettlementStatementLineResponse;
import com.xniu.rental.settlement.dto.SettlementStatementResponse;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/settlement/statements")
public class MerchantSettlementStatementController {

    private final SettlementStatementService settlementStatementService;

    public MerchantSettlementStatementController(SettlementStatementService settlementStatementService) {
        this.settlementStatementService = settlementStatementService;
    }

    @GetMapping("/battery-payable")
    public ApiResponse<BatteryPayableResponse> batteryPayable(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementStatementService.merchantBatteryPayable(month, storeId));
    }

    @GetMapping
    public ApiResponse<List<SettlementStatementResponse>> list(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementStatementService.listMerchant(month, status, storeId));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<SettlementStatementLineResponse>> lines(@PathVariable Long id) {
        return ApiResponse.ok(settlementStatementService.listMerchantLines(id));
    }
}
