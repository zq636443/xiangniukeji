import type { ProductPackage } from '../types/api';

export type StorePackagePriceForm = {
  packageId: number;
  rentalAmount: number;
  periodAmount: number;
  depositAmount: number;
  autoRenewEnabled?: boolean;
  renewalUnit?: 'DAY' | 'MONTH';
  renewalValue?: number;
  renewalAmount?: number;
  renewalBillingMode?: 'PERIOD' | 'DAILY_CAPPED';
  renewalDailyAmount?: number;
  renewalDailyCapEnabled?: boolean;
  renewalGraceHours?: number;
  overdueDailyAmount?: number;
};

type PackageTemplate = Pick<
  ProductPackage,
  'id' | 'skuId' | 'priceAmount' | 'leaseUnit' | 'leaseValue' | 'totalPeriods' | 'status'
>;

type PackageStatusTemplate = Pick<ProductPackage, 'id' | 'status'>;

export type StorePackagePriceValidationErrors = Partial<Record<
  'renewalValue' | 'renewalAmount' | 'renewalDailyAmount' | 'overdueDailyAmount',
  string
>>;

function buildDefaultPackagePrice(template: PackageTemplate): StorePackagePriceForm {
  const rentalAmount = Number(template.priceAmount || 0);
  const totalPeriods = Math.max(Number(template.totalPeriods || 0), 1);
  const rentalAmountInCents = Math.round(rentalAmount * 100);
  const periodAmount = Math.round(rentalAmountInCents / totalPeriods) / 100;

  return {
    packageId: template.id,
    rentalAmount,
    periodAmount,
    depositAmount: 0,
    autoRenewEnabled: true,
    renewalUnit: template.leaseUnit,
    renewalValue: Math.max(1, Math.floor(Number(template.leaseValue || 0) / totalPeriods)),
    renewalAmount: periodAmount,
    renewalBillingMode: 'PERIOD',
    renewalDailyCapEnabled: true,
    renewalGraceHours: 0
  };
}

export function buildDefaultPackagePrices(
  skuId: number,
  templates: readonly PackageTemplate[]
): StorePackagePriceForm[] {
  return templates
    .filter((template) => template.skuId === skuId && template.status === 'ENABLED')
    .map(buildDefaultPackagePrice);
}

export function reconcilePackagePrices(
  current: readonly StorePackagePriceForm[] | undefined,
  selectedPackageIds: readonly number[],
  templates: readonly PackageTemplate[]
): StorePackagePriceForm[] {
  const currentByPackageId = new Map((current ?? []).map((item) => [item.packageId, item]));
  const templatesByPackageId = new Map(templates.map((template) => [template.id, template]));

  return selectedPackageIds.flatMap((packageId) => {
    const existing = currentByPackageId.get(packageId);
    if (existing) {
      return [existing];
    }

    const template = templatesByPackageId.get(packageId);
    return template ? [buildDefaultPackagePrice(template)] : [];
  });
}

export function findInactivePackageIds(
  selectedPackageIds: readonly number[],
  templates: readonly PackageStatusTemplate[]
): number[] {
  const statusByPackageId = new Map(templates.map((template) => [template.id, template.status]));
  return selectedPackageIds.filter((packageId) => statusByPackageId.get(packageId) !== 'ENABLED');
}

export function getStorePackagePriceValidationErrors(
  packagePrice: Partial<StorePackagePriceForm>
): StorePackagePriceValidationErrors {
  const errors: StorePackagePriceValidationErrors = {};
  const isPositive = (value: number | undefined) => value != null && Number.isFinite(value) && value > 0;

  if (packagePrice.autoRenewEnabled) {
    if (!isPositive(packagePrice.renewalValue)) {
      errors.renewalValue = '请输入大于 0 的续租周期';
    }
    if (!isPositive(packagePrice.renewalAmount)) {
      errors.renewalAmount = '请输入大于 0 的续租金额';
    }
    if (packagePrice.renewalBillingMode === 'DAILY_CAPPED' && !isPositive(packagePrice.renewalDailyAmount)) {
      errors.renewalDailyAmount = '按日计费时请输入大于 0 的日续租价';
    }
  }

  if (packagePrice.overdueDailyAmount != null && !isPositive(packagePrice.overdueDailyAmount)) {
    errors.overdueDailyAmount = '逾期日占用费必须大于 0';
  }

  return errors;
}
