INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'inventory.read', '查看配件仓库', 'inventory'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'inventory.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'inventory.operate', '操作配件库存', 'inventory'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'inventory.operate');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'maintenance.read', '查看维修记录', 'maintenance'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'maintenance.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'maintenance.operate', '登记维修记录', 'maintenance'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'maintenance.operate');

INSERT INTO auth_role (role_code, role_name, role_scope)
SELECT 'MAINTENANCE_STAFF', '维修人员', 'STORE'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'MAINTENANCE_STAFF');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
WHERE r.role_code = 'MAINTENANCE_STAFF'
  AND p.permission_code IN ('asset.read', 'inventory.read', 'inventory.operate', 'maintenance.read', 'maintenance.operate')
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
WHERE r.role_code = 'STORE_MANAGER'
  AND p.permission_code IN ('inventory.read', 'maintenance.read', 'maintenance.operate')
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
WHERE r.role_code = 'MERCHANT_OWNER'
  AND p.permission_code IN ('inventory.read', 'maintenance.read')
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
