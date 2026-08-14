import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildDefaultPackagePrices,
  reconcilePackagePrices,
  type StorePackagePriceForm
} from '../src/utils/storeProductPublishing.ts';

type PackageTemplate = Parameters<typeof buildDefaultPackagePrices>[1][number];

function packageTemplate(overrides: Partial<PackageTemplate> = {}): PackageTemplate {
  return {
    id: 1,
    skuId: 100,
    priceAmount: 300,
    leaseUnit: 'MONTH',
    leaseValue: 3,
    totalPeriods: 3,
    status: 'ENABLED',
    ...overrides
  };
}

test('buildDefaultPackagePrices selects every enabled SKU for the link in template order', () => {
  const templates = [
    packageTemplate({ id: 90, skuId: 999 }),
    packageTemplate({ id: 11 }),
    packageTemplate({ id: 12, status: 'DISABLED' }),
    packageTemplate({ id: 13 })
  ];

  const result = buildDefaultPackagePrices(100, templates);

  assert.deepEqual(result.map((item) => item.packageId), [11, 13]);
});

test('buildDefaultPackagePrices derives the complete publishing defaults from the template', () => {
  const [result] = buildDefaultPackagePrices(100, [packageTemplate({
    id: 21,
    priceAmount: 100,
    leaseUnit: 'DAY',
    leaseValue: 5,
    totalPeriods: 2
  })]);

  assert.deepEqual(result, {
    packageId: 21,
    rentalAmount: 100,
    periodAmount: 50,
    depositAmount: 0,
    autoRenewEnabled: true,
    renewalUnit: 'DAY',
    renewalValue: 2,
    renewalAmount: 50,
    renewalBillingMode: 'PERIOD',
    renewalDailyCapEnabled: true,
    renewalGraceHours: 0
  });

  const [rounded] = buildDefaultPackagePrices(100, [packageTemplate({ priceAmount: 100, totalPeriods: 3 })]);
  assert.equal(rounded.periodAmount, 33.33);
  assert.equal(rounded.renewalAmount, 33.33);
});

test('reconcilePackagePrices removes deselected SKUs, preserves selected custom values, and defaults new SKUs', () => {
  const deselected: StorePackagePriceForm = {
    packageId: 11,
    rentalAmount: 200,
    periodAmount: 100,
    depositAmount: 20,
    autoRenewEnabled: true,
    renewalUnit: 'MONTH',
    renewalValue: 1,
    renewalAmount: 100,
    renewalBillingMode: 'PERIOD',
    renewalDailyCapEnabled: true,
    renewalGraceHours: 0
  };
  const retained: StorePackagePriceForm = {
    packageId: 12,
    rentalAmount: 240,
    periodAmount: 77.77,
    depositAmount: 88,
    autoRenewEnabled: false,
    renewalUnit: 'DAY',
    renewalValue: 9,
    renewalAmount: 66.66,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 8.88,
    renewalDailyCapEnabled: false,
    renewalGraceHours: 12,
    overdueDailyAmount: 9.99
  };
  const templates = [
    packageTemplate({ id: 11, priceAmount: 200 }),
    packageTemplate({ id: 12, priceAmount: 240 }),
    packageTemplate({ id: 13, priceAmount: 99, leaseUnit: 'DAY', leaseValue: 2, totalPeriods: 3 })
  ];

  const result = reconcilePackagePrices([deselected, retained], [12, 13], templates);

  assert.equal(result.length, 2);
  assert.strictEqual(result[0], retained);
  assert.deepEqual(result[1], {
    packageId: 13,
    rentalAmount: 99,
    periodAmount: 33,
    depositAmount: 0,
    autoRenewEnabled: true,
    renewalUnit: 'DAY',
    renewalValue: 1,
    renewalAmount: 33,
    renewalBillingMode: 'PERIOD',
    renewalDailyCapEnabled: true,
    renewalGraceHours: 0
  });
});
