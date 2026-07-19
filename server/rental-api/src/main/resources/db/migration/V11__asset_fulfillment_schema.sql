CREATE TABLE rental_asset_handover (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  handover_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  user_account_id BIGINT NULL,
  handover_type VARCHAR(32) NOT NULL,
  frame_asset_id BIGINT NULL,
  battery_asset_id BIGINT NULL,
  frame_result_status VARCHAR(32) NULL,
  battery_result_status VARCHAR(32) NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_handover_no (handover_no),
  KEY idx_asset_handover_order (order_id),
  KEY idx_asset_handover_store (store_id),
  KEY idx_asset_handover_type (handover_type)
);

CREATE TABLE rental_asset_change (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  change_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  old_asset_id BIGINT NULL,
  new_asset_id BIGINT NOT NULL,
  old_asset_result_status VARCHAR(32) NOT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_change_no (change_no),
  KEY idx_asset_change_order (order_id),
  KEY idx_asset_change_store (store_id),
  KEY idx_asset_change_asset (old_asset_id, new_asset_id)
);
