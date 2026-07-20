package com.xniu.rental.auth.repository;

import com.xniu.rental.auth.model.StoreScope;
import com.xniu.rental.auth.model.StoreScopeType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findRoleCodes(Long accountId) {
        return jdbcTemplate.queryForList("""
            SELECT r.role_code
            FROM auth_account_role ar
            JOIN auth_role r ON r.id = ar.role_id
            WHERE ar.account_id = ? AND r.status = 'ENABLED'
            ORDER BY r.role_code
            """, String.class, accountId);
    }

    public List<String> findPermissionCodes(Long accountId) {
        return jdbcTemplate.queryForList("""
            SELECT permission_code
            FROM (
                SELECT p.permission_code
                FROM auth_account_role ar
                JOIN auth_role r ON r.id = ar.role_id
                JOIN auth_role_permission rp ON rp.role_id = r.id
                JOIN auth_permission p ON p.id = rp.permission_id
                WHERE ar.account_id = ? AND r.status = 'ENABLED'
                UNION
                SELECT p.permission_code
                FROM auth_account_permission ap
                JOIN auth_permission p ON p.id = ap.permission_id
                WHERE ap.account_id = ?
            ) effective_permissions
            ORDER BY permission_code
            """, String.class, accountId, accountId);
    }

    public List<StoreScope> findStoreScopes(Long accountId) {
        return jdbcTemplate.query("""
            SELECT merchant_id, store_id, scope_type
            FROM auth_account_store_scope
            WHERE account_id = ?
            ORDER BY merchant_id, store_id
            """, (rs, rowNum) -> new StoreScope(
                rs.getLong("merchant_id"),
                getNullableLong(rs, "store_id"),
                StoreScopeType.valueOf(rs.getString("scope_type"))
            ), accountId);
    }

    private static Long getNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
