package com.xniu.rental.overdue.repository;

import com.xniu.rental.overdue.model.CollectionStatus;
import com.xniu.rental.overdue.model.OverdueCase;
import com.xniu.rental.overdue.model.OverdueCollectionLog;
import com.xniu.rental.overdue.model.OverdueStatus;
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
public class OverdueRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<OverdueCase> caseMapper = new CaseMapper();
    private final RowMapper<OverdueCollectionLog> logMapper = new LogMapper();

    public OverdueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OverdueCase> listCases(String statMonth, OverdueStatus overdueStatus, CollectionStatus collectionStatus, Long merchantId, Long storeId, Long userAccountId, Long storeSkuId) {
        var sql = new StringBuilder("SELECT * FROM rental_overdue_case WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (statMonth != null && !statMonth.isBlank()) {
            sql.append(" AND stat_month = ?");
            params.add(statMonth);
        }
        if (overdueStatus != null) {
            sql.append(" AND overdue_status = ?");
            params.add(overdueStatus.name());
        }
        if (collectionStatus != null) {
            sql.append(" AND collection_status = ?");
            params.add(collectionStatus.name());
        }
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        if (storeSkuId != null) {
            sql.append(" AND store_sku_id = ?");
            params.add(storeSkuId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), caseMapper, params.toArray());
    }

    public Optional<OverdueCase> findCase(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_overdue_case WHERE id = ?", caseMapper, id);
        return list.stream().findFirst();
    }

    public Optional<OverdueCase> findByBillId(Long billId) {
        var list = jdbcTemplate.query("SELECT * FROM rental_overdue_case WHERE bill_id = ?", caseMapper, billId);
        return list.stream().findFirst();
    }

    public OverdueCase createCase(CaseCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_overdue_case
                (case_no, stat_month, order_id, bill_id, user_account_id, merchant_id, store_id,
                 store_sku_id, sku_id, overdue_amount, unpaid_amount, fail_count, last_fail_reason,
                 last_deduct_at, overdue_status, collection_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.caseNo());
            statement.setString(2, row.statMonth());
            statement.setLong(3, row.orderId());
            statement.setLong(4, row.billId());
            setNullableLong(statement, 5, row.userAccountId());
            statement.setLong(6, row.merchantId());
            statement.setLong(7, row.storeId());
            statement.setLong(8, row.storeSkuId());
            statement.setLong(9, row.skuId());
            statement.setBigDecimal(10, row.overdueAmount());
            statement.setBigDecimal(11, row.unpaidAmount());
            statement.setInt(12, row.failCount());
            statement.setString(13, row.lastFailReason());
            statement.setObject(14, row.lastDeductAt());
            statement.setString(15, OverdueStatus.OPEN.name());
            statement.setString(16, CollectionStatus.PENDING.name());
            return statement;
        }, keyHolder);
        return findCase(keyHolder.getKey().longValue()).orElseThrow();
    }

    public OverdueCase updateFailure(Long id, BigDecimal overdueAmount, BigDecimal unpaidAmount, String lastFailReason, LocalDateTime lastDeductAt) {
        jdbcTemplate.update("""
            UPDATE rental_overdue_case
            SET overdue_amount = ?,
                unpaid_amount = ?,
                fail_count = fail_count + 1,
                last_fail_reason = ?,
                last_deduct_at = ?,
                overdue_status = 'OPEN'
            WHERE id = ?
            """, overdueAmount, unpaidAmount, lastFailReason, lastDeductAt, id);
        return findCase(id).orElseThrow();
    }

    public OverdueCase resolveByBillId(Long billId) {
        jdbcTemplate.update("""
            UPDATE rental_overdue_case
            SET overdue_status = 'RESOLVED',
                collection_status = 'RESOLVED',
                unpaid_amount = 0.00,
                resolved_at = COALESCE(resolved_at, CURRENT_TIMESTAMP)
            WHERE bill_id = ? AND overdue_status <> 'RESOLVED'
            """, billId);
        return findByBillId(billId).orElseThrow();
    }

    public OverdueCase updateCollection(Long id, CollectionStatus collectionStatus, String remark) {
        jdbcTemplate.update("""
            UPDATE rental_overdue_case
            SET collection_status = ?,
                collection_remark = ?
            WHERE id = ?
            """, collectionStatus.name(), remark, id);
        return findCase(id).orElseThrow();
    }

    public void addCollectionLog(Long overdueCaseId, CollectionStatus collectionStatus, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO rental_overdue_collection_log
            (overdue_case_id, collection_status, operator_account_id, remark)
            VALUES (?, ?, ?, ?)
            """, overdueCaseId, collectionStatus.name(), operatorAccountId, remark);
    }

    public List<OverdueCollectionLog> listLogs(Long overdueCaseId) {
        return jdbcTemplate.query("SELECT * FROM rental_overdue_collection_log WHERE overdue_case_id = ? ORDER BY id DESC", logMapper, overdueCaseId);
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

    public record CaseCreateRow(
        String caseNo,
        String statMonth,
        Long orderId,
        Long billId,
        Long userAccountId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        BigDecimal overdueAmount,
        BigDecimal unpaidAmount,
        Integer failCount,
        String lastFailReason,
        LocalDateTime lastDeductAt
    ) {
    }

    private static class CaseMapper implements RowMapper<OverdueCase> {
        @Override
        public OverdueCase mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OverdueCase(
                rs.getLong("id"),
                rs.getString("case_no"),
                rs.getString("stat_month"),
                rs.getLong("order_id"),
                rs.getLong("bill_id"),
                getNullableLong(rs, "user_account_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getBigDecimal("overdue_amount"),
                rs.getBigDecimal("unpaid_amount"),
                rs.getInt("fail_count"),
                rs.getString("last_fail_reason"),
                rs.getObject("last_deduct_at", LocalDateTime.class),
                OverdueStatus.valueOf(rs.getString("overdue_status")),
                CollectionStatus.valueOf(rs.getString("collection_status")),
                rs.getString("collection_remark"),
                rs.getObject("resolved_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class LogMapper implements RowMapper<OverdueCollectionLog> {
        @Override
        public OverdueCollectionLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OverdueCollectionLog(
                rs.getLong("id"),
                rs.getLong("overdue_case_id"),
                CollectionStatus.valueOf(rs.getString("collection_status")),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
