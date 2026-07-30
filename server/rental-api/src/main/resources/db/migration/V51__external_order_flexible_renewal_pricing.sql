ALTER TABLE external_rental_order
  ADD COLUMN auto_renew_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER lease_multiplier,
  ADD COLUMN renewal_unit VARCHAR(24) NULL AFTER auto_renew_enabled,
  ADD COLUMN renewal_value INT NULL AFTER renewal_unit,
  ADD COLUMN renewal_amount DECIMAL(12, 2) NULL AFTER renewal_value,
  ADD COLUMN renewal_billing_mode VARCHAR(32) NOT NULL DEFAULT 'PERIOD' AFTER renewal_amount,
  ADD COLUMN renewal_daily_amount DECIMAL(12, 2) NULL AFTER renewal_billing_mode,
  ADD COLUMN renewal_daily_cap_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER renewal_daily_amount,
  ADD COLUMN renewal_grace_hours INT NOT NULL DEFAULT 0 AFTER renewal_daily_cap_enabled,
  ADD COLUMN overdue_daily_amount DECIMAL(12, 2) NULL AFTER renewal_grace_hours;

UPDATE external_rental_order eo
JOIN store_sku_package sp
  ON sp.store_sku_id = eo.store_sku_id
 AND sp.package_id = eo.package_id
SET eo.auto_renew_enabled = sp.auto_renew_enabled,
    eo.renewal_unit = sp.renewal_unit,
    eo.renewal_value = sp.renewal_value,
    eo.renewal_amount = sp.renewal_amount,
    eo.renewal_billing_mode = sp.renewal_billing_mode,
    eo.renewal_daily_amount = sp.renewal_daily_amount,
    eo.renewal_daily_cap_enabled = sp.renewal_daily_cap_enabled,
    eo.renewal_grace_hours = sp.renewal_grace_hours,
    eo.overdue_daily_amount = sp.overdue_daily_amount;

CREATE TABLE external_order_pricing_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  external_order_id BIGINT NOT NULL,
  batch_no VARCHAR(64) NULL,
  revision_status VARCHAR(32) NOT NULL,
  requires_customer_confirmation TINYINT(1) NOT NULL DEFAULT 0,
  previous_auto_renew_enabled TINYINT(1) NOT NULL,
  previous_renewal_unit VARCHAR(24) NULL,
  previous_renewal_value INT NULL,
  previous_renewal_amount DECIMAL(12, 2) NULL,
  previous_billing_mode VARCHAR(32) NOT NULL,
  previous_daily_amount DECIMAL(12, 2) NULL,
  previous_daily_cap_enabled TINYINT(1) NOT NULL,
  previous_grace_hours INT NOT NULL,
  previous_overdue_daily_amount DECIMAL(12, 2) NULL,
  new_auto_renew_enabled TINYINT(1) NOT NULL,
  new_renewal_unit VARCHAR(24) NULL,
  new_renewal_value INT NULL,
  new_renewal_amount DECIMAL(12, 2) NULL,
  new_billing_mode VARCHAR(32) NOT NULL,
  new_daily_amount DECIMAL(12, 2) NULL,
  new_daily_cap_enabled TINYINT(1) NOT NULL,
  new_grace_hours INT NOT NULL,
  new_overdue_daily_amount DECIMAL(12, 2) NULL,
  reason VARCHAR(255) NOT NULL,
  confirmation_method VARCHAR(32) NULL,
  confirmation_reference VARCHAR(500) NULL,
  operator_account_id BIGINT NULL,
  customer_confirmed_at DATETIME NULL,
  applied_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_external_pricing_order (external_order_id),
  KEY idx_external_pricing_batch (batch_no),
  KEY idx_external_pricing_status (revision_status),
  KEY idx_external_pricing_created (created_at)
);
