ALTER TABLE external_order_renewal_event
  ADD COLUMN renewal_source VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' AFTER event_status,
  ADD COLUMN operator_account_id BIGINT NULL AFTER renewal_source,
  ADD COLUMN remark VARCHAR(255) NULL AFTER operator_account_id;
