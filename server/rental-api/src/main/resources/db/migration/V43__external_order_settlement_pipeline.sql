ALTER TABLE external_rental_order
  ADD COLUMN settlement_snapshot_id BIGINT NULL AFTER verification_amount;

ALTER TABLE settlement_income_entry
  MODIFY COLUMN order_id BIGINT NULL,
  ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'ORDER' AFTER entry_no,
  ADD COLUMN source_id BIGINT NULL AFTER source_type,
  ADD COLUMN source_no VARCHAR(64) NULL AFTER source_id,
  ADD COLUMN occurred_at DATETIME NULL AFTER remark;

UPDATE settlement_income_entry e
LEFT JOIN rental_order o ON o.id = e.order_id
SET e.source_type = 'ORDER',
    e.source_id = e.order_id,
    e.source_no = o.order_no,
    e.occurred_at = COALESCE(o.ordered_at, e.created_at)
WHERE e.source_id IS NULL;

ALTER TABLE settlement_income_entry
  MODIFY COLUMN source_id BIGINT NOT NULL,
  MODIFY COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD KEY idx_settlement_entry_source (source_type, source_id),
  ADD KEY idx_settlement_entry_occurred (occurred_at);

CREATE INDEX idx_external_order_snapshot ON external_rental_order (settlement_snapshot_id);
