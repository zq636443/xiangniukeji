package com.xniu.rental.pay.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.pay.dto.AgreementNotifyResponse;
import com.xniu.rental.pay.dto.AgreementResponse;
import com.xniu.rental.pay.dto.DeductBatchResponse;
import com.xniu.rental.pay.dto.DeductRecordResponse;
import com.xniu.rental.pay.dto.DeductRunRequest;
import com.xniu.rental.pay.service.AgreementDeductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAgreementDeductController {

    private final AgreementDeductService agreementDeductService;

    public AdminAgreementDeductController(AgreementDeductService agreementDeductService) {
        this.agreementDeductService = agreementDeductService;
    }

    @GetMapping("/agreements")
    public ApiResponse<List<AgreementResponse>> listAgreements(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long userAccountId,
        @RequestParam(required = false) Long orderId
    ) {
        return ApiResponse.ok(agreementDeductService.listAgreements(status, userAccountId, orderId));
    }

    @PostMapping("/agreements/{id}/query")
    public ApiResponse<AgreementResponse> queryAgreement(@PathVariable Long id) {
        return ApiResponse.ok(agreementDeductService.queryAgreement(id));
    }

    @PostMapping("/agreements/{id}/unsign")
    public ApiResponse<AgreementResponse> unsignAgreement(@PathVariable Long id) {
        return ApiResponse.ok(agreementDeductService.unsignAgreement(id));
    }

    @GetMapping("/agreements/notifies")
    public ApiResponse<List<AgreementNotifyResponse>> listAgreementNotifies() {
        return ApiResponse.ok(agreementDeductService.listAgreementNotifies());
    }

    @PostMapping("/deductions/run")
    public ApiResponse<DeductBatchResponse> runDeduct(@RequestBody(required = false) DeductRunRequest request) {
        return ApiResponse.ok(agreementDeductService.runDueDeduct(
            request == null ? null : request.limit(),
            request == null ? null : request.remark()
        ));
    }

    @GetMapping("/deductions/batches")
    public ApiResponse<List<DeductBatchResponse>> listDeductBatches() {
        return ApiResponse.ok(agreementDeductService.listDeductBatches());
    }

    @GetMapping("/deductions/records")
    public ApiResponse<List<DeductRecordResponse>> listDeductRecords(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long billId,
        @RequestParam(required = false) Long orderId
    ) {
        return ApiResponse.ok(agreementDeductService.listDeductRecords(status, billId, orderId));
    }
}
