package com.xniu.rental.settlement.repository;

import com.xniu.rental.settlement.model.RuleScope;
import com.xniu.rental.settlement.model.SettlementProfitRule;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SettlementRuleStatus;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SettlementProfitRule> ruleMapper = new RuleMapper();
    private final RowMapper<SettlementRuleSnapshot> snapshotMapper = new SnapshotMapper();

    public SettlementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SettlementProfitRule> listRules(RuleScope scope, SettlementRuleStatus status) {
        var sql = new StringBuilder("SELECT * FROM settlement_profit_rule WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (scope != null) {
            sql.append(" AND rule_scope = ?");
            params.add(scope.name());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), ruleMapper, params.toArray());
    }

    public Optional<SettlementProfitRule> findRule(Long id) {
        var rules = jdbcTemplate.query("SELECT * FROM settlement_profit_rule WHERE id = ?", ruleMapper, id);
        return rules.stream().findFirst();
    }

    public SettlementProfitRule createRule(
        String ruleCode,
        String ruleName,
        RuleScope ruleScope,
        Long skuId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        BigDecimal merchantOrderFeeAmount,
        BigDecimal merchantRentShareRate,
        BigDecimal platformRentShareRate,
        BigDecimal investorRentShareRate,
        LocalDateTime effectiveAt,
        LocalDateTime expiredAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_profit_rule
                (rule_code, rule_name, rule_scope, sku_id, merchant_id, store_id, store_sku_id,
                 merchant_order_fee_amount, merchant_rent_share_rate, platform_rent_share_rate, investor_rent_share_rate,
                 effective_at, expired_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, ruleCode);
            statement.setString(2, ruleName);
            statement.setString(3, ruleScope.name());
            setNullableLong(statement, 4, skuId);
            setNullableLong(statement, 5, merchantId);
            setNullableLong(statement, 6, storeId);
            setNullableLong(statement, 7, storeSkuId);
            statement.setBigDecimal(8, merchantOrderFeeAmount);
            statement.setBigDecimal(9, merchantRentShareRate);
            statement.setBigDecimal(10, platformRentShareRate);
            statement.setBigDecimal(11, investorRentShareRate);
            statement.setObject(12, effectiveAt);
            statement.setObject(13, expiredAt);
            return statement;
        }, keyHolder);
        return findRule(keyHolder.getKey().longValue()).orElseThrow();
    }

    public SettlementProfitRule updateRuleStatus(Long id, SettlementRuleStatus status) {
        jdbcTemplate.update("UPDATE settlement_profit_rule SET status = ? WHERE id = ?", status.name(), id);
        return findRule(id).orElseThrow();
    }

    public Optional<SettlementProfitRule> matchRule(Long storeSkuId, Long skuId, Long merchantId, Long storeId, LocalDateTime now) {
        var rules = jdbcTemplate.query("""
            SELECT * FROM settlement_profit_rule
            WHERE status = 'ENABLED'
              AND effective_at <= ?
              AND (expired_at IS NULL OR expired_at > ?)
              AND (
                (rule_scope = 'STORE_SKU' AND store_sku_id = ?)
                OR (rule_scope = 'STORE' AND store_id = ?)
                OR (rule_scope = 'SKU' AND sku_id = ?)
                OR (rule_scope = 'PLATFORM')
              )
            ORDER BY
              CASE rule_scope
                WHEN 'STORE_SKU' THEN 1
                WHEN 'STORE' THEN 2
                WHEN 'SKU' THEN 3
                ELSE 4
              END,
              effective_at DESC,
              id DESC
            LIMIT 1
            """, ruleMapper, now, now, storeSkuId, storeId, skuId);
        return rules.stream().findFirst();
    }

    public SettlementRuleSnapshot createSnapshot(SettlementRuleSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_rule_snapshot
                (snapshot_no, source_type, source_id, store_sku_id, sku_id, merchant_id, store_id,
                 frame_asset_id, battery_asset_id, matched_rule_id, matched_rule_scope,
                 rental_amount, sign_fee_amount, merchant_order_fee_amount,
                 merchant_rent_share_rate, merchant_rent_share_amount,
                 platform_rent_share_rate, platform_rent_share_amount,
                 investor_rent_share_rate, investor_gross_share_amount,
                 investor_operation_fee_amount, maintenance_fee_amount, investor_net_share_amount,
                 rule_summary)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, snapshot.snapshotNo());
            statement.setString(2, snapshot.sourceType().name());
            setNullableLong(statement, 3, snapshot.sourceId());
            statement.setLong(4, snapshot.storeSkuId());
            statement.setLong(5, snapshot.skuId());
            statement.setLong(6, snapshot.merchantId());
            statement.setLong(7, snapshot.storeId());
            setNullableLong(statement, 8, snapshot.frameAssetId());
            setNullableLong(statement, 9, snapshot.batteryAssetId());
            statement.setLong(10, snapshot.matchedRuleId());
            statement.setString(11, snapshot.matchedRuleScope().name());
            statement.setBigDecimal(12, snapshot.rentalAmount());
            statement.setBigDecimal(13, snapshot.signFeeAmount());
            statement.setBigDecimal(14, snapshot.merchantOrderFeeAmount());
            statement.setBigDecimal(15, snapshot.merchantRentShareRate());
            statement.setBigDecimal(16, snapshot.merchantRentShareAmount());
            statement.setBigDecimal(17, snapshot.platformRentShareRate());
            statement.setBigDecimal(18, snapshot.platformRentShareAmount());
            statement.setBigDecimal(19, snapshot.investorRentShareRate());
            statement.setBigDecimal(20, snapshot.investorGrossShareAmount());
            statement.setBigDecimal(21, snapshot.investorOperationFeeAmount());
            statement.setBigDecimal(22, snapshot.maintenanceFeeAmount());
            statement.setBigDecimal(23, snapshot.investorNetShareAmount());
            statement.setString(24, snapshot.ruleSummary());
            return statement;
        }, keyHolder);
        return findSnapshot(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<SettlementRuleSnapshot> findSnapshot(Long id) {
        var snapshots = jdbcTemplate.query("SELECT * FROM settlement_rule_snapshot WHERE id = ?", snapshotMapper, id);
        return snapshots.stream().findFirst();
    }

    public List<SettlementRuleSnapshot> findSnapshotsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query(
            "SELECT * FROM settlement_rule_snapshot WHERE id IN (" + placeholders + ")",
            snapshotMapper,
            ids.toArray()
        );
    }

    public List<SettlementRuleSnapshot> listSnapshots(String sourceType, Long sourceId) {
        var sql = new StringBuilder("SELECT * FROM settlement_rule_snapshot WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND source_type = ?");
            params.add(sourceType);
        }
        if (sourceId != null) {
            sql.append(" AND source_id = ?");
            params.add(sourceId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), snapshotMapper, params.toArray());
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

    private static class RuleMapper implements RowMapper<SettlementProfitRule> {
        @Override
        public SettlementProfitRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementProfitRule(
                rs.getLong("id"),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                RuleScope.valueOf(rs.getString("rule_scope")),
                getNullableLong(rs, "sku_id"),
                getNullableLong(rs, "merchant_id"),
                getNullableLong(rs, "store_id"),
                getNullableLong(rs, "store_sku_id"),
                rs.getBigDecimal("merchant_order_fee_amount"),
                rs.getBigDecimal("merchant_rent_share_rate"),
                rs.getBigDecimal("platform_rent_share_rate"),
                rs.getBigDecimal("investor_rent_share_rate"),
                rs.getObject("effective_at", LocalDateTime.class),
                rs.getObject("expired_at", LocalDateTime.class),
                SettlementRuleStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static class SnapshotMapper implements RowMapper<SettlementRuleSnapshot> {
        @Override
        public SettlementRuleSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementRuleSnapshot(
                rs.getLong("id"),
                rs.getString("snapshot_no"),
                SnapshotSourceType.valueOf(rs.getString("source_type")),
                getNullableLong(rs, "source_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                rs.getLong("matched_rule_id"),
                RuleScope.valueOf(rs.getString("matched_rule_scope")),
                rs.getBigDecimal("rental_amount"),
                rs.getBigDecimal("sign_fee_amount"),
                rs.getBigDecimal("merchant_order_fee_amount"),
                rs.getBigDecimal("merchant_rent_share_rate"),
                rs.getBigDecimal("merchant_rent_share_amount"),
                rs.getBigDecimal("platform_rent_share_rate"),
                rs.getBigDecimal("platform_rent_share_amount"),
                rs.getBigDecimal("investor_rent_share_rate"),
                rs.getBigDecimal("investor_gross_share_amount"),
                rs.getBigDecimal("investor_operation_fee_amount"),
                rs.getBigDecimal("maintenance_fee_amount"),
                rs.getBigDecimal("investor_net_share_amount"),
                rs.getString("rule_summary"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
