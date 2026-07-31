export type CockpitPeriod = 'MONTH' | 'TODAY' | '7D' | '30D' | '90D' | '180D' | '365D' | 'CUSTOM';

export type CockpitCustomRange = [Date, Date] | null;

export type DateWindow = {
  start: Date;
  end: Date;
  previousStart: Date;
  previousEnd: Date;
  label: string;
};

export type TimeBucket = {
  key: string;
  label: string;
  start: Date;
  end: Date;
};

export function getDateWindow(
  period: CockpitPeriod,
  now = new Date(),
  customRange: CockpitCustomRange = null,
  selectedMonth = now
): DateWindow {
  const end = endOfDay(now);
  let start: Date;
  let label: string;

  if (period === 'MONTH') {
    const month = normalizeSelectedMonth(selectedMonth, now);
    const monthStart = startOfMonth(month);
    const monthEnd = endOfMonth(month);
    const currentMonth = isSameMonth(month, now);
    const selectedEnd = currentMonth && monthEnd > end ? end : monthEnd;
    const previousMonthStart = startOfMonth(addMonths(monthStart, -1));
    const previousMonthEnd = endOfMonth(previousMonthStart);
    const previousEnd = currentMonth
      ? endOfDay(new Date(
        previousMonthStart.getFullYear(),
        previousMonthStart.getMonth(),
        Math.min(selectedEnd.getDate(), previousMonthEnd.getDate())
      ))
      : previousMonthEnd;
    return {
      start: monthStart,
      end: selectedEnd,
      previousStart: previousMonthStart,
      previousEnd,
      label: `${monthStart.getFullYear()}年${monthStart.getMonth() + 1}月`
    };
  } else if (period === 'TODAY') {
    start = startOfDay(now);
    label = '今日';
  } else if (period === 'CUSTOM') {
    const [customStart, customEnd] = normalizeCustomRange(customRange, now);
    start = customStart;
    label = `${dateKey(customStart)} 至 ${dateKey(customEnd)}`;
    const duration = customEnd.getTime() - customStart.getTime() + 1;
    const previousEnd = new Date(customStart.getTime() - 1);
    const previousStart = new Date(previousEnd.getTime() - duration + 1);
    return { start: customStart, end: customEnd, previousStart, previousEnd, label };
  } else {
    const days = Number(period.replace('D', ''));
    start = startOfDay(addDays(now, -(days - 1)));
    label = `近 ${days} 天`;
  }

  const duration = end.getTime() - start.getTime() + 1;
  const previousEnd = new Date(start.getTime() - 1);
  const previousStart = new Date(previousEnd.getTime() - duration + 1);
  return { start, end, previousStart, previousEnd, label };
}

export function defaultCockpitCustomRange(now = new Date()): [Date, Date] {
  return [startOfDay(addDays(now, -29)), endOfDay(now)];
}

export function cockpitRangeDays(range: [Date, Date]) {
  return Math.abs(differenceInCalendarDays(range[1], range[0])) + 1;
}

export function isInWindow(value: string | null | undefined, start: Date, end: Date) {
  if (!value) return false;
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) && timestamp >= start.getTime() && timestamp <= end.getTime();
}

export function percentageChange(current: number, previous: number) {
  if (previous === 0) return current === 0 ? 0 : null;
  return (current - previous) / Math.abs(previous) * 100;
}

export function compactMoney(value?: number | string | null) {
  const amount = Number(value || 0);
  if (Math.abs(amount) >= 100000000) return `¥${(amount / 100000000).toFixed(1)}亿`;
  if (Math.abs(amount) >= 10000) return `¥${(amount / 10000).toFixed(1)}万`;
  return `¥${amount.toFixed(2)}`;
}

export function money(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

export function percent(value?: number | string | null, digits = 1) {
  return `${Number(value || 0).toFixed(digits)}%`;
}

export function sumNumbers(values: Array<number | string | null | undefined>) {
  return values.reduce<number>((total, value) => total + Number(value || 0), 0);
}

export function buildTimeBuckets(window: Pick<DateWindow, 'start' | 'end'>, maxBuckets = 14): TimeBucket[] {
  const totalDays = Math.max(1, differenceInCalendarDays(window.end, window.start) + 1);
  const bucketDays = Math.max(1, Math.ceil(totalDays / maxBuckets));
  const buckets: TimeBucket[] = [];

  for (let cursor = startOfDay(window.start); cursor <= window.end; cursor = addDays(cursor, bucketDays)) {
    const bucketEnd = endOfDay(addDays(cursor, bucketDays - 1));
    const end = bucketEnd > window.end ? window.end : bucketEnd;
    const month = cursor.getMonth() + 1;
    const day = cursor.getDate();
    buckets.push({
      key: dateKey(cursor),
      label: totalDays > 180 ? `${month}月` : `${month}/${day}`,
      start: cursor,
      end
    });
  }
  return buckets;
}

export function valueByBuckets<T>(
  buckets: TimeBucket[],
  items: T[],
  dateValue: (item: T) => string | null | undefined,
  numberValue: (item: T) => number
) {
  return buckets.map((bucket) => items.reduce((total, item) => {
    return isInWindow(dateValue(item), bucket.start, bucket.end) ? total + numberValue(item) : total;
  }, 0));
}

export function dateTimeText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function endOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate(), 23, 59, 59, 999);
}

function addDays(value: Date, days: number) {
  const next = new Date(value);
  next.setDate(next.getDate() + days);
  return next;
}

function addMonths(value: Date, months: number) {
  return new Date(value.getFullYear(), value.getMonth() + months, 1);
}

function startOfMonth(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), 1);
}

function endOfMonth(value: Date) {
  return new Date(value.getFullYear(), value.getMonth() + 1, 0, 23, 59, 59, 999);
}

function isSameMonth(left: Date, right: Date) {
  return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth();
}

function differenceInCalendarDays(left: Date, right: Date) {
  const leftDay = Date.UTC(left.getFullYear(), left.getMonth(), left.getDate());
  const rightDay = Date.UTC(right.getFullYear(), right.getMonth(), right.getDate());
  return Math.round((leftDay - rightDay) / 86400000);
}

function dateKey(value: Date) {
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${value.getFullYear()}-${month}-${day}`;
}

function normalizeCustomRange(customRange: CockpitCustomRange, now: Date): [Date, Date] {
  const fallback = defaultCockpitCustomRange(now);
  if (!customRange) return fallback;
  const first = startOfDay(customRange[0]);
  const second = endOfDay(customRange[1]);
  if (!Number.isFinite(first.getTime()) || !Number.isFinite(second.getTime())) return fallback;
  const todayEnd = endOfDay(now);
  const rawStart = first <= second ? first : startOfDay(second);
  const rawEnd = first <= second ? second : endOfDay(first);
  const end = rawEnd > todayEnd ? todayEnd : rawEnd;
  const start = rawStart > end ? startOfDay(end) : rawStart;
  const boundedStart = cockpitRangeDays([start, end]) > 365 ? startOfDay(addDays(end, -364)) : start;
  return [boundedStart, end];
}

function normalizeSelectedMonth(selectedMonth: Date, now: Date) {
  if (!Number.isFinite(selectedMonth.getTime())) return startOfMonth(now);
  const month = startOfMonth(selectedMonth);
  return month > startOfMonth(now) ? startOfMonth(now) : month;
}
