package com.xniu.rental.pay.repository;

import com.xniu.rental.pay.model.PayChannel;
import com.xniu.rental.pay.model.PayStatus;
import com.xniu.rental.pay.model.PaymentCallback;
import com.xniu.rental.pay.model.PaymentOrder;
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
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PaymentOrder> paymentMapper = new PaymentMapper();
    private final RowMapper<PaymentCallback> callbackMapper = new CallbackMapper();

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PaymentOrder> listPayments(PayStatus status, Long billId, Long orderId) {
        var sql = new StringBuilder("SELECT * FROM rental_payment_order WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND pay_status = ?");
            params.add(status.name());
        }
        if (billId != null) {
            sql.append(" AND bill_id = ?");
            params.add(billId);
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), paymentMapper, params.toArray());
    }

    public Optional<PaymentOrder> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_payment_order WHERE id = ?", paymentMapper, id);
        return list.stream().findFirst();
    }

    public Optional<PaymentOrder> findByPaymentNo(String paymentNo) {
        var list = jdbcTemplate.query("SELECT * FROM rental_payment_order WHERE payment_no = ?", paymentMapper, paymentNo);
        return list.stream().findFirst();
    }

    public Optional<PaymentOrder> findActiveByBillId(Long billId) {
        var list = jdbcTemplate.query("""
            SELECT * FROM rental_payment_order
            WHERE bill_id = ? AND pay_status IN ('CREATED', 'PAYING', 'PAID')
            ORDER BY id DESC LIMIT 1
            """, paymentMapper, billId);
        return list.stream().findFirst();
    }

    public PaymentOrder createPayment(PaymentCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_payment_order
                (payment_no, bill_id, order_id, user_account_id, merchant_id, store_id, pay_channel,
                 pay_status, pay_amount, paid_amount, subject, payer_alipay_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.paymentNo());
            statement.setLong(2, row.billId());
            statement.setLong(3, row.orderId());
            setNullableLong(statement, 4, row.userAccountId());
            statement.setLong(5, row.merchantId());
            statement.setLong(6, row.storeId());
            statement.setString(7, row.payChannel().name());
            statement.setString(8, row.payStatus().name());
            statement.setBigDecimal(9, row.payAmount());
            statement.setBigDecimal(10, BigDecimal.ZERO);
            statement.setString(11, row.subject());
            statement.setString(12, row.payerAlipayUserId());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public PaymentOrder markPaying(Long id, String alipayTradeNo) {
        jdbcTemplate.update("""
            UPDATE rental_payment_order
            SET pay_status = 'PAYING', alipay_trade_no = ?, last_error = NULL
            WHERE id = ?
            """, alipayTradeNo, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrder markPaid(Long id, BigDecimal paidAmount, String alipayTradeNo) {
        jdbcTemplate.update("""
            UPDATE rental_payment_order
            SET pay_status = 'PAID',
                paid_amount = ?,
                alipay_trade_no = COALESCE(alipay_trade_no, ?),
                paid_at = COALESCE(paid_at, CURRENT_TIMESTAMP),
                last_error = NULL
            WHERE id = ?
            """, paidAmount, alipayTradeNo, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrder markFailed(Long id, String error) {
        jdbcTemplate.update("""
            UPDATE rental_payment_order
            SET pay_status = 'FAILED', last_error = ?
            WHERE id = ?
            """, error, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrder markRefunded(Long id, BigDecimal refundAmount) {
        jdbcTemplate.update("""
            UPDATE rental_payment_order
            SET pay_status = 'REFUNDED', refund_amount = refund_amount + ?
            WHERE id = ?
            """, refundAmount, id);
        return findById(id).orElseThrow();
    }

    public Optional<PaymentCallback> findCallbackByNotifyId(String notifyId) {
        if (notifyId == null || notifyId.isBlank()) {
            return Optional.empty();
        }
        var list = jdbcTemplate.query("SELECT * FROM rental_payment_callback WHERE notify_id = ?", callbackMapper, notifyId);
        return list.stream().findFirst();
    }

    public PaymentCallback createCallback(CallbackCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_payment_callback
                (payment_id, notify_id, out_trade_no, alipay_trade_no, trade_status, total_amount,
                 verified, processed, raw_payload, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            setNullableLong(statement, 1, row.paymentId());
            statement.setString(2, row.notifyId());
            statement.setString(3, row.outTradeNo());
            statement.setString(4, row.alipayTradeNo());
            statement.setString(5, row.tradeStatus());
            statement.setBigDecimal(6, row.totalAmount());
            statement.setBoolean(7, row.verified());
            statement.setBoolean(8, row.processed());
            statement.setString(9, row.rawPayload());
            statement.setString(10, row.failureReason());
            return statement;
        }, keyHolder);
        return findCallback(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentCallback> findCallback(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_payment_callback WHERE id = ?", callbackMapper, id);
        return list.stream().findFirst();
    }

    public List<PaymentCallback> listCallbacks() {
        return jdbcTemplate.query("SELECT * FROM rental_payment_callback ORDER BY id DESC", callbackMapper);
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

    public record PaymentCreateRow(
        String paymentNo,
        Long billId,
        Long orderId,
        Long userAccountId,
        Long merchantId,
        Long storeId,
        PayChannel payChannel,
        PayStatus payStatus,
        BigDecimal payAmount,
        String subject,
        String payerAlipayUserId
    ) {
    }

    public record CallbackCreateRow(
        Long paymentId,
        String notifyId,
        String outTradeNo,
        String alipayTradeNo,
        String tradeStatus,
        BigDecimal totalAmount,
        Boolean verified,
        Boolean processed,
        String rawPayload,
        String failureReason
    ) {
    }

    private static class PaymentMapper implements RowMapper<PaymentOrder> {
        @Override
        public PaymentOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PaymentOrder(
                rs.getLong("id"),
                rs.getString("payment_no"),
                rs.getLong("bill_id"),
                rs.getLong("order_id"),
                getNullableLong(rs, "user_account_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                PayChannel.valueOf(rs.getString("pay_channel")),
                PayStatus.valueOf(rs.getString("pay_status")),
                rs.getBigDecimal("pay_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("subject"),
                rs.getString("payer_alipay_user_id"),
                rs.getString("alipay_trade_no"),
                rs.getBigDecimal("refund_amount"),
                rs.getObject("paid_at", LocalDateTime.class),
                rs.getObject("closed_at", LocalDateTime.class),
                rs.getString("last_error"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class CallbackMapper implements RowMapper<PaymentCallback> {
        @Override
        public PaymentCallback mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PaymentCallback(
                rs.getLong("id"),
                getNullableLong(rs, "payment_id"),
                rs.getString("notify_id"),
                rs.getString("out_trade_no"),
                rs.getString("alipay_trade_no"),
                rs.getString("trade_status"),
                rs.getBigDecimal("total_amount"),
                rs.getBoolean("verified"),
                rs.getBoolean("processed"),
                rs.getString("raw_payload"),
                rs.getString("failure_reason"),
                rs.getObject("received_at", LocalDateTime.class)
            );
        }
    }
}
