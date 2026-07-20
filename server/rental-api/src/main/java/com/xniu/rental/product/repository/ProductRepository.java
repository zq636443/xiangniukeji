package com.xniu.rental.product.repository;

import com.xniu.rental.product.model.BillDayMode;
import com.xniu.rental.product.model.LeaseUnit;
import com.xniu.rental.product.model.ProductCategory;
import com.xniu.rental.product.model.ProductPackage;
import com.xniu.rental.product.model.ProductSku;
import com.xniu.rental.product.model.ProductStatus;
import com.xniu.rental.product.model.SignFeePayer;
import com.xniu.rental.product.model.SkuType;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuPackage;
import com.xniu.rental.product.model.StoreSkuStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ProductCategory> categoryMapper = new CategoryMapper();
    private final RowMapper<ProductSku> skuMapper = new SkuMapper();
    private final RowMapper<ProductPackage> packageMapper = new PackageMapper();
    private final RowMapper<StoreSku> storeSkuMapper = new StoreSkuMapper();
    private final RowMapper<StoreSkuPackage> storeSkuPackageMapper = new StoreSkuPackageMapper();

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductCategory> listCategories() {
        return jdbcTemplate.query("SELECT * FROM product_category ORDER BY sort_order, id DESC", categoryMapper);
    }

    public Optional<ProductCategory> findCategory(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM product_category WHERE id = ?", categoryMapper, id);
        return list.stream().findFirst();
    }

    public ProductCategory createCategory(String code, String name, Integer sortOrder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO product_category (category_code, category_name, sort_order)
                VALUES (?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setInt(3, sortOrder == null ? 0 : sortOrder);
            return statement;
        }, keyHolder);
        return findCategory(keyHolder.getKey().longValue()).orElseThrow();
    }

    public ProductCategory updateCategory(Long id, String name, Integer sortOrder) {
        jdbcTemplate.update("UPDATE product_category SET category_name = ?, sort_order = ? WHERE id = ?", name, sortOrder == null ? 0 : sortOrder, id);
        return findCategory(id).orElseThrow();
    }

    public List<ProductSku> listSkus(Long categoryId) {
        if (categoryId == null) {
            return jdbcTemplate.query("SELECT * FROM product_sku ORDER BY id DESC", skuMapper);
        }
        return jdbcTemplate.query("SELECT * FROM product_sku WHERE category_id = ? ORDER BY id DESC", skuMapper, categoryId);
    }

    public Optional<ProductSku> findSku(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM product_sku WHERE id = ?", skuMapper, id);
        return list.stream().findFirst();
    }

    public ProductSku createSku(String code, Long categoryId, String name, SkuType type, String description, Boolean needFrame, Boolean needBattery, Boolean crossReturn) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO product_sku
                (sku_code, category_id, sku_name, sku_type, description, need_frame_asset, need_battery_asset, support_cross_store_return)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, code);
            statement.setLong(2, categoryId);
            statement.setString(3, name);
            statement.setString(4, type.name());
            statement.setString(5, description);
            statement.setBoolean(6, Boolean.TRUE.equals(needFrame));
            statement.setBoolean(7, Boolean.TRUE.equals(needBattery));
            statement.setBoolean(8, Boolean.TRUE.equals(crossReturn));
            return statement;
        }, keyHolder);
        return findSku(keyHolder.getKey().longValue()).orElseThrow();
    }

    public ProductSku updateSku(Long id, Long categoryId, String name, SkuType type, String description, Boolean needFrame, Boolean needBattery, Boolean crossReturn) {
        jdbcTemplate.update("""
            UPDATE product_sku
            SET category_id = ?, sku_name = ?, sku_type = ?, description = ?,
                need_frame_asset = ?, need_battery_asset = ?, support_cross_store_return = ?
            WHERE id = ?
            """, categoryId, name, type.name(), description, Boolean.TRUE.equals(needFrame), Boolean.TRUE.equals(needBattery), Boolean.TRUE.equals(crossReturn), id);
        return findSku(id).orElseThrow();
    }

    public List<ProductPackage> listPackages(Long skuId) {
        if (skuId == null) {
            return jdbcTemplate.query("SELECT * FROM product_package ORDER BY id DESC", packageMapper);
        }
        return jdbcTemplate.query("SELECT * FROM product_package WHERE sku_id = ? ORDER BY id DESC", packageMapper, skuId);
    }

    public Optional<ProductPackage> findPackage(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM product_package WHERE id = ?", packageMapper, id);
        return list.stream().findFirst();
    }

    public Optional<ProductPackage> findPackageByCode(String packageCode) {
        var list = jdbcTemplate.query("SELECT * FROM product_package WHERE package_code = ?", packageMapper, packageCode);
        return list.stream().findFirst();
    }

    public ProductPackage createPackage(String code, Long skuId, String name, BigDecimal priceAmount, LeaseUnit unit, Integer leaseValue, Integer totalPeriods, BillDayMode billDayMode, Integer billDay) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO product_package
                (package_code, sku_id, package_name, price_amount, lease_unit, lease_value, total_periods, bill_day_mode, bill_day)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, code);
            statement.setLong(2, skuId);
            statement.setString(3, name);
            statement.setBigDecimal(4, priceAmount);
            statement.setString(5, unit.name());
            statement.setInt(6, leaseValue);
            statement.setInt(7, totalPeriods);
            statement.setString(8, billDayMode.name());
            if (billDay == null) {
                statement.setObject(9, null);
            } else {
                statement.setInt(9, billDay);
            }
            return statement;
        }, keyHolder);
        return findPackage(keyHolder.getKey().longValue()).orElseThrow();
    }

    public ProductPackage updatePackage(Long id, String name, BigDecimal priceAmount, LeaseUnit unit, Integer leaseValue, Integer totalPeriods, BillDayMode billDayMode, Integer billDay) {
        jdbcTemplate.update("""
            UPDATE product_package
            SET package_name = ?, price_amount = ?, lease_unit = ?, lease_value = ?, total_periods = ?, bill_day_mode = ?, bill_day = ?
            WHERE id = ?
            """, name, priceAmount, unit.name(), leaseValue, totalPeriods, billDayMode.name(), billDay, id);
        jdbcTemplate.update("UPDATE store_sku_package SET rental_amount = ? WHERE package_id = ?", priceAmount, id);
        return findPackage(id).orElseThrow();
    }

    public List<StoreSku> listStoreSkus(Long storeId, Long skuId, StoreSkuStatus status) {
        var sql = new StringBuilder("SELECT * FROM store_sku WHERE 1 = 1");
        var params = new java.util.ArrayList<Object>();
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        if (skuId != null) {
            sql.append(" AND sku_id = ?");
            params.add(skuId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), storeSkuMapper, params.toArray());
    }

    public Optional<StoreSku> findStoreSku(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM store_sku WHERE id = ?", storeSkuMapper, id);
        return list.stream().findFirst();
    }

    public Optional<StoreSku> findStoreSkuByCode(String storeSkuCode) {
        var list = jdbcTemplate.query("SELECT * FROM store_sku WHERE store_sku_code = ?", storeSkuMapper, storeSkuCode);
        return list.stream().findFirst();
    }

    public Optional<StoreSku> findStoreSkuByStoreAndSku(Long storeId, Long skuId) {
        var list = jdbcTemplate.query("SELECT * FROM store_sku WHERE store_id = ? AND sku_id = ?", storeSkuMapper, storeId, skuId);
        return list.stream().findFirst();
    }

    public StoreSku createStoreSku(String code, Long merchantId, Long storeId, Long skuId, SkuType saleMode, String displayName, BigDecimal signFee, SignFeePayer payer) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO store_sku
                (merchant_id, store_id, sku_id, store_sku_code, sale_mode, display_name, sign_fee_amount, sign_fee_payer)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, merchantId);
            statement.setLong(2, storeId);
            statement.setLong(3, skuId);
            statement.setString(4, code);
            statement.setString(5, saleMode.name());
            statement.setString(6, displayName);
            statement.setBigDecimal(7, signFee);
            statement.setString(8, payer.name());
            return statement;
        }, keyHolder);
        return findStoreSku(keyHolder.getKey().longValue()).orElseThrow();
    }

    public StoreSku updateStoreSku(Long id, SkuType saleMode, String displayName, BigDecimal signFee, SignFeePayer payer) {
        jdbcTemplate.update("""
            UPDATE store_sku
            SET sale_mode = ?, display_name = ?, sign_fee_amount = ?, sign_fee_payer = ?, status = 'ON_SHELF'
            WHERE id = ?
            """, saleMode.name(), displayName, signFee, payer.name(), id);
        return findStoreSku(id).orElseThrow();
    }

    public StoreSku updateStoreSkuStatus(Long id, StoreSkuStatus status) {
        jdbcTemplate.update("UPDATE store_sku SET status = ? WHERE id = ?", status.name(), id);
        return findStoreSku(id).orElseThrow();
    }

    public void replaceStoreSkuPackages(Long storeSkuId, List<PackagePriceRow> rows) {
        jdbcTemplate.update("DELETE FROM store_sku_package WHERE store_sku_id = ?", storeSkuId);
        for (var row : rows) {
            jdbcTemplate.update("""
                INSERT INTO store_sku_package
                (store_sku_id, package_id, rental_amount, period_amount, deposit_amount,
                 auto_renew_enabled, renewal_unit, renewal_value, renewal_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                storeSkuId,
                row.packageId(),
                row.rentalAmount(),
                row.periodAmount(),
                row.depositAmount(),
                row.autoRenewEnabled(),
                row.renewalUnit() == null ? null : row.renewalUnit().name(),
                row.renewalValue(),
                row.renewalAmount()
            );
        }
    }

    public List<StoreSkuPackage> listStoreSkuPackages(Long storeSkuId) {
        return jdbcTemplate.query("SELECT * FROM store_sku_package WHERE store_sku_id = ? ORDER BY id", storeSkuPackageMapper, storeSkuId);
    }

    public record PackagePriceRow(
        Long packageId,
        BigDecimal rentalAmount,
        BigDecimal periodAmount,
        BigDecimal depositAmount,
        Boolean autoRenewEnabled,
        LeaseUnit renewalUnit,
        Integer renewalValue,
        BigDecimal renewalAmount
    ) {
    }

    private static class CategoryMapper implements RowMapper<ProductCategory> {
        @Override
        public ProductCategory mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProductCategory(
                rs.getLong("id"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getInt("sort_order"),
                ProductStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static class SkuMapper implements RowMapper<ProductSku> {
        @Override
        public ProductSku mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProductSku(
                rs.getLong("id"),
                rs.getString("sku_code"),
                rs.getLong("category_id"),
                rs.getString("sku_name"),
                SkuType.valueOf(rs.getString("sku_type")),
                rs.getString("description"),
                rs.getBoolean("need_frame_asset"),
                rs.getBoolean("need_battery_asset"),
                rs.getBoolean("support_cross_store_return"),
                ProductStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static class PackageMapper implements RowMapper<ProductPackage> {
        @Override
        public ProductPackage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProductPackage(
                rs.getLong("id"),
                rs.getString("package_code"),
                rs.getLong("sku_id"),
                rs.getString("package_name"),
                rs.getBigDecimal("price_amount"),
                LeaseUnit.valueOf(rs.getString("lease_unit")),
                rs.getInt("lease_value"),
                rs.getInt("total_periods"),
                BillDayMode.valueOf(rs.getString("bill_day_mode")),
                getNullableInt(rs, "bill_day"),
                ProductStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static class StoreSkuMapper implements RowMapper<StoreSku> {
        @Override
        public StoreSku mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoreSku(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("sku_id"),
                rs.getString("store_sku_code"),
                SkuType.valueOf(rs.getString("sale_mode")),
                rs.getString("display_name"),
                rs.getBigDecimal("sign_fee_amount"),
                SignFeePayer.valueOf(rs.getString("sign_fee_payer")),
                StoreSkuStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static class StoreSkuPackageMapper implements RowMapper<StoreSkuPackage> {
        @Override
        public StoreSkuPackage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoreSkuPackage(
                rs.getLong("id"),
                rs.getLong("store_sku_id"),
                rs.getLong("package_id"),
                rs.getBigDecimal("rental_amount"),
                rs.getBigDecimal("period_amount"),
                rs.getBigDecimal("deposit_amount"),
                rs.getBoolean("auto_renew_enabled"),
                nullableLeaseUnit(rs, "renewal_unit"),
                getNullableInt(rs, "renewal_value"),
                rs.getBigDecimal("renewal_amount"),
                ProductStatus.valueOf(rs.getString("status"))
            );
        }
    }

    private static LeaseUnit nullableLeaseUnit(ResultSet rs, String column) throws SQLException {
        var value = rs.getString(column);
        return value == null ? null : LeaseUnit.valueOf(value);
    }
}
