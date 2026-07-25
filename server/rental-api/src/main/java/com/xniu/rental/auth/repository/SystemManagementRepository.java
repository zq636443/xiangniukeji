package com.xniu.rental.auth.repository;

import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SystemManagementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AccountRow> accountMapper = new AccountRowMapper();
    private final RowMapper<RoleRow> roleMapper = new RoleRowMapper();
    private final RowMapper<PermissionRow> permissionMapper = new PermissionRowMapper();

    public SystemManagementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccountRow> listAccounts(String keyword, AccountType accountType, Long merchantId, AccountStatus status) {
        var sql = new StringBuilder("""
            SELECT a.*,
                   m.merchant_name,
                   s.store_name,
                   i.investor_name
            FROM sys_account a
            LEFT JOIN merchant m ON m.id = a.merchant_id
            LEFT JOIN merchant_store s ON s.id = a.store_id
            LEFT JOIN investor i ON i.id = a.investor_id
            WHERE a.deleted_at IS NULL
            """);
        var params = new ArrayList<Object>();
        if (accountType != null) {
            sql.append(" AND a.account_type = ?");
            params.add(accountType.name());
        }
        if (merchantId != null) {
            sql.append(" AND a.merchant_id = ?");
            params.add(merchantId);
        }
        if (status != null) {
            sql.append(" AND a.status = ?");
            params.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (a.username LIKE ? OR a.display_name LIKE ? OR a.phone LIKE ?)");
            var like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY a.id DESC");
        return jdbcTemplate.query(sql.toString(), accountMapper, params.toArray());
    }

    public Optional<AccountRow> findAccount(Long accountId) {
        return jdbcTemplate.query("""
            SELECT a.*,
                   m.merchant_name,
                   s.store_name,
                   i.investor_name
            FROM sys_account a
            LEFT JOIN merchant m ON m.id = a.merchant_id
            LEFT JOIN merchant_store s ON s.id = a.store_id
            LEFT JOIN investor i ON i.id = a.investor_id
            WHERE a.id = ? AND a.deleted_at IS NULL
            """, accountMapper, accountId).stream().findFirst();
    }

    public List<RoleRow> listRoles(String status) {
        var sql = new StringBuilder("SELECT * FROM auth_role WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY id");
        return jdbcTemplate.query(sql.toString(), roleMapper, params.toArray());
    }

    public Optional<RoleRow> findRoleByCode(String roleCode) {
        return jdbcTemplate.query("SELECT * FROM auth_role WHERE role_code = ?", roleMapper, roleCode).stream().findFirst();
    }

    public Optional<RoleRow> findRoleById(Long roleId) {
        return jdbcTemplate.query("SELECT * FROM auth_role WHERE id = ?", roleMapper, roleId).stream().findFirst();
    }

    public List<PermissionRow> listPermissions(String moduleCode) {
        var sql = new StringBuilder("SELECT * FROM auth_permission WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (moduleCode != null && !moduleCode.isBlank()) {
            sql.append(" AND module_code = ?");
            params.add(moduleCode);
        }
        sql.append(" ORDER BY module_code, permission_code");
        return jdbcTemplate.query(sql.toString(), permissionMapper, params.toArray());
    }

    public List<String> findPermissionCodesByRole(Long roleId) {
        return jdbcTemplate.queryForList("""
            SELECT p.permission_code
            FROM auth_role_permission rp
            JOIN auth_permission p ON p.id = rp.permission_id
            WHERE rp.role_id = ?
            ORDER BY p.permission_code
            """, String.class, roleId);
    }

    public List<String> findDirectPermissionCodes(Long accountId) {
        return jdbcTemplate.queryForList("""
            SELECT p.permission_code
            FROM auth_account_permission ap
            JOIN auth_permission p ON p.id = ap.permission_id
            WHERE ap.account_id = ?
            ORDER BY p.permission_code
            """, String.class, accountId);
    }

    public void replaceDirectPermissions(Long accountId, List<String> permissionCodes) {
        jdbcTemplate.update("DELETE FROM auth_account_permission WHERE account_id = ?", accountId);
        for (var permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                INSERT INTO auth_account_permission (account_id, permission_id)
                SELECT ?, id FROM auth_permission WHERE permission_code = ?
                """, accountId, permissionCode);
        }
    }

    public int countPermissions(List<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            return 0;
        }
        var placeholders = String.join(",", java.util.Collections.nCopies(permissionCodes.size(), "?"));
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_permission WHERE permission_code IN (" + placeholders + ")",
            Integer.class,
            permissionCodes.toArray()
        );
    }

    public RoleRow updateRole(Long roleId, String roleName, String status) {
        jdbcTemplate.update("UPDATE auth_role SET role_name = ?, status = ? WHERE id = ?", roleName, status, roleId);
        return findRoleById(roleId).orElseThrow();
    }

    public void replaceRolePermissions(Long roleId, List<String> permissionCodes) {
        jdbcTemplate.update("DELETE FROM auth_role_permission WHERE role_id = ?", roleId);
        for (var permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                INSERT INTO auth_role_permission (role_id, permission_id)
                SELECT ?, id FROM auth_permission WHERE permission_code = ?
                """, roleId, permissionCode);
        }
    }

    public int countAccountsByRole(Long roleId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM auth_account_role ar
            JOIN sys_account a ON a.id = ar.account_id
            WHERE ar.role_id = ? AND a.deleted_at IS NULL
            """, Integer.class, roleId);
        return count == null ? 0 : count;
    }

    public int countEnabledAccountsByRoleCode(String roleCode) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM auth_account_role ar
            JOIN auth_role r ON r.id = ar.role_id
            JOIN sys_account a ON a.id = ar.account_id
            WHERE r.role_code = ? AND a.status = 'ENABLED' AND a.deleted_at IS NULL
            """, Integer.class, roleCode);
        return count == null ? 0 : count;
    }

    public void deleteRole(Long roleId) {
        jdbcTemplate.update("DELETE FROM auth_role_permission WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM auth_role WHERE id = ?", roleId);
    }

    public void softDeleteAccount(Long accountId) {
        jdbcTemplate.update("DELETE FROM auth_account_permission WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM auth_account_store_scope WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM auth_account_role WHERE account_id = ?", accountId);
        jdbcTemplate.update("""
            UPDATE auth_session
            SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
            WHERE account_id = ?
            """, accountId);
        jdbcTemplate.update("""
            UPDATE sys_account
            SET username = NULL,
                phone = NULL,
                alipay_user_id = NULL,
                display_name = CONCAT('已删除账号#', id),
                password_hash = NULL,
                merchant_id = NULL,
                store_id = NULL,
                investor_id = NULL,
                status = 'DISABLED',
                deleted_at = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted_at IS NULL
            """, accountId);
    }

    public void replaceAccountRole(Long accountId, String roleCode) {
        jdbcTemplate.update("DELETE FROM auth_account_role WHERE account_id = ?", accountId);
        jdbcTemplate.update("""
            INSERT INTO auth_account_role (account_id, role_id)
            SELECT ?, id FROM auth_role WHERE role_code = ?
            """, accountId, roleCode);
    }

    public void updateAccountType(Long accountId, AccountType accountType) {
        jdbcTemplate.update("UPDATE sys_account SET account_type = ? WHERE id = ?", accountType.name(), accountId);
    }

    public void updateAccountStatus(Long accountId, AccountStatus status) {
        jdbcTemplate.update("UPDATE sys_account SET status = ? WHERE id = ?", status.name(), accountId);
    }

    public void updateDefaultStore(Long accountId, Long storeId) {
        jdbcTemplate.update("UPDATE sys_account SET store_id = ? WHERE id = ?", storeId, accountId);
    }

    public void clearStoreScopes(Long accountId) {
        jdbcTemplate.update("DELETE FROM auth_account_store_scope WHERE account_id = ?", accountId);
    }

    public void insertAllMerchantScope(Long accountId, Long merchantId) {
        jdbcTemplate.update("""
            INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
            VALUES (?, ?, NULL, 'ALL_MERCHANT_STORES')
            """, accountId, merchantId);
    }

    public void insertSingleStoreScope(Long accountId, Long merchantId, Long storeId) {
        jdbcTemplate.update("""
            INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
            VALUES (?, ?, ?, 'SINGLE_STORE')
            """, accountId, merchantId, storeId);
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static class AccountRowMapper implements RowMapper<AccountRow> {
        @Override
        public AccountRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AccountRow(
                rs.getLong("id"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("username"),
                rs.getString("phone"),
                rs.getString("display_name"),
                getNullableLong(rs, "merchant_id"),
                rs.getString("merchant_name"),
                getNullableLong(rs, "store_id"),
                rs.getString("store_name"),
                getNullableLong(rs, "investor_id"),
                rs.getString("investor_name"),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getObject("last_login_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class RoleRowMapper implements RowMapper<RoleRow> {
        @Override
        public RoleRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RoleRow(
                rs.getLong("id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getString("role_scope"),
                rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class PermissionRowMapper implements RowMapper<PermissionRow> {
        @Override
        public PermissionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PermissionRow(
                rs.getLong("id"),
                rs.getString("permission_code"),
                rs.getString("permission_name"),
                rs.getString("module_code"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    public record AccountRow(
        Long id,
        AccountType accountType,
        String username,
        String phone,
        String displayName,
        Long merchantId,
        String merchantName,
        Long storeId,
        String storeName,
        Long investorId,
        String investorName,
        AccountStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
    ) {
    }

    public record RoleRow(
        Long id,
        String roleCode,
        String roleName,
        String roleScope,
        String status,
        LocalDateTime createdAt
    ) {
    }

    public record PermissionRow(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleCode,
        LocalDateTime createdAt
    ) {
    }
}
