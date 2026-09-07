package com.xniu.rental.externalorder.repository;

import com.xniu.rental.asset.model.AssetType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalOrderInitialInvestorAllocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExternalOrderInitialInvestorAllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InitialAllocation> listByExternalOrder(Long externalOrderId) {
        return jdbcTemplate.query("""
            SELECT external_order_id, settlement_snapshot_id, asset_type,
                   asset_id, investor_id, allocation_weight
            FROM external_order_initial_investor_allocation
            WHERE external_order_id = ?
            ORDER BY CASE asset_type
                       WHEN 'VEHICLE_FRAME' THEN 0
                       WHEN 'BATTERY' THEN 1
                       ELSE 2
                     END,
                     asset_id
            """, (rs, rowNum) -> new InitialAllocation(
            rs.getLong("external_order_id"),
            rs.getLong("settlement_snapshot_id"),
            AssetType.valueOf(rs.getString("asset_type")),
            rs.getLong("asset_id"),
            rs.getLong("investor_id"),
            rs.getBigDecimal("allocation_weight")
        ), externalOrderId);
    }

    public void createIfAbsent(InitialAllocation row) {
        jdbcTemplate.update("""
            INSERT IGNORE INTO external_order_initial_investor_allocation
            (external_order_id, settlement_snapshot_id, asset_type, asset_id,
             investor_id, allocation_weight)
            VALUES (?, ?, ?, ?, ?, ?)
            """, row.externalOrderId(), row.settlementSnapshotId(), row.assetType().name(),
            row.assetId(), row.investorId(), row.allocationWeight());
    }

    public void deleteByExternalOrder(Long externalOrderId) {
        jdbcTemplate.update(
            "DELETE FROM external_order_initial_investor_allocation WHERE external_order_id = ?",
            externalOrderId
        );
    }

    public record InitialAllocation(
        Long externalOrderId,
        Long settlementSnapshotId,
        AssetType assetType,
        Long assetId,
        Long investorId,
        BigDecimal allocationWeight
    ) {
    }
}
