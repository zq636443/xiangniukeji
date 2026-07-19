package com.xniu.rental.verify.repository;

import com.xniu.rental.verify.model.IdentityVerification;
import com.xniu.rental.verify.model.OcrStatus;
import com.xniu.rental.verify.model.RealNameStatus;
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
public class IdentityRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<IdentityVerification> mapper = new IdentityMapper();

    public IdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IdentityVerification> list(Long userAccountId, Long orderId, RealNameStatus status) {
        var sql = new StringBuilder("SELECT * FROM user_identity_verification WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (status != null) {
            sql.append(" AND real_name_status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public Optional<IdentityVerification> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM user_identity_verification WHERE id = ?", mapper, id);
        return list.stream().findFirst();
    }

    public Optional<IdentityVerification> findLatestByUserAndOrder(Long userAccountId, Long orderId) {
        var list = jdbcTemplate.query("""
            SELECT * FROM user_identity_verification
            WHERE user_account_id = ? AND order_id = ?
            ORDER BY id DESC LIMIT 1
            """, mapper, userAccountId, orderId);
        return list.stream().findFirst();
    }

    public IdentityVerification createImages(Long userAccountId, Long orderId, String frontImageUrl, String backImageUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO user_identity_verification
                (user_account_id, order_id, front_image_url, back_image_url, ocr_status, real_name_status)
                VALUES (?, ?, ?, ?, 'PENDING', 'PENDING')
                """, new String[] {"id"});
            statement.setLong(1, userAccountId);
            statement.setLong(2, orderId);
            statement.setString(3, frontImageUrl);
            statement.setString(4, backImageUrl);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public IdentityVerification updateImages(Long id, String frontImageUrl, String backImageUrl) {
        jdbcTemplate.update("""
            UPDATE user_identity_verification
            SET front_image_url = ?, back_image_url = ?, ocr_status = 'PENDING', failure_reason = NULL
            WHERE id = ?
            """, frontImageUrl, backImageUrl, id);
        return findById(id).orElseThrow();
    }

    public IdentityVerification markOcrSuccess(Long id, String provider) {
        jdbcTemplate.update("UPDATE user_identity_verification SET ocr_status = 'SUCCESS', ocr_provider = ?, failure_reason = NULL WHERE id = ?", provider, id);
        return findById(id).orElseThrow();
    }

    public IdentityVerification markOcrFailed(Long id, String provider, String reason) {
        jdbcTemplate.update("UPDATE user_identity_verification SET ocr_status = 'FAILED', ocr_provider = ?, failure_reason = ? WHERE id = ?", provider, reason, id);
        return findById(id).orElseThrow();
    }

    public IdentityVerification markVerified(Long id, VerifiedRow row) {
        jdbcTemplate.update("""
            UPDATE user_identity_verification
            SET real_name_status = 'VERIFIED',
                ocr_status = 'SUCCESS',
                real_name_masked = ?,
                id_no_masked = ?,
                id_no_hash = ?,
                gender = ?,
                birth_date = ?,
                address_masked = ?,
                certify_provider = ?,
                external_certify_id = ?,
                verified_at = CURRENT_TIMESTAMP,
                failure_reason = NULL
            WHERE id = ?
            """, row.realNameMasked(), row.idNoMasked(), row.idNoHash(), row.gender(), row.birthDate(), row.addressMasked(), row.certifyProvider(), row.externalCertifyId(), id);
        return findById(id).orElseThrow();
    }

    public record VerifiedRow(
        String realNameMasked,
        String idNoMasked,
        String idNoHash,
        String gender,
        String birthDate,
        String addressMasked,
        String certifyProvider,
        String externalCertifyId
    ) {
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static class IdentityMapper implements RowMapper<IdentityVerification> {
        @Override
        public IdentityVerification mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new IdentityVerification(
                rs.getLong("id"),
                rs.getLong("user_account_id"),
                getNullableLong(rs, "order_id"),
                rs.getString("front_image_url"),
                rs.getString("back_image_url"),
                OcrStatus.valueOf(rs.getString("ocr_status")),
                RealNameStatus.valueOf(rs.getString("real_name_status")),
                rs.getString("real_name_masked"),
                rs.getString("id_no_masked"),
                rs.getString("id_no_hash"),
                rs.getString("gender"),
                rs.getString("birth_date"),
                rs.getString("address_masked"),
                rs.getString("ocr_provider"),
                rs.getString("certify_provider"),
                rs.getString("external_certify_id"),
                rs.getString("failure_reason"),
                rs.getObject("verified_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
