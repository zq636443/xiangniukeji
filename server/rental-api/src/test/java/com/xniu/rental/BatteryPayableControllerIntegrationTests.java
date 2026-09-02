package com.xniu.rental;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.PasswordLoginRequest;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.auth.service.AuthService;
import com.xniu.rental.auth.service.PasswordHasher;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.service.ExternalOrderManualRenewalService;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BatteryPayableControllerIntegrationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalRentalOrderService externalOrderService;

    @Autowired
    private ExternalOrderManualRenewalService manualRenewalService;

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void adminAndAuthorizedMerchantCanReadSourceBackedBatteryPayable() throws Exception {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 2");
        jdbcTemplate.update("""
            UPDATE product_sku
            SET battery_cost_daily_amount = 6.80,
                battery_cost_monthly_amount = NULL
            WHERE id = 2
            """);
        var suffix = Long.toUnsignedString(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id,
             current_merchant_id, current_store_id, status, purchase_amount,
             maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'BATTERY',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'),
                    ?, 1, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE)
            """, "A-battery-payable-http-" + suffix, "BATTERY-PAYABLE-HTTP-" + suffix);
        var batteryAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-battery-payable-http-" + suffix
        );
        AuthContext.set(new CurrentAccount(
            "battery-payable-setup-token",
            new CurrentAccountResponse(
                1L, "PLATFORM_ADMIN", "admin", "18800000001", null,
                "Platform Admin", null, null, null,
                List.of("PLATFORM_ADMIN"), List.of("system.admin"), List.of()
            )
        ));
        var initialStart = LocalDateTime.of(2099, 12, 1, 10, 0);
        var order = externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE", "BATTERY-PAYABLE-HTTP-" + suffix, 2L, 4L,
            "电池应付接口客户", "13800136666", initialStart,
            initialStart.plusDays(20).plusHours(12), null, batteryAssetId,
            new BigDecimal("399.00"), new BigDecimal("399.00"),
            BigDecimal.ZERO, BigDecimal.ZERO, null
        ));
        jdbcTemplate.update(
            "UPDATE external_rental_order SET created_at = '2099-12-05 09:00:00' WHERE id = ?",
            order.id()
        );
        manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            order.expectedReturnAt(),
            order.expectedReturnAt().plusDays(7),
            new BigDecimal("300.00"),
            "七天续租接口验证"
        ));
        AuthContext.clear();

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM settlement_statement WHERE statement_month = '2099-12'",
            Integer.class
        )).isZero();
        var admin = authService.workspaceLogin(new PasswordLoginRequest("admin", "123456"));

        mockMvc.perform(get("/api/admin/settlement/statements/battery-payable")
                .header("Authorization", "Bearer " + admin.token())
                .param("month", "2099-12")
                .param("storeId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.statementMonth").value("2099-12"))
            .andExpect(jsonPath("$.data.storeId").value(1))
            .andExpect(jsonPath("$.data.initialAmount").value(139.4))
            .andExpect(jsonPath("$.data.renewalAmount").value(47.6))
            .andExpect(jsonPath("$.data.billAmount").value(0.0))
            .andExpect(jsonPath("$.data.totalAmount").value(187.0))
            .andExpect(jsonPath("$.data.initialCount").value(1))
            .andExpect(jsonPath("$.data.renewalCount").value(1))
            .andExpect(jsonPath("$.data.billCount").value(0));

        mockMvc.perform(get("/api/admin/settlement/statements/battery-payable")
                .header("Authorization", "Bearer " + admin.token())
                .param("month", "2099-12")
                .param("storeId", "999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value(0.0))
            .andExpect(jsonPath("$.data.initialCount").value(0))
            .andExpect(jsonPath("$.data.renewalCount").value(0));

        var password = "battery-payable-store-manager";
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
        var merchant = authService.workspaceLogin(new PasswordLoginRequest("store_demo", password));

        mockMvc.perform(get("/api/merchant/settlement/statements/battery-payable")
                .header("Authorization", "Bearer " + merchant.token())
                .param("month", "2099-12")
                .param("storeId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.statementMonth").value("2099-12"))
            .andExpect(jsonPath("$.data.storeId").value(1))
            .andExpect(jsonPath("$.data.initialAmount").value(139.4))
            .andExpect(jsonPath("$.data.renewalAmount").value(47.6))
            .andExpect(jsonPath("$.data.totalAmount").value(187.0));

        mockMvc.perform(get("/api/merchant/settlement/statements/battery-payable")
                .header("Authorization", "Bearer " + merchant.token())
                .param("month", "2099-12")
                .param("storeId", "999"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("没有该门店权限"));
    }
}
