/*
 * Move the takeaway-vehicle channel referral share to the gross settlement
 * base. Only V2 snapshots still referenced by a supplemental order or an
 * accrued supplemental-order renewal are eligible. Replaced/orphaned V2
 * snapshots remain immutable audit records.
 *
 * Safety model:
 * 1. Capture every currently referenced, battery-bearing V2 source.
 * 2. Derive the subset that is safe to recalculate.
 * 3. Fail before persistent business DML unless both sets are identical.
 * 4. Clone V2 to V3, retain the old snapshot, repoint pending ledger rows, and
 *    verify every pointer, amount, and per-source conservation equation before
 *    COMMIT.
 *
 * All persistent DML is inside the explicit InnoDB transaction. A NOT NULL
 * assertion failure aborts the Flyway migration before COMMIT, causing the
 * open transaction to be rolled back. The migration intentionally contains no
 * production row-count constant, so an empty database is a valid no-op.
 */
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS settlement_snapshot_recalculation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  migration_code VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  old_snapshot_id BIGINT NOT NULL,
  new_snapshot_id BIGINT NOT NULL,
  old_calculation_version VARCHAR(32) NOT NULL,
  new_calculation_version VARCHAR(32) NOT NULL,
  old_distributable_amount DECIMAL(12, 2) NOT NULL,
  new_distributable_amount DECIMAL(12, 2) NOT NULL,
  old_store_operation_amount DECIMAL(12, 2) NOT NULL,
  new_store_operation_amount DECIMAL(12, 2) NOT NULL,
  old_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  new_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  old_channel_referral_amount DECIMAL(12, 2) NOT NULL,
  new_channel_referral_amount DECIMAL(12, 2) NOT NULL,
  old_investor_share_amount DECIMAL(12, 2) NOT NULL,
  new_investor_share_amount DECIMAL(12, 2) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_snapshot_recalculation_migration_old (migration_code, old_snapshot_id),
  UNIQUE KEY uk_snapshot_recalculation_migration_source (migration_code, source_type, source_id),
  KEY idx_snapshot_recalculation_new_snapshot (new_snapshot_id)
);

/* The persistent DDL above ends any Flyway-started MySQL transaction. Set the
 * isolation level and begin the business transaction before temporary-table
 * DDL, which does not implicitly commit in MySQL. */
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

CREATE TEMPORARY TABLE v65_takeaway_current_source (
  old_snapshot_id BIGINT PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  UNIQUE KEY uk_v65_current_source (source_type, source_id)
);

CREATE TEMPORARY TABLE v65_takeaway_snapshot_candidate (
  old_snapshot_id BIGINT PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  UNIQUE KEY uk_v65_candidate_source (source_type, source_id)
);

CREATE TEMPORARY TABLE v65_takeaway_expected_amount (
  old_snapshot_id BIGINT PRIMARY KEY,
  new_distributable_amount DECIMAL(12, 2) NOT NULL,
  new_store_operation_amount DECIMAL(12, 2) NOT NULL,
  new_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  new_channel_referral_amount DECIMAL(12, 2) NOT NULL,
  new_investor_share_amount DECIMAL(12, 2) NOT NULL
);

/* Every assertion INSERT always emits one row. NULL (or a value other than 1)
 * is rejected and therefore stops the migration. */
CREATE TEMPORARY TABLE v65_assertion (
  assertion_name VARCHAR(96) PRIMARY KEY,
  assertion_passed TINYINT NOT NULL,
  CONSTRAINT chk_v65_assertion_passed CHECK (assertion_passed = 1)
);

/* The complete scope is based on the live business pointer, not merely on
 * snapshot source_id. Battery-bearing includes either the current SKU config
 * or the amount frozen into the snapshot. */
