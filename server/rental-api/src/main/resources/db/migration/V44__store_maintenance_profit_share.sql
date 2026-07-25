UPDATE settlement_income_entry
SET beneficiary_type = 'MERCHANT',
    beneficiary_id = store_id,
    remark = '门店维修分润'
WHERE line_type = 'MAINTENANCE_FUND_SHARE'
  AND beneficiary_type = 'MAINTENANCE_FUND'
  AND entry_status = 'PENDING';
