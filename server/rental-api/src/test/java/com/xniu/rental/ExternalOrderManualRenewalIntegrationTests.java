package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.service.ExternalOrderAutoRenewalService;
import com.xniu.rental.externalorder.service.ExternalOrderManualRenewalService;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ExternalOrderManualRenewalIntegrationTests {

    @Autowired
    private ExternalOrderManualRenewalService manualRenewalService;

    @Autowired
    private ExternalOrderAutoRenewalService autoRenewalService;

    @Autowired
    private ExternalRentalOrderService externalOrderService;

    @Autowired
    private SettlementStatementService settlementStatementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 2");
        jdbcTemplate.update("""
            UPDATE product_sku
            SET battery_cost_daily_amount = 6.80,
                battery_cost_monthly_amount = 200.00
            WHERE id = 2
            """);
        AuthContext.set(adminAccount());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void manualRenewalShouldUseExactPeriodGrossAndRemainImmutable() {
        var initialStart = LocalDateTime.of(2026, 8, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(20).plusHours(12));
        assertThat(snapshotAmount(order.settlementSnapshotId(), "battery_cost_amount"))
            .isEqualByComparingTo("139.40");

        var previousExpectedReturnAt = order.expectedReturnAt();
        var periodEndAt = previousExpectedReturnAt.plusDays(20);
        var renewal = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            previousExpectedReturnAt,
            periodEndAt,
            new BigDecimal("300.00"),
            "  线下已核销续租  "
        ));

        assertThat(renewal.periodStartAt()).isEqualTo(previousExpectedReturnAt);
        assertThat(renewal.periodEndAt()).isEqualTo(periodEndAt);
        assertThat(renewal.renewalAmount()).isEqualByComparingTo("300.00");
        assertThat(renewal.batteryCostAmount()).isEqualByComparingTo("136.00");
        assertThat(renewal.renewalSource()).isEqualTo("MANUAL");
        assertThat(renewal.operatorAccountId()).isEqualTo(1L);
        assertThat(renewal.remark()).isEqualTo("线下已核销续租");
        assertThat(expectedReturnAt(order.id())).isEqualTo(periodEndAt);

        var snapshotId = jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            renewal.id()
        );
        assertThat(snapshotAmount(snapshotId, "settlement_base_amount")).isEqualByComparingTo("300.00");
        assertThat(snapshotAmount(snapshotId, "channel_fee_amount")).isEqualByComparingTo("15.00");
        assertThat(snapshotAmount(snapshotId, "platform_fee_amount")).isEqualByComparingTo("9.00");
        assertThat(snapshotAmount(snapshotId, "battery_cost_amount")).isEqualByComparingTo("136.00");
        assertThat(snapshotAmount(snapshotId, "channel_referral_amount")).isEqualByComparingTo("60.00");
        assertThat(snapshotAmount(snapshotId, "distributable_amount")).isEqualByComparingTo("80.00");
        assertThat(snapshotAmount(snapshotId, "store_operation_amount")).isEqualByComparingTo("15.00");
        assertThat(snapshotAmount(snapshotId, "maintenance_fund_amount")).isEqualByComparingTo("10.00");
        assertThat(snapshotAmount(snapshotId, "investor_share_amount")).isEqualByComparingTo("55.00");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT calculation_version FROM settlement_rule_snapshot WHERE id = ?",
            String.class,
            snapshotId
        )).isEqualTo("PROFIT_V3");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND line_type IN ('MERCHANT_ORDER_FEE', 'PLATFORM_ORDER_FEE_SERVICE_FEE')
            """, Integer.class, renewal.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_rental_order_log
            WHERE external_order_id = ? AND operation_type = 'MANUAL_RENEW'
            """, Integer.class, order.id())).isEqualTo(1);

        // The same requested end cannot create a second period. The order-row
        // lock serializes concurrent calls, so the second caller observes the
        // already-advanced boundary and fails this same validation.
        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            previousExpectedReturnAt,
            periodEndAt,
            new BigDecimal("300.00"),
            "重复提交"
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("起点已变化");
        assertThat(eventCount(order.id())).isEqualTo(1);

        // A manual event is a frozen one-off fact. Verification timeline
        // reconciliation must not replace its gross amount.
        assertThat(autoRenewalService.reconcilePendingEvents(order.id())).isZero();
        assertThat(eventAmount(renewal.id())).isEqualByComparingTo("300.00");

        // Settling an earlier manual event does not prevent appending the next
        // event from the order's current paid-through boundary.
        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = 'SETTLED'
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, renewal.id());
        var next = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            periodEndAt,
            periodEndAt.plusDays(1),
            new BigDecimal("20.00"),
            "续租一天"
        ));
        assertThat(next.periodStartAt()).isEqualTo(periodEndAt);
        assertThat(next.batteryCostAmount()).isEqualByComparingTo("6.80");
        assertThat(eventCount(order.id())).isEqualTo(2);

        // A one-off manual gross does not replace the order's system renewal
        // rule. The following due period is system-sourced and uses the current
        // system amount and generated duration.
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET auto_renew_enabled = 1,
                renewal_unit = 'DAY',
                renewal_value = 31,
                renewal_amount = 300.00
            WHERE id = ?
            """, order.id());
        assertThat(autoRenewalService.accrueDueOrder(order.id(), next.periodEndAt())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT renewal_source
            FROM external_order_renewal_event
            WHERE external_order_id = ? AND period_no = 3
            """, String.class, order.id())).isEqualTo("SYSTEM");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT renewal_amount
            FROM external_order_renewal_event
            WHERE external_order_id = ? AND period_no = 3
            """, BigDecimal.class, order.id())).isEqualByComparingTo("300.00");
        assertThat(expectedReturnAt(order.id())).isEqualTo(next.periodEndAt().plusDays(31));
    }

    @Test
    void underfundedManualRenewalShouldLeaveOrderAndLedgerUnchanged() {
        var initialStart = LocalDateTime.of(2026, 8, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(30));
        var previousExpectedReturnAt = order.expectedReturnAt();

        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            previousExpectedReturnAt,
            previousExpectedReturnAt.plusDays(20).plusHours(12),
            new BigDecimal("100.00"),
            "毛额不足的续租"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不足以覆盖");

        assertThat(expectedReturnAt(order.id())).isEqualTo(previousExpectedReturnAt);
        assertThat(eventCount(order.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_rental_order_log
            WHERE external_order_id = ? AND operation_type = 'MANUAL_RENEW'
            """, Integer.class, order.id())).isZero();
    }

    @Test
    void lockedOccurrenceMonthShouldRejectBeforeAnyRenewalWrite() {
        var initialStart = LocalDateTime.of(2026, 8, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(30));
        var previousExpectedReturnAt = order.expectedReturnAt();
        var suffix = Long.toUnsignedString(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO settlement_statement
            (statement_no, statement_month, beneficiary_type, beneficiary_id, merchant_id, store_id, status)
            VALUES (?, '2026-08', 'MERCHANT', ?, 1, 1, 'CONFIRMED')
            """, "STM-LOCKED-MANUAL-" + suffix, Long.parseLong(suffix.substring(Math.max(0, suffix.length() - 9))));

        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            previousExpectedReturnAt,
            previousExpectedReturnAt.plusDays(20),
            new BigDecimal("300.00"),
            "锁定月不允许补记"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("月份已锁定");

        assertThat(expectedReturnAt(order.id())).isEqualTo(previousExpectedReturnAt);
        assertThat(eventCount(order.id())).isZero();
    }

    @Test
    void automaticRenewalShouldBeSystemSourcedAndUseExactGeneratedPeriod() {
        var initialStart = LocalDateTime.of(2026, 7, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(30));
        var dueAt = order.expectedReturnAt();
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET auto_renew_enabled = 1,
                renewal_unit = 'DAY',
                renewal_value = 31,
                renewal_amount = 300.00
            WHERE id = ?
            """, order.id());

        assertThat(autoRenewalService.accrueDueOrder(order.id(), dueAt)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT renewal_source
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            """, String.class, order.id())).isEqualTo("SYSTEM");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT battery_cost_amount
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            """, BigDecimal.class, order.id())).isEqualByComparingTo("210.80");
        assertThat(expectedReturnAt(order.id())).isEqualTo(dueAt.plusDays(31));
    }

    @Test
    void manualRenewalShouldReplaceJustAccruedSystemTailAndRebuildMutableLedger() {
        var initialStart = LocalDateTime.of(2098, 10, 1, 10, 0);
        var initialEnd = initialStart.plusDays(30);
        var order = createFirstPriceIntegratedOrder(initialStart, initialEnd, "replace-system-tail");

        assertThat(order.renewalAmount()).isEqualByComparingTo("129.00");
        assertThat(autoRenewalService.accrueDueOrder(order.id(), initialEnd)).isEqualTo(1);
        var before = jdbcTemplate.queryForMap("""
            SELECT id, event_no, period_no, period_start_at, period_end_at,
                   renewal_amount, system_renewal_amount, settlement_snapshot_id
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            """, order.id());
        var eventId = ((Number) before.get("id")).longValue();
        var previousSnapshotId = ((Number) before.get("settlement_snapshot_id")).longValue();
        assertThat(before.get("period_start_at")).isEqualTo(initialEnd);
        assertThat((BigDecimal) before.get("renewal_amount")).isEqualByComparingTo("129.00");
        assertThat((BigDecimal) before.get("system_renewal_amount")).isEqualByComparingTo("129.00");

        settlementStatementService.generateMonth("2098-10");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line line_row
            JOIN settlement_statement statement_row ON statement_row.id = line_row.statement_id
            WHERE line_row.source_type = 'EXTERNAL_RENEWAL'
              AND line_row.source_id = ?
              AND statement_row.status = 'DRAFT'
            """, Integer.class, eventId)).isPositive();

        var manualEnd = initialEnd.plusDays(20);
        var replacement = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            initialEnd,
            manualEnd,
            new BigDecimal("96.00"),
            "自动任务先记账后补录人工核销"
        ));

        assertThat(replacement.id()).isEqualTo(eventId);
        assertThat(replacement.eventNo()).isEqualTo(before.get("event_no"));
        assertThat(replacement.periodNo()).isEqualTo(((Number) before.get("period_no")).intValue());
        assertThat(replacement.periodStartAt()).isEqualTo(initialEnd);
        assertThat(replacement.periodEndAt()).isEqualTo(manualEnd);
        assertThat(replacement.renewalSource()).isEqualTo("MANUAL");
        assertThat(replacement.renewalAmount()).isEqualByComparingTo("96.00");
        assertThat(replacement.batteryCostAmount()).isEqualByComparingTo("0.00");
        assertThat(expectedReturnAt(order.id())).isEqualTo(manualEnd);
        assertThat(eventCount(order.id())).isEqualTo(1);

        var currentSnapshotId = jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            eventId
        );
        assertThat(currentSnapshotId).isNotEqualTo(previousSnapshotId);
        assertThat(snapshotAmount(currentSnapshotId, "rental_amount")).isEqualByComparingTo("96.00");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_rule_snapshot
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM settlement_rule_snapshot WHERE id = ?",
            Integer.class,
            previousSnapshotId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND entry_status = 'PENDING'
              AND snapshot_id = ?
            """, Integer.class, eventId, currentSnapshotId)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND snapshot_id <> ?
            """, Integer.class, eventId, currentSnapshotId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_renewal_investor_allocation
            WHERE renewal_event_id = ? AND settlement_snapshot_id = ?
              AND effective_start_at = ? AND effective_end_at = ?
            """, Integer.class, eventId, currentSnapshotId, initialEnd, manualEnd)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_renewal_investor_allocation
            WHERE renewal_event_id = ? AND settlement_snapshot_id = ?
            """, Integer.class, eventId, previousSnapshotId)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line line_row
            JOIN settlement_statement statement_row ON statement_row.id = line_row.statement_id
            WHERE line_row.source_type = 'EXTERNAL_RENEWAL'
              AND line_row.source_id = ?
              AND statement_row.status = 'DRAFT'
              AND line_row.remark LIKE '%人工续租%'
            """, Integer.class, eventId)).isPositive();

        var incomeCountAfterReplacement = jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId);
        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            initialEnd,
            manualEnd,
            new BigDecimal("96.00"),
            "重复提交不得生成第二条流水"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("起点已变化");
        assertThat(eventCount(order.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId)).isEqualTo(incomeCountAfterReplacement);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_rule_snapshot
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId)).isEqualTo(2);

        assertThat(autoRenewalService.accrueDueOrder(order.id(), manualEnd)).isEqualTo(1);
        var nextSystem = jdbcTemplate.queryForMap("""
            SELECT renewal_source, renewal_amount, period_start_at, period_end_at
            FROM external_order_renewal_event
            WHERE external_order_id = ? AND period_no = 2
            """, order.id());
        assertThat(nextSystem.get("renewal_source")).isEqualTo("SYSTEM");
        assertThat((BigDecimal) nextSystem.get("renewal_amount")).isEqualByComparingTo("129.00");
        assertThat(nextSystem.get("period_start_at")).isEqualTo(manualEnd);
        assertThat(nextSystem.get("period_end_at")).isEqualTo(manualEnd.plusDays(30));
    }

    @Test
    void settledSystemRenewalIncomeShouldBlockManualReplacementWithoutChangingLedger() {
        var initialStart = LocalDateTime.of(2098, 12, 1, 10, 0);
        var initialEnd = initialStart.plusDays(30);
        var order = createFirstPriceIntegratedOrder(initialStart, initialEnd, "locked-system-tail");
        assertThat(autoRenewalService.accrueDueOrder(order.id(), initialEnd)).isEqualTo(1);
        var event = jdbcTemplate.queryForMap("""
            SELECT id, period_end_at, renewal_amount, system_renewal_amount,
                   settlement_snapshot_id, renewal_source
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            """, order.id());
        var eventId = ((Number) event.get("id")).longValue();
        var snapshotId = ((Number) event.get("settlement_snapshot_id")).longValue();
        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = 'SETTLED', settled_at = CURRENT_TIMESTAMP(6)
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            LIMIT 1
            """, eventId);

        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            initialEnd,
            initialEnd.plusDays(20),
            new BigDecimal("96.00"),
            "已锁定系统续租不得覆盖"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("收益已锁定");

        var unchanged = jdbcTemplate.queryForMap("""
            SELECT period_end_at, renewal_amount, system_renewal_amount,
                   settlement_snapshot_id, renewal_source
            FROM external_order_renewal_event
            WHERE id = ?
            """, eventId);
        assertThat(unchanged).isEqualTo(event.entrySet().stream()
            .filter(entry -> !"id".equals(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                java.util.Map.Entry::getValue
            )));
        assertThat(expectedReturnAt(order.id())).isEqualTo(event.get("period_end_at"));
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM settlement_rule_snapshot
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM settlement_rule_snapshot WHERE id = ?",
            Integer.class,
            snapshotId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM external_rental_order_log
            WHERE external_order_id = ? AND operation_type = 'MANUAL_RENEW'
            """, Integer.class, order.id())).isZero();
    }

    @Test
    void lockedSystemRenewalMonthShouldBlockManualReplacementWithoutChangingEvent() {
        var initialStart = LocalDateTime.of(2099, 1, 2, 10, 0);
        var initialEnd = initialStart.plusDays(30);
        var order = createFirstPriceIntegratedOrder(initialStart, initialEnd, "locked-system-month");
        assertThat(autoRenewalService.accrueDueOrder(order.id(), initialEnd)).isEqualTo(1);
        var eventId = jdbcTemplate.queryForObject(
            "SELECT id FROM external_order_renewal_event WHERE external_order_id = ?",
            Long.class,
            order.id()
        );
        var snapshotId = jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            eventId
        );
        var suffix = Long.toUnsignedString(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO settlement_statement
            (statement_no, statement_month, beneficiary_type, beneficiary_id, merchant_id, store_id, status)
            VALUES (?, '2099-02', 'MERCHANT', ?, 1, 1, 'CONFIRMED')
            """, "STM-LOCKED-SYSTEM-REPLACE-" + suffix,
            Long.parseLong(suffix.substring(Math.max(0, suffix.length() - 9))));

        assertThatThrownBy(() -> manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            initialEnd,
            initialEnd.plusDays(20),
            new BigDecimal("96.00"),
            "锁定月结不得覆盖系统续租"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("锁定月结");

        assertThat(eventCount(order.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT renewal_source FROM external_order_renewal_event WHERE id = ?",
            String.class,
            eventId
        )).isEqualTo("SYSTEM");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            eventId
        )).isEqualTo(snapshotId);
        assertThat(expectedReturnAt(order.id())).isEqualTo(initialEnd.plusDays(30));
    }

    @Test
    void manualActualAmountAndDurationApplyOnceThenAutomaticRenewalUsesFirstPrice() {
        jdbcTemplate.update("""
            UPDATE store_sku_package
            SET rental_amount = 129.00,
                period_amount = 99.00,
                auto_renew_enabled = 1,
                renewal_unit = 'MONTH',
                renewal_value = 1,
                renewal_amount = 99.00,
                renewal_billing_mode = 'PERIOD'
            WHERE store_sku_id = 1 AND package_id = 2
            """);
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        var initialStart = LocalDateTime.of(2026, 9, 1, 10, 0);
        var initialEnd = initialStart.plusDays(30);
        var suffix = Long.toUnsignedString(System.nanoTime());
        var assetId = createIntegratedAsset(suffix);
        var order = externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "MANUAL-THEN-AUTO-" + suffix,
            1L,
            2L,
            1,
            "人工后恢复自动客户",
            "138" + suffix.substring(Math.max(0, suffix.length() - 8)),
            initialStart,
            initialEnd,
            assetId,
            null,
            new BigDecimal("129.00"),
            new BigDecimal("96.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "129/99/96 分离测试"
        ));
        assertThat(order.renewalAmount()).isEqualByComparingTo("129.00");

        var manualEnd = initialEnd.plusDays(20);
        var manual = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            initialEnd,
            manualEnd,
            new BigDecimal("96.00"),
            "人工核销96元并延长20天"
        ));
        assertThat(manual.renewalSource()).isEqualTo("MANUAL");
        assertThat(manual.renewalAmount()).isEqualByComparingTo("96.00");
        assertThat(manual.periodEndAt()).isEqualTo(manualEnd);

        assertThat(autoRenewalService.accrueDueOrder(order.id(), manualEnd)).isEqualTo(1);
        var events = jdbcTemplate.queryForList("""
            SELECT renewal_source, renewal_amount, period_start_at, period_end_at
            FROM external_order_renewal_event
            WHERE external_order_id = ?
            ORDER BY period_no
            """, order.id());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("renewal_source")).isEqualTo("MANUAL");
        assertThat((BigDecimal) events.get(0).get("renewal_amount")).isEqualByComparingTo("96.00");
        assertThat(events.get(1).get("renewal_source")).isEqualTo("SYSTEM");
        assertThat((BigDecimal) events.get(1).get("renewal_amount")).isEqualByComparingTo("129.00");
        assertThat(events.get(1).get("period_start_at")).isEqualTo(manualEnd);
        assertThat(events.get(1).get("period_end_at")).isEqualTo(manualEnd.plusDays(30));
    }

    @Test
    void batteryPayableShouldIncludeInitialAndRenewalWithoutGeneratedStatements() {
        var initialStart = LocalDateTime.of(2099, 8, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(20).plusHours(12));
        jdbcTemplate.update(
            "UPDATE external_rental_order SET created_at = '2099-08-05 09:00:00' WHERE id = ?",
            order.id()
        );
        var renewal = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            order.expectedReturnAt(),
            order.expectedReturnAt().plusDays(7),
            new BigDecimal("300.00"),
            "七天续租电池费"
        ));

        var payable = settlementStatementService.adminBatteryPayable("2099-08", order.storeId());

        assertThat(payable.statementMonth()).isEqualTo("2099-08");
        assertThat(payable.storeId()).isEqualTo(order.storeId());
        assertThat(payable.initialAmount()).isEqualByComparingTo("139.40");
        assertThat(payable.renewalAmount()).isEqualByComparingTo("47.60");
        assertThat(payable.billAmount()).isEqualByComparingTo("0.00");
        assertThat(payable.totalAmount()).isEqualByComparingTo("187.00");
        assertThat(payable.initialCount()).isEqualTo(1);
        assertThat(payable.renewalCount()).isEqualTo(1);
        assertThat(payable.billCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM settlement_statement WHERE statement_month = '2099-08'",
            Integer.class
        )).isZero();
        assertThat(renewal.batteryCostAmount()).isEqualByComparingTo("47.60");

        var otherStore = settlementStatementService.adminBatteryPayable("2099-08", 999L);
        assertThat(otherStore.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(otherStore.initialCount()).isZero();
        assertThat(otherStore.renewalCount()).isZero();

        AuthContext.set(new CurrentAccount(
            "merchant-battery-payable-token",
            new CurrentAccountResponse(
                3L,
                "STORE_MANAGER",
                "battery_store_manager",
                "18800000003",
                null,
                "Battery Store Manager",
                1L,
                1L,
                null,
                List.of("STORE_MANAGER"),
                List.of("settlement.read"),
                List.of(new StoreScopeResponse(1L, 1L, "SINGLE_STORE"))
            )
        ));
        assertThat(settlementStatementService.merchantBatteryPayable("2099-08", 1L).totalAmount())
            .isEqualByComparingTo("187.00");
        assertThatThrownBy(() -> settlementStatementService.merchantBatteryPayable("2099-08", 999L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    @Test
    void underfundedAutomaticRenewalShouldNotCreateAnUnbalancedLedger() {
        var initialStart = LocalDateTime.of(2026, 7, 1, 10, 0);
        var order = createBatteryOrder(initialStart, initialStart.plusDays(30));
        var dueAt = order.expectedReturnAt();
        jdbcTemplate.update("""
            UPDATE external_rental_order
            SET auto_renew_enabled = 1,
                renewal_unit = 'DAY',
                renewal_value = 20,
                renewal_amount = 100.00
            WHERE id = ?
            """, order.id());

        assertThatThrownBy(() -> autoRenewalService.accrueDueOrder(order.id(), dueAt))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不足以覆盖");

        assertThat(eventCount(order.id())).isZero();
        assertThat(expectedReturnAt(order.id())).isEqualTo(dueAt);
    }

    private com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse createBatteryOrder(
        LocalDateTime startedAt,
        LocalDateTime expectedReturnAt
    ) {
        var suffix = Long.toUnsignedString(System.nanoTime());
        var batteryAssetId = createBatteryAsset(suffix);
        return externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "MANUAL-RENEW-" + suffix,
            2L,
            4L,
            "人工续租客户",
            "138" + suffix.substring(Math.max(0, suffix.length() - 8)),
            startedAt,
            expectedReturnAt,
            null,
            batteryAssetId,
            new BigDecimal("399.00"),
            new BigDecimal("399.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "人工续租集成测试"
        ));
    }

    private com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse createFirstPriceIntegratedOrder(
        LocalDateTime startedAt,
        LocalDateTime expectedReturnAt,
        String marker
    ) {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        jdbcTemplate.update("""
            UPDATE store_sku_package
            SET rental_amount = 129.00,
                period_amount = 99.00,
                auto_renew_enabled = 1,
                renewal_unit = 'MONTH',
                renewal_value = 1,
                renewal_amount = 99.00,
                renewal_billing_mode = 'PERIOD'
            WHERE store_sku_id = 1 AND package_id = 2
            """);
        var suffix = marker + "-" + Long.toUnsignedString(System.nanoTime());
        var assetId = createIntegratedAsset(suffix);
        return externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "MANUAL-RACE-" + suffix,
            1L,
            2L,
            1,
            "人工优先测试客户",
            "138" + Long.toUnsignedString(System.nanoTime()).substring(0, 8),
            startedAt,
            expectedReturnAt,
            assetId,
            null,
            new BigDecimal("129.00"),
            new BigDecimal("96.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "系统续租与人工续租竞态测试"
        ));
    }

    private Long createBatteryAsset(String suffix) {
        var assetCode = "A-manual-renew-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'BATTERY',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'),
                    ?, 1, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE)
            """, assetCode, "BATTERY-MANUAL-" + suffix);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            assetCode
        );
    }

    private Long createIntegratedAsset(String suffix) {
        var assetCode = "A-manual-then-auto-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, assetCode, "MANUAL-THEN-AUTO-" + suffix);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            assetCode
        );
    }

    private BigDecimal snapshotAmount(Long snapshotId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM settlement_rule_snapshot WHERE id = ?",
            BigDecimal.class,
            snapshotId
        );
    }

    private LocalDateTime expectedReturnAt(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT expected_return_at FROM external_rental_order WHERE id = ?",
            LocalDateTime.class,
            orderId
        );
    }

    private int eventCount(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM external_order_renewal_event WHERE external_order_id = ?",
            Integer.class,
            orderId
        );
    }

    private BigDecimal eventAmount(Long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT renewal_amount FROM external_order_renewal_event WHERE id = ?",
            BigDecimal.class,
            eventId
        );
    }

    private CurrentAccount adminAccount() {
        return new CurrentAccount(
            "manual-renewal-test-token",
            new CurrentAccountResponse(
                1L,
                "PLATFORM_ADMIN",
                "admin",
                "18800000001",
                null,
                "Platform Admin",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("system.admin", "order.read", "order.operate"),
                List.of()
            )
        );
    }
}