INSERT INTO v65_takeaway_current_source (old_snapshot_id, source_type, source_id)
SELECT snapshot_row.id, snapshot_row.source_type, snapshot_row.source_id
FROM external_rental_order source_row
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = source_row.settlement_snapshot_id
JOIN product_sku sku ON sku.id = snapshot_row.sku_id
WHERE snapshot_row.source_type = 'EXTERNAL_ORDER'
  AND snapshot_row.source_id = source_row.id
  AND snapshot_row.calculation_version = 'PROFIT_V2'
  AND (COALESCE(sku.battery_cost_daily_amount, 0) > 0
       OR COALESCE(sku.battery_cost_monthly_amount, 0) > 0
       OR snapshot_row.battery_cost_amount > 0);

INSERT INTO v65_takeaway_current_source (old_snapshot_id, source_type, source_id)
SELECT snapshot_row.id, snapshot_row.source_type, snapshot_row.source_id
FROM external_order_renewal_event source_row
JOIN external_rental_order source_order ON source_order.id = source_row.external_order_id
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = source_row.settlement_snapshot_id
JOIN product_sku sku ON sku.id = snapshot_row.sku_id
WHERE snapshot_row.source_type = 'EXTERNAL_RENEWAL'
  AND snapshot_row.source_id = source_row.id
  AND source_row.event_status = 'ACCRUED'
  AND snapshot_row.calculation_version = 'PROFIT_V2'
  AND (COALESCE(sku.battery_cost_daily_amount, 0) > 0
       OR COALESCE(sku.battery_cost_monthly_amount, 0) > 0
       OR snapshot_row.battery_cost_amount > 0);

/* Lock the exact sources, old snapshots, their ledger rows, and any statement
 * rows before qualification. SERIALIZABLE also protects the scanned gaps from
 * a concurrently inserted source or ledger row. */
SELECT source_row.id AS v65_locked_external_order_id
FROM external_rental_order source_row
JOIN v65_takeaway_current_source current_source
  ON current_source.source_type = 'EXTERNAL_ORDER'
 AND current_source.source_id = source_row.id
FOR UPDATE;

SELECT source_row.id AS v65_locked_external_renewal_id
FROM external_order_renewal_event source_row
JOIN v65_takeaway_current_source current_source
  ON current_source.source_type = 'EXTERNAL_RENEWAL'
 AND current_source.source_id = source_row.id
FOR UPDATE;

SELECT snapshot_row.id AS v65_locked_snapshot_id
FROM settlement_rule_snapshot snapshot_row
JOIN v65_takeaway_current_source current_source
  ON current_source.old_snapshot_id = snapshot_row.id
FOR UPDATE;

SELECT income_row.id AS v65_locked_income_id
FROM settlement_income_entry income_row
JOIN v65_takeaway_current_source current_source
  ON current_source.source_type = income_row.source_type
 AND current_source.source_id = income_row.source_id
FOR UPDATE;

SELECT statement_line.id AS v65_locked_statement_line_id
FROM settlement_statement_line statement_line
JOIN v65_takeaway_current_source current_source
  ON current_source.source_type = statement_line.source_type
 AND current_source.source_id = statement_line.source_id
FOR UPDATE;

SELECT audit_row.id AS v65_locked_audit_id
FROM settlement_snapshot_recalculation_audit audit_row
WHERE audit_row.migration_code = 'V65'
FOR UPDATE;

/* A candidate must still be the source's live V2 pointer, must be financially
 * feasible under V3, and must have a complete, unlocked, unposted V2 ledger.
 * Each redistributed line must exist exactly once because this migration
 * updates existing financial identities instead of inventing new ones. */
