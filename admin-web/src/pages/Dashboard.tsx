import { Alert, Button, Col, Progress, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import { CarOutlined, ClockCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, SafetyCertificateOutlined, WalletOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { http } from '../services/request';
import type { Asset, DeductRecord, ExternalRentalOrder, OverdueCase, PaymentOrder, RentalBill, RentalOrder } from '../types/api';

type DashboardData = {
  orders: RentalOrder[];
  externalOrders: ExternalRentalOrder[];
  bills: RentalBill[];
  assets: Asset[];
  overdues: OverdueCase[];
  payments: PaymentOrder[];
  failedDeductions: DeductRecord[];
};

const initialData: DashboardData = {
  orders: [],
  externalOrders: [],
  bills: [],
  assets: [],
  overdues: [],
  payments: [],
  failedDeductions: []
};

export function Dashboard() {
  const [data, setData] = useState<DashboardData>(initialData);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadData = async () => {
    setLoading(true);
    setError('');
    try {
      const [orders, externalOrders, bills, assets, overdues, payments, failedDeductions] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/admin/orders'),
        http.get<unknown, ExternalRentalOrder[]>('/api/admin/external-orders'),
        http.get<unknown, RentalBill[]>('/api/admin/bills'),
        http.get<unknown, Asset[]>('/api/admin/assets'),
        http.get<unknown, OverdueCase[]>('/api/admin/overdues?overdueStatus=OPEN'),
        http.get<unknown, PaymentOrder[]>('/api/admin/payments'),
        http.get<unknown, DeductRecord[]>('/api/admin/deductions/records?status=FAILED')
      ]);
      setData({ orders, externalOrders, bills, assets, overdues, payments, failedDeductions });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '工作台数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const metrics = useMemo(() => {
    const externalActiveOrders = data.externalOrders.filter((item) => item.orderStatus === 'ACTIVE').length;
    const rentingOrders = data.orders.filter((item) => item.orderStatus === 'RENTING').length + externalActiveOrders;
    const pendingPickup = data.orders.filter((item) => item.orderStatus === 'PENDING_PICKUP').length;
    const pendingSupplement = data.orders.filter((item) => item.orderStatus === 'PENDING_SUPPLEMENT' || item.orderStatus === 'OVERDUE').length;
    const pendingBillAmount = sum(data.bills.filter((item) => !['PAID', 'CANCELLED'].includes(item.billStatus)), 'payableAmount');
    const overdueAmount = sum(data.overdues, 'unpaidAmount');
    const paidAmount = sum(data.payments.filter((item) => item.payStatus === 'PAID'), 'paidAmount')
      + sum(data.externalOrders, 'verificationAmount');
    const idleAssets = data.assets.filter((item) => item.status === 'IDLE').length;
    const rentingAssets = data.assets.filter((item) => item.status === 'RENTING').length;
    const repairAssets = data.assets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length;
    return {
      rentingOrders,
      externalActiveOrders,
      externalOrderCount: data.externalOrders.length,
      pendingPickup,
      pendingSupplement,
      pendingBillAmount,
      overdueAmount,
      paidAmount,
      idleAssets,
      rentingAssets,
      repairAssets
    };
  }, [data]);

  const pickupOrders = useMemo(() => data.orders.filter((item) => item.orderStatus === 'PENDING_PICKUP').slice(0, 8), [data.orders]);
  const riskBills = useMemo(() => data.bills.filter((item) => ['OVERDUE', 'FAILED', 'PENDING_PAYMENT'].includes(item.billStatus)).slice(0, 8), [data.bills]);
  const assetTotal = metrics.idleAssets + metrics.rentingAssets + metrics.repairAssets;
  const assetUseRate = assetTotal ? Math.round((metrics.rentingAssets / assetTotal) * 100) : 0;

  return (
    <Space direction="vertical" size={18} className="page-stack">
      <section className="dashboard-hero">
        <div>
          <Typography.Text className="page-eyebrow">Operations Overview</Typography.Text>
          <Typography.Title level={3}>经营工作台</Typography.Title>
          <Typography.Text type="secondary">总部视角汇总订单、账单、资产、逾期和扣款风险。</Typography.Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新数据</Button>
      </section>

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <Row gutter={[14, 14]}>
        <Col xs={24} md={8} xl={6}><Metric icon={<CarOutlined />} title="租赁中订单（含补录）" value={metrics.rentingOrders} tone="green" /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<ClockCircleOutlined />} title="待取车订单" value={metrics.pendingPickup} tone="blue" /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<ExclamationCircleOutlined />} title="逾期/待补缴" value={metrics.pendingSupplement} tone="orange" danger /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<WalletOutlined />} title="待收账单金额" value={formatMoney(metrics.pendingBillAmount)} tone="violet" /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<ExclamationCircleOutlined />} title="逾期未收金额" value={formatMoney(metrics.overdueAmount)} tone="red" danger /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<WalletOutlined />} title="累计已收（含补录）" value={formatMoney(metrics.paidAmount)} tone="green" /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<CarOutlined />} title="外部补录订单" value={metrics.externalOrderCount} suffix={`进行中 ${metrics.externalActiveOrders}`} tone="violet" /></Col>
        <Col xs={24} md={8} xl={6}><Metric icon={<SafetyCertificateOutlined />} title="空闲资产" value={metrics.idleAssets} tone="blue" /></Col>
        <Col xs={24} md={8} xl={6}>
          <section className="metric-tile metric-tile-progress">
            <div className="metric-head">
              <span className="metric-icon green"><CarOutlined /></span>
              <Typography.Text>资产使用率</Typography.Text>
            </div>
            <div className="metric-progress-row">
              <Progress type="circle" percent={assetUseRate} size={62} strokeColor="#0f9f7a" />
              <div>
                <Typography.Title level={4}>{metrics.rentingAssets} / {assetTotal}</Typography.Title>
                <Typography.Text type="secondary">异常/维修 {metrics.repairAssets}</Typography.Text>
              </div>
            </div>
          </section>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <section className="dashboard-section">
            <div className="section-head">
              <Typography.Title level={4}>最近补录订单</Typography.Title>
              <Tag color="purple">已计入经营统计</Tag>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={data.externalOrders.slice(0, 8)}
              columns={[
                { title: '补录单号', dataIndex: 'recordNo' },
                { title: '来源', dataIndex: 'sourcePlatform', render: externalSourceText },
                { title: '门店', dataIndex: 'storeName', render: (value: string | null | undefined, record) => value || `门店 ${record.storeId}` },
                { title: '客户', dataIndex: 'customerName' },
                { title: '状态', dataIndex: 'orderStatus', render: externalOrderStatusTag },
                { title: '实际核销金额', dataIndex: 'verificationAmount', render: formatMoney },
                { title: '起租时间', dataIndex: 'rentStartedAt', render: formatDate },
                { title: '预计归还', dataIndex: 'expectedReturnAt', render: formatDate }
              ]}
            />
          </section>
        </Col>
        <Col xs={24} xl={12}>
          <section className="dashboard-section">
            <div className="section-head">
              <Typography.Title level={4}>待取车订单</Typography.Title>
              <Tag color="blue">履约队列</Tag>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={pickupOrders}
              columns={[
                { title: '订单号', dataIndex: 'orderNo' },
                { title: '门店', dataIndex: 'storeId', render: (value: number) => `门店 ${value}` },
                { title: '用户', dataIndex: 'userAccountId', render: (value?: number) => value || '-' },
                { title: '应付', dataIndex: 'payableAmount', render: formatMoney },
                { title: '创建时间', dataIndex: 'createdAt', render: formatDate }
              ]}
            />
          </section>
        </Col>
        <Col xs={24} xl={12}>
          <section className="dashboard-section">
            <div className="section-head">
              <Typography.Title level={4}>待收账单</Typography.Title>
              <Tag color="gold">收款队列</Tag>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={riskBills}
              columns={[
                { title: '账单号', dataIndex: 'billNo' },
                { title: '订单', dataIndex: 'orderId' },
                { title: '状态', dataIndex: 'billStatus', render: billStatusTag },
                { title: '金额', dataIndex: 'payableAmount', render: formatMoney },
                { title: '到期时间', dataIndex: 'dueAt', render: formatDate }
              ]}
            />
          </section>
        </Col>
        <Col xs={24} xl={12}>
          <section className="dashboard-section">
            <div className="section-head">
              <Typography.Title level={4}>逾期催缴</Typography.Title>
              <Tag color="red">风险队列</Tag>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={data.overdues.slice(0, 8)}
              columns={[
                { title: '案件号', dataIndex: 'caseNo' },
                { title: '订单', dataIndex: 'orderId' },
                { title: '未收', dataIndex: 'unpaidAmount', render: formatMoney },
                { title: '失败次数', dataIndex: 'failCount' },
                { title: '催缴', dataIndex: 'collectionStatus', render: collectionTag }
              ]}
            />
          </section>
        </Col>
        <Col xs={24} xl={12}>
          <section className="dashboard-section">
            <div className="section-head">
              <Typography.Title level={4}>扣款失败</Typography.Title>
              <Tag color="volcano">重试队列</Tag>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={data.failedDeductions.slice(0, 8)}
              columns={[
                { title: '扣款号', dataIndex: 'deductNo' },
                { title: '账单', dataIndex: 'billId' },
                { title: '金额', dataIndex: 'deductAmount', render: formatMoney },
                { title: '重试', dataIndex: 'retryCount' },
                { title: '失败原因', dataIndex: 'lastError', ellipsis: true, render: (value?: string) => value || '-' }
              ]}
            />
          </section>
        </Col>
      </Row>
    </Space>
  );
}

