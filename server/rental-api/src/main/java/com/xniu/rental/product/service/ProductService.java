package com.xniu.rental.product.service;

import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
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
import com.xniu.rental.product.model.SignFeePayer;
import com.xniu.rental.product.model.SkuType;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import java.math.BigDecimal;
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

    public List<SkuResponse> listSkus(Long categoryId) {
        authorizationService.requirePermission("product.read");
        return productRepository.listSkus(categoryId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SkuResponse createSku(SkuRequest request) {
        authorizationService.requirePermission("product.write");
        ensureCategory(request.categoryId());
        var sku = productRepository.createSku(
            nextCode("SKU"),
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
        ensureSku(id);
        ensureCategory(request.categoryId());
        return toResponse(productRepository.updateSku(
            id,
            request.categoryId(),
            request.skuName(),
            parseSkuType(request.skuType()),
            request.description(),
            request.needFrameAsset(),
            request.needBatteryAsset(),
            request.supportCrossStoreReturn()
        ));
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
            nextCode("PKG"),
            request.skuId(),
            request.packageName(),
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
        ensurePackage(id);
        return toResponse(productRepository.updatePackage(
            id,
            request.packageName(),
            parseLeaseUnit(request.leaseUnit()),
            request.leaseValue(),
            request.totalPeriods(),
            parseBillDayMode(request.billDayMode()),
            request.billDay()
        ));
    }

    public List<StoreSkuResponse> listStoreSkus(Long storeId, Long skuId, String status) {
        authorizationService.requirePermission("product.read");
        return productRepository.listStoreSkus(storeId, skuId, parseStoreSkuStatus(status)).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public StoreSkuResponse publishStoreSku(StoreSkuRequest request) {
        authorizationService.requirePermission("product.write");
        validateStoreSkuRequest(request.storeId(), request.skuId(), request.packages());
        var store = storeRepository.findById(request.storeId()).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (!store.merchantId().equals(request.merchantId())) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
        merchantRepository.findById(request.merchantId()).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        var existing = productRepository.findStoreSkuByStoreAndSku(request.storeId(), request.skuId());
        var storeSku = existing
            .map(item -> productRepository.updateStoreSku(
                item.id(),
                parseSkuType(request.saleMode()),
                request.displayName(),
                normalizeMoney(request.signFeeAmount()),
                parseSignFeePayer(request.signFeePayer())
            ))
            .orElseGet(() -> productRepository.createStoreSku(
                nextCode("SSKU"),
                request.merchantId(),
                request.storeId(),
                request.skuId(),
                parseSkuType(request.saleMode()),
                request.displayName(),
                normalizeMoney(request.signFeeAmount()),
                parseSignFeePayer(request.signFeePayer())
            ));
        productRepository.replaceStoreSkuPackages(storeSku.id(), toRows(request.packages()));
        return toResponse(storeSku);
    }

    @Transactional
    public List<StoreSkuResponse> batchPublish(StoreSkuBatchPublishRequest request) {
        authorizationService.requirePermission("product.write");
        ensureSku(request.skuId());
        validatePackagePrices(request.skuId(), request.packages());
        return request.storeIds().stream().map(storeId -> {
            var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
            var sku = ensureSku(request.skuId());
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
        ensureStoreSku(id);
        return toResponse(productRepository.updateStoreSkuStatus(id, status));
    }

    public List<StoreSkuResponse> listUserStoreProducts(String storeCode) {
        var stores = storeRepository.list(null, storeCode);
        var store = stores.stream()
            .filter(item -> item.storeCode().equals(storeCode))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        return productRepository.listStoreSkus(store.id(), null, StoreSkuStatus.ON_SHELF).stream()
            .map(this::toResponse)
            .toList();
    }

    public StoreSkuResponse getUserStoreProduct(Long storeSkuId) {
        var storeSku = ensureStoreSku(storeSkuId);
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("商品未上架");
        }
        return toResponse(storeSku);
    }

    private void validateStoreSkuRequest(Long storeId, Long skuId, List<StoreSkuPackageRequest> packages) {
        ensureSku(skuId);
        storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        validatePackagePrices(skuId, packages);
    }

    private void validatePackagePrices(Long skuId, List<StoreSkuPackageRequest> packages) {
        Set<Long> packageIds = new HashSet<>();
        for (var item : packages) {
            if (!packageIds.add(item.packageId())) {
                throw BusinessException.badRequest("同一个门店商品下套餐不能重复");
            }
            var template = ensurePackage(item.packageId());
            if (!template.skuId().equals(skuId)) {
                throw BusinessException.badRequest("套餐不属于所选 SKU");
            }
            if (item.rentalAmount().signum() < 0 || item.periodAmount().signum() < 0 || item.depositAmount().signum() < 0) {
                throw BusinessException.badRequest("金额不能小于 0");
            }
        }
    }

    private void validatePackage(PackageRequest request) {
        if (request.leaseValue() <= 0 || request.totalPeriods() <= 0) {
            throw BusinessException.badRequest("租期和总期数必须大于 0");
        }
        if ("FIXED_DAY".equals(request.billDayMode()) && (request.billDay() == null || request.billDay() < 1 || request.billDay() > 28)) {
            throw BusinessException.badRequest("固定账单日必须在 1 到 28 之间");
        }
    }

    private List<ProductRepository.PackagePriceRow> toRows(List<StoreSkuPackageRequest> packages) {
        return packages.stream()
            .map(item -> new ProductRepository.PackagePriceRow(
                item.packageId(),
                normalizeMoney(item.rentalAmount()),
                normalizeMoney(item.periodAmount()),
                normalizeMoney(item.depositAmount())
            ))
            .toList();
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
                    template.packageName(),
                    template.leaseUnit().name(),
                    template.leaseValue(),
                    template.totalPeriods(),
                    template.billDayMode().name(),
                    template.billDay(),
                    packagePrice.rentalAmount(),
                    packagePrice.periodAmount(),
                    packagePrice.depositAmount(),
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
        return productRepository.findSku(id).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
    }

    private ProductPackage ensurePackage(Long id) {
        return productRepository.findPackage(id).orElseThrow(() -> BusinessException.badRequest("套餐不存在"));
    }

    private StoreSku ensureStoreSku(Long id) {
        return productRepository.findStoreSku(id).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
    }

    private SkuType parseSkuType(String value) {
        try {
            return SkuType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的 SKU 类型");
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
