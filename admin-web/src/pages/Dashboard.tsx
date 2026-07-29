import {
  AlertOutlined,
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  RiseOutlined,
  WalletOutlined
} from '@ant-design/icons';
import { Alert, Button, Empty, Progress, Select, Space, Table, Tag, Typography } from 'antd';
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
import type {
  Asset,
  DeductRecord,
  ExternalRentalOrder,
  OverdueCase,
  PaymentOrder,
  RentalBill,
  RentalOrder,
  SettlementIncomeEntry,
  Store
} from '../types/api';
import {
  buildTimeBuckets,
  dateTimeText,
  getDateWindow,
  isInWindow,
  percent,
  percentageChange,
  sumNumbers,
  valueByBuckets,
  type CockpitPeriod
} from '../utils/dashboard';

type DashboardData = {
  orders: RentalOrder[];
  externalOrders: ExternalRentalOrder[];
  bills: RentalBill[];
  assets: Asset[];
  overdues: OverdueCase[];
  payments: PaymentOrder[];
  failedDeductions: DeductRecord[];
  stores: Store[];
  incomeEntries: SettlementIncomeEntry[];
};

type StoreRanking = {
  storeId: number;
  storeName: string;
  collected: number;
  activeAssets: number;
  rentingAssets: number;
  overdueAmount: number;
  deploymentRate: number;
};

type StoreBusinessRow = {
  key: string;
  sourceType: 'FORMAL' | 'EXTERNAL';
  sourceLabel: string;
  businessNo: string;
  customerName: string;
  collectedAmount: number;
  status: string;
  asset: string;
  occurredAt: string;
};

const initialData: DashboardData = {
  orders: [],
  externalOrders: [],
  bills: [],
  assets: [],
  overdues: [],
  payments: [],
  failedDeductions: [],
  stores: [],
  incomeEntries: []
};

