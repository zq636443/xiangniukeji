CREATE TABLE spare_part_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_code VARCHAR(64) NOT NULL,
  part_name VARCHAR(128) NOT NULL,
  spec VARCHAR(128) NULL,
  unit VARCHAR(32) NOT NULL DEFAULT '个',
  unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  stock_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_spare_part_code (part_code),
  KEY idx_spare_part_status (status),
  KEY idx_spare_part_name (part_name)
);

CREATE TABLE spare_part_stock_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_id BIGINT NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  quantity_change INT NOT NULL,
  unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  ref_type VARCHAR(32) NULL,
  ref_id BIGINT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_part_stock_log_part (part_id),
  KEY idx_part_stock_log_ref (ref_type, ref_id),
  KEY idx_part_stock_log_created (created_at)
);

CREATE TABLE asset_maintenance_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  maintenance_no VARCHAR(64) NOT NULL,
  asset_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  store_id BIGINT NULL,
  maintenance_type VARCHAR(32) NOT NULL,
  maintenance_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  labor_cost DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  external_cost DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  parts_cost DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  total_cost DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_maintenance_no (maintenance_no),
  KEY idx_asset_maintenance_asset (asset_id),
  KEY idx_asset_maintenance_order (order_id),
  KEY idx_asset_maintenance_store (store_id),
  KEY idx_asset_maintenance_status (maintenance_status)
);

CREATE TABLE asset_maintenance_part (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  maintenance_id BIGINT NOT NULL,
  part_id BIGINT NOT NULL,
  part_name_snapshot VARCHAR(128) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_asset_maintenance_part_record (maintenance_id),
  KEY idx_asset_maintenance_part_part (part_id)
);

INSERT INTO spare_part_category
(id, part_code, part_name, spec, unit, unit_price, stock_quantity, status)
VALUES
(1, 'PART-BRAKE-001', '刹车片', '通用型', '副', 28.00, 20, 'ENABLED'),
(2, 'PART-TIRE-001', '真空胎', '14 寸', '条', 95.00, 12, 'ENABLED');

INSERT INTO spare_part_stock_log
(part_id, change_type, quantity_change, unit_price, amount, ref_type, ref_id, remark)
VALUES
(1, 'INBOUND', 20, 28.00, 560.00, 'INIT', NULL, '初始化库存'),
(2, 'INBOUND', 12, 95.00, 1140.00, 'INIT', NULL, '初始化库存');
