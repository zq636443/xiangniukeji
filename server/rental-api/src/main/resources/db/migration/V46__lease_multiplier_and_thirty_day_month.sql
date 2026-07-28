ALTER TABLE rental_order
  ADD COLUMN lease_multiplier INT NOT NULL DEFAULT 1 AFTER total_periods;

ALTER TABLE external_rental_order
  ADD COLUMN lease_multiplier INT NOT NULL DEFAULT 1 AFTER total_periods;

UPDATE rental_order o
LEFT JOIN (
  SELECT order_id, COALESCE(SUM(bonus_days), 0) AS bonus_days
  FROM rental_order_lease_bonus
  GROUP BY order_id
) bonus ON bonus.order_id = o.id
SET o.expected_return_at = TIMESTAMPADD(
  DAY,
  CASE WHEN o.lease_unit = 'MONTH' THEN o.lease_value * 30 ELSE o.lease_value END
    + COALESCE(bonus.bonus_days, 0)
    + COALESCE(o.renewal_count, 0) *
      CASE
        WHEN o.renewal_unit = 'MONTH' THEN COALESCE(o.renewal_value, 0) * 30
        ELSE COALESCE(o.renewal_value, 0)
      END,
  o.lease_started_at
)
WHERE o.lease_started_at IS NOT NULL
  AND o.order_status NOT IN ('COMPLETED', 'CANCELLED', 'EXCEPTION');

UPDATE external_rental_order
SET expected_return_at = TIMESTAMPADD(
  DAY,
  CASE WHEN lease_unit = 'MONTH' THEN lease_value * 30 ELSE lease_value END,
  rent_started_at
)
WHERE rent_started_at IS NOT NULL
  AND order_status = 'ACTIVE';

UPDATE rental_bill b
JOIN rental_order o ON o.id = b.order_id
SET b.due_at = TIMESTAMPADD(
  DAY,
  (b.period_no - 1) *
    CASE
      WHEN o.lease_unit = 'MONTH' THEN 30
      ELSE GREATEST(1, FLOOR(o.lease_value / GREATEST(o.total_periods, 1)))
    END,
  COALESCE(o.lease_started_at, o.expected_pickup_at, o.ordered_at, o.created_at)
)
WHERE b.bill_type = 'PERIODIC'
  AND b.bill_status = 'PENDING_PAYMENT';
