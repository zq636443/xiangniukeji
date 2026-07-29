SET @cleanup_demo_data = IF(LOWER('${cleanupDemoData}') IN ('true', '1', 'yes'), 1, 0);

SET @demo_merchant_id = (
  SELECT id FROM merchant WHERE merchant_code = 'M-demo-001' LIMIT 1
);
SET @demo_store_id = (
  SELECT id FROM merchant_store WHERE store_code = 'S-demo-001' LIMIT 1
);
SET @demo_investor_id = (
  SELECT id FROM investor WHERE investor_code = 'I-demo-001' LIMIT 1
);

CREATE TEMPORARY TABLE cleanup_demo_account_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_account_ids (id)
SELECT id
FROM sys_account
WHERE @cleanup_demo_data = 1
  AND (
    username IN ('merchant_demo', 'store_demo', 'investor_demo')
    OR merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
    OR investor_id = @demo_investor_id
  );

CREATE TEMPORARY TABLE cleanup_demo_store_sku_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_store_sku_ids (id)
SELECT id
FROM store_sku
WHERE @cleanup_demo_data = 1
  AND (
    store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR store_sku_code IN ('SSKU-demo-frame-battery', 'SSKU-demo-battery')
  );

CREATE TEMPORARY TABLE cleanup_demo_order_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_order_ids (id)
SELECT id
FROM rental_order
WHERE @cleanup_demo_data = 1
  AND (
    store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids)
    OR order_no IN ('ORD-demo-001', 'ORD-SETTLE-202607-001')
  );

CREATE TEMPORARY TABLE cleanup_demo_external_order_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_external_order_ids (id)
SELECT id
FROM external_rental_order
WHERE @cleanup_demo_data = 1
  AND (
    store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR return_store_id = @demo_store_id
    OR store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids)
  );

CREATE TEMPORARY TABLE cleanup_demo_asset_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_asset_ids (id)
SELECT id
FROM asset_item
WHERE @cleanup_demo_data = 1
  AND (
    investor_id = @demo_investor_id
    OR current_store_id = @demo_store_id
    OR current_merchant_id = @demo_merchant_id
    OR serial_no IN ('FRAME-DEMO-001', 'BATTERY-DEMO-001')
    OR asset_code IN ('A-frame-demo-001', 'A-battery-demo-001')
  );

CREATE TEMPORARY TABLE cleanup_demo_bill_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_bill_ids (id)
SELECT id
FROM rental_bill
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_payment_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_payment_ids (id)
SELECT id
FROM rental_payment_order
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR bill_id IN (SELECT id FROM cleanup_demo_bill_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_agreement_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_agreement_ids (id)
SELECT id
FROM rental_pay_agreement
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_auth_order_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_auth_order_ids (id)
SELECT id
FROM rental_fund_auth_order
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_contract_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_contract_ids (id)
SELECT id
FROM rental_contract
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_overdue_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_overdue_ids (id)
SELECT id
FROM rental_overdue_case
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR bill_id IN (SELECT id FROM cleanup_demo_bill_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
  );

CREATE TEMPORARY TABLE cleanup_demo_maintenance_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_maintenance_ids (id)
SELECT id
FROM asset_maintenance_record
WHERE @cleanup_demo_data = 1
  AND (
    asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
    OR order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
  );

CREATE TEMPORARY TABLE cleanup_demo_snapshot_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_snapshot_ids (id)
SELECT id
FROM settlement_rule_snapshot
WHERE @cleanup_demo_data = 1
  AND (
    store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids)
    OR frame_asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
    OR (source_type = 'ORDER' AND source_id IN (SELECT id FROM cleanup_demo_order_ids))
    OR (source_type = 'EXTERNAL_ORDER' AND source_id IN (SELECT id FROM cleanup_demo_external_order_ids))
  );
INSERT IGNORE INTO cleanup_demo_snapshot_ids (id)
SELECT id
FROM settlement_rule_snapshot
WHERE @cleanup_demo_data = 1
  AND battery_asset_id IN (SELECT id FROM cleanup_demo_asset_ids);

CREATE TEMPORARY TABLE cleanup_demo_statement_ids (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO cleanup_demo_statement_ids (id)
SELECT id
FROM settlement_statement
WHERE @cleanup_demo_data = 1
  AND (
    merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
    OR (beneficiary_type = 'INVESTOR' AND beneficiary_id = @demo_investor_id)
    OR (beneficiary_type = 'MERCHANT' AND beneficiary_id = @demo_merchant_id)
    OR (beneficiary_type = 'STORE' AND beneficiary_id = @demo_store_id)
  );

INSERT IGNORE INTO cleanup_demo_statement_ids (id)
SELECT DISTINCT statement_id
FROM settlement_statement_line
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR bill_id IN (SELECT id FROM cleanup_demo_bill_ids)
    OR asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
    OR merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
    OR investor_id = @demo_investor_id
    OR (source_type = 'EXTERNAL_ORDER' AND source_id IN (SELECT id FROM cleanup_demo_external_order_ids))
  );
