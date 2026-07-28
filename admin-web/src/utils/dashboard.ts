export type CockpitPeriod = 'TODAY' | '7D' | '30D' | '90D' | 'YTD';

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

export function getDateWindow(period: CockpitPeriod, now = new Date()): DateWindow {
  const end = endOfDay(now);
  let start: Date;
  let label: string;

  if (period === 'TODAY') {
    start = startOfDay(now);
    label = '今日';
  } else if (period === 'YTD') {
    start = new Date(now.getFullYear(), 0, 1);
    label = '本年度';
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
