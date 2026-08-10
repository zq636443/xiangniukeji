package com.xniu.rental.settlement.repository;

import com.xniu.rental.settlement.model.SettlementStatement;
import com.xniu.rental.settlement.model.SettlementStatementLine;
import com.xniu.rental.settlement.model.SettlementStatementLineType;
import com.xniu.rental.settlement.model.SettlementStatementStatus;
import com.xniu.rental.settlement.model.StatementBeneficiaryType;
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
public class SettlementStatementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SettlementStatement> statementMapper = new StatementMapper();
    private final RowMapper<SettlementStatementLine> lineMapper = new LineMapper();

    public SettlementStatementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasLockedStatements(String statementMonth) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement
            WHERE statement_month = ?
              AND status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
            """, Integer.class, statementMonth);
        return count != null && count > 0;
    }

    public void deleteDraftStatements(String statementMonth) {
        jdbcTemplate.update("""
            DELETE l
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE s.statement_month = ?
              AND s.status IN ('DRAFT', 'RECONCILING')
            """, statementMonth);
        jdbcTemplate.update("""
            DELETE FROM settlement_statement
            WHERE statement_month = ?
              AND status IN ('DRAFT', 'RECONCILING')
            """, statementMonth);
    }

    public SettlementStatement createStatement(CreateStatementRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_statement
                (statement_no, statement_month, beneficiary_type, beneficiary_id, merchant_id, store_id,
                 rent_base_amount, sign_fee_income_amount, rent_share_income_amount, operation_fee_amount,
                 battery_cost_amount, maintenance_deduct_amount, adjustment_amount, payable_amount, order_count, bill_count,
                 status, generated_at, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, new String[] {"id"});
            statement.setString(1, row.statementNo());
            statement.setString(2, row.statementMonth());
            statement.setString(3, row.beneficiaryType().name());
            statement.setLong(4, row.beneficiaryId());
            statement.setLong(5, row.merchantId());
            statement.setLong(6, row.storeId());
            statement.setBigDecimal(7, row.rentBaseAmount());
            statement.setBigDecimal(8, row.signFeeIncomeAmount());
            statement.setBigDecimal(9, row.rentShareIncomeAmount());
            statement.setBigDecimal(10, row.operationFeeAmount());
            statement.setBigDecimal(11, row.batteryCostAmount());
            statement.setBigDecimal(12, row.maintenanceDeductAmount());
            statement.setBigDecimal(13, row.adjustmentAmount());
            statement.setBigDecimal(14, row.payableAmount());
            statement.setInt(15, row.orderCount());
            statement.setInt(16, row.billCount());
            statement.setString(17, row.status().name());
            statement.setString(18, row.remark());
            return statement;
        }, keyHolder);
        return findStatement(keyHolder.getKey().longValue()).orElseThrow();
    }

    public SettlementStatementLine createLine(CreateLineRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO settlement_statement_line
                (statement_id, line_no, source_type, source_id, order_id, bill_id, asset_id,
                 merchant_id, store_id, investor_id, line_type, amount, occurred_at, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, row.statementId());
            statement.setString(2, row.lineNo());
            statement.setString(3, row.sourceType());
            statement.setLong(4, row.sourceId());
            setNullableLong(statement, 5, row.orderId());
            setNullableLong(statement, 6, row.billId());
            setNullableLong(statement, 7, row.assetId());
            statement.setLong(8, row.merchantId());
            statement.setLong(9, row.storeId());
            statement.setLong(10, row.investorId());
            statement.setString(11, row.lineType().name());
            statement.setBigDecimal(12, row.amount());
            statement.setObject(13, row.occurredAt());
            statement.setString(14, row.remark());
            return statement;
        }, keyHolder);
        return findLine(keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<SettlementStatement> listStatements(
        String statementMonth,
        StatementBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        SettlementStatementStatus status,
        Long merchantId,
        Long storeId
    ) {
        var sql = new StringBuilder("SELECT * FROM settlement_statement WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (statementMonth != null && !statementMonth.isBlank()) {
            sql.append(" AND statement_month = ?");
            params.add(statementMonth);
        }
        if (beneficiaryType != null) {
            sql.append(" AND beneficiary_type = ?");
            params.add(beneficiaryType.name());
        }
        if (beneficiaryId != null) {
            sql.append(" AND beneficiary_id = ?");
            params.add(beneficiaryId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY statement_month DESC, beneficiary_type, merchant_id, store_id, beneficiary_id");
        return jdbcTemplate.query(sql.toString(), statementMapper, params.toArray());
    }

    public List<StoreProfitOverviewRow> listStoreProfitOverview(String statementMonth, Long merchantId, Long storeId) {
        var sql = new StringBuilder("""
            SELECT
              s.id AS statement_id,
              s.statement_no,
              s.statement_month,
              s.merchant_id,
              s.store_id,
              s.rent_base_amount,
              s.sign_fee_income_amount,
              COALESCE(line_total.store_operation_amount, 0) AS store_operation_amount,
              COALESCE(line_total.store_maintenance_amount, 0) AS store_maintenance_amount,
              s.battery_cost_amount,
              COALESCE(line_total.maintenance_reimburse_amount, 0) AS maintenance_reimburse_amount,
              s.maintenance_deduct_amount,
              s.adjustment_amount,
              s.payable_amount,
              s.order_count,
              s.bill_count,
              COALESCE(line_total.line_count, 0) AS line_count,
              s.status,
              s.generated_at,
              s.confirmed_at,
              s.paid_at
            FROM settlement_statement s
            LEFT JOIN (
              SELECT
                statement_id,
                SUM(CASE WHEN line_type = 'MERCHANT_RENT_SHARE' THEN amount ELSE 0 END) AS store_operation_amount,
                SUM(CASE WHEN line_type = 'MERCHANT_MAINTENANCE_SHARE' THEN amount ELSE 0 END) AS store_maintenance_amount,
                SUM(CASE WHEN line_type = 'MERCHANT_MAINTENANCE_REIMBURSE' THEN amount ELSE 0 END) AS maintenance_reimburse_amount,
                COUNT(1) AS line_count
              FROM settlement_statement_line
              GROUP BY statement_id
            ) line_total ON line_total.statement_id = s.id
            WHERE s.statement_month = ?
              AND s.beneficiary_type = 'MERCHANT'
            """);
        var params = new ArrayList<Object>();
        params.add(statementMonth);
        if (merchantId != null) {
            sql.append(" AND s.merchant_id = ?");
            params.add(merchantId);
        }
        if (storeId != null) {
            sql.append(" AND s.store_id = ?");
            params.add(storeId);
        }
        sql.append(" ORDER BY s.payable_amount DESC, s.store_id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new StoreProfitOverviewRow(
            rs.getLong("statement_id"),
            rs.getString("statement_no"),
            rs.getString("statement_month"),
            rs.getLong("merchant_id"),
            rs.getLong("store_id"),
            rs.getBigDecimal("rent_base_amount"),
            rs.getBigDecimal("sign_fee_income_amount"),
            rs.getBigDecimal("store_operation_amount"),
            rs.getBigDecimal("store_maintenance_amount"),
            rs.getBigDecimal("battery_cost_amount"),
            rs.getBigDecimal("maintenance_reimburse_amount"),
            rs.getBigDecimal("maintenance_deduct_amount"),
            rs.getBigDecimal("adjustment_amount"),
            rs.getBigDecimal("payable_amount"),
            rs.getInt("order_count"),
            rs.getInt("bill_count"),
            rs.getInt("line_count"),
            rs.getString("status"),
            rs.getObject("generated_at", LocalDateTime.class),
            rs.getObject("confirmed_at", LocalDateTime.class),
            rs.getObject("paid_at", LocalDateTime.class)
        ), params.toArray());
    }

    public Optional<SettlementStatement> findStatement(Long id) {
        return jdbcTemplate.query("SELECT * FROM settlement_statement WHERE id = ?", statementMapper, id).stream().findFirst();
    }

    public Optional<SettlementStatementLine> findLine(Long id) {
        return jdbcTemplate.query("SELECT * FROM settlement_statement_line WHERE id = ?", lineMapper, id).stream().findFirst();
    }

    public boolean hasLinesBySource(String sourceType, Long sourceId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line
            WHERE source_type = ? AND source_id = ?
            """, Integer.class, sourceType, sourceId);
        return count != null && count > 0;
    }

    public boolean hasLockedLinesBySource(String sourceType, Long sourceId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = ?
              AND l.source_id = ?
              AND s.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
            """, Integer.class, sourceType, sourceId);
        return count != null && count > 0;
    }

    public List<String> listDraftStatementMonthsBySource(String sourceType, Long sourceId) {
        return jdbcTemplate.queryForList("""
            SELECT DISTINCT s.statement_month
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = ?
              AND l.source_id = ?
              AND s.status IN ('DRAFT', 'RECONCILING')
            ORDER BY s.statement_month
            """, String.class, sourceType, sourceId);
    }

    public List<SettlementStatementLine> listLines(Long statementId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM settlement_statement_line
            WHERE statement_id = ?
            ORDER BY occurred_at ASC, id ASC
            """, lineMapper, statementId);
    }

    public int countLines(Long statementId) {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM settlement_statement_line WHERE statement_id = ?", Integer.class, statementId);
        return count == null ? 0 : count;
    }

    public SettlementStatement updateStatementStatus(Long id, SettlementStatementStatus status) {
        jdbcTemplate.update("""
            UPDATE settlement_statement
            SET status = ?,
                confirmed_at = CASE WHEN ? = 'CONFIRMED' THEN COALESCE(confirmed_at, CURRENT_TIMESTAMP) ELSE confirmed_at END,
                paid_at = CASE WHEN ? = 'PAID' THEN COALESCE(paid_at, CURRENT_TIMESTAMP) ELSE paid_at END
            WHERE id = ?
            """, status.name(), status.name(), status.name(), id);
        return findStatement(id).orElseThrow();
    }

    public OverviewRow overview(String statementMonth) {
        var sums = jdbcTemplate.queryForObject("""
            SELECT
              COALESCE(SUM(CASE WHEN beneficiary_type = 'MERCHANT' THEN payable_amount ELSE 0 END), 0) AS merchant_payable,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'INVESTOR' THEN payable_amount ELSE 0 END), 0) AS investor_payable,
              COALESCE(SUM(operation_fee_amount), 0) AS operation_fee,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'MERCHANT' THEN battery_cost_amount ELSE 0 END), 0) AS battery_cost,
              COALESCE(SUM(maintenance_deduct_amount), 0) AS maintenance_deduct,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'MERCHANT' THEN sign_fee_income_amount ELSE 0 END), 0) AS sign_fee_total,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'MERCHANT' THEN rent_base_amount ELSE 0 END), 0) AS rent_base_total,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'MERCHANT' THEN 1 ELSE 0 END), 0) AS merchant_statement_count,
              COALESCE(SUM(CASE WHEN beneficiary_type = 'INVESTOR' THEN 1 ELSE 0 END), 0) AS investor_statement_count
            FROM settlement_statement
            WHERE statement_month = ?
            """, (ResultSet rs, int rowNum) -> new OverviewRow(
            rs.getBigDecimal("merchant_payable"),
            rs.getBigDecimal("investor_payable"),
            rs.getBigDecimal("operation_fee"),
            rs.getBigDecimal("battery_cost"),
            rs.getBigDecimal("maintenance_deduct"),
            rs.getBigDecimal("sign_fee_total"),
            rs.getBigDecimal("rent_base_total"),
            BigDecimal.ZERO,
            rs.getInt("merchant_statement_count"),
            rs.getInt("investor_statement_count")
        ), statementMonth);
        var overdueAmount = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(unpaid_amount), 0)
            FROM rental_overdue_case
            WHERE stat_month = ?
              AND overdue_status = 'OPEN'
            """, BigDecimal.class, statementMonth);
        return new OverviewRow(
            sums == null ? BigDecimal.ZERO : sums.merchantPayableAmount(),
            sums == null ? BigDecimal.ZERO : sums.investorPayableAmount(),
            sums == null ? BigDecimal.ZERO : sums.operationFeeAmount(),
            sums == null ? BigDecimal.ZERO : sums.batteryCostAmount(),
            sums == null ? BigDecimal.ZERO : sums.maintenanceDeductAmount(),
            sums == null ? BigDecimal.ZERO : sums.signFeeIncomeAmount(),
            sums == null ? BigDecimal.ZERO : sums.rentBaseAmount(),
            overdueAmount == null ? BigDecimal.ZERO : overdueAmount,
            sums == null ? 0 : sums.merchantStatementCount(),
            sums == null ? 0 : sums.investorStatementCount()
        );
    }

    public List<PaidBillItemRow> listPaidBillItems(LocalDateTime startAt, LocalDateTime endAt) {
        return jdbcTemplate.query("""
            SELECT
              b.id AS bill_id,
              b.order_id,
              b.merchant_id,
              b.store_id,
              b.paid_at,
              o.settlement_snapshot_id,
              i.item_type,
              i.amount
            FROM rental_bill b
            JOIN rental_order o ON o.id = b.order_id
            JOIN rental_bill_item i ON i.bill_id = b.id
            WHERE b.bill_status = 'PAID'
              AND b.paid_at >= ?
              AND b.paid_at < ?
            ORDER BY b.paid_at, b.id, i.id
            """, (rs, rowNum) -> new PaidBillItemRow(
            rs.getLong("bill_id"),
            rs.getLong("order_id"),
            rs.getLong("merchant_id"),
            rs.getLong("store_id"),
            rs.getObject("paid_at", LocalDateTime.class),
            getNullableLong(rs, "settlement_snapshot_id"),
            rs.getString("item_type"),
            rs.getBigDecimal("amount")
        ), startAt, endAt);
    }

    public List<ExternalOrderItemRow> listExternalOrderItems(LocalDateTime startAt, LocalDateTime endAt) {
        return jdbcTemplate.query("""
            SELECT
              eo.id AS external_order_id,
              eo.record_no,
              eo.source_platform,
              eo.merchant_id,
              eo.store_id,
              eo.frame_asset_id,
              eo.battery_asset_id,
              eo.verification_amount,
              eo.sign_fee_amount,
              eo.settlement_snapshot_id,
              eo.created_at
            FROM external_rental_order eo
            WHERE eo.settlement_snapshot_id IS NOT NULL
              AND eo.order_status <> 'TERMINATED'
              AND eo.created_at >= ?
              AND eo.created_at < ?
            ORDER BY eo.created_at, eo.id
            """, (rs, rowNum) -> new ExternalOrderItemRow(
            rs.getLong("external_order_id"),
            rs.getString("record_no"),
            rs.getString("source_platform"),
            rs.getLong("merchant_id"),
            rs.getLong("store_id"),
            getNullableLong(rs, "frame_asset_id"),
            getNullableLong(rs, "battery_asset_id"),
            rs.getBigDecimal("verification_amount"),
            rs.getBigDecimal("sign_fee_amount"),
            rs.getLong("settlement_snapshot_id"),
            rs.getObject("created_at", LocalDateTime.class)
        ), startAt, endAt);
    }

    public List<MaintenanceCostRow> listMaintenanceCosts(LocalDateTime startAt, LocalDateTime endAt) {
        return jdbcTemplate.query("""
            SELECT
              r.id AS maintenance_id,
              r.maintenance_no,
              r.asset_id,
              a.investor_id,
              a.current_merchant_id,
              a.current_store_id,
              r.responsibility_type,
              r.merchant_reimbursement_amount,
              r.investor_deduct_amount,
              r.customer_charge_amount,
              r.cost_bearer_type,
              r.cost_bearer_id,
              r.total_cost,
              COALESCE(r.completed_at, r.created_at) AS occurred_at
            FROM asset_maintenance_record r
            JOIN asset_item a ON a.id = r.asset_id
            WHERE COALESCE(r.completed_at, r.created_at) >= ?
              AND COALESCE(r.completed_at, r.created_at) < ?
            ORDER BY occurred_at, r.id
            """, (rs, rowNum) -> new MaintenanceCostRow(
            rs.getLong("maintenance_id"),
            rs.getString("maintenance_no"),
            rs.getLong("asset_id"),
            rs.getLong("investor_id"),
            getNullableLong(rs, "current_merchant_id"),
            getNullableLong(rs, "current_store_id"),
            rs.getString("responsibility_type"),
            rs.getBigDecimal("merchant_reimbursement_amount"),
            rs.getBigDecimal("investor_deduct_amount"),
            rs.getBigDecimal("customer_charge_amount"),
            rs.getString("cost_bearer_type"),
            getNullableLong(rs, "cost_bearer_id"),
            rs.getBigDecimal("total_cost"),
            rs.getObject("occurred_at", LocalDateTime.class)
        ), startAt, endAt);
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

    public record CreateStatementRow(
        String statementNo,
        String statementMonth,
        StatementBeneficiaryType beneficiaryType,
        Long beneficiaryId,
        Long merchantId,
        Long storeId,
        BigDecimal rentBaseAmount,
        BigDecimal signFeeIncomeAmount,
        BigDecimal rentShareIncomeAmount,
        BigDecimal operationFeeAmount,
        BigDecimal batteryCostAmount,
        BigDecimal maintenanceDeductAmount,
        BigDecimal adjustmentAmount,
        BigDecimal payableAmount,
        Integer orderCount,
        Integer billCount,
        SettlementStatementStatus status,
        String remark
    ) {
    }

    public record CreateLineRow(
        Long statementId,
        String lineNo,
        String sourceType,
        Long sourceId,
        Long orderId,
        Long billId,
        Long assetId,
        Long merchantId,
        Long storeId,
        Long investorId,
        SettlementStatementLineType lineType,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String remark
    ) {
    }

    public record PaidBillItemRow(
        Long billId,
        Long orderId,
        Long merchantId,
        Long storeId,
        LocalDateTime paidAt,
        Long settlementSnapshotId,
        String itemType,
        BigDecimal amount
    ) {
    }

    public record ExternalOrderItemRow(
        Long externalOrderId,
        String recordNo,
        String sourcePlatform,
        Long merchantId,
        Long storeId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        Long settlementSnapshotId,
        LocalDateTime createdAt
    ) {
    }

    public record MaintenanceCostRow(
        Long maintenanceId,
        String maintenanceNo,
        Long assetId,
        Long investorId,
        Long merchantId,
        Long storeId,
        String responsibilityType,
        BigDecimal merchantReimbursementAmount,
        BigDecimal investorDeductAmount,
        BigDecimal customerChargeAmount,
        String costBearerType,
        Long costBearerId,
        BigDecimal totalCost,
        LocalDateTime occurredAt
    ) {
    }

    public record StoreProfitOverviewRow(
        Long statementId,
        String statementNo,
        String statementMonth,
        Long merchantId,
        Long storeId,
        BigDecimal settlementBaseAmount,
        BigDecimal signFeeAmount,
        BigDecimal storeOperationAmount,
        BigDecimal storeMaintenanceAmount,
        BigDecimal batteryCostAmount,
        BigDecimal maintenanceReimburseAmount,
        BigDecimal maintenanceDeductAmount,
        BigDecimal adjustmentAmount,
        BigDecimal payableAmount,
        Integer orderCount,
        Integer billCount,
        Integer lineCount,
        String status,
        LocalDateTime generatedAt,
        LocalDateTime confirmedAt,
        LocalDateTime paidAt
    ) {
    }

    public record OverviewRow(
        BigDecimal merchantPayableAmount,
        BigDecimal investorPayableAmount,
        BigDecimal operationFeeAmount,
        BigDecimal batteryCostAmount,
        BigDecimal maintenanceDeductAmount,
        BigDecimal signFeeIncomeAmount,
        BigDecimal rentBaseAmount,
        BigDecimal overdueAmount,
        Integer merchantStatementCount,
        Integer investorStatementCount
    ) {
    }

    private static class StatementMapper implements RowMapper<SettlementStatement> {
        @Override
        public SettlementStatement mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementStatement(
                rs.getLong("id"),
                rs.getString("statement_no"),
                rs.getString("statement_month"),
                StatementBeneficiaryType.valueOf(rs.getString("beneficiary_type")),
                rs.getLong("beneficiary_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getBigDecimal("rent_base_amount"),
                rs.getBigDecimal("sign_fee_income_amount"),
                rs.getBigDecimal("rent_share_income_amount"),
                rs.getBigDecimal("operation_fee_amount"),
                rs.getBigDecimal("battery_cost_amount"),
                rs.getBigDecimal("maintenance_deduct_amount"),
                rs.getBigDecimal("adjustment_amount"),
                rs.getBigDecimal("payable_amount"),
                rs.getInt("order_count"),
                rs.getInt("bill_count"),
                SettlementStatementStatus.valueOf(rs.getString("status")),
                rs.getObject("generated_at", LocalDateTime.class),
                rs.getObject("confirmed_at", LocalDateTime.class),
                rs.getObject("paid_at", LocalDateTime.class),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
            );
        }
    }

    private static class LineMapper implements RowMapper<SettlementStatementLine> {
        @Override
        public SettlementStatementLine mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SettlementStatementLine(
                rs.getLong("id"),
                rs.getLong("statement_id"),
                rs.getString("line_no"),
                rs.getString("source_type"),
                rs.getLong("source_id"),
                getNullableLong(rs, "order_id"),
                getNullableLong(rs, "bill_id"),
                getNullableLong(rs, "asset_id"),
                rs.getLong("merchant_id"),
                rs.getLong("store_id"),
                rs.getLong("investor_id"),
                SettlementStatementLineType.valueOf(rs.getString("line_type")),
                rs.getBigDecimal("amount"),
                rs.getObject("occurred_at", LocalDateTime.class),
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }
    }
}
