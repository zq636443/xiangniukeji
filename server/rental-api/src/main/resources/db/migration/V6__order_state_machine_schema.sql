CREATE TABLE rental_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_account_id BIGINT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  store_sku_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  package_id BIGINT NOT NULL,
  frame_asset_id BIGINT NULL,
  battery_asset_id BIGINT NULL,
  order_status VARCHAR(32) NOT NULL,
  rental_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  sign_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  deposit_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  payable_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  settlement_snapshot_id BIGINT NULL,
  lease_unit VARCHAR(24) NOT NULL,
  lease_value INT NOT NULL,
  total_periods INT NOT NULL,
  bill_day_mode VARCHAR(32) NOT NULL,
  bill_day INT NULL,
  expected_pickup_at DATETIME NULL,
  lease_started_at DATETIME NULL,
  expected_return_at DATETIME NULL,
  returned_at DATETIME NULL,
  cancelled_at DATETIME NULL,
  cancel_reason VARCHAR(255) NULL,
  exception_reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rental_order_no (order_no),
  KEY idx_rental_order_user (user_account_id),
  KEY idx_rental_order_store (store_id),
  KEY idx_rental_order_status (order_status),
  KEY idx_rental_order_created (created_at)
);

CREATE TABLE rental_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  ref_id BIGINT NULL,
  item_name VARCHAR(128) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  unit_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_item_order (order_id)
);

CREATE TABLE rental_order_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_log_order (order_id),
  KEY idx_order_log_created (created_at)
);

INSERT INTO rental_order
(id, order_no, user_account_id, merchant_id, store_id, store_sku_id, sku_id, package_id,
 frame_asset_id, battery_asset_id, order_status, rental_amount, sign_fee_amount, deposit_amount,
 payable_amount, paid_amount, settlement_snapshot_id, lease_unit, lease_value, total_periods,
 bill_day_mode, bill_day, expected_pickup_at)
VALUES
(1, 'ORD-demo-001', NULL, 1, 1, 1, 1, 2,
 1, 2, 'PENDING_PAYMENT', 399.00, 30.00, 0.00,
 429.00, 0.00, NULL, 'MONTH', 1, 1,
 'PAYMENT_DAY', NULL, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY));

INSERT INTO rental_order_item
(order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
VALUES
(1, 'SKU', 1, '演示门店整车租赁', 1, 399.00, 399.00),
(1, 'SIGN_FEE', NULL, '签单费', 1, 30.00, 30.00),
(1, 'ASSET_FRAME', 1, 'FRAME-DEMO-001', 1, 0.00, 0.00),
(1, 'ASSET_BATTERY', 2, 'BATTERY-DEMO-001', 1, 0.00, 0.00);

INSERT INTO rental_order_operation_log
(order_id, from_status, to_status, operation_type, operator_account_id, remark)
VALUES
(1, NULL, 'PENDING_PAYMENT', 'CREATE', NULL, '演示订单初始化');
