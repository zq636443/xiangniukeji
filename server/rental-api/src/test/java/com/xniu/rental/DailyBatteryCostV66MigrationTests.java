package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

class DailyBatteryCostV66MigrationTests {

    private static final String SERVER_URL =
        "jdbc:mysql://localhost:3310/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    private static final long ORDER_ID = 9_660_001L;
    private static final long ZERO_INITIAL_ORDER_ID = 9_660_002L;
    private static final long RENEWAL_ID = 9_660_101L;
    private static final long ZERO_INITIAL_RENEWAL_ID = 9_660_102L;

    private static final long INITIAL_SNAPSHOT_ID = 9_661_001L;
    private static final long ZERO_INITIAL_SNAPSHOT_ID = 9_661_002L;
    private static final long RENEWAL_SNAPSHOT_ID = 9_661_101L;
    private static final long ZERO_INITIAL_RENEWAL_SNAPSHOT_ID = 9_661_102L;
    private static final long ORPHAN_SNAPSHOT_ID = 9_661_999L;

    private String databaseName;
    private String databaseUrl;

    @BeforeEach
    void createDatabaseAtV65() throws SQLException {
        databaseName = "xniu_v66_" + UUID.randomUUID().toString().replace("-", "");
        databaseUrl = "jdbc:mysql://localhost:3310/" + databaseName
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        flyway(MigrationVersion.fromVersion("65")).migrate();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        if (databaseName == null || !databaseName.startsWith("xniu_v66_")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
    }

    @Test
    void shouldUseExactDailyIntervalsKeepOldSnapshotsAndRemainIdempotent() throws SQLException {
        seedCurrentSources();

        flyway(null).migrate();

        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_battery_recalculation_audit
            WHERE migration_code = 'V66'
            """)).isEqualTo(4);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V66-%'"))
            .isEqualTo(4);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_rule_snapshot
            WHERE id IN (?, ?, ?, ?)
              AND calculation_version = 'PROFIT_V3'
            """, INITIAL_SNAPSHOT_ID, ZERO_INITIAL_SNAPSHOT_ID,
            RENEWAL_SNAPSHOT_ID, ZERO_INITIAL_RENEWAL_SNAPSHOT_ID)).isEqualTo(4);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE id = ?", ORPHAN_SNAPSHOT_ID))
            .isEqualTo(1);

        var initialSnapshotId = currentOrderSnapshotId(ORDER_ID);
        assertSnapshot(initialSnapshotId, "204.00", "83.28", "15.62", "10.41", "79.80", "57.25");

        var renewalSnapshotId = currentRenewalSnapshotId(RENEWAL_ID);
        assertSnapshot(renewalSnapshotId, "210.80", "76.48", "14.34", "9.56", "79.80", "52.58");
        assertThat(singleMoney("SELECT battery_cost_amount FROM external_order_renewal_event WHERE id = ?", RENEWAL_ID))
            .isEqualByComparingTo("210.80");

