import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, DatePicker, Empty, Progress, Select, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import {
  cockpitRangeDays,
  defaultCockpitCustomRange,
  type CockpitCustomRange,
  type CockpitPeriod
} from '../utils/dashboard';

const { RangePicker } = DatePicker;

export type CockpitTone = 'green' | 'blue' | 'orange' | 'red' | 'violet';

type PeriodOption = {
  label: string;
  value: CockpitPeriod;
};

export function CockpitHeader(props: {
  eyebrow: string;
  title: string;
  description: ReactNode;
  period: CockpitPeriod;
  periodOptions?: PeriodOption[];
  onPeriodChange: (period: CockpitPeriod) => void;
  customRange?: CockpitCustomRange;
  onCustomRangeChange?: (range: CockpitCustomRange) => void;
  selectedMonth?: Date;
  onSelectedMonthChange?: (month: Date) => void;
  onRefresh: () => void;
  loading?: boolean;
  scope?: ReactNode;
}) {
  const periodOptions = props.periodOptions || [
    { label: '按月查看', value: 'MONTH' },
    { label: '今日', value: 'TODAY' },
    { label: '近 7 天', value: '7D' },
    { label: '近 30 天', value: '30D' },
    { label: '近 90 天', value: '90D' },
    { label: '近 180 天', value: '180D' },
    { label: '近 365 天', value: '365D' },
    { label: '自定义', value: 'CUSTOM' }
  ];
  const customRange = props.customRange || null;

  function changePeriod(nextPeriod: CockpitPeriod) {
    if (nextPeriod === 'MONTH' && !props.selectedMonth) {
      props.onSelectedMonthChange?.(new Date());
    }
    if (nextPeriod === 'CUSTOM' && !customRange) {
      props.onCustomRangeChange?.(defaultCockpitCustomRange());
    }
    props.onPeriodChange(nextPeriod);
  }

  function changeCustomRange(values: null | [dayjs.Dayjs | null, dayjs.Dayjs | null]) {
    if (!values?.[0] || !values[1]) {
      props.onCustomRangeChange?.(null);
      return;
    }
    const nextRange: [Date, Date] = [values[0].startOf('day').toDate(), values[1].endOf('day').toDate()];
    if (cockpitRangeDays(nextRange) > 365) {
      message.error('自定义时间范围最长为 365 天');
      return;
    }
    props.onCustomRangeChange?.(nextRange);
  }

  return (
    <section className="cockpit-header">
      <div className="cockpit-header-copy">
        <Typography.Text className="page-eyebrow">{props.eyebrow}</Typography.Text>
        <Typography.Title level={3}>{props.title}</Typography.Title>
        <Typography.Text type="secondary">{props.description}</Typography.Text>
      </div>
      <div className="cockpit-header-controls">
        {props.scope ? <div className="cockpit-scope">{props.scope}</div> : null}
        <Select
          className="cockpit-period-select"
          value={props.period}
          options={periodOptions}
          onChange={changePeriod}
        />
        {props.period === 'MONTH' ? (
          <DatePicker
            className="cockpit-month-picker"
            picker="month"
            allowClear={false}
            value={dayjs(props.selectedMonth || new Date())}
            onChange={(value) => props.onSelectedMonthChange?.((value || dayjs()).startOf('month').toDate())}
            disabledDate={(current) => current.isAfter(dayjs(), 'month')}
            format="YYYY年MM月"
          />
        ) : null}
        {props.period === 'CUSTOM' ? (
          <RangePicker
            allowClear={false}
            value={customRange ? [dayjs(customRange[0]), dayjs(customRange[1])] : null}
            onChange={changeCustomRange}
            disabledDate={(current, info) => {
              if (current.isAfter(dayjs(), 'day')) return true;
              return info.from ? Math.abs(current.startOf('day').diff(info.from.startOf('day'), 'day')) > 364 : false;
            }}
            format="YYYY-MM-DD"
            placeholder={['开始日期', '结束日期']}
          />
        ) : null}
        <Button type="primary" icon={<ReloadOutlined />} loading={props.loading} onClick={props.onRefresh}>刷新</Button>
      </div>
    </section>
  );
}

export function CockpitMetric(props: {
  icon: ReactNode;
  tone: CockpitTone;
  label: string;
  value: ReactNode;
  detail: ReactNode;
  change?: number | null;
  changeLabel?: string;
  inverseChange?: boolean;
}) {
  const change = props.change;
  const favorable = change != null && (props.inverseChange ? change < 0 : change > 0);
  const unfavorable = change != null && (props.inverseChange ? change > 0 : change < 0);
  return (
    <section className={`cockpit-metric cockpit-tone-${props.tone}`}>
      <div className="cockpit-metric-head">
        <span className={`metric-icon ${props.tone}`}>{props.icon}</span>
        <span>{props.label}</span>
      </div>
      <div className="cockpit-metric-value">{props.value}</div>
      <div className="cockpit-metric-foot">
        <span>{props.detail}</span>
        {change !== undefined ? (
          <span className={`cockpit-delta${favorable ? ' positive' : ''}${unfavorable ? ' negative' : ''}`}>
            {change == null ? <ArrowUpOutlined /> : change > 0 ? <ArrowUpOutlined /> : change < 0 ? <ArrowDownOutlined /> : <MinusOutlined />}
            {change == null ? '新增' : `${Math.abs(change).toFixed(1)}%`}
            {props.changeLabel ? ` ${props.changeLabel}` : ''}
          </span>
        ) : null}
      </div>
    </section>
  );
}

