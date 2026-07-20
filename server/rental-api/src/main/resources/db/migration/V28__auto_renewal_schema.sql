ALTER TABLE store_sku_package
  ADD COLUMN auto_renew_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER deposit_amount,
  ADD COLUMN renewal_unit VARCHAR(24) NULL AFTER auto_renew_enabled,
  ADD COLUMN renewal_value INT NULL AFTER renewal_unit,
  ADD COLUMN renewal_amount DECIMAL(12, 2) NULL AFTER renewal_value;

UPDATE store_sku_package ssp
JOIN product_package pp ON pp.id = ssp.package_id
SET ssp.renewal_unit = pp.lease_unit,
    ssp.renewal_value = GREATEST(1, FLOOR(pp.lease_value / GREATEST(pp.total_periods, 1))),
    ssp.renewal_amount = CASE
      WHEN ssp.period_amount > 0 THEN ssp.period_amount
      ELSE ROUND(ssp.rental_amount / GREATEST(pp.total_periods, 1), 2)
    END
WHERE ssp.auto_renew_enabled = 1;

ALTER TABLE rental_order
  ADD COLUMN auto_renew_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER bill_day,
  ADD COLUMN renewal_unit VARCHAR(24) NULL AFTER auto_renew_enabled,
  ADD COLUMN renewal_value INT NULL AFTER renewal_unit,
  ADD COLUMN renewal_amount DECIMAL(12, 2) NULL AFTER renewal_value,
  ADD COLUMN renewal_count INT NOT NULL DEFAULT 0 AFTER renewal_amount,
  ADD KEY idx_rental_order_auto_renew (auto_renew_enabled, expected_return_at, order_status);
