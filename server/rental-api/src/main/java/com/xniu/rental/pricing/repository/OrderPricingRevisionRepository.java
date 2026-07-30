package com.xniu.rental.pricing.repository;

import com.xniu.rental.pricing.model.OrderPricingRevision;
import com.xniu.rental.pricing.model.PricingRevisionStatus;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.pricing.model.RenewalPricingRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPricingRevisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<OrderPricingRevision> mapper = new RevisionMapper();

    public OrderPricingRevisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrderPricingRevision create(
        Long orderId,
        PricingRevisionStatus status,
        boolean requiresConfirmation,
        RenewalPricingRule previousRule,
        RenewalPricingRule newRule,
        String reason,
        Long operatorAccountId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_order_pricing_revision
                (order_id, revision_status, requires_customer_confirmation, effective_mode,
                 previous_auto_renew_enabled, previous_renewal_unit, previous_renewal_value,
                 previous_renewal_amount, previous_billing_mode, previous_daily_amount,
                 previous_daily_cap_enabled, previous_grace_hours, previous_overdue_daily_amount,
                 new_auto_renew_enabled, new_renewal_unit, new_renewal_value, new_renewal_amount,
                 new_billing_mode, new_daily_amount, new_daily_cap_enabled, new_grace_hours,
                 new_overdue_daily_amount, reason, operator_account_id)
                VALUES (?, ?, ?, 'NEXT_UNBILLED_RENEWAL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            var index = 1;
            statement.setLong(index++, orderId);
            statement.setString(index++, status.name());
            statement.setBoolean(index++, requiresConfirmation);
            index = setRule(statement, index, previousRule);
            index = setRule(statement, index, newRule);
            statement.setString(index++, reason);
            if (operatorAccountId == null) statement.setObject(index, null); else statement.setLong(index, operatorAccountId);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<OrderPricingRevision> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM rental_order_pricing_revision WHERE id = ?", mapper, id).stream().findFirst();
    }

    public List<OrderPricingRevision> listByOrder(Long orderId) {
        return jdbcTemplate.query("SELECT * FROM rental_order_pricing_revision WHERE order_id = ? ORDER BY id DESC", mapper, orderId);
    }

    public boolean hasPending(Long orderId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) > 0 FROM rental_order_pricing_revision
            WHERE order_id = ? AND revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
            """, Boolean.class, orderId));
    }

    public OrderPricingRevision markApplied(Long id, boolean customerConfirmed) {
        jdbcTemplate.update("""
            UPDATE rental_order_pricing_revision
            SET revision_status = 'APPLIED',
                customer_confirmed_at = CASE WHEN ? = 1 THEN COALESCE(customer_confirmed_at, CURRENT_TIMESTAMP) ELSE customer_confirmed_at END,
                applied_at = COALESCE(applied_at, CURRENT_TIMESTAMP)
            WHERE id = ? AND revision_status <> 'CANCELLED'
            """, customerConfirmed ? 1 : 0, id);
        return findById(id).orElseThrow();
    }

    private static int setRule(java.sql.PreparedStatement statement, int index, RenewalPricingRule rule) throws SQLException {
        statement.setBoolean(index++, Boolean.TRUE.equals(rule.autoRenewEnabled()));
        statement.setString(index++, rule.renewalUnit());
        if (rule.renewalValue() == null) statement.setObject(index++, null); else statement.setInt(index++, rule.renewalValue());
        statement.setBigDecimal(index++, rule.renewalAmount());
        statement.setString(index++, rule.renewalBillingMode().name());
        statement.setBigDecimal(index++, rule.renewalDailyAmount());
        statement.setBoolean(index++, Boolean.TRUE.equals(rule.renewalDailyCapEnabled()));
        statement.setInt(index++, rule.renewalGraceHours());
        statement.setBigDecimal(index++, rule.overdueDailyAmount());
        return index;
    }

    private static class RevisionMapper implements RowMapper<OrderPricingRevision> {
        @Override
        public OrderPricingRevision mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OrderPricingRevision(
                rs.getLong("id"),
                rs.getLong("order_id"),
                PricingRevisionStatus.valueOf(rs.getString("revision_status")),
                rs.getBoolean("requires_customer_confirmation"),
                rs.getString("effective_mode"),
                rule(rs, "previous_"),
                rule(rs, "new_"),
                rs.getString("reason"),
                nullableLong(rs, "operator_account_id"),
                rs.getObject("customer_confirmed_at", java.time.LocalDateTime.class),
                rs.getObject("applied_at", java.time.LocalDateTime.class),
                rs.getObject("created_at", java.time.LocalDateTime.class),
                rs.getObject("updated_at", java.time.LocalDateTime.class)
            );
        }

        private RenewalPricingRule rule(ResultSet rs, String prefix) throws SQLException {
            return new RenewalPricingRule(
                rs.getBoolean(prefix + "auto_renew_enabled"),
                rs.getString(prefix + "renewal_unit"),
                nullableInt(rs, prefix + "renewal_value"),
                rs.getBigDecimal(prefix + "renewal_amount"),
                RenewalBillingMode.valueOf(rs.getString(prefix + "billing_mode")),
                rs.getBigDecimal(prefix + "daily_amount"),
                rs.getBoolean(prefix + "daily_cap_enabled"),
                rs.getInt(prefix + "grace_hours"),
                rs.getBigDecimal(prefix + "overdue_daily_amount")
            );
        }

        private Integer nullableInt(ResultSet rs, String column) throws SQLException {
            var value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }

        private Long nullableLong(ResultSet rs, String column) throws SQLException {
            var value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }
}
