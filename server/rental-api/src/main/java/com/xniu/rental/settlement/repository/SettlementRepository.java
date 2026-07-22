package com.xniu.rental.settlement.repository;

import com.xniu.rental.settlement.model.RuleScope;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
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

    public List<SettlementProfitRule> listDefaultStoreRules() {
        return jdbcTemplate.query("""
            SELECT store_rule.*
            FROM settlement_profit_rule store_rule
            JOIN merchant_store store ON store.id = store_rule.store_id
            WHERE store_rule.rule_scope = 'STORE'
              AND store_rule.source_channel IS NULL
              AND store_rule.status = 'ENABLED'
              AND store_rule.effective_at <= CURRENT_TIMESTAMP
              AND (store_rule.expired_at IS NULL OR store_rule.expired_at > CURRENT_TIMESTAMP)
              AND store_rule.id = (
                SELECT candidate.id
                FROM settlement_profit_rule candidate
                WHERE candidate.rule_scope = 'STORE'
                  AND candidate.store_id = store_rule.store_id
                  AND candidate.source_channel IS NULL
                  AND candidate.status = 'ENABLED'
                  AND candidate.effective_at <= CURRENT_TIMESTAMP
                  AND (candidate.expired_at IS NULL OR candidate.expired_at > CURRENT_TIMESTAMP)
                ORDER BY candidate.rule_priority DESC, candidate.effective_at DESC, candidate.id DESC
                LIMIT 1
              )
            ORDER BY store.id DESC
            """, ruleMapper);
    }

    public Optional<SettlementProfitRule> findDefaultStoreRule(Long storeId) {
        var rules = jdbcTemplate.query("""
            SELECT *
            FROM settlement_profit_rule
            WHERE rule_scope = 'STORE'
              AND store_id = ?
              AND source_channel IS NULL
              AND status = 'ENABLED'
              AND effective_at <= CURRENT_TIMESTAMP
              AND (expired_at IS NULL OR expired_at > CURRENT_TIMESTAMP)
            ORDER BY rule_priority DESC, effective_at DESC, id DESC
            LIMIT 1
            """, ruleMapper, storeId);
        return rules.stream().findFirst();
    }

    public void createDefaultStoreRuleIfMissing(Long storeId) {
        jdbcTemplate.update("""
            INSERT INTO settlement_profit_rule
            (rule_code, rule_name, rule_scope, source_channel, rule_priority,
             sku_id, merchant_id, store_id, store_sku_id,
             channel_fee_rate, platform_fee_rate, store_operation_rate, maintenance_fund_rate,
             channel_referral_rate, investor_share_rate,
             effective_at, expired_at, status)
            SELECT CONCAT('RULE-store-default-', store.id),
                   CONCAT(store.store_name, '分润规则'),
                   'STORE',
                   NULL,
                   0,
                   NULL,
                   store.merchant_id,
                   store.id,
                   NULL,
                   template.channel_fee_rate,
                   template.platform_fee_rate,
                   template.store_operation_rate,
                   template.maintenance_fund_rate,
                   template.channel_referral_rate,
                   template.investor_share_rate,
                   CURRENT_TIMESTAMP,
                   NULL,
                   'ENABLED'
            FROM merchant_store store
            JOIN settlement_profit_rule template
              ON template.id = (
                SELECT platform_rule.id
                FROM settlement_profit_rule platform_rule
                WHERE platform_rule.rule_scope = 'PLATFORM'
                  AND platform_rule.source_channel IS NULL
                  AND platform_rule.status = 'ENABLED'
                  AND platform_rule.effective_at <= CURRENT_TIMESTAMP
                  AND (platform_rule.expired_at IS NULL OR platform_rule.expired_at > CURRENT_TIMESTAMP)
                ORDER BY platform_rule.rule_priority DESC, platform_rule.effective_at DESC, platform_rule.id DESC
                LIMIT 1
              )
            WHERE store.id = ?
              AND NOT EXISTS (
                SELECT 1
                FROM settlement_profit_rule existing_rule
                WHERE existing_rule.rule_scope = 'STORE'
                  AND existing_rule.store_id = store.id
                  AND existing_rule.source_channel IS NULL
                  AND existing_rule.status = 'ENABLED'
              )
            ON DUPLICATE KEY UPDATE
              rule_name = VALUES(rule_name),
              merchant_id = VALUES(merchant_id),
              store_id = VALUES(store_id),
              channel_fee_rate = VALUES(channel_fee_rate),
              platform_fee_rate = VALUES(platform_fee_rate),
              store_operation_rate = VALUES(store_operation_rate),
              maintenance_fund_rate = VALUES(maintenance_fund_rate),
              channel_referral_rate = VALUES(channel_referral_rate),
              investor_share_rate = VALUES(investor_share_rate),
              effective_at = VALUES(effective_at),
              expired_at = NULL,
              status = 'ENABLED'
            """, storeId);
    }

    public SettlementProfitRule createRule(
        String ruleCode,
        String ruleName,
        RuleScope ruleScope,
        String sourceChannel,
        Integer priority,
        Long skuId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate,
        LocalDateTime effectiveAt,
        LocalDateTime expiredAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_profit_rule
                (rule_code, rule_name, rule_scope, source_channel, rule_priority,
                 sku_id, merchant_id, store_id, store_sku_id,
                 channel_fee_rate, platform_fee_rate, store_operation_rate, maintenance_fund_rate,
                 channel_referral_rate, investor_share_rate,
                 effective_at, expired_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, ruleCode);
            statement.setString(2, ruleName);
            statement.setString(3, ruleScope.name());
            statement.setString(4, sourceChannel);
            statement.setInt(5, priority == null ? 0 : priority);
            setNullableLong(statement, 6, skuId);
            setNullableLong(statement, 7, merchantId);
            setNullableLong(statement, 8, storeId);
            setNullableLong(statement, 9, storeSkuId);
            statement.setBigDecimal(10, channelFeeRate);
            statement.setBigDecimal(11, platformFeeRate);
            statement.setBigDecimal(12, storeOperationRate);
            statement.setBigDecimal(13, maintenanceFundRate);
            statement.setBigDecimal(14, channelReferralRate);
            statement.setBigDecimal(15, investorShareRate);
            statement.setObject(16, effectiveAt);
            statement.setObject(17, expiredAt);
            return statement;
        }, keyHolder);
        return findRule(keyHolder.getKey().longValue()).orElseThrow();
    }

    public SettlementProfitRule updateRuleStatus(Long id, SettlementRuleStatus status) {
        jdbcTemplate.update("UPDATE settlement_profit_rule SET status = ? WHERE id = ?", status.name(), id);
        return findRule(id).orElseThrow();
    }

    public SettlementProfitRule updateRule(
        Long id,
        String ruleName,
        RuleScope ruleScope,
        String sourceChannel,
        Integer priority,
        Long skuId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate,
        LocalDateTime effectiveAt,
        LocalDateTime expiredAt
    ) {
        jdbcTemplate.update("""
            UPDATE settlement_profit_rule
            SET rule_name = ?,
                rule_scope = ?,
                source_channel = ?,
                rule_priority = ?,
                sku_id = ?,
                merchant_id = ?,
                store_id = ?,
                store_sku_id = ?,
                channel_fee_rate = ?,
                platform_fee_rate = ?,
                store_operation_rate = ?,
                maintenance_fund_rate = ?,
                channel_referral_rate = ?,
                investor_share_rate = ?,
                effective_at = ?,
                expired_at = ?
            WHERE id = ?
            """,
            ruleName,
            ruleScope.name(),
            sourceChannel,
            priority,
            skuId,
            merchantId,
            storeId,
            storeSkuId,
            channelFeeRate,
            platformFeeRate,
            storeOperationRate,
            maintenanceFundRate,
            channelReferralRate,
            investorShareRate,
            effectiveAt,
            expiredAt,
            id
        );
        return findRule(id).orElseThrow();
    }

    public int countSnapshotsByRuleId(Long ruleId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM settlement_rule_snapshot WHERE matched_rule_id = ?",
            Integer.class,
            ruleId
        );
    }

    public boolean existsOtherActiveFallbackRule(
        RuleScope scope,
        Long storeId,
        Long excludedRuleId,
        LocalDateTime now
    ) {
        var sql = new StringBuilder("""
            SELECT COUNT(1)
            FROM settlement_profit_rule
            WHERE rule_scope = ?
              AND source_channel IS NULL
              AND status = 'ENABLED'
              AND effective_at <= ?
              AND (expired_at IS NULL OR expired_at > ?)
              AND id <> ?
            """);
        var params = new ArrayList<Object>();
        params.add(scope.name());
        params.add(now);
        params.add(now);
        params.add(excludedRuleId);
        if (RuleScope.STORE.equals(scope)) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        var count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null && count > 0;
    }

    public void deleteRule(Long id) {
        jdbcTemplate.update("DELETE FROM settlement_profit_rule WHERE id = ?", id);
    }

    public SettlementProfitRule updateStoreRule(
        Long id,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        jdbcTemplate.update("""
            UPDATE settlement_profit_rule
            SET channel_fee_rate = ?,
                platform_fee_rate = ?,
                store_operation_rate = ?,
                maintenance_fund_rate = ?,
                channel_referral_rate = ?,
                investor_share_rate = ?
            WHERE id = ?
            """,
            channelFeeRate,
            platformFeeRate,
            storeOperationRate,
            maintenanceFundRate,
            channelReferralRate,
            investorShareRate,
            id
        );
        return findRule(id).orElseThrow();
    }

    public void deleteRulesByStoreId(Long storeId) {
        jdbcTemplate.update("DELETE FROM settlement_profit_rule WHERE store_id = ?", storeId);
    }

    public Optional<SettlementProfitRule> matchRule(Long storeSkuId, Long skuId, Long storeId, String sourceChannel, LocalDateTime now) {
        var rules = jdbcTemplate.query("""
            SELECT * FROM settlement_profit_rule
            WHERE status = 'ENABLED'
              AND effective_at <= ?
              AND (expired_at IS NULL OR expired_at > ?)
              AND (source_channel IS NULL OR source_channel = ?)
              AND (
                (rule_scope = 'STORE_SKU' AND store_sku_id = ?)
                OR (rule_scope = 'STORE' AND store_id = ?)
                OR (rule_scope = 'SKU' AND sku_id = ?)
                OR (rule_scope = 'PLATFORM')
              )
            ORDER BY
              CASE rule_scope
                WHEN 'STORE' THEN 4
                WHEN 'STORE_SKU' THEN 3
                WHEN 'SKU' THEN 2
                ELSE 0
              END DESC,
              CASE WHEN source_channel IS NULL THEN 0 ELSE 1 END DESC,
              rule_priority DESC,
              effective_at DESC,
              id DESC
            LIMIT 1
            """, ruleMapper, now, now, sourceChannel, storeSkuId, storeId, skuId);
        return rules.stream().findFirst();
    }

    public SettlementRuleSnapshot createSnapshot(SettlementRuleSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_rule_snapshot
                (snapshot_no, source_type, source_id, calculation_version, source_channel,
                 store_sku_id, sku_id, merchant_id, store_id,
                 frame_asset_id, battery_asset_id, matched_rule_id, matched_rule_scope,
                 rental_amount, settlement_base_amount, sign_fee_amount, merchant_order_fee_amount,
                 merchant_rent_share_rate, merchant_rent_share_amount,
                 platform_rent_share_rate, platform_rent_share_amount,
                 investor_rent_share_rate, investor_gross_share_amount,
                 investor_operation_fee_amount, maintenance_fee_amount, investor_net_share_amount,
                 channel_fee_rate, channel_fee_amount, platform_fee_rate, platform_fee_amount,
                 distributable_amount, store_operation_rate, store_operation_amount,
                 maintenance_fund_rate, maintenance_fund_amount,
                 channel_referral_rate, channel_referral_amount,
                 investor_share_rate, investor_share_amount,
                 rule_summary)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, snapshot.snapshotNo());
            statement.setString(2, snapshot.sourceType().name());
            setNullableLong(statement, 3, snapshot.sourceId());
            statement.setString(4, snapshot.calculationVersion().name());
            statement.setString(5, snapshot.sourceChannel());
            statement.setLong(6, snapshot.storeSkuId());
            statement.setLong(7, snapshot.skuId());
            statement.setLong(8, snapshot.merchantId());
            statement.setLong(9, snapshot.storeId());
            setNullableLong(statement, 10, snapshot.frameAssetId());
            setNullableLong(statement, 11, snapshot.batteryAssetId());
            statement.setLong(12, snapshot.matchedRuleId());
            statement.setString(13, snapshot.matchedRuleScope().name());
            statement.setBigDecimal(14, snapshot.rentalAmount());
            statement.setBigDecimal(15, snapshot.settlementBaseAmount());
            statement.setBigDecimal(16, snapshot.signFeeAmount());
            statement.setBigDecimal(17, snapshot.merchantOrderFeeAmount());
            statement.setBigDecimal(18, snapshot.merchantRentShareRate());
            statement.setBigDecimal(19, snapshot.merchantRentShareAmount());
            statement.setBigDecimal(20, snapshot.platformRentShareRate());
            statement.setBigDecimal(21, snapshot.platformRentShareAmount());
            statement.setBigDecimal(22, snapshot.investorRentShareRate());
            statement.setBigDecimal(23, snapshot.investorGrossShareAmount());
            statement.setBigDecimal(24, snapshot.investorOperationFeeAmount());
            statement.setBigDecimal(25, snapshot.maintenanceFeeAmount());
            statement.setBigDecimal(26, snapshot.investorNetShareAmount());
            statement.setBigDecimal(27, snapshot.channelFeeRate());
            statement.setBigDecimal(28, snapshot.channelFeeAmount());
            statement.setBigDecimal(29, snapshot.platformFeeRate());
            statement.setBigDecimal(30, snapshot.platformFeeAmount());
            statement.setBigDecimal(31, snapshot.distributableAmount());
            statement.setBigDecimal(32, snapshot.storeOperationRate());
            statement.setBigDecimal(33, snapshot.storeOperationAmount());
            statement.setBigDecimal(34, snapshot.maintenanceFundRate());
            statement.setBigDecimal(35, snapshot.maintenanceFundAmount());
            statement.setBigDecimal(36, snapshot.channelReferralRate());
            statement.setBigDecimal(37, snapshot.channelReferralAmount());
            statement.setBigDecimal(38, snapshot.investorShareRate());
            statement.setBigDecimal(39, snapshot.investorShareAmount());
            statement.setString(40, snapshot.ruleSummary());
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
                rs.getString("source_channel"),
                rs.getInt("rule_priority"),
                getNullableLong(rs, "sku_id"),
                getNullableLong(rs, "merchant_id"),
                getNullableLong(rs, "store_id"),
                getNullableLong(rs, "store_sku_id"),
                rs.getBigDecimal("channel_fee_rate"),
                rs.getBigDecimal("platform_fee_rate"),
                rs.getBigDecimal("store_operation_rate"),
                rs.getBigDecimal("maintenance_fund_rate"),
                rs.getBigDecimal("channel_referral_rate"),
                rs.getBigDecimal("investor_share_rate"),
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
                SettlementCalculationVersion.valueOf(rs.getString("calculation_version")),
                rs.getString("source_channel"),
                rs.getLong("store_sku_id"),
                rs.getLong("sku_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                getNullableLong(rs, "frame_asset_id"),
                getNullableLong(rs, "battery_asset_id"),
                rs.getLong("matched_rule_id"),
                RuleScope.valueOf(rs.getString("matched_rule_scope")),
                rs.getBigDecimal("rental_amount"),
                rs.getBigDecimal("settlement_base_amount"),
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
                rs.getBigDecimal("channel_fee_rate"),
                rs.getBigDecimal("channel_fee_amount"),
                rs.getBigDecimal("platform_fee_rate"),
                rs.getBigDecimal("platform_fee_amount"),
                rs.getBigDecimal("distributable_amount"),
                rs.getBigDecimal("store_operation_rate"),
                rs.getBigDecimal("store_operation_amount"),
                rs.getBigDecimal("maintenance_fund_rate"),
                rs.getBigDecimal("maintenance_fund_amount"),
                rs.getBigDecimal("channel_referral_rate"),
                rs.getBigDecimal("channel_referral_amount"),
                rs.getBigDecimal("investor_share_rate"),
                rs.getBigDecimal("investor_share_amount"),
                rs.getString("rule_summary"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
