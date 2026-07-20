ALTER TABLE settlement_profit_rule
  ADD COLUMN source_channel VARCHAR(32) NULL AFTER rule_scope,
  ADD COLUMN rule_priority INT NOT NULL DEFAULT 0 AFTER source_channel,
  ADD COLUMN channel_fee_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0500 AFTER merchant_order_fee_amount,
  ADD COLUMN platform_fee_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0300 AFTER channel_fee_rate,
  ADD COLUMN store_operation_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.1500 AFTER platform_fee_rate,
  ADD COLUMN maintenance_fund_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.1000 AFTER store_operation_rate,
  ADD COLUMN channel_referral_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.2000 AFTER maintenance_fund_rate,
  ADD COLUMN investor_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.5500 AFTER channel_referral_rate,
  ADD KEY idx_settlement_rule_channel_scope (source_channel, rule_scope, status, effective_at);

UPDATE settlement_profit_rule
SET merchant_order_fee_amount = 0.00,
    merchant_rent_share_rate = 0.0000,
    platform_rent_share_rate = 0.0000,
    investor_rent_share_rate = 0.0000;

ALTER TABLE settlement_rule_snapshot
  ADD COLUMN calculation_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_V1' AFTER source_id,
  ADD COLUMN source_channel VARCHAR(32) NOT NULL DEFAULT 'DIRECT' AFTER calculation_version,
  ADD COLUMN settlement_base_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER rental_amount,
  ADD COLUMN channel_fee_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER settlement_base_amount,
  ADD COLUMN channel_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER channel_fee_rate,
  ADD COLUMN platform_fee_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER channel_fee_amount,
  ADD COLUMN platform_fee_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER platform_fee_rate,
  ADD COLUMN distributable_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER platform_fee_amount,
  ADD COLUMN store_operation_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER distributable_amount,
  ADD COLUMN store_operation_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER store_operation_rate,
  ADD COLUMN maintenance_fund_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER store_operation_amount,
  ADD COLUMN maintenance_fund_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER maintenance_fund_rate,
  ADD COLUMN channel_referral_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER maintenance_fund_amount,
  ADD COLUMN channel_referral_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER channel_referral_rate,
  ADD COLUMN investor_share_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 AFTER channel_referral_amount,
  ADD COLUMN investor_share_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER investor_share_rate,
  ADD KEY idx_settlement_snapshot_channel (source_channel);

UPDATE settlement_rule_snapshot
SET settlement_base_amount = rental_amount;

UPDATE settlement_rule_snapshot snapshot
JOIN voucher_verification voucher
  ON snapshot.source_type = 'ORDER'
 AND snapshot.source_id = voucher.order_id
SET snapshot.source_channel = voucher.source_platform;
