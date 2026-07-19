package com.xniu.rental.pay.repository;

import com.xniu.rental.pay.model.AgreementNotify;
import com.xniu.rental.pay.model.AgreementStatus;
import com.xniu.rental.pay.model.AgreementType;
import com.xniu.rental.pay.model.PayAgreement;
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
public class AgreementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PayAgreement> agreementMapper = new AgreementMapper();
    private final RowMapper<AgreementNotify> notifyMapper = new NotifyMapper();

    public AgreementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PayAgreement> listAgreements(AgreementStatus status, Long userAccountId, Long orderId) {
        var sql = new StringBuilder("SELECT * FROM rental_pay_agreement WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND agreement_status = ?");
            params.add(status.name());
        }
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), agreementMapper, params.toArray());
    }

    public Optional<PayAgreement> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_pay_agreement WHERE id = ?", agreementMapper, id);
        return list.stream().findFirst();
    }

    public Optional<PayAgreement> findByExternalAgreementNo(String externalAgreementNo) {
        var list = jdbcTemplate.query("SELECT * FROM rental_pay_agreement WHERE external_agreement_no = ?", agreementMapper, externalAgreementNo);
        return list.stream().findFirst();
    }

    public Optional<PayAgreement> findSignedByOrderId(Long orderId) {
        var list = jdbcTemplate.query("""
            SELECT * FROM rental_pay_agreement
            WHERE order_id = ? AND agreement_status = 'SIGNED'
            ORDER BY id DESC LIMIT 1
            """, agreementMapper, orderId);
        return list.stream().findFirst();
    }

    public PayAgreement createAgreement(AgreementCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_pay_agreement
                (external_agreement_no, user_account_id, alipay_user_id, order_id, merchant_id, store_id,
                 agreement_type, agreement_status, personal_product_code, sign_scene, max_single_amount, sign_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.externalAgreementNo());
            statement.setLong(2, row.userAccountId());
            statement.setString(3, row.alipayUserId());
            setNullableLong(statement, 4, row.orderId());
            statement.setLong(5, row.merchantId());
            statement.setLong(6, row.storeId());
            statement.setString(7, row.agreementType().name());
            statement.setString(8, row.agreementStatus().name());
            statement.setString(9, row.personalProductCode());
            statement.setString(10, row.signScene());
            statement.setBigDecimal(11, row.maxSingleAmount());
            statement.setString(12, row.signUrl());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public PayAgreement updateSignUrl(Long id, String signUrl) {
        jdbcTemplate.update("UPDATE rental_pay_agreement SET sign_url = ?, last_error = NULL WHERE id = ?", signUrl, id);
        return findById(id).orElseThrow();
    }

    public PayAgreement markSigned(Long id, String agreementNo, LocalDateTime signTime, LocalDateTime validTime, LocalDateTime invalidTime) {
        jdbcTemplate.update("""
            UPDATE rental_pay_agreement
            SET agreement_no = ?,
                agreement_status = 'SIGNED',
                sign_time = ?,
                valid_time = ?,
                invalid_time = ?,
                last_error = NULL
            WHERE id = ?
            """, agreementNo, signTime, validTime, invalidTime, id);
        return findById(id).orElseThrow();
    }

    public PayAgreement updateStatus(Long id, AgreementStatus status, String lastError) {
        jdbcTemplate.update("""
            UPDATE rental_pay_agreement
            SET agreement_status = ?, last_error = ?
            WHERE id = ?
            """, status.name(), lastError, id);
        return findById(id).orElseThrow();
    }

    public Optional<AgreementNotify> findNotifyByNotifyId(String notifyId) {
        if (notifyId == null || notifyId.isBlank()) {
            return Optional.empty();
        }
        var list = jdbcTemplate.query("SELECT * FROM rental_agreement_notify WHERE notify_id = ?", notifyMapper, notifyId);
        return list.stream().findFirst();
    }

    public AgreementNotify createNotify(NotifyCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_agreement_notify
                (agreement_id, notify_id, external_agreement_no, agreement_no, agreement_status,
                 verified, processed, raw_payload, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            setNullableLong(statement, 1, row.agreementId());
            statement.setString(2, row.notifyId());
            statement.setString(3, row.externalAgreementNo());
            statement.setString(4, row.agreementNo());
            statement.setString(5, row.agreementStatus());
            statement.setBoolean(6, row.verified());
            statement.setBoolean(7, row.processed());
            statement.setString(8, row.rawPayload());
            statement.setString(9, row.failureReason());
            return statement;
        }, keyHolder);
        return findNotify(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<AgreementNotify> findNotify(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_agreement_notify WHERE id = ?", notifyMapper, id);
        return list.stream().findFirst();
    }

    public List<AgreementNotify> listNotifies() {
        return jdbcTemplate.query("SELECT * FROM rental_agreement_notify ORDER BY id DESC", notifyMapper);
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record AgreementCreateRow(
        String externalAgreementNo,
        Long userAccountId,
        String alipayUserId,
        Long orderId,
        Long merchantId,
        Long storeId,
        AgreementType agreementType,
        AgreementStatus agreementStatus,
        String personalProductCode,
        String signScene,
        BigDecimal maxSingleAmount,
        String signUrl
    ) {
    }

    public record NotifyCreateRow(
        Long agreementId,
        String notifyId,
        String externalAgreementNo,
        String agreementNo,
        String agreementStatus,
        Boolean verified,
        Boolean processed,
        String rawPayload,
        String failureReason
    ) {
    }

    private static class AgreementMapper implements RowMapper<PayAgreement> {
        @Override
        public PayAgreement mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PayAgreement(
                rs.getLong("id"),
                rs.getString("agreement_no"),
                rs.getString("external_agreement_no"),
                rs.getLong("user_account_id"),
                rs.getString("alipay_user_id"),
                getNullableLong(rs, "order_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                AgreementType.valueOf(rs.getString("agreement_type")),
                AgreementStatus.valueOf(rs.getString("agreement_status")),
                rs.getString("personal_product_code"),
                rs.getString("sign_scene"),
                rs.getBigDecimal("max_single_amount"),
                rs.getString("sign_url"),
                rs.getObject("sign_time", LocalDateTime.class),
                rs.getObject("valid_time", LocalDateTime.class),
                rs.getObject("invalid_time", LocalDateTime.class),
                rs.getString("last_error"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class NotifyMapper implements RowMapper<AgreementNotify> {
        @Override
        public AgreementNotify mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgreementNotify(
                rs.getLong("id"),
                getNullableLong(rs, "agreement_id"),
                rs.getString("notify_id"),
                rs.getString("external_agreement_no"),
                rs.getString("agreement_no"),
                rs.getString("agreement_status"),
                rs.getBoolean("verified"),
                rs.getBoolean("processed"),
                rs.getString("raw_payload"),
                rs.getString("failure_reason"),
                rs.getObject("received_at", LocalDateTime.class)
            );
        }
    }
}
