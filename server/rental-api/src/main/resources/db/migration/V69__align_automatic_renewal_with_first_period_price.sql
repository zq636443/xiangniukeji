/*
 * The product package price is the single source of truth for both the first
 * period and the system's future automatic-renewal baseline.  An amount
 * entered through the manual renewal/verification workflow remains an
 * effective-dated order fact and is deliberately excluded below.
 */
SET time_zone = '+08:00';

UPDATE store_sku_package
SET renewal_amount = rental_amount
WHERE auto_renew_enabled = 1
  AND rental_amount > 0
  AND NOT (renewal_amount <=> rental_amount);

/*
 * Record the exact before/after rule before changing an ACTIVE supplemental
 * order.  Only orders with no human pricing proposal/application and no
 * effective-dated verification override are eligible.  V58, V62 and this
 * migration are system repair batches, not human overrides.
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
       'SKU-FIRST-PRICE-SYNC-V69',
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
       sp.rental_amount,
       eo.renewal_billing_mode,
       eo.renewal_daily_amount,
       eo.renewal_daily_cap_enabled,
       eo.renewal_grace_hours,
       eo.overdue_daily_amount,
       '系统自动续租基准同步为商品首月默认金额',
       'SYSTEM',
       CURRENT_TIMESTAMP
FROM external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
WHERE eo.order_status = 'ACTIVE'
  AND eo.auto_renew_enabled = 1
  AND sp.rental_amount > 0
  AND NOT (eo.renewal_amount <=> sp.rental_amount)
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision human_pricing
    WHERE human_pricing.external_order_id = eo.id
      AND human_pricing.revision_status IN ('APPLIED', 'PENDING_CUSTOMER_CONFIRMATION')
      AND (
        human_pricing.batch_no IS NULL
        OR human_pricing.batch_no NOT IN (
          'SKU-PRICE-SYNC-V58',
          'SKU-RENEWAL-RECOVERY-V62',
          'SKU-FIRST-PRICE-SYNC-V69'
        )
      )
  )
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_verification_revision verification_override
    WHERE verification_override.external_order_id = eo.id
      AND verification_override.revision_type <> 'INITIAL'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision existing_v69
    WHERE existing_v69.external_order_id = eo.id
      AND existing_v69.batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
  );

/* Apply only when the row still has the value captured above.  This guard is
 * important for retry safety and prevents an intervening/manual write from
 * being overwritten even if the migration is replayed outside Flyway. */
UPDATE external_rental_order eo
JOIN external_order_pricing_revision v69_revision
  ON v69_revision.external_order_id = eo.id
 AND v69_revision.batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
 AND v69_revision.revision_status = 'APPLIED'
SET eo.renewal_amount = v69_revision.new_renewal_amount
WHERE eo.order_status = 'ACTIVE'
  AND eo.auto_renew_enabled = 1
  AND (eo.renewal_amount <=> v69_revision.previous_renewal_amount)
  AND v69_revision.new_renewal_amount > 0
  AND NOT (eo.renewal_amount <=> v69_revision.new_renewal_amount)
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_pricing_revision human_pricing
    WHERE human_pricing.external_order_id = eo.id
      AND human_pricing.id <> v69_revision.id
      AND human_pricing.revision_status IN ('APPLIED', 'PENDING_CUSTOMER_CONFIRMATION')
      AND (
        human_pricing.batch_no IS NULL
        OR human_pricing.batch_no NOT IN (
          'SKU-PRICE-SYNC-V58',
          'SKU-RENEWAL-RECOVERY-V62',
          'SKU-FIRST-PRICE-SYNC-V69'
        )
      )
  )
  AND NOT EXISTS (
    SELECT 1
    FROM external_order_verification_revision verification_override
    WHERE verification_override.external_order_id = eo.id
      AND verification_override.revision_type <> 'INITIAL'
  );

/*
 * Keep locked historical renewal facts byte-for-byte.  For a mutable SYSTEM
 * event, update only its frozen system baseline; the existing startup
 * reconciler then rebuilds renewal_amount, snapshot, pending income and DRAFT
 * statements transactionally.  MANUAL events are intentionally untouched.
 */
UPDATE external_order_renewal_event renewal_event
JOIN external_order_pricing_revision v69_revision
  ON v69_revision.external_order_id = renewal_event.external_order_id
 AND v69_revision.batch_no = 'SKU-FIRST-PRICE-SYNC-V69'
 AND v69_revision.revision_status = 'APPLIED'
SET renewal_event.system_renewal_amount = v69_revision.new_renewal_amount
WHERE renewal_event.event_status = 'ACCRUED'
  AND renewal_event.renewal_source = 'SYSTEM'
  AND (renewal_event.system_renewal_amount <=> v69_revision.previous_renewal_amount)
  AND v69_revision.new_renewal_amount > 0
  AND NOT (renewal_event.system_renewal_amount <=> v69_revision.new_renewal_amount)
  AND EXISTS (
    SELECT 1
    FROM settlement_income_entry pending_income
    WHERE pending_income.source_type = 'EXTERNAL_RENEWAL'
      AND pending_income.source_id = renewal_event.id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_income_entry locked_income
    WHERE locked_income.source_type = 'EXTERNAL_RENEWAL'
      AND locked_income.source_id = renewal_event.id
      AND locked_income.entry_status <> 'PENDING'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = 'EXTERNAL_RENEWAL'
      AND locked_line.source_id = renewal_event.id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  )
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement locked_month
    WHERE locked_month.statement_month = DATE_FORMAT(renewal_event.period_start_at, '%Y-%m')
      AND locked_month.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );
