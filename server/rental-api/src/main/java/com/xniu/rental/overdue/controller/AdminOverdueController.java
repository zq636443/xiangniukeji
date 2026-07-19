package com.xniu.rental.overdue.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.overdue.dto.OverdueCaseResponse;
import com.xniu.rental.overdue.dto.OverdueCollectionRequest;
import com.xniu.rental.overdue.service.OverdueService;
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
@RequestMapping("/api/admin/overdues")
public class AdminOverdueController {

    private final OverdueService overdueService;

    public AdminOverdueController(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    @GetMapping
    public ApiResponse<List<OverdueCaseResponse>> listCases(
        @RequestParam(required = false) String statMonth,
        @RequestParam(required = false) String overdueStatus,
        @RequestParam(required = false) String collectionStatus,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) Long userAccountId,
        @RequestParam(required = false) Long storeSkuId
    ) {
        return ApiResponse.ok(overdueService.listAdminCases(statMonth, overdueStatus, collectionStatus, merchantId, storeId, userAccountId, storeSkuId));
    }

    @PostMapping("/{id}/collection")
    public ApiResponse<OverdueCaseResponse> updateCollection(@PathVariable Long id, @Valid @RequestBody OverdueCollectionRequest request) {
        return ApiResponse.ok(overdueService.updateCollection(id, request.collectionStatus(), request.remark()));
    }
}
