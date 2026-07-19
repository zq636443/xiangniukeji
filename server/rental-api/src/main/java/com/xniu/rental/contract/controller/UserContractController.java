package com.xniu.rental.contract.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.contract.dto.ContractResponse;
import com.xniu.rental.contract.service.ContractService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/contracts")
public class UserContractController {

    private final ContractService contractService;

    public UserContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public ApiResponse<List<ContractResponse>> listMine(@RequestParam(required = false) Long orderId) {
        return ApiResponse.ok(contractService.listUserContracts(orderId));
    }

    @PostMapping("/{id}/confirm-signed")
    public ApiResponse<ContractResponse> confirmSigned(@PathVariable Long id) {
        return ApiResponse.ok(contractService.userConfirmSigned(id));
    }
}
