package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.SystemAccountCreateRequest;
import com.xniu.rental.auth.dto.SystemAccountPermissionUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountResetPasswordRequest;
import com.xniu.rental.auth.dto.SystemAccountUpdateRequest;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.repository.AuthQueryRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.auth.service.PasswordHasher;
import com.xniu.rental.auth.service.SystemManagementService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.dto.MerchantRequest;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.service.MerchantService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class SystemManagementIntegrationTests {

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private AuthQueryRepository authQueryRepository;

    @Autowired
    private MerchantService merchantService;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
            "test-token",
            new CurrentAccountResponse(
                1L,
                "PLATFORM_ADMIN",
                "admin",
                "18800000001",
                null,
                "Platform Admin",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("system.admin", "auth.account.read", "auth.account.write", "merchant.read", "merchant.write", "store.read", "store.write"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void createUpdateAndResetPasswordShouldPersist() {
        var created = systemManagementService.createAccount(new SystemAccountCreateRequest(
            "FINANCE",
            "finance_case_01",
            "财务甲",
            "13800000001",
            "Init@2026",
            null,
            null,
            null
        ));

        var updated = systemManagementService.updateAccount(created.id(), new SystemAccountUpdateRequest(
            "finance_case_01_renamed",
            "财务经理甲",
            "13900000001"
        ));

        assertThat(updated.username()).isEqualTo("finance_case_01_renamed");
        assertThat(updated.displayName()).isEqualTo("财务经理甲");
        assertThat(updated.phone()).isEqualTo("13900000001");

        systemManagementService.resetPassword(created.id(), new SystemAccountResetPasswordRequest("Reset@2026"));

        var account = accountRepository.findById(created.id()).orElseThrow();
        assertThat(account.username()).isEqualTo("finance_case_01_renamed");
        assertThat(account.displayName()).isEqualTo("财务经理甲");
        assertThat(account.phone()).isEqualTo("13900000001");
        assertThat(passwordHasher.matches("Reset@2026", account.passwordHash())).isTrue();
    }

    @Test
    void platformAdminCanGrantAndRevokeMerchantOrderCreatePermission() {
        var merchantAccount = accountRepository.findByUsername("merchant_demo").orElseThrow();

        var granted = systemManagementService.updateAccountPermissions(
            merchantAccount.id(),
            new SystemAccountPermissionUpdateRequest(List.of("order.create"))
        );

        assertThat(granted.directPermissions()).containsExactly("order.create");
        assertThat(granted.permissions()).contains("order.create");
        assertThat(authQueryRepository.findPermissionCodes(merchantAccount.id())).contains("order.create");

        var revoked = systemManagementService.updateAccountPermissions(
            merchantAccount.id(),
            new SystemAccountPermissionUpdateRequest(List.of())
        );

        assertThat(revoked.directPermissions()).isEmpty();
        assertThat(revoked.permissions()).doesNotContain("order.create");
    }

    @Test
    void merchantAccountShouldResolveOnlyEnabledStoresFromSelectedMerchant() {
        var merchant = merchantService.createMerchant(new MerchantRequest(
            "账号关系测试商户",
            "测试联系人",
            "13800009991",
            null,
            false,
            null,
            null,
            null,
            null
        ));
        var store = merchantService.createStore(new StoreRequest(
            merchant.id(),
            "账号关系测试门店",
            "深圳市南山区关联路 1 号",
            "09:00-21:00",
            null,
            null
        ));

        var created = systemManagementService.createAccount(new SystemAccountCreateRequest(
            "STORE_MANAGER",
            "scope_case_01",
            "门店店长甲",
            "13800009992",
            "Init@2026",
            merchant.id(),
            null,
            List.of(store.id())
        ));

        assertThat(created.merchantId()).isEqualTo(merchant.id());
        assertThat(created.storeId()).isEqualTo(store.id());
        assertThat(created.storeScopes()).singleElement().satisfies(scope -> {
            assertThat(scope.merchantId()).isEqualTo(merchant.id());
            assertThat(scope.storeId()).isEqualTo(store.id());
            assertThat(scope.scopeType()).isEqualTo("SINGLE_STORE");
        });

        assertThatThrownBy(() -> systemManagementService.createAccount(new SystemAccountCreateRequest(
            "STORE_STAFF",
            "scope_case_cross_merchant",
            "跨商户员工",
            "13800009993",
            "Init@2026",
            merchant.id(),
            null,
            List.of(1L)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("门店不属于所选商户");

        merchantService.updateStoreStatus(store.id(), StoreStatus.DISABLED);
        assertThatThrownBy(() -> systemManagementService.createAccount(new SystemAccountCreateRequest(
            "STORE_STAFF",
            "scope_case_disabled_store",
            "停用门店员工",
            "13800009994",
            "Init@2026",
            merchant.id(),
            null,
            List.of(store.id())
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("停用门店");
    }
}
