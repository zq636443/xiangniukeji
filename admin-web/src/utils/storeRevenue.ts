import type { SettlementIncomeEntry } from '../types/api';

/**
 * 收益工作台统一口径：门店收益只统计已生成、未冻结的商户收益流水，
 * 并由运营分成、维修分成和办单费（97% 净收益口径）组成。
 *
 * `MERCHANT_RENT_SHARE` 是旧版运营分成流水，保留它是为了让历史账期
 * 在管理员和商户工作台使用同一口径时不会被遗漏。
 */
export const STORE_OPERATION_REVENUE_LINE_TYPES: ReadonlySet<SettlementIncomeEntry['lineType']> = new Set([
  'STORE_OPERATION_SHARE',
  'MERCHANT_RENT_SHARE'
]);

export const STORE_MAINTENANCE_REVENUE_LINE_TYPES: ReadonlySet<SettlementIncomeEntry['lineType']> = new Set([
  'MAINTENANCE_FUND_SHARE'
]);

export const STORE_ORDER_FEE_REVENUE_LINE_TYPES: ReadonlySet<SettlementIncomeEntry['lineType']> = new Set([
  'MERCHANT_ORDER_FEE'
]);

export const STORE_REVENUE_LINE_TYPES: ReadonlySet<SettlementIncomeEntry['lineType']> = new Set([
  ...STORE_OPERATION_REVENUE_LINE_TYPES,
  ...STORE_MAINTENANCE_REVENUE_LINE_TYPES,
  ...STORE_ORDER_FEE_REVENUE_LINE_TYPES
]);

export type StoreRevenueEntry = Pick<
  SettlementIncomeEntry,
  'sourceType' | 'beneficiaryType' | 'lineType' | 'entryStatus' | 'amount' | 'storeRevenueAmount'
>;

export type StoreRevenueBreakdown = {
  operation: number;
  maintenance: number;
  orderFee: number;
  total: number;
};

export type ProfitWeightSnapshot = {
  storeOperationRate?: number | string | null;
  maintenanceFundRate?: number | string | null;
  investorShareRate?: number | string | null;
};

/** Format the three configurable V3 snapshot rates as percentage weights. */
export function snapshotProfitWeightRatio(snapshot: ProfitWeightSnapshot) {
  return [snapshot.storeOperationRate, snapshot.maintenanceFundRate, snapshot.investorShareRate]
    .map((rate) => {
      const percentValue = Math.round(Number(rate ?? 0) * 10_000) / 100;
      return percentValue.toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1');
    })
    .join(':');
}

/** Merchant entitlement for an order-handling fee: 97%, rounded to cents. */
export function storeOrderFeeNetAmount(amount: number | string | null | undefined) {
  const cents = Math.round(Number(amount ?? 0) * 100);
  return Math.round(cents * 0.97) / 100;
}

/** Normalize snapshot fee values for audit displays without mutating history. */
export function snapshotStoreOrderFeeAmount(snapshot: {
  calculationVersion?: string | null;
  merchantOrderFeeAmount?: number | string | null;
  signFeeAmount?: number | string | null;
}) {
  const amount = Number(snapshot.merchantOrderFeeAmount ?? 0);
  const gross = Number(snapshot.signFeeAmount ?? 0);
  const netGross = storeOrderFeeNetAmount(gross);
  /* Early V2 snapshots left merchantOrderFeeAmount at zero, while some old
   * snapshots stored the gross fee.  Recognize those immutable fingerprints
   * for read-only reporting; current net snapshots pass through unchanged. */
  if (gross > 0 && (amount === 0 || Math.abs(amount - gross) < 0.005)) {
    return netGross;
  }
  if (gross > 0 && Math.abs(amount - netGross) < 0.005) {
    // A legacy/imported snapshot may already contain the merchant's 97% net
    // amount. Do not apply the 97% projection a second time.
    return amount;
  }
  if (snapshot.calculationVersion === 'LEGACY_V1' && amount > 0) {
    return storeOrderFeeNetAmount(amount);
  }
  return amount;
}

/** Return whether a ledger line is one of the store-revenue components. */
export function isStoreRevenueLine(entry: StoreRevenueEntry) {
  return entry.sourceType !== 'ORDER'
    && entry.beneficiaryType === 'MERCHANT'
    && STORE_REVENUE_LINE_TYPES.has(entry.lineType);
}

/** Return whether a ledger line belongs to the live store-revenue metric. */
export function isStoreRevenueEntry(entry: StoreRevenueEntry) {
  return entry.entryStatus !== 'FROZEN' && isStoreRevenueLine(entry);
}

/** Return the normalized store entitlement while preserving raw ledger data. */
export function storeRevenueEntryAmount(entry: StoreRevenueEntry) {
  return Number(entry.storeRevenueAmount ?? entry.amount ?? 0);
}

/**
 * Aggregate the same three store-revenue components used by both workbenches.
 * Filtering is intentionally done here so a new page cannot accidentally count
 * platform, investor, frozen, or unrelated merchant line types.
 */
export function summarizeStoreRevenue(entries: readonly StoreRevenueEntry[]): StoreRevenueBreakdown {
  return entries.reduce<StoreRevenueBreakdown>((summary, entry) => {
    if (!isStoreRevenueEntry(entry)) return summary;
    const amount = storeRevenueEntryAmount(entry);
    if (STORE_OPERATION_REVENUE_LINE_TYPES.has(entry.lineType)) summary.operation += amount;
    if (STORE_MAINTENANCE_REVENUE_LINE_TYPES.has(entry.lineType)) summary.maintenance += amount;
    if (STORE_ORDER_FEE_REVENUE_LINE_TYPES.has(entry.lineType)) summary.orderFee += amount;
    summary.total += amount;
    return summary;
  }, { operation: 0, maintenance: 0, orderFee: 0, total: 0 });
}
