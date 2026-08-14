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

function buildDefaultPackagePrice(template: PackageTemplate): StorePackagePriceForm {
  const rentalAmount = Number(template.priceAmount || 0);
  const totalPeriods = Math.max(Number(template.totalPeriods || 0), 1);
  const periodAmount = Number((rentalAmount / totalPeriods).toFixed(2));

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
