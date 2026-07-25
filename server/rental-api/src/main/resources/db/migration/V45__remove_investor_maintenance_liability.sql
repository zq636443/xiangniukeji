UPDATE asset_maintenance_record m
JOIN merchant_store s ON s.id = m.store_id
LEFT JOIN (
  SELECT DISTINCT l.source_id
  FROM settlement_statement_line l
  JOIN settlement_statement st ON st.id = l.statement_id
  WHERE l.source_type = 'MAINTENANCE'
    AND st.status IN ('CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED')
) locked ON locked.source_id = m.id
SET m.cost_bearer_type = 'MERCHANT',
    m.cost_bearer_id = s.merchant_id,
    m.merchant_reimbursement_amount = 0.00,
    m.investor_deduct_amount = 0.00
WHERE m.responsibility_type = 'ROUTINE_MAINTENANCE'
  AND locked.source_id IS NULL;
