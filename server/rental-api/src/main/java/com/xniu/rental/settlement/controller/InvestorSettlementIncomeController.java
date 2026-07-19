package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.settlement.dto.SettlementIncomeEntryResponse;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investor/settlement/income")
public class InvestorSettlementIncomeController {

    private final SettlementIncomeService settlementIncomeService;

    public InvestorSettlementIncomeController(SettlementIncomeService settlementIncomeService) {
        this.settlementIncomeService = settlementIncomeService;
    }

    @GetMapping("/entries")
    public ApiResponse<List<SettlementIncomeEntryResponse>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(settlementIncomeService.listInvestor(status));
    }
}
