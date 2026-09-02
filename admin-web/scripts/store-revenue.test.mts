import assert from 'node:assert/strict';
import test from 'node:test';

import {
  isStoreRevenueEntry,
  isStoreRevenueLine,
  snapshotProfitWeightRatio,
  snapshotStoreOrderFeeAmount,
  storeOrderFeeNetAmount,
  storeRevenueEntryAmount,
  summarizeStoreRevenue
} from '../src/utils/storeRevenue.ts';
import { externalOrderInitialCollectedAmount } from '../src/utils/dashboard.ts';

const entry = (overrides = {}) => ({
  sourceType: 'EXTERNAL_ORDER',
  beneficiaryType: 'MERCHANT',
  lineType: 'STORE_OPERATION_SHARE',
  entryStatus: 'SETTLED',
  amount: 0,
  ...overrides
});

test('store revenue uses operation + maintenance + 97% net order fee', () => {
  const result = summarizeStoreRevenue([
    entry({ lineType: 'STORE_OPERATION_SHARE', amount: 100 }),
    entry({ lineType: 'MERCHANT_RENT_SHARE', amount: 20 }),
    entry({ lineType: 'MAINTENANCE_FUND_SHARE', amount: 30 }),
    entry({ lineType: 'MERCHANT_ORDER_FEE', amount: 19.4 })
  ]);

  assert.deepEqual(result, {
    operation: 120,
    maintenance: 30,
    orderFee: 19.4,
    total: 169.4
  });
});

test('order handling fee applies 97% and rounds half up to cents', () => {
  assert.equal(storeOrderFeeNetAmount(20), 19.4);
  assert.equal(storeOrderFeeNetAmount(1.5), 1.46);
});

test('V3 allocation weight label comes from the immutable snapshot rates', () => {
  assert.equal(snapshotProfitWeightRatio({
    storeOperationRate: 0.15,
    maintenanceFundRate: 0.1,
    investorShareRate: 0.55
  }), '15:10:55');
  assert.equal(snapshotProfitWeightRatio({
    storeOperationRate: 0.1875,
    maintenanceFundRate: 0.0625,
    investorShareRate: 0.55
  }), '18.75:6.25:55');
});

test('historical snapshot fee is projected while current net snapshot stays unchanged', () => {
  assert.equal(snapshotStoreOrderFeeAmount({ calculationVersion: 'LEGACY_V1', merchantOrderFeeAmount: 20 }), 19.4);
  assert.equal(snapshotStoreOrderFeeAmount({ calculationVersion: 'LEGACY_V1', merchantOrderFeeAmount: 19.4, signFeeAmount: 20 }), 19.4);
  assert.equal(snapshotStoreOrderFeeAmount({ calculationVersion: 'PROFIT_V2', merchantOrderFeeAmount: 0, signFeeAmount: 20 }), 19.4);
  assert.equal(snapshotStoreOrderFeeAmount({ calculationVersion: 'PROFIT_V2', merchantOrderFeeAmount: 20, signFeeAmount: 20 }), 19.4);
  assert.equal(snapshotStoreOrderFeeAmount({ calculationVersion: 'PROFIT_V2', merchantOrderFeeAmount: 19.4, signFeeAmount: 20 }), 19.4);
});

test('store revenue excludes order-source, frozen, non-merchant, and unrelated lines', () => {
  const valid = entry({ lineType: 'MERCHANT_ORDER_FEE', amount: 19.4 });
  assert.equal(isStoreRevenueEntry(valid), true);
  assert.equal(isStoreRevenueLine(entry({ entryStatus: 'FROZEN' })), true);
  assert.equal(isStoreRevenueEntry(entry({ sourceType: 'ORDER', amount: 100 })), false);
  assert.equal(isStoreRevenueEntry(entry({ entryStatus: 'FROZEN', amount: 100 })), false);
  assert.equal(isStoreRevenueEntry(entry({ beneficiaryType: 'PLATFORM', amount: 100 })), false);
  assert.equal(isStoreRevenueEntry(entry({ lineType: 'PLATFORM_ORDER_FEE_SERVICE_FEE', amount: 3 })), false);
  assert.deepEqual(summarizeStoreRevenue([valid, entry({ sourceType: 'ORDER', amount: 100 })]).total, 19.4);
});

test('store revenue uses projected net amount without mutating the raw legacy ledger amount', () => {
  const legacyGross = entry({
    lineType: 'MERCHANT_ORDER_FEE',
    amount: 20,
    storeRevenueAmount: 19.4
  });

  assert.equal(storeRevenueEntryAmount(legacyGross), 19.4);
  assert.equal(legacyGross.amount, 20);
  assert.equal(summarizeStoreRevenue([legacyGross]).total, 19.4);
});

test('dashboard first-period collection stays on the frozen gross rental amount', () => {
  assert.equal(externalOrderInitialCollectedAmount({ settlementRentalAmount: 129, settlementBaseAmount: 96, verificationAmount: 80 }), 129);
  assert.equal(externalOrderInitialCollectedAmount({ settlementRentalAmount: null, settlementBaseAmount: 96, verificationAmount: 80 }), 80);
});
