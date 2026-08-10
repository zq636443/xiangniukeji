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
  applied_at
)
SELECT
  eo.id,
  'SKU-PRICE-SYNC-V58',
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
  '存量外部补录订单续租金额同步为最新 SKU 金额',
  CURRENT_TIMESTAMP
FROM external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
WHERE eo.auto_renew_enabled = 1
  AND sp.rental_amount > 0
  AND NOT (eo.renewal_amount <=> sp.rental_amount);

INSERT INTO external_rental_order_log (
  external_order_id,
  from_status,
  to_status,
  operation_type,
  remark
)
SELECT
  eo.id,
  eo.order_status,
  eo.order_status,
  'RENEWAL_PRICING_ADJUSTMENT',
  '存量续租金额已同步为最新 SKU 金额'
FROM external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
WHERE eo.auto_renew_enabled = 1
  AND sp.rental_amount > 0
  AND NOT (eo.renewal_amount <=> sp.rental_amount);

UPDATE external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
SET eo.renewal_amount = sp.rental_amount
WHERE eo.auto_renew_enabled = 1
  AND sp.rental_amount > 0
  AND NOT (eo.renewal_amount <=> sp.rental_amount);
