package com.xniu.rental.ops.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.ops.dto.AuditLogResponse;
import com.xniu.rental.ops.dto.ExportTaskRequest;
import com.xniu.rental.ops.dto.ExportTaskResponse;
import com.xniu.rental.ops.dto.ReconciliationBatchResponse;
import com.xniu.rental.ops.dto.ReconciliationRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public OpsService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public void audit(HttpServletRequest request, int status, Exception exception) {
        var method = request.getMethod();
        if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            return;
        }
        var current = AuthContext.get();
        jdbcTemplate.update("""
            INSERT INTO audit_operation_log
            (account_id, account_type, request_method, request_uri, query_string, http_status,
             success, error_message, client_ip, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            current == null ? null : current.account().id(),
            current == null ? null : current.account().accountType(),
            method,
            request.getRequestURI(),
            request.getQueryString(),
            status,
            exception == null,
            exception == null ? null : trim(exception.getMessage()),
            clientIp(request),
            trim(request.getHeader("User-Agent"))
        );
    }

    public List<AuditLogResponse> listAudits(Long accountId, String uri) {
        authorizationService.requirePermission("system.admin");
        var sql = new StringBuilder("SELECT * FROM audit_operation_log WHERE 1 = 1");
        var params = new java.util.ArrayList<Object>();
        if (accountId != null) {
            sql.append(" AND account_id = ?");
            params.add(accountId);
        }
        if (uri != null && !uri.isBlank()) {
            sql.append(" AND request_uri LIKE ?");
            params.add("%" + uri.trim() + "%");
        }
        sql.append(" ORDER BY id DESC LIMIT 200");
        return jdbcTemplate.query(sql.toString(), new AuditMapper(), params.toArray());
    }

    @Transactional
    public ExportTaskResponse createExport(ExportTaskRequest request) {
        authorizationService.requirePermission("system.admin");
        var current = AuthContext.get();
        var taskNo = "EXP-" + UUID.randomUUID().toString().substring(0, 8);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO export_task
                (task_no, export_type, request_params, task_status, file_url, created_by, finished_at)
                VALUES (?, ?, ?, 'SUCCESS', ?, ?, CURRENT_TIMESTAMP)
                """, new String[] {"id"});
            statement.setString(1, taskNo);
            statement.setString(2, request.exportType());
            statement.setString(3, request.requestParams());
            statement.setString(4, "/exports/" + taskNo + ".csv");
            statement.setObject(5, current == null ? null : current.account().id());
            return statement;
        }, keyHolder);
        return findExport(keyHolder.getKey().longValue());
    }

    public List<ExportTaskResponse> listExports(String exportType) {
        authorizationService.requirePermission("system.admin");
        if (exportType == null || exportType.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM export_task ORDER BY id DESC LIMIT 100", new ExportMapper());
        }
        return jdbcTemplate.query("SELECT * FROM export_task WHERE export_type = ? ORDER BY id DESC LIMIT 100", new ExportMapper(), exportType);
    }

    @Transactional
    public ReconciliationBatchResponse createReconciliation(ReconciliationRequest request) {
        authorizationService.requirePermission("system.admin");
        var current = AuthContext.get();
        var totals = jdbcTemplate.queryForMap("""
            SELECT COALESCE(SUM(paid_amount), 0) AS total_amount, COUNT(1) AS total_count
            FROM payment_order
            WHERE pay_channel = ? AND pay_status = 'PAID' AND DATE(paid_at) = ?
            """, request.channel(), request.billDate());
        var platformTotal = (BigDecimal) totals.get("total_amount");
        var channelTotal = request.channelTotalAmount() == null ? platformTotal : request.channelTotalAmount();
        var diffAmount = platformTotal.subtract(channelTotal);
        var diffCount = diffAmount.signum() == 0 ? 0 : 1;
        var batchNo = "REC-" + UUID.randomUUID().toString().substring(0, 8);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO reconciliation_batch
                (batch_no, channel, bill_date, batch_status, platform_total_amount, channel_total_amount,
                 diff_count, remark, created_by, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, new String[] {"id"});
            statement.setString(1, batchNo);
            statement.setString(2, request.channel());
            statement.setObject(3, request.billDate());
            statement.setString(4, diffCount == 0 ? "SUCCESS" : "DIFF");
            statement.setBigDecimal(5, platformTotal);
            statement.setBigDecimal(6, channelTotal);
            statement.setInt(7, diffCount);
            statement.setString(8, request.remark());
            statement.setObject(9, current == null ? null : current.account().id());
            return statement;
        }, keyHolder);
        var batch = findReconciliation(keyHolder.getKey().longValue());
        if (diffCount > 0) {
            jdbcTemplate.update("""
                INSERT INTO reconciliation_diff
                (batch_id, diff_type, platform_amount, channel_amount, diff_amount, diff_status, remark)
                VALUES (?, 'TOTAL_AMOUNT_MISMATCH', ?, ?, ?, 'OPEN', ?)
                """, batch.id(), platformTotal, channelTotal, diffAmount, "平台支付金额与渠道账单金额不一致");
        }
        return batch;
    }

    public List<ReconciliationBatchResponse> listReconciliations(LocalDate billDate) {
        authorizationService.requirePermission("system.admin");
        if (billDate == null) {
            return jdbcTemplate.query("SELECT * FROM reconciliation_batch ORDER BY id DESC LIMIT 100", new ReconciliationMapper());
        }
        return jdbcTemplate.query("SELECT * FROM reconciliation_batch WHERE bill_date = ? ORDER BY id DESC LIMIT 100", new ReconciliationMapper(), billDate);
    }

    private ExportTaskResponse findExport(Long id) {
        return jdbcTemplate.query("SELECT * FROM export_task WHERE id = ?", new ExportMapper(), id).stream().findFirst().orElseThrow();
    }

    private ReconciliationBatchResponse findReconciliation(Long id) {
        return jdbcTemplate.query("SELECT * FROM reconciliation_batch WHERE id = ?", new ReconciliationMapper(), id).stream().findFirst().orElseThrow();
    }

    private String clientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static class AuditMapper implements RowMapper<AuditLogResponse> {
        @Override
        public AuditLogResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuditLogResponse(rs.getLong("id"), nullableLong(rs, "account_id"), rs.getString("account_type"), rs.getString("request_method"), rs.getString("request_uri"), rs.getString("query_string"), rs.getInt("http_status"), rs.getBoolean("success"), rs.getString("error_message"), rs.getString("client_ip"), rs.getString("user_agent"), rs.getObject("created_at", LocalDateTime.class));
        }
    }

    private static class ExportMapper implements RowMapper<ExportTaskResponse> {
        @Override
        public ExportTaskResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExportTaskResponse(rs.getLong("id"), rs.getString("task_no"), rs.getString("export_type"), rs.getString("request_params"), rs.getString("task_status"), rs.getString("file_url"), rs.getString("failure_reason"), nullableLong(rs, "created_by"), rs.getObject("created_at", LocalDateTime.class), rs.getObject("finished_at", LocalDateTime.class));
        }
    }

    private static class ReconciliationMapper implements RowMapper<ReconciliationBatchResponse> {
        @Override
        public ReconciliationBatchResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ReconciliationBatchResponse(rs.getLong("id"), rs.getString("batch_no"), rs.getString("channel"), rs.getObject("bill_date", LocalDate.class), rs.getString("batch_status"), rs.getBigDecimal("platform_total_amount"), rs.getBigDecimal("channel_total_amount"), rs.getInt("diff_count"), rs.getString("remark"), nullableLong(rs, "created_by"), rs.getObject("created_at", LocalDateTime.class), rs.getObject("finished_at", LocalDateTime.class));
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
