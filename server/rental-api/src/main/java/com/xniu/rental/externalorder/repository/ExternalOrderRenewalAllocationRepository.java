package com.xniu.rental.externalorder.repository;

import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalInvestorAllocation;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalOrderRenewalAllocationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalOrderRenewalInvestorAllocation> mapper = new AllocationMapper();

    public ExternalOrderRenewalAllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(CreateRow row) {
        jdbcTemplate.update("""
            INSERT INTO external_order_renewal_investor_allocation
            (renewal_event_id, settlement_snapshot_id, asset_type, asset_id, investor_id,
             effective_start_at, effective_end_at, allocation_weight,
             rent_base_amount, investor_share_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            row.renewalEventId(), row.settlementSnapshotId(), row.assetType().name(),
            row.assetId(), row.investorId(), row.effectiveStartAt(), row.effectiveEndAt(),
            row.allocationWeight(), row.rentBaseAmount(), row.investorShareAmount()
        );
    }

    public List<ExternalOrderRenewalInvestorAllocation> listBySnapshot(Long snapshotId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            ORDER BY asset_type, effective_start_at, id
            """, mapper, snapshotId);
    }

    public List<InvestorAllocationTotal> listTotalsBySnapshot(Long snapshotId) {
        return jdbcTemplate.query("""
            SELECT investor_id,
                   SUM(rent_base_amount) AS rent_base_amount,
                   SUM(investor_share_amount) AS investor_share_amount
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            GROUP BY investor_id
            ORDER BY investor_id
            """, (rs, rowNum) -> new InvestorAllocationTotal(
            rs.getLong("investor_id"),
            rs.getBigDecimal("rent_base_amount"),
            rs.getBigDecimal("investor_share_amount")
        ), snapshotId);
    }

    public void deleteByExternalOrder(Long externalOrderId) {
        jdbcTemplate.update("""
            DELETE allocation_row
            FROM external_order_renewal_investor_allocation allocation_row
            JOIN external_order_renewal_event event_row
              ON event_row.id = allocation_row.renewal_event_id
            WHERE event_row.external_order_id = ?
            """, externalOrderId);
    }

    public record CreateRow(
        Long renewalEventId,
        Long settlementSnapshotId,
        AssetType assetType,
        Long assetId,
        Long investorId,
        LocalDateTime effectiveStartAt,
        LocalDateTime effectiveEndAt,
        BigDecimal allocationWeight,
        BigDecimal rentBaseAmount,
        BigDecimal investorShareAmount
    ) {
    }

    public record InvestorAllocationTotal(
        Long investorId,
        BigDecimal rentBaseAmount,
        BigDecimal investorShareAmount
    ) {
    }

    private static class AllocationMapper implements RowMapper<ExternalOrderRenewalInvestorAllocation> {
        @Override
        public ExternalOrderRenewalInvestorAllocation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalOrderRenewalInvestorAllocation(
                rs.getLong("id"),
                rs.getLong("renewal_event_id"),
                rs.getLong("settlement_snapshot_id"),
                AssetType.valueOf(rs.getString("asset_type")),
                rs.getLong("asset_id"),
                rs.getLong("investor_id"),
                rs.getObject("effective_start_at", LocalDateTime.class),
                rs.getObject("effective_end_at", LocalDateTime.class),
                rs.getBigDecimal("allocation_weight"),
                rs.getBigDecimal("rent_base_amount"),
                rs.getBigDecimal("investor_share_amount"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
