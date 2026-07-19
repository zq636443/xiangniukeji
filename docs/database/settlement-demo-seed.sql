-- 2026-07 月结联调演示数据
-- 用途：
-- 1. 造一笔已支付的租赁订单（租金 + 签单费）
-- 2. 造一笔出资方承担维修、一笔商户承担维修
-- 3. 供总部后台“分润结算 -> 月结中心”生成 2026-07 月结单验证
--
-- 执行方式：
-- docker exec -i xniu-mysql mysql -uroot -proot --default-character-set=utf8mb4 xniu_rental < docs/database/settlement-demo-seed.sql

START TRANSACTION;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET collation_connection = 'utf8mb4_unicode_ci';

SET @merchant_id := 1;
SET @store_id := 1;
SET @store_sku_id := 1;
SET @sku_id := 1;
SET @package_id := 2;
SET @frame_asset_id := 1;
SET @battery_asset_id := 2;
SET @investor_id := 1;

SET @order_no := 'ORD-SETTLE-202607-001';
SET @snapshot_no := 'SNP-SETTLE-202607-001';
SET @bill_no := 'BIL-SETTLE-202607-001';
SET @maintenance_no_investor := 'MT-SETTLE-202607-INV-001';
SET @maintenance_no_merchant := 'MT-SETTLE-202607-MER-001';

-- 演示资产补齐当前所属门店，便于维修扣减时正确归属到商户/门店
UPDATE asset_item
SET current_merchant_id = @merchant_id,
    current_store_id = @store_id
WHERE id IN (@frame_asset_id, @battery_asset_id)
  AND (current_merchant_id IS NULL OR current_store_id IS NULL);

INSERT INTO rental_order
(
  order_no, user_account_id, merchant_id, store_id, store_sku_id, sku_id, package_id,
  frame_asset_id, battery_asset_id, order_status, rental_amount, sign_fee_amount,
  deposit_amount, payable_amount, paid_amount, settlement_snapshot_id,
  lease_unit, lease_value, total_periods, bill_day_mode, bill_day,
  expected_pickup_at, lease_started_at, expected_return_at, returned_at,
  created_at, updated_at
)
SELECT
  @order_no, NULL, @merchant_id, @store_id, @store_sku_id, @sku_id, @package_id,
  @frame_asset_id, @battery_asset_id, 'COMPLETED', 399.00, 30.00,
  0.00, 429.00, 429.00, NULL,
  'MONTH', 1, 1, 'PAYMENT_DAY', NULL,
  '2026-07-05 09:00:00', '2026-07-05 10:00:00', '2026-08-05 10:00:00', '2026-07-06 18:00:00',
  '2026-07-05 09:00:00', '2026-07-06 18:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order WHERE order_no = @order_no
);

SET @order_id := (
  SELECT id FROM rental_order WHERE order_no = @order_no LIMIT 1
);

INSERT INTO rental_order_item
(order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
SELECT @order_id, 'SKU', @store_sku_id, '月结联调整车租赁', 1, 399.00, 399.00
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order_item WHERE order_id = @order_id AND item_type = 'SKU'
);

INSERT INTO rental_order_item
(order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
SELECT @order_id, 'SIGN_FEE', NULL, '签单费', 1, 30.00, 30.00
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order_item WHERE order_id = @order_id AND item_type = 'SIGN_FEE'
);

INSERT INTO rental_order_item
(order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
SELECT @order_id, 'ASSET_FRAME', @frame_asset_id, 'FRAME-DEMO-001', 1, 0.00, 0.00
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order_item WHERE order_id = @order_id AND item_type = 'ASSET_FRAME'
);

INSERT INTO rental_order_item
(order_id, item_type, ref_id, item_name, quantity, unit_amount, total_amount)
SELECT @order_id, 'ASSET_BATTERY', @battery_asset_id, 'BATTERY-DEMO-001', 1, 0.00, 0.00
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order_item WHERE order_id = @order_id AND item_type = 'ASSET_BATTERY'
);

INSERT INTO rental_order_operation_log
(order_id, from_status, to_status, operation_type, operator_account_id, remark, created_at)
SELECT @order_id, NULL, 'COMPLETED', 'CREATE', 1, '月结联调演示订单', '2026-07-05 09:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_order_operation_log WHERE order_id = @order_id AND remark = '月结联调演示订单'
);

INSERT INTO settlement_rule_snapshot
(
  snapshot_no, source_type, source_id, store_sku_id, sku_id, merchant_id, store_id,
  frame_asset_id, battery_asset_id, matched_rule_id, matched_rule_scope,
  rental_amount, sign_fee_amount, merchant_order_fee_amount,
  merchant_rent_share_rate, merchant_rent_share_amount,
  platform_rent_share_rate, platform_rent_share_amount,
  investor_rent_share_rate, investor_gross_share_amount,
  investor_operation_fee_amount, maintenance_fee_amount, investor_net_share_amount,
  rule_summary, created_at
)
SELECT
  @snapshot_no, 'ORDER', @order_id, @store_sku_id, @sku_id, @merchant_id, @store_id,
  @frame_asset_id, @battery_asset_id, 2, 'STORE_SKU',
  399.00, 30.00, 30.00,
  0.2500, 99.75,
  0.1000, 39.90,
  0.6500, 259.35,
  20.75, 0.00, 238.60,
  '演示月结联调快照：租金399，签单费30，商户租金分润25%，平台10%，出资方65%，出资方运营手续费8%',
  '2026-07-05 09:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM settlement_rule_snapshot WHERE snapshot_no = @snapshot_no
);

