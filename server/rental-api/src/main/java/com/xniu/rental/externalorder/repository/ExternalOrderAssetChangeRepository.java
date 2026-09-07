package com.xniu.rental.externalorder.repository;

import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.externalorder.model.ExternalOrderAssetChange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalOrderAssetChangeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalOrderAssetChange> mapper = new ChangeMapper();

    public ExternalOrderAssetChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LocalDateTime currentDatabaseTime() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP(6)", LocalDateTime.class);
    }

    public ExternalOrderAssetChange create(CreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO external_order_asset_change
                (change_no, external_order_id, merchant_id, store_id, asset_type,
                 old_asset_id, old_asset_serial_no, old_investor_id,
                 new_asset_id, new_asset_serial_no, new_investor_id,
                 old_asset_result_status, effective_at, operator_account_id, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.changeNo());
            statement.setLong(2, row.externalOrderId());
            statement.setLong(3, row.merchantId());
            statement.setLong(4, row.storeId());
            statement.setString(5, row.assetType().name());
            statement.setLong(6, row.oldAssetId());
            statement.setString(7, row.oldAssetSerialNo());
            statement.setLong(8, row.oldInvestorId());
            statement.setLong(9, row.newAssetId());
            statement.setString(10, row.newAssetSerialNo());
            statement.setLong(11, row.newInvestorId());
            statement.setString(12, row.oldAssetResultStatus().name());
            statement.setObject(13, row.effectiveAt());
            statement.setLong(14, row.operatorAccountId());
            statement.setString(15, row.remark());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    public List<ExternalOrderAssetChange> listByOrderAndAssetType(Long externalOrderId, AssetType assetType) {
        return jdbcTemplate.query("""
            SELECT *
            FROM external_order_asset_change
            WHERE external_order_id = ? AND asset_type = ?
            ORDER BY effective_at, id
            """, mapper, externalOrderId, assetType.name());
    }

    public boolean existsByExternalOrder(Long externalOrderId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_asset_change
            WHERE external_order_id = ?
            """, Integer.class, externalOrderId);
        return count != null && count > 0;
    }

    public boolean existsForRenewalEventOrder(Long renewalEventId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_asset_change asset_change
            JOIN external_order_renewal_event renewal
              ON renewal.external_order_id = asset_change.external_order_id
            WHERE renewal.id = ?
            """, Integer.class, renewalEventId);
        return count != null && count > 0;
    }

    private ExternalOrderAssetChange findById(Long id) {
        return jdbcTemplate.query(
            "SELECT * FROM external_order_asset_change WHERE id = ?",
            mapper,
            id
        ).stream().findFirst().orElseThrow();
    }

    public record CreateRow(
        String changeNo,
        Long externalOrderId,
        Long merchantId,
        Long storeId,
        AssetType assetType,
        Long oldAssetId,
        String oldAssetSerialNo,
        Long oldInvestorId,
        Long newAssetId,
        String newAssetSerialNo,
        Long newInvestorId,
        AssetStatus oldAssetResultStatus,
        LocalDateTime effectiveAt,
        Long operatorAccountId,
        String remark
    ) {
    }

    private static class ChangeMapper implements RowMapper<ExternalOrderAssetChange> {
        @Override
        public ExternalOrderAssetChange mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalOrderAssetChange(
                rs.getLong("id"),
                rs.getString("change_no"),
                rs.getLong("external_order_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                AssetType.valueOf(rs.getString("asset_type")),
                rs.getLong("old_asset_id"),
                rs.getString("old_asset_serial_no"),
                rs.getLong("old_investor_id"),
                rs.getLong("new_asset_id"),
                rs.getString("new_asset_serial_no"),
                rs.getLong("new_investor_id"),
                AssetStatus.valueOf(rs.getString("old_asset_result_status")),
                rs.getObject("effective_at", LocalDateTime.class),
                rs.getLong("operator_account_id"),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
