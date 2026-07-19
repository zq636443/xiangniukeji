package com.xniu.rental.auth.security;

import com.xniu.rental.common.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationService {

    public void requirePermission(String permission) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (!current.hasPermission(permission) && !current.hasPermission("system.admin")) {
            throw BusinessException.forbidden("没有操作权限");
        }
    }

    public void requireStoreAccess(Long merchantId, Long storeId) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (current.hasPermission("system.admin")) {
            return;
        }
        var matched = current.account().storeScopes().stream().anyMatch(scope -> {
            if (!scope.merchantId().equals(merchantId)) {
                return false;
            }
            return "ALL_MERCHANT_STORES".equals(scope.scopeType())
                || (storeId != null && storeId.equals(scope.storeId()));
        });
        if (!matched) {
            throw BusinessException.forbidden("没有该门店权限");
        }
    }
}
