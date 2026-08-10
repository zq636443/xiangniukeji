ALTER TABLE settlement_statement
  ADD COLUMN battery_cost_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER operation_fee_amount;
