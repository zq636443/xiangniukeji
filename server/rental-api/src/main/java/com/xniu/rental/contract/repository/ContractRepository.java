package com.xniu.rental.contract.repository;

import com.xniu.rental.contract.model.ContractNotify;
import com.xniu.rental.contract.model.ContractStatus;
import com.xniu.rental.contract.model.ContractTemplate;
import com.xniu.rental.contract.model.ContractTemplateStatus;
import com.xniu.rental.contract.model.ContractType;
import com.xniu.rental.contract.model.RentalContract;
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
public class ContractRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ContractTemplate> templateMapper = new TemplateMapper();
    private final RowMapper<RentalContract> contractMapper = new ContractMapper();
    private final RowMapper<ContractNotify> notifyMapper = new NotifyMapper();

    public ContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ContractTemplate> listTemplates(ContractType type, ContractTemplateStatus status) {
        var sql = new StringBuilder("SELECT * FROM contract_template WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (type != null) {
            sql.append(" AND contract_type = ?");
            params.add(type.name());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), templateMapper, params.toArray());
    }

    public Optional<ContractTemplate> findTemplate(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM contract_template WHERE id = ?", templateMapper, id);
        return list.stream().findFirst();
    }

    public Optional<ContractTemplate> findEnabledTemplate(ContractType type) {
        var list = jdbcTemplate.query("""
            SELECT * FROM contract_template
            WHERE contract_type = ? AND status = 'ENABLED'
            ORDER BY id DESC LIMIT 1
            """, templateMapper, type.name());
        return list.stream().findFirst();
    }

    public ContractTemplate createTemplate(TemplateCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO contract_template
                (template_code, template_name, contract_type, version_no, provider_template_id, content, status, remark)
                VALUES (?, ?, ?, ?, ?, ?, 'ENABLED', ?)
                """, new String[] {"id"});
            statement.setString(1, row.templateCode());
            statement.setString(2, row.templateName());
            statement.setString(3, row.contractType().name());
            statement.setString(4, row.versionNo());
            statement.setString(5, row.providerTemplateId());
            statement.setString(6, row.content());
            statement.setString(7, row.remark());
            return statement;
        }, keyHolder);
        return findTemplate(keyHolder.getKey().longValue()).orElseThrow();
    }

    public ContractTemplate updateTemplateStatus(Long id, ContractTemplateStatus status) {
        jdbcTemplate.update("UPDATE contract_template SET status = ? WHERE id = ?", status.name(), id);
        return findTemplate(id).orElseThrow();
    }

    public List<RentalContract> listContracts(ContractStatus status, Long orderId, Long userAccountId) {
        var sql = new StringBuilder("SELECT * FROM rental_contract WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            sql.append(" AND contract_status = ?");
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
        return jdbcTemplate.query(sql.toString(), contractMapper, params.toArray());
    }

    public Optional<RentalContract> findContract(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM rental_contract WHERE id = ?", contractMapper, id);
        return list.stream().findFirst();
    }

    public Optional<RentalContract> findByExternalFlowId(String externalFlowId) {
        var list = jdbcTemplate.query("SELECT * FROM rental_contract WHERE external_flow_id = ?", contractMapper, externalFlowId);
        return list.stream().findFirst();
    }

    public Optional<RentalContract> findLatestByOrder(Long orderId) {
        var list = jdbcTemplate.query("SELECT * FROM rental_contract WHERE order_id = ? ORDER BY id DESC LIMIT 1", contractMapper, orderId);
        return list.stream().findFirst();
    }

    public RentalContract createContract(ContractCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO rental_contract
                (contract_no, order_id, user_account_id, merchant_id, store_id, template_id,
                 contract_type, contract_status, provider, rendered_content)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """, new String[] {"id"});
            statement.setString(1, row.contractNo());
            statement.setLong(2, row.orderId());
            statement.setLong(3, row.userAccountId());
            statement.setLong(4, row.merchantId());
            statement.setLong(5, row.storeId());
            statement.setLong(6, row.templateId());
            statement.setString(7, row.contractType().name());
            statement.setString(8, row.provider());
            statement.setString(9, row.renderedContent());
            return statement;
        }, keyHolder);
        return findContract(keyHolder.getKey().longValue()).orElseThrow();
    }

    public RentalContract markSigning(Long id, String provider, String externalFlowId, String signUrl) {
        jdbcTemplate.update("""
            UPDATE rental_contract
            SET contract_status = 'SIGNING',
                provider = ?,
                external_flow_id = ?,
                sign_url = ?,
                sent_at = COALESCE(sent_at, CURRENT_TIMESTAMP),
                failure_reason = NULL
            WHERE id = ?
            """, provider, externalFlowId, signUrl, id);
        return findContract(id).orElseThrow();
    }

    public RentalContract markSigned(Long id) {
        jdbcTemplate.update("""
            UPDATE rental_contract
            SET contract_status = 'SIGNED',
                signed_at = COALESCE(signed_at, CURRENT_TIMESTAMP),
                failure_reason = NULL
            WHERE id = ?
            """, id);
        return findContract(id).orElseThrow();
    }

    public RentalContract archive(Long id, String archivePdfUrl) {
        jdbcTemplate.update("""
            UPDATE rental_contract
            SET contract_status = 'ARCHIVED',
                archive_pdf_url = ?,
                archived_at = COALESCE(archived_at, CURRENT_TIMESTAMP)
            WHERE id = ?
            """, archivePdfUrl, id);
        return findContract(id).orElseThrow();
    }

    public ContractNotify createNotify(NotifyCreateRow row) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO contract_notify
                (contract_id, external_flow_id, notify_id, contract_status, verified, processed, raw_payload, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            setNullableLong(statement, 1, row.contractId());
            statement.setString(2, row.externalFlowId());
            statement.setString(3, row.notifyId());
            statement.setString(4, row.contractStatus());
            statement.setBoolean(5, row.verified());
            statement.setBoolean(6, row.processed());
            statement.setString(7, row.rawPayload());
            statement.setString(8, row.failureReason());
            return statement;
        }, keyHolder);
        return findNotify(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<ContractNotify> findNotify(Long id) {
        var list = jdbcTemplate.query("SELECT * FROM contract_notify WHERE id = ?", notifyMapper, id);
        return list.stream().findFirst();
    }

    public List<ContractNotify> listNotifies() {
        return jdbcTemplate.query("SELECT * FROM contract_notify ORDER BY id DESC", notifyMapper);
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    public record TemplateCreateRow(String templateCode, String templateName, ContractType contractType, String versionNo, String providerTemplateId, String content, String remark) {
    }

    public record ContractCreateRow(String contractNo, Long orderId, Long userAccountId, Long merchantId, Long storeId, Long templateId, ContractType contractType, String provider, String renderedContent) {
    }

    public record NotifyCreateRow(Long contractId, String externalFlowId, String notifyId, String contractStatus, Boolean verified, Boolean processed, String rawPayload, String failureReason) {
    }

    private static class TemplateMapper implements RowMapper<ContractTemplate> {
        @Override
        public ContractTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ContractTemplate(rs.getLong("id"), rs.getString("template_code"), rs.getString("template_name"), ContractType.valueOf(rs.getString("contract_type")), rs.getString("version_no"), rs.getString("provider_template_id"), rs.getString("content"), ContractTemplateStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
        }
    }

    private static class ContractMapper implements RowMapper<RentalContract> {
        @Override
        public RentalContract mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RentalContract(rs.getLong("id"), rs.getString("contract_no"), rs.getLong("order_id"), rs.getLong("user_account_id"), rs.getLong("merchant_id"), rs.getLong("store_id"), rs.getLong("template_id"), ContractType.valueOf(rs.getString("contract_type")), ContractStatus.valueOf(rs.getString("contract_status")), rs.getString("provider"), rs.getString("external_flow_id"), rs.getString("sign_url"), rs.getString("archive_pdf_url"), rs.getString("rendered_content"), rs.getString("failure_reason"), rs.getObject("sent_at", LocalDateTime.class), rs.getObject("signed_at", LocalDateTime.class), rs.getObject("archived_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
        }
    }

    private static class NotifyMapper implements RowMapper<ContractNotify> {
        @Override
        public ContractNotify mapRow(ResultSet rs, int rowNum) throws SQLException {
            var contractId = rs.getLong("contract_id");
            var nullableContractId = rs.wasNull() ? null : contractId;
            return new ContractNotify(rs.getLong("id"), nullableContractId, rs.getString("external_flow_id"), rs.getString("notify_id"), rs.getString("contract_status"), rs.getBoolean("verified"), rs.getBoolean("processed"), rs.getString("raw_payload"), rs.getString("failure_reason"), rs.getObject("received_at", LocalDateTime.class));
        }
    }
}
