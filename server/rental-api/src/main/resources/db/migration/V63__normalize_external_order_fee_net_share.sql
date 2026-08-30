/*
 * Normalize the mutable supplemental-order handling fee to the confirmed
 * merchant entitlement (97%), and book the exact 3% remainder for the
 * platform.  This is intentionally separate from the renewal-price repair in
 * V62 so the two data changes have independent audit/rollback review points.
 *
 * Only a strong gross-fee fingerprint is eligible: the pending merchant row
 * must still equal the external order's recorded gross sign fee, and the
 * joined snapshot must belong to that same order.  A source is processed only
 * when it has exactly one pending merchant fee row; duplicate/ambiguous rows
 * are left for an operator to review instead of producing a duplicate platform
 * row under the post-V49 source-level unique key.  Custom/manual amounts,
 * settled/frozen rows, and statement-locked rows are also left for review.
 * The migration is run during a write pause, so the insert-before-update order
 * makes the gross-to-net reconciliation auditable if a deployment is
 * interrupted.
 */
SET time_zone = '+08:00';

/* Add the platform remainder first, while the merchant row still carries the
 * gross amount.  The line type is deliberately distinct from the platform's
 * rental扣点 line. */
INSERT INTO settlement_income_entry
    (entry_no, source_type, source_id, source_no, order_id, snapshot_id,
     merchant_id, store_id, beneficiary_type, beneficiary_id, line_type,
     amount, entry_status, remark, occurred_at)
SELECT CONCAT('INC-FEE97-', merchant_fee.id),
       merchant_fee.source_type,
       merchant_fee.source_id,
       merchant_fee.source_no,
       merchant_fee.order_id,
       merchant_fee.snapshot_id,
       merchant_fee.merchant_id,
       merchant_fee.store_id,
       'PLATFORM',
       0,
       'PLATFORM_ORDER_FEE_SERVICE_FEE',
       eo.sign_fee_amount - ROUND(eo.sign_fee_amount * 0.97, 2),
       'PENDING',
       '办单费净额外的平台手续费',
       merchant_fee.occurred_at
FROM settlement_income_entry merchant_fee
JOIN (
  /* Aggregation forces materialization in MySQL 8, making it safe to read the
   * target table while the outer INSERT/UPDATE is changing it.  Aggregate all
   * rows for a source so a settled/frozen companion row also makes the source
   * ineligible; this avoids a target-table NOT EXISTS subquery in the UPDATE. */
  SELECT source_id,
         MIN(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN id
         END) AS merchant_fee_id
  FROM settlement_income_entry
  WHERE source_type = 'EXTERNAL_ORDER'
  GROUP BY source_id
  HAVING SUM(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN 1 ELSE 0
         END) = 1
     AND SUM(CASE WHEN entry_status <> 'PENDING' THEN 1 ELSE 0 END) = 0
     AND SUM(CASE
           WHEN beneficiary_type = 'PLATFORM'
            AND (
              line_type = 'PLATFORM_ORDER_FEE_SERVICE_FEE'
              OR (line_type = 'PLATFORM_SERVICE_FEE' AND remark LIKE '%签单费%')
            )
           THEN 1 ELSE 0
         END) = 0
) unique_fee ON unique_fee.merchant_fee_id = merchant_fee.id
JOIN external_rental_order eo ON eo.id = merchant_fee.source_id
JOIN settlement_rule_snapshot s ON s.id = merchant_fee.snapshot_id
WHERE merchant_fee.source_type = 'EXTERNAL_ORDER'
  AND merchant_fee.beneficiary_type = 'MERCHANT'
  AND merchant_fee.line_type = 'MERCHANT_ORDER_FEE'
  AND merchant_fee.entry_status = 'PENDING'
  AND s.source_type = 'EXTERNAL_ORDER'
  AND s.source_id = eo.id
  AND eo.sign_fee_amount > 0
  AND merchant_fee.amount <=> eo.sign_fee_amount
  AND eo.sign_fee_amount - ROUND(eo.sign_fee_amount * 0.97, 2) > 0
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = merchant_fee.source_type
      AND locked_line.source_id = merchant_fee.source_id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

/* Change only the same gross rows selected above.  The null-safe comparison
 * prevents a concurrent/manual amount edit from being overwritten. */
UPDATE settlement_income_entry merchant_fee
JOIN (
  /* Keep the UPDATE population identical to the INSERT population; never
   * rewrite every duplicate gross row for one source.  The aggregate also
   * excludes a source with any already-settled/frozen companion row. */
  SELECT source_id,
         MIN(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN id
         END) AS merchant_fee_id
  FROM settlement_income_entry
  WHERE source_type = 'EXTERNAL_ORDER'
  GROUP BY source_id
  HAVING SUM(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN 1 ELSE 0
         END) = 1
     AND SUM(CASE WHEN entry_status <> 'PENDING' THEN 1 ELSE 0 END) = 0
) unique_fee ON unique_fee.merchant_fee_id = merchant_fee.id
JOIN external_rental_order eo ON eo.id = merchant_fee.source_id
JOIN settlement_rule_snapshot s ON s.id = merchant_fee.snapshot_id
SET merchant_fee.amount = ROUND(eo.sign_fee_amount * 0.97, 2),
    merchant_fee.remark = LEFT(CONCAT('办单费按97%计入门店收益；', COALESCE(merchant_fee.remark, '')), 255)
WHERE merchant_fee.source_type = 'EXTERNAL_ORDER'
  AND merchant_fee.beneficiary_type = 'MERCHANT'
  AND merchant_fee.line_type = 'MERCHANT_ORDER_FEE'
  AND merchant_fee.entry_status = 'PENDING'
  AND s.source_type = 'EXTERNAL_ORDER'
  AND s.source_id = eo.id
  AND eo.sign_fee_amount > 0
  AND merchant_fee.amount <=> eo.sign_fee_amount
  AND NOT EXISTS (
    SELECT 1
    FROM settlement_statement_line locked_line
    JOIN settlement_statement locked_statement
      ON locked_statement.id = locked_line.statement_id
    WHERE locked_line.source_type = merchant_fee.source_type
      AND locked_line.source_id = merchant_fee.source_id
      AND locked_statement.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
  );

/* Draft month-end statements are derived data.  Keep confirmed/payable/paid
 * history immutable while bringing still-editable supplemental fee lines to
 * the same 97% amount. */
UPDATE settlement_statement_line line
JOIN settlement_statement statement_row ON statement_row.id = line.statement_id
JOIN external_rental_order statement_order ON statement_order.id = line.source_id
JOIN settlement_rule_snapshot snapshot_row ON snapshot_row.id = statement_order.settlement_snapshot_id
JOIN (
  /* A draft line may outlive an income-row edit.  Reuse the source-level
   * eligibility rule so a manually overridden/ambiguous fee never gets
   * silently replaced by the default 97% projection.  A normalized row is
   * also accepted because the preceding UPDATE in this migration may already
   * have changed the merchant row while the draft line is still gross. */
  SELECT source_id,
         MIN(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN id
         END) AS merchant_fee_id
  FROM settlement_income_entry
  WHERE source_type = 'EXTERNAL_ORDER'
  GROUP BY source_id
  HAVING SUM(CASE
           WHEN beneficiary_type = 'MERCHANT'
            AND line_type = 'MERCHANT_ORDER_FEE'
            AND entry_status = 'PENDING'
           THEN 1 ELSE 0
         END) = 1
     AND SUM(CASE WHEN entry_status <> 'PENDING' THEN 1 ELSE 0 END) = 0
) unique_fee ON unique_fee.source_id = line.source_id
JOIN settlement_income_entry merchant_fee
  ON merchant_fee.id = unique_fee.merchant_fee_id
SET line.amount = ROUND(statement_order.sign_fee_amount * 0.97, 2),
    line.remark = LEFT(CONCAT('办单费按97%计入门店收益；', COALESCE(line.remark, '')), 255)
WHERE line.source_type = 'EXTERNAL_ORDER'
  AND line.line_type = 'MERCHANT_SIGN_FEE'
  AND statement_row.status IN ('DRAFT', 'RECONCILING')
  AND snapshot_row.source_type = 'EXTERNAL_ORDER'
  AND statement_order.sign_fee_amount > 0
  AND (
    merchant_fee.amount <=> statement_order.sign_fee_amount
    OR merchant_fee.amount <=> ROUND(statement_order.sign_fee_amount * 0.97, 2)
  )
  AND line.amount <=> statement_order.sign_fee_amount;

UPDATE settlement_statement statement_row
SET sign_fee_income_amount = (
      SELECT COALESCE(SUM(CASE WHEN line.line_type = 'MERCHANT_SIGN_FEE' THEN line.amount ELSE 0 END), 0)
      FROM settlement_statement_line line
      WHERE line.statement_id = statement_row.id
    ),
    payable_amount = (
      SELECT COALESCE(SUM(CASE WHEN line.line_type <> 'MERCHANT_BATTERY_COST_PAYABLE' THEN line.amount ELSE 0 END), 0)
      FROM settlement_statement_line line
      WHERE line.statement_id = statement_row.id
    )
WHERE statement_row.status IN ('DRAFT', 'RECONCILING')
  AND EXISTS (
    SELECT 1
    FROM settlement_statement_line external_fee
    WHERE external_fee.statement_id = statement_row.id
      AND external_fee.source_type = 'EXTERNAL_ORDER'
      AND external_fee.line_type = 'MERCHANT_SIGN_FEE'
  );