INSERT IGNORE INTO cleanup_demo_statement_ids (id)
SELECT DISTINCT statement_id
FROM settlement_statement_line
WHERE @cleanup_demo_data = 1
  AND source_type = 'ORDER'
  AND source_id IN (SELECT id FROM cleanup_demo_order_ids);

DELETE FROM audit_operation_log
WHERE @cleanup_demo_data = 1 AND account_id IN (SELECT id FROM cleanup_demo_account_ids);
DELETE FROM export_task
WHERE @cleanup_demo_data = 1 AND created_by IN (SELECT id FROM cleanup_demo_account_ids);
DELETE FROM reconciliation_diff
WHERE @cleanup_demo_data = 1 AND payment_id IN (SELECT id FROM cleanup_demo_payment_ids);

DELETE FROM rental_payment_callback
WHERE @cleanup_demo_data = 1 AND payment_id IN (SELECT id FROM cleanup_demo_payment_ids);
DELETE FROM rental_agreement_notify
WHERE @cleanup_demo_data = 1 AND agreement_id IN (SELECT id FROM cleanup_demo_agreement_ids);
DELETE FROM rental_fund_auth_operation
WHERE @cleanup_demo_data = 1 AND auth_order_id IN (SELECT id FROM cleanup_demo_auth_order_ids);
DELETE FROM rental_fund_auth_notify
WHERE @cleanup_demo_data = 1 AND auth_order_id IN (SELECT id FROM cleanup_demo_auth_order_ids);
DELETE FROM contract_notify
WHERE @cleanup_demo_data = 1 AND contract_id IN (SELECT id FROM cleanup_demo_contract_ids);
DELETE FROM rental_overdue_collection_log
WHERE @cleanup_demo_data = 1 AND overdue_case_id IN (SELECT id FROM cleanup_demo_overdue_ids);
DELETE FROM asset_maintenance_part
WHERE @cleanup_demo_data = 1 AND maintenance_id IN (SELECT id FROM cleanup_demo_maintenance_ids);

DELETE FROM settlement_statement_line
WHERE @cleanup_demo_data = 1
  AND (
    statement_id IN (SELECT id FROM cleanup_demo_statement_ids)
    OR order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR bill_id IN (SELECT id FROM cleanup_demo_bill_ids)
    OR asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
    OR merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
    OR investor_id = @demo_investor_id
  );
DELETE FROM settlement_statement
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_statement_ids);
DELETE FROM settlement_income_entry
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR snapshot_id IN (SELECT id FROM cleanup_demo_snapshot_ids)
    OR store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR (beneficiary_type = 'INVESTOR' AND beneficiary_id = @demo_investor_id)
    OR (source_type = 'EXTERNAL_ORDER' AND source_id IN (SELECT id FROM cleanup_demo_external_order_ids))
  );
DELETE FROM settlement_income_entry
WHERE @cleanup_demo_data = 1
  AND source_type = 'ORDER'
  AND source_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM settlement_rule_snapshot
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_snapshot_ids);
DELETE FROM settlement_profit_rule
WHERE @cleanup_demo_data = 1
  AND (
    merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
    OR store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids)
    OR rule_code = 'RULE-demo-store-sku'
  );

DELETE FROM voucher_verification
WHERE @cleanup_demo_data = 1
  AND (
    store_id = @demo_store_id
    OR merchant_id = @demo_merchant_id
    OR order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids)
  );
DELETE FROM external_rental_order_log
WHERE @cleanup_demo_data = 1 AND external_order_id IN (SELECT id FROM cleanup_demo_external_order_ids);
DELETE FROM external_rental_order
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_external_order_ids);

DELETE FROM rental_deduct_record
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR bill_id IN (SELECT id FROM cleanup_demo_bill_ids)
    OR agreement_id IN (SELECT id FROM cleanup_demo_agreement_ids)
  );
DELETE FROM rental_overdue_case
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_overdue_ids);
DELETE FROM rental_asset_handover
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR frame_asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
  );
DELETE FROM rental_asset_handover
WHERE @cleanup_demo_data = 1
  AND battery_asset_id IN (SELECT id FROM cleanup_demo_asset_ids);
DELETE FROM rental_asset_change
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR store_id = @demo_store_id
    OR old_asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
  );
DELETE FROM rental_asset_change
WHERE @cleanup_demo_data = 1
  AND new_asset_id IN (SELECT id FROM cleanup_demo_asset_ids);
DELETE FROM order_asset_usage
WHERE @cleanup_demo_data = 1
  AND (
    order_id IN (SELECT id FROM cleanup_demo_order_ids)
    OR asset_id IN (SELECT id FROM cleanup_demo_asset_ids)
    OR store_id = @demo_store_id
    OR investor_id = @demo_investor_id
  );
