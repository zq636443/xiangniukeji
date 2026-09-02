/*
 * Remove the historical 200-yuan/30-day battery tier from every currently
 * effective takeaway supplemental-order source. Battery cost is recalculated
 * from the source's exact persisted interval at 6.80 yuan per elapsed day.
 *
 * Initial interval:
 *   rent_started_at -> earliest ACCRUED renewal period_start_at, when present;
 *   otherwise rent_started_at -> current expected_return_at.
 * Renewal interval:
 *   period_start_at -> period_end_at.
 *
 * A zero-length initial interval is valid and costs zero. A negative or missing
 * interval, a non-V3 current snapshot, a locked ledger/statement, an incomplete
 * ledger identity, or an unfunded V3 balance fails before persistent business
 * DML. Old snapshots are retained and the current business pointers are moved
 * to deterministic V66 clones.
 */
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS settlement_battery_recalculation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  migration_code VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  old_snapshot_id BIGINT NOT NULL,
  new_snapshot_id BIGINT NOT NULL,
  period_start_at DATETIME NOT NULL,
  period_end_at DATETIME NOT NULL,
  daily_cost_amount DECIMAL(12, 2) NOT NULL,
  old_battery_cost_amount DECIMAL(12, 2) NOT NULL,
  new_battery_cost_amount DECIMAL(12, 2) NOT NULL,
  old_distributable_amount DECIMAL(12, 2) NOT NULL,
  new_distributable_amount DECIMAL(12, 2) NOT NULL,
  old_store_operation_amount DECIMAL(12, 2) NOT NULL,
  new_store_operation_amount DECIMAL(12, 2) NOT NULL,
  old_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  new_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  old_investor_share_amount DECIMAL(12, 2) NOT NULL,
  new_investor_share_amount DECIMAL(12, 2) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_battery_recalculation_migration_old (migration_code, old_snapshot_id),
  UNIQUE KEY uk_battery_recalculation_migration_source (migration_code, source_type, source_id),
  KEY idx_battery_recalculation_new_snapshot (new_snapshot_id)
);

/* The persistent DDL above ends a MySQL transaction. All business writes start
 * only after the complete candidate set has passed the assertions below. */
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

CREATE TEMPORARY TABLE v66_daily_battery_current_source (
  old_snapshot_id BIGINT PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  external_order_id BIGINT NOT NULL,
  period_start_at DATETIME NULL,
  period_end_at DATETIME NULL,
  UNIQUE KEY uk_v66_current_source (source_type, source_id),
  KEY idx_v66_current_external_order (external_order_id)
);

CREATE TEMPORARY TABLE v66_daily_battery_candidate (
  old_snapshot_id BIGINT PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  external_order_id BIGINT NOT NULL,
  period_start_at DATETIME NOT NULL,
  period_end_at DATETIME NOT NULL,
  UNIQUE KEY uk_v66_candidate_source (source_type, source_id)
);

CREATE TEMPORARY TABLE v66_daily_battery_expected (
  old_snapshot_id BIGINT PRIMARY KEY,
  new_channel_fee_amount DECIMAL(12, 2) NOT NULL,
  new_platform_fee_amount DECIMAL(12, 2) NOT NULL,
  new_battery_cost_amount DECIMAL(12, 2) NOT NULL,
  new_distributable_amount DECIMAL(12, 2) NOT NULL,
  new_store_operation_amount DECIMAL(12, 2) NOT NULL,
  new_maintenance_fund_amount DECIMAL(12, 2) NOT NULL,
  new_channel_referral_amount DECIMAL(12, 2) NOT NULL,
  new_investor_share_amount DECIMAL(12, 2) NOT NULL
);

CREATE TEMPORARY TABLE v66_assertion (
  assertion_name VARCHAR(96) PRIMARY KEY,
  assertion_passed TINYINT NOT NULL,
  CONSTRAINT chk_v66_assertion_passed CHECK (assertion_passed = 1)
);

/* Resolve the original first-period boundary before locking. The live source
 * pointer, rather than snapshot.source_id alone, defines current effectiveness.
 * Existing V66 audit rows are excluded so a deliberate Flyway retry is a no-op. */
INSERT INTO v66_daily_battery_current_source (
  old_snapshot_id, source_type, source_id, external_order_id,
  period_start_at, period_end_at
)
SELECT snapshot_row.id,
       'EXTERNAL_ORDER',
       source_row.id,
       source_row.id,
       source_row.rent_started_at,
       COALESCE(first_renewal.period_start_at, source_row.expected_return_at)
FROM external_rental_order source_row
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = source_row.settlement_snapshot_id
JOIN product_sku sku
  ON sku.id = snapshot_row.sku_id
LEFT JOIN (
  SELECT external_order_id, MIN(period_start_at) AS period_start_at
  FROM external_order_renewal_event
  WHERE event_status = 'ACCRUED'
  GROUP BY external_order_id
) first_renewal
  ON first_renewal.external_order_id = source_row.id
WHERE source_row.order_status <> 'TERMINATED'
  AND snapshot_row.source_type = 'EXTERNAL_ORDER'
  AND snapshot_row.source_id = source_row.id
  AND (COALESCE(sku.battery_cost_daily_amount, 0) > 0
       OR COALESCE(sku.battery_cost_monthly_amount, 0) > 0
       OR snapshot_row.battery_cost_amount > 0)
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_battery_recalculation_audit audit_row
    WHERE audit_row.migration_code = 'V66'
      AND audit_row.source_type = 'EXTERNAL_ORDER'
      AND audit_row.source_id = source_row.id
  );

INSERT INTO v66_daily_battery_current_source (
  old_snapshot_id, source_type, source_id, external_order_id,
  period_start_at, period_end_at
)
SELECT snapshot_row.id,
       'EXTERNAL_RENEWAL',
       source_row.id,
       source_row.external_order_id,
       source_row.period_start_at,
       source_row.period_end_at
FROM external_order_renewal_event source_row
JOIN external_rental_order source_order
  ON source_order.id = source_row.external_order_id
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = source_row.settlement_snapshot_id
JOIN product_sku sku
  ON sku.id = snapshot_row.sku_id
WHERE source_row.event_status = 'ACCRUED'
  AND source_order.order_status <> 'TERMINATED'
  AND snapshot_row.source_type = 'EXTERNAL_RENEWAL'
  AND snapshot_row.source_id = source_row.id
  AND (COALESCE(sku.battery_cost_daily_amount, 0) > 0
       OR COALESCE(sku.battery_cost_monthly_amount, 0) > 0
       OR snapshot_row.battery_cost_amount > 0
       OR source_row.battery_cost_amount > 0)
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_battery_recalculation_audit audit_row
    WHERE audit_row.migration_code = 'V66'
      AND audit_row.source_type = 'EXTERNAL_RENEWAL'
      AND audit_row.source_id = source_row.id
  );

/* Keep the runtime lock order: external order -> renewal -> statement -> income
 * -> snapshot. Duplicate parent ids are harmless and still acquire one row lock. */
SELECT source_order.id AS v66_locked_external_order_id
FROM external_rental_order source_order
JOIN v66_daily_battery_current_source current_source
  ON current_source.external_order_id = source_order.id
ORDER BY source_order.id
FOR UPDATE;

SELECT source_row.id AS v66_locked_external_renewal_id
FROM external_order_renewal_event source_row
JOIN v66_daily_battery_current_source current_source
  ON current_source.source_type = 'EXTERNAL_RENEWAL'
 AND current_source.source_id = source_row.id
ORDER BY source_row.id
FOR UPDATE;

SELECT statement_line.id AS v66_locked_statement_line_id
FROM settlement_statement_line statement_line
JOIN v66_daily_battery_current_source current_source
  ON current_source.source_type = statement_line.source_type
 AND current_source.source_id = statement_line.source_id
FOR UPDATE;

SELECT income_row.id AS v66_locked_income_id
FROM settlement_income_entry income_row
JOIN v66_daily_battery_current_source current_source
  ON current_source.source_type = income_row.source_type
 AND current_source.source_id = income_row.source_id
ORDER BY income_row.id
FOR UPDATE;

SELECT snapshot_row.id AS v66_locked_snapshot_id
FROM settlement_rule_snapshot snapshot_row
JOIN v66_daily_battery_current_source current_source
  ON current_source.old_snapshot_id = snapshot_row.id
FOR UPDATE;

SELECT audit_row.id AS v66_locked_audit_id
FROM settlement_battery_recalculation_audit audit_row
WHERE audit_row.migration_code = 'V66'
FOR UPDATE;

/* Every source must be an intact, mutable V3 fact. Missing financial lines are
 * rejected intentionally: this migration updates identities and never invents
 * a line whose beneficiary cannot be proven from the old ledger. */
INSERT INTO v66_daily_battery_candidate (
  old_snapshot_id, source_type, source_id, external_order_id,
  period_start_at, period_end_at
)
SELECT current_source.old_snapshot_id,
       current_source.source_type,
       current_source.source_id,
       current_source.external_order_id,
       current_source.period_start_at,
       current_source.period_end_at
FROM v66_daily_battery_current_source current_source
JOIN settlement_rule_snapshot snapshot_row
  ON snapshot_row.id = current_source.old_snapshot_id
WHERE snapshot_row.source_type = current_source.source_type
  AND snapshot_row.source_id = current_source.source_id
  AND snapshot_row.calculation_version = 'PROFIT_V3'
  AND current_source.period_start_at IS NOT NULL
  AND current_source.period_end_at IS NOT NULL
  AND current_source.period_end_at >= current_source.period_start_at
  AND snapshot_row.settlement_base_amount >= 0
  AND snapshot_row.channel_fee_rate >= 0
  AND snapshot_row.platform_fee_rate >= 0
  AND snapshot_row.store_operation_rate >= 0
  AND snapshot_row.maintenance_fund_rate >= 0
  AND snapshot_row.channel_referral_rate >= 0
  AND snapshot_row.investor_share_rate >= 0
  AND snapshot_row.store_operation_rate
      + snapshot_row.maintenance_fund_rate
      + snapshot_row.investor_share_rate > 0
  AND (
    current_source.source_type = 'EXTERNAL_ORDER'
    OR EXISTS (
      SELECT 1
      FROM external_order_renewal_event renewal_row
      WHERE renewal_row.id = current_source.source_id
        AND renewal_row.event_status = 'ACCRUED'
        AND renewal_row.settlement_snapshot_id = current_source.old_snapshot_id
        AND renewal_row.battery_cost_amount = snapshot_row.battery_cost_amount
    )
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line statement_line
    WHERE statement_line.source_type = current_source.source_type
      AND statement_line.source_id = current_source.source_id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND (income_row.entry_status <> 'PENDING'
           OR income_row.snapshot_id <> current_source.old_snapshot_id)
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'CHANNEL_VERIFICATION_FEE'
      AND income_row.amount = snapshot_row.channel_fee_amount
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'PLATFORM_SERVICE_FEE'
      AND income_row.amount = snapshot_row.platform_fee_amount
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'STORE_OPERATION_SHARE'
      AND income_row.amount = snapshot_row.store_operation_amount
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'MAINTENANCE_FUND_SHARE'
      AND income_row.amount = snapshot_row.maintenance_fund_amount
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'CHANNEL_REFERRAL_SHARE'
      AND income_row.amount = snapshot_row.channel_referral_amount
  )
  AND 1 = (
    SELECT COUNT(*) FROM settlement_income_entry income_row
    WHERE income_row.source_type = current_source.source_type
      AND income_row.source_id = current_source.source_id
      AND income_row.snapshot_id = current_source.old_snapshot_id
      AND income_row.entry_status = 'PENDING'
      AND income_row.line_type = 'INVESTOR_SHARE'
      AND income_row.amount = snapshot_row.investor_share_amount
  );

SELECT COUNT(*) INTO @v66_current_source_count
FROM v66_daily_battery_current_source;

SELECT COUNT(*) INTO @v66_candidate_count
FROM v66_daily_battery_candidate;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'all_current_sources_are_safe_candidates',
       CASE WHEN @v66_current_source_count = @v66_candidate_count
            THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'no_preexisting_v66_snapshot_collision',
       CASE WHEN @v66_candidate_count = 0 OR NOT EXISTS (
         SELECT 1
         FROM settlement_rule_snapshot existing_snapshot
         JOIN v66_daily_battery_candidate candidate
           ON existing_snapshot.snapshot_no = CONCAT('SNP-V66-', candidate.old_snapshot_id)
       ) THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'no_preexisting_v66_audit_collision',
       CASE WHEN @v66_candidate_count = 0 OR NOT EXISTS (
         SELECT 1
         FROM settlement_battery_recalculation_audit existing_audit
         JOIN v66_daily_battery_candidate candidate
           ON existing_audit.migration_code = 'V66'
          AND existing_audit.source_type = candidate.source_type
          AND existing_audit.source_id = candidate.source_id
       ) THEN 1 ELSE NULL END;

