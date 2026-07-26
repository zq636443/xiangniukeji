package com.xniu.rental.merchant.repository;

import com.xniu.rental.merchant.model.Merchant;
import com.xniu.rental.merchant.model.MerchantStatus;
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
public class MerchantRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Merchant> mapper = new MerchantMapper();

    public MerchantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Merchant> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM merchant ORDER BY id DESC", mapper);
        }
        var like = "%" + keyword.trim() + "%";
        return jdbcTemplate.query("""
            SELECT * FROM merchant
            WHERE merchant_name LIKE ? OR merchant_code LIKE ? OR contact_phone LIKE ?
            ORDER BY id DESC
            """, mapper, like, like, like);
    }

    public Optional<Merchant> findById(Long id) {
        var merchants = jdbcTemplate.query("SELECT * FROM merchant WHERE id = ?", mapper, id);
        return merchants.stream().findFirst();
    }

    public Merchant create(String merchantCode, String merchantName, String contactName, String contactPhone, String businessLicenseNo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO merchant
                (merchant_code, merchant_name, contact_name, contact_phone, business_license_no)
                VALUES (?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, merchantCode);
            statement.setString(2, merchantName);
            statement.setString(3, contactName);
            statement.setString(4, contactPhone);
            statement.setString(5, businessLicenseNo);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Merchant update(Long id, String merchantName, String contactName, String contactPhone, String businessLicenseNo) {
        jdbcTemplate.update("""
            UPDATE merchant
            SET merchant_name = ?, contact_name = ?, contact_phone = ?, business_license_no = ?
            WHERE id = ?
            """, merchantName, contactName, contactPhone, businessLicenseNo, id);
        return findById(id).orElseThrow();
    }

    public Merchant updateStatus(Long id, MerchantStatus status) {
        jdbcTemplate.update("UPDATE merchant SET status = ? WHERE id = ?", status.name(), id);
        return findById(id).orElseThrow();
    }

    public int countStores(Long merchantId) {
        return count("SELECT COUNT(1) FROM merchant_store WHERE merchant_id = ?", merchantId);
    }

    public int countActiveAccounts(Long merchantId) {
        return count("SELECT COUNT(1) FROM sys_account WHERE merchant_id = ? AND deleted_at IS NULL", merchantId);
    }

    public int countCurrentAssets(Long merchantId) {
        return count("SELECT COUNT(1) FROM asset_item WHERE current_merchant_id = ?", merchantId);
    }

    public int countOrders(Long merchantId) {
        return count("SELECT COUNT(1) FROM rental_order WHERE merchant_id = ?", merchantId);
    }

    public int countExternalOrders(Long merchantId) {
        return count("SELECT COUNT(1) FROM external_rental_order WHERE merchant_id = ?", merchantId);
    }

    public int countStoreSkus(Long merchantId) {
        return count("SELECT COUNT(1) FROM store_sku WHERE merchant_id = ?", merchantId);
    }

    public int countSettlementRecords(Long merchantId) {
        return count("""
            SELECT
                (SELECT COUNT(1) FROM settlement_profit_rule WHERE merchant_id = ?)
              + (SELECT COUNT(1) FROM settlement_rule_snapshot WHERE merchant_id = ?)
              + (SELECT COUNT(1) FROM settlement_income_entry WHERE merchant_id = ?)
              + (SELECT COUNT(1) FROM settlement_statement WHERE merchant_id = ?)
            """, merchantId, merchantId, merchantId, merchantId);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM merchant WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        var count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static class MerchantMapper implements RowMapper<Merchant> {
        @Override
        public Merchant mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Merchant(
                rs.getLong("id"),
                rs.getString("merchant_code"),
                rs.getString("merchant_name"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("business_license_no"),
                MerchantStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
