CREATE TABLE audit_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NULL,
  account_type VARCHAR(64) NULL,
  request_method VARCHAR(16) NOT NULL,
  request_uri VARCHAR(255) NOT NULL,
  query_string VARCHAR(512) NULL,
  http_status INT NULL,
  success TINYINT(1) NOT NULL DEFAULT 1,
  error_message VARCHAR(255) NULL,
  client_ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_account (account_id),
  KEY idx_audit_uri (request_uri),
  KEY idx_audit_created (created_at)
);

CREATE TABLE export_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no VARCHAR(64) NOT NULL,
  export_type VARCHAR(64) NOT NULL,
  request_params TEXT NULL,
  task_status VARCHAR(32) NOT NULL,
  file_url VARCHAR(255) NULL,
  failure_reason VARCHAR(255) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  UNIQUE KEY uk_export_task_no (task_no),
  KEY idx_export_type_status (export_type, task_status)
);

CREATE TABLE reconciliation_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  bill_date DATE NOT NULL,
  batch_status VARCHAR(32) NOT NULL,
  platform_total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  channel_total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  diff_count INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  UNIQUE KEY uk_reconciliation_batch_no (batch_no),
  KEY idx_reconciliation_date (bill_date, channel)
);

CREATE TABLE reconciliation_diff (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  payment_id BIGINT NULL,
  payment_no VARCHAR(64) NULL,
  diff_type VARCHAR(64) NOT NULL,
  platform_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  channel_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  diff_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  diff_status VARCHAR(32) NOT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reconciliation_diff_batch (batch_id),
  KEY idx_reconciliation_diff_payment (payment_id)
);
