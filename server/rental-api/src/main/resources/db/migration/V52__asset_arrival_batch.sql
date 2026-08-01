ALTER TABLE asset_item
  ADD COLUMN arrival_batch_no VARCHAR(64) NULL AFTER serial_no,
  ADD KEY idx_asset_investor_arrival_batch (investor_id, arrival_batch_no);
