package com.xniu.rental.ops.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private final OpsService opsService;

    public AuditLogInterceptor(OpsService opsService) {
        this.opsService = opsService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        opsService.audit(request, response.getStatus(), exception);
    }
}
