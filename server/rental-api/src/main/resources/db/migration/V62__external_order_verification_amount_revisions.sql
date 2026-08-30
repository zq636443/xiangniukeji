/* All order/package DATETIME values are stored as Asia/Shanghai local time.
 * Pin the migration session before comparing them with Flyway's TIMESTAMP. */
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS external_order_verification_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  external_order_id BIGINT NOT NULL,
  verification_amount DECIMAL(12, 2) NOT NULL,
  effective_at DATETIME(6) NOT NULL,
  revision_type VARCHAR(24) NOT NULL,
  source_snapshot_id BIGINT NULL,
  operator_account_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_external_verification_revision_snapshot (external_order_id, source_snapshot_id),
  KEY idx_external_verification_revision_order_time (external_order_id, effective_at, id),
  KEY idx_external_verification_revision_snapshot (source_snapshot_id)
);

/* Keep the DDL retry-safe: MySQL commits ALTER TABLE independently, so a
 * failed data-repair statement must be able to rerun without tripping over an
 * already-created column.  Production runs MySQL 8.0.46, which supports the
 * IF NOT EXISTS form (and MariaDB accepts it as well). */
ALTER TABLE external_order_renewal_event
  ADD COLUMN IF NOT EXISTS system_renewal_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER renewal_amount;

UPDATE external_order_renewal_event
SET system_renewal_amount = renewal_amount
WHERE system_renewal_amount = 0.00
  AND renewal_amount <> 0.00;

/*
 * Preserve the first settlement snapshot as the initial-period baseline.  The
 * revision stores the snapshot's explicit gross rental/verification amount.
 * Do not use settlement_base_amount here: that field is a derived settlement
 * base and its meaning varies with the calculation version.  The initial value
 * is intentionally effective at rent_started_at: it must not override later
 * renewal periods unless a subsequent ORDER_EDIT exists.
 */
INSERT INTO external_order_verification_revision
    (external_order_id, verification_amount, effective_at, revision_type, source_snapshot_id)
SELECT eo.id,
       first_snapshot.rental_amount,
       eo.rent_started_at,
       'INITIAL',
       first_snapshot.id
FROM external_rental_order eo
JOIN settlement_rule_snapshot first_snapshot
  ON first_snapshot.id = (
    SELECT s.id
    FROM settlement_rule_snapshot s
    WHERE s.source_type = 'EXTERNAL_ORDER'
      AND s.source_id = eo.id
    ORDER BY s.created_at, s.id
    LIMIT 1
  )
LEFT JOIN external_order_verification_revision existing_revision
  ON existing_revision.external_order_id = eo.id
 AND existing_revision.source_snapshot_id = first_snapshot.id
WHERE existing_revision.id IS NULL;

/*
 * A snapshot whose gross amount changed together with a settlement structure
 * change is not safe to classify from the amount alone.  The application
 * writes an EDIT audit row in the same second as the replacement snapshot;
 * require that durable signal (and a real operator) before treating it as a
 * verification edit.  This covers historical edits such as orders 58/59,
 * while leaving system/rebuild snapshots without an EDIT log for review.
 * ORDER_EDIT is used intentionally: it is already understood by the Java
 * timeline calculator and starts the new amount at the persisted edit time.
 */
INSERT INTO external_order_verification_revision
    (external_order_id, verification_amount, effective_at, revision_type,
     source_snapshot_id, operator_account_id)
SELECT s.source_id,
       s.rental_amount,
       /* The audit log is the human action boundary.  Snapshot creation can
        * lag that action (and may have fractional precision), so use the
        * operator's recorded edit time for the effective timeline. */
       edit_log.created_at,
       'ORDER_EDIT',
       s.id,
       edit_log.operator_account_id
FROM settlement_rule_snapshot s
JOIN external_rental_order eo
  ON eo.id = s.source_id
JOIN settlement_rule_snapshot previous_snapshot
  ON previous_snapshot.id = (
    SELECT candidate.id
    FROM settlement_rule_snapshot candidate
    WHERE candidate.source_type = 'EXTERNAL_ORDER'
      AND candidate.source_id = s.source_id
      AND (
        candidate.created_at < s.created_at
        OR (candidate.created_at = s.created_at AND candidate.id < s.id)
      )
    ORDER BY candidate.created_at DESC, candidate.id DESC
    LIMIT 1
  )
JOIN external_rental_order_log edit_log
  ON edit_log.id = (
    SELECT candidate_log.id
    FROM external_rental_order_log candidate_log
    WHERE candidate_log.external_order_id = s.source_id
      AND candidate_log.operation_type = 'EDIT'
      AND candidate_log.operator_account_id IS NOT NULL
      /* external_rental_order_log is DATETIME(0) while snapshots may carry
       * fractional precision; floor the snapshot to its log's second so a
       * truncated same-second EDIT is retained without matching a later edit. */
      AND candidate_log.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND candidate_log.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
    ORDER BY candidate_log.id
    LIMIT 1
  )