INSERT INTO v65_takeaway_snapshot_candidate (old_snapshot_id, source_type, source_id)
SELECT current_source.old_snapshot_id, current_source.source_type, current_source.source_id
FROM v65_takeaway_current_source current_source
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = current_source.old_snapshot_id
WHERE snapshot_row.source_type = current_source.source_type
  AND snapshot_row.source_id = current_source.source_id
  AND snapshot_row.calculation_version = 'PROFIT_V2'
  AND (
    (current_source.source_type = 'EXTERNAL_ORDER' AND EXISTS (
      SELECT 1
      FROM external_rental_order source_row
      WHERE source_row.id = current_source.source_id
        AND source_row.settlement_snapshot_id = current_source.old_snapshot_id
    ))
    OR
    (current_source.source_type = 'EXTERNAL_RENEWAL' AND EXISTS (
      SELECT 1
      FROM external_order_renewal_event source_row
      WHERE source_row.id = current_source.source_id
        AND source_row.settlement_snapshot_id = current_source.old_snapshot_id
        AND source_row.event_status = 'ACCRUED'
    ))
  )
  AND snapshot_row.settlement_base_amount >= 0
  AND snapshot_row.channel_fee_amount >= 0
  AND snapshot_row.platform_fee_amount >= 0
  AND snapshot_row.battery_cost_amount >= 0
  AND snapshot_row.channel_referral_rate >= 0
  AND snapshot_row.store_operation_rate >= 0
  AND snapshot_row.maintenance_fund_rate >= 0
  AND snapshot_row.investor_share_rate >= 0
  AND snapshot_row.settlement_base_amount
      - snapshot_row.channel_fee_amount
      - snapshot_row.platform_fee_amount
      - snapshot_row.battery_cost_amount
      - ROUND(snapshot_row.settlement_base_amount * snapshot_row.channel_referral_rate, 2) >= 0
  AND snapshot_row.store_operation_rate
      + snapshot_row.maintenance_fund_rate
      + snapshot_row.investor_share_rate > 0
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry unsafe_income
    WHERE unsafe_income.source_type = current_source.source_type
      AND unsafe_income.source_id = current_source.source_id
      AND (unsafe_income.entry_status <> 'PENDING'
           OR unsafe_income.snapshot_id <> current_source.old_snapshot_id)
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line statement_line
    WHERE statement_line.source_type = current_source.source_type
      AND statement_line.source_id = current_source.source_id
  )
  AND 1 = (
    SELECT COUNT(*)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'STORE_OPERATION_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND 1 = (
    SELECT COUNT(*)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'MAINTENANCE_FUND_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND 1 = (
    SELECT COUNT(*)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'CHANNEL_REFERRAL_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND 1 = (
    SELECT COUNT(*)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'INVESTOR_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND snapshot_row.channel_fee_amount = COALESCE((
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'CHANNEL_VERIFICATION_FEE'
      AND income_row.entry_status = 'PENDING'
  ), 0)
  AND snapshot_row.platform_fee_amount = COALESCE((
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'PLATFORM_SERVICE_FEE'
      AND income_row.entry_status = 'PENDING'
  ), 0)
  AND snapshot_row.store_operation_amount = (
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'STORE_OPERATION_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND snapshot_row.maintenance_fund_amount = (
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'MAINTENANCE_FUND_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND snapshot_row.channel_referral_amount = (
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'CHANNEL_REFERRAL_SHARE'
      AND income_row.entry_status = 'PENDING'
  )
  AND snapshot_row.investor_share_amount = (
    SELECT SUM(income_row.amount)
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.line_type = 'INVESTOR_SHARE'
      AND income_row.entry_status = 'PENDING'
  );

/* The candidate SELECT can only emit rows from current_source. Therefore equal
 * cardinality is also exact member equality (both tables enforce the same
 * snapshot and logical-source uniqueness). This succeeds when both are empty. */
SELECT COUNT(*) INTO @v65_current_source_count
FROM v65_takeaway_current_source;

SELECT COUNT(*) INTO @v65_candidate_count
FROM v65_takeaway_snapshot_candidate;

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'all_current_sources_are_safe_candidates',
       CASE WHEN @v65_current_source_count = @v65_candidate_count
            THEN 1 ELSE NULL END;

/* Deterministic identifiers make the result auditable. Existing V65 business
 * rows are a collision while new V2 candidates exist. A deliberate Flyway
 * retry after a fully committed run has no V2 candidates and is a validated
 * no-op rather than a second rewrite. */
INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'no_preexisting_v65_snapshot_collision',
       CASE WHEN @v65_candidate_count = 0 OR NOT EXISTS (
         SELECT 1
         FROM settlement_rule_snapshot existing_snapshot
         JOIN v65_takeaway_snapshot_candidate candidate
           ON existing_snapshot.snapshot_no = CONCAT('SNP-V65-', candidate.old_snapshot_id)
       ) THEN 1 ELSE NULL END;

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'no_preexisting_v65_audit_collision',
       CASE WHEN @v65_candidate_count = 0 OR NOT EXISTS (
         SELECT 1
         FROM settlement_snapshot_recalculation_audit existing_audit
         WHERE existing_audit.migration_code = 'V65'
       ) THEN 1 ELSE NULL END;

INSERT INTO v65_takeaway_expected_amount (
  old_snapshot_id, new_distributable_amount, new_store_operation_amount,
  new_maintenance_fund_amount, new_channel_referral_amount,
  new_investor_share_amount
)
SELECT weighted.old_snapshot_id,
       weighted.new_distributable_amount,
       weighted.new_store_operation_amount,
       weighted.new_maintenance_fund_amount,
       weighted.new_channel_referral_amount,
       weighted.new_distributable_amount
         - weighted.new_store_operation_amount
         - weighted.new_maintenance_fund_amount
FROM (
  SELECT pooled.old_snapshot_id,
         pooled.new_distributable_amount,
         ROUND(
           pooled.new_distributable_amount * pooled.store_operation_rate
             / pooled.remaining_weight,
           2
         ) AS new_store_operation_amount,
         ROUND(
           pooled.new_distributable_amount * pooled.maintenance_fund_rate
             / pooled.remaining_weight,
           2
         ) AS new_maintenance_fund_amount,
         pooled.new_channel_referral_amount
  FROM (
    SELECT snapshot_row.id AS old_snapshot_id,
           snapshot_row.settlement_base_amount
             - snapshot_row.channel_fee_amount
             - snapshot_row.platform_fee_amount
             - snapshot_row.battery_cost_amount
             - ROUND(snapshot_row.settlement_base_amount * snapshot_row.channel_referral_rate, 2)
               AS new_distributable_amount,
           snapshot_row.store_operation_rate,
           snapshot_row.maintenance_fund_rate,
           snapshot_row.store_operation_rate
             + snapshot_row.maintenance_fund_rate
             + snapshot_row.investor_share_rate AS remaining_weight,
           ROUND(snapshot_row.settlement_base_amount * snapshot_row.channel_referral_rate, 2)
             AS new_channel_referral_amount
    FROM v65_takeaway_snapshot_candidate candidate
    JOIN settlement_rule_snapshot snapshot_row
      ON snapshot_row.id = candidate.old_snapshot_id
  ) pooled
) weighted;

SELECT COUNT(*) INTO @v65_expected_count
FROM v65_takeaway_expected_amount;

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'expected_amounts_are_complete',
       CASE WHEN @v65_expected_count = @v65_candidate_count
            THEN 1 ELSE NULL END;

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'expected_amounts_are_balanced',
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM v65_takeaway_expected_amount expected
           JOIN settlement_rule_snapshot old_snapshot
             ON old_snapshot.id = expected.old_snapshot_id
           WHERE expected.new_distributable_amount < 0
              OR expected.new_store_operation_amount < 0
              OR expected.new_maintenance_fund_amount < 0
              OR expected.new_channel_referral_amount < 0
              OR expected.new_investor_share_amount < 0
              OR expected.new_distributable_amount <>
                   expected.new_store_operation_amount
                     + expected.new_maintenance_fund_amount
                     + expected.new_investor_share_amount
              OR old_snapshot.settlement_base_amount <>
                   old_snapshot.channel_fee_amount
                     + old_snapshot.platform_fee_amount
                     + old_snapshot.battery_cost_amount
                     + expected.new_channel_referral_amount
                     + expected.new_distributable_amount
       ) THEN 1 ELSE NULL END;

/* Clone instead of overwriting: the old V2 snapshot remains the exact record
 * of what the system previously calculated. */
