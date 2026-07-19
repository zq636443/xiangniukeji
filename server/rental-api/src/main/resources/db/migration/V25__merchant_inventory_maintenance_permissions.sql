INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('inventory.operate', 'maintenance.operate')
WHERE r.role_code = 'MERCHANT_OWNER'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('inventory.operate')
WHERE r.role_code = 'STORE_MANAGER'
  AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
