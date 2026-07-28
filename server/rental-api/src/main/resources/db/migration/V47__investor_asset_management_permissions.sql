INSERT IGNORE INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN ('asset.manage', 'asset.operate')
WHERE r.role_code = 'INVESTOR';
