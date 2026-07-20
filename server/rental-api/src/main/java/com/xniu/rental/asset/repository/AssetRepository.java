package com.xniu.rental.asset.repository;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
public class AssetRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AssetItem> mapper = new AssetMapper();

    public AssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssetItem> list(Long investorId, Long merchantId, Long storeId, AssetType assetType, AssetStatus status, String keyword) {
        var sql = new StringBuilder("SELECT * FROM asset_item WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (investorId != null) {
            sql.append(" AND investor_id = ?");
            params.add(investorId);
        }
        if (merchantId != null) {
            sql.append(" AND current_merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND current_store_id = ?");
            params.add(storeId);
        }
        if (assetType != null) {
            sql.append(" AND asset_type = ?");
            params.add(assetType.name());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (asset_code LIKE ? OR serial_no LIKE ?)");
            var like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public Optional<AssetItem> findById(Long id) {
        var assets = jdbcTemplate.query("SELECT * FROM asset_item WHERE id = ?", mapper, id);
        return assets.stream().findFirst();
    }

    public Optional<AssetItem> findBySerialNoAndType(String serialNo, AssetType assetType) {
        var assets = jdbcTemplate.query(
            "SELECT * FROM asset_item WHERE serial_no = ? AND asset_type = ?",
            mapper,
            serialNo,
            assetType.name()
        );
        return assets.stream().findFirst();
    }

    public AssetItem create(
        String assetCode,
        AssetType assetType,
        String serialNo,
        Long investorId,
        Long merchantId,
        Long storeId,
        BigDecimal purchaseAmount,
        BigDecimal maintenanceFeeAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO asset_item
                (asset_code, asset_type, serial_no, investor_id, current_merchant_id, current_store_id,
                 purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, assetCode);
            statement.setString(2, assetType.name());
            statement.setString(3, serialNo);
            statement.setLong(4, investorId);
            setNullableLong(statement, 5, merchantId);
            setNullableLong(statement, 6, storeId);
            statement.setBigDecimal(7, purchaseAmount);
            statement.setBigDecimal(8, maintenanceFeeAmount);
            setNullableBigDecimal(statement, 9, residualValue);
            if (purchasedAt == null) {
                statement.setObject(10, null);
            } else {
                statement.setObject(10, purchasedAt);
            }
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public AssetItem updateStatus(Long id, AssetStatus status, LocalDateTime now) {
        jdbcTemplate.update("""
            UPDATE asset_item
            SET status = ?,
                scrapped_at = CASE WHEN ? = 'SCRAPPED' THEN ? ELSE scrapped_at END,
                sold_at = CASE WHEN ? = 'SOLD' THEN ? ELSE sold_at END
            WHERE id = ?
            """, status.name(), status.name(), now, status.name(), now, id);
        return findById(id).orElseThrow();
    }

    public AssetItem transferStore(Long id, Long merchantId, Long storeId) {
        jdbcTemplate.update("""
            UPDATE asset_item
            SET current_merchant_id = ?, current_store_id = ?
            WHERE id = ?
            """, merchantId, storeId, id);
        return findById(id).orElseThrow();
    }

    public AssetItem changeInvestor(Long id, Long investorId) {
        jdbcTemplate.update("UPDATE asset_item SET investor_id = ? WHERE id = ?", investorId, id);
        return findById(id).orElseThrow();
    }

    public void insertOwnership(Long assetId, Long investorId, String reason) {
        jdbcTemplate.update("""
            INSERT INTO asset_ownership_history (asset_id, investor_id, change_reason)
            VALUES (?, ?, ?)
            """, assetId, investorId, reason);
    }

    public void closeActiveOwnership(Long assetId) {
        jdbcTemplate.update("""
            UPDATE asset_ownership_history
            SET ended_at = CURRENT_TIMESTAMP
            WHERE asset_id = ? AND ended_at IS NULL
            """, assetId);
    }

    public void insertLocationHistory(Long assetId, Long fromMerchantId, Long fromStoreId, Long toMerchantId, Long toStoreId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO asset_location_history
            (asset_id, from_merchant_id, from_store_id, to_merchant_id, to_store_id, remark)
            VALUES (?, ?, ?, ?, ?, ?)
            """, assetId, fromMerchantId, fromStoreId, toMerchantId, toStoreId, remark);
    }

    public void insertStatusLog(Long assetId, AssetStatus fromStatus, AssetStatus toStatus, Long operatorAccountId, String remark) {
        jdbcTemplate.update("""
            INSERT INTO asset_status_log
            (asset_id, from_status, to_status, operator_account_id, remark)
            VALUES (?, ?, ?, ?, ?)
            """, assetId, fromStatus == null ? null : fromStatus.name(), toStatus.name(), operatorAccountId, remark);
    }

    public List<AssetLogRow> listLogs(Long assetId) {
        var statusLogs = jdbcTemplate.query("""
            SELECT id, asset_id, 'STATUS' AS log_type, from_status AS from_value, to_status AS to_value, remark, created_at
            FROM asset_status_log
            WHERE asset_id = ?
            """, new AssetLogRowMapper(), assetId);
        var locationLogs = jdbcTemplate.query("""
            SELECT id, asset_id, 'LOCATION' AS log_type,
                   CONCAT(IFNULL(from_merchant_id, '-'), '/', IFNULL(from_store_id, '-')) AS from_value,
                   CONCAT(IFNULL(to_merchant_id, '-'), '/', IFNULL(to_store_id, '-')) AS to_value,
                   remark, created_at
            FROM asset_location_history
            WHERE asset_id = ?
            """, new AssetLogRowMapper(), assetId);
        var ownershipLogs = jdbcTemplate.query("""
            SELECT id, asset_id, 'OWNERSHIP' AS log_type,
                   NULL AS from_value,
                   CAST(investor_id AS CHAR) AS to_value,
                   change_reason AS remark, created_at
            FROM asset_ownership_history
            WHERE asset_id = ?
            """, new AssetLogRowMapper(), assetId);
        var rows = new ArrayList<AssetLogRow>();
        rows.addAll(statusLogs);
        rows.addAll(locationLogs);
        rows.addAll(ownershipLogs);
        rows.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return rows;
    }

    private void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setNullableBigDecimal(java.sql.PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static class AssetMapper implements RowMapper<AssetItem> {
        @Override
        public AssetItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssetItem(
                rs.getLong("id"),
                rs.getString("asset_code"),
                AssetType.valueOf(rs.getString("asset_type")),
                rs.getString("serial_no"),
                rs.getLong("investor_id"),
                getNullableLong(rs, "current_merchant_id"),
                getNullableLong(rs, "current_store_id"),
                AssetStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("purchase_amount"),
                rs.getBigDecimal("maintenance_fee_amount"),
                rs.getBigDecimal("residual_value"),
                rs.getObject("purchased_at", LocalDate.class),
                rs.getObject("scrapped_at", LocalDateTime.class),
                rs.getObject("sold_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }

        private Long getNullableLong(ResultSet rs, String column) throws SQLException {
            var value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }

    private static class AssetLogRowMapper implements RowMapper<AssetLogRow> {
        @Override
        public AssetLogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssetLogRow(
                rs.getLong("id"),
                rs.getLong("asset_id"),
                rs.getString("log_type"),
                rs.getString("from_value"),
                rs.getString("to_value"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }

    public record AssetLogRow(
        Long id,
        Long assetId,
        String logType,
        String fromValue,
        String toValue,
        String remark,
        LocalDateTime createdAt
    ) {
    }
}