LEFT JOIN external_order_verification_revision existing_revision
  ON existing_revision.external_order_id = s.source_id
 AND existing_revision.source_snapshot_id = s.id
WHERE s.source_type = 'EXTERNAL_ORDER'
  AND s.id <> (
    SELECT first_snapshot.id
    FROM settlement_rule_snapshot first_snapshot
    WHERE first_snapshot.source_type = 'EXTERNAL_ORDER'
      AND first_snapshot.source_id = eo.id
    ORDER BY first_snapshot.created_at, first_snapshot.id
    LIMIT 1
  )
  AND NOT (s.rental_amount <=> previous_snapshot.rental_amount)
  AND NOT (
    (s.calculation_version <=> previous_snapshot.calculation_version)
    AND (s.source_channel <=> previous_snapshot.source_channel)
    AND (s.store_sku_id <=> previous_snapshot.store_sku_id)
    AND (s.sku_id <=> previous_snapshot.sku_id)
    AND (s.merchant_id <=> previous_snapshot.merchant_id)
    AND (s.store_id <=> previous_snapshot.store_id)
    AND (s.frame_asset_id <=> previous_snapshot.frame_asset_id)
    AND (s.battery_asset_id <=> previous_snapshot.battery_asset_id)
    AND (s.matched_rule_id <=> previous_snapshot.matched_rule_id)
    AND (s.matched_rule_scope <=> previous_snapshot.matched_rule_scope)
    AND (s.sign_fee_amount <=> previous_snapshot.sign_fee_amount)
    AND (s.merchant_order_fee_amount <=> previous_snapshot.merchant_order_fee_amount)
    AND (s.merchant_rent_share_rate <=> previous_snapshot.merchant_rent_share_rate)
    AND (s.platform_rent_share_rate <=> previous_snapshot.platform_rent_share_rate)
    AND (s.investor_rent_share_rate <=> previous_snapshot.investor_rent_share_rate)
    AND (s.battery_cost_amount <=> previous_snapshot.battery_cost_amount)
    AND (s.channel_fee_rate <=> previous_snapshot.channel_fee_rate)
    AND (s.platform_fee_rate <=> previous_snapshot.platform_fee_rate)
    AND (s.store_operation_rate <=> previous_snapshot.store_operation_rate)
    AND (s.maintenance_fund_rate <=> previous_snapshot.maintenance_fund_rate)
    AND (s.channel_referral_rate <=> previous_snapshot.channel_referral_rate)
    AND (s.investor_share_rate <=> previous_snapshot.investor_share_rate)
  )
  /* DATETIME(0) audit rows cannot distinguish multiple edits/snapshots in
   * one second.  Auto-repair only a one-log/one-snapshot second; every
   * ambiguous second is intentionally left for manual review instead of
   * assigning the earliest log to several amount revisions. */
  AND (
    SELECT COUNT(1)
    FROM external_rental_order_log same_second_log
    WHERE same_second_log.external_order_id = s.source_id
      AND same_second_log.operation_type = 'EDIT'
      AND same_second_log.operator_account_id IS NOT NULL
      AND same_second_log.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND same_second_log.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
  ) = 1
  AND (
    SELECT COUNT(1)
    FROM settlement_rule_snapshot same_second_snapshot
    WHERE same_second_snapshot.source_type = 'EXTERNAL_ORDER'
      AND same_second_snapshot.source_id = s.source_id
      AND same_second_snapshot.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND same_second_snapshot.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
  ) = 1
  AND existing_revision.id IS NULL;

/*
 * V58 copied rental_amount into existing order renewal_amount. Restore the
 * order's pre-sync frozen renewal amount for those orders, except where an
 * explicit pricing adjustment was applied afterwards. The V58 revision keeps
 * the value that was overwritten, so prefer it over today's SKU price (the
 * SKU may have been repriced since the migration).
 */
UPDATE external_rental_order eo
LEFT JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
LEFT JOIN (
  SELECT external_order_id, id AS revision_id
  FROM (
    SELECT p.external_order_id,
           p.id,
           ROW_NUMBER() OVER (
             PARTITION BY p.external_order_id
             ORDER BY COALESCE(p.applied_at, p.created_at) DESC, p.id DESC
           ) AS revision_rank
    FROM external_order_pricing_revision p
    WHERE p.revision_status = 'APPLIED'
      AND (p.batch_no IS NULL OR p.batch_no NOT IN (
        'SKU-PRICE-SYNC-V58',
        'SKU-RENEWAL-RECOVERY-V62'
      ))
  ) ranked_manual
  WHERE revision_rank = 1
) latest_manual ON latest_manual.external_order_id = eo.id
LEFT JOIN external_order_pricing_revision manual
  ON manual.id = latest_manual.revision_id