/* Recalculate all V3 amounts from the gross base and frozen rates. Channel,
 * platform, and referral are all gross-level; only the post-battery balance is
 * normalized across operation/maintenance/investor weights. */
INSERT INTO v66_daily_battery_expected (
  old_snapshot_id, new_channel_fee_amount, new_platform_fee_amount,
  new_battery_cost_amount, new_distributable_amount,
  new_store_operation_amount, new_maintenance_fund_amount,
  new_channel_referral_amount, new_investor_share_amount
)
SELECT weighted.old_snapshot_id,
       weighted.new_channel_fee_amount,
       weighted.new_platform_fee_amount,
       weighted.new_battery_cost_amount,
       weighted.new_distributable_amount,
       weighted.new_store_operation_amount,
       weighted.new_maintenance_fund_amount,
       weighted.new_channel_referral_amount,
       weighted.new_distributable_amount
         - weighted.new_store_operation_amount
         - weighted.new_maintenance_fund_amount
FROM (
  SELECT pooled.old_snapshot_id,
         pooled.new_channel_fee_amount,
         pooled.new_platform_fee_amount,
         pooled.new_battery_cost_amount,
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
    SELECT computed.old_snapshot_id,
           computed.new_channel_fee_amount,
           computed.new_platform_fee_amount,
           computed.new_battery_cost_amount,
           computed.settlement_base_amount
             - computed.new_channel_fee_amount
             - computed.new_platform_fee_amount
             - computed.new_battery_cost_amount
             - computed.new_channel_referral_amount AS new_distributable_amount,
           computed.store_operation_rate,
           computed.maintenance_fund_rate,
           computed.remaining_weight,
           computed.new_channel_referral_amount
    FROM (
      SELECT candidate.old_snapshot_id,
             snapshot_row.settlement_base_amount,
             ROUND(snapshot_row.settlement_base_amount * snapshot_row.channel_fee_rate, 2)
               AS new_channel_fee_amount,
             ROUND(snapshot_row.settlement_base_amount * snapshot_row.platform_fee_rate, 2)
               AS new_platform_fee_amount,
             CAST(ROUND(
               6.80 * TIMESTAMPDIFF(MICROSECOND, candidate.period_start_at, candidate.period_end_at)
                 / 86400000000,
               2
             ) AS DECIMAL(12, 2)) AS new_battery_cost_amount,
             snapshot_row.store_operation_rate,
             snapshot_row.maintenance_fund_rate,
             snapshot_row.store_operation_rate
               + snapshot_row.maintenance_fund_rate
               + snapshot_row.investor_share_rate AS remaining_weight,
             ROUND(snapshot_row.settlement_base_amount * snapshot_row.channel_referral_rate, 2)
               AS new_channel_referral_amount
      FROM v66_daily_battery_candidate candidate
      JOIN settlement_rule_snapshot snapshot_row
        ON snapshot_row.id = candidate.old_snapshot_id
    ) computed
  ) pooled
) weighted;

SELECT COUNT(*) INTO @v66_expected_count
FROM v66_daily_battery_expected;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'expected_amounts_are_complete_and_nonnegative',
       CASE WHEN @v66_expected_count = @v66_candidate_count
                  AND NOT EXISTS (
                    SELECT 1
                    FROM v66_daily_battery_expected expected
                    WHERE expected.new_channel_fee_amount < 0
                       OR expected.new_platform_fee_amount < 0
                       OR expected.new_battery_cost_amount < 0
                       OR expected.new_distributable_amount < 0
                       OR expected.new_store_operation_amount < 0
                       OR expected.new_maintenance_fund_amount < 0
                       OR expected.new_channel_referral_amount < 0
                       OR expected.new_investor_share_amount < 0
                  )
            THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'expected_amounts_are_balanced',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM v66_daily_battery_expected expected
         JOIN settlement_rule_snapshot old_snapshot
           ON old_snapshot.id = expected.old_snapshot_id
         WHERE expected.new_distributable_amount <>
               expected.new_store_operation_amount
                 + expected.new_maintenance_fund_amount
                 + expected.new_investor_share_amount
            OR old_snapshot.settlement_base_amount <>
               expected.new_channel_fee_amount
                 + expected.new_platform_fee_amount
                 + expected.new_battery_cost_amount
                 + expected.new_channel_referral_amount
                 + expected.new_store_operation_amount
                 + expected.new_maintenance_fund_amount
                 + expected.new_investor_share_amount
       ) THEN 1 ELSE NULL END;

