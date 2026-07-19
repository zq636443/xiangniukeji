CREATE TABLE merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_code VARCHAR(64) NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  business_license_no VARCHAR(64) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_code (merchant_code),
  KEY idx_merchant_status (status)
);

CREATE TABLE merchant_store (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  store_code VARCHAR(64) NOT NULL,
  store_name VARCHAR(128) NOT NULL,
  address VARCHAR(255) NOT NULL,
  business_hours VARCHAR(128) NULL,
  longitude DECIMAL(10, 6) NULL,
  latitude DECIMAL(10, 6) NULL,
  qr_content VARCHAR(255) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_code (store_code),
  KEY idx_store_merchant (merchant_id),
  KEY idx_store_status (status)
);

INSERT INTO merchant
(id, merchant_code, merchant_name, contact_name, contact_phone, business_license_no, status)
VALUES
(1, 'M-demo-001', '演示合作商户', '演示商户老板', '18800000002', NULL, 'ENABLED');

INSERT INTO merchant_store
(id, merchant_id, store_code, store_name, address, business_hours, longitude, latitude, qr_content, status)
VALUES
(1, 1, 'S-demo-001', '演示门店', '深圳市南山区演示路 1 号', '09:00-22:00', 113.930000, 22.530000, 'xniu://store/S-demo-001', 'ENABLED');
