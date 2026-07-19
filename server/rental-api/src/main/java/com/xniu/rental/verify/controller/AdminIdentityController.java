package com.xniu.rental.verify.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.verify.dto.IdentityVerificationResponse;
import com.xniu.rental.verify.service.IdentityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/identities")
public class AdminIdentityController {

    private final IdentityService identityService;

    public AdminIdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ApiResponse<List<IdentityVerificationResponse>> list(
        @RequestParam(required = false) Long userAccountId,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(identityService.listAdmin(userAccountId, orderId, status));
    }
}
