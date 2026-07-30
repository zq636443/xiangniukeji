package com.xniu.rental.contract.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.contract.dto.ContractArchiveRequest;
import com.xniu.rental.contract.dto.ContractGenerateRequest;
import com.xniu.rental.contract.dto.ContractNotifyResponse;
import com.xniu.rental.contract.dto.PricingAmendmentGenerateRequest;
import com.xniu.rental.contract.dto.ContractResponse;
import com.xniu.rental.contract.dto.ContractSignRequest;
import com.xniu.rental.contract.dto.ContractTemplateRequest;
import com.xniu.rental.contract.dto.ContractTemplateResponse;
import com.xniu.rental.contract.model.ContractTemplateStatus;
import com.xniu.rental.contract.service.ContractService;
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
@RequestMapping("/api/admin/contracts")
public class AdminContractController {

    private final ContractService contractService;

    public AdminContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/templates")
    public ApiResponse<List<ContractTemplateResponse>> listTemplates(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(contractService.listTemplates(type, status));
    }

    @PostMapping("/templates")
    public ApiResponse<ContractTemplateResponse> createTemplate(@Valid @RequestBody ContractTemplateRequest request) {
        return ApiResponse.ok(contractService.createTemplate(request));
    }

    @PutMapping("/templates/{id}/status")
    public ApiResponse<ContractTemplateResponse> updateTemplateStatus(@PathVariable Long id, @RequestParam ContractTemplateStatus status) {
        return ApiResponse.ok(contractService.updateTemplateStatus(id, status));
    }

    @GetMapping
    public ApiResponse<List<ContractResponse>> listContracts(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long userAccountId
    ) {
        return ApiResponse.ok(contractService.listAdminContracts(status, orderId, userAccountId));
    }

    @GetMapping("/notifies")
    public ApiResponse<List<ContractNotifyResponse>> listNotifies() {
        return ApiResponse.ok(contractService.listNotifies());
    }

    @PostMapping("/generate")
    public ApiResponse<ContractResponse> generate(@Valid @RequestBody ContractGenerateRequest request) {
        return ApiResponse.ok(contractService.generateContract(request));
    }

    @PostMapping("/generate-pricing-amendment")
    public ApiResponse<ContractResponse> generatePricingAmendment(@Valid @RequestBody PricingAmendmentGenerateRequest request) {
        return ApiResponse.ok(contractService.generatePricingAmendment(request));
    }

    @PostMapping("/{id}/start-sign")
    public ApiResponse<ContractResponse> startSign(@PathVariable Long id, @RequestBody ContractSignRequest request) {
        return ApiResponse.ok(contractService.startSign(id, request));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<ContractResponse> archive(@PathVariable Long id, @Valid @RequestBody ContractArchiveRequest request) {
        return ApiResponse.ok(contractService.archive(id, request));
    }
}
