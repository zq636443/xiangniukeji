package com.xniu.rental.product.service;

import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.model.Merchant;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.product.dto.CategoryRequest;
import com.xniu.rental.product.dto.CategoryResponse;
import com.xniu.rental.product.dto.PackageRequest;
import com.xniu.rental.product.dto.PackageResponse;
import com.xniu.rental.product.dto.SkuRequest;
import com.xniu.rental.product.dto.SkuResponse;
import com.xniu.rental.product.dto.StoreSkuBatchPublishRequest;
import com.xniu.rental.product.dto.StoreSkuPackageRequest;
import com.xniu.rental.product.dto.StoreSkuPackageResponse;
import com.xniu.rental.product.dto.StoreSkuRequest;
import com.xniu.rental.product.dto.StoreSkuResponse;
import com.xniu.rental.product.model.BillDayMode;
import com.xniu.rental.product.model.LeaseUnit;
import com.xniu.rental.product.model.ProductCategory;
import com.xniu.rental.product.model.ProductPackage;
import com.xniu.rental.product.model.ProductSku;
import com.xniu.rental.product.model.ProductStatus;
import com.xniu.rental.product.model.SignFeePayer;
import com.xniu.rental.product.model.SkuType;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;

    public ProductService(
        ProductRepository productRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService
    ) {
        this.productRepository = productRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
    }

    public List<CategoryResponse> listCategories() {
        authorizationService.requirePermission("product.read");
        return productRepository.listCategories().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        authorizationService.requirePermission("product.write");
        return toResponse(productRepository.createCategory(nextCode("C"), request.categoryName(), request.sortOrder()));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        authorizationService.requirePermission("product.write");
        ensureCategory(id);
        return toResponse(productRepository.updateCategory(id, request.categoryName(), request.sortOrder()));
    }

    @Transactional
    public void deleteCategory(Long id) {
        authorizationService.requirePermission("product.write");
        ensureCategory(id);
        if (productRepository.countSkusByCategory(id) > 0) {
            throw BusinessException.badRequest("分类仍包含商品链接，请先处理关联链接");
        }
        productRepository.deleteCategory(id);
    }

    public List<SkuResponse> listSkus(Long categoryId) {
        authorizationService.requirePermission("product.read");
        return productRepository.listSkus(categoryId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SkuResponse createSku(SkuRequest request) {
        authorizationService.requirePermission("product.write");
        ensureCategory(request.categoryId());
        var sku = productRepository.createSku(
            nextCode("LINK"),
            request.categoryId(),
            request.skuName(),
            parseSkuType(request.skuType()),
            request.description(),
            request.needFrameAsset(),
            request.needBatteryAsset(),
            request.supportCrossStoreReturn()
        );
        return toResponse(sku);
    }

    @Transactional
    public SkuResponse updateSku(Long id, SkuRequest request) {
        authorizationService.requirePermission("product.write");
        var existing = ensureSku(id);
        ensureCategory(request.categoryId());
        var nextType = parseSkuType(request.skuType());
        if (existing.skuType() != nextType && productRepository.countStoreSkusBySku(id) > 0) {
            throw BusinessException.badRequest("商品链接已配置门店上架，不能变更链接类型");
        }
        return toResponse(productRepository.updateSku(
            id,
            request.categoryId(),
            request.skuName(),
            nextType,
            request.description(),
            request.needFrameAsset(),
            request.needBatteryAsset(),
            request.supportCrossStoreReturn()
        ));
    }

    @Transactional
    public void deleteSku(Long id) {
        authorizationService.requirePermission("product.write");
        ensureSku(id);
        var blockers = new ArrayList<String>();
        addBlocker(blockers, "SKU", productRepository.countPackagesBySku(id));
        addBlocker(blockers, "门店商品", productRepository.countStoreSkusBySku(id));
        addBlocker(blockers, "分润规则", productRepository.countSettlementRulesBySku(id));
        if (!blockers.isEmpty()) {
            throw BusinessException.badRequest("商品链接仍存在关联数据，暂不可删除：" + String.join("、", blockers));
        }
        productRepository.deleteSku(id);
    }

    public List<PackageResponse> listPackages(Long skuId) {
        authorizationService.requirePermission("product.read");
        return productRepository.listPackages(skuId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PackageResponse createPackage(PackageRequest request) {
        authorizationService.requirePermission("product.write");
        validatePackage(request);
        ensureSku(request.skuId());
        return toResponse(productRepository.createPackage(
            nextCode("SKU"),
            request.skuId(),
            request.packageName(),
            normalizeMoney(request.priceAmount()),
            normalizeNullableMoney(request.signFeeAmount()),
            parseLeaseUnit(request.leaseUnit()),
            request.leaseValue(),
            request.totalPeriods(),
            parseBillDayMode(request.billDayMode()),
            request.billDay()
        ));
    }

    @Transactional
    public PackageResponse updatePackage(Long id, PackageRequest request) {
        authorizationService.requirePermission("product.write");
        validatePackage(request);
        var existing = ensurePackage(id);
        if (!existing.skuId().equals(request.skuId())) {
            throw BusinessException.badRequest("SKU 所属商品链接不可变更");
        }
        return toResponse(productRepository.updatePackage(
            id,
            request.packageName(),
            normalizeMoney(request.priceAmount()),
            normalizeNullableMoney(request.signFeeAmount()),
            parseLeaseUnit(request.leaseUnit()),
            request.leaseValue(),
            request.totalPeriods(),
            parseBillDayMode(request.billDayMode()),
            request.billDay()
        ));
    }

    @Transactional
    public void deletePackage(Long id) {
        authorizationService.requirePermission("product.write");
        ensurePackage(id);
        var blockers = new ArrayList<String>();
        addBlocker(blockers, "门店上架配置", productRepository.countStoreSkuPackagesByPackage(id));
        addBlocker(blockers, "租赁订单", productRepository.countOrdersByPackage(id));
        addBlocker(blockers, "补录订单", productRepository.countExternalOrdersByPackage(id));
        addBlocker(blockers, "核销记录", productRepository.countVouchersByPackage(id));
        if (!blockers.isEmpty()) {
            throw BusinessException.badRequest("SKU 仍存在关联数据，暂不可删除：" + String.join("、", blockers));
        }
        productRepository.deletePackage(id);
    }

    public List<StoreSkuResponse> listStoreSkus(Long storeId, Long skuId, String status) {
        authorizationService.requirePermission("product.read");
        return productRepository.listStoreSkus(storeId, skuId, parseStoreSkuStatus(status)).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<StoreSkuResponse> listMerchantStoreSkus(Long storeId) {
        authorizationService.requirePermission("order.read");
        var store = ensureEnabledStore(storeId);
        ensureEnabledMerchant(store.merchantId());
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return productRepository.listStoreSkus(storeId, null, StoreSkuStatus.ON_SHELF).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public StoreSkuResponse publishStoreSku(StoreSkuRequest request) {
        authorizationService.requirePermission("product.write");
        validateStoreSkuRequest(request);
        var existing = productRepository.findStoreSkuByStoreAndSku(request.storeId(), request.skuId());
        if (existing.isPresent()) {
            if (existing.get().status() != StoreSkuStatus.ARCHIVED) {
                throw BusinessException.badRequest("该门店已配置此商品链接，请在门店商品列表中编辑或重新上架");
            }
            var restored = productRepository.updateStoreSku(
                existing.get().id(),
                parseSkuType(request.saleMode()),
                request.displayName(),
                normalizeMoney(request.signFeeAmount()),
                parseSignFeePayer(request.signFeePayer())
            );
            productRepository.replaceStoreSkuPackages(restored.id(), toRows(request.packages()));
            return toResponse(productRepository.updateStoreSkuStatus(restored.id(), StoreSkuStatus.ON_SHELF));
        }
        var storeSku = productRepository.createStoreSku(
            nextCode("SSKU"),
            request.merchantId(),
            request.storeId(),
            request.skuId(),
            parseSkuType(request.saleMode()),
            request.displayName(),
            normalizeMoney(request.signFeeAmount()),
            parseSignFeePayer(request.signFeePayer())
        );
        productRepository.replaceStoreSkuPackages(storeSku.id(), toRows(request.packages()));
        return toResponse(storeSku);
    }

    @Transactional
    public StoreSkuResponse updateStoreSku(Long id, StoreSkuRequest request) {
        authorizationService.requirePermission("product.write");
        var existing = ensureStoreSku(id);
        if (!existing.merchantId().equals(request.merchantId())
            || !existing.storeId().equals(request.storeId())
            || !existing.skuId().equals(request.skuId())) {
            throw BusinessException.badRequest("门店商品的商户、门店和商品链接不可变更");
        }
        validateStoreSkuRequest(request);
        var updated = productRepository.updateStoreSku(
            id,
            parseSkuType(request.saleMode()),
            request.displayName(),
            normalizeMoney(request.signFeeAmount()),
            parseSignFeePayer(request.signFeePayer())
        );
        productRepository.replaceStoreSkuPackages(id, toRows(request.packages()));
        return toResponse(updated);
    }

    @Transactional
    public List<StoreSkuResponse> batchPublish(StoreSkuBatchPublishRequest request) {
        authorizationService.requirePermission("product.write");
        var sku = ensureEnabledSku(request.skuId());
        validatePackagePrices(request.skuId(), request.packages());
        var storeIds = request.storeIds().stream().distinct().toList();
        if (storeIds.size() != request.storeIds().size()) {
            throw BusinessException.badRequest("批量上架门店不能重复");
        }
        var duplicatedStores = storeIds.stream()
            .filter(storeId -> productRepository.findStoreSkuByStoreAndSku(storeId, request.skuId())
                .filter(item -> item.status() != StoreSkuStatus.ARCHIVED)
                .isPresent())
            .map(storeId -> storeRepository.findById(storeId).map(MerchantStore::storeName).orElse("门店#" + storeId))
            .toList();
        if (!duplicatedStores.isEmpty()) {
            throw BusinessException.badRequest("以下门店已配置此商品链接，请单独编辑或重新上架：" + String.join("、", duplicatedStores));
        }
        return storeIds.stream().map(storeId -> {
            var store = ensureEnabledStore(storeId);
            ensureEnabledMerchant(store.merchantId());
            var publishRequest = new StoreSkuRequest(
                store.merchantId(),
                store.id(),
                request.skuId(),
                request.displayName() == null || request.displayName().isBlank() ? sku.skuName() : request.displayName(),
                request.saleMode() == null || request.saleMode().isBlank() ? sku.skuType().name() : request.saleMode(),
                request.signFeeAmount() == null ? BigDecimal.ZERO : request.signFeeAmount(),
                request.signFeePayer() == null || request.signFeePayer().isBlank() ? SignFeePayer.USER.name() : request.signFeePayer(),
                request.packages()
            );
            return publishStoreSku(publishRequest);
        }).toList();
    }

    @Transactional
    public StoreSkuResponse updateStoreSkuStatus(Long id, StoreSkuStatus status) {
        authorizationService.requirePermission("product.write");
        var storeSku = ensureStoreSku(id);
        if (status == StoreSkuStatus.ON_SHELF) {
            ensureStoreSkuCanBeOnShelf(storeSku);
        }
        return toResponse(productRepository.updateStoreSkuStatus(id, status));
    }

    @Transactional
    public void deleteStoreSku(Long id) {
        authorizationService.requirePermission("product.write");
        var storeSku = ensureStoreSku(id);
        if (storeSku.status() != StoreSkuStatus.OFF_SHELF) {
            throw BusinessException.badRequest("请先下架门店商品，再执行删除");
        }
        var blockers = new ArrayList<String>();
        addBlocker(blockers, "租赁订单", productRepository.countOrdersByStoreSku(id));
        addBlocker(blockers, "补录订单", productRepository.countExternalOrdersByStoreSku(id));
        addBlocker(blockers, "核销记录", productRepository.countVouchersByStoreSku(id));
        addBlocker(blockers, "分润快照", productRepository.countSettlementSnapshotsByStoreSku(id));
        addBlocker(blockers, "分润规则", productRepository.countSettlementRulesByStoreSku(id));
        addBlocker(blockers, "逾期记录", productRepository.countOverdueCasesByStoreSku(id));
        if (!blockers.isEmpty()) {
            productRepository.archiveStoreSku(id);
            return;
        }
        productRepository.deleteStoreSkuPackages(id);
        productRepository.deleteStoreSku(id);
    }

    public List<StoreSkuResponse> listUserStoreProducts(String storeCode) {
        var stores = storeRepository.list(null, storeCode);
        var store = stores.stream()
            .filter(item -> item.storeCode().equals(storeCode))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        ensureEnabledMerchant(store.merchantId());
        return productRepository.listStoreSkus(store.id(), null, StoreSkuStatus.ON_SHELF).stream()
            .map(this::toResponse)
            .toList();
    }

    public StoreSkuResponse getUserStoreProduct(Long storeSkuId) {
        var storeSku = ensureStoreSku(storeSkuId);
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("商品未上架");
        }
        ensureStoreSkuCanBeOnShelf(storeSku);
        return toResponse(storeSku);
    }

    private void validateStoreSkuRequest(StoreSkuRequest request) {
        var merchant = ensureEnabledMerchant(request.merchantId());
        var store = ensureEnabledStore(request.storeId());
        if (!store.merchantId().equals(merchant.id())) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
        var sku = ensureEnabledSku(request.skuId());
        if (sku.skuType() != parseSkuType(request.saleMode())) {
            throw BusinessException.badRequest("门店商品类型必须与商品链接类型一致");
        }
        validatePackagePrices(request.skuId(), request.packages());
    }

    private void validatePackagePrices(Long skuId, List<StoreSkuPackageRequest> packages) {
        Set<Long> packageIds = new HashSet<>();
        for (var item : packages) {
            if (!packageIds.add(item.packageId())) {
                throw BusinessException.badRequest("同一个门店商品下 SKU 不能重复");
            }
            var template = ensureEnabledPackage(item.packageId());
            if (!template.skuId().equals(skuId)) {
                throw BusinessException.badRequest("SKU 不属于所选商品链接");
            }
            if (item.periodAmount().signum() < 0 || item.depositAmount().signum() < 0) {
                throw BusinessException.badRequest("金额不能小于 0");
            }
            if (Boolean.FALSE.equals(item.autoRenewEnabled())) {
                continue;
            }
            var renewalUnit = item.renewalUnit() == null || item.renewalUnit().isBlank()
                ? template.leaseUnit()
                : parseLeaseUnit(item.renewalUnit());
            var renewalValue = item.renewalValue() == null ? defaultRenewalValue(template) : item.renewalValue();
            var renewalAmount = item.renewalAmount() == null ? item.periodAmount() : item.renewalAmount();
            var renewalBillingMode = parseRenewalBillingMode(item.renewalBillingMode());
            if (renewalUnit == null || renewalValue <= 0) {
                throw BusinessException.badRequest("自动续租周期必须大于 0");
            }
            if (renewalAmount == null || renewalAmount.signum() <= 0) {
                throw BusinessException.badRequest("开启自动续租时，续租金额必须大于 0");
            }
            validateDailyRenewalRule(
                renewalBillingMode,
                item.renewalDailyAmount(),
                item.renewalDailyCapEnabled() == null || item.renewalDailyCapEnabled(),
                item.renewalGraceHours(),
                item.overdueDailyAmount(),
                renewalUnit,
                renewalValue,
                renewalAmount
            );
        }
    }

    private void validatePackage(PackageRequest request) {
        if (request.leaseValue() <= 0 || request.totalPeriods() <= 0) {
            throw BusinessException.badRequest("租期和总期数必须大于 0");
        }
        if (request.priceAmount().signum() < 0) {
            throw BusinessException.badRequest("SKU 价格不能小于 0");
        }
        if (request.signFeeAmount() != null && request.signFeeAmount().signum() < 0) {
            throw BusinessException.badRequest("办单费不能小于 0");
        }
        if ("FIXED_DAY".equals(request.billDayMode()) && (request.billDay() == null || request.billDay() < 1 || request.billDay() > 28)) {
            throw BusinessException.badRequest("固定账单日必须在 1 到 28 之间");
        }
    }

    private List<ProductRepository.PackagePriceRow> toRows(List<StoreSkuPackageRequest> packages) {
        return packages.stream()
            .map(item -> {
                var template = ensureEnabledPackage(item.packageId());
                var autoRenewEnabled = !Boolean.FALSE.equals(item.autoRenewEnabled());
                var renewalUnit = autoRenewEnabled
                    ? (item.renewalUnit() == null || item.renewalUnit().isBlank() ? template.leaseUnit() : parseLeaseUnit(item.renewalUnit()))
                    : null;
                var renewalValue = autoRenewEnabled
                    ? (item.renewalValue() == null ? defaultRenewalValue(template) : item.renewalValue())
                    : null;
                var renewalAmount = autoRenewEnabled
                    ? normalizeMoney(item.renewalAmount() == null ? item.periodAmount() : item.renewalAmount())
                    : null;
                var renewalBillingMode = autoRenewEnabled ? parseRenewalBillingMode(item.renewalBillingMode()) : RenewalBillingMode.PERIOD;
                var renewalDailyAmount = autoRenewEnabled && renewalBillingMode == RenewalBillingMode.DAILY_CAPPED
                    ? normalizeNullableMoney(item.renewalDailyAmount())
                    : null;
                return new ProductRepository.PackagePriceRow(
                    item.packageId(),
                    normalizeMoney(template.priceAmount()),
                    normalizeMoney(item.periodAmount()),
                    normalizeMoney(item.depositAmount()),
                    autoRenewEnabled,
                    renewalUnit,
                    renewalValue,
                    renewalAmount,
                    renewalBillingMode,
                    renewalDailyAmount,
                    item.renewalDailyCapEnabled() == null || item.renewalDailyCapEnabled(),
                    normalizeGraceHours(item.renewalGraceHours()),
                    autoRenewEnabled && renewalBillingMode == RenewalBillingMode.DAILY_CAPPED
                        ? normalizeNullableMoney(item.overdueDailyAmount())
                        : null
                );
            })
            .toList();
    }

    private int defaultRenewalValue(ProductPackage template) {
        return Math.max(1, template.leaseValue() / Math.max(template.totalPeriods(), 1));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalizeNullableMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private RenewalBillingMode parseRenewalBillingMode(String value) {
        if (value == null || value.isBlank()) {
            return RenewalBillingMode.PERIOD;
        }
        try {
            return RenewalBillingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的续租计费模式");
        }
    }

    private void validateDailyRenewalRule(
        RenewalBillingMode mode,
        BigDecimal dailyAmount,
        Boolean capEnabled,
        Integer graceHours,
        BigDecimal overdueDailyAmount,
        LeaseUnit renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount
    ) {
        if (mode == RenewalBillingMode.DAILY_CAPPED && (dailyAmount == null || dailyAmount.signum() <= 0)) {
            throw BusinessException.badRequest("按日续租价格必须大于 0");
        }
        if (overdueDailyAmount != null && overdueDailyAmount.signum() <= 0) {
            throw BusinessException.badRequest("逾期日占用费必须大于 0");
        }
        if (mode == RenewalBillingMode.DAILY_CAPPED && Boolean.TRUE.equals(capEnabled)) {
            var periodDays = renewalUnit == LeaseUnit.MONTH ? renewalValue * 30 : renewalValue;
            if (dailyAmount.multiply(BigDecimal.valueOf(periodDays)).compareTo(renewalAmount) < 0) {
                throw BusinessException.badRequest("启用整期封顶时，日租累计整期金额不能低于整期续租价");
            }
        }
        normalizeGraceHours(graceHours);
    }

    private int normalizeGraceHours(Integer value) {
        var normalized = value == null ? 0 : value;
        if (normalized < 0 || normalized > 72) {
            throw BusinessException.badRequest("续租宽限时间必须在 0 到 72 小时之间");
        }
        return normalized;
    }

    private CategoryResponse toResponse(ProductCategory category) {
        return new CategoryResponse(category.id(), category.categoryCode(), category.categoryName(), category.sortOrder(), category.status().name());
    }

    private SkuResponse toResponse(ProductSku sku) {
        var categoryName = productRepository.findCategory(sku.categoryId()).map(ProductCategory::categoryName).orElse(null);
        return new SkuResponse(
            sku.id(),
            sku.skuCode(),
            sku.categoryId(),
            categoryName,
            sku.skuName(),
            sku.skuType().name(),
            sku.description(),
            sku.needFrameAsset(),
            sku.needBatteryAsset(),
            sku.supportCrossStoreReturn(),
            sku.status().name()
        );
    }

    private PackageResponse toResponse(ProductPackage item) {
        var skuName = productRepository.findSku(item.skuId()).map(ProductSku::skuName).orElse(null);
        return new PackageResponse(
            item.id(),
            item.packageCode(),
            item.skuId(),
            skuName,
            item.packageName(),
            item.priceAmount(),
            item.signFeeAmount(),
            item.leaseUnit().name(),
            item.leaseValue(),
            item.totalPeriods(),
            item.billDayMode().name(),
            item.billDay(),
            item.status().name()
        );
    }

    private StoreSkuResponse toResponse(StoreSku item) {
        var sku = ensureSku(item.skuId());
        var merchantName = merchantRepository.findById(item.merchantId()).map(merchant -> merchant.merchantName()).orElse(null);
        var storeName = storeRepository.findById(item.storeId()).map(store -> store.storeName()).orElse(null);
        var packages = productRepository.listStoreSkuPackages(item.id()).stream()
            .map(packagePrice -> {
                var template = ensurePackage(packagePrice.packageId());
                return new StoreSkuPackageResponse(
                    packagePrice.id(),
                    packagePrice.packageId(),
                    template.packageCode(),
                    template.packageName(),
                    template.leaseUnit().name(),
                    template.leaseValue(),
                    template.totalPeriods(),
                    template.billDayMode().name(),
                    template.billDay(),
                    packagePrice.rentalAmount(),
                    packagePrice.periodAmount(),
                    packagePrice.depositAmount(),
                    packagePrice.autoRenewEnabled(),
                    packagePrice.renewalUnit() == null ? null : packagePrice.renewalUnit().name(),
                    packagePrice.renewalValue(),
                    packagePrice.renewalAmount(),
                    packagePrice.renewalBillingMode().name(),
                    packagePrice.renewalDailyAmount(),
                    packagePrice.renewalDailyCapEnabled(),
                    packagePrice.renewalGraceHours(),
                    packagePrice.overdueDailyAmount(),
                    packagePrice.status().name()
                );
            })
            .toList();
        return new StoreSkuResponse(
            item.id(),
            item.merchantId(),
            merchantName,
            item.storeId(),
            storeName,
            item.skuId(),
            sku.skuName(),
            item.storeSkuCode(),
            item.saleMode().name(),
            item.displayName(),
            item.signFeeAmount(),
            item.signFeePayer().name(),
            sku.needFrameAsset(),
            sku.needBatteryAsset(),
            sku.supportCrossStoreReturn(),
            item.status().name(),
            packages
        );
    }

    private ProductCategory ensureCategory(Long id) {
        return productRepository.findCategory(id).orElseThrow(() -> BusinessException.badRequest("商品分类不存在"));
    }

    private ProductSku ensureSku(Long id) {
        return productRepository.findSku(id).orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
    }

    private ProductPackage ensurePackage(Long id) {
        return productRepository.findPackage(id).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
    }

    private StoreSku ensureStoreSku(Long id) {
        return productRepository.findStoreSku(id).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
    }

    private Merchant ensureEnabledMerchant(Long id) {
        var merchant = merchantRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        return merchant;
    }

    private MerchantStore ensureEnabledStore(Long id) {
        var store = storeRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        return store;
    }

    private ProductSku ensureEnabledSku(Long id) {
        var sku = ensureSku(id);
        if (sku.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("商品链接已停用");
        }
        return sku;
    }

    private ProductPackage ensureEnabledPackage(Long id) {
        var item = ensurePackage(id);
        if (item.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("SKU 已停用");
        }
        return item;
    }

    private void ensureStoreSkuCanBeOnShelf(StoreSku storeSku) {
        var store = ensureEnabledStore(storeSku.storeId());
        if (!store.merchantId().equals(storeSku.merchantId())) {
            throw BusinessException.badRequest("门店商品商户关系异常");
        }
        ensureEnabledMerchant(storeSku.merchantId());
        var sku = ensureEnabledSku(storeSku.skuId());
        if (sku.skuType() != storeSku.saleMode()) {
            throw BusinessException.badRequest("门店商品类型与商品链接类型不一致");
        }
        var packagePrices = productRepository.listStoreSkuPackages(storeSku.id());
        if (packagePrices.isEmpty()) {
            throw BusinessException.badRequest("门店商品未配置 SKU");
        }
        for (var packagePrice : packagePrices) {
            var template = ensureEnabledPackage(packagePrice.packageId());
            if (!template.skuId().equals(storeSku.skuId())) {
                throw BusinessException.badRequest("门店商品包含不属于当前链接的 SKU");
            }
        }
    }

    private void addBlocker(List<String> blockers, String label, int count) {
        if (count > 0) {
            blockers.add(label + " " + count + " 条");
        }
    }

    private SkuType parseSkuType(String value) {
        try {
            return SkuType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的链接类型");
        }
    }

    private LeaseUnit parseLeaseUnit(String value) {
        try {
            return LeaseUnit.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的租期单位");
        }
    }

    private BillDayMode parseBillDayMode(String value) {
        try {
            return BillDayMode.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的账单日规则");
        }
    }

    private SignFeePayer parseSignFeePayer(String value) {
        try {
            return SignFeePayer.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的签单费承担方");
        }
    }

    private StoreSkuStatus parseStoreSkuStatus(String value) {
        try {
            return value == null || value.isBlank() ? null : StoreSkuStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的上架状态");
        }
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
