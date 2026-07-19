package com.xniu.rental.investor.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.investor.dto.InvestorRequest;
import com.xniu.rental.investor.dto.InvestorResponse;
import com.xniu.rental.investor.model.InvestorStatus;
import com.xniu.rental.investor.service.InvestorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/investors")
public class AdminInvestorController {

    private final InvestorService investorService;

    public AdminInvestorController(InvestorService investorService) {
        this.investorService = investorService;
    }

    @GetMapping
    public ApiResponse<List<InvestorResponse>> listInvestors(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(investorService.listInvestors(keyword));
    }

    @PostMapping
    public ApiResponse<InvestorResponse> createInvestor(@Valid @RequestBody InvestorRequest request) {
        return ApiResponse.ok(investorService.createInvestor(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<InvestorResponse> updateInvestor(@PathVariable Long id, @Valid @RequestBody InvestorRequest request) {
        return ApiResponse.ok(investorService.updateInvestor(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<InvestorResponse> updateInvestorStatus(@PathVariable Long id, @RequestParam InvestorStatus status) {
        return ApiResponse.ok(investorService.updateInvestorStatus(id, status));
    }
}
