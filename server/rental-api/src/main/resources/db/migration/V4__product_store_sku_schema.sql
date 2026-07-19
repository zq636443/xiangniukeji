INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'product.read', '查看商品', 'product'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'product.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'product.write', '管理商品', 'product'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'product.write');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('product.read', 'product.write')
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'product.read'
WHERE r.role_code IN ('MERCHANT_OWNER', 'STORE_MANAGER', 'STORE_STAFF')
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

CREATE TABLE product_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(96) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_category_code (category_code)
);

CREATE TABLE product_sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_code VARCHAR(64) NOT NULL,
  category_id BIGINT NOT NULL,
  sku_name VARCHAR(128) NOT NULL,
  sku_type VARCHAR(32) NOT NULL,
  description VARCHAR(512) NULL,
  need_frame_asset TINYINT(1) NOT NULL DEFAULT 1,
  need_battery_asset TINYINT(1) NOT NULL DEFAULT 1,
  support_cross_store_return TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_sku_code (sku_code),
  KEY idx_product_sku_category (category_id)
);

CREATE TABLE product_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_code VARCHAR(64) NOT NULL,
  sku_id BIGINT NOT NULL,
  package_name VARCHAR(128) NOT NULL,
  lease_unit VARCHAR(24) NOT NULL,
  lease_value INT NOT NULL,
  total_periods INT NOT NULL DEFAULT 1,
  bill_day_mode VARCHAR(32) NOT NULL DEFAULT 'PAYMENT_DAY',
  bill_day INT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_package_code (package_code),
  KEY idx_product_package_sku (sku_id)
);

CREATE TABLE store_sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  store_sku_code VARCHAR(64) NOT NULL,
  sale_mode VARCHAR(24) NOT NULL DEFAULT 'RENTAL',
  display_name VARCHAR(128) NOT NULL,
  sign_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  sign_fee_payer VARCHAR(24) NOT NULL DEFAULT 'USER',
  status VARCHAR(24) NOT NULL DEFAULT 'ON_SHELF',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_sku_code (store_sku_code),
  UNIQUE KEY uk_store_sku_store_sku (store_id, sku_id),
  KEY idx_store_sku_store (store_id),
  KEY idx_store_sku_sku (sku_id)
);

CREATE TABLE store_sku_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  store_sku_id BIGINT NOT NULL,
  package_id BIGINT NOT NULL,
  rental_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  period_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  deposit_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_sku_package (store_sku_id, package_id),
  KEY idx_store_sku_package_store_sku (store_sku_id)
);

INSERT INTO product_category
(id, category_code, category_name, sort_order, status)
VALUES
(1, 'C-rental', '租赁套餐', 1, 'ENABLED');

INSERT INTO product_sku
(id, sku_code, category_id, sku_name, sku_type, description, need_frame_asset, need_battery_asset, support_cross_store_return, status)
VALUES
(1, 'SKU-frame-battery-rental', 1, '车架+电池租赁', 'RENTAL', '适合常规电车整套租赁', 1, 1, 1, 'ENABLED'),
(2, 'SKU-battery-rental', 1, '电池租赁', 'RENTAL', '仅租电池资产', 0, 1, 1, 'ENABLED');

INSERT INTO product_package
(id, package_code, sku_id, package_name, lease_unit, lease_value, total_periods, bill_day_mode, bill_day, status)
VALUES
(1, 'PKG-1-day', 1, '日租 1 天', 'DAY', 1, 1, 'PAYMENT_DAY', NULL, 'ENABLED'),
(2, 'PKG-1-month', 1, '月租 1 期', 'MONTH', 1, 1, 'PAYMENT_DAY', NULL, 'ENABLED'),
(3, 'PKG-3-month', 1, '月租 3 期', 'MONTH', 3, 3, 'PAYMENT_DAY', NULL, 'ENABLED'),
(4, 'PKG-battery-1-month', 2, '电池月租 1 期', 'MONTH', 1, 1, 'PAYMENT_DAY', NULL, 'ENABLED');

INSERT INTO store_sku
(id, merchant_id, store_id, sku_id, store_sku_code, sale_mode, display_name, sign_fee_amount, sign_fee_payer, status)
VALUES
(1, 1, 1, 1, 'SSKU-demo-frame-battery', 'RENTAL', '演示门店整车租赁', 30.00, 'USER', 'ON_SHELF'),
(2, 1, 1, 2, 'SSKU-demo-battery', 'RENTAL', '演示门店电池租赁', 20.00, 'USER', 'ON_SHELF');

INSERT INTO store_sku_package
(store_sku_id, package_id, rental_amount, period_amount, deposit_amount, status)
VALUES
(1, 1, 39.00, 39.00, 0.00, 'ENABLED'),
(1, 2, 399.00, 399.00, 0.00, 'ENABLED'),
(1, 3, 999.00, 333.00, 0.00, 'ENABLED'),
(2, 4, 199.00, 199.00, 0.00, 'ENABLED');