LEFT JOIN (
  SELECT external_order_id, MAX(id) AS revision_id
  FROM external_order_pricing_revision
  WHERE batch_no = 'SKU-PRICE-SYNC-V58'
    AND revision_status = 'APPLIED'
  GROUP BY external_order_id
) latest_sync ON latest_sync.external_order_id = eo.id
LEFT JOIN external_order_pricing_revision sync_revision
  ON sync_revision.id = latest_sync.revision_id
SET eo.renewal_amount = CASE
  WHEN latest_manual.revision_id IS NOT NULL
    AND manual.new_auto_renew_enabled = 1
    AND manual.new_renewal_amount IS NOT NULL
    AND manual.new_renewal_amount > 0
    THEN manual.new_renewal_amount
  WHEN latest_manual.revision_id IS NOT NULL THEN eo.renewal_amount
  WHEN sync_revision.previous_renewal_amount IS NOT NULL
    AND sync_revision.previous_renewal_amount > 0
    AND NOT (sync_revision.previous_renewal_amount <=> sync_revision.new_renewal_amount)
    THEN sync_revision.previous_renewal_amount
  ELSE COALESCE(sp.renewal_amount, sp.period_amount)
END
WHERE eo.auto_renew_enabled = 1
  AND eo.order_status = 'ACTIVE'
  AND EXISTS (
    SELECT 1
    FROM external_order_pricing_revision sync_revision
    WHERE sync_revision.external_order_id = eo.id
      AND sync_revision.batch_no = 'SKU-PRICE-SYNC-V58'
      AND sync_revision.revision_status = 'APPLIED'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision pending_revision
    WHERE pending_revision.external_order_id = eo.id
      AND (pending_revision.batch_no IS NULL OR pending_revision.batch_no <> 'SKU-PRICE-SYNC-V58')
      AND pending_revision.revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
  )
  /* This update changes only the order's future renewal configuration.  It
   * never rewrites an initial snapshot, income row, statement line, or event.
   * Those historical rows remain immutable through the event-level guards
   * below; blocking the order baseline merely because an initial row is locked
   * would leave every later renewal on the known V58 first-period amount. */
  /* Never infer a renewal baseline through an amount change that also
   * changed settlement structure unless the auditable ORDER_EDIT revision
   * above (or a later application edit) explicitly covers that snapshot. */
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_rule_snapshot changed_snapshot
    JOIN settlement_rule_snapshot previous_snapshot
      ON previous_snapshot.id = (
        SELECT candidate.id
        FROM settlement_rule_snapshot candidate
        WHERE candidate.source_type = 'EXTERNAL_ORDER'
          AND candidate.source_id = changed_snapshot.source_id
          AND (
            candidate.created_at < changed_snapshot.created_at
            OR (candidate.created_at = changed_snapshot.created_at
              AND candidate.id < changed_snapshot.id)
          )
        ORDER BY candidate.created_at DESC, candidate.id DESC
        LIMIT 1
      )
    WHERE changed_snapshot.source_type = 'EXTERNAL_ORDER'
      AND changed_snapshot.source_id = eo.id
      AND NOT (changed_snapshot.rental_amount <=> previous_snapshot.rental_amount)
      AND NOT (
        (changed_snapshot.calculation_version <=> previous_snapshot.calculation_version)
        AND (changed_snapshot.source_channel <=> previous_snapshot.source_channel)
        AND (changed_snapshot.store_sku_id <=> previous_snapshot.store_sku_id)
        AND (changed_snapshot.sku_id <=> previous_snapshot.sku_id)
        AND (changed_snapshot.merchant_id <=> previous_snapshot.merchant_id)
        AND (changed_snapshot.store_id <=> previous_snapshot.store_id)
        AND (changed_snapshot.frame_asset_id <=> previous_snapshot.frame_asset_id)
        AND (changed_snapshot.battery_asset_id <=> previous_snapshot.battery_asset_id)
        AND (changed_snapshot.matched_rule_id <=> previous_snapshot.matched_rule_id)
        AND (changed_snapshot.matched_rule_scope <=> previous_snapshot.matched_rule_scope)
        AND (changed_snapshot.sign_fee_amount <=> previous_snapshot.sign_fee_amount)
        AND (changed_snapshot.merchant_order_fee_amount <=> previous_snapshot.merchant_order_fee_amount)
        AND (changed_snapshot.merchant_rent_share_rate <=> previous_snapshot.merchant_rent_share_rate)
        AND (changed_snapshot.platform_rent_share_rate <=> previous_snapshot.platform_rent_share_rate)
        AND (changed_snapshot.investor_rent_share_rate <=> previous_snapshot.investor_rent_share_rate)
        AND (changed_snapshot.battery_cost_amount <=> previous_snapshot.battery_cost_amount)
        AND (changed_snapshot.channel_fee_rate <=> previous_snapshot.channel_fee_rate)
        AND (changed_snapshot.platform_fee_rate <=> previous_snapshot.platform_fee_rate)
        AND (changed_snapshot.store_operation_rate <=> previous_snapshot.store_operation_rate)
        AND (changed_snapshot.maintenance_fund_rate <=> previous_snapshot.maintenance_fund_rate)
        AND (changed_snapshot.channel_referral_rate <=> previous_snapshot.channel_referral_rate)
        AND (changed_snapshot.investor_share_rate <=> previous_snapshot.investor_share_rate)
      )
      AND NOT EXISTS (
        SELECT 1
        FROM external_order_verification_revision covered_revision
        WHERE covered_revision.external_order_id = changed_snapshot.source_id
          AND covered_revision.source_snapshot_id = changed_snapshot.id
      )
  )
  AND (
    latest_manual.revision_id IS NOT NULL
    OR (eo.renewal_amount <=> sync_revision.new_renewal_amount)
  )
  AND (
    (manual.new_auto_renew_enabled = 1
      AND manual.new_renewal_amount IS NOT NULL
      AND manual.new_renewal_amount > 0)
    OR (sync_revision.previous_renewal_amount IS NOT NULL
      AND sync_revision.previous_renewal_amount > 0
      AND NOT (sync_revision.previous_renewal_amount <=> sync_revision.new_renewal_amount))
    OR (COALESCE(sp.renewal_amount, sp.period_amount) > 0)
  )
  AND NOT (eo.renewal_amount <=> CASE
    WHEN latest_manual.revision_id IS NOT NULL
      AND manual.new_auto_renew_enabled = 1
      AND manual.new_renewal_amount IS NOT NULL
      AND manual.new_renewal_amount > 0
      THEN manual.new_renewal_amount
    WHEN latest_manual.revision_id IS NOT NULL THEN eo.renewal_amount
    WHEN sync_revision.previous_renewal_amount IS NOT NULL
      AND sync_revision.previous_renewal_amount > 0
      AND NOT (sync_revision.previous_renewal_amount <=> sync_revision.new_renewal_amount)
      THEN sync_revision.previous_renewal_amount
    ELSE COALESCE(sp.renewal_amount, sp.period_amount)
  END);

