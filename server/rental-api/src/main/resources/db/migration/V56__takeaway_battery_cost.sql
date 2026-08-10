ALTER TABLE product_sku
  ADD COLUMN battery_cost_daily_amount DECIMAL(12, 2) NULL DEFAULT NULL AFTER description,
  ADD COLUMN battery_cost_monthly_amount DECIMAL(12, 2) NULL DEFAULT NULL AFTER battery_cost_daily_amount;

ALTER TABLE settlement_rule_snapshot
  ADD COLUMN battery_cost_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER platform_fee_amount;

UPDATE product_sku
SET battery_cost_daily_amount = 6.60,
    battery_cost_monthly_amount = 200.00
WHERE sku_name = '外卖车换电';
