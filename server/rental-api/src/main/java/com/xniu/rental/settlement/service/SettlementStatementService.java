package com.xniu.rental.settlement.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.settlement.dto.SettlementOverviewResponse;
import com.xniu.rental.settlement.dto.SettlementStatementGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementStatementLineResponse;
import com.xniu.rental.settlement.dto.SettlementStatementResponse;
import com.xniu.rental.settlement.dto.StoreProfitOverviewResponse;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SettlementStatement;
import com.xniu.rental.settlement.model.SettlementStatementLine;
import com.xniu.rental.settlement.model.SettlementStatementLineType;
import com.xniu.rental.settlement.model.SettlementStatementStatus;
import com.xniu.rental.settlement.model.StatementBeneficiaryType;
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
    private static final BigDecimal ORDER_FEE_SERVICE_RATE = new BigDecimal("0.03");

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
        var month = normalizeRequiredMonth(statementMonth);
        if (statementRepository.hasLockedStatements(month)) {
            throw BusinessException.badRequest("该月份已存在已确认或已支付月结单，不能重新生成");
        }
        statementRepository.deleteDraftStatements(month);

        var range = monthRange(month);
        settlementIncomeService.syncPaidBills(range.startAt(), range.endAt());
        var paidBillItems = statementRepository.listPaidBillItems(range.startAt(), range.endAt());
        var externalOrderItems = statementRepository.listExternalOrderItems(range.startAt(), range.endAt());
        var maintenanceCosts = statementRepository.listMaintenanceCosts(range.startAt(), range.endAt());
        var snapshotIds = paidBillItems.stream()
            .map(SettlementStatementRepository.PaidBillItemRow::settlementSnapshotId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        externalOrderItems.stream()
            .map(SettlementStatementRepository.ExternalOrderItemRow::settlementSnapshotId)
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
            var profitAllocation = snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2
                ? calculateProfitV2(snapshot, rentAmount)
                : null;
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
                        snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2 ? "门店运营分润" : "租金分润"
                    ),
                    rentAmount
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
                            snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2 ? "出资方分润" : "出资方租金毛收益"
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
            var settlementBase = money(externalOrder.verificationAmount());
            if (settlementBase.signum() <= 0) {
                continue;
            }
            var merchantShare = snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2
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
                    settlementBase
                );
            }
            if (snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2
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
            money(overview.maintenanceDeductAmount()),
            money(overview.overdueAmount()),
            overview.merchantStatementCount(),
            overview.investorStatementCount()
        );
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
        var grossAmount = snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2
            ? money(calculateProfitV2(snapshot, rentAmount).investorShareAmount())
            : money(rentAmount.multiply(snapshot.investorRentShareRate()));
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

    private ProfitSharingCalculator.Allocation calculateProfitV2(SettlementRuleSnapshot snapshot, BigDecimal settlementBaseAmount) {
        return ProfitSharingCalculator.calculate(
            settlementBaseAmount,
            snapshot.channelFeeRate(),
            snapshot.platformFeeRate(),
            BatteryCostCalculator.prorate(snapshot.batteryCostAmount(), settlementBaseAmount, snapshot.settlementBaseAmount()),
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
        return money(amount).multiply(BigDecimal.ONE.subtract(ORDER_FEE_SERVICE_RATE)).setScale(2, RoundingMode.HALF_UP);
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
            payableAmount = payableAmount.add(line.amount());
            switch (line.lineType()) {
                case MERCHANT_SIGN_FEE -> signFeeIncomeAmount = signFeeIncomeAmount.add(line.amount());
                case MERCHANT_RENT_SHARE, MERCHANT_MAINTENANCE_SHARE, INVESTOR_GROSS_RENT -> rentShareIncomeAmount = rentShareIncomeAmount.add(line.amount());
                case INVESTOR_OPERATION_FEE -> operationFeeAmount = operationFeeAmount.add(line.amount().abs());
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
