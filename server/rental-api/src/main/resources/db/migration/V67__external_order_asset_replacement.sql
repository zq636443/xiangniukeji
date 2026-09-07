/*
 * Supplemental orders need an effective-dated asset history.  A renewal
 * event remains one billing fact (and therefore one statement month), while
 * its frozen investor pool can be attributed to more than one asset/investor
 * when a replacement happens inside the paid period.
 */
CREATE TABLE IF NOT EXISTS external_order_asset_change (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  change_no VARCHAR(64) NOT NULL,
  external_order_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  old_asset_id BIGINT NOT NULL,
  old_asset_serial_no VARCHAR(96) NOT NULL,
  old_investor_id BIGINT NOT NULL,
  new_asset_id BIGINT NOT NULL,
  new_asset_serial_no VARCHAR(96) NOT NULL,
  new_investor_id BIGINT NOT NULL,
  old_asset_result_status VARCHAR(32) NOT NULL,
  effective_at DATETIME(6) NOT NULL,
  operator_account_id BIGINT NOT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_external_asset_change_no (change_no),
  KEY idx_external_asset_change_order_time (external_order_id, effective_at, id),
  KEY idx_external_asset_change_old_asset (old_asset_id),
  KEY idx_external_asset_change_new_asset (new_asset_id),
  KEY idx_external_asset_change_store (store_id)
);

CREATE TABLE IF NOT EXISTS external_order_renewal_investor_allocation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  renewal_event_id BIGINT NOT NULL,
  settlement_snapshot_id BIGINT NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  asset_id BIGINT NOT NULL,
  investor_id BIGINT NOT NULL,
  effective_start_at DATETIME(6) NOT NULL,
  effective_end_at DATETIME(6) NOT NULL,
  allocation_weight DECIMAL(20, 12) NOT NULL,
  rent_base_amount DECIMAL(12, 2) NOT NULL,
  investor_share_amount DECIMAL(12, 2) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_external_renewal_allocation_event (renewal_event_id),
  KEY idx_external_renewal_allocation_snapshot (settlement_snapshot_id),
  KEY idx_external_renewal_allocation_investor (investor_id),
  KEY idx_external_renewal_allocation_asset (asset_id),
  UNIQUE KEY uk_external_renewal_allocation_segment
    (settlement_snapshot_id, asset_type, asset_id, effective_start_at, effective_end_at)
);
