package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;

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
                List.of("system.admin", "auth.account.read", "auth.account.write"),
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
}