export function Dashboard() {
  const [data, setData] = useState<DashboardData>(initialData);
  const [period, setPeriod] = useState<CockpitPeriod>('30D');
  const [selectedStoreId, setSelectedStoreId] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [orders, externalOrders, bills, assets, overdues, payments, failedDeductions, stores, incomeEntries] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/admin/orders'),
        http.get<unknown, ExternalRentalOrder[]>('/api/admin/external-orders'),
        http.get<unknown, RentalBill[]>('/api/admin/bills'),
        http.get<unknown, Asset[]>('/api/admin/assets'),
        http.get<unknown, OverdueCase[]>('/api/admin/overdues?overdueStatus=OPEN'),
        http.get<unknown, PaymentOrder[]>('/api/admin/payments'),
        http.get<unknown, DeductRecord[]>('/api/admin/deductions/records?status=FAILED'),
        optionalGet<Store[]>('/api/admin/stores', []),
        optionalGet<SettlementIncomeEntry[]>('/api/admin/settlement/income/entries', [])
      ]);
      setData({ orders, externalOrders, bills, assets, overdues, payments, failedDeductions, stores, incomeEntries });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '经营驾驶舱数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const window = useMemo(() => getDateWindow(period), [period]);
  const selectedStore = useMemo(
    () => data.stores.find((item) => item.id === selectedStoreId),
    [data.stores, selectedStoreId]
  );
  const scopedData = useMemo<DashboardData>(() => {
    if (!selectedStoreId) {
      return data;
    }
    const orders = data.orders.filter((item) => item.storeId === selectedStoreId);
    const orderIds = new Set(orders.map((item) => item.id));
    return {
      orders,
      externalOrders: data.externalOrders.filter((item) => item.storeId === selectedStoreId),
      bills: data.bills.filter((item) => item.storeId === selectedStoreId),
      assets: data.assets.filter((item) => item.currentStoreId === selectedStoreId),
      overdues: data.overdues.filter((item) => item.storeId === selectedStoreId),
      payments: data.payments.filter((item) => item.storeId === selectedStoreId),
      failedDeductions: data.failedDeductions.filter((item) => orderIds.has(item.orderId)),
      stores: data.stores.filter((item) => item.id === selectedStoreId),
      incomeEntries: data.incomeEntries.filter((item) => item.storeId === selectedStoreId)
    };
  }, [data, selectedStoreId]);
  const dashboard = useMemo(() => {
    const periodPayments = scopedData.payments.filter((item) => item.payStatus === 'PAID' && isInWindow(item.paidAt, window.start, window.end));
    const previousPayments = scopedData.payments.filter((item) => item.payStatus === 'PAID' && isInWindow(item.paidAt, window.previousStart, window.previousEnd));
    const periodExternal = scopedData.externalOrders.filter((item) => isInWindow(item.createdAt || item.rentStartedAt, window.start, window.end));
    const previousExternal = scopedData.externalOrders.filter((item) => isInWindow(item.createdAt || item.rentStartedAt, window.previousStart, window.previousEnd));
    const periodCollected = netPayments(periodPayments) + sumNumbers(periodExternal.map((item) => item.verificationAmount));
    const previousCollected = netPayments(previousPayments) + sumNumbers(previousExternal.map((item) => item.verificationAmount));
    const platformIncome = sumNumbers(scopedData.incomeEntries
      .filter((item) => item.sourceType !== 'ORDER' && item.entryStatus !== 'FROZEN' && item.beneficiaryType === 'PLATFORM' && isInWindow(item.occurredAt, window.start, window.end))
      .map((item) => item.amount));
    const previousPlatformIncome = sumNumbers(scopedData.incomeEntries
      .filter((item) => item.sourceType !== 'ORDER' && item.entryStatus !== 'FROZEN' && item.beneficiaryType === 'PLATFORM' && isInWindow(item.occurredAt, window.previousStart, window.previousEnd))
      .map((item) => item.amount));
    const dueBills = scopedData.bills.filter((item) => isInWindow(item.dueAt, window.start, window.end) && item.billStatus !== 'CANCELLED');
    const previousDueBills = scopedData.bills.filter((item) => isInWindow(item.dueAt, window.previousStart, window.previousEnd) && item.billStatus !== 'CANCELLED');
    const dueAmount = sumNumbers(dueBills.map((item) => item.payableAmount));
    const previousDueAmount = sumNumbers(previousDueBills.map((item) => item.payableAmount));
    const collectionRate = dueAmount ? Math.min(100, sumNumbers(dueBills.map((item) => item.paidAmount)) / dueAmount * 100) : 0;
    const previousCollectionRate = previousDueAmount ? Math.min(100, sumNumbers(previousDueBills.map((item) => item.paidAmount)) / previousDueAmount * 100) : 0;
    const activeAssets = scopedData.assets.filter((item) => !['SCRAPPED', 'SOLD'].includes(item.status));
    const rentingAssets = activeAssets.filter((item) => item.status === 'RENTING');
    const deploymentRate = activeAssets.length ? rentingAssets.length / activeAssets.length * 100 : 0;
    const periodOrders = scopedData.orders.filter((item) => isInWindow(item.orderedAt, window.start, window.end));
    const totalPeriodOrders = periodOrders.length + periodExternal.length;
    const verifiedOrders = periodOrders.filter((item) => Number(item.verificationAmount || 0) > 0 || Number(item.paidAmount || 0) > 0).length + periodExternal.length;
    const startedOrders = periodOrders.filter((item) => Boolean(item.leaseStartedAt)).length + periodExternal.filter((item) => Boolean(item.rentStartedAt)).length;
    const fulfilledOrders = periodOrders.filter((item) => ['RENTING', 'PENDING_RETURN', 'OVERDUE', 'PENDING_SUPPLEMENT', 'COMPLETED'].includes(item.orderStatus)).length
      + periodExternal.filter((item) => ['ACTIVE', 'COMPLETED'].includes(item.orderStatus)).length;
    return {
      periodCollected,
      collectedChange: percentageChange(periodCollected, previousCollected),
      platformIncome,
      platformIncomeChange: percentageChange(platformIncome, previousPlatformIncome),
      dueAmount,
      collectionRate,
      collectionRateChange: collectionRate - previousCollectionRate,
      activeAssets,
      rentingAssets,
      deploymentRate,
      totalPeriodOrders,
      verifiedOrders,
      startedOrders,
      fulfilledOrders,
      overdueAmount: sumNumbers(scopedData.overdues.map((item) => item.unpaidAmount)),
      repairAssets: activeAssets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length,
      pendingPickup: scopedData.orders.filter((item) => item.orderStatus === 'PENDING_PICKUP').length,
      pendingReturn: scopedData.orders.filter((item) => item.orderStatus === 'PENDING_RETURN').length
    };
  }, [scopedData, window]);

  const trend = useMemo(() => {
    const buckets = buildTimeBuckets(window);
    const paymentValues = valueByBuckets(buckets, scopedData.payments.filter((item) => item.payStatus === 'PAID'), (item) => item.paidAt, (item) => Math.max(0, Number(item.paidAmount || 0) - Number(item.refundAmount || 0)));
    const externalValues = valueByBuckets(buckets, scopedData.externalOrders, (item) => item.createdAt || item.rentStartedAt, (item) => Number(item.verificationAmount || 0));
    return {
      labels: buckets.map((item) => item.label),
      receivable: valueByBuckets(buckets, scopedData.bills.filter((item) => item.billStatus !== 'CANCELLED'), (item) => item.dueAt, (item) => Number(item.payableAmount || 0)),
      collected: paymentValues.map((value, index) => value + externalValues[index])
    };
  }, [scopedData, window]);

  const storeRankings = useMemo<StoreRanking[]>(() => {
    const storeMap = new Map<number, StoreRanking>();
    const ensureStore = (storeId: number, storeName?: string | null) => {
      const current = storeMap.get(storeId) || {
        storeId,
        storeName: storeName || data.stores.find((item) => item.id === storeId)?.storeName || `门店 ${storeId}`,
        collected: 0,
        activeAssets: 0,
        rentingAssets: 0,
        overdueAmount: 0,
        deploymentRate: 0
      };
      storeMap.set(storeId, current);
      return current;
    };
    data.stores.forEach((store) => ensureStore(store.id, store.storeName));
    data.payments
      .filter((item) => item.payStatus === 'PAID' && isInWindow(item.paidAt, window.start, window.end))
      .forEach((item) => { ensureStore(item.storeId).collected += Math.max(0, Number(item.paidAmount || 0) - Number(item.refundAmount || 0)); });
    data.externalOrders
      .filter((item) => isInWindow(item.createdAt || item.rentStartedAt, window.start, window.end))
      .forEach((item) => { ensureStore(item.storeId, item.storeName).collected += Number(item.verificationAmount || 0); });
    data.assets.filter((item) => !['SCRAPPED', 'SOLD'].includes(item.status)).forEach((item) => {
      if (!item.currentStoreId) return;
      const row = ensureStore(item.currentStoreId, item.storeName);
      row.activeAssets += 1;
      if (item.status === 'RENTING') row.rentingAssets += 1;
    });
    data.overdues.forEach((item) => { ensureStore(item.storeId).overdueAmount += Number(item.unpaidAmount || 0); });
    const rankings = [...storeMap.values()]
      .map((item) => ({ ...item, deploymentRate: item.activeAssets ? item.rentingAssets / item.activeAssets * 100 : 0 }))
      .filter((item) => item.storeId === selectedStoreId || item.collected || item.activeAssets || item.overdueAmount)
      .sort((left, right) => right.collected - left.collected || right.activeAssets - left.activeAssets);
    return selectedStoreId ? rankings.filter((item) => item.storeId === selectedStoreId) : rankings.slice(0, 8);
  }, [data, selectedStoreId, window]);

  const riskRows = useMemo(() => scopedData.overdues
    .map((item) => ({
      key: `overdue-${item.id}`,
      type: '逾期催缴',
      reference: item.caseNo,
      store: data.stores.find((store) => store.id === item.storeId)?.storeName || `门店 ${item.storeId}`,
      value: fullMoney(item.unpaidAmount),
      status: collectionText(item.collectionStatus),
      level: 'red'
    }))
    .concat(scopedData.failedDeductions.map((item) => ({
      key: `deduct-${item.id}`,
      type: '扣款失败',
      reference: item.deductNo,
      store: data.stores.find((store) => store.id === data.orders.find((order) => order.id === item.orderId)?.storeId)?.storeName || `订单 ${item.orderId}`,
      value: fullMoney(item.deductAmount),
      status: `已重试 ${item.retryCount} 次`,
      level: 'orange'
    })))
    .slice(0, 8), [data.orders, data.stores, scopedData.failedDeductions, scopedData.overdues]);

  const storeBusinessRows = useMemo<StoreBusinessRow[]>(() => {
    if (!selectedStoreId) {
      return [];
    }
    return [
      ...scopedData.orders
        .filter((item) => isInWindow(item.orderedAt, window.start, window.end))
        .map((item) => ({
          key: `formal-${item.id}`,
          sourceType: 'FORMAL' as const,
          sourceLabel: '正式订单',
          businessNo: item.orderNo,
          customerName: item.customerName || `用户 ${item.userAccountId || '-'}`,
          collectedAmount: Number(item.paidAmount || 0),
          status: orderStatusText(item.orderStatus),
          asset: item.frameSerialNo || item.frameAssetCode || '待分配',
          occurredAt: item.orderedAt
        })),
      ...scopedData.externalOrders
        .filter((item) => isInWindow(item.createdAt || item.rentStartedAt, window.start, window.end))
        .map((item) => ({
          key: `external-${item.id}`,
          sourceType: 'EXTERNAL' as const,
          sourceLabel: sourcePlatformText(item.sourcePlatform),
          businessNo: item.recordNo,
          customerName: item.customerName,
          collectedAmount: Number(item.verificationAmount || 0),
          status: externalOrderStatusText(item.orderStatus),
          asset: item.frameAssetSerialNo || '未绑定资产',
          occurredAt: item.createdAt || item.rentStartedAt
        }))
    ].sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime());
  }, [scopedData.externalOrders, scopedData.orders, selectedStoreId, window]);

  return (
    <Space direction="vertical" size={16} className="page-stack cockpit-page">
      <CockpitHeader
        eyebrow="Business Operations"
        title="总部经营驾驶舱"
        description={`${selectedStore?.storeName || '全平台'} · ${window.label}经营、回款、履约与资产风险总览；经营金额均来自现有业务流水。`}
        period={period}
        onPeriodChange={setPeriod}
        onRefresh={loadData}
        loading={loading}
        scope={(
          <Space size={8} wrap>
            <Tag color="green">{selectedStore ? selectedStore.storeName : `全平台 · ${data.stores.length || '-'} 家门店`}</Tag>
            <Select
              showSearch
              optionFilterProp="label"
              value={selectedStoreId || 0}
              onChange={(value) => setSelectedStoreId(value || undefined)}
              options={[
                { label: '全平台', value: 0 },
                ...data.stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))
              ]}
              style={{ minWidth: 230 }}
            />
          </Space>
        )}
      />

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <div className="cockpit-metric-grid">
        <CockpitMetric icon={<WalletOutlined />} tone="green" label="期间实收" value={fullMoney(dashboard.periodCollected)} detail={`含补录核销 · ${dashboard.totalPeriodOrders} 笔订单`} change={dashboard.collectedChange} changeLabel="环比" />
        <CockpitMetric icon={<RiseOutlined />} tone="violet" label="平台期间收入" value={fullMoney(dashboard.platformIncome)} detail="平台服务费及运营费流水" change={dashboard.platformIncomeChange} changeLabel="环比" />
        <CockpitMetric icon={<CheckCircleOutlined />} tone="blue" label="到期账单回款率" value={percent(dashboard.collectionRate)} detail={`期间应收 ${fullMoney(dashboard.dueAmount)}`} change={dashboard.collectionRateChange} changeLabel="百分点" />
        <CockpitMetric icon={<CarOutlined />} tone="orange" label="当前资产投放率" value={percent(dashboard.deploymentRate)} detail={`${dashboard.rentingAssets.length} / ${dashboard.activeAssets.length} 台在租`} />
      </div>

      <div className="cockpit-layout cockpit-layout-main">
        <CockpitPanel title="回款经营趋势" subtitle="按到期日统计应收，按到账/补录日统计实收" extra={<Tag>{window.label}</Tag>}>
          <CockpitTrend labels={trend.labels} primary={trend.collected} secondary={trend.receivable} primaryLabel="实收" secondaryLabel="应收" primaryFormatter={fullMoney} secondaryFormatter={fullMoney} />
        </CockpitPanel>
        <CockpitPanel title="今日经营关注" subtitle="优先处理影响现金流和履约的事项" extra={<Tag color={riskRows.length ? 'red' : 'green'}>{riskRows.length ? `${riskRows.length} 项风险` : '经营正常'}</Tag>}>
          <CockpitAttentionList rows={[
            { key: 'overdue', icon: <ExclamationCircleOutlined />, tone: 'red', label: '逾期未收', detail: `${scopedData.overdues.length} 个未关闭案件`, value: fullMoney(dashboard.overdueAmount), tag: '高优先级' },
            { key: 'deduct', icon: <AlertOutlined />, tone: 'orange', label: '扣款失败', detail: '需检查协议或安排人工催收', value: `${scopedData.failedDeductions.length} 笔` },
            { key: 'pickup', icon: <ClockCircleOutlined />, tone: 'blue', label: '待取车履约', detail: '检查车辆与电池准备情况', value: `${dashboard.pendingPickup} 单` },
            { key: 'return', icon: <CarOutlined />, tone: 'violet', label: '待归还验收', detail: '及时完成归还和资产状态确认', value: `${dashboard.pendingReturn} 单` },
            { key: 'repair', icon: <AlertOutlined />, tone: 'orange', label: '维修及异常资产', detail: '当前不可正常投放的资产', value: `${dashboard.repairAssets} 台` }
          ]} />
        </CockpitPanel>
      </div>

      <div className="cockpit-layout cockpit-layout-equal">
        <CockpitPanel
          title={selectedStore ? '当前门店经营摘要' : '门店经营排名'}
          subtitle={`${window.label}实收、当前投放与逾期风险`}
          extra={selectedStore
            ? <Button size="small" onClick={() => setSelectedStoreId(undefined)}>返回全平台</Button>
            : <Tag color="blue">TOP {storeRankings.length}</Tag>}
        >
          <Table
            rowKey="storeId"
            size="small"
            loading={loading}
            dataSource={storeRankings}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无门店经营数据" /> }}
            columns={[
              { title: '排名', width: 58, render: (_, __, index) => <span className={`cockpit-rank rank-${index + 1}`}>{index + 1}</span> },
              { title: '门店', dataIndex: 'storeName', ellipsis: true },
              { title: '期间实收', dataIndex: 'collected', width: 130, render: (value) => <strong className="amount-positive">{fullMoney(value)}</strong> },
              { title: '当前投放', dataIndex: 'deploymentRate', width: 150, render: (value) => <div className="cockpit-table-progress"><Progress percent={Math.round(value)} size="small" showInfo={false} /><span>{percent(value, 0)}</span></div> },
              { title: '逾期未收', dataIndex: 'overdueAmount', width: 120, render: (value) => value ? <Typography.Text type="danger">{fullMoney(value)}</Typography.Text> : '-' },
              {
                title: '操作',
                width: 82,
                render: (_, record) => selectedStoreId
                  ? <Tag color="green">当前门店</Tag>
                  : <Button size="small" type="link" onClick={() => setSelectedStoreId(record.storeId)}>查看</Button>
              }
            ]}
          />
        </CockpitPanel>
        <CockpitPanel title="订单履约漏斗" subtitle={`${window.label}正式订单与外部补录订单`} extra={<Tag color="purple">{dashboard.totalPeriodOrders} 单</Tag>}>
          <CockpitProgressList rows={[
            { key: 'created', label: '订单创建', value: dashboard.totalPeriodOrders, total: dashboard.totalPeriodOrders, detail: `${dashboard.totalPeriodOrders} 单`, color: '#2563eb' },
            { key: 'verified', label: '支付 / 核销', value: dashboard.verifiedOrders, total: dashboard.totalPeriodOrders, detail: `${dashboard.verifiedOrders} 单`, color: '#7c3aed' },
            { key: 'started', label: '开始租赁', value: dashboard.startedOrders, total: dashboard.totalPeriodOrders, detail: `${dashboard.startedOrders} 单`, color: '#0f9f7a' },
            { key: 'fulfilled', label: '履约中 / 完成', value: dashboard.fulfilledOrders, total: dashboard.totalPeriodOrders, detail: `${dashboard.fulfilledOrders} 单`, color: '#059669' }
          ]} />
          <div className="cockpit-panel-note">漏斗按当前订单状态和关键时间点聚合，可用于识别核销到交付的转化损耗。</div>
        </CockpitPanel>
      </div>

      {selectedStore ? (
        <CockpitPanel
          title={`${selectedStore.storeName}业务明细`}
          subtitle={`${window.label}正式订单与外部补录订单，按业务发生时间倒序展示`}
          extra={<Tag color="green">共 {storeBusinessRows.length} 笔</Tag>}
        >
          <Table
            rowKey="key"
            size="small"
            loading={loading}
            dataSource={storeBusinessRows}
            pagination={{ pageSize: 8, showSizeChanger: false, showTotal: (total) => `共 ${total} 笔业务` }}
            scroll={{ x: 1040 }}
            locale={{ emptyText: <Empty description="当前周期暂无门店业务" /> }}
            columns={[
              { title: '业务来源', dataIndex: 'sourceLabel', width: 110, render: (value, record) => <Tag color={record.sourceType === 'FORMAL' ? 'blue' : 'purple'}>{value}</Tag> },
              { title: '业务编号', dataIndex: 'businessNo', width: 180 },
              { title: '客户', dataIndex: 'customerName', width: 120 },
              { title: '实收/核销', dataIndex: 'collectedAmount', width: 140, render: (value) => <Typography.Text strong className="amount-positive">{fullMoney(value)}</Typography.Text> },
              { title: '当前状态', dataIndex: 'status', width: 120, render: (value) => <Tag>{value}</Tag> },
              { title: '车架/资产', dataIndex: 'asset', width: 180, ellipsis: true },
              { title: '业务时间', dataIndex: 'occurredAt', width: 170, render: dateTimeText }
            ]}
          />
        </CockpitPanel>
      ) : null}

      <CockpitPanel title="风险处置队列" subtitle="逾期催缴与自动扣款失败统一排序展示" extra={<Tag color="red">未关闭 {scopedData.overdues.length + scopedData.failedDeductions.length}</Tag>}>
        <Table
          rowKey="key"
          size="small"
          loading={loading}
          dataSource={riskRows}
          pagination={false}
          locale={{ emptyText: <Empty description="当前没有待处置风险" /> }}
          columns={[
            { title: '风险类型', dataIndex: 'type', width: 110, render: (value, record) => <Tag color={record.level}>{value}</Tag> },
            { title: '业务编号', dataIndex: 'reference' },
            { title: '归属', dataIndex: 'store' },
            { title: '风险金额', dataIndex: 'value', width: 130, render: (value) => <Typography.Text type="danger" strong>{value}</Typography.Text> },
            { title: '当前进度', dataIndex: 'status' }
          ]}
        />
      </CockpitPanel>
    </Space>
  );
}

