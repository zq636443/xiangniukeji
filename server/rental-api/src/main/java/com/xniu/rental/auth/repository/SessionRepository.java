package com.xniu.rental.auth.repository;

import com.xniu.rental.auth.model.AccountType;
import com.xniu.rental.auth.model.AuthSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AuthSession> mapper = new AuthSessionMapper();

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(String token, Long accountId, AccountType accountType, LocalDateTime expiresAt) {
        jdbcTemplate.update("""
            INSERT INTO auth_session (token, account_id, account_type, expires_at)
            VALUES (?, ?, ?, ?)
            """, token, accountId, accountType.name(), expiresAt);
    }

    public Optional<AuthSession> findByToken(String token) {
        var sessions = jdbcTemplate.query("""
            SELECT * FROM auth_session WHERE token = ?
            """, mapper, token);
        return sessions.stream().findFirst();
    }

    public void revoke(String token, LocalDateTime now) {
        jdbcTemplate.update("""
            UPDATE auth_session SET revoked_at = ? WHERE token = ? AND revoked_at IS NULL
            """, now, token);
    }

    private static class AuthSessionMapper implements RowMapper<AuthSession> {
        @Override
        public AuthSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuthSession(
                rs.getString("token"),
                rs.getLong("account_id"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getObject("expires_at", LocalDateTime.class),
                rs.getObject("revoked_at", LocalDateTime.class)
            );
        }
    }
}
