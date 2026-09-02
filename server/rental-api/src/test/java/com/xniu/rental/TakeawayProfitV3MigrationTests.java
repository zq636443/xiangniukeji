package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TakeawayProfitV3MigrationTests {

    private static final String SERVER_URL =
        "jdbc:mysql://localhost:3310/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    private static final long ORDER_ID = 9_650_001L;
    private static final long RENEWAL_ID = 9_650_002L;
    private static final long INITIAL_SNAPSHOT_ID = 9_651_001L;
    private static final long RENEWAL_SNAPSHOT_ID = 9_651_002L;
    private static final long ORPHAN_SNAPSHOT_ID = 9_651_003L;

    private String databaseName;
    private String databaseUrl;

    @BeforeEach
    void createDatabaseAtV64() throws SQLException {
        databaseName = "xniu_v65_" + UUID.randomUUID().toString().replace("-", "");
        databaseUrl = "jdbc:mysql://localhost:3310/" + databaseName
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        flyway(MigrationVersion.fromVersion("64")).migrate();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        if (databaseName == null || !databaseName.startsWith("xniu_v65_")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
    }

    @Test
    void shouldRecalculateCurrentInitialAndRenewalSnapshotsAndRemainIdempotent() throws SQLException {
        seedCurrentInitialAndRenewalSources();

        flyway(MigrationVersion.fromVersion("65")).migrate();

        assertThat(singleLong("SELECT COUNT(*) FROM settlement_snapshot_recalculation_audit WHERE migration_code = 'V65'"))
            .isEqualTo(2);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V65-%'"))
            .isEqualTo(2);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_rental_order source_row
            JOIN settlement_rule_snapshot snapshot_row ON snapshot_row.id = source_row.settlement_snapshot_id
            WHERE source_row.id = ? AND snapshot_row.calculation_version = 'PROFIT_V3'
            """, ORDER_ID)).isEqualTo(1);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_renewal_event source_row
            JOIN settlement_rule_snapshot snapshot_row ON snapshot_row.id = source_row.settlement_snapshot_id
            WHERE source_row.id = ? AND snapshot_row.calculation_version = 'PROFIT_V3'
            """, RENEWAL_ID)).isEqualTo(1);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE id = ? AND calculation_version = 'PROFIT_V2'", ORPHAN_SNAPSHOT_ID))
            .isEqualTo(1);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE id IN (?, ?) AND calculation_version = 'PROFIT_V2'",
            INITIAL_SNAPSHOT_ID, RENEWAL_SNAPSHOT_ID)).isEqualTo(2);

        var newSnapshotId = singleLong("SELECT settlement_snapshot_id FROM external_rental_order WHERE id = ?", ORDER_ID);
        assertMoney(newSnapshotId, "distributable_amount", "87.28");
        assertMoney(newSnapshotId, "store_operation_amount", "16.37");
        assertMoney(newSnapshotId, "maintenance_fund_amount", "10.91");
        assertMoney(newSnapshotId, "channel_referral_amount", "79.80");
        assertMoney(newSnapshotId, "investor_share_amount", "60.00");
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_income_entry WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ? AND snapshot_id = ?",
            ORDER_ID, newSnapshotId)).isEqualTo(6);
        assertThat(singleMoney("SELECT amount FROM settlement_income_entry WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ? AND line_type = 'CHANNEL_REFERRAL_SHARE'",
            ORDER_ID)).isEqualByComparingTo("79.80");

        var remarkBeforeRetry = singleText("""
            SELECT remark FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ? AND line_type = 'CHANNEL_REFERRAL_SHARE'
            """, ORDER_ID);
        update("DELETE FROM flyway_schema_history WHERE version = '65'");
        flyway(MigrationVersion.fromVersion("65")).migrate();

        assertThat(singleLong("SELECT COUNT(*) FROM settlement_snapshot_recalculation_audit WHERE migration_code = 'V65'"))
            .isEqualTo(2);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V65-%'"))
            .isEqualTo(2);
        assertThat(singleText("""
            SELECT remark FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ? AND line_type = 'CHANNEL_REFERRAL_SHARE'
            """, ORDER_ID)).isEqualTo(remarkBeforeRetry);
    }

    @Test
    void shouldFailBeforeBusinessWritesWhenAnyCurrentSourceIsNotEligible() throws SQLException {
        seedCurrentInitialAndRenewalSources();
        update("""
            UPDATE settlement_income_entry
            SET entry_status = 'FROZEN'
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, RENEWAL_ID);

        assertThatThrownBy(() -> flyway(MigrationVersion.fromVersion("65")).migrate())
            .isInstanceOf(FlywayException.class);

        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V65-%'"))
            .isZero();
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_snapshot_recalculation_audit WHERE migration_code = 'V65'"))
            .isZero();
        assertThat(singleLong("SELECT settlement_snapshot_id FROM external_rental_order WHERE id = ?", ORDER_ID))
            .isEqualTo(INITIAL_SNAPSHOT_ID);
        assertThat(singleLong("SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?", RENEWAL_ID))
            .isEqualTo(RENEWAL_SNAPSHOT_ID);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
            .dataSource(databaseUrl, USERNAME, PASSWORD)
            .placeholders(Map.of("cleanupDemoData", "false"));
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void seedCurrentInitialAndRenewalSources() throws SQLException {
        update("UPDATE product_sku SET battery_cost_daily_amount = NULL, battery_cost_monthly_amount = NULL WHERE id = 2");
        update("""
            INSERT INTO external_rental_order (
              id, record_no, source_platform, merchant_id, store_id, store_sku_id, sku_id, package_id,
              customer_name, customer_phone, order_status, external_rental_amount, verification_amount,
              settlement_snapshot_id, sign_fee_amount, deposit_amount, lease_unit, lease_value,
              total_periods, lease_multiplier, auto_renew_enabled, rent_started_at, expected_return_at
            ) VALUES (?, 'EXT-V65-ORDER', 'OTHER', 1, 1, 2, 2, 2,
              '迁移测试', '13800000000', 'ACTIVE', 399.00, 399.00,
              NULL, 0.00, 0.00, 'DAY', 30, 1, 1, 0, '2026-08-01 00:00:00', '2026-08-31 00:00:00')
            """, ORDER_ID);
        insertV2Snapshot(INITIAL_SNAPSHOT_ID, "V65-OLD-INITIAL", "EXTERNAL_ORDER", ORDER_ID);
        update("UPDATE external_rental_order SET settlement_snapshot_id = ? WHERE id = ?", INITIAL_SNAPSHOT_ID, ORDER_ID);
        update("""
            INSERT INTO external_order_renewal_event (
              id, external_order_id, event_no, period_no, period_start_at, period_end_at,
              renewal_amount, system_renewal_amount, battery_cost_amount, settlement_snapshot_id,
              event_status, renewal_source
            ) VALUES (?, ?, 'ERN-V65-RENEWAL', 2, '2026-08-31 00:00:00', '2026-09-30 00:00:00',
              399.00, 399.00, 200.00, NULL, 'ACCRUED', 'SYSTEM')
            """, RENEWAL_ID, ORDER_ID);
        insertV2Snapshot(RENEWAL_SNAPSHOT_ID, "V65-OLD-RENEWAL", "EXTERNAL_RENEWAL", RENEWAL_ID);
        update("UPDATE external_order_renewal_event SET settlement_snapshot_id = ? WHERE id = ?", RENEWAL_SNAPSHOT_ID, RENEWAL_ID);
        insertV2Snapshot(ORPHAN_SNAPSHOT_ID, "V65-OLD-ORPHAN", "EXTERNAL_ORDER", 9_650_099L);
        insertIncomeRows("EXTERNAL_ORDER", ORDER_ID, INITIAL_SNAPSHOT_ID, "INITIAL");
        insertIncomeRows("EXTERNAL_RENEWAL", RENEWAL_ID, RENEWAL_SNAPSHOT_ID, "RENEWAL");
    }

    private void insertV2Snapshot(long id, String snapshotNo, String sourceType, long sourceId) throws SQLException {
        update("""
            INSERT INTO settlement_rule_snapshot (
              id, snapshot_no, source_type, source_id, calculation_version, source_channel,
              store_sku_id, sku_id, merchant_id, store_id, matched_rule_id, matched_rule_scope,
              rental_amount, settlement_base_amount, channel_fee_rate, channel_fee_amount,
              platform_fee_rate, platform_fee_amount, battery_cost_amount, distributable_amount,
              store_operation_rate, store_operation_amount, maintenance_fund_rate, maintenance_fund_amount,
              channel_referral_rate, channel_referral_amount, investor_share_rate, investor_share_amount,
              merchant_rent_share_rate, merchant_rent_share_amount, platform_rent_share_rate,
              platform_rent_share_amount, investor_rent_share_rate, investor_gross_share_amount,
              maintenance_fee_amount, investor_net_share_amount, rule_summary
            ) VALUES (?, ?, ?, ?, 'PROFIT_V2', 'DIRECT',
              2, 2, 1, 1, 1, 'STORE',
              399.00, 399.00, 0.0500, 19.95,
              0.0300, 11.97, 200.00, 167.08,
              0.1500, 25.06, 0.1000, 16.71,
              0.2000, 33.42, 0.5500, 91.89,
              0.1500, 25.06, 0.0300,
              11.97, 0.5500, 91.89,
              16.71, 91.89, 'V65 migration fixture')
            """, id, snapshotNo, sourceType, sourceId);
    }

    private void insertIncomeRows(String sourceType, long sourceId, long snapshotId, String suffix) throws SQLException {
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-CHANNEL-FEE", "CHANNEL", 0L,
            "CHANNEL_VERIFICATION_FEE", "19.95");
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-PLATFORM-FEE", "PLATFORM", 0L,
            "PLATFORM_SERVICE_FEE", "11.97");
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-OPERATION", "MERCHANT", 1L,
            "STORE_OPERATION_SHARE", "25.06");
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-MAINTENANCE", "MERCHANT", 1L,
            "MAINTENANCE_FUND_SHARE", "16.71");
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-REFERRAL", "CHANNEL", 0L,
            "CHANNEL_REFERRAL_SHARE", "33.42");
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-INVESTOR", "INVESTOR", 0L,
            "INVESTOR_SHARE", "91.89");
    }

    private void insertIncome(
        String sourceType,
        long sourceId,
        long snapshotId,
        String entrySuffix,
        String beneficiaryType,
        long beneficiaryId,
        String lineType,
        String amount
    ) throws SQLException {
        update("""
            INSERT INTO settlement_income_entry (
              entry_no, source_type, source_id, source_no, snapshot_id, merchant_id, store_id,
              beneficiary_type, beneficiary_id, line_type, amount, entry_status, remark, occurred_at
            ) VALUES (?, ?, ?, ?, ?, 1, 1, ?, ?, ?, ?, 'PENDING', '迁移前金额', '2026-08-01 00:00:00')
            """, "INC-V65-" + entrySuffix, sourceType, sourceId, "SRC-" + sourceId, snapshotId,
            beneficiaryType, beneficiaryId, lineType, new BigDecimal(amount));
    }

    private void assertMoney(long snapshotId, String column, String expected) throws SQLException {
        assertThat(singleMoney("SELECT " + column + " FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(expected);
    }

    private long singleLong(String sql, Object... arguments) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl, USERNAME, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private BigDecimal singleMoney(String sql, Object... arguments) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl, USERNAME, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBigDecimal(1);
            }
        }
    }

    private String singleText(String sql, Object... arguments) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl, USERNAME, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private void update(String sql, Object... arguments) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl, USERNAME, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index += 1) {
            statement.setObject(index + 1, arguments[index]);
        }
    }
}
