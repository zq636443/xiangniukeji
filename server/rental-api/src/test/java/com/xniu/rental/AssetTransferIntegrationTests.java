package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.service.MerchantService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AssetTransferIntegrationTests {

    @Autowired
    private AssetService assetService;

    @SpyBean
    private AssetRepository assetRepository;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long siblingStoreId;
    private Long disabledStoreId;
    private Long otherMerchantId;
    private Long otherMerchantStoreId;

    @BeforeEach
    void setUp() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO merchant_store
            (merchant_id, store_code, store_name, address, business_hours, qr_content, status)
            VALUES (1, ?, ?, '深圳市南山区调拨路 2 号', '09:00-22:00', ?, 'ENABLED')
            """, "S-transfer-" + suffix, "同商户目标门店-" + suffix, "xniu://store/transfer-" + suffix);
        siblingStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            "S-transfer-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO merchant_store
            (merchant_id, store_code, store_name, address, business_hours, qr_content, status)
            VALUES (1, ?, ?, '深圳市南山区调拨路 3 号', '09:00-22:00', ?, 'DISABLED')
            """, "S-transfer-disabled-" + suffix, "同商户停用门店-" + suffix, "xniu://store/transfer-disabled-" + suffix);
        disabledStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            "S-transfer-disabled-" + suffix
        );

        jdbcTemplate.update("""
            INSERT INTO merchant
            (merchant_code, merchant_name, contact_name, contact_phone, status)
            VALUES (?, ?, '跨商户测试', '18800009999', 'ENABLED')
            """, "M-transfer-other-" + suffix, "其他商户-" + suffix);
        otherMerchantId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant WHERE merchant_code = ?",
            Long.class,
            "M-transfer-other-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO merchant_store
            (merchant_id, store_code, store_name, address, business_hours, qr_content, status)
            VALUES (?, ?, ?, '深圳市福田区跨商户路 8 号', '09:00-22:00', ?, 'ENABLED')
            """, otherMerchantId, "S-transfer-other-" + suffix, "其他商户门店-" + suffix, "xniu://store/other-" + suffix);
        otherMerchantStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            "S-transfer-other-" + suffix
        );
        setStoreManager(1L);
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void storeManagerShouldTransferOwnAssetToSiblingStoreWithoutTargetScope() {
        var assetId = createAsset("IDLE");

        var transferred = assetService.transferMerchantAsset(1L, assetId, new AssetTransferRequest(
            1L,
            siblingStoreId,
            "店长调拨到兄弟门店"
        ));

        assertThat(transferred.currentMerchantId()).isEqualTo(1L);
        assertThat(transferred.currentStoreId()).isEqualTo(siblingStoreId);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM asset_location_history
            WHERE asset_id = ?
              AND from_store_id = 1
              AND to_store_id = ?
              AND remark = '店长调拨到兄弟门店'
            """, Integer.class, assetId, siblingStoreId)).isEqualTo(1);

        var visibleStores = merchantService.listStores(null, null);
        assertThat(visibleStores).extracting("merchantId").containsOnly(1L);
        assertThat(visibleStores).extracting("id").contains(1L, siblingStoreId).doesNotContain(otherMerchantStoreId);
    }

    @Test
    void storeManagerShouldListOnlyEnabledSiblingStoresAsTransferTargets() {
        var transferTargets = assetService.listMerchantTransferTargets(1L);

        assertThat(transferTargets).extracting("id").containsOnly(siblingStoreId);
        assertThat(transferTargets).extracting("merchantId").containsOnly(1L);
        assertThat(transferTargets).allSatisfy(target -> {
            assertThat(target.storeCode()).isNotNull();
            assertThat(target.storeName()).isNotNull();
        });
        assertThat(transferTargets).extracting("id")
            .doesNotContain(1L, disabledStoreId, otherMerchantStoreId);

        var visibleStores = merchantService.listMyStores();
        assertThat(visibleStores).extracting("id").containsOnly(1L);
    }

    @Test
    void storeManagerShouldNotListTransferTargetsFromUnauthorizedSourceStore() {
        setStoreManager(siblingStoreId);

        assertThatThrownBy(() -> assetService.listMerchantTransferTargets(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    @Test
    void storeManagerShouldNotTransferAcrossMerchantsOrFromUnauthorizedStore() {
        var assetId = createAsset("IDLE");

        assertThatThrownBy(() -> assetService.transferMerchantAsset(1L, assetId, new AssetTransferRequest(
            otherMerchantId,
            otherMerchantStoreId,
            "越权跨商户调拨"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("同一商户");

        jdbcTemplate.update("UPDATE asset_item SET current_store_id = ? WHERE id = ?", siblingStoreId, assetId);
        assertThatThrownBy(() -> assetService.transferMerchantAsset(siblingStoreId, assetId, new AssetTransferRequest(
            1L,
            1L,
            "越权调出其他门店资产"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "RENTING",
        "PENDING_REPAIR",
        "REPAIRING",
        "SCRAPPED",
        "SOLD",
        "EXCEPTION"
    })
    void storeManagerShouldNotTransferNonIdleAsset(String status) {
        var assetId = createAsset(status);

        assertThatThrownBy(() -> assetService.transferMerchantAsset(1L, assetId, new AssetTransferRequest(
            1L,
            siblingStoreId,
            "错误调拨非空闲资产"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只有空闲资产可以调拨");
    }

    @Test
    void storeManagerShouldNotTransferAssetThatStopsBeingIdleAfterRead() {
        var assetId = createAsset("IDLE");
        var idleAsset = assetRepository.findById(assetId);
        var firstRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstRead.compareAndSet(true, false)) {
                jdbcTemplate.update("UPDATE asset_item SET status = 'RENTING' WHERE id = ?", assetId);
                return idleAsset;
            }
            return invocation.callRealMethod();
        }).when(assetRepository).findById(assetId);

        assertThatThrownBy(() -> assetService.transferMerchantAsset(1L, assetId, new AssetTransferRequest(
            1L,
            siblingStoreId,
            "读取后变为非空闲"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只有空闲资产可以调拨门店");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_store_id FROM asset_item WHERE id = ?",
            Long.class,
            assetId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM asset_location_history WHERE asset_id = ? AND remark = '读取后变为非空闲'",
            Integer.class,
            assetId
        )).isZero();
    }

    @Test
    void financeAccountWithStorePermissionShouldRetainPlatformStoreVisibility() {
        setFinanceAccount();

        var visibleStores = merchantService.listStores(null, null);

        assertThat(visibleStores).extracting("id").contains(1L, siblingStoreId, otherMerchantStoreId);
    }

    private Long createAsset(String status) {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id,
             status, purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    ?, 1, 1, 1, ?, 4200.00, 0.00, NULL, CURRENT_DATE)
            """, "A-transfer-" + suffix, "FRAME-TRANSFER-" + suffix, status);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-transfer-" + suffix
        );
    }

    private void setStoreManager(Long storeId) {
        AuthContext.set(new CurrentAccount(
            "asset-transfer-test-token",
            new CurrentAccountResponse(
                88001L,
                "STORE_MANAGER",
                "asset-transfer-manager",
                "18800008801",
                null,
                "调拨测试店长",
                1L,
                storeId,
                null,
                List.of("STORE_MANAGER"),
                List.of("asset.read", "asset.operate", "store.read"),
                List.of(new StoreScopeResponse(1L, storeId, "SINGLE_STORE"))
            )
        ));
    }

    private void setFinanceAccount() {
        AuthContext.set(new CurrentAccount(
            "asset-transfer-finance-token",
            new CurrentAccountResponse(
                88002L,
                "FINANCE",
                "asset-transfer-finance",
                "18800008802",
                null,
                "调拨测试财务",
                null,
                null,
                null,
                List.of("FINANCE"),
                List.of("store.read"),
                List.of()
            )
        ));
    }
}