INSERT INTO settlement_rule_snapshot (
  snapshot_no, source_type, source_id, calculation_version, source_channel,
  store_sku_id, sku_id, merchant_id, store_id, frame_asset_id,
  battery_asset_id, matched_rule_id, matched_rule_scope, rental_amount,
  settlement_base_amount, channel_fee_rate, channel_fee_amount,
  platform_fee_rate, platform_fee_amount, battery_cost_amount,
  distributable_amount, store_operation_rate, store_operation_amount,
  maintenance_fund_rate, maintenance_fund_amount, channel_referral_rate,
  channel_referral_amount, investor_share_rate, investor_share_amount,
  sign_fee_amount, merchant_order_fee_amount, merchant_rent_share_rate,
  merchant_rent_share_amount, platform_rent_share_rate,
  platform_rent_share_amount, investor_rent_share_rate,
  investor_gross_share_amount, investor_operation_fee_amount,
  maintenance_fee_amount, investor_net_share_amount, rule_summary, created_at
)
SELECT
  CONCAT('SNP-V65-', old_snapshot.id),
  old_snapshot.source_type,
  old_snapshot.source_id,
  'PROFIT_V3',
  old_snapshot.source_channel,
  old_snapshot.store_sku_id,
  old_snapshot.sku_id,
  old_snapshot.merchant_id,
  old_snapshot.store_id,
  old_snapshot.frame_asset_id,
  old_snapshot.battery_asset_id,
  old_snapshot.matched_rule_id,
  old_snapshot.matched_rule_scope,
  old_snapshot.rental_amount,
  old_snapshot.settlement_base_amount,
  old_snapshot.channel_fee_rate,
  old_snapshot.channel_fee_amount,
  old_snapshot.platform_fee_rate,
  old_snapshot.platform_fee_amount,
  old_snapshot.battery_cost_amount,
  expected.new_distributable_amount,
  old_snapshot.store_operation_rate,
  expected.new_store_operation_amount,
  old_snapshot.maintenance_fund_rate,
  expected.new_maintenance_fund_amount,
  old_snapshot.channel_referral_rate,
  expected.new_channel_referral_amount,
  old_snapshot.investor_share_rate,
  expected.new_investor_share_amount,
  old_snapshot.sign_fee_amount,
  old_snapshot.merchant_order_fee_amount,
  old_snapshot.merchant_rent_share_rate,
  expected.new_store_operation_amount,
  old_snapshot.platform_rent_share_rate,
  old_snapshot.platform_rent_share_amount,
  old_snapshot.investor_rent_share_rate,
  expected.new_investor_share_amount,
  old_snapshot.investor_operation_fee_amount,
  expected.new_maintenance_fund_amount,
  expected.new_investor_share_amount,
  LEFT(CONCAT(old_snapshot.rule_summary,
    ';calculationVersion=PROFIT_V3;grossReferralBase=true;backfill=V65'), 1024),
  CURRENT_TIMESTAMP
FROM v65_takeaway_snapshot_candidate candidate
JOIN settlement_rule_snapshot old_snapshot
  ON old_snapshot.id = candidate.old_snapshot_id
JOIN v65_takeaway_expected_amount expected
  ON expected.old_snapshot_id = candidate.old_snapshot_id;

INSERT INTO settlement_snapshot_recalculation_audit (
  migration_code, source_type, source_id, old_snapshot_id, new_snapshot_id,
  old_calculation_version, new_calculation_version,
  old_distributable_amount, new_distributable_amount,
  old_store_operation_amount, new_store_operation_amount,
  old_maintenance_fund_amount, new_maintenance_fund_amount,
  old_channel_referral_amount, new_channel_referral_amount,
  old_investor_share_amount, new_investor_share_amount, reason
)
SELECT
  'V65',
  candidate.source_type,
  candidate.source_id,
  old_snapshot.id,
  new_snapshot.id,
  old_snapshot.calculation_version,
  new_snapshot.calculation_version,
  old_snapshot.distributable_amount,
  new_snapshot.distributable_amount,
  old_snapshot.store_operation_amount,
  new_snapshot.store_operation_amount,
  old_snapshot.maintenance_fund_amount,
  new_snapshot.maintenance_fund_amount,
  old_snapshot.channel_referral_amount,
  new_snapshot.channel_referral_amount,
  old_snapshot.investor_share_amount,
  new_snapshot.investor_share_amount,
  '外卖车20%渠道引流分润改按核销毛额计算'
