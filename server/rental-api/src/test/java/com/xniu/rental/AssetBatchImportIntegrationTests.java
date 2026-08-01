package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.dto.AssetBatchImportRequest;
import com.xniu.rental.asset.dto.AssetBatchImportRowRequest;
import com.xniu.rental.asset.dto.AssetRequest;
import com.xniu.rental.asset.dto.AssetStatusRequest;
import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.dto.AssetTypeRequest;
import com.xniu.rental.asset.dto.AssetUpdateRequest;
import com.xniu.rental.asset.dto.InvestorAssetRequest;
import com.xniu.rental.asset.dto.InvestorAssetUpdateRequest;
import com.xniu.rental.asset.service.AssetService;
import com.xniu.rental.asset.service.AssetTypeService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class AssetBatchImportIntegrationTests {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetTypeService assetTypeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setAdminAccount() {
        AuthContext.set(new CurrentAccount(
            "admin-test-token",
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
                List.of("system.admin", "asset.import"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void adminBatchImportShouldKeepSuccessfulRowsWhenOtherRowsFail() {
        var serialNo = "FRAME-BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        var result = assetService.batchImportAssets(new AssetBatchImportRequest(List.of(
            row(2, "车架", serialNo, "I-demo-001", "S-demo-001", "2600", "35.50", "", "2026/7/20", "2026-07-LY-01"),
            row(3, "车架", "FRAME-DEMO-001", "I-demo-001", "S-demo-001", "2600", "35", "300", "2026-07-20"),
            row(4, "电池", "BATTERY-NO-INVESTOR", "I-not-found", "S-demo-001", "1800", "25", "200", "2026-07-20")
        )));

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.results().get(0).success()).isTrue();
        assertThat(result.results().get(0).assetCode()).startsWith("A-FRM-");
        assertThat(result.results().get(1).message()).contains("已存在");
        assertThat(result.results().get(2).message()).contains("出资方编码不存在");

        var asset = jdbcTemplate.queryForMap(
            "SELECT id, investor_id, current_merchant_id, current_store_id, purchase_amount, maintenance_fee_amount, residual_value, arrival_batch_no FROM asset_item WHERE serial_no = ?",
            serialNo
        );
        var assetId = ((Number) asset.get("id")).longValue();
        assertThat(asset.get("investor_id")).isEqualTo(1L);
        assertThat(asset.get("current_merchant_id")).isEqualTo(1L);
        assertThat(asset.get("current_store_id")).isEqualTo(1L);
        assertThat(asset.get("purchase_amount")).isEqualTo(new BigDecimal("2600.00"));
        assertThat(asset.get("maintenance_fee_amount")).isEqualTo(new BigDecimal("0.00"));
        assertThat(asset.get("residual_value")).isNull();
        assertThat(asset.get("arrival_batch_no")).isEqualTo("2026-07-LY-01");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT purchased_at FROM asset_item WHERE id = ?",
            LocalDate.class,
            assetId
        )).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(count("asset_ownership_history", assetId)).isEqualTo(1);
        assertThat(count("asset_location_history", assetId)).isEqualTo(1);
        assertThat(count("asset_status_log", assetId)).isEqualTo(1);
    }

    @Test
    void singleAssetEntryShouldIgnoreLegacyMaintenanceFeeAndAllowMissingResidualValue() {
        var serialNo = "BATTERY-SINGLE-" + UUID.randomUUID().toString().substring(0, 8);

        var asset = assetService.createAsset(new AssetRequest(
            "BATTERY",
            serialNo,
            1L,
            1L,
            1L,
            new BigDecimal("1800.00"),
            new BigDecimal("88.00"),
            null,
            LocalDate.of(2026, 7, 20)
        ));

        assertThat(asset.maintenanceFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(asset.residualValue()).isNull();
        assertThat(asset.arrivalBatchNo()).isEqualTo("ARR-2026-07-20-I1-B01");
        var stored = jdbcTemplate.queryForMap(
            "SELECT maintenance_fee_amount, residual_value, arrival_batch_no FROM asset_item WHERE serial_no = ?",
            serialNo
        );
        assertThat(stored.get("maintenance_fee_amount")).isEqualTo(new BigDecimal("0.00"));
        assertThat(stored.get("residual_value")).isNull();
        assertThat(stored.get("arrival_batch_no")).isEqualTo("ARR-2026-07-20-I1-B01");
    }

    @Test
    void legacyAssetsShouldBeBackfilledByInvestorAndPurchaseDate() {
        var legacyAsset = jdbcTemplate.queryForMap(
            "SELECT investor_id, purchased_at, created_at, arrival_batch_no FROM asset_item WHERE id = 1"
        );
        var batchDate = legacyAsset.get("purchased_at") == null
            ? ((java.sql.Timestamp) legacyAsset.get("created_at")).toLocalDateTime().toLocalDate()
            : ((java.sql.Date) legacyAsset.get("purchased_at")).toLocalDate();
        assertThat(legacyAsset.get("arrival_batch_no"))
            .isEqualTo("ARR-" + batchDate + "-I" + legacyAsset.get("investor_id") + "-B01");
    }

    @Test
    void platformAssetEntryShouldRejectIncompleteMerchantStoreLocation() {
        var typeId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'",
            Long.class
        );

        assertThatThrownBy(() -> assetService.createAsset(new AssetRequest(
            typeId,
            null,
            "BATTERY-PARTIAL-" + UUID.randomUUID().toString().substring(0, 8),
            1L,
            1L,
            null,
            new BigDecimal("1800.00"),
            null,
            null,
            LocalDate.of(2026, 7, 28)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("同时选择商户和门店");
    }

    @Test
    void integratedVehicleShouldUseFrameNumberForSingleAndBatchEntry() {
        var singleSerialNo = "INTEGRATED-SINGLE-" + UUID.randomUUID().toString().substring(0, 8);
        var single = assetService.createAsset(new AssetRequest(
            "车电一体",
            singleSerialNo,
            1L,
            1L,
            1L,
            new BigDecimal("4200.00"),
            null,
            null,
            LocalDate.of(2026, 7, 20)
        ));

        assertThat(single.assetType()).isEqualTo("INTEGRATED_VEHICLE");
        assertThat(single.assetCode()).startsWith("A-INT-");
        assertThat(single.serialNo()).isEqualTo(singleSerialNo);
        assertThat(assetService.listAssets(null, null, null, "INTEGRATED_VEHICLE", "IDLE", singleSerialNo))
            .extracting("id")
            .containsExactly(single.id());

        var batchSerialNo = "INTEGRATED-BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        var imported = assetService.batchImportAssets(new AssetBatchImportRequest(List.of(
            row(2, "车电一体", batchSerialNo, "I-demo-001", "S-demo-001", "4300", "", "", "2026-07-20")
        )));

        assertThat(imported.successCount()).isEqualTo(1);
        assertThat(imported.results().getFirst().assetCode()).startsWith("A-INT-");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT asset_type FROM asset_item WHERE serial_no = ?",
            String.class,
            batchSerialNo
        )).isEqualTo("INTEGRATED_VEHICLE");
    }

    @Test
    void platformShouldCreateAndEditCustomAssetTypesAndUseThemForEntry() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var createdType = assetTypeService.createType(new AssetTypeRequest(
            "头盔-" + suffix,
            "GENERAL",
            "头盔编号",
            80,
            "DISABLED"
        ));
        assertThat(createdType.status()).isEqualTo("DISABLED");

        var updatedType = assetTypeService.updateType(createdType.id(), new AssetTypeRequest(
            "智能头盔-" + suffix,
            "GENERAL",
            "设备编号",
            81,
            "ENABLED"
        ));
        assertThat(updatedType.assetClass()).isEqualTo("GENERAL");
        assertThat(updatedType.serialLabel()).isEqualTo("设备编号");

        var serialNo = "HELMET-" + suffix;
        var asset = assetService.createAsset(new AssetRequest(
            updatedType.id(),
            null,
            serialNo,
            1L,
            1L,
            1L,
            new BigDecimal("399.00"),
            null,
            null,
            LocalDate.of(2026, 7, 22)
        ));

        assertThat(asset.assetType()).isEqualTo("GENERAL");
        assertThat(asset.assetTypeId()).isEqualTo(updatedType.id());
        assertThat(asset.assetTypeName()).isEqualTo("智能头盔-" + suffix);
        assertThat(asset.serialLabel()).isEqualTo("设备编号");
        assertThat(assetService.listAssets(null, null, null, updatedType.id(), null, "IDLE", serialNo))
            .extracting("id")
            .containsExactly(asset.id());
    }

    @Test
    void storeManagerShouldCreateAndEditOnlyAssetsInAuthorizedStore() {
        var typeId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'",
            Long.class
        );
        var otherStoreCode = "S-ASSET-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO merchant_store (merchant_id, store_code, store_name, address, qr_content) VALUES (1, ?, '资产测试门店', '测试地址', ?)",
            otherStoreCode,
            "QR-" + otherStoreCode
        );
        var otherStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            otherStoreCode
        );
        setStoreManagerAccount(
            List.of("asset.read", "asset.manage"),
            List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY"))
        );

        var serialNo = "STORE-FRAME-" + UUID.randomUUID().toString().substring(0, 8);
        var created = assetService.createMerchantAsset(1L, new AssetRequest(
            typeId,
            null,
            serialNo,
            1L,
            null,
            null,
            new BigDecimal("2600.00"),
            null,
            null,
            LocalDate.of(2026, 7, 22)
        ));
        assertThat(created.currentMerchantId()).isEqualTo(1L);
        assertThat(created.currentStoreId()).isEqualTo(1L);

        var updated = assetService.updateMerchantAsset(1L, created.id(), new AssetUpdateRequest(
            typeId,
            serialNo + "-EDIT",
            1L,
            new BigDecimal("2680.00"),
            new BigDecimal("200.00"),
            LocalDate.of(2026, 7, 21)
        ));
        assertThat(updated.serialNo()).isEqualTo(serialNo + "-EDIT");
        assertThat(updated.purchaseAmount()).isEqualByComparingTo("2680.00");

        assertThatThrownBy(() -> assetService.createMerchantAsset(otherStoreId, new AssetRequest(
            typeId,
            null,
            serialNo + "-OTHER",
            1L,
            null,
            null,
            new BigDecimal("2600.00"),
            null,
            null,
            LocalDate.of(2026, 7, 22)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");

        assertThatThrownBy(() -> assetService.updateMerchantAsset(otherStoreId, created.id(), new AssetUpdateRequest(
            typeId,
            serialNo + "-CROSS",
            1L,
            new BigDecimal("2600.00"),
            null,
            LocalDate.of(2026, 7, 22)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");

        jdbcTemplate.update("UPDATE merchant_store SET status = 'DISABLED' WHERE id = 1");
        assertThatThrownBy(() -> assetService.createMerchantAsset(1L, new AssetRequest(
            typeId,
            null,
            serialNo + "-DISABLED",
            1L,
            null,
            null,
            new BigDecimal("2600.00"),
            null,
            null,
            LocalDate.of(2026, 7, 22)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("门店已停用");
    }

    @Test
    void merchantBatchImportShouldLockAssetsToAuthorizedStore() {
        setMerchantAccount(List.of("asset.read", "asset.import"), List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY")));
        var serialNo = "BATTERY-BATCH-" + UUID.randomUUID().toString().substring(0, 8);

        var imported = assetService.batchImportMerchantAssets(1L, new AssetBatchImportRequest(List.of(
            row(2, "电池", serialNo, "I-demo-001", "", "1800", "25", "200", "2026-07-20")
        )));

        assertThat(imported.successCount()).isEqualTo(1);
        var location = jdbcTemplate.queryForMap(
            "SELECT current_merchant_id, current_store_id FROM asset_item WHERE serial_no = ?",
            serialNo
        );
        assertThat(location.get("current_merchant_id")).isEqualTo(1L);
        assertThat(location.get("current_store_id")).isEqualTo(1L);

        var mismatchedStore = assetService.batchImportMerchantAssets(1L, new AssetBatchImportRequest(List.of(
            row(3, "电池", serialNo + "-OTHER", "I-demo-001", "S-other-001", "1800", "25", "200", "2026-07-20")
        )));
        assertThat(mismatchedStore.failedCount()).isEqualTo(1);
        assertThat(mismatchedStore.results().get(0).message()).contains("模板门店与当前门店不一致");
    }

    @Test
    void merchantBatchImportShouldRequirePermissionAndStoreScope() {
        var request = new AssetBatchImportRequest(List.of(
            row(2, "车架", "FRAME-PERMISSION-CHECK", "I-demo-001", "", "2600", "35", "300", "2026-07-20")
        ));

        setMerchantAccount(List.of("asset.read"), List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY")));
        assertThatThrownBy(() -> assetService.batchImportMerchantAssets(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有操作权限");

        setMerchantAccount(List.of("asset.read", "asset.import"), List.of());
        assertThatThrownBy(() -> assetService.batchImportMerchantAssets(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    @Test
    void investorShouldManageOnlyOwnedAssetsAndAssignThemToStores() {
        var typeId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'",
            Long.class
        );
        var targetStoreCode = "S-INVESTOR-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO merchant_store (merchant_id, store_code, store_name, address, qr_content) VALUES (1, ?, '出资方调拨门店', '测试地址', ?)",
            targetStoreCode,
            "QR-" + targetStoreCode
        );
        var targetStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            targetStoreCode
        );
        jdbcTemplate.update("""
            INSERT INTO investor
            (investor_code, investor_name, contact_name, contact_phone, operation_fee_rate, status)
            VALUES ('I-investor-other', '其他出资方', '其他出资方', '18800009999', 0.0800, 'ENABLED')
            """);
        var otherInvestorId = jdbcTemplate.queryForObject(
            "SELECT id FROM investor WHERE investor_code = 'I-investor-other'",
            Long.class
        );

        setInvestorAccount(1L);
        var serialNo = "INVESTOR-FRAME-" + UUID.randomUUID().toString().substring(0, 8);
        var created = assetService.createInvestorAsset(new InvestorAssetRequest(
            typeId,
            null,
            serialNo,
            1L,
            1L,
            new BigDecimal("2600.00"),
            new BigDecimal("300.00"),
            LocalDate.of(2026, 7, 29),
            "2026-07-INV-01"
        ));
        assertThat(created.investorId()).isEqualTo(1L);
        assertThat(created.currentMerchantId()).isEqualTo(1L);
        assertThat(created.currentStoreId()).isEqualTo(1L);
        assertThat(created.arrivalBatchNo()).isEqualTo("2026-07-INV-01");

        var updated = assetService.updateInvestorAsset(created.id(), new InvestorAssetUpdateRequest(
            typeId,
            serialNo + "-EDIT",
            new BigDecimal("2680.00"),
            new BigDecimal("280.00"),
            LocalDate.of(2026, 7, 28),
            "2026-07-INV-02"
        ));
        assertThat(updated.serialNo()).isEqualTo(serialNo + "-EDIT");
        assertThat(updated.purchaseAmount()).isEqualByComparingTo("2680.00");
        assertThat(updated.arrivalBatchNo()).isEqualTo("2026-07-INV-02");

        var transferred = assetService.transferInvestorAsset(created.id(), new AssetTransferRequest(
            1L,
            targetStoreId,
            "出资方调拨测试"
        ));
        assertThat(transferred.currentStoreId()).isEqualTo(targetStoreId);

        var repairing = assetService.updateInvestorAssetStatus(created.id(), new AssetStatusRequest(
            "PENDING_REPAIR",
            "出资方送修"
        ));
        assertThat(repairing.status()).isEqualTo("PENDING_REPAIR");
        assetService.updateInvestorAssetStatus(created.id(), new AssetStatusRequest("IDLE", "检修完成"));

        setInvestorAccount(otherInvestorId);
        assertThatThrownBy(() -> assetService.updateInvestorAsset(created.id(), new InvestorAssetUpdateRequest(
            typeId,
            serialNo + "-CROSS",
            new BigDecimal("2600.00"),
            null,
            LocalDate.of(2026, 7, 29)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只能操作当前出资方名下的资产");

        setInvestorAccount(1L);
        assetService.deleteInvestorAsset(created.id());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM asset_item WHERE id = ?",
            Integer.class,
            created.id()
        )).isZero();
    }

    private AssetBatchImportRowRequest row(
        int lineNo,
        String assetType,
        String serialNo,
        String investorCode,
        String storeCode,
        String purchaseAmount,
        String maintenanceFeeAmount,
        String residualValue,
        String purchasedAt
    ) {
        return row(lineNo, assetType, serialNo, investorCode, storeCode, purchaseAmount, maintenanceFeeAmount, residualValue, purchasedAt, null);
    }

    private AssetBatchImportRowRequest row(
        int lineNo,
        String assetType,
        String serialNo,
        String investorCode,
        String storeCode,
        String purchaseAmount,
        String maintenanceFeeAmount,
        String residualValue,
        String purchasedAt,
        String arrivalBatchNo
    ) {
        return new AssetBatchImportRowRequest(
            lineNo,
            assetType,
            serialNo,
            investorCode,
            storeCode,
            purchaseAmount,
            maintenanceFeeAmount,
            residualValue,
            purchasedAt,
            arrivalBatchNo
        );
    }

    private int count(String tableName, Long assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE asset_id = ?",
            Integer.class,
            assetId
        );
    }

    private void setMerchantAccount(List<String> permissions, List<StoreScopeResponse> storeScopes) {
        setMerchantAccount("MERCHANT_OWNER", null, permissions, storeScopes);
    }

    private void setStoreManagerAccount(List<String> permissions, List<StoreScopeResponse> storeScopes) {
        setMerchantAccount("STORE_MANAGER", 1L, permissions, storeScopes);
    }

    private void setInvestorAccount(Long investorId) {
        AuthContext.set(new CurrentAccount(
            "investor-test-token",
            new CurrentAccountResponse(
                4L,
                "INVESTOR",
                "investor-test",
                "18800000004",
                null,
                "出资方测试账号",
                null,
                null,
                investorId,
                List.of("INVESTOR"),
                List.of("asset.read", "asset.manage", "asset.operate"),
                List.of()
            )
        ));
    }

    private void setMerchantAccount(
        String accountType,
        Long storeId,
        List<String> permissions,
        List<StoreScopeResponse> storeScopes
    ) {
        AuthContext.set(new CurrentAccount(
            "merchant-test-token",
            new CurrentAccountResponse(
                2L,
                accountType,
                "merchant_demo",
                "18800000002",
                null,
                "演示商户老板",
                1L,
                storeId,
                null,
                List.of(accountType),
                permissions,
                storeScopes
            )
        ));
    }
}
