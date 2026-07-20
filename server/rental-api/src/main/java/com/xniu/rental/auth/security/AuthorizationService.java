package com.xniu.rental.auth.security;

import com.xniu.rental.common.BusinessException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationService {

    private static final Set<String> PLATFORM_ACCOUNT_TYPES = Set.of("PLATFORM_ADMIN", "FINANCE");

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

    public void requirePlatformAccount() {
        var current = requireCurrent();
        if (!PLATFORM_ACCOUNT_TYPES.contains(current.account().accountType())) {
            throw BusinessException.forbidden("当前账号不是平台账号");
        }
    }

    public void requireConsumerAccount() {
        var current = requireCurrent();
        if (!"CONSUMER".equals(current.account().accountType())) {
            throw BusinessException.forbidden("当前账号不是消费者账号");
        }
    }

    private CurrentAccount requireCurrent() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current;
    }
}
