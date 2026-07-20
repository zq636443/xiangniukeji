INSERT INTO auth_permission (permission_code, permission_name, module_code)
SELECT 'order.create', '创建租赁订单', 'order'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'order.create');

CREATE TABLE auth_account_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_account_permission (account_id, permission_id),
  KEY idx_auth_account_permission_account (account_id),
  KEY idx_auth_account_permission_permission (permission_id)
);

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'order.create'
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );
