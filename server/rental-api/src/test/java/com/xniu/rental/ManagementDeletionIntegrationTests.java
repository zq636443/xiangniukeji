package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.SystemAccountCreateRequest;
import com.xniu.rental.auth.dto.SystemRoleUpdateRequest;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.auth.service.SystemManagementService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.dto.InvestorRequest;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.investor.service.InvestorService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ManagementDeletionIntegrationTests {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private InvestorService investorService;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
            "management-deletion-test-token",
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
                List.of("system.admin"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void assetDeletionShouldAllowUnusedIdleAssetAndRejectBusinessReferencedAsset() {
        var unused = createAsset(1L);

        assetService.deleteAsset(unused.id());

        assertThat(assetRepository.findById(unused.id())).isEmpty();

        var referenced = createAsset(1L);
        jdbcTemplate.update("""
            INSERT INTO asset_maintenance_record
            (maintenance_no, asset_id, maintenance_type, maintenance_status,
             labor_cost, external_cost, parts_cost, total_cost)
            VALUES (?, ?, 'REPAIR', 'COMPLETED', 0, 0, 0, 0)
            """, unique("MNT"), referenced.id());

        assertThatThrownBy(() -> assetService.deleteAsset(referenced.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能删除");
        assertThat(assetRepository.findById(referenced.id())).isPresent();
    }

    @Test
    void investorDeletionShouldRemoveUnusedInvestorButRejectReferencedInvestor() {
        var unused = investorService.createInvestor(investorRequest("无关联出资方"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT operation_fee_rate FROM investor WHERE id = ?",
            BigDecimal.class,
            unused.id()
        )).isEqualByComparingTo("0.0800");

        investorService.deleteInvestor(unused.id());

        assertThat(investorRepository.findById(unused.id())).isEmpty();

        var referenced = investorService.createInvestor(investorRequest("有关联出资方"));
        createAsset(referenced.id());

        assertThatThrownBy(() -> investorService.deleteInvestor(referenced.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已关联");
        assertThat(investorRepository.findById(referenced.id())).isPresent();
    }

    @Test
    void accountDeletionShouldSoftDeleteAccountAndRemoveAuthorizations() {
        var username = unique("finance").toLowerCase();
        var created = systemManagementService.createAccount(new SystemAccountCreateRequest(
            "FINANCE",
            username,
            "待删除财务账号",
            "139" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
            "Init@2026",
            null,
            null,
            null
        ));

        systemManagementService.deleteAccount(created.id());

        assertThat(accountRepository.findById(created.id())).isEmpty();
        assertThat(accountRepository.findByUsername(username)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_account_role WHERE account_id = ?",
            Integer.class,
            created.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_account WHERE id = ? AND deleted_at IS NOT NULL AND status = 'DISABLED' AND username IS NULL",
            Integer.class,
            created.id()
        )).isEqualTo(1);
    }

    @Test
    void roleManagementShouldEditPermissionsAndProtectAssignedRolesFromDeletion() {
        var roleCode = unique("TEST_ROLE");
        jdbcTemplate.update(
            "INSERT INTO auth_role (role_code, role_name, role_scope) VALUES (?, '测试角色', 'PLATFORM')",
            roleCode
        );
        var roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM auth_role WHERE role_code = ?",
            Long.class,
            roleCode
        );

        var updated = systemManagementService.updateRole(
            roleId,
            new SystemRoleUpdateRequest("已编辑测试角色", "DISABLED", List.of("order.read", "asset.read"))
        );

        assertThat(updated.roleName()).isEqualTo("已编辑测试角色");
        assertThat(updated.status()).isEqualTo("DISABLED");
        assertThat(updated.permissions()).containsExactly("asset.read", "order.read");

        jdbcTemplate.update(
            "INSERT INTO auth_account_role (account_id, role_id) VALUES (?, ?)",
            1L,
            roleId
        );
        assertThatThrownBy(() -> systemManagementService.deleteRole(roleId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已分配给账号");

        jdbcTemplate.update("DELETE FROM auth_account_role WHERE account_id = ? AND role_id = ?", 1L, roleId);
        systemManagementService.deleteRole(roleId);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_role WHERE id = ?",
            Integer.class,
            roleId
        )).isZero();
    }

    private com.xniu.rental.asset.model.AssetItem createAsset(Long investorId) {
        var type = jdbcTemplate.queryForMap("""
            SELECT id, asset_class
            FROM asset_type_definition
            WHERE status = 'ENABLED'
            ORDER BY id
            LIMIT 1
            """);
        return assetRepository.create(
            unique("A"),
            AssetType.valueOf((String) type.get("asset_class")),
            ((Number) type.get("id")).longValue(),
            unique("SERIAL"),
            investorId,
            null,
            null,
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            null,
            null
        );
    }

    private InvestorRequest investorRequest(String name) {
        return new InvestorRequest(
            name,
            "测试联系人",
            "138" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
            new BigDecimal("0.0800"),
            false,
            null,
            null,
            null,
            null
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