/* No persistent business row is touched before the assertions above. */
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
SELECT CONCAT('SNP-V66-', old_snapshot.id),
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
       expected.new_channel_fee_amount,
       old_snapshot.platform_fee_rate,
       expected.new_platform_fee_amount,
       expected.new_battery_cost_amount,
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
       expected.new_platform_fee_amount,
       old_snapshot.investor_rent_share_rate,
       expected.new_investor_share_amount,
       old_snapshot.investor_operation_fee_amount,
       expected.new_maintenance_fund_amount,
       expected.new_investor_share_amount,
       LEFT(CONCAT(
         old_snapshot.rule_summary,
         ';batteryDailyRate=6.80;monthlyTier=false;backfill=V66'
       ), 1024),
       CURRENT_TIMESTAMP
FROM v66_daily_battery_candidate candidate
JOIN settlement_rule_snapshot old_snapshot
  ON old_snapshot.id = candidate.old_snapshot_id
JOIN v66_daily_battery_expected expected
  ON expected.old_snapshot_id = candidate.old_snapshot_id;

INSERT INTO settlement_battery_recalculation_audit (
  migration_code, source_type, source_id, old_snapshot_id, new_snapshot_id,
  period_start_at, period_end_at, daily_cost_amount,
  old_battery_cost_amount, new_battery_cost_amount,
  old_distributable_amount, new_distributable_amount,
  old_store_operation_amount, new_store_operation_amount,
  old_maintenance_fund_amount, new_maintenance_fund_amount,
  old_investor_share_amount, new_investor_share_amount, reason
)
SELECT 'V66',
       candidate.source_type,
       candidate.source_id,
       old_snapshot.id,
       new_snapshot.id,
       candidate.period_start_at,
       candidate.period_end_at,
       6.80,
       old_snapshot.battery_cost_amount,
       new_snapshot.battery_cost_amount,
       old_snapshot.distributable_amount,
       new_snapshot.distributable_amount,
       old_snapshot.store_operation_amount,
       new_snapshot.store_operation_amount,
       old_snapshot.maintenance_fund_amount,
       new_snapshot.maintenance_fund_amount,
       old_snapshot.investor_share_amount,
       new_snapshot.investor_share_amount,
       '电池成本取消200元月价阶梯，按精确租期每天6.80元回算'
FROM v66_daily_battery_candidate candidate
JOIN settlement_rule_snapshot old_snapshot
  ON old_snapshot.id = candidate.old_snapshot_id
JOIN settlement_rule_snapshot new_snapshot
  ON new_snapshot.snapshot_no = CONCAT('SNP-V66-', old_snapshot.id);

UPDATE external_rental_order source_row
JOIN settlement_battery_recalculation_audit audit_row
  ON audit_row.migration_code = 'V66'
 AND audit_row.source_type = 'EXTERNAL_ORDER'
 AND audit_row.source_id = source_row.id
SET source_row.settlement_snapshot_id = audit_row.new_snapshot_id,
    source_row.updated_at = source_row.updated_at
WHERE source_row.settlement_snapshot_id = audit_row.old_snapshot_id;

UPDATE external_order_renewal_event source_row
JOIN settlement_battery_recalculation_audit audit_row
  ON audit_row.migration_code = 'V66'
 AND audit_row.source_type = 'EXTERNAL_RENEWAL'
 AND audit_row.source_id = source_row.id
SET source_row.battery_cost_amount = audit_row.new_battery_cost_amount,
    source_row.settlement_snapshot_id = audit_row.new_snapshot_id,
    source_row.updated_at = source_row.updated_at
WHERE source_row.settlement_snapshot_id = audit_row.old_snapshot_id;

