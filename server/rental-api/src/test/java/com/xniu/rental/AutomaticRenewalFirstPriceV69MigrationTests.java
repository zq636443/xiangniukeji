package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomaticRenewalFirstPriceV69MigrationTests {

    private static final String SERVER_URL =
        "jdbc:mysql://localhost:3310/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    private static final long ELIGIBLE_ORDER_ID = 9_690_001L;
    private static final long MANUAL_APPLIED_ORDER_ID = 9_690_002L;
    private static final long MANUAL_PENDING_ORDER_ID = 9_690_003L;
    private static final long ORDER_EDIT_ORDER_ID = 9_690_004L;
    private static final long BACKFILL_ORDER_ID = 9_690_005L;
    private static final long COMPLETED_ORDER_ID = 9_690_006L;
    private static final long MANUAL_EVENT_ORDER_ID = 9_690_007L;
    private static final long PENDING_EVENT_ORDER_ID = 9_690_008L;
    private static final long SETTLED_EVENT_ORDER_ID = 9_690_009L;
    private static final long FROZEN_EVENT_ORDER_ID = 9_690_010L;
    private static final long LOCKED_STATEMENT_ORDER_ID = 9_690_011L;

    private static final long MANUAL_EVENT_ID = 9_691_007L;
    private static final long PENDING_EVENT_ID = 9_691_008L;
    private static final long SETTLED_EVENT_ID = 9_691_009L;
    private static final long FROZEN_EVENT_ID = 9_691_010L;
    private static final long LOCKED_STATEMENT_EVENT_ID = 9_691_011L;

    private String databaseName;
    private String databaseUrl;

    @BeforeEach
    void createDatabaseAtV68() throws SQLException {
        databaseName = "xniu_v69_" + UUID.randomUUID().toString().replace("-", "");
        databaseUrl = "jdbc:mysql://localhost:3310/" + databaseName
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        flyway(MigrationVersion.fromVersion("68")).migrate();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        if (databaseName == null || !databaseName.startsWith("xniu_v69_")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
    }

    @Test
    void shouldAlignAutomaticBaselineWithoutOverwritingManualOrLockedFactsAndRemainIdempotent()
        throws SQLException {
        seedProductAndOrders();
        seedHumanOverrides();
        seedRenewalEvents();

        flyway(null).migrate();

        assertThat(singleMoney("""
            SELECT rental_amount
            FROM store_sku_package
            WHERE store_sku_id = 1 AND package_id = 2
            """)).isEqualByComparingTo("129.00");
        assertThat(singleMoney("""
            SELECT renewal_amount
            FROM store_sku_package
            WHERE store_sku_id = 1 AND package_id = 2
            """)).isEqualByComparingTo("129.00");

        assertOrderRenewalAmount(ELIGIBLE_ORDER_ID, "129.00");
        assertOrderRenewalAmount(MANUAL_EVENT_ORDER_ID, "129.00");
        assertOrderRenewalAmount(PENDING_EVENT_ORDER_ID, "129.00");
        assertOrderRenewalAmount(SETTLED_EVENT_ORDER_ID, "129.00");
        assertOrderRenewalAmount(FROZEN_EVENT_ORDER_ID, "129.00");
        assertOrderRenewalAmount(LOCKED_STATEMENT_ORDER_ID, "129.00");

        assertOrderRenewalAmount(MANUAL_APPLIED_ORDER_ID, "99.00");
        assertOrderRenewalAmount(MANUAL_PENDING_ORDER_ID, "99.00");
        assertOrderRenewalAmount(ORDER_EDIT_ORDER_ID, "99.00");
        assertOrderRenewalAmount(BACKFILL_ORDER_ID, "99.00");
        assertOrderRenewalAmount(COMPLETED_ORDER_ID, "99.00");

        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE external_order_id = ?
              AND batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
              AND revision_status = 'APPLIED'
              AND confirmation_method = 'SYSTEM'
              AND previous_renewal_amount = 99.00
              AND new_renewal_amount = 129.00
            """, ELIGIBLE_ORDER_ID)).isEqualTo(1);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
            """)).isEqualTo(6);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE external_order_id IN (?, ?, ?, ?, ?)
              AND batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
            """, MANUAL_APPLIED_ORDER_ID, MANUAL_PENDING_ORDER_ID, ORDER_EDIT_ORDER_ID,
            BACKFILL_ORDER_ID, COMPLETED_ORDER_ID)).isZero();

        assertRenewalAmounts(MANUAL_EVENT_ID, "96.00", "99.00");
        assertRenewalAmounts(PENDING_EVENT_ID, "99.00", "129.00");
        assertRenewalAmounts(SETTLED_EVENT_ID, "99.00", "99.00");
        assertRenewalAmounts(FROZEN_EVENT_ID, "99.00", "99.00");
        assertRenewalAmounts(LOCKED_STATEMENT_EVENT_ID, "99.00", "99.00");

        var v69AuditCountBeforeReplay = singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
            """);
        update("DELETE FROM flyway_schema_history WHERE version = '69'");
        flyway(null).migrate();

        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
            """)).isEqualTo(v69AuditCountBeforeReplay);
        assertThat(singleLong("""
            SELECT COUNT(*)
            FROM external_order_pricing_revision
            WHERE batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
            GROUP BY external_order_id
            HAVING COUNT(*) > 1
            """)).isZero();
        assertOrderRenewalAmount(ELIGIBLE_ORDER_ID, "129.00");
        assertRenewalAmounts(MANUAL_EVENT_ID, "96.00", "99.00");
        assertRenewalAmounts(PENDING_EVENT_ID, "99.00", "129.00");
        assertRenewalAmounts(SETTLED_EVENT_ID, "99.00", "99.00");
        assertRenewalAmounts(FROZEN_EVENT_ID, "99.00", "99.00");
        assertRenewalAmounts(LOCKED_STATEMENT_EVENT_ID, "99.00", "99.00");
    }

    private void seedProductAndOrders() throws SQLException {
        update("""
            UPDATE store_sku_package
            SET auto_renew_enabled = 1,
                rental_amount = 129.00,
                renewal_amount = 99.00
            WHERE store_sku_id = 1
              AND package_id = 2
            """);

        insertOrder(ELIGIBLE_ORDER_ID, "ACTIVE");
        insertOrder(MANUAL_APPLIED_ORDER_ID, "ACTIVE");
        insertOrder(MANUAL_PENDING_ORDER_ID, "ACTIVE");
        insertOrder(ORDER_EDIT_ORDER_ID, "ACTIVE");
        insertOrder(BACKFILL_ORDER_ID, "ACTIVE");
        insertOrder(COMPLETED_ORDER_ID, "COMPLETED");
        insertOrder(MANUAL_EVENT_ORDER_ID, "ACTIVE");
        insertOrder(PENDING_EVENT_ORDER_ID, "ACTIVE");
        insertOrder(SETTLED_EVENT_ORDER_ID, "ACTIVE");
        insertOrder(FROZEN_EVENT_ORDER_ID, "ACTIVE");
        insertOrder(LOCKED_STATEMENT_ORDER_ID, "ACTIVE");
    }

    private void seedHumanOverrides() throws SQLException {
        insertPricingRevision(MANUAL_APPLIED_ORDER_ID, null, "APPLIED", "96.00");
        insertPricingRevision(
            MANUAL_PENDING_ORDER_ID,
            "MANUAL-PRICE-PENDING-V69-TEST",
            "PENDING_CUSTOMER_CONFIRMATION",
            "109.00"
        );
        insertVerificationRevision(ORDER_EDIT_ORDER_ID, "ORDER_EDIT");
        insertVerificationRevision(BACKFILL_ORDER_ID, "BACKFILL");
    }

    private void seedRenewalEvents() throws SQLException {
        insertRenewalEvent(MANUAL_EVENT_ID, MANUAL_EVENT_ORDER_ID, "MANUAL", "96.00", "99.00");
        insertRenewalEvent(PENDING_EVENT_ID, PENDING_EVENT_ORDER_ID, "SYSTEM", "99.00", "99.00");
        insertRenewalEvent(SETTLED_EVENT_ID, SETTLED_EVENT_ORDER_ID, "SYSTEM", "99.00", "99.00");
        insertRenewalEvent(FROZEN_EVENT_ID, FROZEN_EVENT_ORDER_ID, "SYSTEM", "99.00", "99.00");
        insertRenewalEvent(
            LOCKED_STATEMENT_EVENT_ID,
            LOCKED_STATEMENT_ORDER_ID,
            "SYSTEM",
            "99.00",
            "99.00"
        );

        insertIncome(PENDING_EVENT_ID, "PLATFORM_SERVICE_FEE", "PENDING");
        insertIncome(PENDING_EVENT_ID, "STORE_OPERATION_SHARE", "PENDING");
        insertIncome(SETTLED_EVENT_ID, "PLATFORM_SERVICE_FEE", "SETTLED");
        insertIncome(FROZEN_EVENT_ID, "PLATFORM_SERVICE_FEE", "FROZEN");
        insertIncome(LOCKED_STATEMENT_EVENT_ID, "PLATFORM_SERVICE_FEE", "PENDING");

        insertStatementLine(PENDING_EVENT_ID, 9_692_008L, "DRAFT", 80L);
        insertStatementLine(LOCKED_STATEMENT_EVENT_ID, 9_692_011L, "CONFIRMED", 110L);
    }

    private void insertOrder(long id, String status) throws SQLException {
        update("""
            INSERT INTO external_rental_order (
              id, record_no, source_platform, merchant_id, store_id, store_sku_id, sku_id, package_id,
              customer_name, customer_phone, order_status, external_rental_amount, verification_amount,
              settlement_snapshot_id, sign_fee_amount, deposit_amount, lease_unit, lease_value,
              total_periods, lease_multiplier, auto_renew_enabled, renewal_unit, renewal_value,
              renewal_amount, renewal_billing_mode, renewal_daily_amount, renewal_daily_cap_enabled,
              renewal_grace_hours, overdue_daily_amount, rent_started_at, expected_return_at
            ) VALUES (?, ?, 'OTHER', 1, 1, 1, 1, 2,
              '首月价续租迁移测试', '13800000000', ?, 129.00, 129.00,
              NULL, 0.00, 0.00, 'MONTH', 1,
              1, 1, 1, 'MONTH', 1,
              99.00, 'PERIOD', NULL, 1,
              0, NULL, '2026-08-01 00:00:00', '2026-08-31 00:00:00')
            """, id, "EXT-V69-" + id, status);
    }

    private void insertPricingRevision(
        long orderId,
        String batchNo,
        String status,
        String newAmount
    ) throws SQLException {
        update("""
            INSERT INTO external_order_pricing_revision (
              external_order_id, batch_no, revision_status, requires_customer_confirmation,
              previous_auto_renew_enabled, previous_renewal_unit, previous_renewal_value,
              previous_renewal_amount, previous_billing_mode, previous_daily_amount,
              previous_daily_cap_enabled, previous_grace_hours, previous_overdue_daily_amount,
              new_auto_renew_enabled, new_renewal_unit, new_renewal_value, new_renewal_amount,
              new_billing_mode, new_daily_amount, new_daily_cap_enabled, new_grace_hours,
              new_overdue_daily_amount, reason, confirmation_method, applied_at
            ) VALUES (?, ?, ?, 0,
              1, 'MONTH', 1,
              99.00, 'PERIOD', NULL,
              1, 0, NULL,
              1, 'MONTH', 1, ?,
              'PERIOD', NULL, 1, 0,
              NULL, '人工续租实际金额', 'MANUAL',
              CASE WHEN ? = 'APPLIED' THEN '2026-08-15 10:00:00' ELSE NULL END)
            """, orderId, batchNo, status, new BigDecimal(newAmount), status);
    }

    private void insertVerificationRevision(long orderId, String revisionType) throws SQLException {
        update("""
            INSERT INTO external_order_verification_revision (
              external_order_id, verification_amount, effective_at, revision_type,
              source_snapshot_id, operator_account_id
            ) VALUES (?, 96.00, '2026-08-15 10:00:00.000000', ?, NULL, 1)
            """, orderId, revisionType);
    }

    private void insertRenewalEvent(
        long id,
        long orderId,
        String source,
        String renewalAmount,
        String systemRenewalAmount
    ) throws SQLException {
        update("""
            INSERT INTO external_order_renewal_event (
              id, external_order_id, event_no, period_no, period_start_at, period_end_at,
              renewal_amount, system_renewal_amount, battery_cost_amount, settlement_snapshot_id,
              event_status, renewal_source, operator_account_id, remark
            ) VALUES (?, ?, ?, 2, '2026-08-31 00:00:00', '2026-09-30 00:00:00',
              ?, ?, 0.00, NULL, 'ACCRUED', ?,
              CASE WHEN ? = 'MANUAL' THEN 1 ELSE NULL END, 'V69 migration fixture')
            """, id, orderId, "ERN-V69-" + id, new BigDecimal(renewalAmount),
            new BigDecimal(systemRenewalAmount), source, source);
    }

    private void insertIncome(long renewalId, String lineType, String status) throws SQLException {
        update("""
            INSERT INTO settlement_income_entry (
              entry_no, source_type, source_id, source_no, order_id, snapshot_id,
              merchant_id, store_id, beneficiary_type, beneficiary_id, line_type,
              amount, entry_status, remark, occurred_at
            ) VALUES (?, 'EXTERNAL_RENEWAL', ?, ?, NULL, 1,
              1, 1, 'PLATFORM', 0, ?,
              1.00, ?, 'V69 migration fixture', '2026-09-01 00:00:00')
            """, "INC-V69-" + renewalId + "-" + lineType, renewalId,
            "ERN-V69-" + renewalId, lineType, status);
    }

    private void insertStatementLine(long renewalId, long statementId, String status, long beneficiaryId)
        throws SQLException {
        update("""
            INSERT INTO settlement_statement (
              id, statement_no, statement_month, beneficiary_type, beneficiary_id,
              merchant_id, store_id, status
            ) VALUES (?, ?, '2026-09', 'PLATFORM', ?, 1, 1, ?)
            """, statementId, "STM-V69-" + statementId, beneficiaryId, status);
        update("""
            INSERT INTO settlement_statement_line (
              line_no, statement_id, source_type, source_id, merchant_id, store_id,
              investor_id, line_type, amount, occurred_at, remark
            ) VALUES (?, ?, 'EXTERNAL_RENEWAL', ?, 1, 1,
              0, 'PLATFORM_SERVICE_FEE', 1.00, '2026-09-01 00:00:00', 'V69 migration fixture')
            """, "STML-V69-" + renewalId, statementId, renewalId);
    }

    private void assertOrderRenewalAmount(long orderId, String expected) throws SQLException {
        assertThat(singleMoney(
            "SELECT renewal_amount FROM external_rental_order WHERE id = ?",
            orderId
        )).isEqualByComparingTo(expected);
    }

    private void assertRenewalAmounts(long eventId, String expectedRenewal, String expectedSystem)
        throws SQLException {
        assertThat(singleMoney(
            "SELECT renewal_amount FROM external_order_renewal_event WHERE id = ?",
            eventId
        )).isEqualByComparingTo(expectedRenewal);
        assertThat(singleMoney(
            "SELECT system_renewal_amount FROM external_order_renewal_event WHERE id = ?",
            eventId
        )).isEqualByComparingTo(expectedSystem);
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
                if (!result.next()) {
                    return 0L;
                }
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
