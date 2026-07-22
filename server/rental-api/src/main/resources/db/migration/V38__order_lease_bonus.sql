CREATE TABLE rental_order_lease_bonus (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  bonus_type VARCHAR(24) NOT NULL,
  bonus_days INT NOT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  expected_return_before DATETIME NULL,
  expected_return_after DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_lease_bonus_order (order_id),
  KEY idx_order_lease_bonus_type (bonus_type),
  KEY idx_order_lease_bonus_created (created_at)
);
