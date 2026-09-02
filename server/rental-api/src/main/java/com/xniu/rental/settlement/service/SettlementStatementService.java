package com.xniu.rental.settlement.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.settlement.dto.BatteryPayableResponse;
import com.xniu.rental.settlement.dto.SettlementOverviewResponse;
import com.xniu.rental.settlement.dto.SettlementStatementGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementStatementLineResponse;
import com.xniu.rental.settlement.dto.SettlementStatementResponse;
import com.xniu.rental.settlement.dto.StoreProfitOverviewResponse;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SettlementStatement;
import com.xniu.rental.settlement.model.SettlementStatementLine;
import com.xniu.rental.settlement.model.SettlementStatementLineType;
import com.xniu.rental.settlement.model.SettlementStatementStatus;
import com.xniu.rental.settlement.model.StatementBeneficiaryType;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementStatementService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SettlementStatementRepository statementRepository;
    private final SettlementIncomeRepository incomeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementIncomeService settlementIncomeService;
    private final AssetFulfillmentRepository assetFulfillmentRepository;
    private final AssetRepository assetRepository;
    private final AuthorizationService authorizationService;

    public SettlementStatementService(
        SettlementStatementRepository statementRepository,
        SettlementIncomeRepository incomeRepository,
        SettlementRepository settlementRepository,
        SettlementIncomeService settlementIncomeService,
        AssetFulfillmentRepository assetFulfillmentRepository,
        AssetRepository assetRepository,
        AuthorizationService authorizationService
    ) {
        this.statementRepository = statementRepository;
        this.incomeRepository = incomeRepository;
        this.settlementRepository = settlementRepository;
        this.settlementIncomeService = settlementIncomeService;
        this.assetFulfillmentRepository = assetFulfillmentRepository;
        this.assetRepository = assetRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public SettlementStatementGenerateResponse generateMonth(String statementMonth) {
        authorizationService.requirePermission("settlement.write");
        return generateMonthInternal(statementMonth, true);
    }

    /** Trusted repair path used after an unlocked renewal amount changes. */
    @Transactional
    public SettlementStatementGenerateResponse regenerateUnlockedMonth(String statementMonth) {
        return generateMonthInternal(statementMonth, true);
    }

    /**
     * Rebuild a draft month when the caller already owns that month's
     * statement lock.  Order-mutating services use this entry point after
     * locking the external order first and then the affected months.  Taking
     * the month-wide external-order lock again here would invert that order
     * (month -> other order) and can deadlock with a concurrent generator
     * (order -> month).
     *
     * <p>This method is intentionally public only because the caller lives in
     * another service package; it is not a controller endpoint.  Callers must
     * hold {@code settlement_statement} rows for the month for the duration
     * of the surrounding transaction.</p>
     */
    @Transactional
    public SettlementStatementGenerateResponse regenerateUnlockedMonthAlreadyLocked(String statementMonth) {
        return generateMonthInternal(statementMonth, false);
    }

    private SettlementStatementGenerateResponse generateMonthInternal(
        String statementMonth,
        boolean lockExternalOrders
    ) {
        var month = normalizeRequiredMonth(statementMonth);
        var range = monthRange(month);
        /* External-order mutations lock the order row before any statement
         * rows.  Public generation follows the same order -> statement order
         * so a concurrent terminate/delete cannot be followed by a stale
         * draft insertion.  The already-locked repair path deliberately skips
         * this second, month-wide order scan; its caller already holds the
         * month lock and re-acquiring other order rows here would invert the
         * order and deadlock with a public generator. */
        if (lockExternalOrders) {
            statementRepository.lockExternalOrdersForMonthForUpdate(range.startAt(), range.endAt());
        }
        // Serialize draft regeneration with statement status transitions. A
        // locked month must never be deleted while another transaction is
        // confirming or paying one of its statements.
        statementRepository.lockStatementsByMonthForUpdate(month);
        if (statementRepository.hasLockedStatements(month)) {
            throw BusinessException.badRequest("该月份已存在已确认或已支付月结单，不能重新生成");
        }
        statementRepository.deleteDraftStatements(month);

        settlementIncomeService.syncPaidBills(range.startAt(), range.endAt());
        var paidBillItems = statementRepository.listPaidBillItems(range.startAt(), range.endAt());
        var externalOrderItems = statementRepository.listExternalOrderItems(range.startAt(), range.endAt());
        var externalRenewalItems = statementRepository.listExternalRenewalItems(range.startAt(), range.endAt());
        var maintenanceCosts = statementRepository.listMaintenanceCosts(range.startAt(), range.endAt());
        var snapshotIds = paidBillItems.stream()
            .map(SettlementStatementRepository.PaidBillItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        externalOrderItems.stream()
            .map(SettlementStatementRepository.ExternalOrderItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .forEach(snapshotIds::add);
        externalRenewalItems.stream()
            .map(SettlementStatementRepository.ExternalRenewalItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .forEach(snapshotIds::add);
        var snapshotMap = settlementRepository.findSnapshotsByIds(snapshotIds).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementRuleSnapshot::id, item -> item));
        var orderIds = paidBillItems.stream()
            .map(SettlementStatementRepository.PaidBillItemRow::orderId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var usageMap = assetFulfillmentRepository.listUsageByOrders(new ArrayList<>(orderIds)).stream()
            .collect(java.util.stream.Collectors.groupingBy(
                AssetFulfillmentRepository.OrderAssetUsageRow::orderId,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        var merchantDrafts = new LinkedHashMap<String, StatementDraft>();
        var investorDrafts = new LinkedHashMap<Long, StatementDraft>();

        var billGroups = paidBillItems.stream().collect(java.util.stream.Collectors.groupingBy(
            SettlementStatementRepository.PaidBillItemRow::billId,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
        for (var group : billGroups.values()) {
            var first = group.getFirst();
            var snapshotId = first.settlementSnapshotId();
            var rentAmount = money(sumItemAmount(group, "RENT").add(sumItemAmount(group, "RENEWAL_RENT")));
            var signFeeAmount = sumItemAmount(group, "SIGN_FEE");
            var signFeeNetAmount = netOrderFee(signFeeAmount);
            if (signFeeNetAmount.signum() > 0) {
                var merchantDraft = merchantDraft(merchantDrafts, first.merchantId(), first.storeId());
                merchantDraft.register(
                    new LineDraft(
                        "BILL",
                        first.billId(),
                        first.orderId(),
                        first.billId(),
                        null,
                        first.merchantId(),
                        first.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_SIGN_FEE,
                        signFeeNetAmount,
                        first.paidAt(),
                        "签单费实收（扣除 3% 手续费）"
                    ),
                    BigDecimal.ZERO
                );
            }
            if (rentAmount.signum() <= 0) {
                continue;
            }
            if (snapshotId == null) {
                throw BusinessException.badRequest("订单 " + first.orderId() + " 缺少分润快照，不能生成月结");
            }
            var snapshot = snapshotMap.get(snapshotId);
            if (snapshot == null) {
                throw BusinessException.badRequest("订单 " + first.orderId() + " 的分润快照不存在");
            }
            var profitAllocation = snapshot.calculationVersion().usesProfitSharing()
                ? calculateProfitV2(snapshot, rentAmount)
                : null;
            if (profitAllocation != null && profitAllocation.batteryCostAmount().signum() > 0) {
                merchantDraft(merchantDrafts, first.merchantId(), first.storeId()).register(
                    new LineDraft(
                        "BILL",
                        first.billId(),
                        first.orderId(),
                        first.billId(),
                        null,
                        first.merchantId(),
                        first.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_BATTERY_COST_PAYABLE,
                        profitAllocation.batteryCostAmount(),
                        first.paidAt(),
                        "外卖换电车型电池费（门店应付电池公司）"
                    ),
                    rentAmount
                );
            }
            var merchantShare = profitAllocation == null
                ? money(rentAmount.multiply(snapshot.merchantRentShareRate()))
                : profitAllocation.storeOperationAmount();
            if (merchantShare.signum() > 0) {
                var merchantDraft = merchantDraft(merchantDrafts, first.merchantId(), first.storeId());
                merchantDraft.register(
                    new LineDraft(
                        "BILL",
                        first.billId(),
                        first.orderId(),
                        first.billId(),
                        null,
                        first.merchantId(),
                        first.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_RENT_SHARE,
                        merchantShare,
                        first.paidAt(),
                        snapshot.calculationVersion().usesProfitSharing() ? "门店运营分润" : "租金分润"
                    ),
                    profitAllocation != null && profitAllocation.batteryCostAmount().signum() > 0 ? BigDecimal.ZERO : rentAmount
                );
            }
            if (profitAllocation != null && profitAllocation.maintenanceFundAmount().signum() > 0) {
                merchantDraft(merchantDrafts, first.merchantId(), first.storeId()).register(
                    new LineDraft(
                        "BILL",
                        first.billId(),
                        first.orderId(),
                        first.billId(),
                        null,
                        first.merchantId(),
                        first.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_MAINTENANCE_SHARE,
                        profitAllocation.maintenanceFundAmount(),
                        first.paidAt(),
                        "门店维修分润"
                    ),
                    BigDecimal.ZERO
                );
            }
            for (var allocation : buildInvestorAllocations(snapshot, first.orderId(), rentAmount, usageMap.get(first.orderId()))) {
                var investorDraft = investorDraft(investorDrafts, allocation.investorId());
                if (allocation.grossRentAmount().signum() > 0) {
                    investorDraft.register(
                        new LineDraft(
                            "BILL",
                            first.billId(),
                            first.orderId(),
                            first.billId(),
                            null,
                            first.merchantId(),
                            first.storeId(),
                            allocation.investorId(),
                            SettlementStatementLineType.INVESTOR_GROSS_RENT,
                            allocation.grossRentAmount(),
                            first.paidAt(),
                            snapshot.calculationVersion().usesProfitSharing() ? "出资方分润" : "出资方租金毛收益"
                        ),
                        allocation.rentBaseAmount()
                    );
                }
            }
        }

        for (var externalOrder : externalOrderItems) {
            var snapshot = snapshotMap.get(externalOrder.settlementSnapshotId());
            if (snapshot == null) {
                throw BusinessException.badRequest("补录订单 " + externalOrder.recordNo() + " 的分润快照不存在");
            }
            if (!"EXTERNAL_ORDER".equals(snapshot.sourceType().name()) || !externalOrder.externalOrderId().equals(snapshot.sourceId())) {
                throw BusinessException.badRequest("补录订单 " + externalOrder.recordNo() + " 的分润快照不匹配");
            }
            // The verification/rent base is frozen on the initial snapshot;
            // the order row remains the authoritative recorded handling fee.
            // Structural fee edits are blocked once any statement/income is
            // locked, so using it here cannot rewrite settled history and it
            // also preserves older snapshots created before custom fees were
            // frozen into the snapshot.
            var signFeeAllocation = ProfitSharingCalculator.calculateOrderFee(externalOrder.signFeeAmount());
            if (signFeeAllocation.merchantNetAmount().signum() > 0) {
                merchantDraft(merchantDrafts, externalOrder.merchantId(), externalOrder.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_ORDER",
                        externalOrder.externalOrderId(),
                        null,
                        null,
                        null,
                        externalOrder.merchantId(),
                        externalOrder.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_SIGN_FEE,
                        signFeeAllocation.merchantNetAmount(),
                        externalOrder.createdAt(),
                        "补录订单 " + externalOrder.recordNo() + " 签单费（扣除 3% 手续费）"
                    ),
                    BigDecimal.ZERO
                );
            }
            var settlementBase = money(snapshot.settlementBaseAmount());
            if (settlementBase.signum() <= 0) {
                continue;
            }
            if (snapshot.calculationVersion().usesProfitSharing()
                && snapshot.batteryCostAmount().signum() > 0) {
                merchantDraft(merchantDrafts, externalOrder.merchantId(), externalOrder.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_ORDER",
                        externalOrder.externalOrderId(),
                        null,
                        null,
                        null,
                        externalOrder.merchantId(),
                        externalOrder.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_BATTERY_COST_PAYABLE,
                        snapshot.batteryCostAmount(),
                        externalOrder.createdAt(),
                        "补录订单 " + externalOrder.recordNo() + " 电池费（门店应付电池公司）"
                    ),
                    settlementBase
                );
            }
            var merchantShare = snapshot.calculationVersion().usesProfitSharing()
                ? snapshot.storeOperationAmount()
                : money(settlementBase.multiply(snapshot.merchantRentShareRate()));
            if (merchantShare.signum() > 0) {
                merchantDraft(merchantDrafts, externalOrder.merchantId(), externalOrder.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_ORDER",
                        externalOrder.externalOrderId(),
                        null,
                        null,
                        null,
                        externalOrder.merchantId(),
                        externalOrder.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_RENT_SHARE,
                        merchantShare,
                        externalOrder.createdAt(),
                        "补录订单 " + externalOrder.recordNo() + " 门店运营分润"
                    ),
                    snapshot.calculationVersion().usesProfitSharing()
                        && snapshot.batteryCostAmount().signum() > 0 ? BigDecimal.ZERO : settlementBase
                );
            }
            if (snapshot.calculationVersion().usesProfitSharing()
                && snapshot.maintenanceFundAmount().signum() > 0) {
                merchantDraft(merchantDrafts, externalOrder.merchantId(), externalOrder.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_ORDER",
                        externalOrder.externalOrderId(),
                        null,
                        null,
                        null,
                        externalOrder.merchantId(),
                        externalOrder.storeId(),
                        0L,
                        SettlementStatementLineType.MERCHANT_MAINTENANCE_SHARE,
                        snapshot.maintenanceFundAmount(),
                        externalOrder.createdAt(),
                        "补录订单 " + externalOrder.recordNo() + " 门店维修分润"
                    ),
                    BigDecimal.ZERO
                );
            }
            for (var allocation : buildInvestorAllocations(snapshot, null, settlementBase, null)) {
                if (allocation.grossRentAmount().signum() <= 0) {
                    continue;
                }
                investorDraft(investorDrafts, allocation.investorId()).register(
                    new LineDraft(
                        "EXTERNAL_ORDER",
                        externalOrder.externalOrderId(),
                        null,
                        null,
                        null,
                        externalOrder.merchantId(),
                        externalOrder.storeId(),
                        allocation.investorId(),
                        SettlementStatementLineType.INVESTOR_GROSS_RENT,
                        allocation.grossRentAmount(),
                        externalOrder.createdAt(),
                        "补录订单 " + externalOrder.recordNo() + " 出资方分润"
                    ),
                    allocation.rentBaseAmount()
                );
            }
        }

        for (var renewal : externalRenewalItems) {
            var snapshot = snapshotMap.get(renewal.settlementSnapshotId());
            if (snapshot == null
                || !"EXTERNAL_RENEWAL".equals(snapshot.sourceType().name())
                || !renewal.renewalEventId().equals(snapshot.sourceId())) {
                throw BusinessException.badRequest("补录订单 " + renewal.recordNo() + " 的续租分润快照不存在或不匹配");
            }
            var settlementBase = money(renewal.renewalAmount());
            if (snapshot.batteryCostAmount().signum() > 0) {
                merchantDraft(merchantDrafts, renewal.merchantId(), renewal.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_RENEWAL", renewal.renewalEventId(), null, null, null,
                        renewal.merchantId(), renewal.storeId(), 0L,
                        SettlementStatementLineType.MERCHANT_BATTERY_COST_PAYABLE,
                        snapshot.batteryCostAmount(), renewal.periodStartAt(),
                        "补录订单 " + renewal.recordNo() + " 续租电池费（门店应付电池公司）"
                    ),
                    settlementBase
                );
            }
            /* Legacy renewal snapshots keep the store entitlement in
             * merchantRentShareAmount; current profit versions store it in the separate
             * operation/maintenance fields.  Use the frozen snapshot value so
             * the monthly statement agrees with the income ledger for both
             * calculation versions. */
            var merchantShare = snapshot.calculationVersion().usesProfitSharing()
                ? money(snapshot.storeOperationAmount())
                : money(snapshot.merchantRentShareAmount());
            if (merchantShare.signum() > 0) {
                merchantDraft(merchantDrafts, renewal.merchantId(), renewal.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_RENEWAL", renewal.renewalEventId(), null, null, null,
                        renewal.merchantId(), renewal.storeId(), 0L,
                        SettlementStatementLineType.MERCHANT_RENT_SHARE,
                        merchantShare, renewal.periodStartAt(),
                        "补录订单 " + renewal.recordNo() + " 自动续租门店运营分润"
                    ),
                    snapshot.batteryCostAmount().signum() > 0 ? BigDecimal.ZERO : settlementBase
                );
            }
            if (snapshot.maintenanceFundAmount().signum() > 0) {
                merchantDraft(merchantDrafts, renewal.merchantId(), renewal.storeId()).register(
                    new LineDraft(
                        "EXTERNAL_RENEWAL", renewal.renewalEventId(), null, null, null,
                        renewal.merchantId(), renewal.storeId(), 0L,
                        SettlementStatementLineType.MERCHANT_MAINTENANCE_SHARE,
                        snapshot.maintenanceFundAmount(), renewal.periodStartAt(),
                        "补录订单 " + renewal.recordNo() + " 自动续租门店维修分润"
                    ),
                    BigDecimal.ZERO
                );
            }
            for (var allocation : buildInvestorAllocations(snapshot, null, settlementBase, null)) {
                if (allocation.grossRentAmount().signum() <= 0) {
                    continue;
                }
                investorDraft(investorDrafts, allocation.investorId()).register(
                    new LineDraft(
                        "EXTERNAL_RENEWAL", renewal.renewalEventId(), null, null, null,
                        renewal.merchantId(), renewal.storeId(), allocation.investorId(),
                        SettlementStatementLineType.INVESTOR_GROSS_RENT,
                        allocation.grossRentAmount(), renewal.periodStartAt(),
                        "补录订单 " + renewal.recordNo() + " 自动续租出资方分润"
                    ),
                    allocation.rentBaseAmount()
                );
            }
        }

        for (var maintenance : maintenanceCosts) {
            var totalCost = money(maintenance.totalCost());
            if (totalCost.signum() <= 0) {
                continue;
            }
            var responsibilityType = maintenance.responsibilityType();
            var merchantReimbursement = money(maintenance.merchantReimbursementAmount());
            if (responsibilityType != null && !responsibilityType.isBlank()) {
                if ("PLATFORM_SUBSIDY".equals(responsibilityType)) {
                    if (merchantReimbursement.signum() > 0 && maintenance.merchantId() != null && maintenance.storeId() != null) {
                        var merchantDraft = merchantDraft(merchantDrafts, maintenance.merchantId(), maintenance.storeId());
                        merchantDraft.register(
                            new LineDraft(
                                "MAINTENANCE",
                                maintenance.maintenanceId(),
                                null,
                                null,
                                maintenance.assetId(),
                                maintenance.merchantId(),
                                maintenance.storeId(),
                                maintenance.investorId(),
                                SettlementStatementLineType.MERCHANT_MAINTENANCE_REIMBURSE,
                                merchantReimbursement,
                                maintenance.occurredAt(),
                                "配件消耗补回"
                            ),
                            BigDecimal.ZERO
                        );
                    }
                }
                if ("MERCHANT_RESPONSIBILITY".equals(responsibilityType) && maintenance.merchantId() != null && maintenance.merchantId() > 0) {
                    var merchantDraft = merchantDraft(merchantDrafts, maintenance.merchantId(), maintenance.storeId() == null ? 0L : maintenance.storeId());
                    merchantDraft.register(
                        new LineDraft(
                            "MAINTENANCE",
                            maintenance.maintenanceId(),
                            null,
                            null,
                            maintenance.assetId(),
                            maintenance.merchantId(),
                            maintenance.storeId() == null ? 0L : maintenance.storeId(),
                            maintenance.investorId(),
                            SettlementStatementLineType.MERCHANT_MAINTENANCE_DEDUCT,
                            totalCost.negate(),
                            maintenance.occurredAt(),
                            "门店责任维修扣减"
                        ),
                        BigDecimal.ZERO
                    );
                }
                continue;
            }
            if ("MERCHANT".equals(maintenance.costBearerType()) && maintenance.costBearerId() != null && maintenance.costBearerId() > 0) {
                var merchantDraft = merchantDraft(merchantDrafts, maintenance.costBearerId(), maintenance.storeId() == null ? 0L : maintenance.storeId());
                merchantDraft.register(
                    new LineDraft(
                        "MAINTENANCE",
                        maintenance.maintenanceId(),
                        null,
                        null,
                        maintenance.assetId(),
                        maintenance.merchantId() == null ? 0L : maintenance.merchantId(),
                        maintenance.storeId() == null ? 0L : maintenance.storeId(),
                        maintenance.investorId(),
                        SettlementStatementLineType.MERCHANT_MAINTENANCE_DEDUCT,
                        totalCost.negate(),
                        maintenance.occurredAt(),
                        "维修费用扣减"
                    ),
                    BigDecimal.ZERO
                );
            }
        }

        var merchantCount = persistStatements(month, merchantDrafts.values());
        var investorCount = persistStatements(month, investorDrafts.values());
        return new SettlementStatementGenerateResponse(month, merchantCount, investorCount);
    }

    public SettlementOverviewResponse overview(String statementMonth) {
        authorizationService.requirePermission("settlement.read");
        var month = normalizeMonth(statementMonth);
        var overview = statementRepository.overview(month);
        return new SettlementOverviewResponse(
            month,
            money(overview.rentBaseAmount()),
            money(overview.signFeeIncomeAmount()),
            money(overview.merchantPayableAmount()),
            money(overview.investorPayableAmount()),
            money(overview.operationFeeAmount()),
            money(overview.batteryCostAmount()),
            money(overview.maintenanceDeductAmount()),
            money(overview.overdueAmount()),
            overview.merchantStatementCount(),
            overview.investorStatementCount()
        );
    }

    @Transactional(readOnly = true)
    public BatteryPayableResponse adminBatteryPayable(String statementMonth, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        return batteryPayable(statementMonth, null, storeId, false);
    }

    @Transactional(readOnly = true)
    public BatteryPayableResponse merchantBatteryPayable(String statementMonth, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        var current = AuthContext.get();
        if (current == null || current.account().merchantId() == null) {
            throw BusinessException.forbidden("当前账号未绑定商户");
        }
        if (storeId != null) {
            authorizationService.requireStoreAccess(current.account().merchantId(), storeId);
        }
        return batteryPayable(statementMonth, current.account().merchantId(), storeId, true);
    }

    /**
     * Calculates the source-backed battery payable independently of generated
     * statement rows. Its time attribution deliberately mirrors month-end:
     * paid formal bills use paidAt, supplemental initial periods use createdAt
     * and supplemental renewals use periodStartAt.
     */
    private BatteryPayableResponse batteryPayable(
        String statementMonth,
        Long merchantId,
        Long storeId,
        boolean enforceMerchantStoreScope
    ) {
        var month = normalizeMonth(statementMonth);
        var range = monthRange(month);
        var paidBillItems = statementRepository.listPaidBillItems(range.startAt(), range.endAt());
        var externalOrderItems = statementRepository.listExternalOrderItems(range.startAt(), range.endAt());
        var externalRenewalItems = statementRepository.listExternalRenewalItems(range.startAt(), range.endAt());

        var snapshotIds = paidBillItems.stream()
            .map(SettlementStatementRepository.PaidBillItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        externalOrderItems.stream()
            .map(SettlementStatementRepository.ExternalOrderItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .forEach(snapshotIds::add);
        externalRenewalItems.stream()
            .map(SettlementStatementRepository.ExternalRenewalItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .forEach(snapshotIds::add);
        var snapshotMap = settlementRepository.findSnapshotsByIds(snapshotIds).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementRuleSnapshot::id, item -> item));

        var initialAmount = BigDecimal.ZERO;
        var renewalAmount = BigDecimal.ZERO;
        var billAmount = BigDecimal.ZERO;
        var initialCount = 0;
        var renewalCount = 0;
        var billCount = 0;

        var billGroups = paidBillItems.stream().collect(java.util.stream.Collectors.groupingBy(
            SettlementStatementRepository.PaidBillItemRow::billId,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
        for (var group : billGroups.values()) {
            var first = group.getFirst();
            if (!matchesBatteryPayableScope(
                first.merchantId(), first.storeId(), merchantId, storeId, enforceMerchantStoreScope)) {
                continue;
            }
            var rentAmount = money(sumItemAmount(group, "RENT").add(sumItemAmount(group, "RENEWAL_RENT")));
            if (rentAmount.signum() <= 0) {
                continue;
            }
            if (first.settlementSnapshotId() == null) {
                throw BusinessException.badRequest("订单 " + first.orderId() + " 缺少分润快照，不能计算电池应付款");
            }
            var snapshot = snapshotMap.get(first.settlementSnapshotId());
            if (snapshot == null) {
                throw BusinessException.badRequest("订单 " + first.orderId() + " 的分润快照不存在");
            }
            var amount = snapshot.calculationVersion().usesProfitSharing()
                ? calculateProfitV2(snapshot, rentAmount).batteryCostAmount()
                : BigDecimal.ZERO;
            if (amount.signum() > 0) {
                billAmount = billAmount.add(amount);
                billCount += 1;
            }
        }

        for (var item : externalOrderItems) {
            if (!matchesBatteryPayableScope(
                item.merchantId(), item.storeId(), merchantId, storeId, enforceMerchantStoreScope)) {
                continue;
            }
            var snapshot = snapshotMap.get(item.settlementSnapshotId());
            if (snapshot == null
                || snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER
                || !item.externalOrderId().equals(snapshot.sourceId())) {
                throw BusinessException.badRequest("补录订单 " + item.recordNo() + " 的分润快照不存在或不匹配");
            }
            if (snapshot.calculationVersion().usesProfitSharing()
                && snapshot.settlementBaseAmount().signum() > 0
                && snapshot.batteryCostAmount().signum() > 0) {
                initialAmount = initialAmount.add(snapshot.batteryCostAmount());
                initialCount += 1;
            }
        }

        for (var item : externalRenewalItems) {
            if (!matchesBatteryPayableScope(
                item.merchantId(), item.storeId(), merchantId, storeId, enforceMerchantStoreScope)) {
                continue;
            }
            var snapshot = snapshotMap.get(item.settlementSnapshotId());
            if (snapshot == null
                || snapshot.sourceType() != SnapshotSourceType.EXTERNAL_RENEWAL
                || !item.renewalEventId().equals(snapshot.sourceId())) {
                throw BusinessException.badRequest("补录订单 " + item.recordNo() + " 的续租分润快照不存在或不匹配");
            }
            if (snapshot.batteryCostAmount().signum() > 0) {
                renewalAmount = renewalAmount.add(snapshot.batteryCostAmount());
                renewalCount += 1;
            }
        }

        initialAmount = money(initialAmount);
        renewalAmount = money(renewalAmount);
        billAmount = money(billAmount);
        return new BatteryPayableResponse(
            month,
            storeId,
            initialAmount,
            renewalAmount,
            billAmount,
            money(initialAmount.add(renewalAmount).add(billAmount)),
            initialCount,
            renewalCount,
            billCount
        );
    }

    private boolean matchesBatteryPayableScope(
        Long sourceMerchantId,
        Long sourceStoreId,
        Long merchantId,
        Long storeId,
        boolean enforceMerchantStoreScope
    ) {
        if (merchantId != null && !merchantId.equals(sourceMerchantId)) {
            return false;
        }
        if (storeId != null && !storeId.equals(sourceStoreId)) {
            return false;
        }
        return !enforceMerchantStoreScope || hasMerchantStoreAccess(sourceMerchantId, sourceStoreId);
    }

    public List<StoreProfitOverviewResponse> listStoreProfitOverview(String statementMonth, Long merchantId, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        var month = normalizeMonth(statementMonth);
        return statementRepository.listStoreProfitOverview(month, merchantId, storeId).stream()
            .map(row -> new StoreProfitOverviewResponse(
                row.statementId(),
                row.statementNo(),
                row.statementMonth(),
                row.merchantId(),
                row.storeId(),
                money(row.settlementBaseAmount()),
                money(row.signFeeAmount()),
                money(row.storeOperationAmount()),
                money(row.storeMaintenanceAmount()),
                money(row.batteryCostAmount()),
                money(row.maintenanceReimburseAmount()),
                money(row.maintenanceDeductAmount()),
                money(row.adjustmentAmount()),
                money(row.payableAmount()),
                row.orderCount(),
                row.billCount(),
                row.lineCount(),
                row.status(),
                row.generatedAt(),
                row.confirmedAt(),
                row.paidAt()
            ))
            .toList();
    }

    public List<SettlementStatementResponse> listAdmin(String statementMonth, String beneficiaryType, Long beneficiaryId, String status, Long merchantId, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        return statementRepository.listStatements(
            blankToNull(statementMonth),
            parseBeneficiaryNullable(beneficiaryType),
            beneficiaryId,
            parseStatusNullable(status),
            merchantId,
            storeId
        ).stream().map(this::toResponse).toList();
    }

    public List<SettlementStatementResponse> listMerchant(String statementMonth, String status, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        var current = AuthContext.get();
        if (current == null || current.account().merchantId() == null) {
            throw BusinessException.forbidden("当前账号未绑定商户");
        }
        if (storeId != null) {
            authorizationService.requireStoreAccess(current.account().merchantId(), storeId);
        }
        return statementRepository.listStatements(
            blankToNull(statementMonth),
            StatementBeneficiaryType.MERCHANT,
            current.account().merchantId(),
            parseStatusNullable(status),
            current.account().merchantId(),
            storeId
        ).stream()
            .filter(statement -> hasMerchantStoreAccess(statement.merchantId(), statement.storeId()))
            .map(this::toResponse)
            .toList();
    }

    public List<SettlementStatementResponse> listInvestor(String statementMonth, String status) {
        var current = AuthContext.get();
        if (current == null || current.account().investorId() == null) {
            throw BusinessException.forbidden("当前账号未绑定出资方");
        }
        return statementRepository.listStatements(
            blankToNull(statementMonth),
            StatementBeneficiaryType.INVESTOR,
            current.account().investorId(),
            parseStatusNullable(status),
            null,
            null
        ).stream().map(this::toResponse).toList();
    }

    public List<SettlementStatementLineResponse> listAdminLines(Long statementId) {
        authorizationService.requirePermission("settlement.read");
        ensureStatementExists(statementId);
        return statementRepository.listLines(statementId).stream().map(this::toLineResponse).toList();
    }

    public List<SettlementStatementLineResponse> listMerchantLines(Long statementId) {
        authorizationService.requirePermission("settlement.read");
        var statement = ensureStatementExists(statementId);
        if (statement.beneficiaryType() != StatementBeneficiaryType.MERCHANT) {
            throw BusinessException.forbidden("当前月结单不属于商户视图");
        }
        authorizationService.requireStoreAccess(statement.merchantId(), statement.storeId());
        return statementRepository.listLines(statementId).stream().map(this::toLineResponse).toList();
    }

    public List<SettlementStatementLineResponse> listInvestorLines(Long statementId) {
        var current = AuthContext.get();
        if (current == null || current.account().investorId() == null) {
            throw BusinessException.forbidden("当前账号未绑定出资方");
        }
        var statement = ensureStatementExists(statementId);
        if (statement.beneficiaryType() != StatementBeneficiaryType.INVESTOR || !statement.beneficiaryId().equals(current.account().investorId())) {
            throw BusinessException.forbidden("不能查看其他出资方月结单");
        }
        return statementRepository.listLines(statementId).stream().map(this::toLineResponse).toList();
    }

    @Transactional
    public SettlementStatementResponse updateStatus(Long id, String status) {
        authorizationService.requirePermission("settlement.write");
        var targetStatus = parseStatus(status);
        var current = statementRepository.findStatementForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("月结单不存在"));
        if (targetStatus.ordinal() < current.status().ordinal()) {
            throw BusinessException.badRequest("月结单状态不可回退");
        }
        if (targetStatus == current.status()) {
            return toResponse(current);
        }
        var updated = statementRepository.updateStatementStatus(id, targetStatus);
        if (targetStatus == SettlementStatementStatus.PAID) {
            incomeRepository.settleByStatement(
                updated.id(),
                updated.beneficiaryType(),
                updated.beneficiaryId(),
                updated.storeId()
            );
        }
        return toResponse(updated);
    }

    private int persistStatements(String month, java.util.Collection<StatementDraft> drafts) {
        var createdCount = 0;
        for (var draft : drafts) {
            if (draft.lines().isEmpty()) {
                continue;
            }
            var statement = statementRepository.createStatement(new SettlementStatementRepository.CreateStatementRow(
                "STM-" + UUID.randomUUID().toString().substring(0, 8),
                month,
                draft.beneficiaryType(),
                draft.beneficiaryId(),
                draft.merchantId(),
                draft.storeId(),
                money(draft.rentBaseAmount()),
                money(draft.signFeeIncomeAmount()),
                money(draft.rentShareIncomeAmount()),
                money(draft.operationFeeAmount()),
                money(draft.batteryCostAmount()),
                money(draft.maintenanceDeductAmount()),
                money(draft.adjustmentAmount()),
                money(draft.payableAmount()),
                draft.orderCount(),
                draft.billIds().size(),
                SettlementStatementStatus.DRAFT,
                "系统自动生成月结单"
            ));
            for (var line : draft.lines()) {
                statementRepository.createLine(new SettlementStatementRepository.CreateLineRow(
                    statement.id(),
                    "STL-" + UUID.randomUUID().toString().substring(0, 8),
                    line.sourceType(),
                    line.sourceId(),
                    line.orderId(),
                    line.billId(),
                    line.assetId(),
                    line.merchantId(),
                    line.storeId(),
                    line.investorId(),
                    line.lineType(),
                    money(line.amount()),
                    line.occurredAt(),
                    line.remark()
                ));
            }
            createdCount += 1;
        }
        return createdCount;
    }

    private List<InvestorAllocation> buildInvestorAllocations(
        SettlementRuleSnapshot snapshot,
        Long orderId,
        BigDecimal rentAmount,
        List<AssetFulfillmentRepository.OrderAssetUsageRow> usageRows
    ) {
        var usageAssets = usageRows == null ? List.<AssetFulfillmentRepository.OrderAssetUsageRow>of() : usageRows;
        var investorAssets = new ArrayList<InvestorAssetRef>();
        if (!usageAssets.isEmpty()) {
            for (var usage : usageAssets) {
                investorAssets.add(new InvestorAssetRef(usage.assetId(), usage.investorId()));
            }
        } else {
            if (snapshot.frameAssetId() != null) {
                var asset = assetRepository.findById(snapshot.frameAssetId()).orElseThrow(() -> BusinessException.badRequest("车架资产不存在"));
                investorAssets.add(new InvestorAssetRef(asset.id(), asset.investorId()));
            }
            if (snapshot.batteryAssetId() != null) {
                var asset = assetRepository.findById(snapshot.batteryAssetId()).orElseThrow(() -> BusinessException.badRequest("电池资产不存在"));
                investorAssets.add(new InvestorAssetRef(asset.id(), asset.investorId()));
            }
        }
        if (investorAssets.isEmpty()) {
            return List.of();
        }
        if (investorAssets.stream().anyMatch(asset -> asset.investorId() == null)) {
            throw BusinessException.badRequest("订单资产未绑定出资方，不能生成月结");
        }
        var investorIds = investorAssets.stream().map(InvestorAssetRef::investorId).distinct().toList();
        if (orderId != null && investorIds.size() > 1) {
            throw BusinessException.badRequest("订单 " + orderId + " 绑定了不同出资方的资产，请拆分订单后再生成月结");
        }
        /* Formal BILL statements are built from each paid bill's actual rent
         * items and therefore retain the recalculation path.  External
         * supplemental orders/renewal events already carry a frozen profit-model
         * investor allocation on their source snapshot.  Reuse that exact
         * amount so month-end statements stay cent-for-cent aligned with the
         * income ledger, including legacy/anomalous rows whose base fields may
         * not be identical. */
        BigDecimal grossAmount;
        if (snapshot.calculationVersion().usesProfitSharing()) {
            grossAmount = externalFrozenInvestorShare(snapshot)
                ? money(snapshot.investorShareAmount())
                : money(calculateProfitV2(snapshot, rentAmount).investorShareAmount());
        } else {
            grossAmount = money(rentAmount.multiply(snapshot.investorRentShareRate()));
        }
        var rentByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var grossByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var remainingRent = money(rentAmount);
        var remainingGross = grossAmount;
        var averageRent = remainingRent.divide(BigDecimal.valueOf(investorAssets.size()), 2, RoundingMode.HALF_UP);
        var averageGross = remainingGross.divide(BigDecimal.valueOf(investorAssets.size()), 2, RoundingMode.HALF_UP);
        for (var index = 0; index < investorAssets.size(); index += 1) {
            var asset = investorAssets.get(index);
            var assetRent = index == investorAssets.size() - 1 ? remainingRent : averageRent;
            var assetGross = index == investorAssets.size() - 1 ? remainingGross : averageGross;
            remainingRent = remainingRent.subtract(assetRent);
            remainingGross = remainingGross.subtract(assetGross);
            rentByInvestor.merge(asset.investorId(), money(assetRent), BigDecimal::add);
            grossByInvestor.merge(asset.investorId(), money(assetGross), BigDecimal::add);
        }
        return rentByInvestor.entrySet().stream()
            .map(entry -> new InvestorAllocation(entry.getKey(), money(entry.getValue()), money(grossByInvestor.get(entry.getKey()))))
            .toList();
    }

    private boolean externalFrozenInvestorShare(SettlementRuleSnapshot snapshot) {
        return snapshot.sourceType() == SnapshotSourceType.EXTERNAL_ORDER
            || snapshot.sourceType() == SnapshotSourceType.EXTERNAL_RENEWAL;
    }

    private ProfitSharingCalculator.Allocation calculateProfitV2(SettlementRuleSnapshot snapshot, BigDecimal settlementBaseAmount) {
        return ProfitSharingCalculator.calculate(
            snapshot.calculationVersion(),
            settlementBaseAmount,
            snapshot.channelFeeRate(),
            snapshot.platformFeeRate(),
            BatteryCostCalculator.prorate(snapshot.batteryCostAmount(), settlementBaseAmount, snapshot.rentalAmount()),
            snapshot.storeOperationRate(),
            snapshot.maintenanceFundRate(),
            snapshot.channelReferralRate(),
            snapshot.investorShareRate()
        );
    }

    private SettlementStatement ensureStatementExists(Long id) {
        return statementRepository.findStatement(id).orElseThrow(() -> BusinessException.badRequest("月结单不存在"));
    }

    private StatementDraft merchantDraft(Map<String, StatementDraft> drafts, Long merchantId, Long storeId) {
        var normalizedStoreId = storeId == null ? 0L : storeId;
        var key = merchantId + ":" + normalizedStoreId;
        return drafts.computeIfAbsent(key, ignored -> new StatementDraft(StatementBeneficiaryType.MERCHANT, merchantId, merchantId, normalizedStoreId));
    }

    private StatementDraft investorDraft(Map<Long, StatementDraft> drafts, Long investorId) {
        return drafts.computeIfAbsent(investorId, ignored -> new StatementDraft(StatementBeneficiaryType.INVESTOR, investorId, 0L, 0L));
    }

    private boolean hasMerchantStoreAccess(Long merchantId, Long storeId) {
        var current = AuthContext.get();
        if (current == null) {
            return false;
        }
        if (current.hasPermission("system.admin")) {
            return true;
        }
        return current.account().storeScopes().stream().anyMatch(scope -> {
            if (!scope.merchantId().equals(merchantId)) {
                return false;
            }
            return "ALL_MERCHANT_STORES".equals(scope.scopeType())
                || (scope.storeId() != null && scope.storeId().equals(storeId));
        });
    }

    private SettlementStatementResponse toResponse(SettlementStatement statement) {
        return new SettlementStatementResponse(
            statement.id(),
            statement.statementNo(),
            statement.statementMonth(),
            statement.beneficiaryType().name(),
            statement.beneficiaryId(),
            statement.merchantId(),
            statement.storeId(),
            statement.rentBaseAmount(),
            statement.signFeeIncomeAmount(),
            statement.rentShareIncomeAmount(),
            statement.operationFeeAmount(),
            statement.batteryCostAmount(),
            statement.maintenanceDeductAmount(),
            statement.adjustmentAmount(),
            statement.payableAmount(),
            statement.orderCount(),
            statement.billCount(),
            statement.status().name(),
            statement.generatedAt(),
            statement.confirmedAt(),
            statement.paidAt(),
            statement.remark(),
            statementRepository.countLines(statement.id())
        );
    }

    private SettlementStatementLineResponse toLineResponse(SettlementStatementLine line) {
        return new SettlementStatementLineResponse(
            line.id(),
            line.statementId(),
            line.lineNo(),
            line.sourceType(),
            line.sourceId(),
            line.orderId(),
            line.billId(),
            line.assetId(),
            line.merchantId(),
            line.storeId(),
            line.investorId(),
            line.lineType().name(),
            line.amount(),
            line.occurredAt(),
            line.remark(),
            line.createdAt()
        );
    }

    private BigDecimal sumItemAmount(List<SettlementStatementRepository.PaidBillItemRow> rows, String itemType) {
        return money(rows.stream()
            .filter(row -> itemType.equals(row.itemType()))
            .map(SettlementStatementRepository.PaidBillItemRow::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Range monthRange(String statementMonth) {
        var month = YearMonth.parse(statementMonth, MONTH_FORMAT);
        var startAt = month.atDay(1).atStartOfDay();
        return new Range(startAt, month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private String normalizeMonth(String statementMonth) {
        if (statementMonth == null || statementMonth.isBlank()) {
            return YearMonth.now().format(MONTH_FORMAT);
        }
        try {
            return YearMonth.parse(statementMonth, MONTH_FORMAT).format(MONTH_FORMAT);
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("结算月份格式必须为 yyyy-MM");
        }
    }

    private String normalizeRequiredMonth(String statementMonth) {
        if (statementMonth == null || statementMonth.isBlank()) {
            throw BusinessException.badRequest("请选择要生成月结单的月份");
        }
        return normalizeMonth(statementMonth);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private StatementBeneficiaryType parseBeneficiaryNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StatementBeneficiaryType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的月结对象类型");
        }
    }

    private SettlementStatementStatus parseStatusNullable(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private SettlementStatementStatus parseStatus(String value) {
        try {
            return SettlementStatementStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的月结状态");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal netOrderFee(BigDecimal amount) {
        return ProfitSharingCalculator.calculateOrderFee(amount).merchantNetAmount();
    }

    private record Range(LocalDateTime startAt, LocalDateTime endAt) {
    }

    private record InvestorAssetRef(Long assetId, Long investorId) {
    }

    private record InvestorAllocation(Long investorId, BigDecimal rentBaseAmount, BigDecimal grossRentAmount) {
    }

    private record LineDraft(
        String sourceType,
        Long sourceId,
        Long orderId,
        Long billId,
        Long assetId,
        Long merchantId,
        Long storeId,
        Long investorId,
        SettlementStatementLineType lineType,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String remark
    ) {
    }

    private static final class StatementDraft {
        private final StatementBeneficiaryType beneficiaryType;
        private final Long beneficiaryId;
        private final Long merchantId;
        private final Long storeId;
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<String> businessOrderKeys = new LinkedHashSet<>();
        private final Set<Long> billIds = new LinkedHashSet<>();
        private final List<LineDraft> lines = new ArrayList<>();
        private BigDecimal rentBaseAmount = BigDecimal.ZERO;
        private BigDecimal signFeeIncomeAmount = BigDecimal.ZERO;
        private BigDecimal rentShareIncomeAmount = BigDecimal.ZERO;
        private BigDecimal operationFeeAmount = BigDecimal.ZERO;
        private BigDecimal batteryCostAmount = BigDecimal.ZERO;
        private BigDecimal maintenanceDeductAmount = BigDecimal.ZERO;
        private BigDecimal adjustmentAmount = BigDecimal.ZERO;
        private BigDecimal payableAmount = BigDecimal.ZERO;

        private StatementDraft(StatementBeneficiaryType beneficiaryType, Long beneficiaryId, Long merchantId, Long storeId) {
            this.beneficiaryType = beneficiaryType;
            this.beneficiaryId = beneficiaryId;
            this.merchantId = merchantId == null ? 0L : merchantId;
            this.storeId = storeId == null ? 0L : storeId;
        }

        private void register(LineDraft line, BigDecimal rentBaseIncrement) {
            if (line.orderId() != null) {
                orderIds.add(line.orderId());
                businessOrderKeys.add("ORDER:" + line.orderId());
            }
            if ("EXTERNAL_ORDER".equals(line.sourceType())) {
                businessOrderKeys.add("EXTERNAL_ORDER:" + line.sourceId());
            }
            if (line.billId() != null) {
                billIds.add(line.billId());
            }
            lines.add(line);
            rentBaseAmount = rentBaseAmount.add(rentBaseIncrement == null ? BigDecimal.ZERO : rentBaseIncrement);
            if (line.lineType() != SettlementStatementLineType.MERCHANT_BATTERY_COST_PAYABLE) {
                payableAmount = payableAmount.add(line.amount());
            }
            switch (line.lineType()) {
                case MERCHANT_SIGN_FEE -> signFeeIncomeAmount = signFeeIncomeAmount.add(line.amount());
                case MERCHANT_RENT_SHARE, MERCHANT_MAINTENANCE_SHARE, INVESTOR_GROSS_RENT -> rentShareIncomeAmount = rentShareIncomeAmount.add(line.amount());
                case INVESTOR_OPERATION_FEE -> operationFeeAmount = operationFeeAmount.add(line.amount().abs());
                case MERCHANT_BATTERY_COST_PAYABLE -> batteryCostAmount = batteryCostAmount.add(line.amount().abs());
                case MERCHANT_MAINTENANCE_DEDUCT, INVESTOR_MAINTENANCE_DEDUCT -> maintenanceDeductAmount = maintenanceDeductAmount.add(line.amount().abs());
                case MERCHANT_ADJUSTMENT, INVESTOR_ADJUSTMENT -> adjustmentAmount = adjustmentAmount.add(line.amount());
            }
        }

        private StatementBeneficiaryType beneficiaryType() {
            return beneficiaryType;
        }

        private Long beneficiaryId() {
            return beneficiaryId;
        }

        private Long merchantId() {
            return merchantId;
        }

        private Long storeId() {
            return storeId;
        }

        private int orderCount() {
            return businessOrderKeys.size();
        }

        private Set<Long> billIds() {
            return billIds;
        }

        private List<LineDraft> lines() {
            return lines;
        }

        private BigDecimal rentBaseAmount() {
            return rentBaseAmount;
        }

        private BigDecimal signFeeIncomeAmount() {
            return signFeeIncomeAmount;
        }

        private BigDecimal rentShareIncomeAmount() {
            return rentShareIncomeAmount;
        }

        private BigDecimal operationFeeAmount() {
            return operationFeeAmount;
        }

        private BigDecimal batteryCostAmount() {
            return batteryCostAmount;
        }

        private BigDecimal maintenanceDeductAmount() {
            return maintenanceDeductAmount;
        }

        private BigDecimal adjustmentAmount() {
            return adjustmentAmount;
        }

        private BigDecimal payableAmount() {
            return payableAmount;
        }
    }
}