SET @snapshot_id := (
  SELECT id FROM settlement_rule_snapshot WHERE snapshot_no = @snapshot_no LIMIT 1
);

UPDATE rental_order
SET settlement_snapshot_id = @snapshot_id,
    order_status = 'COMPLETED',
    paid_amount = 429.00,
    updated_at = '2026-07-06 18:00:00'
WHERE id = @order_id;

INSERT INTO rental_bill
(
  bill_no, order_id, user_account_id, merchant_id, store_id, bill_type, period_no,
  bill_status, due_at, payable_amount, paid_amount, overdue_amount, paid_at,
  cancelled_at, remark, generated_batch_no, created_at, updated_at
)
SELECT
  @bill_no, @order_id, NULL, @merchant_id, @store_id, 'INITIAL', 1,
  'PAID', '2026-07-05 10:00:00', 429.00, 429.00, 0.00, '2026-07-06 10:00:00',
  NULL, '月结联调首期账单', 'BATCH-SETTLE-202607', '2026-07-05 10:00:00', '2026-07-06 10:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_bill WHERE bill_no = @bill_no
);

SET @bill_id := (
  SELECT id FROM rental_bill WHERE bill_no = @bill_no LIMIT 1
);

INSERT INTO rental_bill_item
(bill_id, item_type, item_name, amount, created_at)
SELECT @bill_id, 'RENT', '首期租金', 399.00, '2026-07-05 10:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_bill_item WHERE bill_id = @bill_id AND item_type = 'RENT'
);

INSERT INTO rental_bill_item
(bill_id, item_type, item_name, amount, created_at)
SELECT @bill_id, 'SIGN_FEE', '签单费', 30.00, '2026-07-05 10:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_bill_item WHERE bill_id = @bill_id AND item_type = 'SIGN_FEE'
);

INSERT INTO rental_bill_operation_log
(bill_id, from_status, to_status, operation_type, operator_account_id, remark, created_at)
SELECT @bill_id, NULL, 'PAID', 'PAY_SUCCESS', 1, '月结联调账单已支付', '2026-07-06 10:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM rental_bill_operation_log WHERE bill_id = @bill_id AND remark = '月结联调账单已支付'
);

INSERT INTO order_asset_usage
(order_id, asset_id, asset_type, investor_id, store_id, usage_status, start_at, end_at, start_reason, end_reason, created_at, updated_at)
SELECT @order_id, @frame_asset_id, 'VEHICLE_FRAME', @investor_id, @store_id, 'ENDED',
       '2026-07-05 10:00:00', '2026-07-06 18:00:00', 'PICKUP', 'RETURNED', '2026-07-05 10:00:00', '2026-07-06 18:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM order_asset_usage WHERE order_id = @order_id AND asset_id = @frame_asset_id
);

INSERT INTO order_asset_usage
(order_id, asset_id, asset_type, investor_id, store_id, usage_status, start_at, end_at, start_reason, end_reason, created_at, updated_at)
SELECT @order_id, @battery_asset_id, 'BATTERY', @investor_id, @store_id, 'ENDED',
       '2026-07-05 10:00:00', '2026-07-06 18:00:00', 'PICKUP', 'RETURNED', '2026-07-05 10:00:00', '2026-07-06 18:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM order_asset_usage WHERE order_id = @order_id AND asset_id = @battery_asset_id
);

INSERT INTO asset_maintenance_record
(
  maintenance_no, asset_id, order_id, store_id, maintenance_type, maintenance_status,
  started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
  cost_bearer_type, cost_bearer_id, operator_account_id, remark, created_at, updated_at
)
SELECT
  @maintenance_no_investor, @frame_asset_id, @order_id, @store_id, 'REPAIR', 'COMPLETED',
  '2026-07-12 09:00:00', '2026-07-12 11:00:00', 20.00, 0.00, 30.00, 50.00,
  'INVESTOR', @investor_id, 1, '月结联调-出资方承担维修', '2026-07-12 09:00:00', '2026-07-12 11:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM asset_maintenance_record WHERE maintenance_no = @maintenance_no_investor
);

INSERT INTO asset_maintenance_record
(
  maintenance_no, asset_id, order_id, store_id, maintenance_type, maintenance_status,
  started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
  cost_bearer_type, cost_bearer_id, operator_account_id, remark, created_at, updated_at
)
SELECT
  @maintenance_no_merchant, @battery_asset_id, @order_id, @store_id, 'REPAIR', 'COMPLETED',
  '2026-07-18 14:00:00', '2026-07-18 15:00:00', 6.00, 0.00, 6.00, 12.00,
  'MERCHANT', @merchant_id, 1, '月结联调-商户承担维修', '2026-07-18 14:00:00', '2026-07-18 15:00:00'
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM asset_maintenance_record WHERE maintenance_no = @maintenance_no_merchant
);

COMMIT;