FROM v65_takeaway_snapshot_candidate candidate
JOIN settlement_rule_snapshot old_snapshot
  ON old_snapshot.id = candidate.old_snapshot_id
JOIN settlement_rule_snapshot new_snapshot
  ON new_snapshot.snapshot_no = CONCAT('SNP-V65-', old_snapshot.id);

UPDATE external_rental_order source_row
JOIN settlement_snapshot_recalculation_audit audit_row
  ON audit_row.migration_code = 'V65'
 AND audit_row.source_type = 'EXTERNAL_ORDER'
 AND audit_row.source_id = source_row.id
SET source_row.settlement_snapshot_id = audit_row.new_snapshot_id,
    source_row.updated_at = source_row.updated_at
WHERE source_row.settlement_snapshot_id = audit_row.old_snapshot_id;

UPDATE external_order_renewal_event source_row
JOIN settlement_snapshot_recalculation_audit audit_row
  ON audit_row.migration_code = 'V65'
 AND audit_row.source_type = 'EXTERNAL_RENEWAL'
 AND audit_row.source_id = source_row.id
SET source_row.settlement_snapshot_id = audit_row.new_snapshot_id,
    source_row.updated_at = source_row.updated_at
WHERE source_row.settlement_snapshot_id = audit_row.old_snapshot_id;

/* Repoint every still-pending row for the source, but only when it is tied to
 * the exact V2 snapshot preflighted above. Fixed-fee and order-fee amounts are
 * retained; the four redistributed lines receive the V3 amounts. */
UPDATE settlement_income_entry income_row
JOIN settlement_snapshot_recalculation_audit audit_row
  ON audit_row.migration_code = 'V65'
 AND audit_row.source_type = income_row.source_type
 AND audit_row.source_id = income_row.source_id
 AND audit_row.old_snapshot_id = income_row.snapshot_id
SET income_row.snapshot_id = audit_row.new_snapshot_id,
    income_row.amount = CASE income_row.line_type
      WHEN 'STORE_OPERATION_SHARE' THEN audit_row.new_store_operation_amount
      WHEN 'MAINTENANCE_FUND_SHARE' THEN audit_row.new_maintenance_fund_amount
      WHEN 'CHANNEL_REFERRAL_SHARE' THEN audit_row.new_channel_referral_amount
      WHEN 'INVESTOR_SHARE' THEN audit_row.new_investor_share_amount
      ELSE income_row.amount
    END,
    income_row.remark = CASE income_row.line_type
      WHEN 'STORE_OPERATION_SHARE' THEN LEFT(CONCAT('按快照三方权重重分配；', COALESCE(income_row.remark, '')), 255)
      WHEN 'MAINTENANCE_FUND_SHARE' THEN LEFT(CONCAT('按快照三方权重重分配；', COALESCE(income_row.remark, '')), 255)
      WHEN 'CHANNEL_REFERRAL_SHARE' THEN LEFT(CONCAT('按核销毛额计渠道引流；', COALESCE(income_row.remark, '')), 255)
      WHEN 'INVESTOR_SHARE' THEN LEFT(CONCAT('按快照三方权重重分配；', COALESCE(income_row.remark, '')), 255)
      ELSE income_row.remark
    END
WHERE income_row.entry_status = 'PENDING';

/* Persistent-row cardinality and membership. Keep the count queries separate
 * because MySQL cannot reopen one TEMPORARY table twice in a statement. */
SELECT COUNT(*) INTO @v65_new_snapshot_count
FROM settlement_rule_snapshot new_snapshot
JOIN v65_takeaway_snapshot_candidate candidate
  ON new_snapshot.snapshot_no = CONCAT('SNP-V65-', candidate.old_snapshot_id);

SELECT COUNT(*) INTO @v65_new_audit_count
FROM settlement_snapshot_recalculation_audit audit_row
JOIN v65_takeaway_snapshot_candidate candidate
  ON candidate.old_snapshot_id = audit_row.old_snapshot_id
 AND candidate.source_type = audit_row.source_type
 AND candidate.source_id = audit_row.source_id
