package com.xniu.rental.externalorder.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCompleteRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderImportRowRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderImportRowResultResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderLogResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderTerminateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderRenewalResponse;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderSourcePlatform;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderPricingRevisionRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderVerificationRevisionRepository;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.model.ProductPackage;
import com.xniu.rental.product.model.ProductSku;
import com.xniu.rental.product.model.ProductStatus;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuPackage;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.ProfitSharingCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalRentalOrderService {

    private static final Logger log = LoggerFactory.getLogger(ExternalRentalOrderService.class);

    private final ExternalRentalOrderRepository externalRentalOrderRepository;
    private final ExternalOrderPricingRevisionRepository pricingRevisionRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ExternalOrderVerificationRevisionRepository verificationRevisionRepository;
    private final ExternalOrderAutoRenewalService autoRenewalService;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final MerchantRepository merchantRepository;
    private final AuthorizationService authorizationService;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementIncomeRepository settlementIncomeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementStatementService settlementStatementService;
    private final TransactionTemplate transactionTemplate;

    public ExternalRentalOrderService(
        ExternalRentalOrderRepository externalRentalOrderRepository,
        ExternalOrderPricingRevisionRepository pricingRevisionRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ExternalOrderVerificationRevisionRepository verificationRevisionRepository,
        ExternalOrderAutoRenewalService autoRenewalService,
        ProductRepository productRepository,
        AssetRepository assetRepository,
        OrderRepository orderRepository,
        StoreRepository storeRepository,
        MerchantRepository merchantRepository,
        AuthorizationService authorizationService,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementIncomeRepository settlementIncomeRepository,
        SettlementRepository settlementRepository,
        SettlementStatementRepository settlementStatementRepository,
        SettlementStatementService settlementStatementService,
        TransactionTemplate transactionTemplate
    ) {
        this.externalRentalOrderRepository = externalRentalOrderRepository;
        this.pricingRevisionRepository = pricingRevisionRepository;
        this.renewalRepository = renewalRepository;
        this.verificationRevisionRepository = verificationRevisionRepository;
        this.autoRenewalService = autoRenewalService;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.merchantRepository = merchantRepository;
        this.authorizationService = authorizationService;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementIncomeRepository = settlementIncomeRepository;
        this.settlementRepository = settlementRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementStatementService = settlementStatementService;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ExternalRentalOrderResponse> listOrders(
        String status,
        Long storeId,
        String sourcePlatform,
        Long storeSkuId,
        Long packageId,
        LocalDateTime rentStartedFrom,
        LocalDateTime rentStartedTo,
        LocalDateTime expectedReturnFrom,
        LocalDateTime expectedReturnTo,
        String keyword
    ) {
        authorizationService.requirePermission("order.read");
        return externalRentalOrderRepository.list(
                parseStatusNullable(status),
                null,
                storeId,
                parseSourceNullable(sourcePlatform),
                storeSkuId,
                packageId,
                rentStartedFrom,
                rentStartedTo,
                expectedReturnFrom,
                expectedReturnTo,
                keyword
            ).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<ExternalRentalOrderResponse> listMerchantOrders(
        Long storeId,
        String status,
        String sourcePlatform,
        Long storeSkuId,
        Long packageId,
        LocalDateTime rentStartedFrom,
        LocalDateTime rentStartedTo,
        LocalDateTime expectedReturnFrom,
        LocalDateTime expectedReturnTo,
        String keyword
    ) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = ensureStore(storeId);
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return externalRentalOrderRepository.list(
                parseStatusNullable(status),
                store.merchantId(),
                storeId,
                parseSourceNullable(sourcePlatform),
                storeSkuId,
                packageId,
                rentStartedFrom,
                rentStartedTo,
                expectedReturnFrom,
                expectedReturnTo,
                keyword
            ).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<ExternalOrderRenewalResponse> listRenewals(Long storeId) {
        authorizationService.requirePermission("order.read");
        return renewalRepository.listAccrued(storeId).stream().map(this::toRenewalResponse).toList();
    }

    public List<ExternalOrderRenewalResponse> listMerchantRenewals(Long storeId) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = ensureStore(storeId);
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return renewalRepository.listAccrued(storeId).stream().map(this::toRenewalResponse).toList();
    }

    public ExternalRentalOrderResponse getOrder(Long id) {
        authorizationService.requirePermission("order.read");
        var view = ensureView(id);
        authorizationService.requireStoreAccess(view.order().merchantId(), view.order().storeId());
        return toResponse(view);
    }

    @Transactional
    public ExternalRentalOrderResponse createOrder(ExternalRentalOrderCreateRequest request) {
        authorizationService.requirePermission("order.operate");
        return createOrderInternal(request);
    }

    @Transactional
    public ExternalRentalOrderResponse updateOrder(Long id, ExternalRentalOrderUpdateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        /* A terminal order is historical data.  Do not resolve its SKU/package
         * through today's enabled catalog: a discontinued product must not
         * make a harmless customer correction fail, and a missing snapshot
         * must never be recreated by an ordinary edit.  The terminal branch
         * below accepts only descriptive fields plus the current verification
         * amount, preserving every settlement-attribution field byte-for-byte.
         */
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            return updateTerminalOrderMetadata(order, request);
        }

        var storeSku = ensureStoreSku(request.storeSkuId());
        authorizationService.requireStoreAccess(storeSku.merchantId(), storeSku.storeId());
        var sku = ensureSku(storeSku.skuId());
        var packageTemplate = ensureStoreSkuPackage(storeSku, request.packageId());
        var packagePricing = storeSkuPackageAmount(storeSku.id(), request.packageId());
        var leaseMultiplier = request.leaseMultiplier() == null
            ? normalizeLeaseMultiplier(order.leaseMultiplier())
            : normalizeLeaseMultiplier(request.leaseMultiplier());
        validateRequestAssets(request.frameAssetId(), request.batteryAssetId(), sku);
        /* For a terminal order, omitted optional date/amount fields mean
         * "keep the historical value".  Recomputing them from today's SKU
         * would otherwise turn a harmless customer-info edit into a free
         * lease extension or a silent price rewrite. */
        var expectedReturnAt = request.expectedReturnAt() == null
            ? (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
                ? calculateExpectedReturnAt(request.rentStartedAt(), packageTemplate, leaseMultiplier)
                : order.expectedReturnAt())
            : request.expectedReturnAt();
        if (expectedReturnAt != null && expectedReturnAt.isBefore(request.rentStartedAt())) {
            throw BusinessException.badRequest("预计归还时间不能早于起租时间");
        }
        var renewalScheduleChanged = !java.util.Objects.equals(order.rentStartedAt(), request.rentStartedAt())
            || !java.util.Objects.equals(order.expectedReturnAt(), expectedReturnAt);
        var hasRenewalEvents = renewalRepository.hasAccruedEvents(order.id());
        if (renewalScheduleChanged && hasRenewalEvents) {
            throw BusinessException.badRequest("补录订单已生成续租周期，不能再修改起租或预计归还时间");
        }
        var now = LocalDateTime.now();
        if (renewalScheduleChanged
            && !hasRenewalEvents
            && order.expectedReturnAt() != null
            && !order.expectedReturnAt().isAfter(now)) {
            throw BusinessException.badRequest("补录订单已到期，不能直接修改起租或预计归还时间；请先生成续租收益或使用专用续租流程");
        }

        if (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE) {
            validateEditableAsset(request.frameAssetId(), order.frameAssetId(), storeSku, order);
            validateEditableAsset(request.batteryAssetId(), order.batteryAssetId(), storeSku, order);
            releaseEditedAsset(order.frameAssetId(), request.frameAssetId(), "补录订单编辑释放原主资产");
            releaseEditedAsset(order.batteryAssetId(), request.batteryAssetId(), "补录订单编辑释放原第二资产");
            occupyEditedAsset(request.frameAssetId(), order.frameAssetId(), "补录订单编辑绑定主资产");
            occupyEditedAsset(request.batteryAssetId(), order.batteryAssetId(), "补录订单编辑绑定第二资产");
            transferRetainedEditedAsset(
                request.frameAssetId(),
                order.frameAssetId(),
                storeSku,
                "补录订单编辑自动调拨主资产"
            );
            transferRetainedEditedAsset(
                request.batteryAssetId(),
                order.batteryAssetId(),
                storeSku,
                "补录订单编辑自动调拨第二资产"
            );
        } else {
            validateHistoricalEditableAsset(request.frameAssetId(), order.frameAssetId(), storeSku);
            validateHistoricalEditableAsset(request.batteryAssetId(), order.batteryAssetId(), storeSku);
        }

        var externalRentalAmount = normalizeMoney(
            request.externalRentalAmount(),
            order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
                ? packagePricing.rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier))
                : order.externalRentalAmount()
        );
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount());
        var verificationEdited = !sameMoney(order.verificationAmount(), verificationAmount);
        var defaultSignFeeAmount = effectiveSignFeeAmount(packageTemplate, storeSku);
        var signFeeAmount = normalizeMoney(
            request.signFeeAmount(),
            order.orderStatus() == ExternalRentalOrderStatus.ACTIVE ? defaultSignFeeAmount : order.signFeeAmount()
        );
        var depositAmount = normalizeMoney(
            request.depositAmount(),
            order.orderStatus() == ExternalRentalOrderStatus.ACTIVE ? packagePricing.depositAmount() : order.depositAmount()
        );
        var nextSourcePlatform = parseSource(request.sourcePlatform());
        var nextLeaseUnit = packageTemplate.leaseUnit().name();
        var nextLeaseValue = packageTemplate.leaseValue() * leaseMultiplier;
        var nextTotalPeriods = packageTemplate.totalPeriods() * leaseMultiplier;
        /* Extending an already-started active order by changing the multiplier
         * or expected return date would skip the original renewal boundary
         * without creating a billable extension event.  Until a dedicated
         * extension workflow exists, fail closed; ordinary customer/amount
         * edits remain available and are handled by the revision timeline. */
        var orderHasStarted = order.rentStartedAt() != null && !order.rentStartedAt().isAfter(now);
        var scheduleExtended = (order.expectedReturnAt() == null && expectedReturnAt != null)
            || (order.expectedReturnAt() != null && expectedReturnAt != null
                && expectedReturnAt.isAfter(order.expectedReturnAt()))
            || nextTotalPeriods > order.totalPeriods()
            || leaseMultiplier > normalizeLeaseMultiplier(order.leaseMultiplier());
        if (orderHasStarted && scheduleExtended) {
            throw BusinessException.badRequest("已开始的补录订单不能直接增加租期或延后归还时间；请使用续租流程生成对应收益");
        }
        /* These fields determine the financial owner/rate basis of the
         * snapshot. A terminal order may still correct descriptive metadata
         * such as source platform, but changing any of these fields would make
         * the order row point at a different store/asset/fee while its frozen
         * income still belongs to the original snapshot. */
        var terminalSettlementAttributionChanged = !sameMoney(order.signFeeAmount(), signFeeAmount)
            || !sameMoney(order.externalRentalAmount(), externalRentalAmount)
            || !sameMoney(order.depositAmount(), depositAmount)
            || !java.util.Objects.equals(order.frameAssetId(), request.frameAssetId())
            || !java.util.Objects.equals(order.batteryAssetId(), request.batteryAssetId())
            || !java.util.Objects.equals(order.storeSkuId(), storeSku.id())
            || !java.util.Objects.equals(order.skuId(), storeSku.skuId())
            || !java.util.Objects.equals(order.packageId(), packageTemplate.id())
            || !java.util.Objects.equals(order.leaseMultiplier(), leaseMultiplier)
            || !java.util.Objects.equals(order.leaseUnit(), nextLeaseUnit)
            || !java.util.Objects.equals(order.leaseValue(), nextLeaseValue)
            || !java.util.Objects.equals(order.totalPeriods(), nextTotalPeriods)
            || !java.util.Objects.equals(order.rentStartedAt(), request.rentStartedAt())
            || !java.util.Objects.equals(order.expectedReturnAt(), expectedReturnAt);
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE && terminalSettlementAttributionChanged) {
            throw BusinessException.badRequest("已结束补录订单只能修改客户资料、来源平台、核销金额和备注，不能修改门店、资产、办单费或租期结构");
        }
        /* A settlement-affecting edit must never replace a settled snapshot.
         * Metadata-only edits (customer name/phone, remark, return date) keep
         * the existing snapshot and income rows intact. */
        var structuralSettlementChanged = !sameMoney(order.signFeeAmount(), signFeeAmount)
            || !java.util.Objects.equals(order.sourcePlatform(), nextSourcePlatform)
            || !java.util.Objects.equals(order.frameAssetId(), request.frameAssetId())
            || !java.util.Objects.equals(order.batteryAssetId(), request.batteryAssetId())
            || !java.util.Objects.equals(order.storeSkuId(), storeSku.id())
            || !java.util.Objects.equals(order.skuId(), storeSku.skuId())
            || !java.util.Objects.equals(order.packageId(), packageTemplate.id())
            || !java.util.Objects.equals(order.leaseMultiplier(), leaseMultiplier)
            || !java.util.Objects.equals(order.leaseUnit(), nextLeaseUnit)
            || !java.util.Objects.equals(order.leaseValue(), nextLeaseValue)
            || !java.util.Objects.equals(order.totalPeriods(), nextTotalPeriods);
        var settlementStructureChanged = order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
            ? structuralSettlementChanged
            : terminalSettlementAttributionChanged;
        var settlementChanged = verificationEdited || structuralSettlementChanged;
        /* The first snapshot is the immutable first-period fact for every
         * supplemental order. A verification-only edit records an
         * effective-dated renewal override; it must not rewrite the initial
         * snapshot, regardless of whether the order is active, completed, or
         * terminated. Once an order is completed/terminated, all settlement-
         * affecting fields are historical metadata as well: rebuilding the
         * snapshot there could move completed income to another store or
         * resurrect income that termination intentionally removed. */
        var preserveInitialSettlement = order.settlementSnapshotId() != null
            && (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE
                || (verificationEdited && !structuralSettlementChanged));

        /* A verification edit on an order that has no immutable first-period
         * snapshot rebuilds the initial settlement. Any existing
         * DRAFT/RECONCILING line for that source is derived from the old
         * snapshot and must be rebuilt in the same transaction; otherwise the
         * order/income rows would show the new amount while the month-end
         * draft still shows the old one. For a terminal order the existing
         * snapshot is always preserved, so this rebuild path is limited to
         * active orders and legacy rows that genuinely lack a snapshot.
         *
         * The order row is already locked above.  Lock the affected months
         * only after that order lock, matching delete/terminate/reconcile and
         * avoiding the order -> month / month -> order deadlock.  Only months
         * with an existing source-linked draft line are returned; the public
         * generator locks all external-order rows in id order before reading
         * them, so a missing candidate month cannot race this edit.
         */
        var rebuiltInitialDraftMonths = new java.util.LinkedHashSet<String>();
        if (verificationEdited) {
            if (!preserveInitialSettlement) {
                rebuiltInitialDraftMonths.addAll(settlementStatementRepository.listDraftStatementMonthsBySource(
                    SnapshotSourceType.EXTERNAL_ORDER.name(), order.id()
                ));
            }
            /* Lock the complete affected-month set up front, not just the
             * initial line's month. reconcilePendingEvents below may need to
             * lock accrued-renewal months too; taking them here in one sorted
             * pass prevents a historical event month from inverting the
             * global month-lock order. This pre-lock is required even when
             * the initial snapshot is preserved: reconcilePendingEvents may
             * still replace pending renewal rows. */
            var monthsToLock = new java.util.LinkedHashSet<String>(collectDraftStatementMonths(order));
            lockDraftStatementMonths(monthsToLock);
        }
        /* Keep the lock order explicit: order row -> statement rows -> income
         * rows.  In particular, do not acquire income locks and then lock a
         * statement month; status transitions use statement -> income and
         * could otherwise form a cycle. */
        var initialSettlementLocked = hasLockedInitialSettlement(order.id());
        var anySettlementLocked = initialSettlementLocked
            || renewalRepository.hasLockedStatementLinesByExternalOrderForUpdate(order.id())
            || renewalRepository.hasNonPendingIncomeByExternalOrderForUpdate(order.id());
        if (settlementStructureChanged && hasRenewalEvents) {
            throw BusinessException.badRequest("补录订单已生成续租周期，不能再修改办单费、分润资产、渠道或套餐等结构字段");
        }
        if (settlementStructureChanged && settlementStatementRepository.hasLinesBySource(
            SnapshotSourceType.EXTERNAL_ORDER.name(), order.id())) {
            throw BusinessException.badRequest("补录订单首期收益已进入月结单，不能修改办单费、分润资产、渠道或套餐等结构字段");
        }
        if (anySettlementLocked && settlementStructureChanged) {
            throw BusinessException.badRequest("补录订单收益已锁定，不能修改办单费、分润资产、渠道或套餐等结构字段");
        }
        if (verificationEdited && anySettlementLocked && !preserveInitialSettlement) {
            throw BusinessException.badRequest("补录订单收益已锁定，不能改写首期核销金额");
        }
        /* Read the database clock immediately before the order write.  This is
         * the effective boundary used by the renewal timeline, so validation
         * and lock acquisition time cannot make a manual edit appear earlier
         * than the actual persisted change.  Metadata-only edits do not create
         * a verification revision or need a clock read. */
        var editedAt = verificationEdited
            ? verificationRevisionRepository.currentDatabaseTime()
            : null;
        var updated = externalRentalOrderRepository.update(new ExternalRentalOrderRepository.UpdateRow(
            order.id(),
            nextSourcePlatform,
            blankToNull(request.externalOrderNo()),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            storeSku.skuId(),
            packageTemplate.id(),
            request.customerName().trim(),
            request.customerPhone().trim(),
            request.frameAssetId(),
            request.batteryAssetId(),
            externalRentalAmount,
            verificationAmount,
            signFeeAmount,
            depositAmount,
            nextLeaseUnit,
            nextLeaseValue,
            nextTotalPeriods,
            leaseMultiplier,
            order.autoRenewEnabled(),
            order.renewalUnit(),
            order.renewalValue(),
            order.renewalAmount(),
            order.renewalBillingMode(),
            order.renewalDailyAmount(),
            order.renewalDailyCapEnabled(),
            order.renewalGraceHours(),
            order.overdueDailyAmount(),
            request.rentStartedAt(),
            expectedReturnAt,
            blankToNull(request.remark()),
            currentAccountId()
        ));
        /* Only an active order may create or replace a settlement snapshot
         * during an edit.  A completed order with a missing legacy snapshot is
         * repaired by the dedicated backfill runner; allowing an ordinary
         * metadata edit to create it here would resurrect historical income.
         * A terminated order must never recreate income after its cleanup. */
        if (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
            && ((settlementChanged && !preserveInitialSettlement) || updated.settlementSnapshotId() == null)) {
            var batteryCostBasisChanged = !java.util.Objects.equals(order.storeSkuId(), storeSku.id())
                || !java.util.Objects.equals(order.skuId(), storeSku.skuId())
                || !java.util.Objects.equals(order.packageId(), packageTemplate.id())
                || !java.util.Objects.equals(order.frameAssetId(), request.frameAssetId())
                || !java.util.Objects.equals(order.batteryAssetId(), request.batteryAssetId())
                || !java.util.Objects.equals(order.leaseMultiplier(), leaseMultiplier)
                || !java.util.Objects.equals(order.leaseUnit(), nextLeaseUnit)
                || !java.util.Objects.equals(order.leaseValue(), nextLeaseValue);
            // A newly rebuilt snapshot must use the current verification
            // amount. The old first-period base is preserved only by the
            // verification-only path above; structural edits (including
            // structural + verification edits) are a new settlement fact and
            // must not accidentally retain the old amount.
            updated = createAndSyncSettlement(updated, !batteryCostBasisChanged, null);
        }
        if (verificationEdited) {
            verificationRevisionRepository.create(
                updated.id(),
                updated.verificationAmount(),
                editedAt,
                ExternalOrderVerificationRevisionType.ORDER_EDIT,
                preserveInitialSettlement ? null : updated.settlementSnapshotId(),
                currentAccountId()
            );
            // Rebuild only accrued renewal events whose income is still
            // pending. Locked/settled statements remain immutable.
            autoRenewalService.reconcilePendingEvents(updated.id());
            // A non-preserved verification edit replaced the initial
            // snapshot/income rows above. Rebuild any pre-existing draft
            // statement lines from the new snapshot while the month locks
            // acquired before the update are still held.
            regenerateDraftMonths(rebuiltInitialDraftMonths);
        } else if (!java.util.Objects.equals(order.rentStartedAt(), request.rentStartedAt())
            && updated.settlementSnapshotId() != null) {
            // A date-only correction changes the initial period boundary but
            // must not become a renewal-price override for later periods.
            verificationRevisionRepository.createIfMissingSnapshot(
                updated.id(),
                updated.verificationAmount(),
                updated.rentStartedAt(),
                ExternalOrderVerificationRevisionType.INITIAL,
                updated.settlementSnapshotId(),
                currentAccountId()
            );
        }
        externalRentalOrderRepository.addLog(
            updated.id(),
            updated.orderStatus(),
            updated.orderStatus(),
            ExternalOrderOperationType.EDIT,
            currentAccountId(),
            "编辑补录订单资料"
        );
        return toResponse(ensureView(updated.id()));
    }

    /**
     * Edit only historical/descriptive fields on a completed or terminated
     * supplemental order.  This deliberately bypasses the live product
     * catalog: products may be off-shelf or deleted by the time an operator
     * corrects a customer's name or a manual verification amount.  Every
     * settlement-attribution field is compared with the persisted order and
     * then written back unchanged, so this path cannot move a frozen income to
     * another store or recreate a missing terminal snapshot.
     */
    private ExternalRentalOrderResponse updateTerminalOrderMetadata(
        ExternalRentalOrder order,
        ExternalRentalOrderUpdateRequest request
    ) {
        if (!java.util.Objects.equals(order.storeSkuId(), request.storeSkuId())
            || !java.util.Objects.equals(order.packageId(), request.packageId())
            || (request.leaseMultiplier() != null
                && !java.util.Objects.equals(order.leaseMultiplier(), normalizeLeaseMultiplier(request.leaseMultiplier())))
            || !java.util.Objects.equals(order.rentStartedAt(), request.rentStartedAt())
            || (request.expectedReturnAt() != null
                && !java.util.Objects.equals(order.expectedReturnAt(), request.expectedReturnAt()))
            || !java.util.Objects.equals(order.frameAssetId(), request.frameAssetId())
            || !java.util.Objects.equals(order.batteryAssetId(), request.batteryAssetId())
            || (request.externalRentalAmount() != null
                && !sameMoney(order.externalRentalAmount(), normalizeMoney(request.externalRentalAmount(), null)))
            || (request.signFeeAmount() != null
                && !sameMoney(order.signFeeAmount(), normalizeMoney(request.signFeeAmount(), null)))
            || (request.depositAmount() != null
                && !sameMoney(order.depositAmount(), normalizeMoney(request.depositAmount(), null)))) {
            throw BusinessException.badRequest("已结束补录订单只能修改客户资料、来源平台、核销金额和备注，不能修改门店、资产、办单费或租期结构");
        }

        var nextSourcePlatform = parseSource(request.sourcePlatform());
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount());
        var verificationEdited = !sameMoney(order.verificationAmount(), verificationAmount);
        var editedAt = verificationEdited ? verificationRevisionRepository.currentDatabaseTime() : null;
        var updated = externalRentalOrderRepository.update(new ExternalRentalOrderRepository.UpdateRow(
            order.id(),
            nextSourcePlatform,
            blankToNull(request.externalOrderNo()),
            order.merchantId(),
            order.storeId(),
            order.storeSkuId(),
            order.skuId(),
            order.packageId(),
            request.customerName().trim(),
            request.customerPhone().trim(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.externalRentalAmount(),
            verificationAmount,
            order.signFeeAmount(),
            order.depositAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.leaseMultiplier(),
            order.autoRenewEnabled(),
            order.renewalUnit(),
            order.renewalValue(),
            order.renewalAmount(),
            order.renewalBillingMode(),
            order.renewalDailyAmount(),
            order.renewalDailyCapEnabled(),
            order.renewalGraceHours(),
            order.overdueDailyAmount(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            blankToNull(request.remark()),
            currentAccountId()
        ));
        if (verificationEdited) {
            verificationRevisionRepository.create(
                updated.id(),
                updated.verificationAmount(),
                editedAt,
                ExternalOrderVerificationRevisionType.ORDER_EDIT,
                null,
                currentAccountId()
            );
            /* COMPLETED orders may retain accrued, still-mutable renewal
             * periods.  They use the same exact edit-time rule as ACTIVE
             * orders; TERMINATED events were reversed during termination and
             * must never be resurrected. */
            if (updated.orderStatus() == ExternalRentalOrderStatus.COMPLETED) {
                autoRenewalService.reconcilePendingEvents(updated.id());
            }
        }
        externalRentalOrderRepository.addLog(
            updated.id(),
            updated.orderStatus(),
            updated.orderStatus(),
            ExternalOrderOperationType.EDIT,
            currentAccountId(),
            "编辑已结束补录订单资料"
        );
        return toResponse(ensureView(updated.id()));
    }

    @Transactional
    public void deleteOrder(Long id) {
        authorizationService.requirePermission("order.operate");
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());

        var draftMonths = collectDraftStatementMonths(order);
        lockDraftStatementMonths(draftMonths);
        if (settlementStatementRepository.hasLockedLinesBySourceForUpdate(SnapshotSourceType.EXTERNAL_ORDER.name(), order.id())) {
            throw BusinessException.badRequest("补录订单已进入已确认或已支付月结单，不能删除");
        }
        ensureDraftMonthsRegenerable(draftMonths);
        if (settlementStatementRepository.hasLinesBySource(SnapshotSourceType.EXTERNAL_ORDER.name(), order.id())) {
            if (order.orderStatus() != ExternalRentalOrderStatus.TERMINATED) {
                throw BusinessException.badRequest("补录订单已进入月结单，不能删除");
            }
        }
        if (settlementIncomeRepository.hasNonPendingBySourceForUpdate(IncomeSourceType.EXTERNAL_ORDER, order.id())) {
            throw BusinessException.badRequest("补录订单收益已结算或冻结，不能删除");
        }
        ensureRenewalsReversible(order.id(), "删除");

        if (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE) {
            releaseDeletedActiveAsset(order.frameAssetId(), order, "删除补录订单释放主资产");
            releaseDeletedActiveAsset(order.batteryAssetId(), order, "删除补录订单释放第二资产");
        }

        settlementIncomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_ORDER, order.id());
        reverseRenewals(order.id(), true);
        verificationRevisionRepository.deleteByOrder(order.id());
        externalRentalOrderRepository.deleteLogs(order.id());
        externalRentalOrderRepository.delete(order.id());
        settlementRepository.deleteSnapshotsBySource(SnapshotSourceType.EXTERNAL_ORDER, order.id());
        regenerateDraftMonths(draftMonths);
    }

    public ExternalRentalOrderBatchImportResponse batchImport(ExternalRentalOrderBatchImportRequest request) {
        authorizationService.requirePermission("order.operate");
        var results = new ArrayList<ExternalRentalOrderImportRowResultResponse>();
        int successCount = 0;
        for (var row : request.rows()) {
            try {
                var created = transactionTemplate.execute(status -> createOrderInternal(toCreateRequest(row)));
                if (created == null) {
                    throw BusinessException.badRequest("导入失败");
                }
                results.add(new ExternalRentalOrderImportRowResultResponse(
                    row.lineNo(),
                    true,
                    created.id(),
                    created.recordNo(),
                    "导入成功"
                ));
                successCount++;
            } catch (Exception exception) {
                results.add(new ExternalRentalOrderImportRowResultResponse(
                    row.lineNo(),
                    false,
                    null,
                    null,
                    exception.getMessage()
                ));
            }
        }
        return new ExternalRentalOrderBatchImportResponse(
            request.rows().size(),
            successCount,
            request.rows().size() - successCount,
            results
        );
    }

    private ExternalRentalOrderResponse createOrderInternal(ExternalRentalOrderCreateRequest request) {
        var storeSku = ensureStoreSku(request.storeSkuId());
        authorizationService.requireStoreAccess(storeSku.merchantId(), storeSku.storeId());
        var sku = ensureSku(storeSku.skuId());
        var packageTemplate = ensureStoreSkuPackage(storeSku, request.packageId());
        var packagePricing = storeSkuPackageAmount(storeSku.id(), request.packageId());
        var leaseMultiplier = normalizeLeaseMultiplier(request.leaseMultiplier());
        // Older store-SKU rows may have a null renewal amount; the product
        // service treats the period amount as the compatible system fallback.
        var systemRenewalAmount = packagePricing.renewalAmount() == null
            ? packagePricing.periodAmount()
            : packagePricing.renewalAmount();
        validateRequestAssets(request.frameAssetId(), request.batteryAssetId(), sku);
        var expectedReturnAt = request.expectedReturnAt() == null
            ? calculateExpectedReturnAt(request.rentStartedAt(), packageTemplate, leaseMultiplier)
            : request.expectedReturnAt();
        if (expectedReturnAt != null && expectedReturnAt.isBefore(request.rentStartedAt())) {
            throw BusinessException.badRequest("预计归还时间不能早于起租时间");
        }
        var frameAsset = request.frameAssetId() == null ? null : occupyAsset(request.frameAssetId(), storeSku, "外部补录订单绑定主资产");
        var batteryAsset = request.batteryAssetId() == null ? null : occupyAsset(request.batteryAssetId(), storeSku, "外部补录订单绑定第二资产");
        var externalRentalAmount = normalizeMoney(
            request.externalRentalAmount(),
            packagePricing.rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier))
        );
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount());
        var defaultSignFeeAmount = effectiveSignFeeAmount(packageTemplate, storeSku);
        var order = externalRentalOrderRepository.create(new ExternalRentalOrderRepository.CreateRow(
            nextRecordNo(),
            parseSource(request.sourcePlatform()),
            blankToNull(request.externalOrderNo()),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            storeSku.skuId(),
            packageTemplate.id(),
            request.customerName().trim(),
            request.customerPhone().trim(),
            frameAsset == null ? null : frameAsset.id(),
            batteryAsset == null ? null : batteryAsset.id(),
            ExternalRentalOrderStatus.ACTIVE,
            externalRentalAmount,
            verificationAmount,
            normalizeMoney(request.signFeeAmount(), defaultSignFeeAmount),
            normalizeMoney(request.depositAmount(), packagePricing.depositAmount()),
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue() * leaseMultiplier,
            packageTemplate.totalPeriods() * leaseMultiplier,
            leaseMultiplier,
            packagePricing.autoRenewEnabled(),
            packagePricing.renewalUnit() == null ? null : packagePricing.renewalUnit().name(),
            packagePricing.renewalValue(),
            Boolean.TRUE.equals(packagePricing.autoRenewEnabled()) ? systemRenewalAmount : null,
            packagePricing.renewalBillingMode().name(),
            packagePricing.renewalDailyAmount(),
            packagePricing.renewalDailyCapEnabled(),
            packagePricing.renewalGraceHours(),
            packagePricing.overdueDailyAmount(),
            request.rentStartedAt(),
            expectedReturnAt,
            blankToNull(request.remark()),
            currentAccountId(),
            currentAccountId()
        ));
        order = createAndSyncSettlement(order);
        verificationRevisionRepository.createIfMissingSnapshot(
            order.id(),
            order.verificationAmount(),
            order.rentStartedAt(),
            ExternalOrderVerificationRevisionType.INITIAL,
            order.settlementSnapshotId(),
            currentAccountId()
        );
        externalRentalOrderRepository.addLog(
            order.id(),
            null,
            ExternalRentalOrderStatus.ACTIVE,
            ExternalOrderOperationType.CREATE,
            currentAccountId(),
            defaultRemark(request.remark(), "创建外部补录订单")
        );
        return toResponse(ensureView(order.id()));
    }

    @Transactional
    public ExternalRentalOrderResponse complete(Long id, ExternalRentalOrderCompleteRequest request) {
        authorizationService.requirePermission("order.operate");
        request = request == null ? new ExternalRentalOrderCompleteRequest(null, null, null, null) : request;
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        ensureActive(order);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        /* The background job runs hourly, so an operator may complete an
         * order after its renewal boundary but before the next scan.  Accrue
         * all due periods while the order is still ACTIVE; once COMPLETED, the
         * scheduler deliberately stops touching it. */
        var completeAt = LocalDateTime.now();
        if (Boolean.TRUE.equals(order.autoRenewEnabled())
            && order.expectedReturnAt() != null
            && !order.expectedReturnAt().isAfter(completeAt)) {
            autoRenewalService.accrueDueOrder(order.id(), completeAt);
            order = externalRentalOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        }
        var returnStore = resolveReturnStore(order, request.returnStoreId());
        if (order.frameAssetId() != null) {
            returnAssetToStore(order.frameAssetId(), parseReturnStatus(request.frameResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单归还主资产"));
        }
        if (order.batteryAssetId() != null) {
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单归还第二资产"));
        }
        var finished = externalRentalOrderRepository.finish(
            id,
            ExternalRentalOrderStatus.COMPLETED,
            returnStore.id(),
            LocalDateTime.now(),
            null,
            blankToNull(request.remark()) == null ? order.remark() : request.remark().trim(),
            currentAccountId()
        );
        pricingRevisionRepository.cancelPendingByOrder(id);
        externalRentalOrderRepository.addLog(
            id,
            order.orderStatus(),
            ExternalRentalOrderStatus.COMPLETED,
            ExternalOrderOperationType.COMPLETE,
            currentAccountId(),
            defaultRemark(request.remark(), "外部补录订单正常完结")
        );
        return toResponse(ensureView(finished.id()));
    }

    @Transactional
    public ExternalRentalOrderResponse terminate(Long id, ExternalRentalOrderTerminateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        ensureActive(order);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        var returnStore = resolveReturnStore(order, request.returnStoreId());
        if (order.frameAssetId() != null) {
            returnAssetToStore(order.frameAssetId(), parseReturnStatus(request.frameResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单提前终止归还主资产"));
        }
        if (order.batteryAssetId() != null) {
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单提前终止归还第二资产"));
        }
        var finished = externalRentalOrderRepository.finish(
            id,
            ExternalRentalOrderStatus.TERMINATED,
            returnStore.id(),
            LocalDateTime.now(),
            request.terminationReason().trim(),
            blankToNull(request.remark()) == null ? order.remark() : request.remark().trim(),
            currentAccountId()
        );
        removeTerminatedSettlementData(order);
        pricingRevisionRepository.cancelPendingByOrder(id);
        externalRentalOrderRepository.addLog(
            id,
            order.orderStatus(),
            ExternalRentalOrderStatus.TERMINATED,
            ExternalOrderOperationType.TERMINATE,
            currentAccountId(),
            defaultRemark(request.remark(), "外部补录订单提前终止: " + request.terminationReason().trim())
        );
        return toResponse(ensureView(finished.id()));
    }

    private void validateRequestAssets(Long frameAssetId, Long batteryAssetId, ProductSku sku) {
        var primaryAsset = frameAssetId == null ? null : ensureAsset(frameAssetId);
        if (batteryAssetId != null) {
            ensureAsset(batteryAssetId);
        }
        var primaryAssetCoversBothSlots = primaryAsset != null && primaryAsset.assetType().isIntegratedVehicle();
        if (Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId == null) {
            throw BusinessException.badRequest("当前商品链接必须绑定主资产");
        }
        if (Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId == null && !primaryAssetCoversBothSlots) {
            throw BusinessException.badRequest("当前商品链接必须绑定第二资产");
        }
        if (!Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定主资产");
        }
        if (!Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定第二资产");
        }
        if (frameAssetId != null && frameAssetId.equals(batteryAssetId)) {
            throw BusinessException.badRequest("主资产和第二资产不能选择同一条资产");
        }
    }

    private void validateEditableAsset(
        Long assetId,
        Long currentAssetId,
        StoreSku storeSku,
        ExternalRentalOrder order
    ) {
        if (assetId == null) {
            return;
        }
        var asset = ensureAsset(assetId);
        if (assetId.equals(currentAssetId)) {
            var activeOrder = externalRentalOrderRepository.findActiveByAsset(assetId).orElse(null);
            if (asset.status() != AssetStatus.RENTING || activeOrder == null || !activeOrder.id().equals(order.id())) {
                throw BusinessException.badRequest("订单当前绑定资产状态异常，暂不能编辑");
            }
            if (!storeSku.merchantId().equals(asset.currentMerchantId())) {
                throw BusinessException.badRequest("暂不支持跨商户自动调拨订单资产");
            }
            return;
        }
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("所选资产不是空闲状态");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
    }

    private void validateHistoricalEditableAsset(
        Long assetId,
        Long currentAssetId,
        StoreSku storeSku
    ) {
        if (assetId == null) {
            return;
        }
        var asset = ensureAsset(assetId);
        if (assetId.equals(currentAssetId)) {
            return;
        }
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("已结束订单只能改绑当前门店的空闲资产");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
    }

    private ExternalRentalOrder createAndSyncSettlement(ExternalRentalOrder order) {
        return createAndSyncSettlement(order, false);
    }

    private ExternalRentalOrder createAndSyncSettlement(ExternalRentalOrder order, boolean preserveBatteryCost) {
        return createAndSyncSettlement(order, preserveBatteryCost, null);
    }

    private ExternalRentalOrder createAndSyncSettlement(
        ExternalRentalOrder order,
        boolean preserveBatteryCost,
        BigDecimal initialSettlementBase
    ) {
        var sku = productRepository.findSku(order.skuId())
            .orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        var packageTemplate = productRepository.findPackage(order.packageId())
            .orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        // Keep the original battery-cost basis when an existing supplemental
        // order is edited. Product-cost changes must not silently rewrite a
        // historical settlement snapshot; a new order has no snapshot and is
        // calculated from the current SKU configuration.
        var previousSnapshot = order.settlementSnapshotId() == null
            ? null
            : settlementRepository.findSnapshot(order.settlementSnapshotId()).orElse(null);
        var batteryCostAmount = preserveBatteryCost && previousSnapshot != null
            && previousSnapshot.sourceType() == SnapshotSourceType.EXTERNAL_ORDER
            && order.id().equals(previousSnapshot.sourceId())
            ? previousSnapshot.batteryCostAmount()
            : BatteryCostCalculator.calculate(
                sku.batteryCostDailyAmount(),
                sku.batteryCostMonthlyAmount(),
                packageTemplate.leaseUnit(),
                packageTemplate.leaseValue(),
                order.leaseMultiplier()
            );
        var snapshot = settlementService.createOrderSnapshot(new SnapshotCreateRequest(
            "EXTERNAL_ORDER",
            order.id(),
            order.storeSkuId(),
            order.frameAssetId(),
            order.batteryAssetId(),
            initialSettlementBase == null ? order.verificationAmount() : initialSettlementBase,
            order.sourcePlatform().name(),
            order.signFeeAmount(),
            batteryCostAmount
        ));
        var updated = externalRentalOrderRepository.updateSettlementSnapshot(order.id(), snapshot.id());
        settlementIncomeService.syncExternalOrder(updated);
        return updated;
    }

    private void removeTerminatedSettlementData(ExternalRentalOrder order) {
        var orderId = order.id();
        var draftMonths = collectDraftStatementMonths(order);
        lockDraftStatementMonths(draftMonths);
        if (settlementStatementRepository.hasLockedLinesBySourceForUpdate(SnapshotSourceType.EXTERNAL_ORDER.name(), orderId)) {
            throw BusinessException.badRequest("补录订单已进入已确认或已支付月结单，不能终止");
        }
        if (settlementIncomeRepository.hasNonPendingBySourceForUpdate(IncomeSourceType.EXTERNAL_ORDER, orderId)) {
            throw BusinessException.badRequest("补录订单首期收益已结算或冻结，不能终止");
        }
        ensureDraftMonthsRegenerable(draftMonths);
        ensureRenewalsReversible(orderId, "终止");
        settlementIncomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_ORDER, orderId);
        reverseRenewals(orderId, false);
        regenerateDraftMonths(draftMonths);
    }

    private void ensureRenewalsReversible(Long orderId, String action) {
        if (renewalRepository.hasLockedStatementLinesByExternalOrderForUpdate(orderId)) {
            throw BusinessException.badRequest("补录订单续租收益已进入已确认或已支付月结单，不能" + action);
        }
        if (renewalRepository.hasNonPendingIncomeByExternalOrderForUpdate(orderId)) {
            throw BusinessException.badRequest("补录订单续租收益已结算或冻结，不能" + action);
        }
    }

    private void reverseRenewals(Long orderId, boolean deleteEvents) {
        renewalRepository.reversePendingByExternalOrder(orderId);
        if (deleteEvents) {
            renewalRepository.deleteByExternalOrder(orderId);
        }
    }

    /**
     * Return the draft-statement months that currently contain a derived line
     * for this order.  Do not add the order creation month or every accrued
     * event's period month here: those are only candidate months and may
     * belong to an unrelated statement.  Including them made delete/terminate
     * reject an order when another order had already locked that month.
     *
     * The caller holds the order row lock before invoking this method.  The
     * public month generator acquires all external-order locks in id order,
     * so a newly-created line cannot race this operation while the order is
     * being deleted or terminated.
     */
    private java.util.Set<String> collectDraftStatementMonths(ExternalRentalOrder order) {
        var orderId = order.id();
        var months = new java.util.LinkedHashSet<String>();
        months.addAll(settlementStatementRepository.listDraftStatementMonthsBySource(
            SnapshotSourceType.EXTERNAL_ORDER.name(), orderId));
        months.addAll(renewalRepository.listDraftStatementMonthsByExternalOrder(orderId));
        return months;
    }

    /** Rebuild only affected unlocked months; never delete another order's
     * draft lines as a side effect of terminating/deleting this order. */
    private void regenerateDraftMonths(java.util.Set<String> months) {
        for (var month : months) {
            settlementStatementService.regenerateUnlockedMonthAlreadyLocked(month);
        }
    }

    private void lockDraftStatementMonths(java.util.Set<String> months) {
        months.stream()
            .sorted()
            .forEach(settlementStatementRepository::lockStatementsByMonthForUpdate);
    }

    private void ensureDraftMonthsRegenerable(java.util.Set<String> months) {
        for (var month : months) {
            if (settlementStatementRepository.hasLockedStatements(month)) {
                throw BusinessException.badRequest(
                    "补录订单关联月份含已锁定月结单，不能自动清理草稿，请先人工核对 " + month
                );
            }
        }
    }

    public int backfillMissingSettlements() {
        var completed = 0;
        for (var id : externalRentalOrderRepository.listIdsWithoutSettlementSnapshot()) {
            try {
                var updated = transactionTemplate.execute(status -> {
                    var order = externalRentalOrderRepository.findByIdForUpdate(id).orElse(null);
                    /* A terminated order intentionally has no live income
                     * after cleanup.  Do not let the generic historical
                     * backfill recreate a snapshot (and therefore income)
                     * for a legacy terminated row that is missing its link. */
                    if (order == null
                        || order.settlementSnapshotId() != null
                        || order.orderStatus() == ExternalRentalOrderStatus.TERMINATED) {
                        return false;
                    }
                    var settled = createAndSyncSettlement(order);
                    verificationRevisionRepository.createIfMissingSnapshot(
                        settled.id(),
                        settled.verificationAmount(),
                        settled.rentStartedAt(),
                        ExternalOrderVerificationRevisionType.INITIAL,
                        settled.settlementSnapshotId(),
                        settled.createdByAccountId()
                    );
                    return true;
                });
                if (Boolean.TRUE.equals(updated)) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                log.warn("补录订单 {} 自动补建分润失败: {}", id, exception.getMessage());
            }
        }
        return completed;
    }

    private void releaseEditedAsset(Long currentAssetId, Long nextAssetId, String remark) {
        if (currentAssetId == null || currentAssetId.equals(nextAssetId)) {
            return;
        }
        var asset = ensureAsset(currentAssetId);
        if (asset.status() != AssetStatus.IDLE) {
            assetRepository.updateStatus(asset.id(), AssetStatus.IDLE, LocalDateTime.now());
            assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.IDLE, currentAccountId(), remark);
        }
    }

    private void releaseDeletedActiveAsset(Long assetId, ExternalRentalOrder order, String remark) {
        if (assetId == null) {
            return;
        }
        if (externalRentalOrderRepository.existsOtherActiveByAsset(assetId, order.id())) {
            throw BusinessException.badRequest("订单资产仍被其他补录订单占用，不能删除");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("订单资产已被正式订单占用，不能删除");
        }
        var asset = ensureAsset(assetId);
        if (asset.status() == AssetStatus.RENTING) {
            assetRepository.updateStatus(asset.id(), AssetStatus.IDLE, LocalDateTime.now());
            assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.IDLE, currentAccountId(), remark);
        }
    }

    private void occupyEditedAsset(Long nextAssetId, Long currentAssetId, String remark) {
        if (nextAssetId == null || nextAssetId.equals(currentAssetId)) {
            return;
        }
        var asset = ensureAsset(nextAssetId);
        assetRepository.updateStatus(asset.id(), AssetStatus.RENTING, LocalDateTime.now());
        assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.RENTING, currentAccountId(), remark);
    }

    private void transferRetainedEditedAsset(
        Long nextAssetId,
        Long currentAssetId,
        StoreSku storeSku,
        String remark
    ) {
        if (nextAssetId == null || !nextAssetId.equals(currentAssetId)) {
            return;
        }
        var asset = ensureAsset(nextAssetId);
        if (storeSku.storeId().equals(asset.currentStoreId())) {
            return;
        }
        assetRepository.transferStore(asset.id(), storeSku.merchantId(), storeSku.storeId());
        assetRepository.insertLocationHistory(
            asset.id(),
            asset.currentMerchantId(),
            asset.currentStoreId(),
            storeSku.merchantId(),
            storeSku.storeId(),
            remark
        );
    }

    private AssetItem occupyAsset(Long assetId, StoreSku storeSku, String remark) {
        var asset = ensureAsset(assetId);
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("所选资产不是空闲状态");
        }
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
        assetRepository.updateStatus(assetId, AssetStatus.RENTING, LocalDateTime.now());
        assetRepository.insertStatusLog(assetId, asset.status(), AssetStatus.RENTING, currentAccountId(), remark);
        return assetRepository.findById(assetId).orElseThrow();
    }

    private void returnAssetToStore(Long assetId, AssetStatus nextStatus, Long returnMerchantId, Long returnStoreId, String remark) {
        var asset = ensureAsset(assetId);
        if (asset.status() != nextStatus) {
            assetRepository.updateStatus(assetId, nextStatus, LocalDateTime.now());
            assetRepository.insertStatusLog(assetId, asset.status(), nextStatus, currentAccountId(), remark);
        }
        if (!returnMerchantId.equals(asset.currentMerchantId()) || !returnStoreId.equals(asset.currentStoreId())) {
            assetRepository.transferStore(assetId, returnMerchantId, returnStoreId);
            assetRepository.insertLocationHistory(
                assetId,
                asset.currentMerchantId(),
                asset.currentStoreId(),
                returnMerchantId,
                returnStoreId,
                "外部补录订单结束自动调拨"
            );
        }
    }

    private MerchantStore resolveReturnStore(ExternalRentalOrder order, Long returnStoreId) {
        var resolvedStoreId = returnStoreId == null ? order.storeId() : returnStoreId;
        var store = ensureStore(resolvedStoreId);
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("归还门店已停用");
        }
        if (!store.merchantId().equals(order.merchantId())) {
            throw BusinessException.badRequest("暂不支持跨商户归还");
        }
        if (!store.id().equals(order.storeId())) {
            var sku = productRepository.findSku(order.skuId()).orElseThrow(() -> BusinessException.badRequest("订单商品链接不存在"));
            if (!Boolean.TRUE.equals(sku.supportCrossStoreReturn())) {
                throw BusinessException.badRequest("当前商品链接不支持跨门店归还");
            }
        }
        return store;
    }

    private AssetStatus parseReturnStatus(String value, AssetStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var status = AssetStatus.valueOf(value);
            if (status != AssetStatus.IDLE && status != AssetStatus.PENDING_REPAIR && status != AssetStatus.EXCEPTION) {
                throw BusinessException.badRequest("归还资产状态只能为空闲、待检修或异常");
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的资产归还状态");
        }
    }

    private ExternalRentalOrderResponse toResponse(ExternalRentalOrderRepository.ExternalRentalOrderView view) {
        var order = view.order();
        var snapshot = order.settlementSnapshotId() == null
            ? null
            : settlementRepository.findSnapshot(order.settlementSnapshotId()).orElse(null);
        return new ExternalRentalOrderResponse(
            order.id(),
            order.recordNo(),
            order.sourcePlatform().name(),
            order.externalOrderNo(),
            order.merchantId(),
            view.merchantName(),
            order.storeId(),
            view.storeName(),
            order.storeSkuId(),
            view.storeSkuDisplayName(),
            order.skuId(),
            view.skuName(),
            order.packageId(),
            view.packageName(),
            order.customerName(),
            order.customerPhone(),
            order.frameAssetId(),
            view.frameAssetSerialNo(),
            order.batteryAssetId(),
            view.batteryAssetSerialNo(),
            order.orderStatus().name(),
            order.externalRentalAmount(),
            order.verificationAmount(),
            order.settlementSnapshotId(),
            snapshot == null ? null : snapshot.snapshotNo(),
            snapshot == null ? null : snapshot.settlementBaseAmount(),
            snapshot == null ? null : snapshot.rentalAmount(),
            snapshot == null ? null : snapshot.channelFeeAmount(),
            snapshot == null ? null : snapshot.platformFeeAmount(),
            snapshot == null ? null : snapshot.storeOperationAmount(),
            snapshot == null ? null : snapshot.maintenanceFundAmount(),
            storeOrderFeeAmount(order, snapshot),
            storeRevenueAmount(order, snapshot),
            snapshot == null ? null : snapshot.channelReferralAmount(),
            snapshot == null ? null : snapshot.investorShareAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.leaseMultiplier(),
            order.autoRenewEnabled(),
            order.renewalUnit(),
            order.renewalValue(),
            order.renewalAmount(),
            order.renewalBillingMode(),
            order.renewalDailyAmount(),
            order.renewalDailyCapEnabled(),
            order.renewalGraceHours(),
            order.overdueDailyAmount(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            order.finishedAt(),
            order.returnStoreId(),
            view.returnStoreName(),
            order.terminationReason(),
            order.remark(),
            order.createdByAccountId(),
            order.updatedByAccountId(),
            order.createdAt(),
            order.updatedAt(),
            externalRentalOrderRepository.listLogs(order.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private ExternalOrderRenewalResponse toRenewalResponse(ExternalOrderRenewalRepository.RenewalView view) {
        var event = view.event();
        return new ExternalOrderRenewalResponse(
            event.id(),
            event.externalOrderId(),
            event.eventNo(),
            view.externalOrderRecordNo(),
            view.merchantId(),
            view.storeId(),
            event.periodNo(),
            event.periodStartAt(),
            event.periodEndAt(),
            event.renewalAmount(),
            event.batteryCostAmount(),
            event.eventStatus(),
            view.includedInMerchantStatement(),
            event.periodStartAt()
        );
    }

    private ExternalRentalOrderResponse toResponse(ExternalRentalOrder order) {
        return toResponse(ensureView(order.id()));
    }

    /** Store-facing fee projection shared by order lists and details. */
    private BigDecimal storeOrderFeeAmount(
        ExternalRentalOrder order,
        com.xniu.rental.settlement.model.SettlementRuleSnapshot snapshot
    ) {
        if (snapshot == null) {
            return ProfitSharingCalculator.calculateOrderFee(order.signFeeAmount()).merchantNetAmount();
        }
        if (snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2) {
            var snapshotFee = money(snapshot.merchantOrderFeeAmount());
            var orderFee = money(order.signFeeAmount());
            /* Before the fee was frozen into V2 snapshots, old supplemental
             * snapshots either stored zero or the gross fee.  The order row is
             * the authoritative actual fee for those records; never display
             * zero or 100% as the store entitlement. */
            if (snapshotFee.signum() <= 0 || snapshotFee.compareTo(orderFee) == 0) {
                return ProfitSharingCalculator.calculateOrderFee(orderFee).merchantNetAmount();
            }
            return snapshotFee;
        }
        return ProfitSharingCalculator.calculateOrderFee(order.signFeeAmount()).merchantNetAmount();
    }

    private BigDecimal storeRevenueAmount(
        ExternalRentalOrder order,
        com.xniu.rental.settlement.model.SettlementRuleSnapshot snapshot
    ) {
        var operation = snapshot == null ? BigDecimal.ZERO : snapshot.storeOperationAmount();
        var maintenance = snapshot == null ? BigDecimal.ZERO : snapshot.maintenanceFundAmount();
        if (snapshot != null && snapshot.calculationVersion() != SettlementCalculationVersion.PROFIT_V2) {
            operation = snapshot.merchantRentShareAmount();
            maintenance = BigDecimal.ZERO;
        }
        return money(operation)
            .add(money(maintenance))
            .add(storeOrderFeeAmount(order, snapshot))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public List<com.xniu.rental.asset.dto.AssetRentalRecordResponse> listAssetRentalRecords(Long assetId) {
        return externalRentalOrderRepository.listByAsset(assetId).stream()
            .map(this::toAssetRentalRecord)
            .toList();
    }

    private com.xniu.rental.asset.dto.AssetRentalRecordResponse toAssetRentalRecord(ExternalRentalOrderRepository.ExternalRentalOrderView view) {
        var order = view.order();
        return new com.xniu.rental.asset.dto.AssetRentalRecordResponse(
            "EXTERNAL",
            order.id(),
            order.recordNo(),
            order.sourcePlatform().name(),
            order.externalOrderNo(),
            null,
            order.storeId(),
            order.customerName(),
            order.customerPhone(),
            order.orderStatus().name(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.externalRentalAmount(),
            order.verificationAmount(),
            order.signFeeAmount(),
            order.verificationAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            order.finishedAt(),
            order.createdAt(),
            List.of()
        );
    }

    private ExternalRentalOrderLogResponse toLogResponse(com.xniu.rental.externalorder.model.ExternalRentalOrderLog log) {
        return new ExternalRentalOrderLogResponse(
            log.id(),
            log.externalOrderId(),
            log.fromStatus() == null ? null : log.fromStatus().name(),
            log.toStatus().name(),
            log.operationType().name(),
            log.operatorAccountId(),
            log.remark(),
            log.createdAt()
        );
    }

    private ExternalRentalOrder ensureOrder(Long id) {
        return externalRentalOrderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
    }

    private ExternalRentalOrderRepository.ExternalRentalOrderView ensureView(Long id) {
        return externalRentalOrderRepository.findViewById(id).orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
    }

    private void ensureActive(ExternalRentalOrder order) {
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单才可以结束");
        }
    }

    private StoreSku ensureStoreSku(Long id) {
        var storeSku = productRepository.findStoreSku(id).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var merchant = merchantRepository.findById(storeSku.merchantId()).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        var store = storeRepository.findById(storeSku.storeId()).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        if (!store.merchantId().equals(storeSku.merchantId())) {
            throw BusinessException.badRequest("门店商品商户关系异常");
        }
        return storeSku;
    }

    private ProductSku ensureSku(Long id) {
        var sku = productRepository.findSku(id).orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        if (sku.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("商品链接已停用");
        }
        return sku;
    }

    private ProductPackage ensureStoreSkuPackage(StoreSku storeSku, Long packageId) {
        var configured = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(packageId))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("当前门店商品未配置该 SKU"));
        if (configured.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("门店 SKU 已停用");
        }
        var packageTemplate = productRepository.findPackage(configured.packageId()).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        if (packageTemplate.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("SKU 已停用");
        }
        if (!packageTemplate.skuId().equals(storeSku.skuId())) {
            throw BusinessException.badRequest("SKU 与门店商品不匹配");
        }
        return packageTemplate;
    }

    private StoreSkuPackage storeSkuPackageAmount(Long storeSkuId, Long packageId) {
        return productRepository.listStoreSkuPackages(storeSkuId).stream()
            .filter(item -> item.packageId().equals(packageId) && item.status() == ProductStatus.ENABLED)
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("当前门店商品未配置该 SKU 价格"));
    }

    private BigDecimal effectiveSignFeeAmount(ProductPackage packageTemplate, StoreSku storeSku) {
        return packageTemplate.signFeeAmount() == null ? storeSku.signFeeAmount() : packageTemplate.signFeeAmount();
    }

    private ExternalRentalOrderCreateRequest toCreateRequest(ExternalRentalOrderImportRowRequest row) {
        return new ExternalRentalOrderCreateRequest(
            row.sourcePlatform(),
            row.externalOrderNo(),
            row.storeSkuId(),
            row.packageId(),
            row.leaseMultiplier(),
            row.customerName(),
            row.customerPhone(),
            row.rentStartedAt(),
            row.expectedReturnAt(),
            row.frameAssetId(),
            row.batteryAssetId(),
            row.externalRentalAmount(),
            row.verificationAmount(),
            row.signFeeAmount(),
            row.depositAmount(),
            row.remark()
        );
    }

    private MerchantStore ensureStore(Long id) {
        return storeRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
    }

    private AssetItem ensureAsset(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private ExternalRentalOrderStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExternalRentalOrderStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的补录订单状态");
        }
    }

    private ExternalOrderSourcePlatform parseSourceNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseSource(value);
    }

    private ExternalOrderSourcePlatform parseSource(String value) {
        try {
            return ExternalOrderSourcePlatform.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的订单来源平台");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value, BigDecimal fallback) {
        var amount = value == null ? fallback : value;
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (amount.signum() < 0) {
            throw BusinessException.badRequest("金额不能小于 0");
        }
        return amount;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeVerificationAmount(BigDecimal value) {
        if (value == null) {
            throw BusinessException.badRequest("请输入实际核销金额");
        }
        var amount = normalizeMoney(value, null);
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private boolean hasLockedInitialSettlement(Long externalOrderId) {
        // Keep the global lock order (statement rows before income rows), the
        // same order used by month-end status transitions and termination.
        var statementLocked = settlementStatementRepository.hasLockedLinesBySourceForUpdate(
            SnapshotSourceType.EXTERNAL_ORDER.name(), externalOrderId
        );
        var incomeLocked = settlementIncomeRepository.hasNonPendingBySourceForUpdate(
            IncomeSourceType.EXTERNAL_ORDER, externalOrderId
        );
        return incomeLocked || statementLocked;
    }

    private LocalDateTime calculateExpectedReturnAt(LocalDateTime startedAt, ProductPackage productPackage, Integer leaseMultiplier) {
        if (startedAt == null) {
            return null;
        }
        var multiplier = normalizeLeaseMultiplier(leaseMultiplier);
        var leaseValue = (long) productPackage.leaseValue() * multiplier;
        if ("MONTH".equals(productPackage.leaseUnit().name())) {
            return startedAt.plusDays(leaseValue * 30L);
        }
        return startedAt.plusDays(leaseValue);
    }

    private Integer normalizeLeaseMultiplier(Integer value) {
        var multiplier = value == null ? 1 : value;
        if (multiplier < 1 || multiplier > 120) {
            throw BusinessException.badRequest("租期倍数必须在 1 到 120 之间");
        }
        return multiplier;
    }

    private String defaultRemark(String remark, String fallback) {
        return remark == null || remark.isBlank() ? fallback : remark.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String nextRecordNo() {
        return "EORD-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
