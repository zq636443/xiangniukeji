package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xniu.rental.auth.dto.PasswordLoginRequest;
import com.xniu.rental.auth.service.AuthService;
import com.xniu.rental.auth.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthWorkspaceLoginIntegrationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformAdministratorCanLoginThroughUnifiedWorkspaceEntry() {
        var login = authService.workspaceLogin(new PasswordLoginRequest("admin", "123456"));

        assertThat(login.token()).isNotBlank();
        assertThat(login.account().accountType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(login.account().permissions()).contains("system.admin");
    }

    @Test
    void merchantCanStillLoginThroughUnifiedWorkspaceEntry() {
        var password = "merchant-workspace-test";
        jdbcTemplate.update(
            "UPDATE sys_account SET password_hash = ? WHERE username = ?",
            passwordHasher.encode(password),
            "merchant_demo"
        );

        var login = authService.workspaceLogin(new PasswordLoginRequest("merchant_demo", password));

        assertThat(login.token()).isNotBlank();
        assertThat(login.account().accountType()).isEqualTo("MERCHANT_OWNER");
        assertThat(login.account().merchantId()).isEqualTo(1L);
    }

    @Test
    void storeManagerCanReadSettlementWhileOtherStoreRolesCannot() {
        var storeRolesWithSettlementRead = jdbcTemplate.queryForList("""
            SELECT r.role_code
            FROM auth_role r
            JOIN auth_role_permission rp ON rp.role_id = r.id
            JOIN auth_permission p ON p.id = rp.permission_id
            WHERE r.role_code IN (
                'STORE_MANAGER',
                'STORE_OPERATOR',
                'STORE_STAFF',
                'MAINTENANCE_STAFF',
                'WAREHOUSE_STAFF'
            )
              AND p.permission_code = 'settlement.read'
            ORDER BY r.role_code
            """, String.class);
        assertThat(storeRolesWithSettlementRead).containsExactly("STORE_MANAGER");

        var password = "store-manager-workspace-test";
        var accountId = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_account WHERE username = ?",
            Long.class,
            "store_demo"
        );
        jdbcTemplate.update(
            "UPDATE sys_account SET account_type = 'STORE_MANAGER', password_hash = ? WHERE id = ?",
            passwordHasher.encode(password),
            accountId
        );
        jdbcTemplate.update("DELETE FROM auth_account_role WHERE account_id = ?", accountId);
        jdbcTemplate.update("""
            INSERT INTO auth_account_role (account_id, role_id)
            SELECT ?, id FROM auth_role WHERE role_code = 'STORE_MANAGER'
            """, accountId);

        var login = authService.workspaceLogin(new PasswordLoginRequest("store_demo", password));

        assertThat(login.account().accountType()).isEqualTo("STORE_MANAGER");
        assertThat(login.account().roles()).contains("STORE_MANAGER");
        assertThat(login.account().permissions()).contains("settlement.read");
    }

    @Test
    void unifiedWorkspaceEndpointAllowsAnonymousAdministratorLogin() throws Exception {
        mockMvc.perform(post("/api/auth/workspace/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"123456"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.account.accountType").value("PLATFORM_ADMIN"));
    }
}