/*
 * Orders created after V58 inherited the same rental-price bug in application
 * code, but did not receive the V58 audit revision because they did not exist
 * when that migration ran. There is no historical SKU price table from which
 * to recover their creation-time renewal price. For the narrow, detectable
 * population below, freeze the configured SKU renewal amount proven to have
 * existed when the order was created and write an explicit audit revision
 * before changing the order. Ambiguous historical rows, completed orders,
 * settlement locks, and every manual pricing workflow are deliberately
 * excluded for separate review. Existing mutable renewal events are repaired
 * below using the same old-value guard.
 */
INSERT INTO external_order_pricing_revision (
  external_order_id,
  batch_no,
  revision_status,
  requires_customer_confirmation,
  previous_auto_renew_enabled,
  previous_renewal_unit,
  previous_renewal_value,
  previous_renewal_amount,
  previous_billing_mode,
  previous_daily_amount,
  previous_daily_cap_enabled,
  previous_grace_hours,
  previous_overdue_daily_amount,
  new_auto_renew_enabled,
  new_renewal_unit,
  new_renewal_value,
  new_renewal_amount,
  new_billing_mode,
  new_daily_amount,
  new_daily_cap_enabled,
  new_grace_hours,
  new_overdue_daily_amount,
  reason,
  confirmation_method,
  applied_at
)
SELECT eo.id,
       'SKU-RENEWAL-RECOVERY-V62',
       'APPLIED',
       0,
       eo.auto_renew_enabled,
       eo.renewal_unit,
       eo.renewal_value,
       eo.renewal_amount,
       eo.renewal_billing_mode,
       eo.renewal_daily_amount,
       eo.renewal_daily_cap_enabled,
       eo.renewal_grace_hours,
       eo.overdue_daily_amount,
       eo.auto_renew_enabled,
       eo.renewal_unit,
       eo.renewal_value,
       COALESCE(sp.renewal_amount, sp.period_amount),
       eo.renewal_billing_mode,
       eo.renewal_daily_amount,
       eo.renewal_daily_cap_enabled,
       eo.renewal_grace_hours,
       eo.overdue_daily_amount,
       '恢复 V58 上线后补录订单被冻结为首期租金的系统续租金额',
       'SYSTEM',
       CURRENT_TIMESTAMP
