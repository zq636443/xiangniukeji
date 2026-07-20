ALTER TABLE voucher_verification
  ADD COLUMN verification_amount DECIMAL(12,2) NULL AFTER voucher_amount;

UPDATE voucher_verification
SET verification_amount = voucher_amount
WHERE verification_amount IS NULL;

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'order.operate'
WHERE r.role_code = 'MERCHANT_OWNER'
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );
