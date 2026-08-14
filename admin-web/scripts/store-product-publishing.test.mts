import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildDefaultPackagePrices,
  findInactivePackageIds,
  getStorePackagePriceValidationErrors,
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

test('buildDefaultPackagePrices rounds positive amounts HALF_UP from integer cents', () => {
  const result = buildDefaultPackagePrices(100, [
    packageTemplate({ id: 31, priceAmount: 2.01, totalPeriods: 2 }),
    packageTemplate({ id: 32, priceAmount: 10.05, totalPeriods: 2 })
  ]);

  assert.deepEqual(result.map((item) => item.periodAmount), [1.01, 5.03]);
  assert.deepEqual(result.map((item) => item.renewalAmount), [1.01, 5.03]);
});

test('buildDefaultPackagePrices disables auto-renew when the default period amount is zero', () => {
  const result = buildDefaultPackagePrices(100, [
    packageTemplate({ id: 41, priceAmount: 0, totalPeriods: 2 }),
    packageTemplate({ id: 42, priceAmount: 0.01, totalPeriods: 3 })
  ]);

  assert.deepEqual(result.map((item) => item.periodAmount), [0, 0]);
  assert.deepEqual(result.map((item) => item.autoRenewEnabled), [false, false]);
  assert.deepEqual(result.map((item) => item.renewalAmount), [0, 0]);
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
    renewalAmount: 0,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 8.88,
    renewalDailyCapEnabled: false,
    renewalGraceHours: 12,
    overdueDailyAmount: 9.99
  };
  const templates = [
    packageTemplate({ id: 11, priceAmount: 200 }),
    packageTemplate({ id: 12, priceAmount: 240, status: 'DISABLED' }),
    packageTemplate({ id: 13, priceAmount: 99, leaseUnit: 'DAY', leaseValue: 2, totalPeriods: 3 })
  ];

  const result = reconcilePackagePrices([deselected, retained], [12, 13], templates);

  assert.equal(result.length, 2);
  assert.strictEqual(result[0], retained);
  assert.equal(result[0].renewalAmount, 0);
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

test('findInactivePackageIds reports disabled and missing selected templates without removing them', () => {
  const selectedPackageIds = [11, 12, 13];

  const inactivePackageIds = findInactivePackageIds(selectedPackageIds, [
    { id: 11, status: 'ENABLED' },
    { id: 12, status: 'DISABLED' }
  ]);

  assert.deepEqual(inactivePackageIds, [12, 13]);
});

test('getStorePackagePriceValidationErrors does not require renewal fields when auto-renew is off', () => {
  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: false,
    renewalValue: 0,
    renewalAmount: 0,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 0,
    overdueDailyAmount: 0
  }), {});
});

test('getStorePackagePriceValidationErrors requires positive renewal amounts for enabled modes', () => {
  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalValue: 0,
    renewalAmount: undefined,
    renewalBillingMode: 'PERIOD'
  }), {
    renewalValue: '请输入大于 0 的续租周期',
    renewalAmount: '请输入大于 0 的续租金额'
  });

  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalValue: 1,
    renewalAmount: 1,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 0,
    overdueDailyAmount: 0
  }), {
    renewalDailyAmount: '按日计费时请输入大于 0 的日续租价',
    overdueDailyAmount: '逾期日占用费必须大于 0'
  });

  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalValue: 1,
    renewalAmount: 1,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 0.01,
    overdueDailyAmount: 0.01
  }), {});
});

test('getStorePackagePriceValidationErrors rejects capped daily totals below the period amount', () => {
  const expectedError = {
    renewalDailyAmount: '启用整期封顶时，日租累计整期金额不能低于整期续租价'
  };

  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalUnit: 'MONTH',
    renewalValue: 1,
    renewalAmount: 100,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 3,
    renewalDailyCapEnabled: true
  }), expectedError);

  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalUnit: 'DAY',
    renewalValue: 5,
    renewalAmount: 11,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 2,
    renewalDailyCapEnabled: true
  }), expectedError);

  assert.deepEqual(getStorePackagePriceValidationErrors({
    autoRenewEnabled: true,
    renewalUnit: 'MONTH',
    renewalValue: 1,
    renewalAmount: 100,
    renewalBillingMode: 'DAILY_CAPPED',
    renewalDailyAmount: 3,
    renewalDailyCapEnabled: false
  }), {});
});
