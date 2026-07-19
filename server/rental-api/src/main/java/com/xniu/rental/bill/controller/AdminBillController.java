package com.xniu.rental.bill.controller;

import com.xniu.rental.bill.dto.BillBatchResponse;
import com.xniu.rental.bill.dto.BillCancelRequest;
import com.xniu.rental.bill.dto.BillGenerateRequest;
import com.xniu.rental.bill.dto.BillGenerationResultResponse;
import com.xniu.rental.bill.dto.BillPlanGenerateRequest;
import com.xniu.rental.bill.dto.BillResponse;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.ApiResponse;
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
@RequestMapping("/api/admin/bills")
public class AdminBillController {

    private final BillService billService;

    public AdminBillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping
    public ApiResponse<List<BillResponse>> listBills(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(billService.listBills(status, orderId, storeId));
    }

    @GetMapping("/batches")
    public ApiResponse<List<BillBatchResponse>> listBatches() {
        return ApiResponse.ok(billService.listBatches());
    }

    @PostMapping("/generate")
    public ApiResponse<BillGenerationResultResponse> generate(@Valid @RequestBody BillGenerateRequest request) {
        return ApiResponse.ok(billService.generate(request));
    }

    @PostMapping("/generate-plan")
    public ApiResponse<BillGenerationResultResponse> generatePlan(@Valid @RequestBody BillPlanGenerateRequest request) {
        return ApiResponse.ok(billService.generatePlan(request.orderId(), request.remark()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BillResponse> cancel(@PathVariable Long id, @RequestBody BillCancelRequest request) {
        return ApiResponse.ok(billService.cancelBill(id, request == null ? null : request.remark()));
    }
}
