import {
  BankOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ShopOutlined,
  ToolOutlined,
  WalletOutlined
} from '@ant-design/icons';
import { Alert, Empty, Progress, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  CockpitAttentionList,
  CockpitHeader,
  CockpitMetric,
  CockpitPanel,
  CockpitProgressList,
  CockpitTrend
} from '../components/OperationsCockpit';
import { http } from '../services/request';
import type { Asset, CurrentAccount, SettlementIncomeEntry, SettlementStatement } from '../types/api';
import {
  buildTimeBuckets,
  compactMoney,
  getDateWindow,
  isInWindow,
  money,
  percent,
  percentageChange,
  sumNumbers,
  valueByBuckets,
  type CockpitCustomRange,
  type CockpitPeriod
} from '../utils/dashboard';

type InvestorOperationsCockpitProps = {
  account: CurrentAccount;
};

type StorePerformance = {
  key: string;
  merchantName: string;
  storeName: string;
  total: number;
  renting: number;
  attention: number;
  purchaseAmount: number;
  periodIncome: number;
  deploymentRate: number;
  incomePerAsset: number;
};

export function InvestorOperationsCockpit({ account }: InvestorOperationsCockpitProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [period, setPeriod] = useState<CockpitPeriod>('MONTH');
  const [customRange, setCustomRange] = useState<CockpitCustomRange>(null);
  const [selectedMonth, setSelectedMonth] = useState(new Date());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [assetData, incomeData, statementData] = await Promise.all([
        http.get<unknown, Asset[]>('/api/investor/assets'),
        http.get<unknown, SettlementIncomeEntry[]>('/api/investor/settlement/income/entries'),
        http.get<unknown, SettlementStatement[]>('/api/investor/settlement/statements')
      ]);
      setAssets(assetData);
      setEntries(incomeData);
      setStatements(statementData);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '出资方经营驾驶舱加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const window = useMemo(() => getDateWindow(period, new Date(), customRange, selectedMonth), [customRange, period, selectedMonth]);
  const actualEntries = useMemo(
    () => entries.filter((item) => item.sourceType !== 'ORDER' && item.entryStatus !== 'FROZEN'),
    [entries]
  );
  const dashboard = useMemo(() => {
    const activeAssets = assets.filter((item) => !['SCRAPPED', 'SOLD'].includes(item.status));
    const rentingAssets = activeAssets.filter((item) => item.status === 'RENTING');
    const idleAssets = activeAssets.filter((item) => item.status === 'IDLE');
    const repairAssets = activeAssets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status));
    const unassignedAssets = activeAssets.filter((item) => !item.currentStoreId);
    const periodEntries = actualEntries.filter((item) => isInWindow(item.occurredAt, window.start, window.end));
    const previousEntries = actualEntries.filter((item) => isInWindow(item.occurredAt, window.previousStart, window.previousEnd));
    const periodIncome = sumNumbers(periodEntries.map((item) => item.amount));
    const previousIncome = sumNumbers(previousEntries.map((item) => item.amount));
    const periodSettledIncome = sumNumbers(periodEntries.filter((item) => item.entryStatus === 'SETTLED').map((item) => item.amount));
    const settledIncome = sumNumbers(actualEntries.filter((item) => item.entryStatus === 'SETTLED').map((item) => item.amount));
    const pendingIncome = sumNumbers(actualEntries.filter((item) => item.entryStatus === 'PENDING').map((item) => item.amount));
    const frozenIncome = sumNumbers(entries.filter((item) => item.sourceType !== 'ORDER' && item.entryStatus === 'FROZEN').map((item) => item.amount));
    const purchaseAmount = sumNumbers(activeAssets.map((item) => item.purchaseAmount));
    const payableStatements = statements.filter((item) => ['CONFIRMED', 'PAYABLE'].includes(item.status));
    return {
      activeAssets,
      rentingAssets,
      idleAssets,
      repairAssets,
      unassignedAssets,
      periodEntries,
      periodIncome,
      periodSettledIncome,
      incomeChange: percentageChange(periodIncome, previousIncome),
      settledIncome,
      pendingIncome,
      frozenIncome,
      purchaseAmount,
      deploymentRate: activeAssets.length ? rentingAssets.length / activeAssets.length * 100 : 0,
      cumulativeReturnRate: purchaseAmount ? settledIncome / purchaseAmount * 100 : 0,
      payableStatementAmount: sumNumbers(payableStatements.map((item) => item.payableAmount)),
      payableStatementCount: payableStatements.length
    };
  }, [actualEntries, assets, entries, statements, window]);

  const trend = useMemo(() => {
    const buckets = buildTimeBuckets(window);
    return {
      labels: buckets.map((item) => item.label),
      income: valueByBuckets(buckets, actualEntries, (item) => item.occurredAt, (item) => Number(item.amount || 0)),
      settled: valueByBuckets(buckets, actualEntries.filter((item) => item.entryStatus === 'SETTLED'), (item) => item.occurredAt, (item) => Number(item.amount || 0))
    };
  }, [actualEntries, window]);

  const storePerformance = useMemo<StorePerformance[]>(() => {
    const map = new Map<string, Omit<StorePerformance, 'deploymentRate' | 'incomePerAsset'>>();
    dashboard.activeAssets.forEach((asset) => {
      const key = asset.currentStoreId ? `STORE:${asset.currentStoreId}` : 'UNASSIGNED';
      const row = map.get(key) || {
        key,
        merchantName: asset.merchantName || '未分配商户',
        storeName: asset.storeName || '未分配门店',
        total: 0,
        renting: 0,
        attention: 0,
        purchaseAmount: 0,
        periodIncome: 0
      };
      row.total += 1;
      row.purchaseAmount += Number(asset.purchaseAmount || 0);
      if (asset.status === 'RENTING') row.renting += 1;
      if (['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(asset.status)) row.attention += 1;
      map.set(key, row);
    });
    dashboard.periodEntries.forEach((entry) => {
      const row = map.get(`STORE:${entry.storeId}`);
      if (row) row.periodIncome += Number(entry.amount || 0);
    });
    return [...map.values()].map((row) => ({
      ...row,
      deploymentRate: row.total ? row.renting / row.total * 100 : 0,
      incomePerAsset: row.total ? row.periodIncome / row.total : 0
    })).sort((left, right) => right.periodIncome - left.periodIncome || right.deploymentRate - left.deploymentRate);
  }, [dashboard]);

  const lowEfficiencyAssets = useMemo(() => dashboard.activeAssets
    .filter((item) => item.status !== 'RENTING' || !item.currentStoreId)
    .sort((left, right) => assetAttentionScore(right) - assetAttentionScore(left) || left.assetCode.localeCompare(right.assetCode))
    .slice(0, 10), [dashboard.activeAssets]);

  const latestStatements = useMemo(() => [...statements]
    .sort((left, right) => right.statementMonth.localeCompare(left.statementMonth) || right.id - left.id)
    .slice(0, 6), [statements]);

  return (
    <Space direction="vertical" size={16} className="page-stack cockpit-page">
      <CockpitHeader
        eyebrow="Asset Investment Operations"
        title="资产方经营驾驶舱"
        description={`${account.displayName} · 资产投入、当前投放效率、收益回报与低效资产总览。`}
        period={period}
        onPeriodChange={setPeriod}
        customRange={customRange}
        onCustomRangeChange={setCustomRange}
        selectedMonth={selectedMonth}
        onSelectedMonthChange={setSelectedMonth}
        onRefresh={loadData}
        loading={loading}
        scope={<Tag color="purple">名下全部资产</Tag>}
      />

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <div className="cockpit-metric-grid">
        <CockpitMetric icon={<BankOutlined />} tone="blue" label="运营资产投入" value={compactMoney(dashboard.purchaseAmount)} detail={`${dashboard.activeAssets.length} 台有效资产`} />
        <CockpitMetric icon={<ShopOutlined />} tone="green" label="当前资产投放率" value={percent(dashboard.deploymentRate)} detail={`${dashboard.rentingAssets.length} / ${dashboard.activeAssets.length} 台在租`} />
        <CockpitMetric icon={<WalletOutlined />} tone="violet" label="期间确认收益" value={compactMoney(dashboard.periodIncome)} detail={`其中已结算 ${compactMoney(dashboard.periodSettledIncome)}`} change={dashboard.incomeChange} changeLabel="环比" />
        <CockpitMetric icon={<CheckCircleOutlined />} tone="orange" label="累计已结算回报率" value={percent(dashboard.cumulativeReturnRate, 2)} detail={`累计已结算 ${compactMoney(dashboard.settledIncome)}`} />
      </div>

      <div className="cockpit-layout cockpit-layout-main">
        <CockpitPanel title="资产收益趋势" subtitle="按收益发生时间统计确认收益与已结算收益" extra={<Tag>{window.label}</Tag>}>
          <CockpitTrend labels={trend.labels} primary={trend.income} secondary={trend.settled} primaryLabel="确认收益" secondaryLabel="已结算收益" primaryFormatter={compactMoney} secondaryFormatter={compactMoney} />
        </CockpitPanel>
        <CockpitPanel title="资产经营关注" subtitle="收益归集、资产状态与投放事项" extra={<Tag color={dashboard.frozenIncome ? 'red' : 'green'}>{dashboard.frozenIncome ? '存在冻结收益' : '收益正常'}</Tag>}>
          <CockpitAttentionList rows={[
            { key: 'payable', icon: <WalletOutlined />, tone: 'green', label: '待打款月结', detail: `${dashboard.payableStatementCount} 张已确认月结单`, value: compactMoney(dashboard.payableStatementAmount) },
            { key: 'pending', icon: <CheckCircleOutlined />, tone: 'violet', label: '待归集收益', detail: '尚未进入正式月结打款', value: compactMoney(dashboard.pendingIncome) },
            { key: 'frozen', icon: <ExclamationCircleOutlined />, tone: 'red', label: '冻结收益', detail: '需等待平台核对处理', value: compactMoney(dashboard.frozenIncome), tag: dashboard.frozenIncome ? '关注' : undefined },
            { key: 'repair', icon: <ToolOutlined />, tone: 'orange', label: '维修及异常资产', detail: '当前不可正常投放', value: `${dashboard.repairAssets.length} 台` },
            { key: 'unassigned', icon: <ShopOutlined />, tone: 'blue', label: '未分配门店资产', detail: '尚未进入门店经营', value: `${dashboard.unassignedAssets.length} 台` }
          ]} />
        </CockpitPanel>
      </div>

      <CockpitPanel title="门店投放经营表现" subtitle={`${window.label}收益、当前投放率与单台收益`} extra={<Tag color="green">{storePerformance.length} 个投放位置</Tag>}>
        <Table
          rowKey="key"
          size="small"
          loading={loading}
          dataSource={storePerformance}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无资产投放数据" /> }}
          columns={[
            { title: '商户 / 门店', render: (_, record) => <div className="cockpit-primary-cell"><strong>{record.storeName}</strong><span>{record.merchantName}</span></div> },
            { title: '资产', dataIndex: 'total', width: 72 },
            { title: '在租', dataIndex: 'renting', width: 72 },
            { title: '期间收益', dataIndex: 'periodIncome', width: 120, render: (value) => <strong className="amount-positive">{money(value)}</strong> },
            { title: '单台收益', dataIndex: 'incomePerAsset', width: 110, render: money },
            { title: '当前投放率', dataIndex: 'deploymentRate', width: 170, render: (value) => <div className="cockpit-table-progress"><Progress percent={Math.round(value)} size="small" showInfo={false} /><span>{percent(value, 0)}</span></div> },
            { title: '需关注', dataIndex: 'attention', width: 85, render: (value) => value ? <Tag color="orange">{value} 台</Tag> : '-' }
          ]}
        />
      </CockpitPanel>

      <div className="cockpit-layout cockpit-layout-equal">
        <CockpitPanel title="资产状态结构" subtitle={`当前有效资产 ${dashboard.activeAssets.length} 台`} extra={<Tag color="blue">快照指标</Tag>}>
          <CockpitProgressList rows={[
            { key: 'renting', label: '租赁中', value: dashboard.rentingAssets.length, total: dashboard.activeAssets.length, detail: `${dashboard.rentingAssets.length} 台`, color: '#0f9f7a' },
            { key: 'idle', label: '空闲', value: dashboard.idleAssets.length, total: dashboard.activeAssets.length, detail: `${dashboard.idleAssets.length} 台`, color: '#2563eb' },
            { key: 'repair', label: '维修 / 异常', value: dashboard.repairAssets.length, total: dashboard.activeAssets.length, detail: `${dashboard.repairAssets.length} 台`, color: '#d97706' },
            { key: 'unassigned', label: '未分配门店', value: dashboard.unassignedAssets.length, total: dashboard.activeAssets.length, detail: `${dashboard.unassignedAssets.length} 台`, color: '#7c3aed' }
          ]} />
          <div className="cockpit-panel-note">当前投放率为实时资产快照，不等同于按资产天数计算的期间利用率。</div>
        </CockpitPanel>
        <CockpitPanel title="最近月结" subtitle="正式月结金额和打款状态" extra={<Tag>{statements.length} 张</Tag>}>
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={latestStatements}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无月结单" /> }}
            columns={[
              { title: '月份', dataIndex: 'statementMonth', width: 88 },
              { title: '应结算', dataIndex: 'payableAmount', width: 120, render: (value) => <strong className="amount-positive">{money(value)}</strong> },
              { title: '状态', dataIndex: 'status', width: 96, render: statementStatusTag },
              { title: '打款时间', dataIndex: 'paidAt', render: dateText }
            ]}
          />
        </CockpitPanel>
      </div>

      <CockpitPanel title="低效与待盘活资产" subtitle="按异常、维修、未分配、空闲优先级展示" extra={<Tag color="orange">{lowEfficiencyAssets.length} 台需关注</Tag>}>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={lowEfficiencyAssets}
          pagination={false}
          locale={{ emptyText: <Empty description="当前没有待盘活资产" /> }}
          columns={[
            { title: '资产编码', dataIndex: 'assetCode' },
            { title: '序列号', dataIndex: 'serialNo' },
            { title: '资产类型', dataIndex: 'assetTypeName' },
            { title: '当前门店', dataIndex: 'storeName', render: (value) => value || <Tag color="purple">未分配</Tag> },
            { title: '状态', dataIndex: 'status', render: assetStatusTag },
            { title: '账面投入', dataIndex: 'purchaseAmount', render: money },
            { title: '建议动作', render: (_, record) => assetSuggestion(record) }
          ]}
        />
      </CockpitPanel>
    </Space>
  );
}

function assetAttentionScore(asset: Asset) {
  if (asset.status === 'EXCEPTION') return 5;
  if (['PENDING_REPAIR', 'REPAIRING'].includes(asset.status)) return 4;
  if (!asset.currentStoreId) return 3;
  if (asset.status === 'IDLE') return 2;
  return 0;
}

function assetSuggestion(asset: Asset) {
  if (asset.status === 'EXCEPTION') return '排查异常并明确处置';
  if (asset.status === 'PENDING_REPAIR') return '尽快安排检修';
  if (asset.status === 'REPAIRING') return '跟进维修完成时间';
  if (!asset.currentStoreId) return '分配至合适门店';
  return '评估调拨或促销投放';
}

function assetStatusTag(value: Asset['status']) {
  const item = ({
    IDLE: { text: '空闲', color: 'blue' },
    RENTING: { text: '租赁中', color: 'green' },
    PENDING_REPAIR: { text: '待检修', color: 'gold' },
    REPAIRING: { text: '维修中', color: 'orange' },
    SCRAPPED: { text: '已报废', color: 'default' },
    SOLD: { text: '已售出', color: 'default' },
    EXCEPTION: { text: '异常', color: 'red' }
  } as Record<Asset['status'], { text: string; color: string }>)[value];
  return <Tag color={item.color}>{item.text}</Tag>;
}

function statementStatusTag(value: SettlementStatement['status']) {
  const item = ({
    DRAFT: { text: '草稿', color: 'default' },
    RECONCILING: { text: '对账中', color: 'blue' },
    CONFIRMED: { text: '已确认', color: 'purple' },
    PAYABLE: { text: '待打款', color: 'gold' },
    PAID: { text: '已打款', color: 'green' },
    CLOSED: { text: '已关闭', color: 'default' }
  } as Record<SettlementStatement['status'], { text: string; color: string }>)[value];
  return <Tag color={item.color}>{item.text}</Tag>;
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
