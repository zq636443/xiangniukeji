package com.xniu.rental.externalorder.repository;

import com.xniu.rental.externalorder.model.ExternalOrderPricingRevision;
import com.xniu.rental.pricing.model.PricingRevisionStatus;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.pricing.model.RenewalPricingRule;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalOrderPricingRevisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalOrderPricingRevision> mapper = new RevisionMapper();

    public ExternalOrderPricingRevisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ExternalOrderPricingRevision create(
        Long externalOrderId,
        String batchNo,
        PricingRevisionStatus status,
        boolean requiresConfirmation,
        RenewalPricingRule previousRule,
        RenewalPricingRule newRule,
        String reason,
        String confirmationMethod,
        String confirmationReference,
        Long operatorAccountId,
        LocalDateTime customerConfirmedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO external_order_pricing_revision
                (external_order_id, batch_no, revision_status, requires_customer_confirmation,
                 previous_auto_renew_enabled, previous_renewal_unit, previous_renewal_value,
                 previous_renewal_amount, previous_billing_mode, previous_daily_amount,
                 previous_daily_cap_enabled, previous_grace_hours, previous_overdue_daily_amount,
                 new_auto_renew_enabled, new_renewal_unit, new_renewal_value, new_renewal_amount,
                 new_billing_mode, new_daily_amount, new_daily_cap_enabled, new_grace_hours,
                 new_overdue_daily_amount, reason, confirmation_method, confirmation_reference,
                 operator_account_id, customer_confirmed_at, applied_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CASE WHEN ? = 'APPLIED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, new String[] {"id"});
            var index = 1;
            statement.setLong(index++, externalOrderId);
            statement.setString(index++, batchNo);
            statement.setString(index++, status.name());
            statement.setBoolean(index++, requiresConfirmation);
            index = setRule(statement, index, previousRule);
            index = setRule(statement, index, newRule);
            statement.setString(index++, reason);
            statement.setString(index++, confirmationMethod);
            statement.setString(index++, confirmationReference);
            setNullableLong(statement, index++, operatorAccountId);
            statement.setObject(index++, customerConfirmedAt);
            statement.setString(index, status.name());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<ExternalOrderPricingRevision> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM external_order_pricing_revision WHERE id = ?", mapper, id).stream().findFirst();
    }

    public Optional<ExternalOrderPricingRevision> findByIdForUpdate(Long id) {
        return jdbcTemplate.query("SELECT * FROM external_order_pricing_revision WHERE id = ? FOR UPDATE", mapper, id).stream().findFirst();
    }

    public List<ExternalOrderPricingRevision> listByOrder(Long externalOrderId) {
        return jdbcTemplate.query(
            "SELECT * FROM external_order_pricing_revision WHERE external_order_id = ? ORDER BY id DESC",
            mapper,
            externalOrderId
        );
    }

    public boolean hasPending(Long externalOrderId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) > 0
            FROM external_order_pricing_revision
            WHERE external_order_id = ? AND revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
            """, Boolean.class, externalOrderId));
    }

    public Set<Long> findPendingOrderIds(List<Long> externalOrderIds) {
        if (externalOrderIds == null || externalOrderIds.isEmpty()) {
            return Set.of();
        }
        var placeholders = String.join(",", java.util.Collections.nCopies(externalOrderIds.size(), "?"));
        return new HashSet<>(jdbcTemplate.queryForList("""
            SELECT DISTINCT external_order_id
            FROM external_order_pricing_revision
            WHERE revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
              AND external_order_id IN (%s)
            """.formatted(placeholders), Long.class, externalOrderIds.toArray()));
    }

    public ExternalOrderPricingRevision confirmAndMarkApplied(
        Long id,
        String confirmationMethod,
        String confirmationReference,
        LocalDateTime customerConfirmedAt
    ) {
        jdbcTemplate.update("""
            UPDATE external_order_pricing_revision
            SET revision_status = 'APPLIED',
                confirmation_method = ?,
                confirmation_reference = ?,
                customer_confirmed_at = ?,
                applied_at = COALESCE(applied_at, CURRENT_TIMESTAMP)
            WHERE id = ? AND revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
            """, confirmationMethod, confirmationReference, customerConfirmedAt, id);
        return findById(id).orElseThrow();
    }

    public void cancelPendingByOrder(Long externalOrderId) {
        jdbcTemplate.update("""
            UPDATE external_order_pricing_revision
            SET revision_status = 'CANCELLED'
            WHERE external_order_id = ?
              AND revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
            """, externalOrderId);
    }

    public void deleteByOrder(Long externalOrderId) {
        jdbcTemplate.update("DELETE FROM external_order_pricing_revision WHERE external_order_id = ?", externalOrderId);
    }

    private static int setRule(PreparedStatement statement, int index, RenewalPricingRule rule) throws SQLException {
        statement.setBoolean(index++, Boolean.TRUE.equals(rule.autoRenewEnabled()));
        statement.setString(index++, rule.renewalUnit());
        if (rule.renewalValue() == null) statement.setObject(index++, null); else statement.setInt(index++, rule.renewalValue());
        statement.setBigDecimal(index++, rule.renewalAmount());
        statement.setString(index++, rule.renewalBillingMode().name());
        statement.setBigDecimal(index++, rule.renewalDailyAmount());
        statement.setBoolean(index++, Boolean.TRUE.equals(rule.renewalDailyCapEnabled()));
        statement.setInt(index++, rule.renewalGraceHours() == null ? 0 : rule.renewalGraceHours());
        statement.setBigDecimal(index++, rule.overdueDailyAmount());
        return index;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setObject(index, null); else statement.setLong(index, value);
    }

    private static class RevisionMapper implements RowMapper<ExternalOrderPricingRevision> {
        @Override
        public ExternalOrderPricingRevision mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalOrderPricingRevision(
                rs.getLong("id"),
                rs.getLong("external_order_id"),
                rs.getString("batch_no"),
                PricingRevisionStatus.valueOf(rs.getString("revision_status")),
                rs.getBoolean("requires_customer_confirmation"),
                rule(rs, "previous_"),
                rule(rs, "new_"),
                rs.getString("reason"),
                rs.getString("confirmation_method"),
                rs.getString("confirmation_reference"),
                nullableLong(rs, "operator_account_id"),
                rs.getObject("customer_confirmed_at", LocalDateTime.class),
                rs.getObject("applied_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
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