function Metric(props: { icon: ReactNode; title: string; value: number | string; suffix?: string; danger?: boolean; tone: 'green' | 'blue' | 'orange' | 'red' | 'violet' }) {
  return (
    <section className="metric-tile">
      <div className="metric-head">
        <span className={`metric-icon ${props.tone}`}>{props.icon}</span>
        <Typography.Text>{props.title}</Typography.Text>
      </div>
      <Statistic value={props.value} valueStyle={props.danger ? { color: '#b42318' } : undefined} />
      {props.suffix ? <Typography.Text type="secondary">{props.suffix}</Typography.Text> : null}
    </section>
  );
}

function sum<T extends Record<string, unknown>>(items: T[], key: keyof T) {
  return items.reduce((total, item) => total + Number(item[key] || 0), 0);
}

function formatMoney(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function billStatusTag(value: RentalBill['billStatus']) {
  const map: Record<RentalBill['billStatus'], { text: string; color: string }> = {
    PENDING_PAYMENT: { text: '待支付', color: 'gold' },
    PAYING: { text: '支付中', color: 'blue' },
    PAID: { text: '已支付', color: 'green' },
    OVERDUE: { text: '已逾期', color: 'red' },
    CANCELLED: { text: '已取消', color: 'default' },
    FAILED: { text: '扣款失败', color: 'volcano' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.text}</Tag>;
}

function collectionTag(value: OverdueCase['collectionStatus']) {
  const map: Record<OverdueCase['collectionStatus'], string> = {
    PENDING: '待催缴',
    CONTACTED: '已联系',
    PROMISED: '承诺付款',
    RESOLVED: '已解决',
    BAD_DEBT: '坏账'
  };
  return <Tag>{map[value]}</Tag>;
}

function externalOrderStatusTag(value: ExternalRentalOrder['orderStatus']) {
  const map: Record<ExternalRentalOrder['orderStatus'], { text: string; color: string }> = {
    ACTIVE: { text: '进行中', color: 'green' },
    COMPLETED: { text: '已完成', color: 'blue' },
    TERMINATED: { text: '已终止', color: 'default' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.text}</Tag>;
}

function externalSourceText(value: ExternalRentalOrder['sourcePlatform']) {
  const map: Record<ExternalRentalOrder['sourcePlatform'], string> = {
    DOUYIN: '抖音',
    MEITUAN: '美团',
    XIANYU: '闲鱼',
    OFFLINE: '线下',
    OTHER: '其他'
  };
  return map[value];
}
