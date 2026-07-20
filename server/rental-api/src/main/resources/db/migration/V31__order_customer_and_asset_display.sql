ALTER TABLE rental_order
  ADD COLUMN customer_name VARCHAR(64) NULL AFTER user_account_id,
  ADD COLUMN customer_phone VARCHAR(32) NULL AFTER customer_name,
  ADD KEY idx_rental_order_customer_phone (customer_phone);

UPDATE rental_order o
JOIN sys_account a ON a.id = o.user_account_id
SET o.customer_name = COALESCE(NULLIF(a.display_name, ''), a.username),
    o.customer_phone = a.phone
WHERE o.user_account_id IS NOT NULL;

UPDATE rental_order
SET customer_name = '演示客户',
    customer_phone = '13800000000'
WHERE order_no = 'ORD-demo-001'
  AND user_account_id IS NULL
  AND customer_name IS NULL;
