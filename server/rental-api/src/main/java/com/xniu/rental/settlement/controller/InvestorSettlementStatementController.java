package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
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
@RequestMapping("/api/investor/settlement/statements")
public class InvestorSettlementStatementController {

    private final SettlementStatementService settlementStatementService;

    public InvestorSettlementStatementController(SettlementStatementService settlementStatementService) {
        this.settlementStatementService = settlementStatementService;
    }

    @GetMapping
    public ApiResponse<List<SettlementStatementResponse>> list(
        @RequestParam(required = false) String month,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(settlementStatementService.listInvestor(month, status));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<SettlementStatementLineResponse>> lines(@PathVariable Long id) {
        return ApiResponse.ok(settlementStatementService.listInvestorLines(id));
    }
}
