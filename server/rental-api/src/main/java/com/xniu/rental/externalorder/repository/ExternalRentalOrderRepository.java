package com.xniu.rental.externalorder.repository;

import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderSourcePlatform;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderLog;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingFilterRequest;
import com.xniu.rental.pricing.model.RenewalPricingRule;
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
public class ExternalRentalOrderRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalRentalOrder> orderMapper = new OrderMapper();
    private final RowMapper<ExternalRentalOrderLog> logMapper = new LogMapper();
    private final RowMapper<ExternalRentalOrderView> viewMapper = new ViewMapper();

    public ExternalRentalOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExternalRentalOrderView> list(
        ExternalRentalOrderStatus status,
        Long merchantId,
        Long storeId,
        ExternalOrderSourcePlatform sourcePlatform,
        Long storeSkuId,
        Long packageId,
        LocalDateTime rentStartedFrom,
        LocalDateTime rentStartedTo,
        LocalDateTime expectedReturnFrom,
        LocalDateTime expectedReturnTo,
        String keyword
    ) {
        var sql = new StringBuilder("""
            SELECT eo.*,
                   m.merchant_name,
                   s.store_name,
                   ss.display_name AS store_sku_display_name,
                   ps.sku_name,
                   pp.package_name,
                   fa.serial_no AS frame_asset_serial_no,
                   ba.serial_no AS battery_asset_serial_no,
                   rs.store_name AS return_store_name
            FROM external_rental_order eo
            LEFT JOIN merchant m ON m.id = eo.merchant_id
            LEFT JOIN merchant_store s ON s.id = eo.store_id
            LEFT JOIN store_sku ss ON ss.id = eo.store_sku_id
            LEFT JOIN product_sku ps ON ps.id = eo.sku_id
            LEFT JOIN product_package pp ON pp.id = eo.package_id
            LEFT JOIN asset_item fa ON fa.id = eo.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = eo.battery_asset_id
            LEFT JOIN merchant_store rs ON rs.id = eo.return_store_id
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND eo.order_status = ?");
            params.add(status.name());
        }
        if (merchantId != null) {
            sql.append(" AND eo.merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND eo.store_id = ?");
            params.add(storeId);
        }
        if (sourcePlatform != null) {
            sql.append(" AND eo.source_platform = ?");
            params.add(sourcePlatform.name());
        }
        if (storeSkuId != null) {
            sql.append(" AND eo.store_sku_id = ?");
            params.add(storeSkuId);
        }
        if (packageId != null) {
            sql.append(" AND eo.package_id = ?");
            params.add(packageId);
        }
        if (rentStartedFrom != null) {
            sql.append(" AND eo.rent_started_at >= ?");
            params.add(rentStartedFrom);
        }
        if (rentStartedTo != null) {
            sql.append(" AND eo.rent_started_at <= ?");
            params.add(rentStartedTo);
        }
        if (expectedReturnFrom != null) {
            sql.append(" AND eo.expected_return_at >= ?");
            params.add(expectedReturnFrom);
        }
        if (expectedReturnTo != null) {
            sql.append(" AND eo.expected_return_at <= ?");
            params.add(expectedReturnTo);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (
                    eo.record_no LIKE ?
                    OR eo.external_order_no LIKE ?
                    OR eo.customer_name LIKE ?
                    OR eo.customer_phone LIKE ?
                    OR fa.serial_no LIKE ?
                    OR ba.serial_no LIKE ?
                 )
                """);
            var like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY eo.id DESC");
        return jdbcTemplate.query(sql.toString(), viewMapper, params.toArray());
    }

    public List<ExternalRentalOrder> listForPricing(ExternalOrderPricingFilterRequest filter) {
        var sql = new StringBuilder("""
            SELECT eo.*
            FROM external_rental_order eo
            LEFT JOIN asset_item fa ON fa.id = eo.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = eo.battery_asset_id
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (filter.orderIds() != null && !filter.orderIds().isEmpty()) {
            sql.append(" AND eo.id IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(filter.orderIds().size(), "?")));
            sql.append(")");
            params.addAll(filter.orderIds());
        }
        if (filter.storeId() != null) {
            sql.append(" AND eo.store_id = ?");
            params.add(filter.storeId());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            sql.append(" AND eo.order_status = ?");
            params.add(filter.status());
        }
        if (filter.sourcePlatform() != null && !filter.sourcePlatform().isBlank()) {
            sql.append(" AND eo.source_platform = ?");
            params.add(filter.sourcePlatform());
        }
        if (filter.storeSkuId() != null) {
            sql.append(" AND eo.store_sku_id = ?");
            params.add(filter.storeSkuId());
        }
        if (filter.packageId() != null) {
            sql.append(" AND eo.package_id = ?");
            params.add(filter.packageId());
        }
        if (filter.rentStartedFrom() != null) {
            sql.append(" AND eo.rent_started_at >= ?");
            params.add(filter.rentStartedFrom());
        }
        if (filter.rentStartedTo() != null) {
            sql.append(" AND eo.rent_started_at <= ?");
            params.add(filter.rentStartedTo());
        }
        if (filter.expectedReturnFrom() != null) {
            sql.append(" AND eo.expected_return_at >= ?");
            params.add(filter.expectedReturnFrom());
        }
        if (filter.expectedReturnTo() != null) {
            sql.append(" AND eo.expected_return_at <= ?");
            params.add(filter.expectedReturnTo());
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            sql.append("""
                 AND (
                    eo.record_no LIKE ?
                    OR eo.external_order_no LIKE ?
                    OR eo.customer_name LIKE ?
                    OR eo.customer_phone LIKE ?
                    OR fa.serial_no LIKE ?
                    OR ba.serial_no LIKE ?
                 )
                """);
            var like = "%" + filter.keyword().trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY eo.id");
        return jdbcTemplate.query(sql.toString(), orderMapper, params.toArray());
    }

    public Optional<ExternalRentalOrder> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM external_rental_order WHERE id = ?", orderMapper, id);
        return list.stream().findFirst();
    }

    public Optional<ExternalRentalOrder> findByIdForUpdate(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM external_rental_order WHERE id = ? FOR UPDATE", orderMapper, id);
        return list.stream().findFirst();
    }

    public ExternalRentalOrder advanceExpectedReturnAt(Long id, LocalDateTime expectedReturnAt) {
        jdbcTemplate.update(
            "UPDATE external_rental_order SET expected_return_at = ? WHERE id = ?",
            expectedReturnAt,
            id
        );
        return findById(id).orElseThrow();
    }

    public List<Long> listIdsWithoutSettlementSnapshot() {
        return jdbcTemplate.queryForList("""
            SELECT id
            FROM external_rental_order
            WHERE settlement_snapshot_id IS NULL
            ORDER BY id
            """, Long.class);
    }

    public Optional<ExternalRentalOrderView> findViewById(Long id) {
        var list = jdbcTemplate.query("""
            SELECT eo.*,
                   m.merchant_name,
                   s.store_name,
                   ss.display_name AS store_sku_display_name,
                   ps.sku_name,
                   pp.package_name,
                   fa.serial_no AS frame_asset_serial_no,
                   ba.serial_no AS battery_asset_serial_no,
                   rs.store_name AS return_store_name
            FROM external_rental_order eo
            LEFT JOIN merchant m ON m.id = eo.merchant_id
            LEFT JOIN merchant_store s ON s.id = eo.store_id
            LEFT JOIN store_sku ss ON ss.id = eo.store_sku_id
            LEFT JOIN product_sku ps ON ps.id = eo.sku_id
            LEFT JOIN product_package pp ON pp.id = eo.package_id
            LEFT JOIN asset_item fa ON fa.id = eo.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = eo.battery_asset_id
            LEFT JOIN merchant_store rs ON rs.id = eo.return_store_id
            WHERE eo.id = ?
            """, viewMapper, id);
        return list.stream().findFirst();
    }

    public Optional<ExternalRentalOrder> findActiveByAsset(Long assetId) {
        var list = jdbcTemplate.query("""
            SELECT *
            FROM external_rental_order
            WHERE order_status = 'ACTIVE'
              AND (frame_asset_id = ? OR battery_asset_id = ?)
            ORDER BY id DESC
            LIMIT 1
            """, orderMapper, assetId, assetId);
        return list.stream().findFirst();
    }

    public boolean existsOtherActiveByAsset(Long assetId, Long excludedOrderId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_rental_order
            WHERE order_status = 'ACTIVE'
              AND id <> ?
              AND (frame_asset_id = ? OR battery_asset_id = ?)
            """, Integer.class, excludedOrderId, assetId, assetId);
        return count != null && count > 0;
    }

    public List<ExternalRentalOrderView> listByAsset(Long assetId) {
        return jdbcTemplate.query("""
            SELECT eo.*,
                   m.merchant_name,
                   s.store_name,
                   ss.display_name AS store_sku_display_name,
                   ps.sku_name,
                   pp.package_name,
                   fa.serial_no AS frame_asset_serial_no,
                   ba.serial_no AS battery_asset_serial_no,
                   rs.store_name AS return_store_name
            FROM external_rental_order eo
            LEFT JOIN merchant m ON m.id = eo.merchant_id
            LEFT JOIN merchant_store s ON s.id = eo.store_id
            LEFT JOIN store_sku ss ON ss.id = eo.store_sku_id
            LEFT JOIN product_sku ps ON ps.id = eo.sku_id
            LEFT JOIN product_package pp ON pp.id = eo.package_id
            LEFT JOIN asset_item fa ON fa.id = eo.frame_asset_id
            LEFT JOIN asset_item ba ON ba.id = eo.battery_asset_id
            LEFT JOIN merchant_store rs ON rs.id = eo.return_store_id
            WHERE eo.frame_asset_id = ? OR eo.battery_asset_id = ?
            ORDER BY eo.id DESC
            """, viewMapper, assetId, assetId);
    }

    public ExternalRentalOrder create(CreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO external_rental_order
                (record_no, source_platform, external_order_no, merchant_id, store_id, store_sku_id, sku_id, package_id,
                customer_name, customer_phone, frame_asset_id, battery_asset_id, order_status,
                 external_rental_amount, verification_amount, sign_fee_amount, deposit_amount, lease_unit, lease_value, total_periods, lease_multiplier,
                 auto_renew_enabled, renewal_unit, renewal_value, renewal_amount, renewal_billing_mode,
                 renewal_daily_amount, renewal_daily_cap_enabled, renewal_grace_hours, overdue_daily_amount,
                 rent_started_at, expected_return_at, remark, created_by_account_id, updated_by_account_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.recordNo());
            statement.setString(2, row.sourcePlatform().name());
            statement.setString(3, row.externalOrderNo());
            statement.setLong(4, row.merchantId());
            statement.setLong(5, row.storeId());
            statement.setLong(6, row.storeSkuId());
            statement.setLong(7, row.skuId());
            statement.setLong(8, row.packageId());
            statement.setString(9, row.customerName());
            statement.setString(10, row.customerPhone());
            setNullableLong(statement, 11, row.frameAssetId());
            setNullableLong(statement, 12, row.batteryAssetId());
            statement.setString(13, row.orderStatus().name());
            statement.setBigDecimal(14, row.externalRentalAmount());
            statement.setBigDecimal(15, row.verificationAmount());
            statement.setBigDecimal(16, row.signFeeAmount());
            statement.setBigDecimal(17, row.depositAmount());
            statement.setString(18, row.leaseUnit());
            statement.setInt(19, row.leaseValue());
            statement.setInt(20, row.totalPeriods());
            statement.setInt(21, row.leaseMultiplier());
            statement.setBoolean(22, Boolean.TRUE.equals(row.autoRenewEnabled()));
            statement.setString(23, row.renewalUnit());
            if (row.renewalValue() == null) statement.setObject(24, null); else statement.setInt(24, row.renewalValue());
            statement.setBigDecimal(25, row.renewalAmount());
            statement.setString(26, row.renewalBillingMode());
            statement.setBigDecimal(27, row.renewalDailyAmount());
            statement.setBoolean(28, Boolean.TRUE.equals(row.renewalDailyCapEnabled()));
            statement.setInt(29, row.renewalGraceHours() == null ? 0 : row.renewalGraceHours());
            statement.setBigDecimal(30, row.overdueDailyAmount());
            statement.setObject(31, row.rentStartedAt());
            statement.setObject(32, row.expectedReturnAt());
            statement.setString(33, row.remark());
            setNullableLong(statement, 34, row.createdByAccountId());
            setNullableLong(statement, 35, row.updatedByAccountId());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public ExternalRentalOrder finish(Long id, ExternalRentalOrderStatus targetStatus, Long returnStoreId, LocalDateTime finishedAt, String terminationReason, String remark, Long updatedByAccountId) {
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET order_status = ?,
                return_store_id = ?,
                finished_at = ?,
                termination_reason = ?,
                remark = ?,
                updated_by_account_id = ?
            WHERE id = ?
            """, targetStatus.name(), returnStoreId, finishedAt, terminationReason, remark, updatedByAccountId, id);
        return findById(id).orElseThrow();
    }

    public ExternalRentalOrder update(UpdateRow row) {
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET source_platform = ?,
                external_order_no = ?,
                merchant_id = ?,
                store_id = ?,
                store_sku_id = ?,
                sku_id = ?,
                package_id = ?,
                customer_name = ?,
                customer_phone = ?,
                frame_asset_id = ?,
                battery_asset_id = ?,
                external_rental_amount = ?,
                verification_amount = ?,
                sign_fee_amount = ?,
                deposit_amount = ?,
                lease_unit = ?,
                lease_value = ?,
                total_periods = ?,
                lease_multiplier = ?,
                auto_renew_enabled = ?,
                renewal_unit = ?,
                renewal_value = ?,
                renewal_amount = ?,
                renewal_billing_mode = ?,
                renewal_daily_amount = ?,
                renewal_daily_cap_enabled = ?,
                renewal_grace_hours = ?,
                overdue_daily_amount = ?,
                rent_started_at = ?,
                expected_return_at = ?,
                remark = ?,
                updated_by_account_id = ?
            WHERE id = ?
            """,
            row.sourcePlatform().name(), row.externalOrderNo(), row.merchantId(), row.storeId(), row.storeSkuId(),
            row.skuId(), row.packageId(), row.customerName(), row.customerPhone(), row.frameAssetId(), row.batteryAssetId(),
            row.externalRentalAmount(), row.verificationAmount(), row.signFeeAmount(), row.depositAmount(), row.leaseUnit(),
            row.leaseValue(), row.totalPeriods(), row.leaseMultiplier(), row.autoRenewEnabled(), row.renewalUnit(),
            row.renewalValue(), row.renewalAmount(), row.renewalBillingMode(), row.renewalDailyAmount(),
            row.renewalDailyCapEnabled(), row.renewalGraceHours(), row.overdueDailyAmount(), row.rentStartedAt(),
            row.expectedReturnAt(), row.remark(),
            row.updatedByAccountId(), row.id()
        );
        return findById(row.id()).orElseThrow();
    }

    public ExternalRentalOrder updateRenewalPricing(Long id, RenewalPricingRule rule, Long updatedByAccountId) {
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET auto_renew_enabled = ?, renewal_unit = ?, renewal_value = ?, renewal_amount = ?,
                renewal_billing_mode = ?, renewal_daily_amount = ?, renewal_daily_cap_enabled = ?,
                renewal_grace_hours = ?, overdue_daily_amount = ?, updated_by_account_id = ?
            WHERE id = ?
            """,
            rule.autoRenewEnabled(), rule.renewalUnit(), rule.renewalValue(), rule.renewalAmount(),
            rule.renewalBillingMode().name(), rule.renewalDailyAmount(), rule.renewalDailyCapEnabled(),
            rule.renewalGraceHours(), rule.overdueDailyAmount(), updatedByAccountId, id
        );
        return findById(id).orElseThrow();
    }

    public ExternalRentalOrder updateSettlementSnapshot(Long id, Long settlementSnapshotId) {
        jdbcTemplate.update(
            "UPDATE external_rental_order SET settlement_snapshot_id = ? WHERE id = ?",
            settlementSnapshotId,
            id
        );
        return findById(id).orElseThrow();
    }

    public List<ExternalRentalOrderLog> listLogs(Long externalOrderId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM external_rental_order_log
            WHERE external_order_id = ?
            ORDER BY id DESC
            """, logMapper, externalOrderId);
    }

    public void addLog(Long externalOrderId, ExternalRentalOrderStatus fromStatus, ExternalRentalOrderStatus toStatus, ExternalOrderOperationType operationType, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO external_rental_order_log
            (external_order_id, from_status, to_status, operation_type, operator_account_id, remark)
            VALUES (?, ?, ?, ?, ?, ?)
            """, externalOrderId, fromStatus == null ? null : fromStatus.name(), toStatus.name(), operationType.name(), operatorAccountId, remark);
    }

    public void deleteLogs(Long externalOrderId) {
        jdbcTemplate.update("DELETE FROM external_rental_order_log WHERE external_order_id = ?", externalOrderId);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM external_order_pricing_revision WHERE external_order_id = ?", id);
        jdbcTemplate.update("DELETE FROM external_rental_order WHERE id = ?", id);
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

    private static class OrderMapper implements RowMapper<ExternalRentalOrder> {
        @Override
        public ExternalRentalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalRentalOrder(
                rs.getLong("id"),
                rs.getString("record_no"),
                ExternalOrderSourcePlatform.valueOf(rs.getString("source_platform")),
                rs.getString("external_order_no"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getLong("package_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                ExternalRentalOrderStatus.valueOf(rs.getString("order_status")),
                rs.getBigDecimal("external_rental_amount"),
                rs.getBigDecimal("verification_amount"),
                getNullableLong(rs, "settlement_snapshot_id"),
                rs.getBigDecimal("sign_fee_amount"),
                rs.getBigDecimal("deposit_amount"),
                rs.getString("lease_unit"),
                rs.getInt("lease_value"),
                rs.getInt("total_periods"),
                rs.getInt("lease_multiplier"),
                rs.getBoolean("auto_renew_enabled"),
                rs.getString("renewal_unit"),
                getNullableInt(rs, "renewal_value"),
                rs.getBigDecimal("renewal_amount"),
                rs.getString("renewal_billing_mode"),
                rs.getBigDecimal("renewal_daily_amount"),
                rs.getBoolean("renewal_daily_cap_enabled"),
                rs.getInt("renewal_grace_hours"),
                rs.getBigDecimal("overdue_daily_amount"),
                rs.getObject("rent_started_at", LocalDateTime.class),
                rs.getObject("expected_return_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class),
                getNullableLong(rs, "return_store_id"),
                rs.getString("termination_reason"),
                rs.getString("remark"),
                getNullableLong(rs, "created_by_account_id"),
                getNullableLong(rs, "updated_by_account_id"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class LogMapper implements RowMapper<ExternalRentalOrderLog> {
        @Override
        public ExternalRentalOrderLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            var fromStatus = rs.getString("from_status");
            return new ExternalRentalOrderLog(
                rs.getLong("id"),
                rs.getLong("external_order_id"),
                fromStatus == null ? null : ExternalRentalOrderStatus.valueOf(fromStatus),
                ExternalRentalOrderStatus.valueOf(rs.getString("to_status")),
                ExternalOrderOperationType.valueOf(rs.getString("operation_type")),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class ViewMapper implements RowMapper<ExternalRentalOrderView> {
        @Override
        public ExternalRentalOrderView mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalRentalOrderView(
                new OrderMapper().mapRow(rs, rowNum),
                rs.getString("merchant_name"),
                rs.getString("store_name"),
                rs.getString("store_sku_display_name"),
                rs.getString("sku_name"),
                rs.getString("package_name"),
                rs.getString("frame_asset_serial_no"),
                rs.getString("battery_asset_serial_no"),
                rs.getString("return_store_name")
            );
        }
    }

    public record CreateRow(
        String recordNo,
        ExternalOrderSourcePlatform sourcePlatform,
        String externalOrderNo,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        Long packageId,
        String customerName,
        String customerPhone,
        Long frameAssetId,
        Long batteryAssetId,
        ExternalRentalOrderStatus orderStatus,
        BigDecimal externalRentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        Integer leaseMultiplier,
        Boolean autoRenewEnabled,
        String renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount,
        String renewalBillingMode,
        BigDecimal renewalDailyAmount,
        Boolean renewalDailyCapEnabled,
        Integer renewalGraceHours,
        BigDecimal overdueDailyAmount,
        LocalDateTime rentStartedAt,
        LocalDateTime expectedReturnAt,
        String remark,
        Long createdByAccountId,
        Long updatedByAccountId
    ) {
    }

    public record UpdateRow(
        Long id,
        ExternalOrderSourcePlatform sourcePlatform,
        String externalOrderNo,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        Long skuId,
        Long packageId,
        String customerName,
        String customerPhone,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal externalRentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        Integer leaseMultiplier,
        Boolean autoRenewEnabled,
        String renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount,
        String renewalBillingMode,
        BigDecimal renewalDailyAmount,
        Boolean renewalDailyCapEnabled,
        Integer renewalGraceHours,
        BigDecimal overdueDailyAmount,
        LocalDateTime rentStartedAt,
        LocalDateTime expectedReturnAt,
        String remark,
        Long updatedByAccountId
    ) {
    }

    public record ExternalRentalOrderView(
        ExternalRentalOrder order,
        String merchantName,
        String storeName,
        String storeSkuDisplayName,
        String skuName,
        String packageName,
        String frameAssetSerialNo,
        String batteryAssetSerialNo,
        String returnStoreName
    ) {
    }
}
