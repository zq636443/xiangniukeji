import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

import {
  batteryPayableBreakdown,
  batteryPayableQueryParams
} from '../src/utils/batteryPayable.ts';

test('admin all-store request omits storeId while scoped requests include it', () => {
  assert.deepEqual(batteryPayableQueryParams('2026-08'), { month: '2026-08' });
  assert.deepEqual(batteryPayableQueryParams('2026-08', 12), { month: '2026-08', storeId: 12 });
});

test('battery payable detail exposes initial, renewal, and formal bill amounts with counts', () => {
  const detail = batteryPayableBreakdown({
    statementMonth: '2026-08',
    storeId: 12,
    initialAmount: 1360,
    renewalAmount: 476,
    billAmount: 204,
    totalAmount: 2040,
    initialCount: 8,
    renewalCount: 4,
    billCount: 1
  }, (value) => `¥${value.toFixed(2)}`);

  assert.equal(detail, '首期 ¥1360.00（8 笔） · 续租 ¥476.00（4 笔） · 正式账单 ¥204.00（1 笔）');
});

test('dashboards use authoritative totals and product management exposes daily cost only', async () => {
  const [adminDashboard, merchantWorkspace, productManagement] = await Promise.all([
    readFile(new URL('../src/pages/Dashboard.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/MerchantWorkspace.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/ProductManagement.tsx', import.meta.url), 'utf8')
  ]);

  assert.match(adminDashboard, /\/api\/admin\/settlement\/statements\/battery-payable/);
  assert.match(merchantWorkspace, /\/api\/merchant\/settlement\/statements\/battery-payable/);
  assert.match(adminDashboard, /batteryPayableState\.data\?\.totalAmount/);
  assert.match(merchantWorkspace, /batteryPayableState\.data\?\.totalAmount/);
  assert.doesNotMatch(adminDashboard, /includedInMerchantStatement/);
  assert.doesNotMatch(merchantWorkspace, /includedInMerchantStatement/);
  assert.doesNotMatch(productManagement, /batteryCostMonthlyAmount/);
  assert.match(productManagement, /实际租期天数 × 日成本/);
});
