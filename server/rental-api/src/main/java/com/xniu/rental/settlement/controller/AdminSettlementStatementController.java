package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.settlement.dto.BatteryPayableResponse;
import com.xniu.rental.settlement.dto.SettlementOverviewResponse;
import com.xniu.rental.settlement.dto.SettlementStatementGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementStatementLineResponse;
import com.xniu.rental.settlement.dto.SettlementStatementResponse;
import com.xniu.rental.settlement.dto.StoreProfitOverviewResponse;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settlement/statements")
public class AdminSettlementStatementController {

    private final SettlementStatementService settlementStatementService;

    public AdminSettlementStatementController(SettlementStatementService settlementStatementService) {
        this.settlementStatementService = settlementStatementService;
    }

    @GetMapping("/overview")
    public ApiResponse<SettlementOverviewResponse> overview(@RequestParam(required = false) String month) {
        return ApiResponse.ok(settlementStatementService.overview(month));
    }

    @GetMapping("/battery-payable")
    public ApiResponse<BatteryPayableResponse> batteryPayable(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementStatementService.adminBatteryPayable(month, storeId));
    }

    @PostMapping("/generate")
    public ApiResponse<SettlementStatementGenerateResponse> generate(@RequestParam String month) {
        return ApiResponse.ok(settlementStatementService.generateMonth(month));
    }

    @GetMapping("/store-profit-overview")
    public ApiResponse<List<StoreProfitOverviewResponse>> storeProfitOverview(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementStatementService.listStoreProfitOverview(month, merchantId, storeId));
    }

    @GetMapping
    public ApiResponse<List<SettlementStatementResponse>> list(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) String beneficiaryType,
        @RequestParam(required = false) Long beneficiaryId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementStatementService.listAdmin(month, beneficiaryType, beneficiaryId, status, merchantId, storeId));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<SettlementStatementLineResponse>> lines(@PathVariable Long id) {
        return ApiResponse.ok(settlementStatementService.listAdminLines(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<SettlementStatementResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.ok(settlementStatementService.updateStatus(id, status));
    }
}
