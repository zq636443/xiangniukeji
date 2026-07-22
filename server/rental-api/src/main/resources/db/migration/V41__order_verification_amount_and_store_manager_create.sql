ALTER TABLE rental_order
  ADD COLUMN verification_amount DECIMAL(12, 2) NULL AFTER rental_amount;

UPDATE rental_order
SET verification_amount = rental_amount
WHERE verification_amount IS NULL;

ALTER TABLE rental_order
  MODIFY COLUMN verification_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE external_rental_order
  ADD COLUMN verification_amount DECIMAL(12, 2) NULL AFTER external_rental_amount;

UPDATE external_rental_order
SET verification_amount = external_rental_amount
WHERE verification_amount IS NULL;

ALTER TABLE external_rental_order
  MODIFY COLUMN verification_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'order.create'
WHERE r.role_code = 'STORE_MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );
