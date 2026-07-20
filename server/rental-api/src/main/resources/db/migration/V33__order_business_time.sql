ALTER TABLE rental_order
  ADD COLUMN ordered_at DATETIME NULL AFTER bill_day;

UPDATE rental_order
SET ordered_at = created_at
WHERE ordered_at IS NULL;

ALTER TABLE rental_order
  MODIFY COLUMN ordered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD KEY idx_rental_order_ordered_at (ordered_at);
