ALTER TABLE product_package
  ADD COLUMN sign_fee_amount DECIMAL(12, 2) NULL DEFAULT NULL AFTER price_amount;
