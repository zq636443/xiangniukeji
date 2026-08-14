# Merchant Asset Transfer and Store Product Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let merchant owners and store managers safely transfer idle assets within one merchant, and let platform administrators assign a product link with all enabled SKUs selected by default to one or many stores.

**Architecture:** Keep existing asset and `store_sku` persistence models. Add an idempotent RBAC migration and a source-scoped transfer-target query so store managers can choose sibling stores without gaining sibling-store data access; isolate product publishing defaults in a pure TypeScript helper used by both single and batch forms.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring JDBC, MySQL 8, Flyway, JUnit 5, AssertJ, React 18, TypeScript 5.6, Ant Design 5, Node.js 22 test runner, Vite 7.

---

### Task 1: Enforce merchant transfer permissions and idle-only status

**Files:**
- Create: `server/rental-api/src/main/resources/db/migration/V61__merchant_asset_transfer_permission.sql`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/AuthWorkspaceLoginIntegrationTests.java`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/AssetTransferIntegrationTests.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/asset/service/AssetService.java`

- [ ] **Step 1: Write failing permission and status tests**

Add `asset.operate` to the merchant login assertion and replace the renting-only test with a loop covering `RENTING`, `PENDING_REPAIR`, `REPAIRING`, `SCRAPPED`, `SOLD`, and `EXCEPTION`:

```java
assertThat(login.account().permissions()).contains("asset.operate");

for (var status : List.of("RENTING", "PENDING_REPAIR", "REPAIRING", "SCRAPPED", "SOLD", "EXCEPTION")) {
    var assetId = createAsset(status);
    assertThatThrownBy(() -> assetService.transferMerchantAsset(
        1L,
        assetId,
        new AssetTransferRequest(1L, siblingStoreId, "非空闲资产不能调拨")
    )).isInstanceOf(BusinessException.class)
      .hasMessageContaining("只有空闲资产可以调拨");
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd server/rental-api
./mvnw -Dtest=AuthWorkspaceLoginIntegrationTests,AssetTransferIntegrationTests test
```

Expected: the merchant permission assertion fails because `MERCHANT_OWNER` lacks `asset.operate`, and at least one non-idle status transfers because the service only blocks `RENTING`.

- [ ] **Step 3: Add the permission migration**

Create V61 with an idempotent role-permission insert:

```sql
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'asset.operate'
WHERE r.role_code = 'MERCHANT_OWNER'
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
```

- [ ] **Step 4: Enforce idle-only transfer in the service**

Change the shared transfer guard to:

```java
if (asset.status() != AssetStatus.IDLE) {
    throw BusinessException.badRequest("只有空闲资产可以调拨门店");
}
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Task 1 Maven command again. Expected: all tests in both classes pass and Flyway reports migration through V61.

### Task 2: Expose safe sibling-store transfer targets

**Files:**
- Create: `server/rental-api/src/main/java/com/xniu/rental/asset/dto/AssetTransferStoreResponse.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/asset/controller/MerchantAssetController.java`
- Modify: `server/rental-api/src/main/java/com/xniu/rental/asset/service/AssetService.java`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/AssetTransferIntegrationTests.java`
- Modify: `admin-web/src/pages/MerchantWorkspace.tsx`

- [ ] **Step 1: Write failing target-list tests**

Create an additional disabled sibling store and assert that a store manager sees only the enabled sibling in transfer targets while `merchantService.listMyStores()` still contains only the authorized source store:

```java
var targets = assetService.listMerchantTransferTargets(1L);

assertThat(targets).extracting("id").containsExactly(siblingStoreId);
assertThat(merchantService.listMyStores()).extracting("id")
    .containsExactly(1L)
    .doesNotContain(siblingStoreId, disabledSiblingStoreId, otherMerchantStoreId);
```

Also set the current account to the sibling store and verify querying targets from source store `1` fails with “没有该门店权限”.

- [ ] **Step 2: Run the target-list test and verify RED**

Run:

