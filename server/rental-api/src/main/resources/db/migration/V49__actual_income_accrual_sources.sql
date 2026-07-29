ALTER TABLE settlement_income_entry
  DROP INDEX uk_settlement_entry_line,
  ADD UNIQUE KEY uk_settlement_entry_source_line
    (source_type, source_id, beneficiary_type, beneficiary_id, line_type);

UPDATE settlement_income_entry
SET entry_status = 'FROZEN',
    remark = CONCAT('历史整单预计分润，已由实收账单口径替代；', COALESCE(remark, ''))
WHERE source_type = 'ORDER'
  AND entry_status = 'PENDING';
