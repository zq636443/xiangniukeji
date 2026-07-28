package com.xniu.rental.merchant.repository;

import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.StoreStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StoreRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MerchantStore> mapper = new StoreMapper();

    public StoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MerchantStore> list(Long merchantId, String keyword) {
        var sql = new StringBuilder("""
            SELECT s.*
            FROM merchant_store s
            JOIN merchant m ON m.id = s.merchant_id
            WHERE m.status <> 'ARCHIVED'
            """);
        var params = new java.util.ArrayList<Object>();
        if (merchantId != null) {
            sql.append(" AND s.merchant_id = ?");
            params.add(merchantId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.store_name LIKE ? OR s.store_code LIKE ? OR s.address LIKE ?)");
            var like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY s.id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public List<MerchantStore> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM merchant_store WHERE id IN (" + placeholders + ")", mapper, ids.toArray());
    }

    public List<MerchantStore> findByMerchantId(Long merchantId) {
        return jdbcTemplate.query("SELECT * FROM merchant_store WHERE merchant_id = ? ORDER BY id DESC", mapper, merchantId);
    }

    public Optional<MerchantStore> findById(Long id) {
        var stores = jdbcTemplate.query("SELECT * FROM merchant_store WHERE id = ?", mapper, id);
        return stores.stream().findFirst();
    }

    public Optional<MerchantStore> findByCode(String storeCode) {
        var stores = jdbcTemplate.query("SELECT * FROM merchant_store WHERE store_code = ?", mapper, storeCode);
        return stores.stream().findFirst();
    }

    public MerchantStore create(
        Long merchantId,
        String storeCode,
        String storeName,
        String address,
        String businessHours,
        BigDecimal longitude,
        BigDecimal latitude,
        String qrContent
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO merchant_store
                (merchant_id, store_code, store_name, address, business_hours, longitude, latitude, qr_content)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, merchantId);
            statement.setString(2, storeCode);
            statement.setString(3, storeName);
            statement.setString(4, address);
            statement.setString(5, businessHours);
            statement.setBigDecimal(6, longitude);
            statement.setBigDecimal(7, latitude);
            statement.setString(8, qrContent);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public MerchantStore update(Long id, String storeName, String address, String businessHours, BigDecimal longitude, BigDecimal latitude) {
        jdbcTemplate.update("""
            UPDATE merchant_store
            SET store_name = ?, address = ?, business_hours = ?, longitude = ?, latitude = ?
            WHERE id = ?
            """, storeName, address, businessHours, longitude, latitude, id);
        return findById(id).orElseThrow();
    }

    public MerchantStore updateStatus(Long id, StoreStatus status) {
        jdbcTemplate.update("UPDATE merchant_store SET status = ? WHERE id = ?", status.name(), id);
        return findById(id).orElseThrow();
    }

    public MerchantStore updateQrContent(Long id, String qrContent) {
        jdbcTemplate.update("UPDATE merchant_store SET qr_content = ? WHERE id = ?", qrContent, id);
        return findById(id).orElseThrow();
    }

    public int countBoundAccounts(Long storeId) {
        return count("SELECT COUNT(1) FROM sys_account WHERE store_id = ?", storeId);
    }

    public int countStoreScopes(Long storeId) {
        return count("SELECT COUNT(1) FROM auth_account_store_scope WHERE store_id = ?", storeId);
    }

    public int countStoreSkus(Long storeId) {
        return count("SELECT COUNT(1) FROM store_sku WHERE store_id = ?", storeId);
    }

    public int countSettlementRules(Long storeId) {
        return count("SELECT COUNT(1) FROM settlement_profit_rule WHERE store_id = ?", storeId);
    }

    public int countCurrentAssets(Long storeId) {
        return count("SELECT COUNT(1) FROM asset_item WHERE current_store_id = ?", storeId);
    }

    public int countOrders(Long storeId) {
        return count("SELECT COUNT(1) FROM rental_order WHERE store_id = ?", storeId);
    }

    public int countExternalOrders(Long storeId) {
        return count("SELECT COUNT(1) FROM external_rental_order WHERE store_id = ? OR return_store_id = ?", storeId, storeId);
    }

    public int countVouchers(Long storeId) {
        return count("SELECT COUNT(1) FROM voucher_verification WHERE store_id = ?", storeId);
    }

    public int countMaintenanceRecords(Long storeId) {
        return count("SELECT COUNT(1) FROM asset_maintenance_record WHERE store_id = ?", storeId);
    }

    public int countStorePartStocks(Long storeId) {
        return count("SELECT COUNT(1) FROM store_spare_part_stock WHERE store_id = ?", storeId);
    }

    public int countStorePartStockLogs(Long storeId) {
        return count("SELECT COUNT(1) FROM spare_part_stock_log WHERE store_id = ?", storeId);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM merchant_store WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private static class StoreMapper implements RowMapper<MerchantStore> {
        @Override
        public MerchantStore mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MerchantStore(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("store_code"),
                rs.getString("store_name"),
                rs.getString("address"),
                rs.getString("business_hours"),
                rs.getBigDecimal("longitude"),
                rs.getBigDecimal("latitude"),
                rs.getString("qr_content"),
                StoreStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
