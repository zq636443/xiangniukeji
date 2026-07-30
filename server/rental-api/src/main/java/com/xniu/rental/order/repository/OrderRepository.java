package com.xniu.rental.order.repository;

import com.xniu.rental.order.model.OrderItemType;
import com.xniu.rental.order.model.OrderLeaseBonus;
import com.xniu.rental.order.model.OrderLeaseBonusType;
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
    private final RowMapper<OrderLeaseBonus> leaseBonusMapper = new LeaseBonusMapper();
    private final RowMapper<RentalOrderOperationLog> logMapper = new LogMapper();

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RentalOrder> list(OrderStatus status, Long storeId, Long userAccountId, String keyword) {
        var sql = new StringBuilder("""
            SELECT o.*
            FROM rental_order o
            LEFT JOIN merchant_store ms ON ms.id = o.store_id
            LEFT JOIN store_sku ss ON ss.id = o.store_sku_id
            LEFT JOIN product_package pp ON pp.id = o.package_id
            LEFT JOIN asset_item fa ON fa.id = o.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = o.battery_asset_id
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND o.order_status = ?");
            params.add(status.name());
        }
        if (storeId != null) {
            sql.append(" AND o.store_id = ?");
            params.add(storeId);
        }
        if (userAccountId != null) {
            sql.append(" AND o.user_account_id = ?");
            params.add(userAccountId);
        }
        if (keyword != null && !keyword.isBlank()) {
            var like = "%" + keyword.trim() + "%";
            sql.append("""
                 AND (o.order_no LIKE ?
                      OR o.customer_name LIKE ?
                      OR o.customer_phone LIKE ?
                      OR CAST(o.user_account_id AS CHAR) LIKE ?
                      OR ms.store_name LIKE ?
                      OR ss.display_name LIKE ?
                      OR pp.package_name LIKE ?
                      OR fa.asset_code LIKE ?
                      OR fa.serial_no LIKE ?
                      OR ba.asset_code LIKE ?
                      OR ba.serial_no LIKE ?)
                """);
            for (int index = 0; index < 11; index++) {
                params.add(like);
            }
        }
        sql.append(" ORDER BY o.id DESC");
        return jdbcTemplate.query(sql.toString(), orderMapper, params.toArray());
    }

    public Optional<RentalOrder> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_order WHERE id = ?", orderMapper, id);
        return list.stream().findFirst();
    }

    public Optional<RentalOrder> findByIdForUpdate(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_order WHERE id = ? FOR UPDATE", orderMapper, id);
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
                (order_no, user_account_id, customer_name, customer_phone,
                 merchant_id, store_id, store_sku_id, sku_id, package_id,
                 frame_asset_id, battery_asset_id, order_status, rental_amount, verification_amount,
                 sign_fee_amount, deposit_amount, payable_amount, paid_amount, settlement_snapshot_id,
                 lease_unit, lease_value, total_periods, lease_multiplier, bill_day_mode, bill_day, ordered_at,
                 auto_renew_enabled, renewal_unit, renewal_value, renewal_amount, renewal_billing_mode,
                 renewal_daily_amount, renewal_daily_cap_enabled, renewal_grace_hours, overdue_daily_amount,
                 expected_pickup_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.orderNo());
            setNullableLong(statement, 2, row.userAccountId());
            statement.setString(3, row.customerName());
            statement.setString(4, row.customerPhone());
            statement.setLong(5, row.merchantId());
            statement.setLong(6, row.storeId());
            statement.setLong(7, row.storeSkuId());
            statement.setLong(8, row.skuId());
            statement.setLong(9, row.packageId());
            setNullableLong(statement, 10, row.frameAssetId());
            setNullableLong(statement, 11, row.batteryAssetId());
            statement.setString(12, row.orderStatus().name());
            statement.setBigDecimal(13, row.rentalAmount());
            statement.setBigDecimal(14, row.verificationAmount());
            statement.setBigDecimal(15, row.signFeeAmount());
            statement.setBigDecimal(16, row.depositAmount());
            statement.setBigDecimal(17, row.payableAmount());
            statement.setBigDecimal(18, row.paidAmount());
            setNullableLong(statement, 19, row.settlementSnapshotId());
            statement.setString(20, row.leaseUnit());
            statement.setInt(21, row.leaseValue());
            statement.setInt(22, row.totalPeriods());
            statement.setInt(23, row.leaseMultiplier());
            statement.setString(24, row.billDayMode());
            if (row.billDay() == null) {
                statement.setObject(25, null);
            } else {
                statement.setInt(25, row.billDay());
            }
            statement.setObject(26, row.orderedAt());
            statement.setBoolean(27, Boolean.TRUE.equals(row.autoRenewEnabled()));
            statement.setString(28, row.renewalUnit());
            if (row.renewalValue() == null) {
                statement.setObject(29, null);
            } else {
                statement.setInt(29, row.renewalValue());
            }
            statement.setBigDecimal(30, row.renewalAmount());
            statement.setString(31, row.renewalBillingMode());
            statement.setBigDecimal(32, row.renewalDailyAmount());
            statement.setBoolean(33, Boolean.TRUE.equals(row.renewalDailyCapEnabled()));
            statement.setInt(34, row.renewalGraceHours() == null ? 0 : row.renewalGraceHours());
            statement.setBigDecimal(35, row.overdueDailyAmount());
            statement.setObject(36, row.expectedPickupAt());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<OrderDisplayRow> findDisplayInfo(Long orderId) {
        var rows = jdbcTemplate.query("""
            SELECT ms.store_name,
                   ss.display_name AS store_sku_name,
                   pp.package_name,
                   fa.asset_code AS frame_asset_code,
                   fa.serial_no AS frame_serial_no,
                   ba.asset_code AS battery_asset_code,
                   ba.serial_no AS battery_serial_no
            FROM rental_order o
            LEFT JOIN merchant_store ms ON ms.id = o.store_id
            LEFT JOIN store_sku ss ON ss.id = o.store_sku_id
            LEFT JOIN product_package pp ON pp.id = o.package_id
            LEFT JOIN asset_item fa ON fa.id = o.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = o.battery_asset_id
            WHERE o.id = ?
            """, (rs, rowNum) -> new OrderDisplayRow(
                rs.getString("store_name"),
                rs.getString("store_sku_name"),
                rs.getString("package_name"),
                rs.getString("frame_asset_code"),
                rs.getString("frame_serial_no"),
                rs.getString("battery_asset_code"),
                rs.getString("battery_serial_no")
            ), orderId);
        return rows.stream().findFirst();
    }

    public void addItem(Long orderId, OrderItemType itemType, Long refId, String itemName, Integer quantity, BigDecimal unitAmount, BigDecimal totalAmount) {
        jdbcTemplate.update("""
            INSERT INTO rental_order_item
            (order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, orderId, itemType.name(), refId, itemName, quantity, unitAmount, totalAmount);
    }

    public void deleteItems(Long orderId) {
        jdbcTemplate.update("DELETE FROM rental_order_item WHERE order_id = ?", orderId);
    }

    public List<RentalOrderItem> listItems(Long orderId) {
        return jdbcTemplate.query("SELECT * FROM rental_order_item WHERE order_id = ? ORDER BY id", itemMapper, orderId);
    }

    public List<RentalOrderOperationLog> listLogs(Long orderId) {
        return jdbcTemplate.query("SELECT * FROM rental_order_operation_log WHERE order_id = ? ORDER BY id DESC", logMapper, orderId);
    }

    public List<OrderLeaseBonus> listLeaseBonuses(Long orderId) {
        return jdbcTemplate.query(
            "SELECT * FROM rental_order_lease_bonus WHERE order_id = ? ORDER BY id DESC",
            leaseBonusMapper,
            orderId
        );
    }

    public LeaseBonusSummary summarizeLeaseBonuses(Long orderId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN bonus_type = 'REVIEW' THEN bonus_days ELSE 0 END), 0) AS review_days,
                   COALESCE(SUM(CASE WHEN bonus_type = 'CAMPAIGN' THEN bonus_days ELSE 0 END), 0) AS campaign_days,
                   COALESCE(SUM(bonus_days), 0) AS total_days
            FROM rental_order_lease_bonus
            WHERE order_id = ?
            """, (rs, rowNum) -> new LeaseBonusSummary(
            rs.getInt("review_days"),
            rs.getInt("campaign_days"),
            rs.getInt("total_days")
        ), orderId);
    }

    public OrderLeaseBonus addLeaseBonus(LeaseBonusCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_order_lease_bonus
                (order_id, bonus_type, bonus_days, operator_account_id, remark,
                 expected_return_before, expected_return_after)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, row.orderId());
            statement.setString(2, row.bonusType().name());
            statement.setInt(3, row.bonusDays());
            setNullableLong(statement, 4, row.operatorAccountId());
            statement.setString(5, row.remark());
            statement.setObject(6, row.expectedReturnBefore());
            statement.setObject(7, row.expectedReturnAfter());
            return statement;
        }, keyHolder);
        return jdbcTemplate.queryForObject(
            "SELECT * FROM rental_order_lease_bonus WHERE id = ?",
            leaseBonusMapper,
            keyHolder.getKey().longValue()
        );
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

    public RentalOrder updateEditableDetails(EditableOrderRow row) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET user_account_id = ?,
                customer_name = ?,
                customer_phone = ?,
                merchant_id = ?,
                store_id = ?,
                store_sku_id = ?,
                sku_id = ?,
                package_id = ?,
                frame_asset_id = ?,
                battery_asset_id = ?,
                rental_amount = ?,
                verification_amount = ?,
                sign_fee_amount = ?,
                deposit_amount = ?,
                payable_amount = ?,
                lease_unit = ?,
                lease_value = ?,
                total_periods = ?,
                lease_multiplier = ?,
                bill_day_mode = ?,
                bill_day = ?,
                ordered_at = ?,
                auto_renew_enabled = ?,
                renewal_unit = ?,
                renewal_value = ?,
                renewal_amount = ?,
                renewal_billing_mode = ?,
                renewal_daily_amount = ?,
                renewal_daily_cap_enabled = ?,
                renewal_grace_hours = ?,
                overdue_daily_amount = ?
            WHERE id = ?
            """,
            row.userAccountId(),
            row.customerName(),
            row.customerPhone(),
            row.merchantId(),
            row.storeId(),
            row.storeSkuId(),
            row.skuId(),
            row.packageId(),
            row.frameAssetId(),
            row.batteryAssetId(),
            row.rentalAmount(),
            row.verificationAmount(),
            row.signFeeAmount(),
            row.depositAmount(),
            row.payableAmount(),
            row.leaseUnit(),
            row.leaseValue(),
            row.totalPeriods(),
            row.leaseMultiplier(),
            row.billDayMode(),
            row.billDay(),
            row.orderedAt(),
            row.autoRenewEnabled(),
            row.renewalUnit(),
            row.renewalValue(),
            row.renewalAmount(),
            row.renewalBillingMode(),
            row.renewalDailyAmount(),
            row.renewalDailyCapEnabled(),
            row.renewalGraceHours(),
            row.overdueDailyAmount(),
            row.id()
        );
        return findById(row.id()).orElseThrow();
    }

    public RentalOrder updateLeaseBonusDeadline(Long id, LocalDateTime expectedReturnAt, OrderStatus targetStatus) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET expected_return_at = ?, order_status = ?
            WHERE id = ?
            """, expectedReturnAt, targetStatus.name(), id);
        return findById(id).orElseThrow();
    }

    public RentalOrder updateRenewalPricing(Long id, com.xniu.rental.pricing.model.RenewalPricingRule rule) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET auto_renew_enabled = ?, renewal_unit = ?, renewal_value = ?, renewal_amount = ?,
                renewal_billing_mode = ?, renewal_daily_amount = ?, renewal_daily_cap_enabled = ?,
                renewal_grace_hours = ?, overdue_daily_amount = ?
            WHERE id = ?
            """,
            rule.autoRenewEnabled(), rule.renewalUnit(), rule.renewalValue(), rule.renewalAmount(),
            rule.renewalBillingMode().name(), rule.renewalDailyAmount(), rule.renewalDailyCapEnabled(),
            rule.renewalGraceHours(), rule.overdueDailyAmount(), id
        );
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

    public List<RentalOrder> listDueForAutoRenewal(LocalDateTime now, Integer limit) {
        return jdbcTemplate.query("""
            SELECT *
            FROM rental_order
            WHERE auto_renew_enabled = 1
              AND returned_at IS NULL
              AND expected_return_at IS NOT NULL
              AND expected_return_at <= ?
              AND order_status IN ('RENTING', 'PENDING_RETURN', 'OVERDUE')
            ORDER BY expected_return_at ASC, id ASC
            LIMIT ?
            """, orderMapper, now, limit);
    }

    public RentalOrder applyRenewalSuccess(Long id, LocalDateTime nextExpectedReturnAt, boolean backToRenting) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET expected_return_at = ?,
                renewal_count = renewal_count + 1,
                order_status = CASE
                  WHEN ? = 1 AND order_status IN ('OVERDUE', 'PENDING_SUPPLEMENT', 'PENDING_RETURN') THEN 'RENTING'
                  ELSE order_status
                END
            WHERE id = ?
              AND order_status NOT IN ('COMPLETED', 'CANCELLED', 'EXCEPTION')
            """, nextExpectedReturnAt, backToRenting ? 1 : 0, id);
        return findById(id).orElseThrow();
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
        String customerName,
        String customerPhone,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        OrderStatus orderStatus,
        BigDecimal rentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        Long settlementSnapshotId,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        Integer leaseMultiplier,
        String billDayMode,
        Integer billDay,
        LocalDateTime orderedAt,
        Boolean autoRenewEnabled,
        String renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount,
        String renewalBillingMode,
        BigDecimal renewalDailyAmount,
        Boolean renewalDailyCapEnabled,
        Integer renewalGraceHours,
        BigDecimal overdueDailyAmount,
        LocalDateTime expectedPickupAt
    ) {
    }

    public record EditableOrderRow(
        Long id,
        Long userAccountId,
        String customerName,
        String customerPhone,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal rentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        BigDecimal payableAmount,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        Integer leaseMultiplier,
        String billDayMode,
        Integer billDay,
        LocalDateTime orderedAt,
        Boolean autoRenewEnabled,
        String renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount,
        String renewalBillingMode,
        BigDecimal renewalDailyAmount,
        Boolean renewalDailyCapEnabled,
        Integer renewalGraceHours,
        BigDecimal overdueDailyAmount
    ) {
    }

    public record OrderDisplayRow(
        String storeName,
        String storeSkuName,
        String packageName,
        String frameAssetCode,
        String frameSerialNo,
        String batteryAssetCode,
        String batterySerialNo
    ) {
    }

    public record LeaseBonusCreateRow(
        Long orderId,
        OrderLeaseBonusType bonusType,
        Integer bonusDays,
        Long operatorAccountId,
        String remark,
        LocalDateTime expectedReturnBefore,
        LocalDateTime expectedReturnAfter
    ) {
    }

    public record LeaseBonusSummary(
        Integer reviewDays,
        Integer campaignDays,
        Integer totalDays
    ) {
    }

    private static class OrderMapper implements RowMapper<RentalOrder> {
        @Override
        public RentalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalOrder(
                rs.getLong("id"),
                rs.getString("order_no"),
                getNullableLong(rs, "user_account_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getLong("package_id"),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                OrderStatus.valueOf(rs.getString("order_status")),
                rs.getBigDecimal("rental_amount"),
                rs.getBigDecimal("verification_amount"),
                rs.getBigDecimal("sign_fee_amount"),
                rs.getBigDecimal("deposit_amount"),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("paid_amount"),
                getNullableLong(rs, "settlement_snapshot_id"),
                rs.getString("lease_unit"),
                rs.getInt("lease_value"),
                rs.getInt("total_periods"),
                rs.getInt("lease_multiplier"),
                rs.getString("bill_day_mode"),
                getNullableInt(rs, "bill_day"),
                rs.getObject("ordered_at", LocalDateTime.class),
                rs.getBoolean("auto_renew_enabled"),
                rs.getString("renewal_unit"),
                getNullableInt(rs, "renewal_value"),
                rs.getBigDecimal("renewal_amount"),
                rs.getString("renewal_billing_mode"),
                rs.getBigDecimal("renewal_daily_amount"),
                rs.getBoolean("renewal_daily_cap_enabled"),
                rs.getInt("renewal_grace_hours"),
                rs.getBigDecimal("overdue_daily_amount"),
                rs.getInt("renewal_count"),
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

    private static class LeaseBonusMapper implements RowMapper<OrderLeaseBonus> {
        @Override
        public OrderLeaseBonus mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OrderLeaseBonus(
                rs.getLong("id"),
                rs.getLong("order_id"),
                OrderLeaseBonusType.valueOf(rs.getString("bonus_type")),
                rs.getInt("bonus_days"),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("expected_return_before", LocalDateTime.class),
                rs.getObject("expected_return_after", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
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
