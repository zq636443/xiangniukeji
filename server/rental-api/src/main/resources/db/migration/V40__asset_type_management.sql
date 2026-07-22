CREATE TABLE asset_type_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type_code VARCHAR(64) NOT NULL,
  type_name VARCHAR(96) NOT NULL,
  asset_class VARCHAR(32) NOT NULL,
  serial_label VARCHAR(64) NOT NULL DEFAULT '资产编号',
  system_defined TINYINT(1) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_type_code (type_code),
  UNIQUE KEY uk_asset_type_name (type_name),
  KEY idx_asset_type_status_sort (status, sort_order)
);

INSERT INTO asset_type_definition
(type_code, type_name, asset_class, serial_label, system_defined, sort_order, status)
VALUES
('VEHICLE_FRAME', '车架', 'VEHICLE_FRAME', '车架号', 1, 10, 'ENABLED'),
('BATTERY', '电池', 'BATTERY', '电池号', 1, 20, 'ENABLED'),
('INTEGRATED_VEHICLE', '车电一体', 'INTEGRATED_VEHICLE', '车架号', 1, 30, 'ENABLED');

ALTER TABLE asset_item
  ADD COLUMN asset_type_id BIGINT NULL AFTER asset_type;

UPDATE asset_item a
JOIN asset_type_definition t ON t.asset_class = a.asset_type AND t.system_defined = 1
SET a.asset_type_id = t.id;

ALTER TABLE asset_item
  MODIFY COLUMN asset_type_id BIGINT NOT NULL,
  ADD KEY idx_asset_type_definition (asset_type_id);

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'asset.manage', '录入及编辑资产', 'asset'
WHERE NOT EXISTS (
  SELECT 1 FROM auth_permission WHERE permission_code = 'asset.manage'
);

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'asset.manage'
WHERE r.role_code IN ('PLATFORM_ADMIN', 'MERCHANT_OWNER', 'STORE_MANAGER')
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
