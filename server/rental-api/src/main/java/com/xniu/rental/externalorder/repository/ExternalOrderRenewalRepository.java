package com.xniu.rental.externalorder.repository;

import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
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
public class ExternalOrderRenewalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalOrderRenewalEvent> mapper = new EventMapper();

    public ExternalOrderRenewalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> listDueOrderIds(LocalDateTime dueAt, int limit) {
        return jdbcTemplate.queryForList("""
            SELECT id
            FROM external_rental_order
            WHERE order_status = 'ACTIVE'
              AND auto_renew_enabled = 1
              AND expected_return_at IS NOT NULL
              AND expected_return_at <= ?
              AND renewal_amount IS NOT NULL
              AND renewal_amount > 0
              AND renewal_value IS NOT NULL
              AND renewal_value > 0
              AND settlement_snapshot_id IS NOT NULL
            ORDER BY expected_return_at, id
            LIMIT ?
            """, Long.class, dueAt, limit);
    }

    public int nextPeriodNo(Long externalOrderId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(period_no), 0) + 1
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            """, Integer.class, externalOrderId);
    }

    public ExternalOrderRenewalEvent create(
        Long externalOrderId,
        String eventNo,
        int periodNo,
        LocalDateTime periodStartAt,
        LocalDateTime periodEndAt,
        java.math.BigDecimal renewalAmount,
        java.math.BigDecimal batteryCostAmount
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO external_order_renewal_event
                (external_order_id, event_no, period_no, period_start_at, period_end_at,
                 renewal_amount, battery_cost_amount, event_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACCRUED')
                """, new String[] {"id"});
            statement.setLong(1, externalOrderId);
            statement.setString(2, eventNo);
            statement.setInt(3, periodNo);
            statement.setObject(4, periodStartAt);
            statement.setObject(5, periodEndAt);
            statement.setBigDecimal(6, renewalAmount);
            statement.setBigDecimal(7, batteryCostAmount);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<ExternalOrderRenewalEvent> findById(Long id) {
        return jdbcTemplate.query(
            "SELECT * FROM external_order_renewal_event WHERE id = ?",
            mapper,
            id
        ).stream().findFirst();
    }

    public ExternalOrderRenewalEvent attachSnapshot(Long id, Long snapshotId) {
        jdbcTemplate.update(
            "UPDATE external_order_renewal_event SET settlement_snapshot_id = ? WHERE id = ?",
            snapshotId,
            id
        );
        return findById(id).orElseThrow();
    }

    public List<ExternalOrderRenewalEvent> listByExternalOrder(Long externalOrderId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            ORDER BY period_no
            """, mapper, externalOrderId);
    }

    public List<RenewalView> listAccrued(Long storeId) {
        var sql = new StringBuilder("""
            SELECT r.*, o.record_no AS external_order_record_no, o.merchant_id, o.store_id,
                   EXISTS (
                     SELECT 1
                     FROM settlement_statement_line l
                     JOIN settlement_statement s ON s.id = l.statement_id
                     WHERE l.source_type = 'EXTERNAL_RENEWAL'
                       AND l.source_id = r.id
                       AND s.beneficiary_type = 'MERCHANT'
                       AND s.store_id = o.store_id
                   ) AS included_in_merchant_statement
            FROM external_order_renewal_event r
            JOIN external_rental_order o ON o.id = r.external_order_id
            WHERE r.event_status = 'ACCRUED'
            """);
        var params = new java.util.ArrayList<Object>();
        if (storeId != null) {
            sql.append(" AND o.store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY r.period_start_at DESC, r.id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RenewalView(
            mapper.mapRow(rs, rowNum),
            rs.getString("external_order_record_no"),
            rs.getLong("merchant_id"),
            rs.getLong("store_id"),
            rs.getBoolean("included_in_merchant_statement")
        ), params.toArray());
    }

    public boolean hasNonPendingIncomeByExternalOrder(Long externalOrderId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry e
            JOIN external_order_renewal_event r ON r.id = e.source_id
            WHERE e.source_type = 'EXTERNAL_RENEWAL'
              AND r.external_order_id = ?
              AND e.entry_status <> 'PENDING'
            """, Integer.class, externalOrderId);
        return count != null && count > 0;
    }

    public boolean hasLockedStatementLinesByExternalOrder(Long externalOrderId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            JOIN external_order_renewal_event r ON r.id = l.source_id
            WHERE l.source_type = 'EXTERNAL_RENEWAL'
              AND r.external_order_id = ?
              AND s.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
            """, Integer.class, externalOrderId);
        return count != null && count > 0;
    }

    public List<String> listDraftStatementMonthsByExternalOrder(Long externalOrderId) {
        return jdbcTemplate.queryForList("""
            SELECT DISTINCT s.statement_month
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            JOIN external_order_renewal_event r ON r.id = l.source_id
            WHERE l.source_type = 'EXTERNAL_RENEWAL'
              AND r.external_order_id = ?
              AND s.status IN ('DRAFT', 'RECONCILING')
            ORDER BY s.statement_month
            """, String.class, externalOrderId);
    }

    public void reversePendingByExternalOrder(Long externalOrderId) {
        jdbcTemplate.update("""
            DELETE e
            FROM settlement_income_entry e
            JOIN external_order_renewal_event r ON r.id = e.source_id
            WHERE e.source_type = 'EXTERNAL_RENEWAL'
              AND r.external_order_id = ?
              AND e.entry_status = 'PENDING'
            """, externalOrderId);
        jdbcTemplate.update("""
            DELETE s
            FROM settlement_rule_snapshot s
            JOIN external_order_renewal_event r ON r.id = s.source_id
            WHERE s.source_type = 'EXTERNAL_RENEWAL'
              AND r.external_order_id = ?
            """, externalOrderId);
        jdbcTemplate.update("""
            UPDATE external_order_renewal_event
            SET event_status = 'REVERSED', settlement_snapshot_id = NULL
            WHERE external_order_id = ? AND event_status = 'ACCRUED'
            """, externalOrderId);
    }

    public void deleteByExternalOrder(Long externalOrderId) {
        jdbcTemplate.update(
            "DELETE FROM external_order_renewal_event WHERE external_order_id = ?",
            externalOrderId
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record RenewalView(
        ExternalOrderRenewalEvent event,
        String externalOrderRecordNo,
        Long merchantId,
        Long storeId,
        Boolean includedInMerchantStatement
    ) {
    }

    private static class EventMapper implements RowMapper<ExternalOrderRenewalEvent> {
        @Override
        public ExternalOrderRenewalEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalOrderRenewalEvent(
                rs.getLong("id"),
                rs.getLong("external_order_id"),
                rs.getString("event_no"),
                rs.getInt("period_no"),
                rs.getObject("period_start_at", LocalDateTime.class),
                rs.getObject("period_end_at", LocalDateTime.class),
                rs.getBigDecimal("renewal_amount"),
                rs.getBigDecimal("battery_cost_amount"),
                nullableLong(rs, "settlement_snapshot_id"),
                rs.getString("event_status"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
