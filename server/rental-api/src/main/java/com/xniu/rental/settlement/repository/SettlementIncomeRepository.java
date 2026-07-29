package com.xniu.rental.settlement.repository;

import com.xniu.rental.settlement.model.IncomeBeneficiaryType;
import com.xniu.rental.settlement.model.IncomeEntryStatus;
import com.xniu.rental.settlement.model.IncomeLineType;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SettlementIncomeEntry;
import com.xniu.rental.settlement.model.StatementBeneficiaryType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementIncomeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SettlementIncomeEntry> mapper = new EntryMapper();

    public SettlementIncomeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SettlementIncomeEntry> list(IncomeBeneficiaryType beneficiaryType, Long beneficiaryId, IncomeEntryStatus status, Long orderId, Long storeId) {
        var sql = new StringBuilder("SELECT * FROM settlement_income_entry WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (beneficiaryType != null) {
            sql.append(" AND beneficiary_type = ?");
            params.add(beneficiaryType.name());
        }
        if (beneficiaryId != null) {
            sql.append(" AND beneficiary_id = ?");
            params.add(beneficiaryId);
        }
        if (status != null) {
            sql.append(" AND entry_status = ?");
            params.add(status.name());
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public Optional<SettlementIncomeEntry> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM settlement_income_entry WHERE id = ?", mapper, id);
        return list.stream().findFirst();
    }

    public List<SettlementIncomeEntry> listBySource(IncomeSourceType sourceType, Long sourceId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM settlement_income_entry
            WHERE source_type = ? AND source_id = ?
            ORDER BY id DESC
            """, mapper, sourceType.name(), sourceId);
    }

    public boolean hasNonPendingBySource(IncomeSourceType sourceType, Long sourceId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = ?
              AND source_id = ?
              AND entry_status <> 'PENDING'
            """, Integer.class, sourceType.name(), sourceId);
        return count != null && count > 0;
    }

    public void deleteBySource(IncomeSourceType sourceType, Long sourceId) {
        jdbcTemplate.update(
            "DELETE FROM settlement_income_entry WHERE source_type = ? AND source_id = ?",
            sourceType.name(),
            sourceId
        );
    }

    public boolean exists(
        IncomeSourceType sourceType,
        Long sourceId,
        IncomeBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        IncomeLineType lineType
    ) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_income_entry
            WHERE source_type = ?
              AND source_id = ?
              AND beneficiary_type = ?
              AND beneficiary_id = ?
              AND line_type = ?
            """, Integer.class, sourceType.name(), sourceId, beneficiaryType.name(), beneficiaryId, lineType.name());
        return count != null && count > 0;
    }

    public Optional<SettlementIncomeEntry> create(CreateRow row) {
        if (row.amount() == null || row.amount().signum() <= 0 || exists(
            row.sourceType(),
            row.sourceId(),
            row.beneficiaryType(),
            row.beneficiaryId(),
            row.lineType()
        )) {
            return Optional.empty();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_income_entry
                (entry_no, source_type, source_id, source_no, order_id, snapshot_id, merchant_id, store_id,
                 beneficiary_type, beneficiary_id, line_type, amount, entry_status, remark, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.entryNo());
            statement.setString(2, row.sourceType().name());
            statement.setLong(3, row.sourceId());
            statement.setString(4, row.sourceNo());
            setNullableLong(statement, 5, row.orderId());
            statement.setLong(6, row.snapshotId());
            statement.setLong(7, row.merchantId());
            statement.setLong(8, row.storeId());
            statement.setString(9, row.beneficiaryType().name());
            setNullableLong(statement, 10, row.beneficiaryId());
            statement.setString(11, row.lineType().name());
            statement.setBigDecimal(12, row.amount());
            statement.setString(13, row.remark());
            statement.setObject(14, row.occurredAt());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    public SettlementIncomeEntry updateStatus(Long id, IncomeEntryStatus status) {
        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = ?,
                settled_at = CASE WHEN ? = 'SETTLED' THEN COALESCE(settled_at, CURRENT_TIMESTAMP) ELSE settled_at END
            WHERE id = ?
            """, status.name(), status.name(), id);
        return findById(id).orElseThrow();
    }

    public int settleByStatement(
        Long statementId,
        StatementBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        Long storeId
    ) {
        var beneficiarySql = beneficiaryType == StatementBeneficiaryType.MERCHANT
            ? "e.beneficiary_type = 'MERCHANT' AND e.store_id = ?"
            : "e.beneficiary_type = 'INVESTOR' AND e.beneficiary_id = ?";
        var targetId = beneficiaryType == StatementBeneficiaryType.MERCHANT ? storeId : beneficiaryId;
        return jdbcTemplate.update("""
            UPDATE settlement_income_entry e
            SET entry_status = 'SETTLED',
                settled_at = COALESCE(settled_at, CURRENT_TIMESTAMP)
            WHERE e.entry_status = 'PENDING'
              AND %s
              AND EXISTS (
                SELECT 1
                FROM settlement_statement_line l
                WHERE l.statement_id = ?
                  AND (
                    (l.source_type = 'BILL' AND e.source_type = 'BILL' AND e.source_id = l.bill_id)
                    OR (
                      l.source_type = 'EXTERNAL_ORDER'
                      AND e.source_type = 'EXTERNAL_ORDER'
                      AND e.source_id = l.source_id
                    )
                  )
              )
            """.formatted(beneficiarySql), targetId, statementId);
    }

    public record CreateRow(
        String entryNo,
        IncomeSourceType sourceType,
        Long sourceId,
        String sourceNo,
        Long orderId,
        Long snapshotId,
        Long merchantId,
        Long storeId,
        IncomeBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        IncomeLineType lineType,
        BigDecimal amount,
        String remark,
        LocalDateTime occurredAt
    ) {
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static class EntryMapper implements RowMapper<SettlementIncomeEntry> {
        @Override
        public SettlementIncomeEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementIncomeEntry(
                rs.getLong("id"),
                rs.getString("entry_no"),
                IncomeSourceType.valueOf(rs.getString("source_type")),
                rs.getLong("source_id"),
                rs.getString("source_no"),
                getNullableLong(rs, "order_id"),
                rs.getLong("snapshot_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                IncomeBeneficiaryType.valueOf(rs.getString("beneficiary_type")),
                rs.getLong("beneficiary_id"),
                IncomeLineType.valueOf(rs.getString("line_type")),
                rs.getBigDecimal("amount"),
                IncomeEntryStatus.valueOf(rs.getString("entry_status")),
                rs.getString("remark"),
                rs.getObject("occurred_at", LocalDateTime.class),
                rs.getObject("settled_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
