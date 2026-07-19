package com.xniu.rental.merchant.repository;

import com.xniu.rental.auth.model.Account;
import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Account> mapper = new AccountMapper();

    public EmployeeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Account> listByMerchant(Long merchantId) {
        return jdbcTemplate.query("""
            SELECT * FROM sys_account
            WHERE merchant_id = ? AND account_type IN ('MERCHANT_OWNER', 'STORE_MANAGER', 'STORE_OPERATOR', 'STORE_STAFF', 'MAINTENANCE_STAFF', 'WAREHOUSE_STAFF')
            ORDER BY id DESC
            """, mapper, merchantId);
    }

    public Optional<Account> findByUsername(String username) {
        var accounts = jdbcTemplate.query("SELECT * FROM sys_account WHERE username = ?", mapper, username);
        return accounts.stream().findFirst();
    }

    public Optional<Account> findById(Long id) {
        var accounts = jdbcTemplate.query("SELECT * FROM sys_account WHERE id = ?", mapper, id);
        return accounts.stream().findFirst();
    }

    public Account create(
        AccountType accountType,
        String username,
        String phone,
        String displayName,
        String passwordHash,
        Long merchantId,
        Long storeId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO sys_account
                (account_type, username, phone, display_name, password_hash, merchant_id, store_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ENABLED')
                """, new String[] {"id"});
            statement.setString(1, accountType.name());
            statement.setString(2, username);
            statement.setString(3, phone);
            statement.setString(4, displayName);
            statement.setString(5, passwordHash);
            statement.setLong(6, merchantId);
            if (storeId == null) {
                statement.setObject(7, null);
            } else {
                statement.setLong(7, storeId);
            }
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public void bindRole(Long accountId, String roleCode) {
        jdbcTemplate.update("""
            INSERT INTO auth_account_role (account_id, role_id)
            SELECT ?, id FROM auth_role WHERE role_code = ?
            """, accountId, roleCode);
    }

    public void replaceStoreScopes(Long accountId, Long merchantId, List<Long> storeIds) {
        jdbcTemplate.update("DELETE FROM auth_account_store_scope WHERE account_id = ?", accountId);
        if (storeIds == null || storeIds.isEmpty()) {
            jdbcTemplate.update("""
                INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
                VALUES (?, ?, NULL, 'ALL_MERCHANT_STORES')
                """, accountId, merchantId);
            return;
        }
        for (Long storeId : storeIds) {
            jdbcTemplate.update("""
                INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
                VALUES (?, ?, ?, 'SINGLE_STORE')
                """, accountId, merchantId, storeId);
        }
    }

    public Account updateStatus(Long accountId, AccountStatus status) {
        jdbcTemplate.update("UPDATE sys_account SET status = ? WHERE id = ?", status.name(), accountId);
        return findById(accountId).orElseThrow();
    }

    private static class AccountMapper implements RowMapper<Account> {
        @Override
        public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Account(
                rs.getLong("id"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("username"),
                rs.getString("phone"),
                rs.getString("alipay_user_id"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                getNullableLong(rs, "merchant_id"),
                getNullableLong(rs, "store_id"),
                getNullableLong(rs, "investor_id"),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getObject("last_login_at", LocalDateTime.class)
            );
        }

        private Long getNullableLong(ResultSet rs, String column) throws SQLException {
            var value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }
}
