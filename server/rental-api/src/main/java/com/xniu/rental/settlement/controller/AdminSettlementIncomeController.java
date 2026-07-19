package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.settlement.dto.SettlementEntryGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementIncomeEntryResponse;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settlement/income")
public class AdminSettlementIncomeController {

    private final SettlementIncomeService settlementIncomeService;

    public AdminSettlementIncomeController(SettlementIncomeService settlementIncomeService) {
        this.settlementIncomeService = settlementIncomeService;
    }

    @GetMapping("/entries")
    public ApiResponse<List<SettlementIncomeEntryResponse>> list(
        @RequestParam(required = false) String beneficiaryType,
        @RequestParam(required = false) Long beneficiaryId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(settlementIncomeService.listAdmin(beneficiaryType, beneficiaryId, status, orderId, storeId));
    }

    @PostMapping("/orders/{orderId}/generate")
    public ApiResponse<SettlementEntryGenerateResponse> generate(@PathVariable Long orderId) {
        return ApiResponse.ok(settlementIncomeService.generateForOrder(orderId));
    }

    @PutMapping("/entries/{id}/status")
    public ApiResponse<SettlementIncomeEntryResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.ok(settlementIncomeService.updateStatus(id, status));
    }
}