FROM external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
LEFT JOIN external_order_pricing_revision existing_revision
  ON existing_revision.external_order_id = eo.id
WHERE eo.auto_renew_enabled = 1
  AND eo.order_status = 'ACTIVE'
  AND COALESCE(sp.renewal_amount, sp.period_amount) > 0
  AND (eo.renewal_amount <=> sp.rental_amount)
  AND NOT (eo.renewal_amount <=> COALESCE(sp.renewal_amount, sp.period_amount))
  AND sp.created_at <= eo.created_at
  AND sp.updated_at <= eo.created_at
  AND EXISTS (
    SELECT 1
    FROM flyway_schema_history v58_marker
    WHERE v58_marker.version = '58'
      AND v58_marker.script = 'V58__sync_external_order_renewal_to_sku_price.sql'
      AND v58_marker.checksum = 2085066607
      AND v58_marker.success = 1
  )
  AND eo.created_at > (
    /* Use the first successful install boundary.  A repaired/replayed history
     * row must not make orders created between the original V58 cutover and
     * the repair look like pre-V58 data. */
    SELECT MIN(installed_on)
    FROM flyway_schema_history
    WHERE version = '58'
      AND script = 'V58__sync_external_order_renewal_to_sku_price.sql'
      AND checksum = 2085066607
      AND success = 1
  )
  AND existing_revision.id IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry locked_income
    WHERE locked_income.source_type = 'EXTERNAL_ORDER'
      AND locked_income.source_id = eo.id
      AND locked_income.entry_status <> 'PENDING'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = 'EXTERNAL_ORDER'
      AND locked_line.source_id = eo.id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

UPDATE external_rental_order eo
JOIN external_order_pricing_revision recovery_revision
  ON recovery_revision.external_order_id = eo.id
 AND recovery_revision.batch_no = 'SKU-RENEWAL-RECOVERY-V62'
 AND recovery_revision.revision_status = 'APPLIED'
SET eo.renewal_amount = recovery_revision.new_renewal_amount
WHERE recovery_revision.new_renewal_amount IS NOT NULL
  AND recovery_revision.new_renewal_amount > 0
  AND eo.order_status = 'ACTIVE'
  AND (eo.renewal_amount <=> recovery_revision.previous_renewal_amount)
  AND NOT (eo.renewal_amount <=> recovery_revision.new_renewal_amount)
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision concurrent_revision
    WHERE concurrent_revision.external_order_id = eo.id
      AND (concurrent_revision.batch_no IS NULL OR concurrent_revision.batch_no <> 'SKU-RENEWAL-RECOVERY-V62')
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry locked_income
    WHERE locked_income.source_type = 'EXTERNAL_ORDER'
      AND locked_income.source_id = eo.id
      AND locked_income.entry_status <> 'PENDING'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = 'EXTERNAL_ORDER'
      AND locked_line.source_id = eo.id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

/* Repair an already-accrued event from the same post-V58 fingerprint only
 * while its frozen baseline still equals the known bad order value. Locked
 * event income/statements remain immutable and the startup reconciler will
 * rebuild the other mutable rows from the new baseline. */
UPDATE external_order_renewal_event r
JOIN external_order_pricing_revision recovery_revision
  ON recovery_revision.external_order_id = r.external_order_id
 AND recovery_revision.batch_no = 'SKU-RENEWAL-RECOVERY-V62'
 AND recovery_revision.revision_status = 'APPLIED'
SET r.system_renewal_amount = recovery_revision.new_renewal_amount
WHERE r.event_status = 'ACCRUED'
  AND (r.system_renewal_amount <=> recovery_revision.previous_renewal_amount)
  AND recovery_revision.new_renewal_amount IS NOT NULL
  AND recovery_revision.new_renewal_amount > 0
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry locked_income
    WHERE locked_income.source_type = 'EXTERNAL_RENEWAL'
      AND locked_income.source_id = r.id
      AND locked_income.entry_status <> 'PENDING'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = 'EXTERNAL_RENEWAL'
      AND locked_line.source_id = r.id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

