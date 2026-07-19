ALTER TABLE spare_part_category
  ADD COLUMN procurement_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER unit,
  ADD COLUMN buyback_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER unit_price;

UPDATE spare_part_category
SET procurement_price = unit_price,
    buyback_price = unit_price
WHERE procurement_price = 0.00
   OR buyback_price = 0.00;

ALTER TABLE store_spare_part_stock
  ADD COLUMN merchant_id BIGINT NULL AFTER store_id,
  ADD KEY idx_store_part_stock_merchant (merchant_id);

UPDATE store_spare_part_stock s
JOIN merchant_store ms ON ms.id = s.store_id
SET s.merchant_id = ms.merchant_id
WHERE s.merchant_id IS NULL;

ALTER TABLE spare_part_stock_log
  ADD COLUMN merchant_id BIGINT NULL AFTER store_id,
  ADD KEY idx_part_stock_log_merchant (merchant_id);

UPDATE spare_part_stock_log l
JOIN merchant_store ms ON ms.id = l.store_id
SET l.merchant_id = ms.merchant_id
WHERE l.store_id IS NOT NULL
  AND l.merchant_id IS NULL;

ALTER TABLE asset_maintenance_record
  ADD COLUMN responsibility_type VARCHAR(32) NOT NULL DEFAULT 'ROUTINE_MAINTENANCE' AFTER maintenance_status,
  ADD COLUMN merchant_reimbursement_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER total_cost,
  ADD COLUMN investor_deduct_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER merchant_reimbursement_amount,
  ADD COLUMN customer_charge_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER investor_deduct_amount,
  ADD KEY idx_asset_maintenance_responsibility (responsibility_type);

UPDATE asset_maintenance_record
SET responsibility_type = CASE cost_bearer_type
    WHEN 'MERCHANT' THEN 'MERCHANT_RESPONSIBILITY'
    WHEN 'USER' THEN 'CUSTOMER_DAMAGE'
    WHEN 'PLATFORM' THEN 'PLATFORM_SUBSIDY'
    ELSE 'ROUTINE_MAINTENANCE'
  END,
  merchant_reimbursement_amount = CASE
    WHEN cost_bearer_type IN ('INVESTOR', 'PLATFORM') THEN parts_cost
    ELSE 0.00
  END,
  investor_deduct_amount = CASE
    WHEN cost_bearer_type = 'INVESTOR' THEN total_cost
    ELSE 0.00
  END,
  customer_charge_amount = CASE
    WHEN cost_bearer_type = 'USER' THEN total_cost
    ELSE 0.00
  END
WHERE responsibility_type = 'ROUTINE_MAINTENANCE'
  AND merchant_reimbursement_amount = 0.00
  AND investor_deduct_amount = 0.00
  AND customer_charge_amount = 0.00;
