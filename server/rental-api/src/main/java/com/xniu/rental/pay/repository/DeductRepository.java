package com.xniu.rental.pay.repository;

import com.xniu.rental.pay.model.DeductBatch;
import com.xniu.rental.pay.model.DeductBatchStatus;
import com.xniu.rental.pay.model.DeductRecord;
import com.xniu.rental.pay.model.DeductStatus;
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
public class DeductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DeductBatch> batchMapper = new BatchMapper();
    private final RowMapper<DeductRecord> recordMapper = new RecordMapper();

    public DeductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeductBatch createBatch(String batchNo, Integer plannedCount, String remark) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_deduct_batch
                (batch_no, batch_status, planned_count, remark, started_at)
                VALUES (?, 'PROCESSING', ?, ?, CURRENT_TIMESTAMP)
                """, new String[] {"id"});
            statement.setString(1, batchNo);
            statement.setInt(2, plannedCount);
            statement.setString(3, remark);
            return statement;
        }, keyHolder);
        return findBatch(keyHolder.getKey().longValue()).orElseThrow();
    }

    public DeductBatch finishBatch(String batchNo, Integer successCount, Integer failedCount) {
        jdbcTemplate.update("""
            UPDATE rental_deduct_batch
            SET batch_status = 'FINISHED',
                success_count = ?,
                failed_count = ?,
                finished_at = CURRENT_TIMESTAMP
            WHERE batch_no = ?
            """, successCount, failedCount, batchNo);
        return findBatchByNo(batchNo).orElseThrow();
    }

    public Optional<DeductBatch> findBatch(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_deduct_batch WHERE id = ?", batchMapper, id);
        return list.stream().findFirst();
    }

    public Optional<DeductBatch> findBatchByNo(String batchNo) {
        var list = jdbcTemplate.query("SELECT * FROM rental_deduct_batch WHERE batch_no = ?", batchMapper, batchNo);
        return list.stream().findFirst();
    }

    public List<DeductBatch> listBatches() {
        return jdbcTemplate.query("SELECT * FROM rental_deduct_batch ORDER BY id DESC", batchMapper);
    }

    public Optional<DeductRecord> findRecord(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_deduct_record WHERE id = ?", recordMapper, id);
        return list.stream().findFirst();
    }

    public Optional<DeductRecord> findLatestByBillId(Long billId) {
        var list = jdbcTemplate.query("""
            SELECT * FROM rental_deduct_record
            WHERE bill_id = ?
            ORDER BY id DESC LIMIT 1
            """, recordMapper, billId);
        return list.stream().findFirst();
    }

    public List<DeductRecord> listRecords(DeductStatus status, Long billId, Long orderId) {
        var sql = new StringBuilder("SELECT * FROM rental_deduct_record WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND deduct_status = ?");
            params.add(status.name());
        }
        if (billId != null) {
            sql.append(" AND bill_id = ?");
            params.add(billId);
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), recordMapper, params.toArray());
    }

    public DeductRecord createRecord(DeductCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_deduct_record
                (deduct_no, batch_no, bill_id, order_id, agreement_id, agreement_no, deduct_status,
                 deduct_amount, retry_count, next_retry_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.deductNo());
            statement.setString(2, row.batchNo());
            statement.setLong(3, row.billId());
            statement.setLong(4, row.orderId());
            statement.setLong(5, row.agreementId());
            statement.setString(6, row.agreementNo());
            statement.setString(7, row.deductStatus().name());
            statement.setBigDecimal(8, row.deductAmount());
            statement.setInt(9, row.retryCount());
            statement.setObject(10, row.nextRetryAt());
            return statement;
        }, keyHolder);
        return findRecord(keyHolder.getKey().longValue()).orElseThrow();
    }

    public DeductRecord markProcessing(Long id, String batchNo, Long paymentId) {
        jdbcTemplate.update("""
            UPDATE rental_deduct_record
            SET deduct_status = 'PROCESSING',
                batch_no = ?,
                payment_id = ?,
                requested_at = CURRENT_TIMESTAMP,
                last_error = NULL
            WHERE id = ?
            """, batchNo, paymentId, id);
        return findRecord(id).orElseThrow();
    }

    public DeductRecord markSuccess(Long id, String alipayTradeNo) {
        jdbcTemplate.update("""
            UPDATE rental_deduct_record
            SET deduct_status = 'SUCCESS',
                alipay_trade_no = ?,
                success_at = CURRENT_TIMESTAMP,
                last_error = NULL
            WHERE id = ?
            """, alipayTradeNo, id);
        return findRecord(id).orElseThrow();
    }

    public DeductRecord markFailed(Long id, String error, LocalDateTime nextRetryAt) {
        jdbcTemplate.update("""
            UPDATE rental_deduct_record
            SET deduct_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = ?,
                last_error = ?
            WHERE id = ?
            """, nextRetryAt, error, id);
        return findRecord(id).orElseThrow();
    }

    public record DeductCreateRow(
        String deductNo,
        String batchNo,
        Long billId,
        Long orderId,
        Long agreementId,
        String agreementNo,
        DeductStatus deductStatus,
        BigDecimal deductAmount,
        Integer retryCount,
        LocalDateTime nextRetryAt
    ) {
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static class BatchMapper implements RowMapper<DeductBatch> {
        @Override
        public DeductBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DeductBatch(
                rs.getLong("id"),
                rs.getString("batch_no"),
                DeductBatchStatus.valueOf(rs.getString("batch_status")),
                rs.getInt("planned_count"),
                rs.getInt("success_count"),
                rs.getInt("failed_count"),
                rs.getString("remark"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class RecordMapper implements RowMapper<DeductRecord> {
        @Override
        public DeductRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DeductRecord(
                rs.getLong("id"),
                rs.getString("deduct_no"),
                rs.getString("batch_no"),
                rs.getLong("bill_id"),
                rs.getLong("order_id"),
                rs.getLong("agreement_id"),
                rs.getString("agreement_no"),
                getNullableLong(rs, "payment_id"),
                DeductStatus.valueOf(rs.getString("deduct_status")),
                rs.getBigDecimal("deduct_amount"),
                rs.getInt("retry_count"),
                rs.getObject("next_retry_at", LocalDateTime.class),
                rs.getString("alipay_trade_no"),
                rs.getString("last_error"),
                rs.getObject("requested_at", LocalDateTime.class),
                rs.getObject("success_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
