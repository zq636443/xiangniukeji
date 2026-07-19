CREATE TABLE sys_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_type VARCHAR(32) NOT NULL,
  username VARCHAR(64) NULL,
  phone VARCHAR(32) NULL,
  alipay_user_id VARCHAR(128) NULL,
  display_name VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NULL,
  merchant_id BIGINT NULL,
  store_id BIGINT NULL,
  investor_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_account_username (username),
  UNIQUE KEY uk_account_phone_type (phone, account_type),
  UNIQUE KEY uk_account_alipay_user_id (alipay_user_id)
);

CREATE TABLE auth_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  role_scope VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_role_code (role_code)
);

CREATE TABLE auth_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(96) NOT NULL,
  permission_name VARCHAR(96) NOT NULL,
  module_code VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_permission_code (permission_code)
);

CREATE TABLE auth_account_role (
  account_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (account_id, role_id)
);

CREATE TABLE auth_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE auth_account_store_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NULL,
  scope_type VARCHAR(24) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_account_store_scope_account (account_id),
  KEY idx_account_store_scope_store (store_id)
);

CREATE TABLE auth_session (
  token VARCHAR(96) PRIMARY KEY,
  account_id BIGINT NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_auth_session_account (account_id),
  KEY idx_auth_session_expires_at (expires_at)
);

INSERT INTO auth_permission (permission_code, permission_name, module_code) VALUES
('system.admin', '系统管理', 'system'),
('merchant.read', '查看商户', 'merchant'),
('merchant.write', '管理商户', 'merchant'),
('store.read', '查看门店', 'merchant'),
('store.write', '管理门店', 'merchant'),
('order.read', '查看订单', 'order'),
('order.operate', '操作订单', 'order'),
('asset.read', '查看资产', 'asset'),
('asset.operate', '操作资产', 'asset'),
('settlement.read', '查看结算', 'settlement'),
('investor.read', '查看出资方', 'investor');

INSERT INTO auth_role (role_code, role_name, role_scope) VALUES
('PLATFORM_ADMIN', '平台管理员', 'PLATFORM'),
('FINANCE', '财务人员', 'PLATFORM'),
('MERCHANT_OWNER', '商户老板', 'MERCHANT'),
('STORE_MANAGER', '门店店长', 'STORE'),
('STORE_STAFF', '门店员工', 'STORE'),
('INVESTOR', '出资方', 'INVESTOR'),
('CONSUMER', '消费者', 'CONSUMER');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'PLATFORM_ADMIN';

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'FINANCE' AND p.permission_code IN ('order.read', 'settlement.read', 'investor.read');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'MERCHANT_OWNER' AND p.permission_code IN ('store.read', 'order.read', 'asset.read', 'settlement.read');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'STORE_MANAGER' AND p.permission_code IN ('store.read', 'order.read', 'order.operate', 'asset.read', 'asset.operate');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'STORE_STAFF' AND p.permission_code IN ('order.read', 'order.operate', 'asset.read', 'asset.operate');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
WHERE r.role_code = 'INVESTOR' AND p.permission_code IN ('asset.read', 'settlement.read');

INSERT INTO sys_account
(account_type, username, phone, display_name, password_hash, merchant_id, store_id, investor_id, status)
VALUES
('PLATFORM_ADMIN', 'admin', '18800000001', '平台管理员', 'pbkdf2$120000$eg2uVcQ0272bPD8XnYRB0g==$gWwFFpGLC2aIvPvKLuvcMJfW5Qh4eKTXbl4zbnaiG/k=', NULL, NULL, NULL, 'ENABLED'),
('MERCHANT_OWNER', 'merchant_demo', '18800000002', '演示商户老板', 'pbkdf2$120000$eg2uVcQ0272bPD8XnYRB0g==$gWwFFpGLC2aIvPvKLuvcMJfW5Qh4eKTXbl4zbnaiG/k=', 1, NULL, NULL, 'ENABLED'),
('STORE_STAFF', 'store_demo', '18800000003', '演示门店员工', 'pbkdf2$120000$eg2uVcQ0272bPD8XnYRB0g==$gWwFFpGLC2aIvPvKLuvcMJfW5Qh4eKTXbl4zbnaiG/k=', 1, 1, NULL, 'ENABLED'),
('INVESTOR', 'investor_demo', '18800000004', '演示出资方', 'pbkdf2$120000$eg2uVcQ0272bPD8XnYRB0g==$gWwFFpGLC2aIvPvKLuvcMJfW5Qh4eKTXbl4zbnaiG/k=', NULL, NULL, 1, 'ENABLED');

INSERT INTO auth_account_role (account_id, role_id)
SELECT a.id, r.id FROM sys_account a JOIN auth_role r
WHERE a.username = 'admin' AND r.role_code = 'PLATFORM_ADMIN';

INSERT INTO auth_account_role (account_id, role_id)
SELECT a.id, r.id FROM sys_account a JOIN auth_role r
WHERE a.username = 'merchant_demo' AND r.role_code = 'MERCHANT_OWNER';

INSERT INTO auth_account_role (account_id, role_id)
SELECT a.id, r.id FROM sys_account a JOIN auth_role r
WHERE a.username = 'store_demo' AND r.role_code = 'STORE_STAFF';

INSERT INTO auth_account_role (account_id, role_id)
SELECT a.id, r.id FROM sys_account a JOIN auth_role r
WHERE a.username = 'investor_demo' AND r.role_code = 'INVESTOR';

INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
SELECT id, 1, NULL, 'ALL_MERCHANT_STORES' FROM sys_account WHERE username = 'merchant_demo';

INSERT INTO auth_account_store_scope (account_id, merchant_id, store_id, scope_type)
SELECT id, 1, 1, 'SINGLE_STORE' FROM sys_account WHERE username = 'store_demo';