WHERE audit_row.migration_code = 'V65';

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'new_snapshot_and_audit_counts_match',
       CASE WHEN @v65_new_snapshot_count = @v65_candidate_count
                  AND @v65_new_audit_count = @v65_candidate_count
            THEN 1 ELSE NULL END;

INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'new_snapshots_and_audit_match_candidates',
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM v65_takeaway_snapshot_candidate candidate
           JOIN v65_takeaway_expected_amount expected
             ON expected.old_snapshot_id = candidate.old_snapshot_id
           JOIN settlement_rule_snapshot old_snapshot
             ON old_snapshot.id = candidate.old_snapshot_id
           LEFT JOIN settlement_snapshot_recalculation_audit audit_row
             ON audit_row.migration_code = 'V65'
            AND audit_row.source_type = candidate.source_type
            AND audit_row.source_id = candidate.source_id
            AND audit_row.old_snapshot_id = candidate.old_snapshot_id
           LEFT JOIN settlement_rule_snapshot new_snapshot
             ON new_snapshot.id = audit_row.new_snapshot_id
           WHERE audit_row.id IS NULL
              OR new_snapshot.id IS NULL
              OR old_snapshot.calculation_version <> 'PROFIT_V2'
              OR new_snapshot.snapshot_no <> CONCAT('SNP-V65-', old_snapshot.id)
              OR new_snapshot.source_type <> candidate.source_type
              OR new_snapshot.source_id <> candidate.source_id
              OR new_snapshot.calculation_version <> 'PROFIT_V3'
              OR new_snapshot.settlement_base_amount <> old_snapshot.settlement_base_amount
              OR new_snapshot.channel_fee_amount <> old_snapshot.channel_fee_amount
              OR new_snapshot.platform_fee_amount <> old_snapshot.platform_fee_amount
              OR new_snapshot.battery_cost_amount <> old_snapshot.battery_cost_amount
              OR new_snapshot.distributable_amount <> expected.new_distributable_amount
              OR new_snapshot.store_operation_amount <> expected.new_store_operation_amount
              OR new_snapshot.maintenance_fund_amount <> expected.new_maintenance_fund_amount
              OR new_snapshot.channel_referral_amount <> expected.new_channel_referral_amount
              OR new_snapshot.investor_share_amount <> expected.new_investor_share_amount
              OR audit_row.old_distributable_amount <> old_snapshot.distributable_amount
              OR audit_row.new_distributable_amount <> expected.new_distributable_amount
              OR audit_row.old_store_operation_amount <> old_snapshot.store_operation_amount
              OR audit_row.new_store_operation_amount <> expected.new_store_operation_amount
              OR audit_row.old_maintenance_fund_amount <> old_snapshot.maintenance_fund_amount
              OR audit_row.new_maintenance_fund_amount <> expected.new_maintenance_fund_amount
              OR audit_row.old_channel_referral_amount <> old_snapshot.channel_referral_amount
              OR audit_row.new_channel_referral_amount <> expected.new_channel_referral_amount
              OR audit_row.old_investor_share_amount <> old_snapshot.investor_share_amount
              OR audit_row.new_investor_share_amount <> expected.new_investor_share_amount
       ) THEN 1 ELSE NULL END;

/* Every live source must now point to its V3 clone, while no audit target may
 * be missing. */
INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'all_business_pointers_reference_v3',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_snapshot_recalculation_audit audit_row
         LEFT JOIN external_rental_order source_row
           ON audit_row.source_type = 'EXTERNAL_ORDER'
          AND source_row.id = audit_row.source_id
          AND source_row.settlement_snapshot_id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V65'
           AND audit_row.source_type = 'EXTERNAL_ORDER'
           AND source_row.id IS NULL
       ) AND NOT EXISTS (
         SELECT 1
         FROM settlement_snapshot_recalculation_audit audit_row
         LEFT JOIN external_order_renewal_event source_row
           ON audit_row.source_type = 'EXTERNAL_RENEWAL'
          AND source_row.id = audit_row.source_id
          AND source_row.settlement_snapshot_id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V65'
           AND audit_row.source_type = 'EXTERNAL_RENEWAL'
           AND source_row.id IS NULL
       ) THEN 1 ELSE NULL END;

