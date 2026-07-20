import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Empty, Modal, Space, Statistic, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  AssetDetail,
  AssetMaintenance,
  AssetRentalRecord,
  CurrentAccount,
  SettlementIncomeEntry,
  SettlementStatement,
  SettlementStatementLine
} from '../types/api';

type InvestorPageProps = {
  account: CurrentAccount;
};

export function InvestorDashboard({ account }: InvestorPageProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
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
      setError(requestError instanceof Error ? requestError.message : '出资方工作台加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const metrics = useMemo(() => ({
    totalAssets: assets.length,
    rentingAssets: assets.filter((item) => item.status === 'RENTING').length,
    idleAssets: assets.filter((item) => item.status === 'IDLE').length,
    repairAssets: assets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length,
    settledIncome: entries.filter((item) => item.entryStatus === 'SETTLED').reduce((sum, item) => sum + Number(item.amount || 0), 0),
    pendingIncome: entries.filter((item) => item.entryStatus === 'PENDING').reduce((sum, item) => sum + Number(item.amount || 0), 0),
    latestStatementIncome: Number(statements[0]?.payableAmount || 0)
  }), [assets, entries, statements]);

  return (
    <Space direction="vertical" size={18} className="page-stack">
      <section className="dashboard-hero">
        <div>
          <Typography.Text className="page-eyebrow">Investor Workspace</Typography.Text>
          <Typography.Title level={3}>出资方工作台</Typography.Title>
          <Typography.Text type="secondary">
            查看 {account.displayName} 名下资产状态与收益分成，聚焦资产运营回报。
          </Typography.Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新数据</Button>
      </section>

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <Space size={16} wrap>
        <Metric title="资产总数" value={metrics.totalAssets} />
        <Metric title="租赁中资产" value={metrics.rentingAssets} />
        <Metric title="空闲资产" value={metrics.idleAssets} />
        <Metric title="维修/异常资产" value={metrics.repairAssets} />
        <Metric title="累计已结算收益" value={money(metrics.settledIncome)} />
        <Metric title="待结算收益" value={money(metrics.pendingIncome)} />
        <Metric title="最近月结金额" value={money(metrics.latestStatementIncome)} />
      </Space>

      <div className="section">
        <Typography.Title level={5}>最近收益</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={entries.slice(0, 8)}
          pagination={false}
          columns={[
            { title: '收益单号', dataIndex: 'entryNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '收益类型', dataIndex: 'lineType', render: lineTypeText },
            { title: '金额', dataIndex: 'amount', render: money },
            { title: '状态', dataIndex: 'entryStatus', render: incomeStatusTag },
            { title: '时间', dataIndex: 'createdAt', render: dateText }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>最近月结单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={statements.slice(0, 6)}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无月结单" /> }}
          columns={[
            { title: '月结单号', dataIndex: 'statementNo' },
            { title: '月份', dataIndex: 'statementMonth' },
            { title: '租金收益', dataIndex: 'rentShareIncomeAmount', render: money },
            { title: '运营手续费', dataIndex: 'operationFeeAmount', render: money },
            { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
            { title: '应结算', dataIndex: 'payableAmount', render: money },
            { title: '状态', dataIndex: 'status', render: statementStatusTag }
          ]}
        />
      </div>
    </Space>
  );
}

export function InvestorAssetsPage({ account }: InvestorPageProps) {
  return <InvestorAssetManagement account={account} />;
}

export function InvestorIncomePage() {
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [statementLines, setStatementLines] = useState<SettlementStatementLine[]>([]);
  const [selectedStatement, setSelectedStatement] = useState<SettlementStatement | null>(null);
  const [statementOpen, setStatementOpen] = useState(false);
  const [status, setStatus] = useState<SettlementIncomeEntry['entryStatus'] | undefined>();
  const [statementStatus, setStatementStatus] = useState<SettlementStatement['status'] | undefined>();
  const [loading, setLoading] = useState(false);

  async function loadEntries() {
    setLoading(true);
    try {
      const [entryData, statementData] = await Promise.all([
        http.get<unknown, SettlementIncomeEntry[]>('/api/investor/settlement/income/entries', { params: { status } }),
        http.get<unknown, SettlementStatement[]>('/api/investor/settlement/statements', { params: { status: statementStatus } })
      ]);
      setEntries(entryData);
      setStatements(statementData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadEntries();
  }, [status, statementStatus]);

  async function openStatement(record: SettlementStatement) {
    setSelectedStatement(record);
    setStatementOpen(true);
    setStatementLines(await http.get<unknown, SettlementStatementLine[]>(`/api/investor/settlement/statements/${record.id}/lines`));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>收益分成</Typography.Title>
        <Tag color="blue">仅展示当前出资方名下收益</Tag>
        <Button icon={<ReloadOutlined />} onClick={loadEntries}>刷新</Button>
      </Space>

      <div className="section">
        <Space style={{ marginBottom: 16 }}>
          <Button size="small" type={!status ? 'primary' : 'default'} onClick={() => setStatus(undefined)}>全部</Button>
          <Button size="small" type={status === 'PENDING' ? 'primary' : 'default'} onClick={() => setStatus('PENDING')}>待结算</Button>
          <Button size="small" type={status === 'SETTLED' ? 'primary' : 'default'} onClick={() => setStatus('SETTLED')}>已结算</Button>
          <Button size="small" type={status === 'FROZEN' ? 'primary' : 'default'} onClick={() => setStatus('FROZEN')}>已冻结</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={entries}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无收益记录" /> }}
          columns={[
            { title: '收益单号', dataIndex: 'entryNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '收益类型', dataIndex: 'lineType', render: lineTypeText },
            { title: '金额', dataIndex: 'amount', render: money },
            { title: '状态', dataIndex: 'entryStatus', render: incomeStatusTag },
            { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
            { title: '结算时间', dataIndex: 'settledAt', render: dateText },
            { title: '创建时间', dataIndex: 'createdAt', render: dateText }
          ]}
        />
      </div>

      <div className="section">
        <Space style={{ marginBottom: 16 }}>
          <Typography.Title level={5} style={{ margin: 0 }}>月结单</Typography.Title>
          <Button size="small" type={!statementStatus ? 'primary' : 'default'} onClick={() => setStatementStatus(undefined)}>全部</Button>
          <Button size="small" type={statementStatus === 'DRAFT' ? 'primary' : 'default'} onClick={() => setStatementStatus('DRAFT')}>草稿</Button>
          <Button size="small" type={statementStatus === 'CONFIRMED' ? 'primary' : 'default'} onClick={() => setStatementStatus('CONFIRMED')}>已确认</Button>
          <Button size="small" type={statementStatus === 'PAID' ? 'primary' : 'default'} onClick={() => setStatementStatus('PAID')}>已打款</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={statements}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无月结单" /> }}
          columns={[
            { title: '月结单号', dataIndex: 'statementNo' },
            { title: '月份', dataIndex: 'statementMonth' },
            { title: '租金收益', dataIndex: 'rentShareIncomeAmount', render: money },
            { title: '运营手续费', dataIndex: 'operationFeeAmount', render: money },
            { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
            { title: '应结算金额', dataIndex: 'payableAmount', render: money },
            { title: '状态', dataIndex: 'status', render: statementStatusTag },
            { title: '操作', render: (_, record) => <Button size="small" onClick={() => openStatement(record)}>明细</Button> }
          ]}
        />
      </div>

      <Modal
        title={selectedStatement ? `月结明细 - ${selectedStatement.statementNo}` : '月结明细'}
        open={statementOpen}
        onCancel={() => {
          setStatementOpen(false);
          setSelectedStatement(null);
          setStatementLines([]);
        }}
        footer={null}
        width={960}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={statementLines}
          pagination={false}
          columns={[
            { title: '类型', dataIndex: 'lineType', render: statementLineText },
            { title: '订单', dataIndex: 'orderId', render: (value) => value ?? '-' },
            { title: '账单', dataIndex: 'billId', render: (value) => value ?? '-' },
            { title: '资产', dataIndex: 'assetId', render: (value) => value ?? '-' },
            { title: '发生时间', dataIndex: 'occurredAt', render: dateText },
            { title: '金额', dataIndex: 'amount', render: signedMoney },
            { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' }
          ]}
        />
      </Modal>
    </Space>
  );
}

function InvestorAssetManagement({ account }: InvestorPageProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<Asset | null>(null);
  const [assetDetail, setAssetDetail] = useState<AssetDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  async function loadAssets() {
    setLoading(true);
    try {
      setAssets(await http.get<unknown, Asset[]>('/api/investor/assets'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAssets();
  }, []);

  async function openDetail(record: Asset) {
    setSelectedAsset(record);
    setDetailOpen(true);
    setAssetDetail(await http.get<unknown, AssetDetail>(`/api/investor/assets/${record.id}/detail`));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>我的资产</Typography.Title>
        <Typography.Text type="secondary">{account.displayName} 名下资产台账</Typography.Text>
        <Button icon={<ReloadOutlined />} onClick={loadAssets}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={assets}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无资产" /> }}
          columns={[
            { title: '资产编码', dataIndex: 'assetCode' },
            { title: '类型', dataIndex: 'assetType', render: assetTypeText },
            { title: '序列号', dataIndex: 'serialNo' },
            { title: '当前商户', dataIndex: 'merchantName', render: (value?: string | null) => value || '-' },
            { title: '当前门店', dataIndex: 'storeName', render: (value?: string | null) => value || '-' },
            { title: '状态', dataIndex: 'status', render: assetStatusTag },
            { title: '采购金额', dataIndex: 'purchaseAmount', render: money },
            { title: '残值', dataIndex: 'residualValue', render: optionalMoney },
            { title: '操作', render: (_, record) => <Button size="small" onClick={() => openDetail(record)}>详情</Button> }
          ]}
        />
      </div>

      <Modal
        title={`${selectedAsset?.assetCode ?? ''} / 资产详情`}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setAssetDetail(null);
        }}
        footer={null}
        width={1120}
      >
        {assetDetail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <section className="section">
              <Typography.Title level={5}>基础信息</Typography.Title>
              <Descriptions bordered size="small" column={3}>
                <Descriptions.Item label="资产编码">{assetDetail.asset.assetCode}</Descriptions.Item>
                <Descriptions.Item label="资产类型">{assetTypeText(assetDetail.asset.assetType)}</Descriptions.Item>
                <Descriptions.Item label="序列号">{assetDetail.asset.serialNo}</Descriptions.Item>
                <Descriptions.Item label="当前商户">{assetDetail.asset.merchantName || '-'}</Descriptions.Item>
                <Descriptions.Item label="当前门店">{assetDetail.asset.storeName || '-'}</Descriptions.Item>
                <Descriptions.Item label="状态">{assetStatusTag(assetDetail.asset.status)}</Descriptions.Item>
                <Descriptions.Item label="残值">{optionalMoney(assetDetail.asset.residualValue)}</Descriptions.Item>
                <Descriptions.Item label="采购日期">{dateText(assetDetail.asset.purchasedAt)}</Descriptions.Item>
              </Descriptions>
            </section>

            <section className="section">
              <Typography.Title level={5}>租赁记录</Typography.Title>
              <Table
                rowKey={(record) => `${record.recordType}-${record.orderId}`}
                size="small"
                dataSource={assetDetail.rentals}
                pagination={false}
                expandable={{ expandedRowRender: rentalBillsTable, rowExpandable: (record) => record.bills.length > 0 }}
                locale={{ emptyText: <Empty description="暂无租赁记录" /> }}
                columns={[
                  { title: '订单号', dataIndex: 'orderNo' },
                  { title: '记录类型', dataIndex: 'recordType', render: rentalRecordTypeTag },
                  { title: '来源', dataIndex: 'sourcePlatform', render: rentalSourceText },
                  { title: '状态', dataIndex: 'orderStatus', render: rentalStatusTag },
                  { title: '外部单号', dataIndex: 'externalOrderNo', render: (value?: string | null) => value || '-' },
                  { title: '客户', render: (_, record) => record.customerName ? `${record.customerName} / ${record.customerPhone || '-'}` : '-' },
                  { title: '租金', dataIndex: 'rentalAmount', render: money },
                  { title: '签单费', dataIndex: 'signFeeAmount', render: money },
                  { title: '已收', dataIndex: 'paidAmount', render: money },
                  { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'MONTH' ? '个月' : '天'} / ${record.totalPeriods}期` },
                  { title: '开始', dataIndex: 'leaseStartedAt', render: dateText },
                  { title: '应还', dataIndex: 'expectedReturnAt', render: dateText },
                  { title: '归还', dataIndex: 'returnedAt', render: dateText }
                ]}
              />
            </section>

            <section className="section">
              <Typography.Title level={5}>维修记录</Typography.Title>
              <Table
                rowKey="id"
                size="small"
                dataSource={assetDetail.maintenances}
                pagination={false}
                expandable={{ expandedRowRender: maintenancePartsTable }}
                locale={{ emptyText: <Empty description="暂无维修记录" /> }}
                columns={[
                  { title: '维修单', dataIndex: 'maintenanceNo' },
                  { title: '类型', dataIndex: 'maintenanceType', render: maintenanceTypeText },
                  { title: '状态', dataIndex: 'maintenanceStatus', render: (value?: string | null) => value || '-' },
                  { title: '配件费', dataIndex: 'partsCost', render: money },
                  { title: '人工费', dataIndex: 'laborCost', render: money },
                  { title: '外协费', dataIndex: 'externalCost', render: money },
                  { title: '总费用', dataIndex: 'totalCost', render: money },
                  { title: '承担方', dataIndex: 'costBearerType', render: costBearerText },
                  { title: '时间', dataIndex: 'createdAt', render: dateText }
                ]}
              />
            </section>
          </Space>
        ) : (
          <Empty description="资产详情加载中" />
        )}
      </Modal>
    </Space>
  );
}

function Metric(props: { title: string; value: string | number }) {
  return (
    <section className="metric-tile">
      <Typography.Text type="secondary">{props.title}</Typography.Text>
      <Statistic value={props.value} />
    </section>
  );
}

function money(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function optionalMoney(value?: number | string | null) {
  return value == null || value === '' ? '-' : money(value);
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function assetStatusTag(value: Asset['status']) {
  const map: Record<Asset['status'], { label: string; color: string }> = {
    IDLE: { label: '空闲', color: 'blue' },
    RENTING: { label: '租赁中', color: 'green' },
    PENDING_REPAIR: { label: '待检修', color: 'orange' },
    REPAIRING: { label: '维修中', color: 'orange' },
    SCRAPPED: { label: '已报废', color: 'default' },
    SOLD: { label: '已售出', color: 'default' },
    EXCEPTION: { label: '异常', color: 'red' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function assetTypeText(value: Asset['assetType']) {
  if (value === 'INTEGRATED_VEHICLE') return '车电一体';
  return value === 'VEHICLE_FRAME' ? '车架' : '电池';
}

function incomeStatusTag(value: SettlementIncomeEntry['entryStatus']) {
  const map: Record<SettlementIncomeEntry['entryStatus'], { label: string; color: string }> = {
    PENDING: { label: '待结算', color: 'gold' },
    SETTLED: { label: '已结算', color: 'green' },
    FROZEN: { label: '已冻结', color: 'blue' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function statementStatusTag(value: SettlementStatement['status']) {
  const map: Record<SettlementStatement['status'], { label: string; color: string }> = {
    DRAFT: { label: '草稿', color: 'default' },
    RECONCILING: { label: '对账中', color: 'processing' },
    CONFIRMED: { label: '已确认', color: 'blue' },
    PAYABLE: { label: '待打款', color: 'gold' },
    PAID: { label: '已打款', color: 'green' },
    CLOSED: { label: '已关闭', color: 'red' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function statementLineText(value: SettlementStatementLine['lineType']) {
  const map: Record<SettlementStatementLine['lineType'], string> = {
    MERCHANT_SIGN_FEE: '商户签单费',
    MERCHANT_RENT_SHARE: '商户租金分润',
    MERCHANT_MAINTENANCE_REIMBURSE: '门店配件补回',
    MERCHANT_MAINTENANCE_DEDUCT: '商户维保扣减',
    MERCHANT_ADJUSTMENT: '商户调整',
    INVESTOR_GROSS_RENT: '出资方租金毛收益',
    INVESTOR_OPERATION_FEE: '运营手续费',
    INVESTOR_MAINTENANCE_DEDUCT: '维保扣减',
    INVESTOR_ADJUSTMENT: '出资方调整'
  };
  return map[value] || value;
}

function signedMoney(value?: number | string | null) {
  const amount = Number(value || 0);
  return amount >= 0 ? `+¥${amount.toFixed(2)}` : `-¥${Math.abs(amount).toFixed(2)}`;
}

function lineTypeText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
    CHANNEL_VERIFICATION_FEE: '渠道核销扣点',
    PLATFORM_SERVICE_FEE: '租赁平台扣点',
    STORE_OPERATION_SHARE: '门店运营分润',
    MAINTENANCE_FUND_SHARE: '维修基金',
    CHANNEL_REFERRAL_SHARE: '渠道引流分润',
    INVESTOR_SHARE: '出资方分润',
    MERCHANT_ORDER_FEE: '门店办单费',
    MERCHANT_RENT_SHARE: '门店租金分成',
    PLATFORM_RENT_SHARE: '平台租金分成',
    PLATFORM_OPERATION_FEE: '运营手续费',
    MAINTENANCE_FEE: '维保费',
    INVESTOR_NET_RENT: '出资方净收益'
  };
  return map[value] || value;
}

function maintenanceTypeText(value: string) {
  const map: Record<string, string> = {
    REPAIR: '维修',
    MAINTENANCE: '保养',
    REPLACE_PART: '换件',
    INSPECTION: '检测'
  };
  return map[value] || value;
}

function costBearerText(value?: AssetMaintenance['costBearerType'] | null) {
  const map: Record<string, string> = {
    USER: '用户',
    INVESTOR: '出资方',
    MERCHANT: '商户',
    PLATFORM: '平台'
  };
  return value ? map[value] || value : '-';
}

function rentalBillsTable(record: AssetRentalRecord) {
  return (
    <Table
      rowKey="id"
      size="small"
      dataSource={record.bills}
      pagination={false}
      columns={[
        { title: '账单号', dataIndex: 'billNo' },
        { title: '期数', dataIndex: 'periodNo' },
        { title: '类型', dataIndex: 'billType' },
        { title: '状态', dataIndex: 'billStatus' },
        { title: '应付', dataIndex: 'payableAmount', render: money },
        { title: '已付', dataIndex: 'paidAmount', render: money },
        { title: '逾期', dataIndex: 'overdueAmount', render: money },
        { title: '到期', dataIndex: 'dueAt', render: dateText }
      ]}
    />
  );
}

function rentalRecordTypeTag(value: AssetRentalRecord['recordType']) {
  return <Tag color={value === 'FORMAL' ? 'blue' : 'purple'}>{value === 'FORMAL' ? '正式订单' : '补录订单'}</Tag>;
}

function rentalSourceText(value?: AssetRentalRecord['sourcePlatform'] | null) {
  const map: Record<string, string> = {
    DOUYIN: '抖音',
    MEITUAN: '美团',
    XIANYU: '闲鱼',
    OFFLINE: '线下',
    OTHER: '其他'
  };
  return value ? map[value] || value : '-';
}

function rentalStatusTag(value: AssetRentalRecord['orderStatus']) {
  const map: Record<string, string> = {
    PENDING_PAYMENT: '待支付',
    PENDING_REAL_NAME: '待实名',
    PENDING_AGREEMENT: '待签约',
    PENDING_DEPOSIT_AUTH: '待免押',
    PENDING_VERIFY: '待核销',
    PENDING_PICKUP: '待取车',
    RENTING: '租赁中',
    PENDING_RETURN: '待归还',
    OVERDUE: '已逾期',
    PENDING_SUPPLEMENT: '待补缴',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    EXCEPTION: '异常',
    ACTIVE: '进行中',
    TERMINATED: '已提前终止'
  };
  return <Tag>{map[value] || value}</Tag>;
}

function maintenancePartsTable(record: AssetMaintenance) {
  return (
    <Table
      rowKey="id"
      size="small"
      dataSource={record.parts}
      pagination={false}
      columns={[
        { title: '配件', dataIndex: 'partNameSnapshot' },
        { title: '数量', dataIndex: 'quantity' },
        { title: '单价', dataIndex: 'unitPrice', render: money },
        { title: '金额', dataIndex: 'totalAmount', render: money },
        { title: '备注', dataIndex: 'remark', render: (value) => value || '-' }
      ]}
    />
  );
}