```bash
cd server/rental-api
./mvnw -Dtest=AssetTransferIntegrationTests test
```

Expected: compilation fails because `listMerchantTransferTargets` does not exist.

- [ ] **Step 3: Implement the minimal DTO and service query**

Add:

```java
public record AssetTransferStoreResponse(
    Long id,
    Long merchantId,
    String storeCode,
    String storeName
) {}
```

Implement `listMerchantTransferTargets(sourceStoreId)` by requiring `asset.operate`, loading the source through `requireActiveAccessibleStore`, then filtering `storeRepository.findByMerchantId(source.merchantId())` to enabled stores other than the source.

- [ ] **Step 4: Add the merchant API endpoint**

Expose:

```java
@GetMapping("/stores/{storeId}/transfer-targets")
public ApiResponse<List<AssetTransferStoreResponse>> listTransferTargets(@PathVariable Long storeId) {
    return ApiResponse.ok(assetService.listMerchantTransferTargets(storeId));
}
```

- [ ] **Step 5: Run backend tests and verify GREEN**

Run the Task 2 Maven command again. Expected: all `AssetTransferIntegrationTests` pass.

- [ ] **Step 6: Wire the merchant asset workspace to the target endpoint**

In `MerchantAssetWorkspace`, add `transferTargets` state, load `/api/merchant/assets/stores/${storeId}/transfer-targets` only when the account can operate assets, and derive the select options from that state instead of the workspace `stores` prop. Disable the transfer button unless `record.status === 'IDLE'` and a target exists; use the title “只有空闲资产可以调拨” for non-idle records.

- [ ] **Step 7: Run the admin typecheck**

Run:

```bash
cd admin-web
npm run typecheck
```

Expected: TypeScript exits successfully with no errors.

### Task 3: Test and implement default-all SKU publishing selections

**Files:**
- Create: `admin-web/src/utils/storeProductPublishing.ts`
- Create: `admin-web/scripts/store-product-publishing.test.mts`
- Modify: `admin-web/package.json`
- Modify: `admin-web/src/pages/ProductManagement.tsx`

- [ ] **Step 1: Write failing pure-helper tests**

Use Node's test runner to assert enabled packages for the selected link are all selected, disabled/other-link packages are excluded, period and renewal defaults are calculated, and removing one selected id preserves custom values for the remaining SKU:

```typescript
assert.deepEqual(
  buildDefaultPackagePrices(templates, 10).map((item) => item.packageId),
  [101, 102]
);
assert.equal(defaults[1].periodAmount, 333);
assert.equal(defaults[1].renewalAmount, 333);

const reconciled = reconcilePackagePrices(defaults, [102], templates);
assert.deepEqual(reconciled.map((item) => item.packageId), [102]);
```

- [ ] **Step 2: Run the helper test and verify RED**

Run:

```bash
cd admin-web
node --test --experimental-strip-types scripts/store-product-publishing.test.mts
```

Expected: the test fails because `src/utils/storeProductPublishing.ts` does not exist.

- [ ] **Step 3: Implement the pure selection helpers**

Export `StorePackagePriceForm`, `buildDefaultPackagePrices(templates, linkId)`, and `reconcilePackagePrices(current, selectedIds, templates)`. Defaults must use `priceAmount` for rental price, `priceAmount / totalPeriods` rounded to two decimals for each period, zero deposit, enabled auto-renewal, the template lease unit, `max(1, floor(leaseValue / totalPeriods))` renewal value, period amount as renewal amount, `PERIOD` billing, daily cap enabled, and zero grace hours.

- [ ] **Step 4: Run the helper test and verify GREEN**

Run the Task 3 Node command. Expected: all helper tests pass.

- [ ] **Step 5: Add a reusable npm test command**

Add:

```json
"test:store-product-publishing": "node --test --experimental-strip-types scripts/store-product-publishing.test.mts"
```

- [ ] **Step 6: Use default-all selection in both forms**

Replace each link-change assignment of `[defaultPackagePrice()]` with `buildDefaultPackagePrices(packages, skuId)`. Convert `storeSkuFields` into a `StoreSkuFields` React component, observe `packages` with `Form.useWatch('packages', form)`, and add a controlled multi-select whose `onChange` calls `reconcilePackagePrices`. Keep the detailed cards for editing price, deposit, and renewal fields, remove the manual “新增 SKU” button, and keep each card's delete command synchronized with the multi-select.

- [ ] **Step 7: Run helper tests and typecheck**

Run:

```bash
cd admin-web
npm run test:store-product-publishing
npm run typecheck
```

Expected: the helper tests and TypeScript typecheck pass.

### Task 4: Add publishing filters and duplicate-store protection

**Files:**
- Modify: `admin-web/src/pages/ProductManagement.tsx`
- Modify: `server/rental-api/src/test/java/com/xniu/rental/ProductLinkSkuIntegrationTests.java`

- [ ] **Step 1: Add a backend regression test for subset and duplicate batch behavior**

Extend `ProductLinkSkuIntegrationTests` to publish two enabled SKUs to one new store, verify both are persisted, then batch-publish only one selected SKU to another store and assert that only that SKU exists. Re-run the same batch request and assert the existing message contains “已配置此商品链接”.

- [ ] **Step 2: Run the focused product test and verify its baseline**

Run:

```bash
cd server/rental-api
./mvnw -Dtest=ProductLinkSkuIntegrationTests test
```

Expected before adding assertions: the existing suite passes. After adding the new test, it must pass against the existing backend contract; any failure identifies a server regression to fix before UI wiring.

- [ ] **Step 3: Add store-product filters**

Add merchant, store, link, and status filter state; derive `filteredStoreSkus`; reset the selected store whenever merchant changes; render filter controls above the table; and switch the table data source from `storeSkus` to `filteredStoreSkus`.

- [ ] **Step 4: Mark configured stores in single and batch forms**

For a selected link, derive store ids with non-archived `store_sku` records. Render those stores disabled with “已配置” in both store selectors. When a link change makes an already selected store invalid, clear it; in the batch form, remove configured stores from the current selection before submission.

- [ ] **Step 5: Run focused backend and frontend checks**

Run:

```bash
cd server/rental-api
./mvnw -Dtest=ProductLinkSkuIntegrationTests test
cd ../../admin-web
npm run test:store-product-publishing
npm run typecheck
```

Expected: all commands pass.

### Task 5: Full verification and implementation commit

**Files:**
- Modify: `docs/feature-content-updates-changelog.md`

- [ ] **Step 1: Update the feature changelog**

Add a 2026-08-14 entry recording merchant-owner transfer permission, store-manager sibling targets without widened data scope, idle-only transfers, and default-all enabled SKU publishing for one or many stores.

- [ ] **Step 2: Run the full backend suite**

Run:

```bash
cd server/rental-api
./mvnw test
```

Expected: all tests pass with Flyway at V61.

- [ ] **Step 3: Run admin runtime and production checks**

Run:

```bash
cd admin-web
npm run test:store-product-publishing
npm run test:dayjs-dedupe
npm run typecheck
npm run build
```

Expected: helper tests, Dayjs runtime assertion, typecheck, and Vite production build all pass.

- [ ] **Step 4: Inspect the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat HEAD~2
```

Expected: no whitespace errors, no secrets, and the pre-existing untracked `admin-web/pnpm-lock.yaml` and `admin-web/pnpm-workspace.yaml` remain unstaged.

- [ ] **Step 5: Commit only implementation files**

Explicitly stage the migration, backend source/tests, admin source/test script/package metadata, changelog, and this plan. Do not stage the two pre-existing untracked pnpm files. Commit with:

```bash
git commit -m "feat: improve merchant transfers and store products"
```

- [ ] **Step 6: Report deployment boundary**

Report the local commit and validation results. Do not push or deploy until the user explicitly requests publishing this implementation.
