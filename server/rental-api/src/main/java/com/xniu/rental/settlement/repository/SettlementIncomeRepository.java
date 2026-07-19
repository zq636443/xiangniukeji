package com.xniu.rental.settlement.repository;

import com.xniu.rental.settlement.model.IncomeBeneficiaryType;
import com.xniu.rental.settlement.model.IncomeEntryStatus;
import com.xniu.rental.settlement.model.IncomeLineType;
import com.xniu.rental.settlement.model.SettlementIncomeEntry;
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

    public boolean exists(Long snapshotId, IncomeBeneficiaryType beneficiaryType, Long beneficiaryId, IncomeLineType lineType) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_income_entry
            WHERE snapshot_id = ? AND beneficiary_type = ? AND beneficiary_id = ? AND line_type = ?
            """, Integer.class, snapshotId, beneficiaryType.name(), beneficiaryId, lineType.name());
        return count != null && count > 0;
    }

    public Optional<SettlementIncomeEntry> create(CreateRow row) {
        if (row.amount() == null || row.amount().signum() <= 0 || exists(row.snapshotId(), row.beneficiaryType(), row.beneficiaryId(), row.lineType())) {
            return Optional.empty();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_income_entry
                (entry_no, order_id, snapshot_id, merchant_id, store_id, beneficiary_type, beneficiary_id,
                 line_type, amount, entry_status, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, new String[] {"id"});
            statement.setString(1, row.entryNo());
            statement.setLong(2, row.orderId());
            statement.setLong(3, row.snapshotId());
            statement.setLong(4, row.merchantId());
            statement.setLong(5, row.storeId());
            statement.setString(6, row.beneficiaryType().name());
            statement.setLong(7, row.beneficiaryId());
            statement.setString(8, row.lineType().name());
            statement.setBigDecimal(9, row.amount());
            statement.setString(10, row.remark());
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

    public record CreateRow(
        String entryNo,
        Long orderId,
        Long snapshotId,
        Long merchantId,
        Long storeId,
        IncomeBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        IncomeLineType lineType,
        BigDecimal amount,
        String remark
    ) {
    }

    private static class EntryMapper implements RowMapper<SettlementIncomeEntry> {
        @Override
        public SettlementIncomeEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementIncomeEntry(
                rs.getLong("id"),
                rs.getString("entry_no"),
                rs.getLong("order_id"),
                rs.getLong("snapshot_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                IncomeBeneficiaryType.valueOf(rs.getString("beneficiary_type")),
                rs.getLong("beneficiary_id"),
                IncomeLineType.valueOf(rs.getString("line_type")),
                rs.getBigDecimal("amount"),
                IncomeEntryStatus.valueOf(rs.getString("entry_status")),
                rs.getString("remark"),
                rs.getObject("settled_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