function netPayments(items: PaymentOrder[]) {
  return sumNumbers(items.map((item) => Math.max(0, Number(item.paidAmount || 0) - Number(item.refundAmount || 0))));
}

function fullMoney(value?: number | string | null) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`;
}

async function optionalGet<T>(url: string, fallback: T) {
  try {
    return await http.get<unknown, T>(url);
  } catch {
    return fallback;
  }
}

function collectionText(value: OverdueCase['collectionStatus']) {
  return ({
    PENDING: '待催缴',
    CONTACTED: '已联系',
    PROMISED: '承诺付款',
    RESOLVED: '已解决',
    BAD_DEBT: '坏账'
  } as Record<OverdueCase['collectionStatus'], string>)[value];
}

function orderStatusText(value: RentalOrder['orderStatus']) {
  return ({
    PENDING_PAYMENT: '待支付',
    PENDING_REAL_NAME: '待实名',
    PENDING_AGREEMENT: '待签约',
    PENDING_DEPOSIT_AUTH: '待押金授权',
    PENDING_VERIFY: '待核销',
    PENDING_PICKUP: '待取车',
    RENTING: '租赁中',
    PENDING_RETURN: '待归还',
    OVERDUE: '已逾期',
    PENDING_SUPPLEMENT: '待补缴',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    EXCEPTION: '异常'
  } as Record<RentalOrder['orderStatus'], string>)[value];
}

function externalOrderStatusText(value: ExternalRentalOrder['orderStatus']) {
  return ({
    ACTIVE: '履约中',
    COMPLETED: '已完成',
    TERMINATED: '已终止'
  } as Record<ExternalRentalOrder['orderStatus'], string>)[value];
}

function sourcePlatformText(value: ExternalRentalOrder['sourcePlatform']) {
  return ({
    DOUYIN: '抖音补录',
    MEITUAN: '美团补录',
    XIANYU: '闲鱼补录',
    OFFLINE: '线下补录',
    OTHER: '其他补录'
  } as Record<ExternalRentalOrder['sourcePlatform'], string>)[value];
}
