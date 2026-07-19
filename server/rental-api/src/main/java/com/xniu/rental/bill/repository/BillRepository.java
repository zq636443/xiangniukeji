package com.xniu.rental.bill.repository;

import com.xniu.rental.bill.model.BillGenerationBatch;
import com.xniu.rental.bill.model.BillGenerationType;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.model.RentalBillItem;
import com.xniu.rental.bill.model.RentalBillOperationLog;
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
public class BillRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RentalBill> billMapper = new BillMapper();
    private final RowMapper<RentalBillItem> itemMapper = new ItemMapper();
    private final RowMapper<RentalBillOperationLog> logMapper = new LogMapper();
    private final RowMapper<BillGenerationBatch> batchMapper = new BatchMapper();

    public BillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RentalBill> listBills(BillStatus status, Long orderId, Long storeId) {
        var sql = new StringBuilder("SELECT * FROM rental_bill WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND bill_status = ?");
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
        return jdbcTemplate.query(sql.toString(), billMapper, params.toArray());
    }

    public Optional<RentalBill> findExisting(Long orderId, BillType billType, Integer periodNo) {
        var list = jdbcTemplate.query("""
            SELECT * FROM rental_bill
            WHERE order_id = ? AND bill_type = ? AND period_no = ?
            """, billMapper, orderId, billType.name(), periodNo);
        return list.stream().findFirst();
    }

    public Optional<RentalBill> findBill(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_bill WHERE id = ?", billMapper, id);
        return list.stream().findFirst();
    }

    public List<RentalBill> listDueBillsForDeduct(LocalDateTime now, Integer limit) {
        return jdbcTemplate.query("""
            SELECT * FROM rental_bill
            WHERE bill_status IN ('PENDING_PAYMENT', 'FAILED')
              AND due_at <= ?
              AND payable_amount > paid_amount
            ORDER BY due_at ASC, id ASC
            LIMIT ?
            """, billMapper, now, limit);
    }

    public Optional<Integer> findMaxPeriodNo(Long orderId, BillType billType) {
        var value = jdbcTemplate.queryForObject("""
            SELECT MAX(period_no) FROM rental_bill WHERE order_id = ? AND bill_type = ?
            """, Integer.class, orderId, billType.name());
        return Optional.ofNullable(value);
    }

    public RentalBill createBill(BillCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_bill
                (bill_no, order_id, user_account_id, merchant_id, store_id, bill_type, period_no,
                 bill_status, due_at, payable_amount, paid_amount, overdue_amount, remark, generated_batch_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.billNo());
            statement.setLong(2, row.orderId());
            setNullableLong(statement, 3, row.userAccountId());
            statement.setLong(4, row.merchantId());
            statement.setLong(5, row.storeId());
            statement.setString(6, row.billType().name());
            statement.setInt(7, row.periodNo());
            statement.setString(8, row.billStatus().name());
            statement.setObject(9, row.dueAt());
            statement.setBigDecimal(10, row.payableAmount());
            statement.setBigDecimal(11, row.paidAmount());
            statement.setBigDecimal(12, row.overdueAmount());
            statement.setString(13, row.remark());
            statement.setString(14, row.generatedBatchNo());
            return statement;
        }, keyHolder);
        return findBill(keyHolder.getKey().longValue()).orElseThrow();
    }

    public void addItem(Long billId, BillItemType itemType, String itemName, BigDecimal amount) {
        jdbcTemplate.update("""
            INSERT INTO rental_bill_item (bill_id, item_type, item_name, amount)
            VALUES (?, ?, ?, ?)
            """, billId, itemType.name(), itemName, amount);
    }

    public List<RentalBillItem> listItems(Long billId) {
        return jdbcTemplate.query("SELECT * FROM rental_bill_item WHERE bill_id = ? ORDER BY id", itemMapper, billId);
    }

    public List<RentalBillOperationLog> listLogs(Long billId) {
        return jdbcTemplate.query("SELECT * FROM rental_bill_operation_log WHERE bill_id = ? ORDER BY id DESC", logMapper, billId);
    }

    public RentalBill updateStatus(Long id, BillStatus status) {
        var paidAtSql = status == BillStatus.PAID ? "paid_at = COALESCE(paid_at, CURRENT_TIMESTAMP)," : "";
        var cancelledAtSql = status == BillStatus.CANCELLED ? "cancelled_at = COALESCE(cancelled_at, CURRENT_TIMESTAMP)," : "";
        jdbcTemplate.update("""
            UPDATE rental_bill
            SET bill_status = ?,
                %s
                %s
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.formatted(paidAtSql, cancelledAtSql), status.name(), id);
        return findBill(id).orElseThrow();
    }

    public RentalBill markPaid(Long id, BigDecimal paidAmount) {
        jdbcTemplate.update("""
            UPDATE rental_bill
            SET bill_status = 'PAID',
                paid_amount = ?,
                paid_at = COALESCE(paid_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, paidAmount, id);
        return findBill(id).orElseThrow();
    }

    public void addLog(Long billId, BillStatus fromStatus, BillStatus toStatus, BillOperationType operationType, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO rental_bill_operation_log
            (bill_id, from_status, to_status, operation_type, operator_account_id, remark)
            VALUES (?, ?, ?, ?, ?, ?)
            """, billId, fromStatus == null ? null : fromStatus.name(), toStatus.name(), operationType.name(), operatorAccountId, remark);
    }

    public BillGenerationBatch createBatch(String batchNo, BillGenerationType generationType, Long orderId, Integer generatedCount, String remark) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_bill_generation_batch
                (batch_no, generation_type, order_id, generated_count, remark)
                VALUES (?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, batchNo);
            statement.setString(2, generationType.name());
            setNullableLong(statement, 3, orderId);
            statement.setInt(4, generatedCount);
            statement.setString(5, remark);
            return statement;
        }, keyHolder);
        return findBatch(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<BillGenerationBatch> findBatch(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_bill_generation_batch WHERE id = ?", batchMapper, id);
        return list.stream().findFirst();
    }

    public List<BillGenerationBatch> listBatches() {
        return jdbcTemplate.query("SELECT * FROM rental_bill_generation_batch ORDER BY id DESC", batchMapper);
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

    public record BillCreateRow(
        String billNo,
        Long orderId,
        Long userAccountId,
        Long merchantId,
        Long storeId,
        BillType billType,
        Integer periodNo,
        BillStatus billStatus,
        LocalDateTime dueAt,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        BigDecimal overdueAmount,
        String remark,
        String generatedBatchNo
    ) {
    }

    private static class BillMapper implements RowMapper<RentalBill> {
        @Override
        public RentalBill mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalBill(
                rs.getLong("id"),
                rs.getString("bill_no"),
                rs.getLong("order_id"),
                getNullableLong(rs, "user_account_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                BillType.valueOf(rs.getString("bill_type")),
                rs.getInt("period_no"),
                BillStatus.valueOf(rs.getString("bill_status")),
                rs.getObject("due_at", LocalDateTime.class),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("overdue_amount"),
                rs.getObject("paid_at", LocalDateTime.class),
                rs.getObject("cancelled_at", LocalDateTime.class),
                rs.getString("remark"),
                rs.getString("generated_batch_no"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class ItemMapper implements RowMapper<RentalBillItem> {
        @Override
        public RentalBillItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalBillItem(
                rs.getLong("id"),
                rs.getLong("bill_id"),
                BillItemType.valueOf(rs.getString("item_type")),
                rs.getString("item_name"),
                rs.getBigDecimal("amount")
            );
        }
    }

    private static class LogMapper implements RowMapper<RentalBillOperationLog> {
        @Override
        public RentalBillOperationLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            var fromStatus = rs.getString("from_status");
            return new RentalBillOperationLog(
                rs.getLong("id"),
                rs.getLong("bill_id"),
                fromStatus == null ? null : BillStatus.valueOf(fromStatus),
                BillStatus.valueOf(rs.getString("to_status")),
                BillOperationType.fromDb(rs.getString("operation_type")),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class BatchMapper implements RowMapper<BillGenerationBatch> {
        @Override
        public BillGenerationBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BillGenerationBatch(
                rs.getLong("id"),
                rs.getString("batch_no"),
                BillGenerationType.valueOf(rs.getString("generation_type")),
                getNullableLong(rs, "order_id"),
                rs.getInt("generated_count"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
