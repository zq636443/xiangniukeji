package com.xniu.rental.externalorder.repository;

import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevision;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Stores the effective-dated verification amount timeline for supplemental orders. */
@Repository
public class ExternalOrderVerificationRevisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExternalOrderVerificationRevision> mapper = new RevisionMapper();

    public ExternalOrderVerificationRevisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Use the database clock and its persisted precision for edit boundaries. */
    public LocalDateTime currentDatabaseTime() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP(6)", LocalDateTime.class);
    }

    public ExternalOrderVerificationRevision create(
        Long externalOrderId,
        BigDecimal verificationAmount,
        LocalDateTime effectiveAt,
        ExternalOrderVerificationRevisionType revisionType,
        Long sourceSnapshotId,
        Long operatorAccountId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO external_order_verification_revision
                (external_order_id, verification_amount, effective_at, revision_type,
                 source_snapshot_id, operator_account_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, externalOrderId);
            statement.setBigDecimal(2, money(verificationAmount));
            statement.setObject(3, effectiveAt);
            statement.setString(4, revisionType.name());
            if (sourceSnapshotId == null) {
                statement.setObject(5, null);
            } else {
                statement.setLong(5, sourceSnapshotId);
            }
            if (operatorAccountId == null) {
                statement.setObject(6, null);
            } else {
                statement.setLong(6, operatorAccountId);
            }
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    /** Idempotent helper used by create/backfill paths. */
    public ExternalOrderVerificationRevision createIfMissingSnapshot(
        Long externalOrderId,
        BigDecimal verificationAmount,
        LocalDateTime effectiveAt,
        ExternalOrderVerificationRevisionType revisionType,
        Long sourceSnapshotId,
        Long operatorAccountId
    ) {
        if (sourceSnapshotId != null) {
            var existing = jdbcTemplate.query("""
                SELECT *
                FROM external_order_verification_revision
                WHERE external_order_id = ? AND source_snapshot_id = ?
                LIMIT 1
                """, mapper, externalOrderId, sourceSnapshotId).stream().findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return create(externalOrderId, verificationAmount, effectiveAt, revisionType, sourceSnapshotId, operatorAccountId);
    }

    public List<ExternalOrderVerificationRevision> listByOrder(Long externalOrderId) {
        return jdbcTemplate.query("""
            SELECT *
            FROM external_order_verification_revision
            WHERE external_order_id = ?
            ORDER BY effective_at, id
            """, mapper, externalOrderId);
    }

    public boolean existsByOrder(Long externalOrderId) {
        var count = jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM external_order_verification_revision
            WHERE external_order_id = ?
            """, Integer.class, externalOrderId);
        return count != null && count > 0;
    }

    public void deleteByOrder(Long externalOrderId) {
        jdbcTemplate.update(
            "DELETE FROM external_order_verification_revision WHERE external_order_id = ?",
            externalOrderId
        );
    }

    private ExternalOrderVerificationRevision findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM external_order_verification_revision WHERE id = ?", mapper, id)
            .stream().findFirst().orElseThrow();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static class RevisionMapper implements RowMapper<ExternalOrderVerificationRevision> {
        @Override
        public ExternalOrderVerificationRevision mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExternalOrderVerificationRevision(
                rs.getLong("id"),
                rs.getLong("external_order_id"),
                rs.getBigDecimal("verification_amount"),
                rs.getObject("effective_at", LocalDateTime.class),
                ExternalOrderVerificationRevisionType.valueOf(rs.getString("revision_type")),
                nullableLong(rs, "source_snapshot_id"),
                nullableLong(rs, "operator_account_id"),
                rs.getObject("created_at", LocalDateTime.class)
            );
        }

        private Long nullableLong(ResultSet rs, String column) throws SQLException {
            var value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }
}