UPDATE settlement_income_entry income_row
JOIN settlement_battery_recalculation_audit audit_row
  ON audit_row.migration_code = 'V66'
 AND audit_row.source_type = income_row.source_type
 AND audit_row.source_id = income_row.source_id
 AND audit_row.old_snapshot_id = income_row.snapshot_id
JOIN settlement_rule_snapshot new_snapshot
  ON new_snapshot.id = audit_row.new_snapshot_id
SET income_row.snapshot_id = audit_row.new_snapshot_id,
    income_row.amount = CASE income_row.line_type
      WHEN 'CHANNEL_VERIFICATION_FEE' THEN new_snapshot.channel_fee_amount
      WHEN 'PLATFORM_SERVICE_FEE' THEN new_snapshot.platform_fee_amount
      WHEN 'STORE_OPERATION_SHARE' THEN new_snapshot.store_operation_amount
      WHEN 'MAINTENANCE_FUND_SHARE' THEN new_snapshot.maintenance_fund_amount
      WHEN 'CHANNEL_REFERRAL_SHARE' THEN new_snapshot.channel_referral_amount
      WHEN 'INVESTOR_SHARE' THEN new_snapshot.investor_share_amount
      ELSE income_row.amount
    END,
    income_row.remark = CASE
      WHEN income_row.line_type IN (
        'CHANNEL_VERIFICATION_FEE', 'PLATFORM_SERVICE_FEE',
        'STORE_OPERATION_SHARE', 'MAINTENANCE_FUND_SHARE',
        'CHANNEL_REFERRAL_SHARE', 'INVESTOR_SHARE'
      ) THEN LEFT(CONCAT('电池成本按6.80元/天回算；', COALESCE(income_row.remark, '')), 255)
      ELSE income_row.remark
    END
WHERE income_row.entry_status = 'PENDING';

UPDATE product_sku
SET battery_cost_daily_amount = 6.80,
    battery_cost_monthly_amount = NULL
WHERE COALESCE(battery_cost_daily_amount, 0) > 0
   OR COALESCE(battery_cost_monthly_amount, 0) > 0;

SELECT COUNT(*) INTO @v66_new_snapshot_count
FROM settlement_rule_snapshot new_snapshot
JOIN v66_daily_battery_candidate candidate
  ON new_snapshot.snapshot_no = CONCAT('SNP-V66-', candidate.old_snapshot_id);

SELECT COUNT(*) INTO @v66_new_audit_count
FROM settlement_battery_recalculation_audit audit_row
JOIN v66_daily_battery_candidate candidate
  ON candidate.old_snapshot_id = audit_row.old_snapshot_id
 AND candidate.source_type = audit_row.source_type
 AND candidate.source_id = audit_row.source_id
