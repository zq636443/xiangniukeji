INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'investor.write', '管理出资方', 'investor'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'investor.write');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'investor.write'
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

CREATE TABLE investor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  investor_code VARCHAR(64) NOT NULL,
  investor_name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  operation_fee_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_investor_code (investor_code),
  KEY idx_investor_status (status)
);

CREATE TABLE asset_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_code VARCHAR(64) NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  serial_no VARCHAR(96) NOT NULL,
  investor_id BIGINT NOT NULL,
  current_merchant_id BIGINT NULL,
  current_store_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  purchase_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  maintenance_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  residual_value DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  purchased_at DATE NULL,
  scrapped_at DATETIME NULL,
  sold_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_code (asset_code),
  UNIQUE KEY uk_asset_serial_type (serial_no, asset_type),
  KEY idx_asset_investor (investor_id),
  KEY idx_asset_store (current_store_id),
  KEY idx_asset_status (status)
);

CREATE TABLE asset_ownership_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_id BIGINT NOT NULL,
  investor_id BIGINT NOT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME NULL,
  change_reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_asset_ownership_asset (asset_id),
  KEY idx_asset_ownership_investor (investor_id)
);

CREATE TABLE asset_location_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_id BIGINT NOT NULL,
  from_merchant_id BIGINT NULL,
  from_store_id BIGINT NULL,
  to_merchant_id BIGINT NULL,
  to_store_id BIGINT NULL,
  moved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_asset_location_asset (asset_id),
  KEY idx_asset_location_to_store (to_store_id)
);

CREATE TABLE asset_status_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_id BIGINT NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_asset_status_log_asset (asset_id),
  KEY idx_asset_status_log_created (created_at)
);

INSERT INTO investor
(id, investor_code, investor_name, contact_name, contact_phone, operation_fee_rate, status)
VALUES
(1, 'I-demo-001', '演示出资方', '演示出资方', '18800000004', 0.0800, 'ENABLED');

INSERT INTO asset_item
(id, asset_code, asset_type, serial_no, investor_id, current_merchant_id, current_store_id, status, purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
VALUES
(1, 'A-frame-demo-001', 'VEHICLE_FRAME', 'FRAME-DEMO-001', 1, 1, 1, 'IDLE', 2600.00, 35.00, 300.00, CURRENT_DATE),
(2, 'A-battery-demo-001', 'BATTERY', 'BATTERY-DEMO-001', 1, 1, 1, 'IDLE', 1800.00, 25.00, 200.00, CURRENT_DATE);

INSERT INTO asset_ownership_history (asset_id, investor_id, change_reason)
VALUES
(1, 1, '初始化入库'),
(2, 1, '初始化入库');

INSERT INTO asset_location_history (asset_id, to_merchant_id, to_store_id, remark)
VALUES
(1, 1, 1, '初始化入库到演示门店'),
(2, 1, 1, '初始化入库到演示门店');

INSERT INTO asset_status_log (asset_id, from_status, to_status, operator_account_id, remark)
VALUES
(1, NULL, 'IDLE', NULL, '初始化入库'),
(2, NULL, 'IDLE', NULL, '初始化入库');
