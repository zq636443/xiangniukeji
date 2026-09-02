package com.xniu.rental.externalorder.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderRenewalResponse;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalSource;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.ProfitSharingCalculator;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalOrderManualRenewalService {

    private static final DateTimeFormatter STATEMENT_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ProductRepository productRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementStatementService settlementStatementService;
    private final AuthorizationService authorizationService;

    public ExternalOrderManualRenewalService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ProductRepository productRepository,
        SettlementRepository settlementRepository,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementStatementRepository settlementStatementRepository,
        SettlementStatementService settlementStatementService,
        AuthorizationService authorizationService
    ) {
        this.orderRepository = orderRepository;
        this.renewalRepository = renewalRepository;
        this.productRepository = productRepository;
        this.settlementRepository = settlementRepository;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementStatementService = settlementStatementService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public ExternalOrderRenewalResponse create(Long externalOrderId, ExternalOrderManualRenewalRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = orderRepository.findByIdForUpdate(externalOrderId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单才能人工续租");
        }
        if (order.expectedReturnAt() == null) {
            throw BusinessException.badRequest("补录订单缺少当前预计归还时间");
        }
        if (request == null || request.periodEndAt() == null) {
            throw BusinessException.badRequest("请输入本次续租结束时间");
        }
        if (request.expectedPeriodStartAt() == null
            || !order.expectedReturnAt().withNano(0).equals(request.expectedPeriodStartAt().withNano(0))) {
            throw BusinessException.badRequest("订单续租起点已变化，请刷新订单后重新确认租期和核销金额");
        }
        var periodStartAt = order.expectedReturnAt();
        var periodEndAt = request.periodEndAt().withNano(0);
        if (!periodEndAt.isAfter(periodStartAt)) {
            throw BusinessException.badRequest("本次续租结束时间必须晚于当前预计归还时间");
        }
        var renewalAmount = money(request.verificationAmount());
        if (renewalAmount == null || renewalAmount.signum() <= 0) {
            throw BusinessException.badRequest("本次续租毛额必须大于 0");
        }
        var remark = normalizeRemark(request.remark());
        var statementMonth = periodStartAt.format(STATEMENT_MONTH_FORMAT);
        settlementStatementRepository.lockStatementsByMonthForUpdate(statementMonth);
        if (settlementStatementRepository.hasLockedStatements(statementMonth)) {
            throw BusinessException.badRequest("本次续租起点所在月份已锁定，不能直接补记；请通过结算调整单处理");
        }
        var regenerateDraftStatement = settlementStatementRepository.hasDraftStatements(statementMonth);
        var sourceSnapshot = order.settlementSnapshotId() == null
            ? null
            : settlementRepository.findSnapshot(order.settlementSnapshotId()).orElse(null);
        if (sourceSnapshot == null
            || sourceSnapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER
            || !order.id().equals(sourceSnapshot.sourceId())) {
            throw BusinessException.badRequest("补录订单原始分润快照不存在");
        }
        if (!sourceSnapshot.calculationVersion().usesProfitSharing()) {
            throw BusinessException.badRequest("补录订单分润快照不是当前分润口径，请先修复快照后再人工续租");
        }
        var sku = productRepository.findSku(order.skuId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单 SKU 不存在"));
        var batteryCost = BatteryCostCalculator.calculateExactPeriod(
            sku.batteryCostDailyAmount(),
            sku.batteryCostMonthlyAmount(),
            periodStartAt,
            periodEndAt
        );

        // Validate the economic floor before creating any event or advancing
        // the paid-through boundary. ProfitSharingCalculator deliberately
        // clamps a negative distributable amount to zero, which is correct for
        // reporting but must not turn an underfunded manual renewal into a
        // silently accepted transaction.
        var previewVersion = sourceSnapshot.calculationVersion().usesGrossChannelReferral()
            ? sourceSnapshot.calculationVersion()
            : batteryCost.signum() > 0
                ? SettlementCalculationVersion.PROFIT_V3
                : sourceSnapshot.calculationVersion();
        var preview = ProfitSharingCalculator.calculate(
            previewVersion,
            renewalAmount,
            sourceSnapshot.channelFeeRate(),
            sourceSnapshot.platformFeeRate(),
            batteryCost,
            sourceSnapshot.storeOperationRate(),
            sourceSnapshot.maintenanceFundRate(),
            sourceSnapshot.channelReferralRate(),
            sourceSnapshot.investorShareRate()
        );
        var amountAfterFixedDeductions = preview.settlementBaseAmount()
            .subtract(preview.channelFeeAmount())
            .subtract(preview.platformFeeAmount())
            .subtract(previewVersion.usesGrossChannelReferral()
                ? preview.channelReferralAmount()
                : BigDecimal.ZERO)
            .subtract(preview.batteryCostAmount())
            .setScale(2, RoundingMode.HALF_UP);
        if (amountAfterFixedDeductions.signum() < 0) {
            throw BusinessException.badRequest("本次续租毛额不足以覆盖渠道费、平台费、渠道引流分润和全租期电池成本");
        }
        if (preview.investorShareAmount().signum() < 0) {
            throw BusinessException.badRequest("本次续租毛额不足以覆盖毛额级渠道引流分润、电池成本和其他分润");
        }

        var operatorAccountId = currentAccountId();
        var event = renewalRepository.create(
            order.id(),
            nextEventNo(),
            renewalRepository.nextPeriodNo(order.id()),
            periodStartAt,
            periodEndAt,
            renewalAmount,
            order.renewalAmount() == null ? renewalAmount : money(order.renewalAmount()),
            batteryCost,
            ExternalOrderRenewalSource.MANUAL,
            operatorAccountId,
            remark
        );
        var snapshot = settlementService.createExternalRenewalSnapshot(
            event.id(),
            order.settlementSnapshotId(),
            event.renewalAmount(),
            event.batteryCostAmount()
        );
        event = renewalRepository.attachSnapshot(event.id(), snapshot.id());
        settlementIncomeService.createExternalRenewalEntries(
            event.id(),
            event.eventNo(),
            snapshot.id(),
            event.periodStartAt(),
            event.renewalAmount()
        );
        orderRepository.advanceExpectedReturnAt(order.id(), periodEndAt);
        orderRepository.addLog(
            order.id(),
            order.orderStatus(),
            order.orderStatus(),
            ExternalOrderOperationType.MANUAL_RENEW,
            operatorAccountId,
            "人工续租至 " + periodEndAt + "；本次核销毛额 " + renewalAmount
        );
        if (regenerateDraftStatement) {
            settlementStatementService.regenerateUnlockedMonthAlreadyLocked(statementMonth);
        }
        return toResponse(order, event);
    }

    private ExternalOrderRenewalResponse toResponse(
        com.xniu.rental.externalorder.model.ExternalRentalOrder order,
        com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent event
    ) {
        return new ExternalOrderRenewalResponse(
            event.id(),
            event.externalOrderId(),
            event.eventNo(),
            order.recordNo(),
            order.merchantId(),
            order.storeId(),
            event.periodNo(),
            event.periodStartAt(),
            event.periodEndAt(),
            event.renewalAmount(),
            event.batteryCostAmount(),
            event.eventStatus(),
            event.renewalSource().name(),
            event.operatorAccountId(),
            event.remark(),
            false,
            event.periodStartAt()
        );
    }

    private BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRemark(String value) {
        var remark = value == null ? null : value.trim();
        if (remark == null || remark.isEmpty()) {
            throw BusinessException.badRequest("请填写人工续租备注");
        }
        if (remark.length() > 255) {
            throw BusinessException.badRequest("人工续租备注不能超过 255 个字");
        }
        return remark;
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private String nextEventNo() {
        return "ERN-" + UUID.randomUUID();
    }
}