/*
 * V58 used the first-period rental price for existing renewal events. Keep
 * every already locked event immutable, but correct the frozen system baseline
 * for mutable V58 rows so the application reconciliation runner can rebuild
 * their snapshots and income entries using the frozen renewal price. A later
 * pricing adjustment applies only from its effective time; an event that
 * started before that adjustment keeps the previous baseline.
 */
UPDATE external_order_renewal_event r
JOIN external_rental_order eo ON eo.id = r.external_order_id
LEFT JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
LEFT JOIN (
  SELECT external_order_id, MAX(id) AS revision_id
  FROM external_order_pricing_revision
  WHERE batch_no = 'SKU-PRICE-SYNC-V58'
    AND revision_status = 'APPLIED'
  GROUP BY external_order_id
) latest_sync ON latest_sync.external_order_id = eo.id
LEFT JOIN external_order_pricing_revision sync_revision
  ON sync_revision.id = latest_sync.revision_id
LEFT JOIN external_order_pricing_revision period_manual
  ON period_manual.id = (
    SELECT p.id
    FROM external_order_pricing_revision p
    WHERE p.external_order_id = eo.id
      AND p.revision_status = 'APPLIED'
      AND (p.batch_no IS NULL OR p.batch_no NOT IN (
        'SKU-PRICE-SYNC-V58',
        'SKU-RENEWAL-RECOVERY-V62'
      ))
      AND p.new_auto_renew_enabled = 1
      AND p.new_renewal_amount IS NOT NULL
      AND p.new_renewal_amount > 0
      AND COALESCE(p.applied_at, p.created_at) <= r.period_start_at
    ORDER BY COALESCE(p.applied_at, p.created_at) DESC, p.id DESC
    LIMIT 1
  )
SET r.system_renewal_amount = CASE
  WHEN period_manual.new_renewal_amount IS NOT NULL
    AND period_manual.new_renewal_amount > 0
    THEN period_manual.new_renewal_amount
  WHEN sync_revision.previous_renewal_amount IS NOT NULL
    AND sync_revision.previous_renewal_amount > 0
    AND NOT (sync_revision.previous_renewal_amount <=> sync_revision.new_renewal_amount)
    THEN sync_revision.previous_renewal_amount
  ELSE COALESCE(sp.renewal_amount, sp.period_amount)
END
WHERE r.event_status = 'ACCRUED'
  /* A COMPLETED order can still have an accrued, not-yet-settled renewal
   * event.  Order status alone is therefore not a lock; the event-level
   * income/statement guards below are the mutability boundary. */
  /* The new column is initialized from renewal_amount immediately above.
   * Do not overwrite a baseline that an application edit/reconciliation has
   * already changed (old-value CAS); the runner will use that row's timeline. */
  AND (r.system_renewal_amount <=> r.renewal_amount)
  /* For rows that predate V62, the initialization above cannot distinguish a
   * pre-existing manual event amount from the V58 value.  Restrict this
   * migration-owned update to a known V58 old/new amount (or the effective
   * manual amount for this period); an unknown amount is left for review. */
  AND (
    r.renewal_amount <=> sync_revision.new_renewal_amount
    OR r.renewal_amount <=> sync_revision.previous_renewal_amount
    OR (period_manual.new_renewal_amount IS NOT NULL
      AND r.renewal_amount <=> period_manual.new_renewal_amount)
  )
  AND EXISTS (
    SELECT 1
    FROM external_order_pricing_revision sync_revision
    WHERE sync_revision.external_order_id = eo.id
      AND sync_revision.batch_no = 'SKU-PRICE-SYNC-V58'
      AND sync_revision.revision_status = 'APPLIED'
  )
  AND (
    (period_manual.new_renewal_amount IS NOT NULL AND period_manual.new_renewal_amount > 0)
    OR (sync_revision.previous_renewal_amount IS NOT NULL
      AND sync_revision.previous_renewal_amount > 0
      AND NOT (sync_revision.previous_renewal_amount <=> sync_revision.new_renewal_amount))
    OR (COALESCE(sp.renewal_amount, sp.period_amount) > 0)
  )
  /* A pending customer-confirmation edit is the authoritative future
   * proposal until it is accepted or cancelled.  Keep its accrued event on
   * the currently persisted amount; the application will reconcile it after
   * confirmation instead of rolling it back to the V58 predecessor here. */
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision pending_revision
    WHERE pending_revision.external_order_id = eo.id
      AND (pending_revision.batch_no IS NULL OR pending_revision.batch_no <> 'SKU-PRICE-SYNC-V58')
      AND pending_revision.revision_status = 'PENDING_CUSTOMER_CONFIRMATION'
  )
  /* A structure+amount snapshot without an auditable verification revision
   * is ambiguous (package/store/asset/rule changes may explain the amount).
   * Leave that event untouched for review instead of rolling it back to the
   * V58 value.  The structural INSERT above covers only matching EDIT logs. */
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_rule_snapshot changed_snapshot
    JOIN settlement_rule_snapshot previous_snapshot
      ON previous_snapshot.id = (
        SELECT candidate.id
        FROM settlement_rule_snapshot candidate
        WHERE candidate.source_type = 'EXTERNAL_ORDER'
          AND candidate.source_id = changed_snapshot.source_id
          AND (
            candidate.created_at < changed_snapshot.created_at
            OR (candidate.created_at = changed_snapshot.created_at
              AND candidate.id < changed_snapshot.id)
          )
        ORDER BY candidate.created_at DESC, candidate.id DESC
        LIMIT 1
      )
    WHERE changed_snapshot.source_type = 'EXTERNAL_ORDER'
      AND changed_snapshot.source_id = eo.id
      AND NOT (changed_snapshot.rental_amount <=> previous_snapshot.rental_amount)
      AND NOT (
        (changed_snapshot.calculation_version <=> previous_snapshot.calculation_version)
        AND (changed_snapshot.source_channel <=> previous_snapshot.source_channel)
        AND (changed_snapshot.store_sku_id <=> previous_snapshot.store_sku_id)
        AND (changed_snapshot.sku_id <=> previous_snapshot.sku_id)
        AND (changed_snapshot.merchant_id <=> previous_snapshot.merchant_id)
        AND (changed_snapshot.store_id <=> previous_snapshot.store_id)
        AND (changed_snapshot.frame_asset_id <=> previous_snapshot.frame_asset_id)
        AND (changed_snapshot.battery_asset_id <=> previous_snapshot.battery_asset_id)
        AND (changed_snapshot.matched_rule_id <=> previous_snapshot.matched_rule_id)
        AND (changed_snapshot.matched_rule_scope <=> previous_snapshot.matched_rule_scope)
        AND (changed_snapshot.sign_fee_amount <=> previous_snapshot.sign_fee_amount)
        AND (changed_snapshot.merchant_order_fee_amount <=> previous_snapshot.merchant_order_fee_amount)
        AND (changed_snapshot.merchant_rent_share_rate <=> previous_snapshot.merchant_rent_share_rate)
        AND (changed_snapshot.platform_rent_share_rate <=> previous_snapshot.platform_rent_share_rate)
        AND (changed_snapshot.investor_rent_share_rate <=> previous_snapshot.investor_rent_share_rate)
        AND (changed_snapshot.battery_cost_amount <=> previous_snapshot.battery_cost_amount)
        AND (changed_snapshot.channel_fee_rate <=> previous_snapshot.channel_fee_rate)
        AND (changed_snapshot.platform_fee_rate <=> previous_snapshot.platform_fee_rate)
        AND (changed_snapshot.store_operation_rate <=> previous_snapshot.store_operation_rate)
        AND (changed_snapshot.maintenance_fund_rate <=> previous_snapshot.maintenance_fund_rate)
        AND (changed_snapshot.channel_referral_rate <=> previous_snapshot.channel_referral_rate)
        AND (changed_snapshot.investor_share_rate <=> previous_snapshot.investor_share_rate)
      )
      AND NOT EXISTS (
        SELECT 1
        FROM external_order_verification_revision covered_revision
        WHERE covered_revision.external_order_id = changed_snapshot.source_id
          AND covered_revision.source_snapshot_id = changed_snapshot.id
      )
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry locked_income
    WHERE locked_income.source_type = 'EXTERNAL_RENEWAL'
      AND locked_income.source_id = r.id
      AND locked_income.entry_status <> 'PENDING'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = 'EXTERNAL_RENEWAL'
      AND locked_line.source_id = r.id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

/*
 * Pure amount-only changes are the safe BACKFILL population: the settlement
 * gross rental amount differs while every structural settlement input remains unchanged from
 * the immediately preceding snapshot.  Structural+amount changes are handled
 * by the audited ORDER_EDIT insert above; any remaining structural change is
 * deliberately left for manual review.  New edits are recorded directly with
 * the database edit timestamp.
 */
INSERT INTO external_order_verification_revision
    (external_order_id, verification_amount, effective_at, revision_type,
     source_snapshot_id, operator_account_id)
SELECT s.source_id,
       s.rental_amount,
       /* Prefer the durable human EDIT timestamp when it can be matched.
        * Older rows without an audit log fall back to snapshot persistence
        * time, which is the narrowest safe historical boundary available. */
       COALESCE(edit_log.created_at, s.created_at),
       'BACKFILL',
       s.id,
       edit_log.operator_account_id