        var zeroInitialSnapshotId = currentOrderSnapshotId(ZERO_INITIAL_ORDER_ID);
        assertSnapshot(zeroInitialSnapshotId, "0.00", "287.28", "53.87", "35.91", "79.80", "197.50");
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_battery_recalculation_audit
            WHERE migration_code = 'V66'
              AND source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND period_start_at = period_end_at
              AND new_battery_cost_amount = 0.00
            """, ZERO_INITIAL_ORDER_ID)).isEqualTo(1);

        var zeroInitialRenewalSnapshotId = currentRenewalSnapshotId(ZERO_INITIAL_RENEWAL_ID);
        assertSnapshot(zeroInitialRenewalSnapshotId, "190.40", "96.88", "18.17", "12.11", "79.80", "66.60");
        assertThat(singleMoney(
            "SELECT battery_cost_amount FROM external_order_renewal_event WHERE id = ?",
            ZERO_INITIAL_RENEWAL_ID
        )).isEqualByComparingTo("190.40");

        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_income_entry income_row
            JOIN settlement_battery_recalculation_audit audit_row
              ON audit_row.migration_code = 'V66'
             AND audit_row.source_type = income_row.source_type
             AND audit_row.source_id = income_row.source_id
             AND audit_row.new_snapshot_id = income_row.snapshot_id
            WHERE income_row.entry_status = 'PENDING'
            """)).isEqualTo(24);
        assertThat(singleMoney("""
            SELECT amount
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, ORDER_ID)).isEqualByComparingTo("15.62");
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_battery_recalculation_audit audit_row
            JOIN settlement_rule_snapshot snapshot_row
              ON snapshot_row.id = audit_row.new_snapshot_id
            WHERE audit_row.migration_code = 'V66'
              AND snapshot_row.settlement_base_amount <>
                  snapshot_row.channel_fee_amount
                    + snapshot_row.platform_fee_amount
                    + snapshot_row.battery_cost_amount
                    + snapshot_row.channel_referral_amount
                    + snapshot_row.store_operation_amount
                    + snapshot_row.maintenance_fund_amount
                    + snapshot_row.investor_share_amount
            """)).isZero();
        assertThat(singleMoney("SELECT battery_cost_daily_amount FROM product_sku WHERE id = 2"))
            .isEqualByComparingTo("6.80");
        assertThat(singleLong("SELECT COUNT(*) FROM product_sku WHERE id = 2 AND battery_cost_monthly_amount IS NULL"))
            .isEqualTo(1);

        var remarkBeforeRetry = singleText("""
            SELECT remark
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, ORDER_ID);
        update("DELETE FROM flyway_schema_history WHERE version = '66'");
        flyway(null).migrate();

        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_battery_recalculation_audit
            WHERE migration_code = 'V66'
            """)).isEqualTo(4);
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V66-%'"))
            .isEqualTo(4);
        assertThat(singleText("""
            SELECT remark
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, ORDER_ID)).isEqualTo(remarkBeforeRetry);
    }

    @Test
    void shouldRollBackEveryBusinessWriteWhenAnyIncomeIsNotPending() throws SQLException {
        seedCurrentSources();
        update("""
            UPDATE settlement_income_entry
            SET entry_status = 'FROZEN'
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, RENEWAL_ID);

        assertThatThrownBy(() -> flyway(null).migrate())
            .isInstanceOf(FlywayException.class);

        assertNoV66BusinessWrites();
        assertThat(currentOrderSnapshotId(ORDER_ID)).isEqualTo(INITIAL_SNAPSHOT_ID);
        assertThat(currentRenewalSnapshotId(RENEWAL_ID)).isEqualTo(RENEWAL_SNAPSHOT_ID);
        assertThat(singleMoney("SELECT battery_cost_amount FROM external_order_renewal_event WHERE id = ?", RENEWAL_ID))
            .isEqualByComparingTo("206.80");
        assertThat(singleMoney("SELECT battery_cost_monthly_amount FROM product_sku WHERE id = 2"))
            .isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRollBackWhenPendingIncomePointsAtAnUnexpectedSnapshot() throws SQLException {
        seedCurrentSources();
        update("""
            UPDATE settlement_income_entry
            SET snapshot_id = ?
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, ORPHAN_SNAPSHOT_ID, ORDER_ID);

        assertThatThrownBy(() -> flyway(null).migrate())
            .isInstanceOf(FlywayException.class);

        assertNoV66BusinessWrites();
        assertThat(currentOrderSnapshotId(ORDER_ID)).isEqualTo(INITIAL_SNAPSHOT_ID);
        assertThat(currentRenewalSnapshotId(RENEWAL_ID)).isEqualTo(RENEWAL_SNAPSHOT_ID);
        assertThat(singleMoney("SELECT battery_cost_amount FROM external_order_renewal_event WHERE id = ?", RENEWAL_ID))
            .isEqualByComparingTo("206.80");
        assertThat(singleMoney("SELECT battery_cost_monthly_amount FROM product_sku WHERE id = 2"))
            .isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRollBackWhenDailyCostWouldMakeTheV3BalanceNegative() throws SQLException {
        update("UPDATE product_sku SET battery_cost_daily_amount = 6.80, battery_cost_monthly_amount = 200.00 WHERE id = 2");
        insertOrder(
            ORDER_ID,
            "EXT-V66-UNFUNDED",
            "2026-08-01 00:00:00",
            "2026-08-31 00:00:00"
        );
        insertV3Snapshot(
            INITIAL_SNAPSHOT_ID,
            "V66-UNFUNDED-INITIAL",
            "EXTERNAL_ORDER",
            ORDER_ID,
            "100.00",
            "0.00",
            "72.00",
            "13.50",
            "9.00",
            "20.00",
            "49.50"
        );
        update("UPDATE external_rental_order SET settlement_snapshot_id = ? WHERE id = ?", INITIAL_SNAPSHOT_ID, ORDER_ID);
        insertIncomeRows(
            "EXTERNAL_ORDER",
            ORDER_ID,
            INITIAL_SNAPSHOT_ID,
            "UNFUNDED",
            "5.00",
            "3.00",
            "13.50",
            "9.00",
            "20.00",
            "49.50"
        );

        assertThatThrownBy(() -> flyway(null).migrate())
            .isInstanceOf(FlywayException.class);

        assertNoV66BusinessWrites();
        assertThat(currentOrderSnapshotId(ORDER_ID)).isEqualTo(INITIAL_SNAPSHOT_ID);
        assertThat(singleMoney("SELECT battery_cost_monthly_amount FROM product_sku WHERE id = 2"))
            .isEqualByComparingTo("200.00");
    }

    private void seedCurrentSources() throws SQLException {
        update("UPDATE product_sku SET battery_cost_daily_amount = 6.80, battery_cost_monthly_amount = 200.00 WHERE id = 2");

        insertOrder(ORDER_ID, "EXT-V66-ORDER", "2026-08-01 00:00:00", "2026-10-01 00:00:00");
        insertV3Snapshot(INITIAL_SNAPSHOT_ID, "V66-OLD-INITIAL", "EXTERNAL_ORDER", ORDER_ID,
            "399.00", "200.00", "87.28", "16.37", "10.91", "79.80", "60.00");
        update("UPDATE external_rental_order SET settlement_snapshot_id = ? WHERE id = ?", INITIAL_SNAPSHOT_ID, ORDER_ID);

        insertRenewal(RENEWAL_ID, ORDER_ID, "ERN-V66-RENEWAL",
            "2026-08-31 00:00:00", "2026-10-01 00:00:00", "206.80");
        insertV3Snapshot(RENEWAL_SNAPSHOT_ID, "V66-OLD-RENEWAL", "EXTERNAL_RENEWAL", RENEWAL_ID,
            "399.00", "206.80", "80.48", "15.09", "10.06", "79.80", "55.33");
        update("UPDATE external_order_renewal_event SET settlement_snapshot_id = ? WHERE id = ?", RENEWAL_SNAPSHOT_ID, RENEWAL_ID);

        insertOrder(ZERO_INITIAL_ORDER_ID, "EXT-V66-ZERO-INITIAL", "2026-08-06 00:00:00", "2026-09-03 00:00:00");
        insertV3Snapshot(ZERO_INITIAL_SNAPSHOT_ID, "V66-OLD-ZERO-INITIAL", "EXTERNAL_ORDER", ZERO_INITIAL_ORDER_ID,
            "399.00", "200.00", "87.28", "16.37", "10.91", "79.80", "60.00");
        update("UPDATE external_rental_order SET settlement_snapshot_id = ? WHERE id = ?", ZERO_INITIAL_SNAPSHOT_ID, ZERO_INITIAL_ORDER_ID);

        insertRenewal(ZERO_INITIAL_RENEWAL_ID, ZERO_INITIAL_ORDER_ID, "ERN-V66-ZERO-INITIAL",
            "2026-08-06 00:00:00", "2026-09-03 00:00:00", "190.40");
        insertV3Snapshot(ZERO_INITIAL_RENEWAL_SNAPSHOT_ID, "V66-OLD-ZERO-RENEWAL", "EXTERNAL_RENEWAL", ZERO_INITIAL_RENEWAL_ID,
            "399.00", "190.40", "96.88", "18.17", "12.11", "79.80", "66.60");
        update("UPDATE external_order_renewal_event SET settlement_snapshot_id = ? WHERE id = ?",
            ZERO_INITIAL_RENEWAL_SNAPSHOT_ID, ZERO_INITIAL_RENEWAL_ID);

        insertV3Snapshot(ORPHAN_SNAPSHOT_ID, "V66-OLD-ORPHAN", "EXTERNAL_ORDER", 9_660_999L,
            "399.00", "200.00", "87.28", "16.37", "10.91", "79.80", "60.00");

        insertIncomeRows("EXTERNAL_ORDER", ORDER_ID, INITIAL_SNAPSHOT_ID, "INITIAL",
            "19.95", "11.97", "16.37", "10.91", "79.80", "60.00");
        insertIncomeRows("EXTERNAL_RENEWAL", RENEWAL_ID, RENEWAL_SNAPSHOT_ID, "RENEWAL",
            "19.95", "11.97", "15.09", "10.06", "79.80", "55.33");
        insertIncomeRows("EXTERNAL_ORDER", ZERO_INITIAL_ORDER_ID, ZERO_INITIAL_SNAPSHOT_ID, "ZERO-INITIAL",
            "19.95", "11.97", "16.37", "10.91", "79.80", "60.00");
        insertIncomeRows("EXTERNAL_RENEWAL", ZERO_INITIAL_RENEWAL_ID, ZERO_INITIAL_RENEWAL_SNAPSHOT_ID, "ZERO-RENEWAL",
            "19.95", "11.97", "18.17", "12.11", "79.80", "66.60");
    }

    private void insertOrder(long id, String recordNo, String startedAt, String expectedReturnAt) throws SQLException {
        update("""
            INSERT INTO external_rental_order (
              id, record_no, source_platform, merchant_id, store_id, store_sku_id, sku_id, package_id,
              customer_name, customer_phone, order_status, external_rental_amount, verification_amount,
              settlement_snapshot_id, sign_fee_amount, deposit_amount, lease_unit, lease_value,
              total_periods, lease_multiplier, auto_renew_enabled, rent_started_at, expected_return_at
            ) VALUES (?, ?, 'OTHER', 1, 1, 2, 2, 2,
              '日价迁移测试', '13800000000', 'ACTIVE', 399.00, 399.00,
              NULL, 0.00, 0.00, 'DAY', 30, 1, 1, 0, ?, ?)
            """, id, recordNo, startedAt, expectedReturnAt);
    }

    private void insertRenewal(
        long id,
        long externalOrderId,
        String eventNo,
        String periodStartAt,
        String periodEndAt,
        String batteryCost
    ) throws SQLException {
        update("""
            INSERT INTO external_order_renewal_event (
              id, external_order_id, event_no, period_no, period_start_at, period_end_at,
              renewal_amount, system_renewal_amount, battery_cost_amount, settlement_snapshot_id,
              event_status, renewal_source
            ) VALUES (?, ?, ?, 2, ?, ?, 399.00, 399.00, ?, NULL, 'ACCRUED', 'SYSTEM')
            """, id, externalOrderId, eventNo, periodStartAt, periodEndAt, new BigDecimal(batteryCost));
    }

    private void insertV3Snapshot(
        long id,
        String snapshotNo,
        String sourceType,
        long sourceId,
        String gross,
        String battery,
        String distributable,
        String operation,
        String maintenance,
        String referral,
        String investor
    ) throws SQLException {
        var grossAmount = new BigDecimal(gross);
        var channelFee = grossAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        var platformFee = grossAmount.multiply(new BigDecimal("0.03")).setScale(2, RoundingMode.HALF_UP);
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
            ) VALUES (?, ?, ?, ?, 'PROFIT_V3', 'DIRECT',
              2, 2, 1, 1, 1, 'STORE',
              ?, ?, 0.0500, ?,
              0.0300, ?, ?, ?,
              0.1500, ?, 0.1000, ?,
              0.2000, ?, 0.5500, ?,
              0.1500, ?, 0.0300,
              ?, 0.5500, ?,
              ?, ?, 'V66 migration fixture')
            """,
            id, snapshotNo, sourceType, sourceId,
            grossAmount, grossAmount, channelFee,
            platformFee, new BigDecimal(battery), new BigDecimal(distributable),
            new BigDecimal(operation), new BigDecimal(maintenance),
            new BigDecimal(referral), new BigDecimal(investor),
            new BigDecimal(operation), platformFee, new BigDecimal(investor),
            new BigDecimal(maintenance), new BigDecimal(investor));
    }

    private void insertIncomeRows(
        String sourceType,
        long sourceId,
        long snapshotId,
        String suffix,
        String channelFee,
        String platformFee,
        String operation,
        String maintenance,
        String referral,
        String investor
    ) throws SQLException {
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-CHANNEL-FEE", "CHANNEL", 0L,
            "CHANNEL_VERIFICATION_FEE", channelFee);
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-PLATFORM-FEE", "PLATFORM", 0L,
            "PLATFORM_SERVICE_FEE", platformFee);
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-OPERATION", "MERCHANT", 1L,
            "STORE_OPERATION_SHARE", operation);
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-MAINTENANCE", "MERCHANT", 1L,
            "MAINTENANCE_FUND_SHARE", maintenance);
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-REFERRAL", "CHANNEL", 0L,
            "CHANNEL_REFERRAL_SHARE", referral);
        insertIncome(sourceType, sourceId, snapshotId, suffix + "-INVESTOR", "INVESTOR", 0L,
            "INVESTOR_SHARE", investor);
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
            """, "INC-V66-" + entrySuffix, sourceType, sourceId, "SRC-" + sourceId, snapshotId,
            beneficiaryType, beneficiaryId, lineType, new BigDecimal(amount));
    }

    private void assertSnapshot(
        long snapshotId,
        String battery,
        String distributable,
        String operation,
        String maintenance,
        String referral,
        String investor
    ) throws SQLException {
        assertThat(singleMoney("SELECT battery_cost_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(battery);
        assertThat(singleMoney("SELECT distributable_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(distributable);
        assertThat(singleMoney("SELECT store_operation_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(operation);
        assertThat(singleMoney("SELECT maintenance_fund_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(maintenance);
        assertThat(singleMoney("SELECT channel_referral_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(referral);
        assertThat(singleMoney("SELECT investor_share_amount FROM settlement_rule_snapshot WHERE id = ?", snapshotId))
            .isEqualByComparingTo(investor);
    }

    private void assertNoV66BusinessWrites() throws SQLException {
        assertThat(singleLong("SELECT COUNT(*) FROM settlement_rule_snapshot WHERE snapshot_no LIKE 'SNP-V66-%'"))
            .isZero();
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM settlement_battery_recalculation_audit
            WHERE migration_code = 'V66'
            """)).isZero();
    }

    private long currentOrderSnapshotId(long orderId) throws SQLException {
        return singleLong("SELECT settlement_snapshot_id FROM external_rental_order WHERE id = ?", orderId);
    }

    private long currentRenewalSnapshotId(long renewalId) throws SQLException {
        return singleLong("SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?", renewalId);
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
