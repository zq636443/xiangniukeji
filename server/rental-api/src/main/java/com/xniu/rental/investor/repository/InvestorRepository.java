package com.xniu.rental.investor.repository;

import com.xniu.rental.investor.model.Investor;
import com.xniu.rental.investor.model.InvestorStatus;
import java.math.BigDecimal;
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
public class InvestorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Investor> mapper = new InvestorMapper();

    public InvestorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Investor> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM investor ORDER BY id DESC", mapper);
        }
        var like = "%" + keyword.trim() + "%";
        return jdbcTemplate.query("""
            SELECT * FROM investor
            WHERE investor_name LIKE ? OR investor_code LIKE ? OR contact_phone LIKE ?
            ORDER BY id DESC
            """, mapper, like, like, like);
    }

    public Optional<Investor> findById(Long id) {
        var investors = jdbcTemplate.query("SELECT * FROM investor WHERE id = ?", mapper, id);
        return investors.stream().findFirst();
    }

    public Optional<Investor> findByCode(String investorCode) {
        var investors = jdbcTemplate.query("SELECT * FROM investor WHERE investor_code = ?", mapper, investorCode);
        return investors.stream().findFirst();
    }

    public Investor create(String investorCode, String investorName, String contactName, String contactPhone, BigDecimal operationFeeRate) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO investor
                (investor_code, investor_name, contact_name, contact_phone, operation_fee_rate)
                VALUES (?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, investorCode);
            statement.setString(2, investorName);
            statement.setString(3, contactName);
            statement.setString(4, contactPhone);
            statement.setBigDecimal(5, operationFeeRate);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Investor update(Long id, String investorName, String contactName, String contactPhone, BigDecimal operationFeeRate) {
        jdbcTemplate.update("""
            UPDATE investor
            SET investor_name = ?, contact_name = ?, contact_phone = ?, operation_fee_rate = ?
            WHERE id = ?
            """, investorName, contactName, contactPhone, operationFeeRate, id);
        return findById(id).orElseThrow();
    }

    public Investor updateStatus(Long id, InvestorStatus status) {
        jdbcTemplate.update("UPDATE investor SET status = ? WHERE id = ?", status.name(), id);
        return findById(id).orElseThrow();
    }

    public int countReferences(Long investorId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT
                (SELECT COUNT(*) FROM asset_item WHERE investor_id = ?)
              + (SELECT COUNT(*) FROM asset_ownership_history WHERE investor_id = ?)
              + (SELECT COUNT(*) FROM order_asset_usage WHERE investor_id = ?)
              + (SELECT COUNT(*) FROM settlement_statement_line WHERE investor_id = ?)
              + (SELECT COUNT(*) FROM sys_account WHERE investor_id = ?)
            """, Integer.class, investorId, investorId, investorId, investorId, investorId);
        return count == null ? 0 : count;
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM investor WHERE id = ?", id);
    }

    private static class InvestorMapper implements RowMapper<Investor> {
        @Override
        public Investor mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Investor(
                rs.getLong("id"),
                rs.getString("investor_code"),
                rs.getString("investor_name"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getBigDecimal("operation_fee_rate"),
                InvestorStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