export function CockpitPanel(props: {
  title: string;
  subtitle?: ReactNode;
  extra?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={`cockpit-panel${props.className ? ` ${props.className}` : ''}`}>
      <div className="cockpit-panel-head">
        <div>
          <Typography.Title level={4}>{props.title}</Typography.Title>
          {props.subtitle ? <Typography.Text type="secondary">{props.subtitle}</Typography.Text> : null}
        </div>
        {props.extra}
      </div>
      {props.children}
    </section>
  );
}

export function CockpitTrend(props: {
  labels: string[];
  primary: number[];
  secondary?: number[];
  primaryLabel: string;
  secondaryLabel?: string;
  primaryFormatter?: (value: number) => string;
  secondaryFormatter?: (value: number) => string;
}) {
  const allValues = [...props.primary, ...(props.secondary || [])];
  const max = Math.max(1, ...allValues);
  const width = 720;
  const height = 220;
  const paddingX = 18;
  const paddingTop = 18;
  const paddingBottom = 28;
  const plotHeight = height - paddingTop - paddingBottom;
  const point = (value: number, index: number, count: number) => ({
    x: count <= 1 ? width / 2 : paddingX + index * (width - paddingX * 2) / (count - 1),
    y: paddingTop + plotHeight - value / max * plotHeight
  });
  const path = (values: number[]) => values.map((value, index) => {
    const current = point(value, index, values.length);
    return `${index === 0 ? 'M' : 'L'} ${current.x.toFixed(1)} ${current.y.toFixed(1)}`;
  }).join(' ');
  const hasData = allValues.some((value) => value !== 0);
  const primaryFormatter = props.primaryFormatter || ((value: number) => String(value));
  const secondaryFormatter = props.secondaryFormatter || primaryFormatter;

  return (
    <div className="cockpit-trend">
      <div className="cockpit-chart-legend">
        <span><i className="primary" />{props.primaryLabel} <strong>{primaryFormatter(props.primary.reduce((sum, value) => sum + value, 0))}</strong></span>
        {props.secondary && props.secondaryLabel ? <span><i className="secondary" />{props.secondaryLabel} <strong>{secondaryFormatter(props.secondary.reduce((sum, value) => sum + value, 0))}</strong></span> : null}
      </div>
      {hasData ? (
        <div className="cockpit-chart-scroll">
          <svg className="cockpit-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${props.primaryLabel}趋势图`}>
            {[0, 1, 2, 3].map((line) => {
              const y = paddingTop + line * plotHeight / 3;
              return <line key={line} x1={paddingX} x2={width - paddingX} y1={y} y2={y} className="cockpit-chart-grid" />;
            })}
            {props.secondary ? <path d={path(props.secondary)} className="cockpit-chart-line secondary" /> : null}
            <path d={path(props.primary)} className="cockpit-chart-line primary" />
            {props.primary.map((value, index) => {
              const current = point(value, index, props.primary.length);
              return <circle key={index} cx={current.x} cy={current.y} r="3.5" className="cockpit-chart-point" />;
            })}
            {props.labels.map((label, index) => {
              if (props.labels.length > 9 && index % 2 === 1 && index !== props.labels.length - 1) return null;
              const current = point(0, index, props.labels.length);
              return <text key={`${label}-${index}`} x={current.x} y={height - 5} textAnchor="middle" className="cockpit-chart-label">{label}</text>;
            })}
          </svg>
        </div>
      ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前周期暂无趋势数据" />}
    </div>
  );
}

export type ProgressRow = {
  key: string;
  label: string;
  value: number;
  total?: number;
  detail?: ReactNode;
  color?: string;
};

export function CockpitProgressList({ rows }: { rows: ProgressRow[] }) {
  const max = Math.max(1, ...rows.map((row) => row.total || row.value));
  return (
    <div className="cockpit-progress-list">
      {rows.map((row) => (
        <div className="cockpit-progress-row" key={row.key}>
          <div className="cockpit-progress-copy">
            <strong>{row.label}</strong>
            <span>{row.detail ?? row.value}</span>
          </div>
          <Progress
            percent={Math.min(100, Math.round(row.value / (row.total || max) * 100))}
            showInfo={false}
            strokeColor={row.color || '#0f9f7a'}
            trailColor="#eef2f6"
            size="small"
          />
        </div>
      ))}
    </div>
  );
}

export type AttentionRow = {
  key: string;
  icon: ReactNode;
  tone: CockpitTone;
  label: string;
  detail: ReactNode;
  value: ReactNode;
  tag?: string;
};

export function CockpitAttentionList({ rows }: { rows: AttentionRow[] }) {
  return (
    <div className="cockpit-attention-list">
      {rows.map((row) => (
        <div className="cockpit-attention-row" key={row.key}>
          <span className={`metric-icon ${row.tone}`}>{row.icon}</span>
          <div>
            <span>{row.label}{row.tag ? <Tag color={row.tone === 'red' ? 'red' : row.tone === 'orange' ? 'gold' : 'blue'}>{row.tag}</Tag> : null}</span>
            <small>{row.detail}</small>
          </div>
          <strong>{row.value}</strong>
        </div>
      ))}
    </div>
  );
}
