package com.xniu.rental.auth.controller;

import com.xniu.rental.auth.dto.AlipayLoginRequest;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.LoginResponse;
import com.xniu.rental.auth.dto.PasswordLoginRequest;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.service.AuthService;
import com.xniu.rental.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/admin/login")
    public ApiResponse<LoginResponse> adminLogin(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.adminLogin(request));
    }

    @PostMapping("/merchant/login")
    public ApiResponse<LoginResponse> merchantLogin(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.merchantLogin(request));
    }

    @PostMapping("/workspace/login")
    public ApiResponse<LoginResponse> workspaceLogin(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.workspaceLogin(request));
    }

    @PostMapping("/alipay/login")
    public ApiResponse<LoginResponse> alipayLogin(@Valid @RequestBody AlipayLoginRequest request) {
        return ApiResponse.ok(authService.alipayLogin(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentAccountResponse> me() {
        return ApiResponse.ok(authService.current(AuthContext.get()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh() {
        return ApiResponse.ok(authService.refresh(AuthContext.get()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout(AuthContext.get());
        return ApiResponse.ok(null);
    }
}