DELETE FROM user_identity_verification
WHERE @cleanup_demo_data = 1 AND order_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM rental_contract
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_contract_ids);
DELETE FROM rental_fund_auth_order
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_auth_order_ids);
DELETE FROM rental_pay_agreement
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_agreement_ids);
DELETE FROM rental_payment_order
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_payment_ids);

DELETE FROM rental_bill_item
WHERE @cleanup_demo_data = 1 AND bill_id IN (SELECT id FROM cleanup_demo_bill_ids);
DELETE FROM rental_bill_operation_log
WHERE @cleanup_demo_data = 1 AND bill_id IN (SELECT id FROM cleanup_demo_bill_ids);
DELETE FROM rental_bill_generation_batch
WHERE @cleanup_demo_data = 1 AND order_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM rental_bill
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_bill_ids);
DELETE FROM rental_order_lease_bonus
WHERE @cleanup_demo_data = 1 AND order_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM rental_order_item
WHERE @cleanup_demo_data = 1 AND order_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM rental_order_operation_log
WHERE @cleanup_demo_data = 1 AND order_id IN (SELECT id FROM cleanup_demo_order_ids);
DELETE FROM rental_order
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_order_ids);

DELETE FROM asset_maintenance_record
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_maintenance_ids);
DELETE FROM asset_status_log
WHERE @cleanup_demo_data = 1 AND asset_id IN (SELECT id FROM cleanup_demo_asset_ids);
DELETE FROM asset_location_history
WHERE @cleanup_demo_data = 1 AND asset_id IN (SELECT id FROM cleanup_demo_asset_ids);
DELETE FROM asset_ownership_history
WHERE @cleanup_demo_data = 1
  AND (asset_id IN (SELECT id FROM cleanup_demo_asset_ids) OR investor_id = @demo_investor_id);
DELETE FROM asset_item
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_asset_ids);

DELETE FROM spare_part_stock_log
WHERE @cleanup_demo_data = 1
  AND (store_id = @demo_store_id OR operator_account_id IN (SELECT id FROM cleanup_demo_account_ids));
DELETE FROM store_spare_part_stock
WHERE @cleanup_demo_data = 1 AND store_id = @demo_store_id;
DELETE FROM store_sku_package
WHERE @cleanup_demo_data = 1 AND store_sku_id IN (SELECT id FROM cleanup_demo_store_sku_ids);
DELETE FROM store_sku
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_store_sku_ids);

DELETE FROM auth_session
WHERE @cleanup_demo_data = 1 AND account_id IN (SELECT id FROM cleanup_demo_account_ids);
DELETE FROM auth_account_permission
WHERE @cleanup_demo_data = 1 AND account_id IN (SELECT id FROM cleanup_demo_account_ids);
DELETE FROM auth_account_store_scope
WHERE @cleanup_demo_data = 1
  AND (
    account_id IN (SELECT id FROM cleanup_demo_account_ids)
    OR merchant_id = @demo_merchant_id
    OR store_id = @demo_store_id
  );
DELETE FROM auth_account_role
WHERE @cleanup_demo_data = 1 AND account_id IN (SELECT id FROM cleanup_demo_account_ids);
DELETE FROM sys_account
WHERE @cleanup_demo_data = 1 AND id IN (SELECT id FROM cleanup_demo_account_ids);

DELETE FROM merchant_store
WHERE @cleanup_demo_data = 1 AND id = @demo_store_id;
DELETE FROM investor
WHERE @cleanup_demo_data = 1 AND id = @demo_investor_id;
DELETE FROM merchant
WHERE @cleanup_demo_data = 1 AND id = @demo_merchant_id;

DROP TEMPORARY TABLE cleanup_demo_statement_ids;
DROP TEMPORARY TABLE cleanup_demo_snapshot_ids;
DROP TEMPORARY TABLE cleanup_demo_maintenance_ids;
DROP TEMPORARY TABLE cleanup_demo_overdue_ids;
DROP TEMPORARY TABLE cleanup_demo_contract_ids;
DROP TEMPORARY TABLE cleanup_demo_auth_order_ids;
DROP TEMPORARY TABLE cleanup_demo_agreement_ids;
DROP TEMPORARY TABLE cleanup_demo_payment_ids;
DROP TEMPORARY TABLE cleanup_demo_bill_ids;
DROP TEMPORARY TABLE cleanup_demo_asset_ids;
DROP TEMPORARY TABLE cleanup_demo_external_order_ids;
DROP TEMPORARY TABLE cleanup_demo_order_ids;
DROP TEMPORARY TABLE cleanup_demo_store_sku_ids;
DROP TEMPORARY TABLE cleanup_demo_account_ids;
