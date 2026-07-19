INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.account.read', '查看账号', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.account.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.account.write', '管理账号', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.account.write');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.role.read', '查看角色', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.role.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.role.write', '管理角色', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.role.write');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.permission.read', '查看权限', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.permission.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.scope.read', '查看数据范围', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.scope.read');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.scope.write', '管理数据范围', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.scope.write');

INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'auth.audit.read', '查看权限审计', 'auth'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth.audit.read');

INSERT INTO auth_role (role_code, role_name, role_scope)
SELECT 'STORE_OPERATOR', '门店运营', 'STORE'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'STORE_OPERATOR');

INSERT INTO auth_role (role_code, role_name, role_scope)
SELECT 'WAREHOUSE_STAFF', '仓库人员', 'STORE'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'WAREHOUSE_STAFF');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('order.read', 'order.operate', 'asset.read')
WHERE r.role_code = 'STORE_OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('inventory.read', 'inventory.operate', 'maintenance.read')
WHERE r.role_code = 'WAREHOUSE_STAFF'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN (
  'auth.account.read',
  'auth.account.write',
  'auth.role.read',
  'auth.role.write',
  'auth.permission.read',
  'auth.scope.read',
  'auth.scope.write',
  'auth.audit.read'
)
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
