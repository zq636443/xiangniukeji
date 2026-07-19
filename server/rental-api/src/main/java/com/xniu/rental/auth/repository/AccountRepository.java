package com.xniu.rental.auth.repository;

import com.xniu.rental.auth.model.Account;
import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Account> mapper = new AccountMapper();

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Account> findByUsername(String username) {
        var accounts = jdbcTemplate.query("""
            SELECT * FROM sys_account WHERE username = ?
            """, mapper, username);
        return accounts.stream().findFirst();
    }

    public Optional<Account> findById(Long id) {
        var accounts = jdbcTemplate.query("""
            SELECT * FROM sys_account WHERE id = ?
            """, mapper, id);
        return accounts.stream().findFirst();
    }

    public Optional<Account> findByAlipayUserId(String alipayUserId) {
        var accounts = jdbcTemplate.query("""
            SELECT * FROM sys_account WHERE alipay_user_id = ?
            """, mapper, alipayUserId);
        return accounts.stream().findFirst();
    }

    public Account createAlipayConsumer(String alipayUserId, String displayName, String phone) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO sys_account
                (account_type, phone, alipay_user_id, display_name, status)
                VALUES (?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, AccountType.CONSUMER.name());
            statement.setString(2, phone);
            statement.setString(3, alipayUserId);
            statement.setString(4, displayName);
            statement.setString(5, AccountStatus.ENABLED.name());
            return statement;
        }, keyHolder);
        var accountId = keyHolder.getKey().longValue();
        jdbcTemplate.update("""
            INSERT INTO auth_account_role (account_id, role_id)
            SELECT ?, id FROM auth_role WHERE role_code = 'CONSUMER'
            """, accountId);
        return findById(accountId).orElseThrow();
    }

    public Account createManual(
        AccountType accountType,
        String username,
        String phone,
        String displayName,
        String passwordHash,
        Long merchantId,
        Long storeId,
        Long investorId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO sys_account
                (account_type, username, phone, display_name, password_hash, merchant_id, store_id, investor_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, accountType.name());
            statement.setString(2, username);
            statement.setString(3, phone);
            statement.setString(4, displayName);
            statement.setString(5, passwordHash);
            statement.setObject(6, merchantId);
            statement.setObject(7, storeId);
            statement.setObject(8, investorId);
            statement.setString(9, AccountStatus.ENABLED.name());
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

    public void updateBasicInfo(Long accountId, String username, String phone, String displayName) {
        jdbcTemplate.update("""
            UPDATE sys_account
            SET username = ?, phone = ?, display_name = ?
            WHERE id = ?
            """, username, phone, displayName, accountId);
    }

    public void updatePassword(Long accountId, String passwordHash) {
        jdbcTemplate.update("""
            UPDATE sys_account
            SET password_hash = ?
            WHERE id = ?
            """, passwordHash, accountId);
    }

    public void markLastLoginAt(Long accountId, LocalDateTime now) {
        jdbcTemplate.update("""
            UPDATE sys_account SET last_login_at = ? WHERE id = ?
            """, now, accountId);
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
