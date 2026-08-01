UPDATE asset_item
SET arrival_batch_no = CONCAT(
  'ARR-',
  DATE_FORMAT(COALESCE(purchased_at, DATE(created_at)), '%Y-%m-%d'),
  '-I',
  investor_id,
  '-B01'
)
WHERE arrival_batch_no IS NULL OR TRIM(arrival_batch_no) = '';
