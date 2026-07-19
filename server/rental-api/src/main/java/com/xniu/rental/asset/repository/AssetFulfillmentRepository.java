package com.xniu.rental.asset.repository;

import com.xniu.rental.asset.model.AssetChange;
import com.xniu.rental.asset.model.AssetHandover;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.model.HandoverType;
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
public class AssetFulfillmentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AssetHandover> handoverMapper = new HandoverMapper();
    private final RowMapper<AssetChange> changeMapper = new ChangeMapper();

    public AssetFulfillmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AssetHandover createHandover(HandoverCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_asset_handover
                (handover_no, order_id, merchant_id, store_id, user_account_id, handover_type,
                 frame_asset_id, battery_asset_id, frame_result_status, battery_result_status,
                 operator_account_id, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.handoverNo());
            statement.setLong(2, row.orderId());
            statement.setLong(3, row.merchantId());
            statement.setLong(4, row.storeId());
            setNullableLong(statement, 5, row.userAccountId());
            statement.setString(6, row.handoverType().name());
            setNullableLong(statement, 7, row.frameAssetId());
            setNullableLong(statement, 8, row.batteryAssetId());
            statement.setString(9, row.frameResultStatus() == null ? null : row.frameResultStatus().name());
            statement.setString(10, row.batteryResultStatus() == null ? null : row.batteryResultStatus().name());
            setNullableLong(statement, 11, row.operatorAccountId());
            statement.setString(12, row.remark());
            return statement;
        }, keyHolder);
        return findHandover(keyHolder.getKey().longValue()).orElseThrow();
    }

    public AssetChange createChange(ChangeCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_asset_change
                (change_no, order_id, merchant_id, store_id, asset_type, old_asset_id, new_asset_id,
                 old_asset_result_status, operator_account_id, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.changeNo());
            statement.setLong(2, row.orderId());
            statement.setLong(3, row.merchantId());
            statement.setLong(4, row.storeId());
            statement.setString(5, row.assetType().name());
            setNullableLong(statement, 6, row.oldAssetId());
            statement.setLong(7, row.newAssetId());
            statement.setString(8, row.oldAssetResultStatus().name());
            setNullableLong(statement, 9, row.operatorAccountId());
            statement.setString(10, row.remark());
            return statement;
        }, keyHolder);
        return findChange(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<AssetHandover> findHandover(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_asset_handover WHERE id = ?", handoverMapper, id);
        return list.stream().findFirst();
    }

    public Optional<AssetChange> findChange(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_asset_change WHERE id = ?", changeMapper, id);
        return list.stream().findFirst();
    }

    public List<AssetHandover> listHandovers(Long orderId, Long storeId, HandoverType handoverType) {
        var sql = new StringBuilder("SELECT * FROM rental_asset_handover WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        if (handoverType != null) {
            sql.append(" AND handover_type = ?");
            params.add(handoverType.name());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), handoverMapper, params.toArray());
    }

    public List<AssetChange> listChanges(Long orderId, Long storeId) {
        var sql = new StringBuilder("SELECT * FROM rental_asset_change WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), changeMapper, params.toArray());
    }

    public void startUsage(Long orderId, Long assetId, AssetType assetType, Long investorId, Long storeId, String startReason) {
        jdbcTemplate.update("""
            INSERT INTO order_asset_usage
            (order_id, asset_id, asset_type, investor_id, store_id, usage_status, start_reason)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
            """, orderId, assetId, assetType.name(), investorId, storeId, startReason);
    }

    public void closeActiveUsage(Long orderId, AssetType assetType, String endReason) {
        jdbcTemplate.update("""
            UPDATE order_asset_usage
            SET usage_status = 'ENDED',
                end_at = CURRENT_TIMESTAMP,
                end_reason = ?
            WHERE order_id = ?
              AND asset_type = ?
              AND usage_status = 'ACTIVE'
            """, endReason, orderId, assetType.name());
    }

    public void closeAllActiveUsage(Long orderId, String endReason) {
        jdbcTemplate.update("""
            UPDATE order_asset_usage
            SET usage_status = 'ENDED',
                end_at = CURRENT_TIMESTAMP,
                end_reason = ?
            WHERE order_id = ?
              AND usage_status = 'ACTIVE'
            """, endReason, orderId);
    }

    public List<OrderAssetUsageRow> listUsageByOrder(Long orderId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM order_asset_usage
            WHERE order_id = ?
            ORDER BY id
            """, (rs, rowNum) -> new OrderAssetUsageRow(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getLong("asset_id"),
            AssetType.valueOf(rs.getString("asset_type")),
            rs.getLong("investor_id"),
            rs.getLong("store_id"),
            rs.getString("usage_status"),
            rs.getObject("start_at", LocalDateTime.class),
            rs.getObject("end_at", LocalDateTime.class),
            rs.getString("start_reason"),
            rs.getString("end_reason")
        ), orderId);
    }

    public List<OrderAssetUsageRow> listUsageByOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(", ", java.util.Collections.nCopies(orderIds.size(), "?"));
        return jdbcTemplate.query(
            """
            SELECT *
            FROM order_asset_usage
            WHERE order_id IN (%s)
            ORDER BY order_id, id
            """.formatted(placeholders),
            (rs, rowNum) -> new OrderAssetUsageRow(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("asset_id"),
                AssetType.valueOf(rs.getString("asset_type")),
                rs.getLong("investor_id"),
                rs.getLong("store_id"),
                rs.getString("usage_status"),
                rs.getObject("start_at", LocalDateTime.class),
                rs.getObject("end_at", LocalDateTime.class),
                rs.getString("start_reason"),
                rs.getString("end_reason")
            ),
            orderIds.toArray()
        );
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

    private static AssetStatus getNullableStatus(ResultSet rs, String column) throws SQLException {
        var value = rs.getString(column);
        return value == null ? null : AssetStatus.valueOf(value);
    }

    public record HandoverCreateRow(
        String handoverNo,
        Long orderId,
        Long merchantId,
        Long storeId,
        Long userAccountId,
        HandoverType handoverType,
        Long frameAssetId,
        Long batteryAssetId,
        AssetStatus frameResultStatus,
        AssetStatus batteryResultStatus,
        Long operatorAccountId,
        String remark
    ) {
    }

    public record ChangeCreateRow(
        String changeNo,
        Long orderId,
        Long merchantId,
        Long storeId,
        AssetType assetType,
        Long oldAssetId,
        Long newAssetId,
        AssetStatus oldAssetResultStatus,
        Long operatorAccountId,
        String remark
    ) {
    }

    public record OrderAssetUsageRow(
        Long id,
        Long orderId,
        Long assetId,
        AssetType assetType,
        Long investorId,
        Long storeId,
        String usageStatus,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String startReason,
        String endReason
    ) {
    }

    private static class HandoverMapper implements RowMapper<AssetHandover> {
        @Override
        public AssetHandover mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssetHandover(
                rs.getLong("id"),
                rs.getString("handover_no"),
                rs.getLong("order_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                getNullableLong(rs, "user_account_id"),
                HandoverType.valueOf(rs.getString("handover_type")),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                getNullableStatus(rs, "frame_result_status"),
                getNullableStatus(rs, "battery_result_status"),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", java.time.LocalDateTime.class)
            );
        }
    }

    private static class ChangeMapper implements RowMapper<AssetChange> {
        @Override
        public AssetChange mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssetChange(
                rs.getLong("id"),
                rs.getString("change_no"),
                rs.getLong("order_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                AssetType.valueOf(rs.getString("asset_type")),
                getNullableLong(rs, "old_asset_id"),
                rs.getLong("new_asset_id"),
                AssetStatus.valueOf(rs.getString("old_asset_result_status")),
                getNullableLong(rs, "operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", java.time.LocalDateTime.class)
            );
        }
    }
}
