ALTER TABLE settlement_profit_rule
  ADD KEY idx_settlement_rule_store_default (store_id, rule_scope, source_channel, status);

INSERT INTO settlement_profit_rule
(rule_code, rule_name, rule_scope, source_channel, rule_priority,
 sku_id, merchant_id, store_id, store_sku_id,
 channel_fee_rate, platform_fee_rate, store_operation_rate, maintenance_fund_rate,
 channel_referral_rate, investor_share_rate,
 effective_at, expired_at, status)
SELECT CONCAT('RULE-store-default-', store.id),
       CONCAT(store.store_name, '分润规则'),
       'STORE',
       NULL,
       0,
       NULL,
       store.merchant_id,
       store.id,
       NULL,
       template.channel_fee_rate,
       template.platform_fee_rate,
       template.store_operation_rate,
       template.maintenance_fund_rate,
       template.channel_referral_rate,
       template.investor_share_rate,
       CURRENT_TIMESTAMP,
       NULL,
       'ENABLED'
FROM merchant_store store
JOIN settlement_profit_rule template
  ON template.id = COALESCE(
    (
      SELECT store_rule.id
      FROM settlement_profit_rule store_rule
      WHERE store_rule.rule_scope = 'STORE'
        AND store_rule.store_id = store.id
        AND store_rule.source_channel IS NULL
        AND store_rule.status = 'ENABLED'
        AND store_rule.effective_at <= CURRENT_TIMESTAMP
        AND (store_rule.expired_at IS NULL OR store_rule.expired_at > CURRENT_TIMESTAMP)
      ORDER BY store_rule.rule_priority DESC, store_rule.effective_at DESC, store_rule.id DESC
      LIMIT 1
    ),
    (
      SELECT store_sku_rule.id
      FROM settlement_profit_rule store_sku_rule
      WHERE store_sku_rule.rule_scope = 'STORE_SKU'
        AND store_sku_rule.store_id = store.id
        AND store_sku_rule.source_channel IS NULL
        AND store_sku_rule.status = 'ENABLED'
        AND store_sku_rule.effective_at <= CURRENT_TIMESTAMP
        AND (store_sku_rule.expired_at IS NULL OR store_sku_rule.expired_at > CURRENT_TIMESTAMP)
      ORDER BY store_sku_rule.rule_priority DESC, store_sku_rule.effective_at DESC, store_sku_rule.id DESC
      LIMIT 1
    ),
    (
      SELECT platform_rule.id
      FROM settlement_profit_rule platform_rule
      WHERE platform_rule.rule_scope = 'PLATFORM'
        AND platform_rule.source_channel IS NULL
        AND platform_rule.status = 'ENABLED'
        AND platform_rule.effective_at <= CURRENT_TIMESTAMP
        AND (platform_rule.expired_at IS NULL OR platform_rule.expired_at > CURRENT_TIMESTAMP)
      ORDER BY platform_rule.rule_priority DESC, platform_rule.effective_at DESC, platform_rule.id DESC
      LIMIT 1
    )
  )
WHERE NOT EXISTS (
  SELECT 1
  FROM settlement_profit_rule existing_rule
  WHERE existing_rule.rule_scope = 'STORE'
    AND existing_rule.store_id = store.id
    AND existing_rule.source_channel IS NULL
    AND existing_rule.status = 'ENABLED'
);
