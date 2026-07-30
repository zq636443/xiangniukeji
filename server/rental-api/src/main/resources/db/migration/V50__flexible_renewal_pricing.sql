ALTER TABLE store_sku_package
  ADD COLUMN renewal_billing_mode VARCHAR(32) NOT NULL DEFAULT 'PERIOD' AFTER renewal_amount,
  ADD COLUMN renewal_daily_amount DECIMAL(12, 2) NULL AFTER renewal_billing_mode,
  ADD COLUMN renewal_daily_cap_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER renewal_daily_amount,
  ADD COLUMN renewal_grace_hours INT NOT NULL DEFAULT 0 AFTER renewal_daily_cap_enabled,
  ADD COLUMN overdue_daily_amount DECIMAL(12, 2) NULL AFTER renewal_grace_hours;

ALTER TABLE rental_order
  ADD COLUMN renewal_billing_mode VARCHAR(32) NOT NULL DEFAULT 'PERIOD' AFTER renewal_amount,
  ADD COLUMN renewal_daily_amount DECIMAL(12, 2) NULL AFTER renewal_billing_mode,
  ADD COLUMN renewal_daily_cap_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER renewal_daily_amount,
  ADD COLUMN renewal_grace_hours INT NOT NULL DEFAULT 0 AFTER renewal_daily_cap_enabled,
  ADD COLUMN overdue_daily_amount DECIMAL(12, 2) NULL AFTER renewal_grace_hours;

ALTER TABLE rental_bill
  ADD COLUMN renewal_charge_mode VARCHAR(32) NULL AFTER generated_batch_no,
  ADD COLUMN renewal_days INT NULL AFTER renewal_charge_mode,
  ADD COLUMN renewal_unit_price DECIMAL(12, 2) NULL AFTER renewal_days;

UPDATE rental_bill b
JOIN rental_order o ON o.id = b.order_id
SET b.renewal_charge_mode = 'PERIOD',
    b.renewal_days = CASE
      WHEN o.renewal_unit = 'MONTH' AND o.renewal_value > 0 THEN o.renewal_value * 30
      WHEN o.renewal_unit = 'DAY' AND o.renewal_value > 0 THEN o.renewal_value
      ELSE NULL
    END,
    b.renewal_unit_price = COALESCE(o.renewal_amount, b.payable_amount)
WHERE b.bill_type = 'RENEWAL'
  AND b.renewal_charge_mode IS NULL;

CREATE TABLE rental_order_pricing_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  revision_status VARCHAR(32) NOT NULL,
  requires_customer_confirmation TINYINT(1) NOT NULL DEFAULT 0,
  effective_mode VARCHAR(32) NOT NULL DEFAULT 'NEXT_UNBILLED_RENEWAL',
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
  operator_account_id BIGINT NULL,
  customer_confirmed_at DATETIME NULL,
  applied_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_pricing_revision_order (order_id),
  KEY idx_pricing_revision_status (revision_status),
  KEY idx_pricing_revision_created (created_at)
);

ALTER TABLE rental_contract
  ADD COLUMN contract_kind VARCHAR(32) NOT NULL DEFAULT 'MAIN' AFTER contract_type,
  ADD COLUMN parent_contract_id BIGINT NULL AFTER contract_kind,
  ADD COLUMN pricing_revision_id BIGINT NULL AFTER parent_contract_id,
  ADD KEY idx_contract_pricing_revision (pricing_revision_id),
  ADD KEY idx_contract_parent (parent_contract_id);

INSERT INTO contract_template
(template_code, template_name, contract_type, version_no, provider_template_id, content, status, remark)
SELECT
  'RENEWAL-PRICE-AMENDMENT',
  '续租价格调整补充协议',
  'RENEWAL_PRICE_AMENDMENT',
  'v1',
  NULL,
  '途派熊续租价格调整补充协议\n订单号：{{orderNo}}\n承租人：{{userName}}，证件号：{{idNo}}\n原续租规则：{{previousRenewalRule}}\n新续租规则：{{newRenewalRule}}\n生效规则：自下一笔尚未生成的续租账单起生效；已支付及已生成账单不作追溯调整。\n调整原因：{{adjustmentReason}}\n签署日期：{{signDate}}',
  'ENABLED',
  '用于已进入履约流程订单的续租价格调整；涨价或新增收费项须经用户确认。'
WHERE NOT EXISTS (
  SELECT 1 FROM contract_template WHERE template_code = 'RENEWAL-PRICE-AMENDMENT' AND version_no = 'v1'
);
