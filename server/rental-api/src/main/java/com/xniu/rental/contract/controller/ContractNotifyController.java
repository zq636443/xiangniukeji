package com.xniu.rental.contract.controller;

import com.xniu.rental.contract.service.ContractService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractNotifyController {

    private final ContractService contractService;

    public ContractNotifyController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        return contractService.handleNotify(params) ? "success" : "failure";
    }
}
