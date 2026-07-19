package com.xniu.rental.ops.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.ops.dto.AuditLogResponse;
import com.xniu.rental.ops.dto.ExportTaskRequest;
import com.xniu.rental.ops.dto.ExportTaskResponse;
import com.xniu.rental.ops.dto.ReconciliationBatchResponse;
import com.xniu.rental.ops.dto.ReconciliationRequest;
import com.xniu.rental.ops.service.OpsService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops")
public class AdminOpsController {

    private final OpsService opsService;

    public AdminOpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/audits")
    public ApiResponse<List<AuditLogResponse>> listAudits(
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) String uri
    ) {
        return ApiResponse.ok(opsService.listAudits(accountId, uri));
    }

    @GetMapping("/exports")
    public ApiResponse<List<ExportTaskResponse>> listExports(@RequestParam(required = false) String exportType) {
        return ApiResponse.ok(opsService.listExports(exportType));
    }

    @PostMapping("/exports")
    public ApiResponse<ExportTaskResponse> createExport(@Valid @RequestBody ExportTaskRequest request) {
        return ApiResponse.ok(opsService.createExport(request));
    }

    @GetMapping("/reconciliations")
    public ApiResponse<List<ReconciliationBatchResponse>> listReconciliations(@RequestParam(required = false) LocalDate billDate) {
        return ApiResponse.ok(opsService.listReconciliations(billDate));
    }

    @PostMapping("/reconciliations")
    public ApiResponse<ReconciliationBatchResponse> createReconciliation(@Valid @RequestBody ReconciliationRequest request) {
        return ApiResponse.ok(opsService.createReconciliation(request));
    }
}
