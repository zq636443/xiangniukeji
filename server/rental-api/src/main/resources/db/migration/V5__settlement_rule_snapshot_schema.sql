INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'settlement.write', '管理结算规则', 'settlement'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'settlement.write');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'settlement.write'
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

CREATE TABLE settlement_profit_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  rule_scope VARCHAR(32) NOT NULL,
  sku_id BIGINT NULL,
  merchant_id BIGINT NULL,
  store_id BIGINT NULL,
  store_sku_id BIGINT NULL,
  merchant_order_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  merchant_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  platform_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  investor_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  effective_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expired_at DATETIME NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_settlement_rule_code (rule_code),
  KEY idx_settlement_rule_scope (rule_scope, status),
  KEY idx_settlement_rule_store_sku (store_sku_id),
  KEY idx_settlement_rule_store (store_id),
  KEY idx_settlement_rule_sku (sku_id)
);

CREATE TABLE settlement_rule_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_no VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NULL,
  store_sku_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  frame_asset_id BIGINT NULL,
  battery_asset_id BIGINT NULL,
  matched_rule_id BIGINT NOT NULL,
  matched_rule_scope VARCHAR(32) NOT NULL,
  rental_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  sign_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  merchant_order_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  merchant_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  merchant_rent_share_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  platform_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  platform_rent_share_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  investor_rent_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
  investor_gross_share_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  investor_operation_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  maintenance_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  investor_net_share_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  rule_summary VARCHAR(1024) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_settlement_snapshot_no (snapshot_no),
  KEY idx_settlement_snapshot_source (source_type, source_id),
  KEY idx_settlement_snapshot_store_sku (store_sku_id)
);

INSERT INTO settlement_profit_rule
(id, rule_code, rule_name, rule_scope, sku_id, merchant_id, store_id, store_sku_id,
 merchant_order_fee_amount, merchant_rent_share_rate, platform_rent_share_rate, investor_rent_share_rate,
 effective_at, status)
VALUES
(1, 'RULE-platform-default', '平台默认租赁分润', 'PLATFORM', NULL, NULL, NULL, NULL,
 20.00, 0.2000, 0.1000, 0.7000, CURRENT_TIMESTAMP, 'ENABLED'),
(2, 'RULE-demo-store-sku', '演示门店整车租赁分润', 'STORE_SKU', 1, 1, 1, 1,
 35.00, 0.2500, 0.1000, 0.6500, CURRENT_TIMESTAMP, 'ENABLED');