FROM settlement_rule_snapshot s
JOIN external_rental_order eo ON eo.id = s.source_id
JOIN settlement_rule_snapshot previous_snapshot
  ON previous_snapshot.id = (
    SELECT candidate.id
    FROM settlement_rule_snapshot candidate
    WHERE candidate.source_type = 'EXTERNAL_ORDER'
      AND candidate.source_id = s.source_id
      AND (
        candidate.created_at < s.created_at
        OR (candidate.created_at = s.created_at AND candidate.id < s.id)
      )
    ORDER BY candidate.created_at DESC, candidate.id DESC
    LIMIT 1
  )
LEFT JOIN external_rental_order_log edit_log
  ON edit_log.id = (
    SELECT candidate_log.id
    FROM external_rental_order_log candidate_log
    WHERE candidate_log.external_order_id = s.source_id
      AND candidate_log.operation_type = 'EDIT'
      AND candidate_log.operator_account_id IS NOT NULL
      AND candidate_log.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND candidate_log.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
    ORDER BY candidate_log.id
    LIMIT 1
  )
LEFT JOIN external_order_verification_revision existing_revision
  ON existing_revision.external_order_id = s.source_id
 AND existing_revision.source_snapshot_id = s.id
WHERE s.source_type = 'EXTERNAL_ORDER'
  AND s.id <> (
    SELECT first_snapshot.id
    FROM settlement_rule_snapshot first_snapshot
    WHERE first_snapshot.source_type = 'EXTERNAL_ORDER'
      AND first_snapshot.source_id = eo.id
    ORDER BY first_snapshot.created_at, first_snapshot.id
    LIMIT 1
  )
  AND NOT (s.rental_amount <=> previous_snapshot.rental_amount)
  AND (s.calculation_version <=> previous_snapshot.calculation_version)
  AND (s.source_channel <=> previous_snapshot.source_channel)
  AND (s.store_sku_id <=> previous_snapshot.store_sku_id)
  AND (s.sku_id <=> previous_snapshot.sku_id)
  AND (s.merchant_id <=> previous_snapshot.merchant_id)
  AND (s.store_id <=> previous_snapshot.store_id)
  AND (s.frame_asset_id <=> previous_snapshot.frame_asset_id)
  AND (s.battery_asset_id <=> previous_snapshot.battery_asset_id)
  AND (s.matched_rule_id <=> previous_snapshot.matched_rule_id)
  AND (s.matched_rule_scope <=> previous_snapshot.matched_rule_scope)
  AND (s.sign_fee_amount <=> previous_snapshot.sign_fee_amount)
  AND (s.merchant_order_fee_amount <=> previous_snapshot.merchant_order_fee_amount)
  AND (s.merchant_rent_share_rate <=> previous_snapshot.merchant_rent_share_rate)
  AND (s.platform_rent_share_rate <=> previous_snapshot.platform_rent_share_rate)
  AND (s.investor_rent_share_rate <=> previous_snapshot.investor_rent_share_rate)
  AND (s.battery_cost_amount <=> previous_snapshot.battery_cost_amount)
  AND (s.channel_fee_rate <=> previous_snapshot.channel_fee_rate)
  AND (s.platform_fee_rate <=> previous_snapshot.platform_fee_rate)
  AND (s.store_operation_rate <=> previous_snapshot.store_operation_rate)
  AND (s.maintenance_fund_rate <=> previous_snapshot.maintenance_fund_rate)
  AND (s.channel_referral_rate <=> previous_snapshot.channel_referral_rate)
  AND (s.investor_share_rate <=> previous_snapshot.investor_share_rate)
  /* Preserve only unambiguous historical timing.  No matching EDIT log is
   * allowed (snapshot time is then the best available boundary), or exactly
   * one matching log may supply the operator time. Multiple logs/snapshots in
   * one DATETIME(0) second are left for manual review. */
  AND (
    SELECT COUNT(1)
    FROM external_rental_order_log same_second_log
    WHERE same_second_log.external_order_id = s.source_id
      AND same_second_log.operation_type = 'EDIT'
      AND same_second_log.operator_account_id IS NOT NULL
      AND same_second_log.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND same_second_log.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
  ) <= 1
  AND (
    SELECT COUNT(1)
    FROM settlement_rule_snapshot same_second_snapshot
    WHERE same_second_snapshot.source_type = 'EXTERNAL_ORDER'
      AND same_second_snapshot.source_id = s.source_id
      AND same_second_snapshot.created_at >= s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
      AND same_second_snapshot.created_at < s.created_at
        - INTERVAL MICROSECOND(s.created_at) MICROSECOND
        + INTERVAL 1 SECOND
  ) = 1
  AND existing_revision.id IS NULL;
