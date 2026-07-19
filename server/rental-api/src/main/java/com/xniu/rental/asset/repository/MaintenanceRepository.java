package com.xniu.rental.asset.repository;

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
public class MaintenanceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SparePartRow> partMapper = new SparePartMapper();
    private final RowMapper<SparePartStockLogRow> stockLogMapper = new StockLogMapper();
    private final RowMapper<StoreSparePartStockRow> storeStockMapper = new StoreStockMapper();
    private final RowMapper<MaintenanceRow> maintenanceMapper = new MaintenanceMapper();
    private final RowMapper<MaintenancePartRow> maintenancePartMapper = new MaintenancePartMapper();

    public MaintenanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SparePartRow> listParts(String keyword, String status) {
        var sql = new StringBuilder("SELECT * FROM spare_part_category WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (part_code LIKE ? OR part_name LIKE ? OR spec LIKE ?)");
            var like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), partMapper, params.toArray());
    }

    public Optional<SparePartRow> findPart(Long id) {
        return jdbcTemplate.query("SELECT * FROM spare_part_category WHERE id = ?", partMapper, id).stream().findFirst();
    }

    public SparePartRow createPart(String partCode, String partName, String spec, String unit, BigDecimal unitPrice, Integer initialQuantity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO spare_part_category
                (part_code, part_name, spec, unit, procurement_price, unit_price, buyback_price, stock_quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, partCode);
            statement.setString(2, partName);
            statement.setString(3, spec);
            statement.setString(4, unit);
            statement.setBigDecimal(5, unitPrice);
            statement.setBigDecimal(6, unitPrice);
            statement.setBigDecimal(7, unitPrice);
            statement.setInt(8, initialQuantity == null ? 0 : initialQuantity);
            return statement;
        }, keyHolder);
        return findPart(keyHolder.getKey().longValue()).orElseThrow();
    }

    public SparePartRow createPart(
        String partCode,
        String partName,
        String spec,
        String unit,
        BigDecimal procurementPrice,
        BigDecimal unitPrice,
        BigDecimal buybackPrice,
        Integer initialQuantity
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO spare_part_category
                (part_code, part_name, spec, unit, procurement_price, unit_price, buyback_price, stock_quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, partCode);
            statement.setString(2, partName);
            statement.setString(3, spec);
            statement.setString(4, unit);
            statement.setBigDecimal(5, procurementPrice);
            statement.setBigDecimal(6, unitPrice);
            statement.setBigDecimal(7, buybackPrice);
            statement.setInt(8, initialQuantity == null ? 0 : initialQuantity);
            return statement;
        }, keyHolder);
        return findPart(keyHolder.getKey().longValue()).orElseThrow();
    }

    public SparePartRow updatePart(Long id, String partName, String spec, String unit, BigDecimal procurementPrice, BigDecimal unitPrice, BigDecimal buybackPrice) {
        jdbcTemplate.update("""
            UPDATE spare_part_category
            SET part_name = ?, spec = ?, unit = ?, procurement_price = ?, unit_price = ?, buyback_price = ?
            WHERE id = ?
            """, partName, spec, unit, procurementPrice, unitPrice, buybackPrice, id);
        return findPart(id).orElseThrow();
    }

    public void changePlatformStock(Long partId, Integer quantityChange) {
        jdbcTemplate.update("""
            UPDATE spare_part_category
            SET stock_quantity = stock_quantity + ?
            WHERE id = ?
            """, quantityChange, partId);
    }

    public void changeStoreStock(Long merchantId, Long storeId, Long partId, Integer quantityChange, BigDecimal unitPrice) {
        jdbcTemplate.update("""
            INSERT INTO store_spare_part_stock (store_id, merchant_id, part_id, stock_quantity, avg_unit_price)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              merchant_id = VALUES(merchant_id),
              stock_quantity = stock_quantity + VALUES(stock_quantity),
              avg_unit_price = CASE WHEN VALUES(stock_quantity) > 0 THEN VALUES(avg_unit_price) ELSE avg_unit_price END
            """, storeId, merchantId, partId, quantityChange, unitPrice);
    }

    public Integer getPlatformStock(Long partId) {
        var value = jdbcTemplate.queryForObject("""
            SELECT stock_quantity
            FROM spare_part_category
            WHERE id = ?
            """, Integer.class, partId);
        return value == null ? 0 : value;
    }

    public Integer getStoreStock(Long storeId, Long partId) {
        var value = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(stock_quantity), 0)
            FROM store_spare_part_stock
            WHERE store_id = ? AND part_id = ?
            """, Integer.class, storeId, partId);
        return value == null ? 0 : value;
    }

    public void addStockLog(Long partId, Long merchantId, Long storeId, String changeType, Integer quantityChange, BigDecimal unitPrice, BigDecimal amount, String refType, Long refId, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO spare_part_stock_log
            (part_id, store_id, merchant_id, change_type, quantity_change, unit_price, amount, ref_type, ref_id, operator_account_id, remark)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, partId, storeId, merchantId, changeType, quantityChange, unitPrice, amount, refType, refId, operatorAccountId, remark);
    }

    public List<SparePartStockLogRow> listStockLogs(Long partId, Long merchantId, Long storeId) {
        var sql = new StringBuilder("""
            SELECT l.*, p.part_name, m.merchant_name, s.store_name
            FROM spare_part_stock_log l
            JOIN spare_part_category p ON p.id = l.part_id
            LEFT JOIN merchant_store s ON s.id = l.store_id
            LEFT JOIN merchant m ON m.id = COALESCE(l.merchant_id, s.merchant_id)
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (partId != null) {
            sql.append(" AND l.part_id = ?");
            params.add(partId);
        }
        if (merchantId != null) {
            sql.append(" AND COALESCE(l.merchant_id, s.merchant_id) = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND l.store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY l.id DESC");
        return jdbcTemplate.query(sql.toString(), stockLogMapper, params.toArray());
    }

    public List<StoreSparePartStockRow> listStoreStocks(Long partId, Long merchantId, Long storeId) {
        var sql = new StringBuilder("""
            SELECT
              sps.merchant_id,
              m.merchant_name,
              sps.store_id,
              s.store_name,
              sps.part_id,
              p.part_name,
              sps.stock_quantity,
              sps.avg_unit_price
            FROM store_spare_part_stock sps
            JOIN merchant_store s ON s.id = sps.store_id
            JOIN merchant m ON m.id = sps.merchant_id
            JOIN spare_part_category p ON p.id = sps.part_id
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (partId != null) {
            sql.append(" AND sps.part_id = ?");
            params.add(partId);
        }
        if (merchantId != null) {
            sql.append(" AND sps.merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND sps.store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY sps.merchant_id, sps.store_id, sps.part_id");
        return jdbcTemplate.query(sql.toString(), storeStockMapper, params.toArray());
    }

    public MaintenanceRow createMaintenance(
        String maintenanceNo,
        Long assetId,
        Long orderId,
        Long storeId,
        String maintenanceType,
        String maintenanceStatus,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        BigDecimal laborCost,
        BigDecimal externalCost,
        BigDecimal partsCost,
        BigDecimal totalCost,
        BigDecimal merchantReimbursementAmount,
        BigDecimal investorDeductAmount,
        BigDecimal customerChargeAmount,
        String responsibilityType,
        String costBearerType,
        Long costBearerId,
        Long operatorAccountId,
        String remark
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO asset_maintenance_record
                (maintenance_no, asset_id, order_id, store_id, maintenance_type, maintenance_status,
                 responsibility_type, started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
                 merchant_reimbursement_amount, investor_deduct_amount, customer_charge_amount,
                 cost_bearer_type, cost_bearer_id, operator_account_id, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, maintenanceNo);
            statement.setLong(2, assetId);
            setNullableLong(statement, 3, orderId);
            setNullableLong(statement, 4, storeId);
            statement.setString(5, maintenanceType);
            statement.setString(6, maintenanceStatus);
            statement.setString(7, responsibilityType);
            statement.setObject(8, startedAt);
            statement.setObject(9, completedAt);
            statement.setBigDecimal(10, laborCost);
            statement.setBigDecimal(11, externalCost);
            statement.setBigDecimal(12, partsCost);
            statement.setBigDecimal(13, totalCost);
            statement.setBigDecimal(14, merchantReimbursementAmount);
            statement.setBigDecimal(15, investorDeductAmount);
            statement.setBigDecimal(16, customerChargeAmount);
            statement.setString(17, costBearerType);
            setNullableLong(statement, 18, costBearerId);
            setNullableLong(statement, 19, operatorAccountId);
            statement.setString(20, remark);
            return statement;
        }, keyHolder);
        return findMaintenance(keyHolder.getKey().longValue()).orElseThrow();
    }

    public void addMaintenancePart(Long maintenanceId, Long partId, String partNameSnapshot, Integer quantity, BigDecimal unitPrice, BigDecimal totalAmount, String remark) {
        jdbcTemplate.update("""
            INSERT INTO asset_maintenance_part
            (maintenance_id, part_id, part_name_snapshot, quantity, unit_price, total_amount, remark)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, maintenanceId, partId, partNameSnapshot, quantity, unitPrice, totalAmount, remark);
    }

    public Optional<MaintenanceRow> findMaintenance(Long id) {
        return jdbcTemplate.query("""
            SELECT r.*, a.asset_code, a.asset_type, a.serial_no
            FROM asset_maintenance_record r
            JOIN asset_item a ON a.id = r.asset_id
            WHERE r.id = ?
            """, maintenanceMapper, id).stream().findFirst();
    }

    public List<MaintenanceRow> listMaintenances(Long assetId, Long orderId) {
        var sql = new StringBuilder("""
            SELECT r.*, a.asset_code, a.asset_type, a.serial_no
            FROM asset_maintenance_record r
            JOIN asset_item a ON a.id = r.asset_id
            WHERE 1 = 1
            """);
        var params = new ArrayList<Object>();
        if (assetId != null) {
            sql.append(" AND r.asset_id = ?");
            params.add(assetId);
        }
        if (orderId != null) {
            sql.append(" AND r.order_id = ?");
            params.add(orderId);
        }
        sql.append(" ORDER BY r.id DESC");
        return jdbcTemplate.query(sql.toString(), maintenanceMapper, params.toArray());
    }

    public List<MaintenancePartRow> listMaintenanceParts(Long maintenanceId) {
        return jdbcTemplate.query("SELECT * FROM asset_maintenance_part WHERE maintenance_id = ? ORDER BY id", maintenancePartMapper, maintenanceId);
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

    private static class SparePartMapper implements RowMapper<SparePartRow> {
        @Override
        public SparePartRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SparePartRow(
                rs.getLong("id"),
                rs.getString("part_code"),
                rs.getString("part_name"),
                rs.getString("spec"),
                rs.getString("unit"),
                rs.getBigDecimal("procurement_price"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("buyback_price"),
                rs.getInt("stock_quantity"),
                rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class StockLogMapper implements RowMapper<SparePartStockLogRow> {
        @Override
        public SparePartStockLogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SparePartStockLogRow(
                rs.getLong("id"),
                rs.getLong("part_id"),
                getNullableLong(rs, "merchant_id"),
                rs.getString("merchant_name"),
                getNullableLong(rs, "store_id"),
                rs.getString("store_name"),
                rs.getString("part_name"),
                rs.getString("change_type"),
                rs.getInt("quantity_change"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("amount"),
                rs.getString("ref_type"),
                getNullableLong(rs, "ref_id"),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class StoreStockMapper implements RowMapper<StoreSparePartStockRow> {
        @Override
        public StoreSparePartStockRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoreSparePartStockRow(
                rs.getLong("merchant_id"),
                rs.getString("merchant_name"),
                rs.getLong("store_id"),
                rs.getString("store_name"),
                rs.getLong("part_id"),
                rs.getString("part_name"),
                rs.getInt("stock_quantity"),
                rs.getBigDecimal("avg_unit_price")
            );
        }
    }

    private static class MaintenanceMapper implements RowMapper<MaintenanceRow> {
        @Override
        public MaintenanceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MaintenanceRow(
                rs.getLong("id"),
                rs.getString("maintenance_no"),
                rs.getLong("asset_id"),
                rs.getString("asset_code"),
                rs.getString("asset_type"),
                rs.getString("serial_no"),
                getNullableLong(rs, "order_id"),
                getNullableLong(rs, "store_id"),
                rs.getString("maintenance_type"),
                rs.getString("maintenance_status"),
                rs.getString("responsibility_type"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class),
                rs.getBigDecimal("labor_cost"),
                rs.getBigDecimal("external_cost"),
                rs.getBigDecimal("parts_cost"),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("merchant_reimbursement_amount"),
                rs.getBigDecimal("investor_deduct_amount"),
                rs.getBigDecimal("customer_charge_amount"),
                rs.getString("cost_bearer_type"),
                getNullableLong(rs, "cost_bearer_id"),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    private static class MaintenancePartMapper implements RowMapper<MaintenancePartRow> {
        @Override
        public MaintenancePartRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MaintenancePartRow(
                rs.getLong("id"),
                rs.getLong("maintenance_id"),
                rs.getLong("part_id"),
                rs.getString("part_name_snapshot"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("total_amount"),
                rs.getString("remark")
            );
        }
    }

    public record SparePartRow(
        Long id,
        String partCode,
        String partName,
        String spec,
        String unit,
        BigDecimal procurementPrice,
        BigDecimal unitPrice,
        BigDecimal buybackPrice,
        Integer stockQuantity,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }

    public record SparePartStockLogRow(
        Long id,
        Long partId,
        Long merchantId,
        String merchantName,
        Long storeId,
        String storeName,
        String partName,
        String changeType,
        Integer quantityChange,
        BigDecimal unitPrice,
        BigDecimal amount,
        String refType,
        Long refId,
        Long operatorAccountId,
        String remark,
        LocalDateTime createdAt
    ) {
    }

    public record StoreSparePartStockRow(
        Long merchantId,
        String merchantName,
        Long storeId,
        String storeName,
        Long partId,
        String partName,
        Integer stockQuantity,
        BigDecimal avgUnitPrice
    ) {
    }

    public record MaintenanceRow(
        Long id,
        String maintenanceNo,
        Long assetId,
        String assetCode,
        String assetType,
        String serialNo,
        Long orderId,
        Long storeId,
        String maintenanceType,
        String maintenanceStatus,
        String responsibilityType,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        BigDecimal laborCost,
        BigDecimal externalCost,
        BigDecimal partsCost,
        BigDecimal totalCost,
        BigDecimal merchantReimbursementAmount,
        BigDecimal investorDeductAmount,
        BigDecimal customerChargeAmount,
        String costBearerType,
        Long costBearerId,
        Long operatorAccountId,
        String remark,
        LocalDateTime createdAt
    ) {
    }

    public record MaintenancePartRow(Long id, Long maintenanceId, Long partId, String partNameSnapshot, Integer quantity, BigDecimal unitPrice, BigDecimal totalAmount, String remark) {
    }
}
