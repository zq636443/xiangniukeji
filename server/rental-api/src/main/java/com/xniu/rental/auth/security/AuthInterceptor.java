package com.xniu.rental.auth.security;

import com.xniu.rental.auth.service.AuthService;
import com.xniu.rental.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> PLATFORM_ACCOUNT_TYPES = Set.of("PLATFORM_ADMIN", "FINANCE");

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var token = resolveToken(request);
        if (token == null || token.isBlank()) {
            throw BusinessException.unauthorized("请先登录");
        }
        var current = authService.authenticate(token);
        if (request.getRequestURI().startsWith("/api/admin/")
            && !PLATFORM_ACCOUNT_TYPES.contains(current.account().accountType())) {
            throw BusinessException.forbidden("当前账号不能访问总部管理接口");
        }
        AuthContext.set(current);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        var authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return request.getHeader("X-Auth-Token");
    }
}
