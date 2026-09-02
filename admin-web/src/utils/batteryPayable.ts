import type { BatteryPayableSummary } from '../types/api';

export function batteryPayableQueryParams(month: string, storeId?: number) {
  return storeId == null ? { month } : { month, storeId };
}

export function batteryPayableBreakdown(
  summary: BatteryPayableSummary,
  formatMoney: (value: number) => string
) {
  return [
    `首期 ${formatMoney(summary.initialAmount)}（${summary.initialCount} 笔）`,
    `续租 ${formatMoney(summary.renewalAmount)}（${summary.renewalCount} 笔）`,
    `正式账单 ${formatMoney(summary.billAmount)}（${summary.billCount} 笔）`
  ].join(' · ');
}
