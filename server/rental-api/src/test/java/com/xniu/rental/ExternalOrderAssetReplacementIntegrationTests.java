package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.dto.AssetInvestorChangeRequest;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.externalorder.service.ExternalOrderAssetReplacementService;
import com.xniu.rental.externalorder.service.ExternalOrderAutoRenewalService;
import com.xniu.rental.externalorder.service.ExternalOrderManualRenewalService;
import com.xniu.rental.externalorder.service.ExternalOrderRenewalAllocationService;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class ExternalOrderAssetReplacementIntegrationTests {

    private static final DateTimeFormatter STATEMENT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private ExternalRentalOrderService externalOrderService;

    @Autowired
    private ExternalOrderAssetReplacementService replacementService;

    @Autowired
    private ExternalOrderAutoRenewalService autoRenewalService;

    @Autowired
    private ExternalOrderManualRenewalService manualRenewalService;

    @Autowired
    private ExternalOrderRenewalRepository renewalRepository;

    @Autowired
    private SettlementStatementService statementService;

    @Autowired
    private SettlementIncomeService settlementIncomeService;

    @Autowired
    private ExternalOrderRenewalAllocationService renewalAllocationService;

    @Autowired
    private ExternalRentalOrderRepository externalOrderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id IN (1, 2)");
        jdbcTemplate.update("""
            UPDATE product_sku
            SET battery_cost_daily_amount = 6.80,
                battery_cost_monthly_amount = NULL
            WHERE id = 2
            """);
        jdbcTemplate.update("""
            UPDATE store_sku_package
            SET auto_renew_enabled = 1,
                renewal_unit = 'DAY',
                renewal_value = 30,
                renewal_amount = 400.00,
                renewal_billing_mode = 'PERIOD'
            WHERE store_sku_id = 2 AND package_id = 4
            """);
        AuthContext.set(adminAccount());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void replacementShouldSplitOnlyMutableInvestorAttributionAndKeepBillingFactInOriginalMonth() {
        var oldInvestorId = 1L;
        var newInvestorId = createInvestor("split");
        var oldAssetId = createAsset("split-old", "BATTERY", oldInvestorId);
        var newAssetId = createAsset("split-new", "BATTERY", newInvestorId);
        var now = databaseTime();
        var order = createDueBatteryOrder("split", oldAssetId, now.minusDays(40), now.minusDays(10));
        var initialSnapshotId = order.settlementSnapshotId();

        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = 'SETTLED', settled_at = CURRENT_TIMESTAMP(6)
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, order.id());
        assertThat(autoRenewalService.accrueDueOrder(order.id(), now)).isEqualTo(1);

        var before = onlyRenewal(order.id());
        var oldRenewalSnapshotId = before.settlementSnapshotId();
        var oldRenewalSnapshotCount = renewalSnapshotCount(before.id());
        var nonInvestorBefore = incomeByLine(before.id(), false);
        var investorTotalBefore = incomeTotal(before.id(), true);
        var month = before.periodStartAt().format(STATEMENT_MONTH);
        statementService.generateMonth(month);

        var change = replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, newAssetId, null, "履约中跨出资方更换第二资产"
        ));

        var after = onlyRenewal(order.id());
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.periodNo()).isEqualTo(before.periodNo());
        assertThat(after.periodStartAt()).isEqualTo(before.periodStartAt());
        assertThat(after.periodEndAt()).isEqualTo(before.periodEndAt());
        assertThat(after.renewalAmount()).isEqualByComparingTo(before.renewalAmount());
        assertThat(after.settlementSnapshotId()).isNotEqualTo(oldRenewalSnapshotId);
        assertThat(renewalSnapshotCount(before.id())).isEqualTo(oldRenewalSnapshotCount + 1);
        assertThat(rowCount("settlement_rule_snapshot", oldRenewalSnapshotId)).isEqualTo(1);

        assertThat(change.oldAssetId()).isEqualTo(oldAssetId);
        assertThat(change.newAssetId()).isEqualTo(newAssetId);
        assertThat(change.assetType()).isEqualTo("BATTERY");
        assertThat(change.oldAssetResultStatus()).isEqualTo("IDLE");
        assertThat(change.effectiveAt()).isAfter(before.periodStartAt()).isBefore(before.periodEndAt());
        assertThat(externalOrderService.getOrder(order.id()).batteryAssetId()).isEqualTo(newAssetId);
        assertThat(assetStatus(oldAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(newAssetId)).isEqualTo("RENTING");

        var timeline = jdbcTemplate.queryForList("""
            SELECT asset_id, investor_id, effective_start_at, effective_end_at,
                   allocation_weight, investor_share_amount
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            ORDER BY effective_start_at, id
            """, after.settlementSnapshotId());
        assertThat(timeline).hasSize(2);
        assertThat(((Number) timeline.get(0).get("asset_id")).longValue()).isEqualTo(oldAssetId);
        assertThat(((Number) timeline.get(1).get("asset_id")).longValue()).isEqualTo(newAssetId);
        assertThat(timeline.get(0).get("effective_start_at")).isEqualTo(before.periodStartAt());
        assertThat(timeline.get(0).get("effective_end_at")).isEqualTo(change.effectiveAt());
        assertThat(timeline.get(1).get("effective_start_at")).isEqualTo(change.effectiveAt());
        assertThat(timeline.get(1).get("effective_end_at")).isEqualTo(before.periodEndAt());
        assertThat(allocationTotal(after.settlementSnapshotId(), "allocation_weight"))
            .isEqualByComparingTo("1.000000000000");
        assertThat(allocationTotal(after.settlementSnapshotId(), "investor_share_amount"))
            .isEqualByComparingTo(investorTotalBefore);

        assertThat(incomeByLine(after.id(), false)).isEqualTo(nonInvestorBefore);
        assertMoneyMapEquals(
            investorIncomeByBeneficiary(after.id()),
            allocationByInvestor(after.settlementSnapshotId())
        );
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ? AND snapshot_id <> ?
            """, Integer.class, after.id(), after.settlementSnapshotId())).isZero();

        assertThat(jdbcTemplate.queryForList("""
            SELECT DISTINCT s.statement_month
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = 'EXTERNAL_RENEWAL' AND l.source_id = ?
            """, String.class, after.id())).containsExactly(month);
        assertMoneyMapEquals(
            statementInvestorByBeneficiary(after.id()),
            allocationByInvestor(after.settlementSnapshotId())
        );

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ? AND entry_status = 'SETTLED'
            """, Integer.class, order.id())).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_rental_order WHERE id = ?",
            Long.class,
            order.id()
        )).isEqualTo(initialSnapshotId);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM external_rental_order_log
            WHERE external_order_id = ? AND operation_type = 'REPLACE_ASSET'
            """, Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void repeatedReplacementShouldPreserveTimelineAndFutureManualAndAutomaticRenewalsUseCurrentAsset() {
        var middleInvestorId = createInvestor("repeat-middle");
        var finalInvestorId = createInvestor("repeat-final");
        var oldAssetId = createAsset("repeat-old", "BATTERY", 1L);
        var middleAssetId = createAsset("repeat-middle", "BATTERY", middleInvestorId);
        var finalAssetId = createAsset("repeat-final", "BATTERY", finalInvestorId);
        var now = databaseTime();
        var order = createDueBatteryOrder("repeat", oldAssetId, now.minusDays(40), now.minusDays(10));
        assertThat(autoRenewalService.accrueDueOrder(order.id(), now)).isEqualTo(1);

        replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, middleAssetId, "PENDING_REPAIR", "第一次更换"
        ));
        var marker = ";assetReplacement=true";
        var fullLengthSummary = "X".repeat(1024 - marker.length()) + marker;
        var firstReplacementSnapshotId = onlyRenewal(order.id()).settlementSnapshotId();
        jdbcTemplate.update(
            "UPDATE settlement_rule_snapshot SET rule_summary = ? WHERE id = ?",
            fullLengthSummary,
            firstReplacementSnapshotId
        );
        jdbcTemplate.queryForObject("SELECT SLEEP(0.01)", Integer.class);
        replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", middleAssetId, finalAssetId, null, "同一续租周期再次更换"
        ));

        var crossingEvent = renewalRepository.listByExternalOrder(order.id()).getFirst();
        var timelineAssets = jdbcTemplate.queryForList("""
            SELECT asset_id
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            ORDER BY effective_start_at, id
            """, Long.class, crossingEvent.settlementSnapshotId());
        assertThat(timelineAssets).containsExactly(oldAssetId, middleAssetId, finalAssetId);
        assertThat(allocationTotal(crossingEvent.settlementSnapshotId(), "allocation_weight"))
            .isEqualByComparingTo("1.000000000000");
        assertThat(allocationTotal(crossingEvent.settlementSnapshotId(), "investor_share_amount"))
            .isEqualByComparingTo(snapshotAmount(crossingEvent.settlementSnapshotId(), "investor_share_amount"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT rule_summary FROM settlement_rule_snapshot WHERE id = ?",
            String.class,
            crossingEvent.settlementSnapshotId()
        )).isEqualTo(fullLengthSummary);
        assertThat(eventCount(order.id())).isEqualTo(1);
        assertThat(assetStatus(oldAssetId)).isEqualTo("PENDING_REPAIR");
        assertThat(assetStatus(middleAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(finalAssetId)).isEqualTo("RENTING");

        var current = externalOrderService.getOrder(order.id());
        var manual = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            current.expectedReturnAt(), current.expectedReturnAt().plusDays(7), new BigDecimal("400.00"),
            "更换后人工续租"
        ));
        assertEventFullyAttributedTo(manual.id(), finalAssetId, finalInvestorId);

        assertThat(autoRenewalService.accrueDueOrder(order.id(), manual.periodEndAt())).isEqualTo(1);
        var automatic = renewalRepository.listByExternalOrder(order.id()).stream()
            .filter(event -> event.periodNo() == 3)
            .findFirst()
            .orElseThrow();
        assertThat(automatic.renewalSource().name()).isEqualTo("SYSTEM");
        assertEventFullyAttributedTo(automatic.id(), finalAssetId, finalInvestorId);
    }

    @Test
    void lockedRenewalIncomeShouldStayHistoricalWithoutBlockingPhysicalReplacement() {
        var targetInvestorId = createInvestor("rollback");
        var oldAssetId = createAsset("rollback-old", "BATTERY", 1L);
        var targetAssetId = createAsset("rollback-target", "BATTERY", targetInvestorId);
        var now = databaseTime();
        var order = createDueBatteryOrder("rollback", oldAssetId, now.minusDays(40), now.minusDays(10));
        assertThat(autoRenewalService.accrueDueOrder(order.id(), now)).isEqualTo(1);
        var event = onlyRenewal(order.id());
        var snapshotCount = renewalSnapshotCount(event.id());
        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = 'FROZEN'
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND beneficiary_type = 'INVESTOR'
            """, event.id());

        var change = replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, targetAssetId, null, "锁定账务不允许改写"
        ));

        assertThat(externalOrderService.getOrder(order.id()).batteryAssetId()).isEqualTo(targetAssetId);
        assertThat(assetStatus(oldAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(targetAssetId)).isEqualTo("RENTING");
        assertThat(onlyRenewal(order.id()).settlementSnapshotId()).isEqualTo(event.settlementSnapshotId());
        assertThat(renewalSnapshotCount(event.id())).isEqualTo(snapshotCount);
        assertThat(change.remark()).contains("已保留 1 条锁定续租周期的原分润归属");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM external_order_asset_change WHERE external_order_id = ?",
            Integer.class,
            order.id()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1) FROM external_rental_order_log
            WHERE external_order_id = ? AND operation_type = 'REPLACE_ASSET'
            """, Integer.class, order.id())).isEqualTo(1);

        var current = externalOrderService.getOrder(order.id());
        var nextRenewal = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            current.expectedReturnAt(), current.expectedReturnAt().plusDays(7), new BigDecimal("400.00"),
            "锁定周期后的下一期"
        ));
        assertEventFullyAttributedTo(nextRenewal.id(), targetAssetId, targetInvestorId);
        assertThat(eventCount(order.id())).isEqualTo(2);
    }

    @Test
    void replacementShouldRegenerateAnExistingDraftMonthForANewlyAccruedRenewal() {
        var targetInvestorId = createInvestor("draft-month");
        var oldAssetId = createAsset("draft-old", "BATTERY", 1L);
        var targetAssetId = createAsset("draft-target", "BATTERY", targetInvestorId);
        var unrelatedAssetId = createAsset("draft-unrelated", "BATTERY", 1L);
        var now = databaseTime();
        var order = createDueBatteryOrder("draft-due", oldAssetId, now.minusDays(31), now.minusDays(1));
        var periodStartAt = order.expectedReturnAt();
        createDueBatteryOrder(
            "draft-source", unrelatedAssetId, now.minusHours(2), now.plusDays(30)
        );
        var occurrenceMonth = periodStartAt.format(STATEMENT_MONTH);
        statementService.generateMonth(occurrenceMonth);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement
            WHERE statement_month = ? AND status IN ('DRAFT', 'RECONCILING')
            """, Integer.class, occurrenceMonth)).isGreaterThan(0);
        assertThat(eventCount(order.id())).isZero();

        replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, targetAssetId, null, "已有草稿月结时更换"
        ));

        var newlyAccrued = onlyRenewal(order.id());
        assertThat(newlyAccrued.periodStartAt()).isEqualTo(periodStartAt);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE s.statement_month = ?
              AND s.status IN ('DRAFT', 'RECONCILING')
              AND l.source_type = 'EXTERNAL_RENEWAL'
              AND l.source_id = ?
            """, Integer.class, occurrenceMonth, newlyAccrued.id())).isGreaterThan(0);
    }

    @Test
    void activeOrderShouldAlsoReplaceItsMainAssetWithoutChangingInitialSnapshot() {
        var oldAssetId = createAsset("main-old", "VEHICLE_FRAME", 1L);
        var retainedBatteryId = createAsset("main-battery", "BATTERY", 1L);
        var newAssetId = createAsset("main-new", "INTEGRATED_VEHICLE", 1L);
        var now = databaseTime();
        var suffix = unique("main");
        var order = externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE", "EXT-" + suffix, 1L, 2L, 1,
            "主资产更换客户", phone(), now, now.plusDays(30), oldAssetId, retainedBatteryId,
            new BigDecimal("399.00"), new BigDecimal("399.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            "主资产更换集成测试"
        ));

        var change = replacementService.replace(order.id(), new AssetReplaceRequest(
            "VEHICLE_FRAME", oldAssetId, newAssetId, "EXCEPTION", "履约中更换车架"
        ));

        var updated = externalOrderService.getOrder(order.id());
        assertThat(updated.frameAssetId()).isEqualTo(newAssetId);
        assertThat(updated.batteryAssetId()).isEqualTo(retainedBatteryId);
        assertThat(updated.settlementSnapshotId()).isEqualTo(order.settlementSnapshotId());
        assertThat(change.assetType()).isEqualTo("VEHICLE_FRAME");
        assertThat(assetStatus(oldAssetId)).isEqualTo("EXCEPTION");
        assertThat(assetStatus(newAssetId)).isEqualTo("RENTING");
        assertThat(eventCount(order.id())).isZero();
    }

    @Test
    void replacingIntegratedMainWithOrdinaryAssetShouldRejectAnIncompleteRequiredCombination() {
        var integratedAssetId = createAsset("combo-integrated", "INTEGRATED_VEHICLE", 1L);
        var ordinaryAssetId = createAsset("combo-ordinary", "VEHICLE_FRAME", 1L);
        var now = databaseTime();
        var suffix = unique("combo");
        var order = externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE", "EXT-" + suffix, 1L, 2L, 1,
            "资产组合校验客户", phone(), now, now.plusDays(30), integratedAssetId, null,
            new BigDecimal("399.00"), new BigDecimal("399.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            "资产组合校验"
        ));

        assertThatThrownBy(() -> replacementService.replace(order.id(), new AssetReplaceRequest(
            "VEHICLE_FRAME", integratedAssetId, ordinaryAssetId, null, "更换为非一体车"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("还需要绑定第二资产");

        assertThat(externalOrderService.getOrder(order.id()).frameAssetId()).isEqualTo(integratedAssetId);
        assertThat(assetStatus(integratedAssetId)).isEqualTo("RENTING");
        assertThat(assetStatus(ordinaryAssetId)).isEqualTo("IDLE");
    }

    @Test
    void retroactiveManualRenewalShouldRebuildAssetHistoryAcrossAnOverdueReplacement() {
        var newInvestorId = createInvestor("retroactive");
        var oldAssetId = createAsset("retroactive-old", "BATTERY", 1L);
        var newAssetId = createAsset("retroactive-new", "BATTERY", newInvestorId);
        var now = databaseTime();
        var order = createDueBatteryOrder(
            "retroactive", oldAssetId, now.minusDays(40), now.minusDays(10)
        );
        jdbcTemplate.update(
            "UPDATE external_rental_order SET auto_renew_enabled = 0 WHERE id = ?",
            order.id()
        );

        var change = replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, newAssetId, null, "停止自动续租后先更换资产"
        ));
        assertThat(eventCount(order.id())).isZero();

        var current = externalOrderService.getOrder(order.id());
        var renewal = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            current.expectedReturnAt(), now.plusDays(10), new BigDecimal("400.00"),
            "事后人工补录跨更换时点的续租"
        ));
        var snapshotId = jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            renewal.id()
        );
        var timeline = jdbcTemplate.query("""
            SELECT asset_id, investor_id, effective_start_at, effective_end_at
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            ORDER BY effective_start_at, id
            """, (rs, rowNum) -> new Object[] {
                rs.getLong("asset_id"),
                rs.getLong("investor_id"),
                rs.getObject("effective_start_at", LocalDateTime.class),
                rs.getObject("effective_end_at", LocalDateTime.class)
            }, snapshotId);

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0)).containsExactly(
            oldAssetId, 1L, renewal.periodStartAt(), change.effectiveAt()
        );
        assertThat(timeline.get(1)).containsExactly(
            newAssetId, newInvestorId, change.effectiveAt(), renewal.periodEndAt()
        );
        assertThat(allocationTotal(snapshotId, "allocation_weight")).isEqualByComparingTo("1.000000000000");
        assertThat(allocationTotal(snapshotId, "investor_share_amount"))
            .isEqualByComparingTo(snapshotAmount(snapshotId, "investor_share_amount"));
    }

    @Test
    void staleExpectedAssetShouldReturnConflictWithoutReplacingTheWinner() {
        var oldAssetId = createAsset("stale-old", "BATTERY", 1L);
        var winnerAssetId = createAsset("stale-winner", "BATTERY", 1L);
        var staleTargetId = createAsset("stale-target", "BATTERY", 1L);
        var now = databaseTime();
        var order = createDueBatteryOrder("stale", oldAssetId, now, now.plusDays(30));
        replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, winnerAssetId, null, "先到的更换请求"
        ));

        assertThatThrownBy(() -> replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, staleTargetId, null, "基于旧页面的过期请求"
        )))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.httpStatus().value()).isEqualTo(409);
                assertThat(exception.getMessage()).contains("请刷新后重试");
            });
        assertThat(externalOrderService.getOrder(order.id()).batteryAssetId()).isEqualTo(winnerAssetId);
        assertThat(assetStatus(winnerAssetId)).isEqualTo("RENTING");
        assertThat(assetStatus(staleTargetId)).isEqualTo("IDLE");
    }

    @Test
    void replacementShouldRejectATargetWithoutARealInvestorBinding() {
        var oldAssetId = createAsset("investor-old", "BATTERY", 1L);
        var unboundTargetId = createAsset("investor-unbound", "BATTERY", 0L);
        var now = databaseTime();
        var order = createDueBatteryOrder("investor-binding", oldAssetId, now, now.plusDays(30));

        assertThatThrownBy(() -> replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, unboundTargetId, null, "换入未绑定出资方的资产"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未绑定出资方");
        assertThat(externalOrderService.getOrder(order.id()).batteryAssetId()).isEqualTo(oldAssetId);
        assertThat(assetStatus(oldAssetId)).isEqualTo("RENTING");
        assertThat(assetStatus(unboundTargetId)).isEqualTo("IDLE");
    }

    @Test
    void formalPrebindingAndSupplementalReplacementShouldConserveExclusiveAssetInEitherOrder() {
        var now = databaseTime();
        var oldAssetId = createAsset("formal-first-old", "BATTERY", 1L);
        var formalTargetId = createAsset("formal-first-target", "BATTERY", 1L);
        var external = createDueBatteryOrder("formal-first", oldAssetId, now, now.plusDays(30));
        var formal = orderService.createOrder(new OrderCreateRequest(
            null, "正式单预绑客户", phone(), 2L, 4L, null, formalTargetId,
            now.plusHours(1), now, new BigDecimal("400.00")
        ));
        jdbcTemplate.update(
            "UPDATE rental_order SET order_status = 'PENDING_PICKUP' WHERE id = ?",
            formal.id()
        );

        assertThatThrownBy(() -> replacementService.replace(external.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, formalTargetId, null, "正式单预绑已先提交"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("正式订单占用");
        assertThat(externalOrderService.getOrder(external.id()).batteryAssetId()).isEqualTo(oldAssetId);
        assertThat(assetStatus(oldAssetId)).isEqualTo("RENTING");
        assertThat(assetStatus(formalTargetId)).isEqualTo("IDLE");

        var replacementFirstOldId = createAsset("replacement-first-old", "BATTERY", 1L);
        var replacementFirstTargetId = createAsset("replacement-first-target", "BATTERY", 1L);
        var replacementFirst = createDueBatteryOrder(
            "replacement-first", replacementFirstOldId, now, now.plusDays(30)
        );
        replacementService.replace(replacementFirst.id(), new AssetReplaceRequest(
            "BATTERY", replacementFirstOldId, replacementFirstTargetId, null, "补录换车已先提交"
        ));

        assertThatThrownBy(() -> orderService.createOrder(new OrderCreateRequest(
            null, "正式单后到客户", phone(), 2L, 4L, null, replacementFirstTargetId,
            now.plusHours(1), now, new BigDecimal("400.00")
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("空闲");
        assertThat(assetStatus(replacementFirstTargetId)).isEqualTo("RENTING");
    }

    @Test
    void initialOwnerAndAssetPointersShouldStayFrozenAfterReplacementOwnerTransferAndStructuralEdit() {
        var replacementInvestorId = createInvestor("initial-replacement");
        var transferredInvestorId = createInvestor("initial-transferred");
        var oldAssetId = createAsset("initial-old", "BATTERY", 1L);
        var newAssetId = createAsset("initial-new", "BATTERY", replacementInvestorId);
        var now = databaseTime();
        var order = createDueBatteryOrder("initial-owner", oldAssetId, now, now.plusDays(30));
        var originalIncome = initialInvestorIncomeByBeneficiary(order.id());
        assertThat(originalIncome).containsOnlyKeys(1L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_initial_investor_allocation
            WHERE external_order_id = ? AND asset_type = 'BATTERY'
              AND asset_id = ? AND investor_id = 1
            """, Integer.class, order.id(), oldAssetId)).isEqualTo(1);

        replacementService.replace(order.id(), new AssetReplaceRequest(
            "BATTERY", oldAssetId, newAssetId, null, "首期归属冻结测试"
        ));
        assetService.changeInvestor(oldAssetId, new AssetInvestorChangeRequest(
            transferredInvestorId, "原资产释放后转出资方"
        ));
        var current = externalOrderService.getOrder(order.id());
        var updated = externalOrderService.updateOrder(order.id(), new ExternalRentalOrderUpdateRequest(
            "MEITUAN", current.externalOrderNo(), current.storeSkuId(), current.packageId(),
            current.leaseMultiplier(), "结构编辑后客户", current.customerPhone(),
            current.rentStartedAt(), current.expectedReturnAt(), current.frameAssetId(), current.batteryAssetId(),
            current.externalRentalAmount(), current.verificationAmount(), current.signFeeAmount(),
            current.depositAmount(), "换车后结构编辑"
        ));

        assertThat(updated.settlementSnapshotId()).isNotEqualTo(order.settlementSnapshotId());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT battery_asset_id FROM settlement_rule_snapshot WHERE id = ?",
            Long.class, updated.settlementSnapshotId()
        )).isEqualTo(oldAssetId);
        var rebuiltIncome = initialInvestorIncomeByBeneficiary(order.id());
        assertThat(rebuiltIncome).containsOnlyKeys(1L);
        assertThat(rebuiltIncome.get(1L)).isEqualByComparingTo(
            snapshotAmount(updated.settlementSnapshotId(), "investor_share_amount")
        );

        var month = updated.createdAt().format(STATEMENT_MONTH);
        statementService.generateMonth(month);
        assertMoneyMapEquals(initialStatementInvestorByBeneficiary(order.id()), rebuiltIncome);
        assertThat(initialStatementInvestorByBeneficiary(order.id()))
            .doesNotContainKeys(replacementInvestorId, transferredInvestorId);
    }

    @Test
    void initialOneCentTieShouldStayWithFrameSlotRegardlessOfInvestorIdOrdering() {
        var batteryInvestorId = createInvestor("one-cent-battery-low-id");
        var frameInvestorId = createInvestor("one-cent-frame-high-id");
        assertThat(frameInvestorId).isGreaterThan(batteryInvestorId);
        var frameAssetId = createAsset("one-cent-frame", "VEHICLE_FRAME", frameInvestorId);
        var batteryAssetId = createAsset("one-cent-battery", "BATTERY", batteryInvestorId);
        var now = databaseTime();
        var suffix = unique("one-cent");
        var order = externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE", "EXT-" + suffix, 1L, 2L, 1,
            "一分钱槽位测试", phone(), now, now.plusDays(30), frameAssetId, batteryAssetId,
            new BigDecimal("399.00"), new BigDecimal("399.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            "首期一分钱并列顺序"
        ));
        jdbcTemplate.update(
            "UPDATE settlement_rule_snapshot SET investor_share_amount = 0.01 WHERE id = ?",
            order.settlementSnapshotId()
        );
        settlementIncomeService.syncExternalOrder(
            externalOrderRepository.findById(order.id()).orElseThrow()
        );

        assertThat(initialInvestorIncomeByBeneficiary(order.id()))
            .containsOnlyKeys(frameInvestorId)
            .containsEntry(frameInvestorId, new BigDecimal("0.01"));
    }

    @Test
    void lowValueTenSegmentRenewalShouldUseStableLargestRemainderWithoutNegativeOrOverpayment() {
        var periodStart = databaseTime().minusDays(20).withNano(0);
        var assets = new java.util.ArrayList<Long>();
        var investors = new java.util.ArrayList<Long>();
        for (var index = 0; index < 10; index += 1) {
            var investorId = createInvestor("tiny-" + index);
            investors.add(investorId);
            assets.add(createAsset("tiny-" + index, "BATTERY", investorId));
        }
        var order = createDueBatteryOrder("tiny-allocation", assets.getFirst(), periodStart.minusDays(30), periodStart);
        jdbcTemplate.update("UPDATE external_rental_order SET auto_renew_enabled = 0 WHERE id = ?", order.id());
        for (var index = 0; index < 9; index += 1) {
            jdbcTemplate.update("""
                INSERT INTO external_order_asset_change
                (change_no, external_order_id, merchant_id, store_id, asset_type,
                 old_asset_id, old_asset_serial_no, old_investor_id,
                 new_asset_id, new_asset_serial_no, new_investor_id,
                 old_asset_result_status, effective_at, operator_account_id, remark)
                VALUES (?, ?, 1, 1, 'BATTERY', ?, ?, ?, ?, ?, ?, 'IDLE', ?, 1, '等时长低额测试')
                """, "EAC-" + unique("tiny-" + index), order.id(),
                assets.get(index), "SERIAL-TINY-OLD-" + index, investors.get(index),
                assets.get(index + 1), "SERIAL-TINY-NEW-" + index, investors.get(index + 1),
                periodStart.plusDays(index + 1));
        }
        var renewal = manualRenewalService.create(order.id(), new ExternalOrderManualRenewalRequest(
            periodStart, periodStart.plusDays(10), new BigDecimal("400.00"), "低额分配测试"
        ));
        var renewalEvent = renewalRepository.findById(renewal.id()).orElseThrow();
        var snapshotId = renewalEvent.settlementSnapshotId();
        jdbcTemplate.update(
            "DELETE FROM external_order_renewal_investor_allocation WHERE settlement_snapshot_id = ?",
            snapshotId
        );
        jdbcTemplate.update(
            "UPDATE settlement_rule_snapshot SET investor_share_amount = 0.05 WHERE id = ?",
            snapshotId
        );
        renewalAllocationService.freezeCurrentAssets(
            renewalEvent, snapshotId
        );

        var amounts = jdbcTemplate.queryForList("""
            SELECT investor_share_amount
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            ORDER BY effective_start_at, id
            """, BigDecimal.class, snapshotId);
        assertThat(amounts).containsExactly(
            new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.01"),
            new BigDecimal("0.01"), new BigDecimal("0.01"), BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)
        );
        assertThat(allocationTotal(snapshotId, "investor_share_amount")).isEqualByComparingTo("0.05");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT MIN(investor_share_amount)
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            """, BigDecimal.class, snapshotId)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void preV67RenewalShouldKeepFrozenIncomeOwnerForStatementAndRepricingWhenLiveOwnerChanges() {
        var changedOwnerId = createInvestor("legacy-renewal-live-owner");
        var assetId = createAsset("legacy-renewal-asset", "BATTERY", 1L);
        var now = databaseTime();
        var order = createDueBatteryOrder("legacy-renewal", assetId, now.minusDays(40), now.minusDays(10));
        assertThat(autoRenewalService.accrueDueOrder(order.id(), now)).isEqualTo(1);
        var event = onlyRenewal(order.id());
        var frozenIncome = investorIncomeByBeneficiary(event.id());
        assertThat(frozenIncome).containsOnlyKeys(1L);
        jdbcTemplate.update(
            "DELETE FROM external_order_renewal_investor_allocation WHERE settlement_snapshot_id = ?",
            event.settlementSnapshotId()
        );
        /* Simulate a pre-fix legacy row whose live owner drifted while the
         * event had no V67 allocation ledger. Current APIs reject this write,
         * but reconciliation must not move its historical beneficiary. */
        jdbcTemplate.update("UPDATE asset_item SET investor_id = ? WHERE id = ?", changedOwnerId, assetId);

        var month = event.periodStartAt().format(STATEMENT_MONTH);
        statementService.generateMonth(month);
        assertMoneyMapEquals(statementInvestorByBeneficiary(event.id()), frozenIncome);
        assertThat(statementInvestorByBeneficiary(event.id())).doesNotContainKey(changedOwnerId);

        var current = externalOrderService.getOrder(order.id());
        externalOrderService.updateOrder(order.id(), new ExternalRentalOrderUpdateRequest(
            current.sourcePlatform(), current.externalOrderNo(), current.storeSkuId(), current.packageId(),
            current.leaseMultiplier(), current.customerName(), current.customerPhone(),
            current.rentStartedAt(), current.expectedReturnAt(), current.frameAssetId(), current.batteryAssetId(),
            current.externalRentalAmount(), new BigDecimal("350.00"), current.signFeeAmount(),
            current.depositAmount(), "触发旧续租调价重算"
        ));

        var repriced = onlyRenewal(order.id());
        assertThat(repriced.settlementSnapshotId()).isNotEqualTo(event.settlementSnapshotId());
        assertThat(jdbcTemplate.queryForList("""
            SELECT DISTINCT investor_id
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            """, Long.class, repriced.settlementSnapshotId())).containsExactly(1L);
        assertThat(investorIncomeByBeneficiary(repriced.id())).containsOnlyKeys(1L);
        assertThat(statementInvestorByBeneficiary(repriced.id())).containsOnlyKeys(1L);
    }

    private com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse createDueBatteryOrder(
        String label,
        Long assetId,
        LocalDateTime startAt,
        LocalDateTime expectedReturnAt
    ) {
        var suffix = unique(label);
        return externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE", "EXT-" + suffix, 2L, 4L, 1,
            "换车集成测试客户", phone(), startAt, expectedReturnAt, null, assetId,
            new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            "补录订单换车集成测试"
        ));
    }

    private Long createInvestor(String label) {
        var suffix = unique(label);
        jdbcTemplate.update("""
            INSERT INTO investor
            (investor_code, investor_name, contact_name, contact_phone, operation_fee_rate, status)
            VALUES (?, ?, ?, ?, 0.0000, 'ENABLED')
            """, "I-" + suffix, "出资方-" + suffix, "测试联系人", phone());
        return jdbcTemplate.queryForObject(
            "SELECT id FROM investor WHERE investor_code = ?",
            Long.class,
            "I-" + suffix
        );
    }

    private Long createAsset(String label, String typeCode, Long investorId) {
        var suffix = unique(label);
        var assetCode = "A-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id,
             current_merchant_id, current_store_id, status, purchase_amount,
             maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, ?, (SELECT id FROM asset_type_definition WHERE type_code = ?), ?, ?,
                    1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, assetCode, typeCode, typeCode, "SERIAL-" + suffix, investorId);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            assetCode
        );
    }

    private ExternalOrderRenewalEvent onlyRenewal(Long orderId) {
        assertThat(renewalRepository.listByExternalOrder(orderId)).hasSize(1);
        return renewalRepository.listByExternalOrder(orderId).getFirst();
    }

    private void assertEventFullyAttributedTo(Long eventId, Long assetId, Long investorId) {
        var snapshotId = jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM external_order_renewal_event WHERE id = ?",
            Long.class,
            eventId
        );
        assertThat(jdbcTemplate.queryForList("""
            SELECT DISTINCT asset_id
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            """, Long.class, snapshotId)).containsExactly(assetId);
        assertThat(jdbcTemplate.queryForList("""
            SELECT DISTINCT investor_id
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            """, Long.class, snapshotId)).containsExactly(investorId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT battery_asset_id FROM settlement_rule_snapshot WHERE id = ?",
            Long.class,
            snapshotId
        )).isEqualTo(assetId);
        assertThat(allocationTotal(snapshotId, "allocation_weight")).isEqualByComparingTo("1.000000000000");
    }

    private Map<String, BigDecimal> incomeByLine(Long eventId, boolean investor) {
        var result = new LinkedHashMap<String, BigDecimal>();
        jdbcTemplate.query("""
            SELECT line_type, SUM(amount) AS amount
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND beneficiary_type %s 'INVESTOR'
            GROUP BY line_type
            ORDER BY line_type
            """.formatted(investor ? "=" : "<>"), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            result.put(rs.getString("line_type"), rs.getBigDecimal("amount"));
        }, eventId);
        return result;
    }

    private Map<Long, BigDecimal> investorIncomeByBeneficiary(Long eventId) {
        return moneyMap("""
            SELECT beneficiary_id AS target_id, SUM(amount) AS amount
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND beneficiary_type = 'INVESTOR'
            GROUP BY beneficiary_id
            ORDER BY beneficiary_id
            """, eventId);
    }

    private Map<Long, BigDecimal> initialInvestorIncomeByBeneficiary(Long externalOrderId) {
        return moneyMap("""
            SELECT beneficiary_id AS target_id, SUM(amount) AS amount
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND beneficiary_type = 'INVESTOR'
            GROUP BY beneficiary_id
            ORDER BY beneficiary_id
            """, externalOrderId);
    }

    private Map<Long, BigDecimal> initialStatementInvestorByBeneficiary(Long externalOrderId) {
        return moneyMap("""
            SELECT l.investor_id AS target_id, SUM(l.amount) AS amount
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = 'EXTERNAL_ORDER'
              AND l.source_id = ?
              AND s.beneficiary_type = 'INVESTOR'
            GROUP BY l.investor_id
            ORDER BY l.investor_id
            """, externalOrderId);
    }

    private Map<Long, BigDecimal> statementInvestorByBeneficiary(Long eventId) {
        return moneyMap("""
            SELECT l.investor_id AS target_id, SUM(l.amount) AS amount
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = 'EXTERNAL_RENEWAL'
              AND l.source_id = ?
              AND s.beneficiary_type = 'INVESTOR'
            GROUP BY l.investor_id
            ORDER BY l.investor_id
            """, eventId);
    }

    private Map<Long, BigDecimal> allocationByInvestor(Long snapshotId) {
        return moneyMap("""
            SELECT investor_id AS target_id, SUM(investor_share_amount) AS amount
            FROM external_order_renewal_investor_allocation
            WHERE settlement_snapshot_id = ?
            GROUP BY investor_id
            ORDER BY investor_id
            """, snapshotId);
    }

    private Map<Long, BigDecimal> moneyMap(String sql, Long id) {
        var result = new LinkedHashMap<Long, BigDecimal>();
        jdbcTemplate.query(
            sql,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getLong("target_id"), rs.getBigDecimal("amount")),
            id
        );
        return result;
    }

    private void assertMoneyMapEquals(Map<Long, BigDecimal> actual, Map<Long, BigDecimal> expected) {
        assertThat(actual.keySet()).isEqualTo(expected.keySet());
        expected.forEach((key, amount) -> assertThat(actual.get(key)).isEqualByComparingTo(amount));
    }

    private BigDecimal incomeTotal(Long eventId, boolean investor) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_RENEWAL'
              AND source_id = ?
              AND beneficiary_type %s 'INVESTOR'
            """.formatted(investor ? "=" : "<>"), BigDecimal.class, eventId);
    }

    private BigDecimal allocationTotal(Long snapshotId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(" + column + "), 0) FROM external_order_renewal_investor_allocation "
                + "WHERE settlement_snapshot_id = ?",
            BigDecimal.class,
            snapshotId
        );
    }

    private BigDecimal snapshotAmount(Long snapshotId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM settlement_rule_snapshot WHERE id = ?",
            BigDecimal.class,
            snapshotId
        );
    }

    private int renewalSnapshotCount(Long eventId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_rule_snapshot
            WHERE source_type = 'EXTERNAL_RENEWAL' AND source_id = ?
            """, Integer.class, eventId);
    }

    private int eventCount(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM external_order_renewal_event WHERE external_order_id = ?",
            Integer.class,
            orderId
        );
    }

    private int rowCount(String table, Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + table + " WHERE id = ?",
            Integer.class,
            id
        );
    }

    private String assetStatus(Long assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM asset_item WHERE id = ?",
            String.class,
            assetId
        );
    }

    private LocalDateTime databaseTime() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP(6)", LocalDateTime.class);
    }

    private String unique(String label) {
        return label + "-" + Long.toUnsignedString(System.nanoTime());
    }

    private String phone() {
        var suffix = Long.toUnsignedString(System.nanoTime());
        return "138" + suffix.substring(Math.max(0, suffix.length() - 8));
    }

    private CurrentAccount adminAccount() {
        return new CurrentAccount(
            "external-order-asset-replacement-test-token",
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
                List.of("system.admin", "order.read", "order.operate", "asset.operate", "settlement.read", "settlement.write"),
                List.of()
            )
        );
    }
}
