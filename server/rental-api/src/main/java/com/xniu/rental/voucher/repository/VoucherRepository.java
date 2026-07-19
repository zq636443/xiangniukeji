package com.xniu.rental.voucher.repository;

import com.xniu.rental.voucher.model.SourcePlatform;
import com.xniu.rental.voucher.model.VoucherVerification;
import com.xniu.rental.voucher.model.VoucherVerifyStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VoucherRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<VoucherVerification> mapper = new VoucherMapper();

    public VoucherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VoucherVerification> list(SourcePlatform platform, VoucherVerifyStatus status, Long userAccountId, Long storeId) {
        var sql = new StringBuilder("SELECT * FROM voucher_verification WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (platform != null) {
            sql.append(" AND source_platform = ?");
            params.add(platform.name());
        }
        if (status != null) {
            sql.append(" AND verify_status = ?");
            params.add(status.name());
        }
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public Optional<VoucherVerification> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM voucher_verification WHERE id = ?", mapper, id);
        return list.stream().findFirst();
    }

    public Optional<VoucherVerification> findByPlatformAndCode(SourcePlatform platform, String voucherCode) {
        var list = jdbcTemplate.query("SELECT * FROM voucher_verification WHERE source_platform = ? AND voucher_code = ?", mapper, platform.name(), voucherCode);
        return list.stream().findFirst();
    }

    public VoucherVerification create(CreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO voucher_verification
                (source_platform, voucher_code, user_account_id, merchant_id, store_id, store_sku_id,
                 package_id, verify_status, voucher_amount, sign_fee_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'INPUT', ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.sourcePlatform().name());
            statement.setString(2, row.voucherCode());
            setNullableLong(statement, 3, row.userAccountId());
            statement.setLong(4, row.merchantId());
            statement.setLong(5, row.storeId());
            statement.setLong(6, row.storeSkuId());
            statement.setLong(7, row.packageId());
            statement.setBigDecimal(8, row.voucherAmount());
            statement.setBigDecimal(9, row.signFeeAmount());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public VoucherVerification issueInternalCode(IssueRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO voucher_verification
                (source_platform, voucher_code, user_account_id, merchant_id, store_id, store_sku_id,
                 package_id, verify_status, voucher_title, voucher_amount, sign_fee_amount, valid_from, raw_payload)
                VALUES (?, ?, NULL, ?, ?, ?, ?, 'INPUT', ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, new String[] {"id"});
            statement.setString(1, row.sourcePlatform().name());
            statement.setString(2, row.voucherCode());
            statement.setLong(3, row.merchantId());
            statement.setLong(4, row.storeId());
            statement.setLong(5, row.storeSkuId());
            statement.setLong(6, row.packageId());
            statement.setString(7, row.voucherTitle());
            statement.setBigDecimal(8, row.voucherAmount());
            statement.setBigDecimal(9, row.signFeeAmount());
            statement.setString(10, row.rawPayload());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public VoucherVerification updateSelection(Long id, Long userAccountId, Long merchantId, Long storeId, Long storeSkuId, Long packageId, BigDecimal voucherAmount, BigDecimal signFeeAmount) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET user_account_id = COALESCE(user_account_id, ?),
                merchant_id = ?,
                store_id = ?,
                store_sku_id = ?,
                package_id = ?,
                voucher_amount = ?,
                sign_fee_amount = ?,
                failure_reason = NULL
            WHERE id = ?
            """, userAccountId, merchantId, storeId, storeSkuId, packageId, voucherAmount, signFeeAmount, id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markPrepared(Long id, GatewayRow row) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'PREPARED',
                voucher_title = ?,
                voucher_amount = ?,
                external_prepare_id = ?,
                valid_from = ?,
                valid_to = ?,
                raw_payload = ?,
                failure_reason = NULL
            WHERE id = ?
            """, row.voucherTitle(), row.voucherAmount(), row.externalId(), row.validFrom(), row.validTo(), row.rawPayload(), id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markVerified(Long id, GatewayRow row) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'VERIFIED',
                voucher_title = COALESCE(?, voucher_title),
                voucher_amount = ?,
                external_verify_id = ?,
                valid_from = COALESCE(?, valid_from),
                valid_to = COALESCE(?, valid_to),
                raw_payload = ?,
                failure_reason = NULL,
                verified_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, row.voucherTitle(), row.voucherAmount(), row.externalId(), row.validFrom(), row.validTo(), row.rawPayload(), id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification attachOrder(Long id, Long orderId, Long billId) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'WAITING_SIGN_FEE',
                order_id = ?,
                sign_fee_bill_id = ?
            WHERE id = ?
            """, orderId, billId, id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markConsuming(Long id) {
        jdbcTemplate.update("UPDATE voucher_verification SET verify_status = 'CONSUMING', retry_count = retry_count + 1 WHERE id = ?", id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markConsumed(Long id, GatewayRow row) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'CONSUMED',
                external_consume_id = ?,
                raw_payload = ?,
                failure_reason = NULL,
                consumed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, row.externalId(), row.rawPayload(), id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markFailed(Long id, String reason, String rawPayload) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'FAILED',
                failure_reason = ?,
                raw_payload = COALESCE(?, raw_payload)
            WHERE id = ?
            """, reason, rawPayload, id);
        return findById(id).orElseThrow();
    }

    public VoucherVerification markException(Long id, String reason) {
        jdbcTemplate.update("""
            UPDATE voucher_verification
            SET verify_status = 'EXCEPTION',
                exception_reason = ?
            WHERE id = ?
            """, reason, id);
        return findById(id).orElseThrow();
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record CreateRow(SourcePlatform sourcePlatform, String voucherCode, Long userAccountId, Long merchantId, Long storeId, Long storeSkuId, Long packageId, BigDecimal voucherAmount, BigDecimal signFeeAmount) {
    }

    public record IssueRow(SourcePlatform sourcePlatform, String voucherCode, Long merchantId, Long storeId, Long storeSkuId, Long packageId, String voucherTitle, BigDecimal voucherAmount, BigDecimal signFeeAmount, String rawPayload) {
    }

    public record GatewayRow(String externalId, String voucherTitle, BigDecimal voucherAmount, LocalDateTime validFrom, LocalDateTime validTo, String rawPayload) {
    }

    private static class VoucherMapper implements RowMapper<VoucherVerification> {
        @Override
        public VoucherVerification mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new VoucherVerification(
                rs.getLong("id"),
                SourcePlatform.valueOf(rs.getString("source_platform")),
                rs.getString("voucher_code"),
                nullableLong(rs, "user_account_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("store_sku_id"),
                rs.getLong("package_id"),
                nullableLong(rs, "order_id"),
                nullableLong(rs, "sign_fee_bill_id"),
                VoucherVerifyStatus.valueOf(rs.getString("verify_status")),
                rs.getString("voucher_title"),
                rs.getBigDecimal("voucher_amount"),
                rs.getBigDecimal("sign_fee_amount"),
                rs.getString("external_prepare_id"),
                rs.getString("external_verify_id"),
                rs.getString("external_consume_id"),
                rs.getObject("valid_from", LocalDateTime.class),
                rs.getObject("valid_to", LocalDateTime.class),
                rs.getInt("retry_count"),
                rs.getString("raw_payload"),
                rs.getString("failure_reason"),
                rs.getObject("verified_at", LocalDateTime.class),
                rs.getObject("consumed_at", LocalDateTime.class),
                rs.getString("exception_reason"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
