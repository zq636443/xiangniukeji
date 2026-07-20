package com.xniu.rental.auth.controller;

import com.xniu.rental.auth.dto.SystemAccountResponse;
import com.xniu.rental.auth.dto.SystemAccountCreateRequest;
import com.xniu.rental.auth.dto.SystemAccountPermissionUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountResetPasswordRequest;
import com.xniu.rental.auth.dto.SystemAccountRoleUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountScopeUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountUpdateRequest;
import com.xniu.rental.auth.dto.SystemPermissionResponse;
import com.xniu.rental.auth.dto.SystemRoleResponse;
import com.xniu.rental.auth.service.SystemManagementService;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.ApiResponse;
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
@RequestMapping("/api/admin/system")
public class AdminSystemManagementController {

    private final SystemManagementService systemManagementService;
    private final AuthorizationService authorizationService;

    public AdminSystemManagementController(
        SystemManagementService systemManagementService,
        AuthorizationService authorizationService
    ) {
        this.systemManagementService = systemManagementService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<SystemAccountResponse>> listAccounts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String accountType,
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false) String status
    ) {
        authorizationService.requirePermission("auth.account.read");
        return ApiResponse.ok(systemManagementService.listAccounts(keyword, accountType, merchantId, status));
    }

    @GetMapping("/accounts/{accountId}")
    public ApiResponse<SystemAccountResponse> getAccount(@PathVariable Long accountId) {
        authorizationService.requirePermission("auth.account.read");
        return ApiResponse.ok(systemManagementService.getAccount(accountId));
    }

    @PostMapping("/accounts")
    public ApiResponse<SystemAccountResponse> createAccount(@Valid @RequestBody SystemAccountCreateRequest request) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.createAccount(request));
    }

    @PutMapping("/accounts/{accountId}")
    public ApiResponse<SystemAccountResponse> updateAccount(
        @PathVariable Long accountId,
        @Valid @RequestBody SystemAccountUpdateRequest request
    ) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.updateAccount(accountId, request));
    }

    @GetMapping("/roles")
    public ApiResponse<List<SystemRoleResponse>> listRoles(@RequestParam(required = false) String status) {
        authorizationService.requirePermission("auth.role.read");
        return ApiResponse.ok(systemManagementService.listRoles(status));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SystemPermissionResponse>> listPermissions(@RequestParam(required = false) String moduleCode) {
        authorizationService.requirePermission("auth.permission.read");
        return ApiResponse.ok(systemManagementService.listPermissions(moduleCode));
    }

    @PutMapping("/accounts/{accountId}/status")
    public ApiResponse<SystemAccountResponse> updateAccountStatus(@PathVariable Long accountId, @RequestParam String status) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.updateAccountStatus(accountId, status));
    }

    @PutMapping("/accounts/{accountId}/password")
    public ApiResponse<SystemAccountResponse> resetPassword(
        @PathVariable Long accountId,
        @Valid @RequestBody SystemAccountResetPasswordRequest request
    ) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.resetPassword(accountId, request));
    }

    @PutMapping("/accounts/{accountId}/role")
    public ApiResponse<SystemAccountResponse> updateAccountRole(
        @PathVariable Long accountId,
        @Valid @RequestBody SystemAccountRoleUpdateRequest request
    ) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.updateAccountRole(accountId, request));
    }

    @PutMapping("/accounts/{accountId}/scopes")
    public ApiResponse<SystemAccountResponse> updateAccountScopes(
        @PathVariable Long accountId,
        @RequestBody SystemAccountScopeUpdateRequest request
    ) {
        authorizationService.requirePermission("auth.scope.write");
        return ApiResponse.ok(systemManagementService.updateAccountScopes(accountId, request));
    }

    @PutMapping("/accounts/{accountId}/permissions")
    public ApiResponse<SystemAccountResponse> updateAccountPermissions(
        @PathVariable Long accountId,
        @Valid @RequestBody SystemAccountPermissionUpdateRequest request
    ) {
        authorizationService.requirePermission("auth.account.write");
        return ApiResponse.ok(systemManagementService.updateAccountPermissions(accountId, request));
    }
}
