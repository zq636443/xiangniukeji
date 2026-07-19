CREATE TABLE order_asset_usage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  investor_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  usage_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_at DATETIME NULL,
  start_reason VARCHAR(64) NOT NULL,
  end_reason VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_order_asset_usage_order (order_id),
  KEY idx_order_asset_usage_asset (asset_id),
  KEY idx_order_asset_usage_active (order_id, asset_type, usage_status),
  KEY idx_order_asset_usage_investor (investor_id)
);

CREATE TABLE store_spare_part_stock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  part_id BIGINT NOT NULL,
  stock_quantity INT NOT NULL DEFAULT 0,
  avg_unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_part_stock (store_id, part_id),
  KEY idx_store_part_stock_store (store_id),
  KEY idx_store_part_stock_part (part_id)
);

ALTER TABLE spare_part_stock_log
  ADD COLUMN store_id BIGINT NULL AFTER part_id,
  ADD KEY idx_part_stock_log_store (store_id);

ALTER TABLE asset_maintenance_record
  ADD COLUMN cost_bearer_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM' AFTER total_cost,
  ADD COLUMN cost_bearer_id BIGINT NULL AFTER cost_bearer_type,
  ADD KEY idx_asset_maintenance_cost_bearer (cost_bearer_type, cost_bearer_id);

INSERT INTO store_spare_part_stock (store_id, part_id, stock_quantity, avg_unit_price)
SELECT 1, id, stock_quantity, unit_price
FROM spare_part_category
WHERE NOT EXISTS (
  SELECT 1 FROM store_spare_part_stock s WHERE s.store_id = 1 AND s.part_id = spare_part_category.id
);

UPDATE spare_part_stock_log SET store_id = 1 WHERE store_id IS NULL;
