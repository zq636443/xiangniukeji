CREATE TABLE rental_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  user_account_id BIGINT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  bill_type VARCHAR(32) NOT NULL,
  period_no INT NOT NULL DEFAULT 1,
  bill_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
  due_at DATETIME NOT NULL,
  payable_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  overdue_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  paid_at DATETIME NULL,
  cancelled_at DATETIME NULL,
  remark VARCHAR(255) NULL,
  generated_batch_no VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bill_no (bill_no),
  UNIQUE KEY uk_bill_order_type_period (order_id, bill_type, period_no),
  KEY idx_bill_order (order_id),
  KEY idx_bill_status_due (bill_status, due_at),
  KEY idx_bill_store (store_id)
);

CREATE TABLE rental_bill_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id BIGINT NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_bill_item_bill (bill_id)
);

CREATE TABLE rental_bill_generation_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  generation_type VARCHAR(32) NOT NULL,
  order_id BIGINT NULL,
  generated_count INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bill_generation_batch_no (batch_no),
  KEY idx_bill_generation_type (generation_type)
);

CREATE TABLE rental_bill_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id BIGINT NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  operator_account_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_bill_log_bill (bill_id)
);
