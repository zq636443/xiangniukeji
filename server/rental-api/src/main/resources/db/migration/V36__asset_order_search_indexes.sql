ALTER TABLE asset_item
  ADD KEY idx_asset_type_status_store (asset_type, status, current_store_id),
  ADD KEY idx_asset_merchant_status (current_merchant_id, status);

ALTER TABLE rental_order
  ADD KEY idx_order_store_status_ordered (store_id, order_status, ordered_at),
  ADD KEY idx_order_customer_name (customer_name),
  ADD KEY idx_order_frame_asset (frame_asset_id),
  ADD KEY idx_order_battery_asset (battery_asset_id);
