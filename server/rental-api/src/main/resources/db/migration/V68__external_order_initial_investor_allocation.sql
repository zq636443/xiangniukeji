/*
 * Freeze the initial-period asset-slot ownership independently from mutable
 * asset_item ownership.  Income rows omit zero-cent shares, so they cannot be
 * used alone to recover every owner when a small pool is later repriced.
 */
CREATE TABLE IF NOT EXISTS external_order_initial_investor_allocation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  external_order_id BIGINT NOT NULL,
  settlement_snapshot_id BIGINT NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  asset_id BIGINT NOT NULL,
  investor_id BIGINT NOT NULL,
  allocation_weight DECIMAL(20, 12) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_external_initial_allocation_slot (external_order_id, asset_type),
  KEY idx_external_initial_allocation_snapshot (settlement_snapshot_id),
  KEY idx_external_initial_allocation_asset (asset_id),
  KEY idx_external_initial_allocation_investor (investor_id)
);

INSERT IGNORE INTO external_order_initial_investor_allocation
(external_order_id, settlement_snapshot_id, asset_type, asset_id, investor_id, allocation_weight)
SELECT external_order.id,
       snapshot.id,
       'VEHICLE_FRAME',
       snapshot.frame_asset_id,
       COALESCE(
         (SELECT asset_change.old_investor_id
          FROM external_order_asset_change asset_change
          WHERE asset_change.external_order_id = external_order.id
            AND asset_change.asset_type = 'VEHICLE_FRAME'
          ORDER BY asset_change.effective_at, asset_change.id
          LIMIT 1),
         frame_asset.investor_id
       ),
       CAST(1 AS DECIMAL(20, 12)) /
         ((snapshot.frame_asset_id IS NOT NULL) + (snapshot.battery_asset_id IS NOT NULL))
FROM external_rental_order external_order
JOIN settlement_rule_snapshot snapshot
  ON snapshot.id = external_order.settlement_snapshot_id
 AND snapshot.source_type = 'EXTERNAL_ORDER'
 AND snapshot.source_id = external_order.id
JOIN asset_item frame_asset ON frame_asset.id = snapshot.frame_asset_id
WHERE snapshot.frame_asset_id IS NOT NULL
  AND external_order.order_status = 'ACTIVE'
  AND COALESCE(
        (SELECT asset_change.old_investor_id
         FROM external_order_asset_change asset_change
         WHERE asset_change.external_order_id = external_order.id
           AND asset_change.asset_type = 'VEHICLE_FRAME'
         ORDER BY asset_change.effective_at, asset_change.id
         LIMIT 1),
        frame_asset.investor_id
      ) IS NOT NULL;

INSERT IGNORE INTO external_order_initial_investor_allocation
(external_order_id, settlement_snapshot_id, asset_type, asset_id, investor_id, allocation_weight)
SELECT external_order.id,
       snapshot.id,
       'BATTERY',
       snapshot.battery_asset_id,
       COALESCE(
         (SELECT asset_change.old_investor_id
          FROM external_order_asset_change asset_change
          WHERE asset_change.external_order_id = external_order.id
            AND asset_change.asset_type = 'BATTERY'
          ORDER BY asset_change.effective_at, asset_change.id
          LIMIT 1),
         battery_asset.investor_id
       ),
       CAST(1 AS DECIMAL(20, 12)) /
         ((snapshot.frame_asset_id IS NOT NULL) + (snapshot.battery_asset_id IS NOT NULL))
FROM external_rental_order external_order
JOIN settlement_rule_snapshot snapshot
  ON snapshot.id = external_order.settlement_snapshot_id
 AND snapshot.source_type = 'EXTERNAL_ORDER'
 AND snapshot.source_id = external_order.id
JOIN asset_item battery_asset ON battery_asset.id = snapshot.battery_asset_id
WHERE snapshot.battery_asset_id IS NOT NULL
  AND external_order.order_status = 'ACTIVE'
  AND COALESCE(
        (SELECT asset_change.old_investor_id
         FROM external_order_asset_change asset_change
         WHERE asset_change.external_order_id = external_order.id
           AND asset_change.asset_type = 'BATTERY'
         ORDER BY asset_change.effective_at, asset_change.id
         LIMIT 1),
        battery_asset.investor_id
      ) IS NOT NULL;
