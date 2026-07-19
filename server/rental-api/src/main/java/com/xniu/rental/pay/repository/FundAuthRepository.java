package com.xniu.rental.pay.repository;

import com.xniu.rental.pay.model.FundAuthNotify;
import com.xniu.rental.pay.model.FundAuthOperation;
import com.xniu.rental.pay.model.FundAuthOperationStatus;
import com.xniu.rental.pay.model.FundAuthOperationType;
import com.xniu.rental.pay.model.FundAuthOrder;
import com.xniu.rental.pay.model.FundAuthStatus;
import com.xniu.rental.pay.model.FundAuthType;
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
public class FundAuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<FundAuthOrder> authMapper = new AuthMapper();
    private final RowMapper<FundAuthOperation> operationMapper = new OperationMapper();
    private final RowMapper<FundAuthNotify> notifyMapper = new NotifyMapper();

    public FundAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FundAuthOrder> listAuthOrders(FundAuthStatus status, Long orderId, Long userAccountId) {
        var sql = new StringBuilder("SELECT * FROM rental_fund_auth_order WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND auth_status = ?");
            params.add(status.name());
        }
        if (orderId != null) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (userAccountId != null) {
            sql.append(" AND user_account_id = ?");
            params.add(userAccountId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), authMapper, params.toArray());
    }

    public Optional<FundAuthOrder> findById(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_fund_auth_order WHERE id = ?", authMapper, id);
        return list.stream().findFirst();
    }

    public Optional<FundAuthOrder> findByAuthOrderNo(String authOrderNo) {
        var list = jdbcTemplate.query("SELECT * FROM rental_fund_auth_order WHERE auth_order_no = ?", authMapper, authOrderNo);
        return list.stream().findFirst();
    }

    public Optional<FundAuthOrder> findActiveByOrderId(Long orderId) {
        var list = jdbcTemplate.query("""
            SELECT * FROM rental_fund_auth_order
            WHERE order_id = ? AND auth_status IN ('CREATED', 'AUTHORIZING', 'AUTHORIZED')
            ORDER BY id DESC LIMIT 1
            """, authMapper, orderId);
        return list.stream().findFirst();
    }

    public FundAuthOrder createAuthOrder(AuthCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_fund_auth_order
                (auth_order_no, order_id, user_account_id, alipay_user_id, merchant_id, store_id,
                 auth_type, auth_status, auth_amount, frozen_amount, captured_amount, released_amount,
                 out_request_no, subject)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, 0.00, 0.00, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.authOrderNo());
            statement.setLong(2, row.orderId());
            statement.setLong(3, row.userAccountId());
            statement.setString(4, row.alipayUserId());
            statement.setLong(5, row.merchantId());
            statement.setLong(6, row.storeId());
            statement.setString(7, row.authType().name());
            statement.setString(8, row.authStatus().name());
            statement.setBigDecimal(9, row.authAmount());
            statement.setString(10, row.outRequestNo());
            statement.setString(11, row.subject());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public FundAuthOrder markAuthorizing(Long id, String orderStr) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET auth_status = 'AUTHORIZING', order_str = ?, last_error = NULL
            WHERE id = ?
            """, orderStr, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOrder markAuthorized(Long id, String authNo, String operationId, BigDecimal frozenAmount) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET auth_status = 'AUTHORIZED',
                alipay_auth_no = COALESCE(alipay_auth_no, ?),
                alipay_operation_id = COALESCE(alipay_operation_id, ?),
                frozen_amount = ?,
                authorized_at = COALESCE(authorized_at, CURRENT_TIMESTAMP),
                last_error = NULL
            WHERE id = ?
            """, authNo, operationId, frozenAmount, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOrder markFailed(Long id, String error) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET auth_status = 'FAILED', last_error = ?
            WHERE id = ?
            """, error, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOrder addCaptured(Long id, BigDecimal amount) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET captured_amount = captured_amount + ?,
                auth_status = CASE
                  WHEN frozen_amount <= captured_amount + ? + released_amount THEN 'CAPTURED'
                  ELSE auth_status
                END
            WHERE id = ?
            """, amount, amount, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOrder addReleased(Long id, BigDecimal amount) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET released_amount = released_amount + ?,
                auth_status = CASE
                  WHEN frozen_amount <= captured_amount + released_amount + ? THEN 'UNFROZEN'
                  ELSE auth_status
                END,
                closed_at = CASE
                  WHEN frozen_amount <= captured_amount + released_amount + ? THEN COALESCE(closed_at, CURRENT_TIMESTAMP)
                  ELSE closed_at
                END
            WHERE id = ?
            """, amount, amount, amount, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOrder markCancelled(Long id) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_order
            SET auth_status = 'CANCELLED', closed_at = COALESCE(closed_at, CURRENT_TIMESTAMP)
            WHERE id = ?
            """, id);
        return findById(id).orElseThrow();
    }

    public FundAuthOperation createOperation(OperationCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_fund_auth_operation
                (operation_no, auth_order_id, bill_id, payment_id, operation_type, operation_status,
                 amount, out_request_no, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.operationNo());
            statement.setLong(2, row.authOrderId());
            setNullableLong(statement, 3, row.billId());
            setNullableLong(statement, 4, row.paymentId());
            statement.setString(5, row.operationType().name());
            statement.setString(6, row.operationStatus().name());
            statement.setBigDecimal(7, row.amount());
            statement.setString(8, row.outRequestNo());
            statement.setString(9, row.remark());
            return statement;
        }, keyHolder);
        return findOperation(keyHolder.getKey().longValue()).orElseThrow();
    }

    public FundAuthOperation markOperationSuccess(Long id, String alipayTradeNo, String operationId, Long paymentId) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_operation
            SET operation_status = 'SUCCESS',
                alipay_trade_no = COALESCE(alipay_trade_no, ?),
                alipay_operation_id = COALESCE(alipay_operation_id, ?),
                payment_id = COALESCE(payment_id, ?),
                failure_reason = NULL
            WHERE id = ?
            """, alipayTradeNo, operationId, paymentId, id);
        return findOperation(id).orElseThrow();
    }

    public FundAuthOperation markOperationFailed(Long id, String failureReason) {
        jdbcTemplate.update("""
            UPDATE rental_fund_auth_operation
            SET operation_status = 'FAILED', failure_reason = ?
            WHERE id = ?
            """, failureReason, id);
        return findOperation(id).orElseThrow();
    }

    public Optional<FundAuthOperation> findOperation(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_fund_auth_operation WHERE id = ?", operationMapper, id);
        return list.stream().findFirst();
    }

    public List<FundAuthOperation> listOperations(Long authOrderId) {
        return jdbcTemplate.query("SELECT * FROM rental_fund_auth_operation WHERE auth_order_id = ? ORDER BY id DESC", operationMapper, authOrderId);
    }

    public Optional<FundAuthNotify> findNotifyByNotifyId(String notifyId) {
        if (notifyId == null || notifyId.isBlank()) {
            return Optional.empty();
        }
        var list = jdbcTemplate.query("SELECT * FROM rental_fund_auth_notify WHERE notify_id = ?", notifyMapper, notifyId);
        return list.stream().findFirst();
    }

    public FundAuthNotify createNotify(NotifyCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_fund_auth_notify
                (auth_order_id, notify_id, out_order_no, out_request_no, auth_no, operation_id,
                 auth_status, total_freeze_amount, rest_amount, verified, processed, raw_payload, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            setNullableLong(statement, 1, row.authOrderId());
            statement.setString(2, row.notifyId());
            statement.setString(3, row.outOrderNo());
            statement.setString(4, row.outRequestNo());
            statement.setString(5, row.authNo());
            statement.setString(6, row.operationId());
            statement.setString(7, row.authStatus());
            statement.setBigDecimal(8, row.totalFreezeAmount());
            statement.setBigDecimal(9, row.restAmount());
            statement.setBoolean(10, row.verified());
            statement.setBoolean(11, row.processed());
            statement.setString(12, row.rawPayload());
            statement.setString(13, row.failureReason());
            return statement;
        }, keyHolder);
        return findNotify(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<FundAuthNotify> findNotify(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_fund_auth_notify WHERE id = ?", notifyMapper, id);
        return list.stream().findFirst();
    }

    public List<FundAuthNotify> listNotifies() {
        return jdbcTemplate.query("SELECT * FROM rental_fund_auth_notify ORDER BY id DESC", notifyMapper);
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

    public record AuthCreateRow(
        String authOrderNo,
        Long orderId,
        Long userAccountId,
        String alipayUserId,
        Long merchantId,
        Long storeId,
        FundAuthType authType,
        FundAuthStatus authStatus,
        BigDecimal authAmount,
        String outRequestNo,
        String subject
    ) {
    }

    public record OperationCreateRow(
        String operationNo,
        Long authOrderId,
        Long billId,
        Long paymentId,
        FundAuthOperationType operationType,
        FundAuthOperationStatus operationStatus,
        BigDecimal amount,
        String outRequestNo,
        String remark
    ) {
    }

    public record NotifyCreateRow(
        Long authOrderId,
        String notifyId,
        String outOrderNo,
        String outRequestNo,
        String authNo,
        String operationId,
        String authStatus,
        BigDecimal totalFreezeAmount,
        BigDecimal restAmount,
        Boolean verified,
        Boolean processed,
        String rawPayload,
        String failureReason
    ) {
    }

    private static class AuthMapper implements RowMapper<FundAuthOrder> {
        @Override
        public FundAuthOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FundAuthOrder(
                rs.getLong("id"),
                rs.getString("auth_order_no"),
                rs.getLong("order_id"),
                rs.getLong("user_account_id"),
                rs.getString("alipay_user_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                FundAuthType.valueOf(rs.getString("auth_type")),
                FundAuthStatus.valueOf(rs.getString("auth_status")),
                rs.getBigDecimal("auth_amount"),
                rs.getBigDecimal("frozen_amount"),
                rs.getBigDecimal("captured_amount"),
                rs.getBigDecimal("released_amount"),
                rs.getString("out_request_no"),
                rs.getString("alipay_auth_no"),
                rs.getString("alipay_operation_id"),
                rs.getString("order_str"),
                rs.getString("subject"),
                rs.getString("last_error"),
                rs.getObject("authorized_at", LocalDateTime.class),
                rs.getObject("closed_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class OperationMapper implements RowMapper<FundAuthOperation> {
        @Override
        public FundAuthOperation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FundAuthOperation(
                rs.getLong("id"),
                rs.getString("operation_no"),
                rs.getLong("auth_order_id"),
                getNullableLong(rs, "bill_id"),
                getNullableLong(rs, "payment_id"),
                FundAuthOperationType.valueOf(rs.getString("operation_type")),
                FundAuthOperationStatus.valueOf(rs.getString("operation_status")),
                rs.getBigDecimal("amount"),
                rs.getString("out_request_no"),
                rs.getString("alipay_trade_no"),
                rs.getString("alipay_operation_id"),
                rs.getString("remark"),
                rs.getString("failure_reason"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class NotifyMapper implements RowMapper<FundAuthNotify> {
        @Override
        public FundAuthNotify mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FundAuthNotify(
                rs.getLong("id"),
                getNullableLong(rs, "auth_order_id"),
                rs.getString("notify_id"),
                rs.getString("out_order_no"),
                rs.getString("out_request_no"),
                rs.getString("auth_no"),
                rs.getString("operation_id"),
                rs.getString("auth_status"),
                rs.getBigDecimal("total_freeze_amount"),
                rs.getBigDecimal("rest_amount"),
                rs.getBoolean("verified"),
                rs.getBoolean("processed"),
                rs.getString("raw_payload"),
                rs.getString("failure_reason"),
                rs.getObject("received_at", LocalDateTime.class)
            );
        }
    }
}