/* Verify exact ledger identity, amount mapping, and conservation source by
 * source. Order-fee income is independently conserved and is not mixed into
 * the rental split. */
INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'pending_income_rows_match_v3_and_conserve',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_snapshot_recalculation_audit audit_row
         JOIN settlement_rule_snapshot new_snapshot
           ON new_snapshot.id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V65'
           AND (
             EXISTS (
               SELECT 1
               FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND (income_row.entry_status <> 'PENDING'
                      OR income_row.snapshot_id <> audit_row.new_snapshot_id)
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'STORE_OPERATION_SHARE'
                 AND income_row.amount = audit_row.new_store_operation_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'MAINTENANCE_FUND_SHARE'
                 AND income_row.amount = audit_row.new_maintenance_fund_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'CHANNEL_REFERRAL_SHARE'
                 AND income_row.amount = audit_row.new_channel_referral_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'INVESTOR_SHARE'
                 AND income_row.amount = audit_row.new_investor_share_amount
             )
             OR new_snapshot.channel_fee_amount <> COALESCE((
               SELECT SUM(income_row.amount)
               FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'CHANNEL_VERIFICATION_FEE'
             ), 0)
             OR new_snapshot.platform_fee_amount <> COALESCE((
               SELECT SUM(income_row.amount)
               FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'PLATFORM_SERVICE_FEE'
             ), 0)
             OR new_snapshot.settlement_base_amount - new_snapshot.battery_cost_amount <>
                COALESCE((
                  SELECT SUM(income_row.amount)
                  FROM settlement_income_entry income_row
                  WHERE income_row.source_type = audit_row.source_type
                    AND income_row.source_id = audit_row.source_id
                    AND income_row.line_type IN (
                      'CHANNEL_VERIFICATION_FEE', 'PLATFORM_SERVICE_FEE',
                      'STORE_OPERATION_SHARE', 'MAINTENANCE_FUND_SHARE',
                      'CHANNEL_REFERRAL_SHARE', 'INVESTOR_SHARE'
                    )
                ), 0)
             OR new_snapshot.sign_fee_amount <> COALESCE((
               SELECT SUM(income_row.amount)
               FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type IN (
                   'MERCHANT_ORDER_FEE', 'PLATFORM_ORDER_FEE_SERVICE_FEE'
                 )
             ), 0)
           )
       ) THEN 1 ELSE NULL END;

/* Final snapshot arithmetic: gross equals fixed deductions plus the complete
 * normalized V3 allocation, with the investor carrying the cent residual. */
INSERT INTO v65_assertion (assertion_name, assertion_passed)
SELECT 'each_v3_snapshot_is_balanced',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_snapshot_recalculation_audit audit_row
         JOIN settlement_rule_snapshot new_snapshot
           ON new_snapshot.id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V65'
           AND (
             new_snapshot.distributable_amount <>
               new_snapshot.store_operation_amount
                 + new_snapshot.maintenance_fund_amount
                 + new_snapshot.investor_share_amount
             OR new_snapshot.settlement_base_amount <>
               new_snapshot.channel_fee_amount
                 + new_snapshot.platform_fee_amount
                 + new_snapshot.battery_cost_amount
                 + new_snapshot.channel_referral_amount
                 + new_snapshot.store_operation_amount
                 + new_snapshot.maintenance_fund_amount
                 + new_snapshot.investor_share_amount
           )
       ) THEN 1 ELSE NULL END;

DROP TEMPORARY TABLE IF EXISTS v65_assertion;
DROP TEMPORARY TABLE IF EXISTS v65_takeaway_expected_amount;
DROP TEMPORARY TABLE IF EXISTS v65_takeaway_snapshot_candidate;
DROP TEMPORARY TABLE IF EXISTS v65_takeaway_current_source;

COMMIT;