WHERE audit_row.migration_code = 'V66';

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'new_snapshot_and_audit_counts_match',
       CASE WHEN @v66_new_snapshot_count = @v66_candidate_count
                  AND @v66_new_audit_count = @v66_candidate_count
            THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'old_snapshots_are_retained',
       CASE WHEN @v66_candidate_count = (
         SELECT COUNT(*)
         FROM settlement_rule_snapshot old_snapshot
         JOIN v66_daily_battery_candidate candidate
           ON candidate.old_snapshot_id = old_snapshot.id
       ) THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'new_snapshots_and_audit_match_expected',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM v66_daily_battery_candidate candidate
         JOIN v66_daily_battery_expected expected
           ON expected.old_snapshot_id = candidate.old_snapshot_id
         LEFT JOIN settlement_battery_recalculation_audit audit_row
           ON audit_row.migration_code = 'V66'
          AND audit_row.source_type = candidate.source_type
          AND audit_row.source_id = candidate.source_id
          AND audit_row.old_snapshot_id = candidate.old_snapshot_id
         LEFT JOIN settlement_rule_snapshot new_snapshot
           ON new_snapshot.id = audit_row.new_snapshot_id
         WHERE audit_row.id IS NULL
            OR new_snapshot.id IS NULL
            OR new_snapshot.snapshot_no <> CONCAT('SNP-V66-', candidate.old_snapshot_id)
            OR new_snapshot.source_type <> candidate.source_type
            OR new_snapshot.source_id <> candidate.source_id
            OR new_snapshot.calculation_version <> 'PROFIT_V3'
            OR new_snapshot.channel_fee_amount <> expected.new_channel_fee_amount
            OR new_snapshot.platform_fee_amount <> expected.new_platform_fee_amount
            OR new_snapshot.battery_cost_amount <> expected.new_battery_cost_amount
            OR new_snapshot.distributable_amount <> expected.new_distributable_amount
            OR new_snapshot.store_operation_amount <> expected.new_store_operation_amount
            OR new_snapshot.maintenance_fund_amount <> expected.new_maintenance_fund_amount
            OR new_snapshot.channel_referral_amount <> expected.new_channel_referral_amount
            OR new_snapshot.investor_share_amount <> expected.new_investor_share_amount
            OR audit_row.period_start_at <> candidate.period_start_at
            OR audit_row.period_end_at <> candidate.period_end_at
            OR audit_row.daily_cost_amount <> 6.80
            OR audit_row.new_battery_cost_amount <> expected.new_battery_cost_amount
       ) THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'all_business_pointers_and_event_costs_reference_v66',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_battery_recalculation_audit audit_row
         LEFT JOIN external_rental_order source_row
           ON source_row.id = audit_row.source_id
          AND source_row.settlement_snapshot_id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V66'
           AND audit_row.source_type = 'EXTERNAL_ORDER'
           AND source_row.id IS NULL
       ) AND NOT EXISTS (
         SELECT 1
         FROM settlement_battery_recalculation_audit audit_row
         LEFT JOIN external_order_renewal_event source_row
           ON source_row.id = audit_row.source_id
          AND source_row.settlement_snapshot_id = audit_row.new_snapshot_id
          AND source_row.battery_cost_amount = audit_row.new_battery_cost_amount
         WHERE audit_row.migration_code = 'V66'
           AND audit_row.source_type = 'EXTERNAL_RENEWAL'
           AND source_row.id IS NULL
       ) THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'pending_income_rows_match_v66_and_conserve',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_battery_recalculation_audit audit_row
         JOIN settlement_rule_snapshot new_snapshot
           ON new_snapshot.id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V66'
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
                 AND income_row.line_type = 'CHANNEL_VERIFICATION_FEE'
                 AND income_row.amount = new_snapshot.channel_fee_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'PLATFORM_SERVICE_FEE'
                 AND income_row.amount = new_snapshot.platform_fee_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'STORE_OPERATION_SHARE'
                 AND income_row.amount = new_snapshot.store_operation_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'MAINTENANCE_FUND_SHARE'
                 AND income_row.amount = new_snapshot.maintenance_fund_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'CHANNEL_REFERRAL_SHARE'
                 AND income_row.amount = new_snapshot.channel_referral_amount
             )
             OR 1 <> (
               SELECT COUNT(*) FROM settlement_income_entry income_row
               WHERE income_row.source_type = audit_row.source_type
                 AND income_row.source_id = audit_row.source_id
                 AND income_row.line_type = 'INVESTOR_SHARE'
                 AND income_row.amount = new_snapshot.investor_share_amount
             )
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
           )
       ) THEN 1 ELSE NULL END;

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'each_v66_snapshot_is_balanced',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM settlement_battery_recalculation_audit audit_row
         JOIN settlement_rule_snapshot new_snapshot
           ON new_snapshot.id = audit_row.new_snapshot_id
         WHERE audit_row.migration_code = 'V66'
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

INSERT INTO v66_assertion (assertion_name, assertion_passed)
SELECT 'configured_battery_skus_use_daily_only',
       CASE WHEN NOT EXISTS (
         SELECT 1
         FROM product_sku
         WHERE COALESCE(battery_cost_daily_amount, 0) > 0
           AND (battery_cost_daily_amount <> 6.80
                OR battery_cost_monthly_amount IS NOT NULL)
       ) AND NOT EXISTS (
         SELECT 1
         FROM product_sku
         WHERE COALESCE(battery_cost_monthly_amount, 0) > 0
       ) THEN 1 ELSE NULL END;

DROP TEMPORARY TABLE IF EXISTS v66_assertion;
DROP TEMPORARY TABLE IF EXISTS v66_daily_battery_expected;
DROP TEMPORARY TABLE IF EXISTS v66_daily_battery_candidate;
DROP TEMPORARY TABLE IF EXISTS v66_daily_battery_current_source;

COMMIT;
