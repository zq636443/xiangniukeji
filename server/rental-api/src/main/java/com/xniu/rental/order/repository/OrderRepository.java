package com.xniu.rental.order.repository;

import com.xniu.rental.order.model.OrderItemType;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.model.RentalOrderItem;
import com.xniu.rental.order.model.RentalOrderOperationLog;
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
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RentalOrder> orderMapper = new OrderMapper();
    private final RowMapper<RentalOrderItem> itemMapper = new ItemMapper();
    private final RowMapper<RentalOrderOperationLog> logMapper = new LogMapper();

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RentalOrder> list(OrderStatus status, Long storeId, Long userAccountId) {
        var sql = new StringBuilder("SELECT * FROM rental_order WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND order_status = ?");
            params.add(status.name());
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), orderMapper, params.toArray());
    }

    public Optional<RentalOrder> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_order WHERE id = ?", orderMapper, id);
        return list.stream().findFirst();
    }

    public List<RentalOrder> listByAsset(Long assetId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM rental_order
            WHERE frame_asset_id = ? OR battery_asset_id = ?
            ORDER BY id DESC
            """, orderMapper, assetId, assetId);
    }

    public RentalOrder create(OrderCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_order
                (order_no, user_account_id, merchant_id, store_id, store_sku_id, sku_id, package_id,
                 frame_asset_id, battery_asset_id, order_status, rental_amount, sign_fee_amount,
                 deposit_amount, payable_amount, paid_amount, settlement_snapshot_id,
                 lease_unit, lease_value, total_periods, bill_day_mode, bill_day, expected_pickup_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.orderNo());
            setNullableLong(statement, 2, row.userAccountId());
            statement.setLong(3, row.merchantId());
            statement.setLong(4, row.storeId());
            statement.setLong(5, row.storeSkuId());
            statement.setLong(6, row.skuId());
            statement.setLong(7, row.packageId());
            setNullableLong(statement, 8, row.frameAssetId());
            setNullableLong(statement, 9, row.batteryAssetId());
            statement.setString(10, row.orderStatus().name());
            statement.setBigDecimal(11, row.rentalAmount());
            statement.setBigDecimal(12, row.signFeeAmount());
            statement.setBigDecimal(13, row.depositAmount());
            statement.setBigDecimal(14, row.payableAmount());
            statement.setBigDecimal(15, row.paidAmount());
            setNullableLong(statement, 16, row.settlementSnapshotId());
            statement.setString(17, row.leaseUnit());
            statement.setInt(18, row.leaseValue());
            statement.setInt(19, row.totalPeriods());
            statement.setString(20, row.billDayMode());
            if (row.billDay() == null) {
                statement.setObject(21, null);
            } else {
                statement.setInt(21, row.billDay());
            }
            statement.setObject(22, row.expectedPickupAt());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public void addItem(Long orderId, OrderItemType itemType, Long refId, String itemName, Integer quantity, BigDecimal unitAmount, BigDecimal totalAmount) {
        jdbcTemplate.update("""
            INSERT INTO rental_order_item
            (order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, orderId, itemType.name(), refId, itemName, quantity, unitAmount, totalAmount);
    }

    public List<RentalOrderItem> listItems(Long orderId) {
        return jdbcTemplate.query("SELECT * FROM rental_order_item WHERE order_id = ? ORDER BY id", itemMapper, orderId);
    }

    public List<RentalOrderOperationLog> listLogs(Long orderId) {
        return jdbcTemplate.query("SELECT * FROM rental_order_operation_log WHERE order_id = ? ORDER BY id DESC", logMapper, orderId);
    }

    public RentalOrder updateStatus(Long id, OrderStatus targetStatus, LocalDateTime leaseStartedAt, LocalDateTime expectedReturnAt, LocalDateTime returnedAt) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = ?,
                lease_started_at = COALESCE(lease_started_at, ?),
                expected_return_at = COALESCE(expected_return_at, ?),
                returned_at = COALESCE(returned_at, ?)
            WHERE id = ?
            """, targetStatus.name(), leaseStartedAt, expectedReturnAt, returnedAt, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder updateSettlementSnapshot(Long id, Long settlementSnapshotId) {
        jdbcTemplate.update("UPDATE rental_order SET settlement_snapshot_id = ? WHERE id = ?", settlementSnapshotId, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder increasePaidAmount(Long id, BigDecimal amount) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET paid_amount = paid_amount + ?
            WHERE id = ?
            """, amount, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder updateAssets(Long id, Long frameAssetId, Long batteryAssetId) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET frame_asset_id = ?, battery_asset_id = ?
            WHERE id = ?
            """, frameAssetId, batteryAssetId, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder completeReturn(Long id, LocalDateTime returnedAt) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = 'COMPLETED',
                returned_at = COALESCE(returned_at, ?)
            WHERE id = ?
            """, returnedAt, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder cancel(Long id, String reason) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP, cancel_reason = ?
            WHERE id = ?
            """, reason, id);
        return findById(id).orElseThrow();
    }

    public RentalOrder markException(Long id, String reason) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = 'EXCEPTION', exception_reason = ?
            WHERE id = ?
            """, reason, id);
        return findById(id).orElseThrow();
    }

    public void addLog(Long orderId, OrderStatus fromStatus, OrderStatus toStatus, OrderOperationType operationType, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO rental_order_operation_log
            (order_id, from_status, to_status, operation_type, operator_account_id, remark)
            VALUES (?, ?, ?, ?, ?, ?)
            """, orderId, fromStatus == null ? null : fromStatus.name(), toStatus.name(), operationType.name(), operatorAccountId, remark);
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

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record OrderCreateRow(
        String orderNo,
        Long userAccountId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        OrderStatus orderStatus,
        BigDecimal rentalAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        Long settlementSnapshotId,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        String billDayMode,
        Integer billDay,
        LocalDateTime expectedPickupAt
    ) {
    }

    private static class OrderMapper implements RowMapper<RentalOrder> {
        @Override
        public RentalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalOrder(
                rs.getLong("id"),
                rs.getString("order_no"),
                getNullableLong(rs, "user_account_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getLong("package_id"),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                OrderStatus.valueOf(rs.getString("order_status")),
                rs.getBigDecimal("rental_amount"),
                rs.getBigDecimal("sign_fee_amount"),
                rs.getBigDecimal("deposit_amount"),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                getNullableLong(rs, "settlement_snapshot_id"),
                rs.getString("lease_unit"),
                rs.getInt("lease_value"),
                rs.getInt("total_periods"),
                rs.getString("bill_day_mode"),
                getNullableInt(rs, "bill_day"),
                rs.getObject("expected_pickup_at", LocalDateTime.class),
                rs.getObject("lease_started_at", LocalDateTime.class),
                rs.getObject("expected_return_at", LocalDateTime.class),
                rs.getObject("returned_at", LocalDateTime.class),
                rs.getObject("cancelled_at", LocalDateTime.class),
                rs.getString("cancel_reason"),
                rs.getString("exception_reason"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class ItemMapper implements RowMapper<RentalOrderItem> {
        @Override
        public RentalOrderItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalOrderItem(
                rs.getLong("id"),
                rs.getLong("order_id"),
                OrderItemType.valueOf(rs.getString("item_type")),
                getNullableLong(rs, "ref_id"),
                rs.getString("item_name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_amount"),
                rs.getBigDecimal("total_amount")
            );
        }
    }

    private static class LogMapper implements RowMapper<RentalOrderOperationLog> {
        @Override
        public RentalOrderOperationLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            var from = rs.getString("from_status");
            return new RentalOrderOperationLog(
                rs.getLong("id"),
                rs.getLong("order_id"),
                from == null ? null : OrderStatus.valueOf(from),
                OrderStatus.valueOf(rs.getString("to_status")),
                OrderOperationType.valueOf(rs.getString("operation_type")),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
