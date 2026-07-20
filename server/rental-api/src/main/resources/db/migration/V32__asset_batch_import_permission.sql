INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'asset.import', '批量录入资产', 'asset'
WHERE NOT EXISTS (
  SELECT 1 FROM auth_permission WHERE permission_code = 'asset.import'
);

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'asset.import'
WHERE r.role_code IN ('PLATFORM_ADMIN', 'MERCHANT_OWNER', 'STORE_MANAGER')
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );
