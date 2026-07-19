package com.xniu.rental.settlement.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.settlement.dto.ProfitRuleRequest;
import com.xniu.rental.settlement.dto.ProfitRuleResponse;
import com.xniu.rental.settlement.dto.SettlementPreviewRequest;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.model.SettlementRuleStatus;
import com.xniu.rental.settlement.service.SettlementService;
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
@RequestMapping("/api/admin/settlement")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/rules")
    public ApiResponse<List<ProfitRuleResponse>> listRules(
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(settlementService.listRules(scope, status));
    }

    @PostMapping("/rules")
    public ApiResponse<ProfitRuleResponse> createRule(@Valid @RequestBody ProfitRuleRequest request) {
        return ApiResponse.ok(settlementService.createRule(request));
    }

    @PutMapping("/rules/{id}/status")
    public ApiResponse<ProfitRuleResponse> updateRuleStatus(@PathVariable Long id, @RequestParam SettlementRuleStatus status) {
        return ApiResponse.ok(settlementService.updateRuleStatus(id, status));
    }

    @PostMapping("/preview")
    public ApiResponse<SettlementSnapshotResponse> preview(@Valid @RequestBody SettlementPreviewRequest request) {
        return ApiResponse.ok(settlementService.preview(request));
    }

    @PostMapping("/snapshots")
    public ApiResponse<SettlementSnapshotResponse> createSnapshot(@Valid @RequestBody SnapshotCreateRequest request) {
        return ApiResponse.ok(settlementService.createSnapshot(request));
    }

    @GetMapping("/snapshots")
    public ApiResponse<List<SettlementSnapshotResponse>> listSnapshots(
        @RequestParam(required = false) String sourceType,
        @RequestParam(required = false) Long sourceId
    ) {
        return ApiResponse.ok(settlementService.listSnapshots(sourceType, sourceId));
    }
}
