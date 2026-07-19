package com.xniu.rental.auth.security;

import com.xniu.rental.ops.service.AuditLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AuditLogInterceptor auditLogInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, AuditLogInterceptor auditLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.auditLogInterceptor = auditLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/admin/login",
                "/api/auth/merchant/login",
                "/api/auth/alipay/login",
                "/api/pay/alipay/notify",
                "/api/pay/alipay/agreement/notify",
                "/api/pay/alipay/fund-auth/notify",
                "/api/contracts/notify",
                "/api/health",
                "/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**"
            );
        registry.addInterceptor(auditLogInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/admin/login",
                "/api/auth/merchant/login",
                "/api/auth/alipay/login",
                "/api/pay/alipay/notify",
                "/api/pay/alipay/agreement/notify",
                "/api/pay/alipay/fund-auth/notify",
                "/api/contracts/notify",
                "/api/health",
                "/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**"
            );
    }
}
