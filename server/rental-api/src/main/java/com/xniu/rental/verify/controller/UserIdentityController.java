package com.xniu.rental.verify.controller;

import com.xniu.rental.common.ApiResponse;
import com.xniu.rental.verify.dto.IdentityImageRequest;
import com.xniu.rental.verify.dto.IdentityVerificationResponse;
import com.xniu.rental.verify.dto.RealNameConfirmRequest;
import com.xniu.rental.verify.service.IdentityService;
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
@RequestMapping("/api/user/identities")
public class UserIdentityController {

    private final IdentityService identityService;

    public UserIdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ApiResponse<List<IdentityVerificationResponse>> listMine(@RequestParam(required = false) Long orderId) {
        return ApiResponse.ok(identityService.listMine(orderId));
    }

    @PostMapping("/images")
    public ApiResponse<IdentityVerificationResponse> uploadImages(@Valid @RequestBody IdentityImageRequest request) {
        return ApiResponse.ok(identityService.uploadImages(request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<IdentityVerificationResponse> confirm(@PathVariable Long id, @Valid @RequestBody RealNameConfirmRequest request) {
        return ApiResponse.ok(identityService.confirmRealName(id, request));
    }
}
