package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.product.dto.CategoryRequest;
import com.xniu.rental.product.dto.PackageRequest;
import com.xniu.rental.product.dto.SkuRequest;
import com.xniu.rental.product.dto.StoreSkuPackageRequest;
import com.xniu.rental.product.dto.StoreSkuRequest;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
class ProductLinkSkuIntegrationTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setAdminAccount() {
        AuthContext.set(new CurrentAccount(
            "product-link-test-token",
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
                List.of("system.admin", "product.read", "product.write"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void productLinkShouldSupportMultipleNamedSkusWithIndependentPrices() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var category = productService.createCategory(new CategoryRequest("链接分类-" + suffix, 10));
        var link = productService.createSku(new SkuRequest(
            category.id(),
            "租赁链接-" + suffix,
            "RENTAL",
            "integration product link",
            new BigDecimal("6.60"),
            new BigDecimal("200.00"),
            true,
            true,
            false
        ));
        var monthlySku = productService.createPackage(new PackageRequest(
            link.id(),
            "月租 SKU-" + suffix,
            new BigDecimal("399.00"),
            new BigDecimal("12.00"),
            "MONTH",
            1,
            1,
            "PAYMENT_DAY",
            null
        ));
        var quarterlySku = productService.createPackage(new PackageRequest(
            link.id(),
            "季租 SKU-" + suffix,
            new BigDecimal("999.00"),
            "MONTH",
            3,
            3,
            "PAYMENT_DAY",
            null
        ));

        var published = productService.publishStoreSku(new StoreSkuRequest(
            1L,
            1L,
            link.id(),
            "门店租赁链接-" + suffix,
            "RENTAL",
            BigDecimal.ZERO,
            "USER",
            List.of(
                price(monthlySku.id(), "1.00", "399.00"),
                price(quarterlySku.id(), "1.00", "333.00")
            )
        ));

        assertThat(link.skuCode()).startsWith("LINK-");
        assertThat(link.batteryCostDailyAmount()).isEqualByComparingTo("6.60");
        assertThat(link.batteryCostMonthlyAmount()).isEqualByComparingTo("200.00");
        assertThat(monthlySku.packageCode()).startsWith("SKU-");
        assertThat(quarterlySku.packageCode()).startsWith("SKU-");
        assertThat(monthlySku.priceAmount()).isEqualByComparingTo("399.00");
        assertThat(monthlySku.signFeeAmount()).isEqualByComparingTo("12.00");
        assertThat(quarterlySku.priceAmount()).isEqualByComparingTo("999.00");
        assertThat(published.packages())
            .extracting(item -> item.packageName())
            .containsExactlyInAnyOrder("月租 SKU-" + suffix, "季租 SKU-" + suffix);
        assertThat(published.packages())
            .extracting(item -> item.rentalAmount())
            .containsExactlyInAnyOrder(new BigDecimal("399.00"), new BigDecimal("999.00"));

        var updatedMonthlySku = productService.updatePackage(monthlySku.id(), new PackageRequest(
            link.id(),
            monthlySku.packageName(),
            new BigDecimal("459.00"),
            new BigDecimal("15.00"),
            monthlySku.leaseUnit(),
            monthlySku.leaseValue(),
            monthlySku.totalPeriods(),
            monthlySku.billDayMode(),
            monthlySku.billDay()
        ));
        assertThat(updatedMonthlySku.signFeeAmount()).isEqualByComparingTo("15.00");
        var refreshed = productService.listStoreSkus(1L, link.id(), null).getFirst();
        assertThat(refreshed.packages().stream()
            .filter(item -> item.packageId().equals(monthlySku.id()))
            .findFirst()
            .orElseThrow()
            .rentalAmount()).isEqualByComparingTo("459.00");

        assertThatThrownBy(() -> productService.publishStoreSku(new StoreSkuRequest(
            1L,
            1L,
            link.id(),
            "重复 SKU 校验-" + suffix,
            "RENTAL",
            BigDecimal.ZERO,
            "USER",
            List.of(
                price(monthlySku.id(), "399.00", "399.00"),
                price(monthlySku.id(), "499.00", "499.00")
            )
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("SKU 不能重复");
    }

    @Test
    void productAndStorePublishLifecycleShouldKeepRelationsConsistent() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var category = productService.createCategory(new CategoryRequest("生命周期分类-" + suffix, 20));
        var link = productService.createSku(new SkuRequest(
            category.id(),
            "生命周期链接-" + suffix,
            "RENTAL",
            null,
            true,
            false,
            false
        ));
        var packageItem = productService.createPackage(new PackageRequest(
            link.id(),
            "生命周期 SKU-" + suffix,
            new BigDecimal("299.00"),
            "MONTH",
            1,
            1,
            "PAYMENT_DAY",
            null
        ));
        var publishRequest = new StoreSkuRequest(
            1L,
            1L,
            link.id(),
            "生命周期门店商品-" + suffix,
            "RENTAL",
            BigDecimal.ZERO,
            "USER",
            List.of(price(packageItem.id(), "299.00", "299.00"))
        );
        var published = productService.publishStoreSku(publishRequest);

        assertThatThrownBy(() -> productService.publishStoreSku(publishRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已配置此商品链接");
        assertThatThrownBy(() -> productService.updateSku(link.id(), new SkuRequest(
            category.id(),
            link.skuName(),
            "SALE",
            null,
            true,
            false,
            false
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能变更链接类型");
        assertThatThrownBy(() -> productService.deletePackage(packageItem.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("门店上架配置");

        productService.updateStoreSkuStatus(published.id(), StoreSkuStatus.OFF_SHELF);
        var updated = productService.updateStoreSku(published.id(), new StoreSkuRequest(
            1L,
            1L,
            link.id(),
            "已编辑但保持下架-" + suffix,
            "RENTAL",
            new BigDecimal("12.00"),
            "MERCHANT",
            List.of(price(packageItem.id(), "299.00", "299.00"))
        ));

        assertThat(updated.status()).isEqualTo("OFF_SHELF");
        assertThat(updated.displayName()).isEqualTo("已编辑但保持下架-" + suffix);
        productService.deleteStoreSku(published.id());

        productService.deletePackage(packageItem.id());
        productService.deleteSku(link.id());
        productService.deleteCategory(category.id());

        assertThat(productService.listPackages(link.id())).isEmpty();
        assertThat(productService.listSkus(category.id())).isEmpty();
        assertThat(productService.listCategories()).noneMatch(item -> item.id().equals(category.id()));
    }

    @Test
    void storeSkuWithHistoricalBusinessDataShouldBeArchivedAndCanBeRepublished() {
        productService.updateStoreSkuStatus(1L, StoreSkuStatus.OFF_SHELF);
        productService.deleteStoreSku(1L);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM store_sku WHERE id = 1",
            String.class
        )).isEqualTo("ARCHIVED");
        assertThat(productService.listStoreSkus(1L, 1L, null)).isEmpty();

        var restored = productService.publishStoreSku(new StoreSkuRequest(
            1L,
            1L,
            1L,
            "重新上架的演示门店整车租赁",
            "RENTAL",
            new BigDecimal("30.00"),
            "USER",
            List.of(price(1L, "39.00", "39.00"))
        ));

        assertThat(restored.id()).isEqualTo(1L);
        assertThat(restored.status()).isEqualTo("ON_SHELF");
        assertThat(restored.displayName()).isEqualTo("重新上架的演示门店整车租赁");
    }

    private StoreSkuPackageRequest price(Long skuId, String price, String periodAmount) {
        return new StoreSkuPackageRequest(
            skuId,
            new BigDecimal(price),
            new BigDecimal(periodAmount),
            BigDecimal.ZERO,
            true,
            "MONTH",
            1,
            new BigDecimal(periodAmount)
        );
    }
}
