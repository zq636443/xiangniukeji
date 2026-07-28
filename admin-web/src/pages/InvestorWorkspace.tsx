import {
  BankOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  DollarOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  SwapOutlined,
  ToolOutlined,
  WalletOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Col,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { InvestorOperationsCockpit } from './InvestorCockpit';
import { http } from '../services/request';
import type {
  Asset,
  AssetDetail,
  AssetMaintenance,
  AssetRentalRecord,
  AssetTypeDefinition,
  CurrentAccount,
  SettlementIncomeEntry,
  SettlementStatement,
  SettlementStatementLine
} from '../types/api';
import { downloadCsv } from '../utils/csv';

type InvestorPageProps = {
  account: CurrentAccount;
};

type InvestorAssetForm = {
  assetTypeId: number;
  serialNo: string;
  currentMerchantId?: number;
  currentStoreId?: number;
  purchaseAmount: number;
  residualValue?: number;
  purchasedAt?: Dayjs;
};

type InvestorTransferForm = {
  merchantId: number;
  storeId: number;
  remark?: string;
};

type InvestorAssetStatusForm = {
  status: Asset['status'];
  remark?: string;
};

type InvestorMerchantOption = {
  id: number;
  merchantCode: string;
  merchantName: string;
};

type InvestorStoreOption = {
  id: number;
  merchantId: number;
  storeCode: string;
  storeName: string;
};

type InvestorMetricTone = 'green' | 'blue' | 'orange' | 'red' | 'violet';

export function InvestorDashboard({ account }: InvestorPageProps) {
  return <InvestorOperationsCockpit account={account} />;
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
  const [activeTab, setActiveTab] = useState('income');
  const [entryKeyword, setEntryKeyword] = useState('');
  const [entryStatus, setEntryStatus] = useState<SettlementIncomeEntry['entryStatus'] | undefined>();
  const [entrySource, setEntrySource] = useState<SettlementIncomeEntry['sourceType'] | undefined>();
  const [entryLineType, setEntryLineType] = useState<SettlementIncomeEntry['lineType'] | undefined>();
  const [statementKeyword, setStatementKeyword] = useState('');
  const [statementMonth, setStatementMonth] = useState<string>();
  const [statementStatus, setStatementStatus] = useState<SettlementStatement['status'] | undefined>();
  const [loading, setLoading] = useState(false);
  const [lineLoading, setLineLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [entryData, statementData] = await Promise.all([
        http.get<unknown, SettlementIncomeEntry[]>('/api/investor/settlement/income/entries'),
        http.get<unknown, SettlementStatement[]>('/api/investor/settlement/statements')
      ]);
      setEntries(entryData);
      setStatements(statementData);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '收益结算加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const incomeMetrics = useMemo(() => ({
    total: sum(entries.map((item) => item.amount)),
    settled: sum(entries.filter((item) => item.entryStatus === 'SETTLED').map((item) => item.amount)),
    pending: sum(entries.filter((item) => item.entryStatus === 'PENDING').map((item) => item.amount)),
    frozen: sum(entries.filter((item) => item.entryStatus === 'FROZEN').map((item) => item.amount)),
    paidStatement: sum(statements.filter((item) => item.status === 'PAID').map((item) => item.payableAmount)),
    payableStatement: sum(statements.filter((item) => ['CONFIRMED', 'PAYABLE'].includes(item.status)).map((item) => item.payableAmount))
  }), [entries, statements]);

  const lineTypeOptions = useMemo(() => [...new Set(entries.map((item) => item.lineType))].map((value) => ({
    value,
    label: lineTypeText(value)
  })), [entries]);

  const filteredEntries = useMemo(() => {
    const keyword = entryKeyword.trim().toLowerCase();
    return [...entries]
      .filter((item) => {
        if (entryStatus && item.entryStatus !== entryStatus) return false;
        if (entrySource && item.sourceType !== entrySource) return false;
        if (entryLineType && item.lineType !== entryLineType) return false;
        if (!keyword) return true;
        return [item.entryNo, item.sourceNo, item.sourceId, lineTypeText(item.lineType), item.remark]
          .some((value) => String(value || '').toLowerCase().includes(keyword));
      })
      .sort((left, right) => right.id - left.id);
  }, [entries, entryKeyword, entryLineType, entrySource, entryStatus]);

  const filteredStatements = useMemo(() => {
    const keyword = statementKeyword.trim().toLowerCase();
    return [...statements]
      .filter((item) => {
        if (statementMonth && item.statementMonth !== statementMonth) return false;
        if (statementStatus && item.status !== statementStatus) return false;
        if (!keyword) return true;
        return [item.statementNo, item.statementMonth, item.remark]
          .some((value) => String(value || '').toLowerCase().includes(keyword));
      })
      .sort((left, right) => right.statementMonth.localeCompare(left.statementMonth) || right.id - left.id);
  }, [statementKeyword, statementMonth, statementStatus, statements]);

  async function openStatement(record: SettlementStatement) {
    setSelectedStatement(record);
    setStatementOpen(true);
    setLineLoading(true);
    try {
      setStatementLines(await http.get<unknown, SettlementStatementLine[]>(`/api/investor/settlement/statements/${record.id}/lines`));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '月结明细加载失败');
    } finally {
      setLineLoading(false);
    }
  }

  function resetIncomeFilters() {
    setEntryKeyword('');
    setEntryStatus(undefined);
    setEntrySource(undefined);
    setEntryLineType(undefined);
  }

  function resetStatementFilters() {
    setStatementKeyword('');
    setStatementMonth(undefined);
    setStatementStatus(undefined);
  }

  function exportIncome() {
    downloadCsv('出资方收益流水', [
      '收益单号', '来源', '业务单号', '收益类型', '金额', '状态', '结算时间', '备注'
    ], filteredEntries.map((item) => [
      item.entryNo,
      item.sourceType === 'EXTERNAL_ORDER' ? '补录订单' : '正式订单',
      item.sourceNo || item.sourceId,
      lineTypeText(item.lineType),
      Number(item.amount || 0).toFixed(2),
      incomeStatusText(item.entryStatus),
      dateText(item.settledAt),
      item.remark || ''
    ]));
  }

  function exportStatements() {
    downloadCsv('出资方月结单', [
      '月份', '月结单号', '收益基数', '出资方分润', '结算调整', '最终应结算', '订单数', '账单数', '状态', '确认时间', '打款时间', '备注'
    ], filteredStatements.map((item) => [
      item.statementMonth,
      item.statementNo,
      Number(item.rentBaseAmount || 0).toFixed(2),
      Number(item.rentShareIncomeAmount || 0).toFixed(2),
      Number(item.adjustmentAmount || 0).toFixed(2),
      Number(item.payableAmount || 0).toFixed(2),
      item.orderCount,
      item.billCount,
      statementStatusText(item.status),
      dateText(item.confirmedAt),
      dateText(item.paidAt),
      item.remark || ''
    ]));
  }

  function exportStatementLines() {
    if (!selectedStatement) return;
    downloadCsv(`出资方月结明细-${selectedStatement.statementNo}`, [
      '明细号', '类型', '来源', '来源ID', '订单ID', '账单ID', '资产ID', '金额', '备注'
    ], statementLines.map((item) => [
      item.lineNo,
      statementLineText(item.lineType),
      statementSourceText(item.sourceType),
      item.sourceId,
      item.orderId || '',
      item.billId || '',
      item.assetId || '',
      Number(item.amount || 0).toFixed(2),
      item.remark || ''
    ]));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <section className="dashboard-hero investor-page-header">
        <div>
          <Typography.Text className="page-eyebrow">Investor Settlement</Typography.Text>
          <Typography.Title level={3}>收益结算</Typography.Title>
          <Typography.Text type="secondary">核对订单收益流水、月结金额与打款状态。</Typography.Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新数据</Button>
      </section>

      {error ? <Alert type="error" message={error} showIcon closable onClose={() => setError('')} /> : null}

      <Alert
        type="info"
        showIcon
        message="当前出资方结算口径"
        description="收益按订单实际绑定的单一出资方归属；车架与电池属于不同出资方时应拆分订单，日常维修不从出资方收益中扣减。"
      />

      <Row gutter={[12, 12]}>
        <Col span={4}><InvestorMetric icon={<DollarOutlined />} tone="blue" label="累计收益流水" value={money(incomeMetrics.total)} detail={`${entries.length} 笔收益记录`} /></Col>
        <Col span={4}><InvestorMetric icon={<CheckCircleOutlined />} tone="green" label="已结算收益" value={money(incomeMetrics.settled)} detail="收益台账已结算" /></Col>
        <Col span={4}><InvestorMetric icon={<WalletOutlined />} tone="orange" label="待归集收益" value={money(incomeMetrics.pending)} detail="等待进入月结" /></Col>
        <Col span={4}><InvestorMetric icon={<ExclamationCircleOutlined />} tone="red" label="冻结收益" value={money(incomeMetrics.frozen)} detail="等待平台处理" /></Col>
        <Col span={4}><InvestorMetric icon={<FileDoneOutlined />} tone="green" label="已打款月结" value={money(incomeMetrics.paidStatement)} detail="以正式月结单为准" /></Col>
        <Col span={4}><InvestorMetric icon={<FileSearchOutlined />} tone="violet" label="待打款月结" value={money(incomeMetrics.payableStatement)} detail="已确认或待打款" /></Col>
      </Row>

      <Tabs
        className="settlement-tabs investor-settlement-tabs"
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'income',
            label: <span>收益流水 <span className="tab-count">{filteredEntries.length}</span></span>,
            children: (
              <section className="section">
                <div className="section-head investor-list-head">
                  <div>
                    <Typography.Title level={4}>收益流水</Typography.Title>
                    <Typography.Text type="secondary">逐笔核对正式订单与补录订单产生的出资方收益</Typography.Text>
                  </div>
                  <Button icon={<DownloadOutlined />} disabled={filteredEntries.length === 0} onClick={exportIncome}>导出收益流水</Button>
                </div>
                <div className="investor-filter-bar">
                  <Input.Search
                    allowClear
                    value={entryKeyword}
                    onChange={(event) => setEntryKeyword(event.target.value)}
                    placeholder="搜索收益单号、业务单号或备注"
                    style={{ width: 280 }}
                  />
                  <Select
                    allowClear
                    value={entrySource}
                    onChange={setEntrySource}
                    placeholder="订单来源"
                    style={{ width: 140 }}
                    options={[
                      { label: '正式订单', value: 'ORDER' },
                      { label: '补录订单', value: 'EXTERNAL_ORDER' }
                    ]}
                  />
                  <Select
                    allowClear
                    value={entryLineType}
                    onChange={setEntryLineType}
                    placeholder="收益类型"
                    style={{ width: 160 }}
                    options={lineTypeOptions}
                  />
                  <Select
                    allowClear
                    value={entryStatus}
                    onChange={setEntryStatus}
                    placeholder="结算状态"
                    style={{ width: 140 }}
                    options={[
                      { label: '待结算', value: 'PENDING' },
                      { label: '已结算', value: 'SETTLED' },
                      { label: '已冻结', value: 'FROZEN' }
                    ]}
                  />
                  <Button onClick={resetIncomeFilters}>重置</Button>
                </div>
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={filteredEntries}
                  pagination={{ pageSize: 12, showSizeChanger: true, showTotal: (total) => `共 ${total} 笔` }}
                  locale={{ emptyText: <Empty description="暂无收益记录" /> }}
                  scroll={{ x: 1120 }}
                  columns={[
                    { title: '收益单号', dataIndex: 'entryNo', width: 190, fixed: 'left' },
                    { title: '来源', dataIndex: 'sourceType', width: 105, render: incomeSourceTag },
                    { title: '业务单号', width: 180, render: (_, record) => record.sourceNo || record.sourceId },
                    { title: '收益类型', dataIndex: 'lineType', width: 140, render: lineTypeText },
                    { title: '金额', dataIndex: 'amount', width: 120, render: (value) => <strong className="amount-positive">{money(value)}</strong> },
                    { title: '状态', dataIndex: 'entryStatus', width: 100, render: incomeStatusTag },
                    { title: '结算时间', dataIndex: 'settledAt', width: 160, render: dateText },
                    { title: '备注', dataIndex: 'remark', render: emptyText }
                  ]}
                />
              </section>
            )
          },
          {
            key: 'statements',
            label: <span>月结对账 <span className="tab-count">{filteredStatements.length}</span></span>,
            children: (
              <section className="section">
                <div className="section-head investor-list-head">
                  <div>
                    <Typography.Title level={4}>月结对账</Typography.Title>
                    <Typography.Text type="secondary">按月份查看收益基数、出资方分润、调整和实际打款</Typography.Text>
                  </div>
                  <Button icon={<DownloadOutlined />} disabled={filteredStatements.length === 0} onClick={exportStatements}>导出月结单</Button>
                </div>
                <div className="investor-filter-bar">
                  <Input.Search
                    allowClear
                    value={statementKeyword}
                    onChange={(event) => setStatementKeyword(event.target.value)}
                    placeholder="搜索月结单号或备注"
                    style={{ width: 260 }}
                  />
                  <DatePicker
                    picker="month"
                    allowClear
                    value={monthValue(statementMonth)}
                    onChange={(value) => setStatementMonth(value ? value.format('YYYY-MM') : undefined)}
                    placeholder="结算月份"
                    style={{ width: 150 }}
                  />
                  <Select
                    allowClear
                    value={statementStatus}
                    onChange={setStatementStatus}
                    placeholder="月结状态"
                    style={{ width: 140 }}
                    options={statementStatusOptions}
                  />
                  <Button onClick={resetStatementFilters}>重置</Button>
                </div>
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={filteredStatements}
                  pagination={{ pageSize: 12, showSizeChanger: true, showTotal: (total) => `共 ${total} 张` }}
                  locale={{ emptyText: <Empty description="暂无月结单" /> }}
                  scroll={{ x: 1260 }}
                  columns={[
                    { title: '月份', dataIndex: 'statementMonth', width: 90, fixed: 'left' },
                    { title: '月结单号', dataIndex: 'statementNo', width: 200 },
                    { title: '收益基数', dataIndex: 'rentBaseAmount', width: 120, render: money },
                    { title: '出资方分润', dataIndex: 'rentShareIncomeAmount', width: 130, render: money },
                    { title: '结算调整', dataIndex: 'adjustmentAmount', width: 110, render: signedMoney },
                    { title: '最终应结算', dataIndex: 'payableAmount', width: 130, render: (value) => <strong className="amount-positive">{money(value)}</strong> },
                    { title: '业务量', width: 120, render: (_, record) => `${record.orderCount} 单 / ${record.billCount} 账单` },
                    { title: '状态', dataIndex: 'status', width: 95, render: statementStatusTag },
                    { title: '确认时间', dataIndex: 'confirmedAt', width: 160, render: dateText },
                    { title: '打款时间', dataIndex: 'paidAt', width: 160, render: dateText },
                    { title: '操作', width: 82, fixed: 'right', render: (_, record) => <Button size="small" type="link" onClick={() => void openStatement(record)}>明细</Button> }
                  ]}
                />
              </section>
            )
          }
        ]}
      />

      <Modal
        title={selectedStatement ? `月结明细 - ${selectedStatement.statementNo}` : '月结明细'}
        open={statementOpen}
        onCancel={() => {
          setStatementOpen(false);
          setSelectedStatement(null);
          setStatementLines([]);
        }}
        footer={<Button onClick={() => setStatementOpen(false)}>关闭</Button>}
        width={1120}
      >
        {selectedStatement ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={[12, 12]}>
              <Col span={6}><InvestorMetric icon={<WalletOutlined />} tone="blue" label="收益基数" value={money(selectedStatement.rentBaseAmount)} detail={`${selectedStatement.orderCount} 笔订单`} compact /></Col>
              <Col span={6}><InvestorMetric icon={<DollarOutlined />} tone="green" label="出资方分润" value={money(selectedStatement.rentShareIncomeAmount)} detail={`${selectedStatement.billCount} 张账单`} compact /></Col>
              <Col span={6}><InvestorMetric icon={<FileSearchOutlined />} tone="violet" label="结算调整" value={signedMoneyText(selectedStatement.adjustmentAmount)} detail="人工调整项" compact /></Col>
              <Col span={6}><InvestorMetric icon={<CheckCircleOutlined />} tone="green" label="最终应结算" value={money(selectedStatement.payableAmount)} detail={statementStatusText(selectedStatement.status)} compact /></Col>
            </Row>
            {Number(selectedStatement.operationFeeAmount || 0) + Number(selectedStatement.maintenanceDeductAmount || 0) > 0 ? (
              <Alert
                type="warning"
                showIcon
                message="该月结单包含历史扣减项目"
                description="历史数据继续保留用于对账；当前规则不再从出资方承担日常维修费用。"
              />
            ) : null}
            <Descriptions bordered size="small" column={4}>
              <Descriptions.Item label="结算月份">{selectedStatement.statementMonth}</Descriptions.Item>
              <Descriptions.Item label="状态">{statementStatusTag(selectedStatement.status)}</Descriptions.Item>
              <Descriptions.Item label="确认时间">{dateText(selectedStatement.confirmedAt)}</Descriptions.Item>
              <Descriptions.Item label="打款时间">{dateText(selectedStatement.paidAt)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={4}>{selectedStatement.remark || '-'}</Descriptions.Item>
            </Descriptions>
            <div className="section-head investor-list-head">
              <div>
                <Typography.Title level={4}>月结构成</Typography.Title>
                <Typography.Text type="secondary">共 {statementLines.length} 条结算明细</Typography.Text>
              </div>
              <Button icon={<DownloadOutlined />} disabled={statementLines.length === 0} onClick={exportStatementLines}>导出明细</Button>
            </div>
            <Table
              rowKey="id"
              size="small"
              loading={lineLoading}
              dataSource={statementLines}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              locale={{ emptyText: <Empty description="暂无月结明细" /> }}
              scroll={{ x: 980 }}
              columns={[
                { title: '明细号', dataIndex: 'lineNo', width: 160 },
                { title: '类型', dataIndex: 'lineType', width: 140, render: statementLineText },
                { title: '来源', dataIndex: 'sourceType', width: 110, render: statementSourceTag },
                { title: '业务引用', width: 150, render: (_, record) => `${record.sourceType} #${record.sourceId}` },
                { title: '订单', dataIndex: 'orderId', width: 90, render: emptyText },
                { title: '账单', dataIndex: 'billId', width: 90, render: emptyText },
                { title: '资产', dataIndex: 'assetId', width: 90, render: emptyText },
                { title: '金额', dataIndex: 'amount', width: 120, render: signedMoney },
                { title: '备注', dataIndex: 'remark', render: emptyText }
              ]}
            />
          </Space>
        ) : <Empty description="请选择月结单" />}
      </Modal>
    </Space>
  );
}

function InvestorAssetManagement({ account }: InvestorPageProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [assetTypes, setAssetTypes] = useState<AssetTypeDefinition[]>([]);
  const [merchants, setMerchants] = useState<InvestorMerchantOption[]>([]);
  const [stores, setStores] = useState<InvestorStoreOption[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<Asset | null>(null);
  const [editingAsset, setEditingAsset] = useState<Asset | null>(null);
  const [actionAsset, setActionAsset] = useState<Asset | null>(null);
  const [assetDetail, setAssetDetail] = useState<AssetDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [assetOpen, setAssetOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<Asset['status']>();
  const [storeFilter, setStoreFilter] = useState<number>();
  const [typeFilter, setTypeFilter] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [assetForm] = Form.useForm<InvestorAssetForm>();
  const [transferForm] = Form.useForm<InvestorTransferForm>();
  const [statusForm] = Form.useForm<InvestorAssetStatusForm>();
  const selectedAssetMerchantId = Form.useWatch('currentMerchantId', assetForm);
  const selectedTransferMerchantId = Form.useWatch('merchantId', transferForm);
  const canManageAssets = account.permissions.includes('asset.manage');
  const canOperateAssets = account.permissions.includes('asset.operate');

  async function loadAssets() {
    setLoading(true);
    setError('');
    try {
      const [assetData, typeData, merchantData, storeData] = await Promise.all([
        http.get<unknown, Asset[]>('/api/investor/assets'),
        http.get<unknown, AssetTypeDefinition[]>('/api/investor/assets/types'),
        http.get<unknown, InvestorMerchantOption[]>('/api/investor/assets/merchants'),
        http.get<unknown, InvestorStoreOption[]>('/api/investor/assets/stores')
      ]);
      setAssets(assetData);
      setAssetTypes(typeData);
      setMerchants(merchantData);
      setStores(storeData);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '资产台账加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAssets();
  }, []);

  const storeOptions = useMemo(() => {
    const options = new Map<number, string>();
    assets.forEach((asset) => {
      if (asset.currentStoreId) options.set(asset.currentStoreId, asset.storeName || `门店 #${asset.currentStoreId}`);
    });
    return [...options.entries()].map(([value, label]) => ({ value, label }));
  }, [assets]);

  const typeOptions = useMemo(() => {
    const options = new Map<number, string>();
    assets.forEach((asset) => options.set(asset.assetTypeId, assetTypeLabel(asset)));
    return [...options.entries()].map(([value, label]) => ({ value, label }));
  }, [assets]);

  const assetTypeEntryOptions = useMemo(() => assetTypes.map((type) => ({
    value: type.id,
    label: type.typeName
  })), [assetTypes]);

  const merchantOptions = useMemo(() => merchants.map((merchant) => ({
    value: merchant.id,
    label: `${merchant.merchantName} / ${merchant.merchantCode}`
  })), [merchants]);

  const assetStoreOptions = useMemo(() => stores
    .filter((store) => store.merchantId === selectedAssetMerchantId)
    .map((store) => ({ value: store.id, label: `${store.storeName} / ${store.storeCode}` })), [selectedAssetMerchantId, stores]);

  const transferStoreOptions = useMemo(() => stores
    .filter((store) => store.merchantId === selectedTransferMerchantId)
    .map((store) => ({ value: store.id, label: `${store.storeName} / ${store.storeCode}` })), [selectedTransferMerchantId, stores]);

  const filteredAssets = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return assets.filter((asset) => {
      if (statusFilter && asset.status !== statusFilter) return false;
      if (storeFilter && asset.currentStoreId !== storeFilter) return false;
      if (typeFilter && asset.assetTypeId !== typeFilter) return false;
      if (!normalized) return true;
      return [asset.assetCode, asset.serialNo, asset.assetTypeName, asset.assetTypeCode, asset.merchantName, asset.storeName]
        .some((value) => String(value || '').toLowerCase().includes(normalized));
    });
  }, [assets, keyword, statusFilter, storeFilter, typeFilter]);

  const assetMetrics = useMemo(() => ({
    total: filteredAssets.length,
    purchaseAmount: sum(filteredAssets.map((item) => item.purchaseAmount)),
    renting: filteredAssets.filter((item) => item.status === 'RENTING').length,
    idle: filteredAssets.filter((item) => item.status === 'IDLE').length,
    attention: filteredAssets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length,
    residualValue: sum(filteredAssets.map((item) => item.residualValue))
  }), [filteredAssets]);

  const detailMetrics = useMemo(() => {
    if (!assetDetail) return null;
    return {
      rentals: assetDetail.rentals.length,
      paidAmount: sum(assetDetail.rentals.map((item) => item.paidAmount)),
      verificationAmount: sum(assetDetail.rentals.map((item) => item.verificationAmount)),
      maintenanceAmount: sum(assetDetail.maintenances.map((item) => item.totalCost))
    };
  }, [assetDetail]);

  async function openDetail(record: Asset) {
    setSelectedAsset(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      setAssetDetail(await http.get<unknown, AssetDetail>(`/api/investor/assets/${record.id}/detail`));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '资产详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function openCreateAsset() {
    setEditingAsset(null);
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetTypeId: assetTypes[0]?.id,
      purchaseAmount: 0,
      purchasedAt: dayjs()
    });
    setAssetOpen(true);
  }

  function openEditAsset(record: Asset) {
    setEditingAsset(record);
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetTypeId: record.assetTypeId,
      serialNo: record.serialNo,
      purchaseAmount: Number(record.purchaseAmount),
      residualValue: record.residualValue == null ? undefined : Number(record.residualValue),
      purchasedAt: record.purchasedAt ? dayjs(record.purchasedAt) : undefined
    });
    setAssetOpen(true);
  }

  function openTransferAsset(record: Asset) {
    setActionAsset(record);
    transferForm.resetFields();
    transferForm.setFieldsValue({
      merchantId: record.currentMerchantId ?? undefined,
      storeId: record.currentStoreId ?? undefined,
      remark: '出资方调拨资产'
    });
    setTransferOpen(true);
  }

  function openStatusAsset(record: Asset) {
    setActionAsset(record);
    statusForm.resetFields();
    statusForm.setFieldsValue({ status: record.status, remark: '出资方变更资产状态' });
    setStatusOpen(true);
  }

  async function submitAsset(values: InvestorAssetForm) {
    setSaving(true);
    try {
      const payload = {
        assetTypeId: values.assetTypeId,
        serialNo: values.serialNo.trim(),
        purchaseAmount: values.purchaseAmount,
        residualValue: values.residualValue,
        purchasedAt: values.purchasedAt?.format('YYYY-MM-DD')
      };
      if (editingAsset) {
        await http.put(`/api/investor/assets/${editingAsset.id}`, payload);
        message.success('资产资料已更新');
      } else {
        await http.post('/api/investor/assets', {
          ...payload,
          currentMerchantId: values.currentMerchantId,
          currentStoreId: values.currentStoreId
        });
        message.success('资产已录入并关联到所选门店');
      }
      setAssetOpen(false);
      setEditingAsset(null);
      assetForm.resetFields();
      await loadAssets();
    } finally {
      setSaving(false);
    }
  }

  async function submitTransfer(values: InvestorTransferForm) {
    if (!actionAsset) return;
    setSaving(true);
    try {
      await http.put(`/api/investor/assets/${actionAsset.id}/transfer`, values);
      message.success('资产调拨完成');
      setTransferOpen(false);
      setActionAsset(null);
      await loadAssets();
    } finally {
      setSaving(false);
    }
  }

  async function submitStatus(values: InvestorAssetStatusForm) {
    if (!actionAsset) return;
    setSaving(true);
    try {
      await http.put(`/api/investor/assets/${actionAsset.id}/status`, values);
      message.success('资产状态已更新');
      setStatusOpen(false);
      setActionAsset(null);
      await loadAssets();
    } finally {
      setSaving(false);
    }
  }

  async function deleteAsset(record: Asset) {
    await http.delete(`/api/investor/assets/${record.id}`);
    message.success('资产已删除');
    await loadAssets();
  }

  function resetFilters() {
    setKeyword('');
    setStatusFilter(undefined);
    setStoreFilter(undefined);
    setTypeFilter(undefined);
  }

  function exportAssets() {
    downloadCsv('出资方资产台账', [
      '资产编码', '资产类型', '序列号', '商户', '门店', '状态', '采购金额', '参考残值', '采购日期'
    ], filteredAssets.map((item) => [
      item.assetCode,
      assetTypeLabel(item),
      item.serialNo,
      item.merchantName || '',
      item.storeName || '',
      assetStatusText(item.status),
      Number(item.purchaseAmount || 0).toFixed(2),
      item.residualValue == null ? '' : Number(item.residualValue).toFixed(2),
      dateText(item.purchasedAt)
    ]));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <section className="dashboard-hero investor-page-header">
        <div>
          <Typography.Text className="page-eyebrow">Investor Assets</Typography.Text>
          <Typography.Title level={3}>资产运营</Typography.Title>
          <Typography.Text type="secondary">{account.displayName} 名下资产、投放位置、租赁履历和维修记录。</Typography.Text>
        </div>
        <Space>
          <Button icon={<DownloadOutlined />} disabled={filteredAssets.length === 0} onClick={exportAssets}>导出资产</Button>
          {canManageAssets ? <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAsset}>录入资产</Button> : null}
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadAssets}>刷新数据</Button>
        </Space>
      </section>

      {error ? <Alert type="error" message={error} showIcon closable onClose={() => setError('')} /> : null}

      <Row gutter={[12, 12]}>
        <Col span={4}><InvestorMetric icon={<BankOutlined />} tone="blue" label="筛选资产" value={assetMetrics.total} detail={`参考残值 ${money(assetMetrics.residualValue)}`} /></Col>
        <Col span={5}><InvestorMetric icon={<WalletOutlined />} tone="violet" label="账面投入" value={money(assetMetrics.purchaseAmount)} detail="按采购金额汇总" /></Col>
        <Col span={5}><InvestorMetric icon={<SafetyCertificateOutlined />} tone="green" label="租赁中" value={assetMetrics.renting} detail={`使用率 ${percent(assetMetrics.total ? assetMetrics.renting / assetMetrics.total * 100 : 0)}`} /></Col>
        <Col span={5}><InvestorMetric icon={<ShopOutlined />} tone="blue" label="空闲资产" value={assetMetrics.idle} detail="可继续投放" /></Col>
        <Col span={5}><InvestorMetric icon={<ToolOutlined />} tone="orange" label="维修及异常" value={assetMetrics.attention} detail="需要关注状态" /></Col>
      </Row>

      <section className="section">
        <div className="section-head investor-list-head">
          <div>
            <Typography.Title level={4}>资产台账</Typography.Title>
            <Typography.Text type="secondary">支持自定义资产类型、门店和状态筛选</Typography.Text>
          </div>
          <Tag color="blue">{filteredAssets.length} / {assets.length}</Tag>
        </div>
        <div className="investor-filter-bar">
          <Input.Search
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索资产编码、序列号、类型或门店"
            style={{ width: 300 }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={typeFilter}
            onChange={setTypeFilter}
            placeholder="资产类型"
            style={{ width: 170 }}
            options={typeOptions}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={storeFilter}
            onChange={setStoreFilter}
            placeholder="当前门店"
            style={{ width: 180 }}
            options={storeOptions}
          />
          <Select
            allowClear
            value={statusFilter}
            onChange={setStatusFilter}
            placeholder="资产状态"
            style={{ width: 150 }}
            options={assetStatusOptions}
          />
          <Button onClick={resetFilters}>重置</Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={filteredAssets}
          pagination={{ pageSize: 12, showSizeChanger: true, showTotal: (total) => `共 ${total} 台` }}
          locale={{ emptyText: <Empty description="暂无资产" /> }}
          scroll={{ x: 1180 }}
          columns={[
            { title: '序号', width: 70, fixed: 'left', render: (_, __, index) => index + 1 },
            {
              title: '资产',
              width: 210,
              fixed: 'left',
              render: (_, record) => (
                <div className="investor-primary-cell">
                  <strong>{record.assetCode}</strong>
                  <span>{record.serialLabel || '序列号'}：{record.serialNo}</span>
                </div>
              )
            },
            { title: '资产类型', width: 150, render: (_, record) => assetTypeLabel(record) },
            {
              title: '当前投放',
              width: 210,
              render: (_, record) => (
                <div className="investor-primary-cell">
                  <strong>{record.storeName || '未分配门店'}</strong>
                  <span>{record.merchantName || '未分配商户'}</span>
                </div>
              )
            },
            { title: '状态', dataIndex: 'status', width: 100, render: assetStatusTag },
            { title: '采购金额', dataIndex: 'purchaseAmount', width: 120, render: money },
            { title: '参考残值', dataIndex: 'residualValue', width: 120, render: optionalMoney },
            { title: '采购日期', dataIndex: 'purchasedAt', width: 120, render: dateOnlyText },
            {
              title: '操作',
              width: 300,
              fixed: 'right',
              render: (_, record) => (
                <Space size={4} wrap>
                  {canManageAssets ? (
                    <Button size="small" icon={<EditOutlined />} disabled={record.status === 'RENTING'} onClick={() => openEditAsset(record)}>编辑</Button>
                  ) : null}
                  {canOperateAssets ? (
                    <Button size="small" icon={<SwapOutlined />} disabled={record.status === 'RENTING'} onClick={() => openTransferAsset(record)}>调拨</Button>
                  ) : null}
                  {canOperateAssets ? (
                    <Button size="small" disabled={record.status === 'RENTING' || record.status === 'SCRAPPED' || record.status === 'SOLD'} onClick={() => openStatusAsset(record)}>状态</Button>
                  ) : null}
                  <Button size="small" type="link" onClick={() => void openDetail(record)}>详情</Button>
                  {canManageAssets ? (
                    <Popconfirm title="删除资产" description="仅空闲且没有任何业务记录的资产可以删除。" onConfirm={() => void deleteAsset(record)}>
                      <Button size="small" danger icon={<DeleteOutlined />} disabled={record.status !== 'IDLE'} />
                    </Popconfirm>
                  ) : null}
                </Space>
              )
            }
          ]}
        />
      </section>

      <Modal
        title={editingAsset ? '编辑我的资产' : '录入我的资产'}
        open={assetOpen}
        onCancel={() => { setAssetOpen(false); setEditingAsset(null); assetForm.resetFields(); }}
        onOk={() => assetForm.submit()}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={assetForm} layout="vertical" onFinish={submitAsset}>
          <Form.Item name="assetTypeId" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
            <Select showSearch optionFilterProp="label" options={assetTypeEntryOptions} />
          </Form.Item>
          <Form.Item name="serialNo" label="资产编号" rules={[{ required: true, message: '请输入资产编号' }]}>
            <Input />
          </Form.Item>
          {!editingAsset ? (
            <>
              <Form.Item name="currentMerchantId" label="关联商户">
                <Select allowClear showSearch optionFilterProp="label" options={merchantOptions} onChange={() => assetForm.setFieldValue('currentStoreId', undefined)} />
              </Form.Item>
              <Form.Item
                name="currentStoreId"
                label="关联门店"
                rules={[({ getFieldValue }) => ({
                  validator: (_, value) => getFieldValue('currentMerchantId') && !value
                    ? Promise.reject(new Error('选择商户后必须同时选择门店'))
                    : Promise.resolve()
                })]}
              >
                <Select allowClear showSearch optionFilterProp="label" disabled={!selectedAssetMerchantId} options={assetStoreOptions} />
              </Form.Item>
            </>
          ) : null}
          <Form.Item name="purchaseAmount" label="采购金额" rules={[{ required: true, message: '请输入采购金额' }]}>
            <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="residualValue" label="参考残值">
            <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="purchasedAt" label="采购日期">
            <DatePicker format="YYYY-MM-DD" style={{ width: '100%' }} placeholder="点击选择采购日期" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`调拨资产 ${actionAsset?.assetCode || ''}`} open={transferOpen} onCancel={() => { setTransferOpen(false); setActionAsset(null); }} onOk={() => transferForm.submit()} confirmLoading={saving} destroyOnHidden>
        <Form form={transferForm} layout="vertical" onFinish={submitTransfer}>
          <Form.Item name="merchantId" label="目标商户" rules={[{ required: true, message: '请选择目标商户' }]}>
            <Select showSearch optionFilterProp="label" options={merchantOptions} onChange={() => transferForm.setFieldValue('storeId', undefined)} />
          </Form.Item>
          <Form.Item name="storeId" label="目标门店" rules={[{ required: true, message: '请选择目标门店' }]}>
            <Select showSearch optionFilterProp="label" disabled={!selectedTransferMerchantId} options={transferStoreOptions} />
          </Form.Item>
          <Form.Item name="remark" label="调拨备注"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`变更资产状态 ${actionAsset?.assetCode || ''}`} open={statusOpen} onCancel={() => { setStatusOpen(false); setActionAsset(null); }} onOk={() => statusForm.submit()} confirmLoading={saving} destroyOnHidden>
        <Form form={statusForm} layout="vertical" onFinish={submitStatus}>
          <Form.Item name="status" label="目标状态" rules={[{ required: true, message: '请选择目标状态' }]}>
            <Select options={assetStatusOptions.filter((item) => item.value !== 'RENTING')} />
          </Form.Item>
          <Form.Item name="remark" label="变更备注"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${selectedAsset?.assetCode ?? ''} / 资产运营详情`}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setSelectedAsset(null);
          setAssetDetail(null);
        }}
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        width={1180}
      >
        {assetDetail && detailMetrics ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={[12, 12]}>
              <Col span={6}><InvestorMetric icon={<FileSearchOutlined />} tone="blue" label="租赁记录" value={detailMetrics.rentals} detail="正式订单与补录订单" compact /></Col>
              <Col span={6}><InvestorMetric icon={<WalletOutlined />} tone="green" label="关联订单已收" value={money(detailMetrics.paidAmount)} detail="同一订单不按资产拆分" compact /></Col>
              <Col span={6}><InvestorMetric icon={<CheckCircleOutlined />} tone="violet" label="关联订单核销额" value={money(detailMetrics.verificationAmount)} detail="同一订单不按资产拆分" compact /></Col>
              <Col span={6}><InvestorMetric icon={<ToolOutlined />} tone="orange" label="维修记录金额" value={money(detailMetrics.maintenanceAmount)} detail={`${assetDetail.maintenances.length} 条维修记录`} compact /></Col>
            </Row>

            <section className="section investor-modal-section">
              <div className="section-head"><Typography.Title level={4}>基础信息</Typography.Title></div>
              <Descriptions bordered size="small" column={4}>
                <Descriptions.Item label="资产编码">{assetDetail.asset.assetCode}</Descriptions.Item>
                <Descriptions.Item label="资产类型">{assetTypeLabel(assetDetail.asset)}</Descriptions.Item>
                <Descriptions.Item label={assetDetail.asset.serialLabel || '序列号'}>{assetDetail.asset.serialNo}</Descriptions.Item>
                <Descriptions.Item label="状态">{assetStatusTag(assetDetail.asset.status)}</Descriptions.Item>
                <Descriptions.Item label="当前商户">{assetDetail.asset.merchantName || '-'}</Descriptions.Item>
                <Descriptions.Item label="当前门店">{assetDetail.asset.storeName || '-'}</Descriptions.Item>
                <Descriptions.Item label="采购金额">{money(assetDetail.asset.purchaseAmount)}</Descriptions.Item>
                <Descriptions.Item label="参考残值">{optionalMoney(assetDetail.asset.residualValue)}</Descriptions.Item>
                <Descriptions.Item label="采购日期">{dateOnlyText(assetDetail.asset.purchasedAt)}</Descriptions.Item>
                <Descriptions.Item label="报废日期">{dateOnlyText(assetDetail.asset.scrappedAt)}</Descriptions.Item>
                <Descriptions.Item label="出售日期">{dateOnlyText(assetDetail.asset.soldAt)}</Descriptions.Item>
              </Descriptions>
            </section>

            <section className="section investor-modal-section">
              <div className="section-head">
                <Typography.Title level={4}>租赁履历</Typography.Title>
                <Tag>{assetDetail.rentals.length} 条</Tag>
              </div>
              <Table
                rowKey={(record) => `${record.recordType}-${record.orderId}`}
                size="small"
                dataSource={assetDetail.rentals}
                pagination={{ pageSize: 8, showSizeChanger: true }}
                expandable={{ expandedRowRender: rentalBillsTable, rowExpandable: (record) => record.bills.length > 0 }}
                locale={{ emptyText: <Empty description="暂无租赁记录" /> }}
                scroll={{ x: 1450 }}
                columns={[
                  { title: '订单号', dataIndex: 'orderNo', width: 190, fixed: 'left' },
                  { title: '记录类型', dataIndex: 'recordType', width: 100, render: rentalRecordTypeTag },
                  { title: '来源', dataIndex: 'sourcePlatform', width: 90, render: rentalSourceText },
                  { title: '状态', dataIndex: 'orderStatus', width: 100, render: rentalStatusTag },
                  { title: '外部单号', dataIndex: 'externalOrderNo', width: 160, render: emptyText },
                  { title: '客户', width: 180, render: (_, record) => record.customerName ? `${record.customerName} / ${record.customerPhone || '-'}` : '-' },
                  { title: '租金', dataIndex: 'rentalAmount', width: 110, render: money },
                  { title: '实际核销', dataIndex: 'verificationAmount', width: 115, render: money },
                  { title: '签单费', dataIndex: 'signFeeAmount', width: 100, render: money },
                  { title: '已收', dataIndex: 'paidAmount', width: 110, render: money },
                  { title: '租期', width: 130, render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'MONTH' ? '个月' : '天'} / ${record.totalPeriods}期` },
                  { title: '开始', dataIndex: 'leaseStartedAt', width: 150, render: dateText },
                  { title: '应还', dataIndex: 'expectedReturnAt', width: 150, render: dateText },
                  { title: '归还', dataIndex: 'returnedAt', width: 150, render: dateText }
                ]}
              />
            </section>

            <section className="section investor-modal-section">
              <div className="section-head">
                <Typography.Title level={4}>维修记录</Typography.Title>
                <Tag color={assetDetail.maintenances.length > 0 ? 'orange' : 'default'}>{assetDetail.maintenances.length} 条</Tag>
              </div>
              <Table
                rowKey="id"
                size="small"
                dataSource={assetDetail.maintenances}
                pagination={{ pageSize: 8, showSizeChanger: true }}
                expandable={{ expandedRowRender: maintenancePartsTable, rowExpandable: (record) => record.parts.length > 0 }}
                locale={{ emptyText: <Empty description="暂无维修记录" /> }}
                scroll={{ x: 1250 }}
                columns={[
                  { title: '维修单', dataIndex: 'maintenanceNo', width: 190, fixed: 'left' },
                  { title: '类型', dataIndex: 'maintenanceType', width: 90, render: maintenanceTypeText },
                  { title: '状态', dataIndex: 'maintenanceStatus', width: 100, render: emptyText },
                  { title: '责任类型', dataIndex: 'responsibilityType', width: 130, render: responsibilityTypeText },
                  { title: '配件费', dataIndex: 'partsCost', width: 100, render: money },
                  { title: '人工费', dataIndex: 'laborCost', width: 100, render: money },
                  { title: '外协费', dataIndex: 'externalCost', width: 100, render: money },
                  { title: '总费用', dataIndex: 'totalCost', width: 110, render: money },
                  { title: '承担方', dataIndex: 'costBearerType', width: 120, render: costBearerText },
                  { title: '开始', dataIndex: 'startedAt', width: 150, render: dateText },
                  { title: '完成', dataIndex: 'completedAt', width: 150, render: dateText },
                  { title: '备注', dataIndex: 'remark', render: emptyText }
                ]}
              />
            </section>
          </Space>
        ) : (
          <Empty description={detailLoading ? '资产详情加载中' : '暂无资产详情'} />
        )}
      </Modal>
    </Space>
  );
}

function InvestorMetric(props: {
  icon: ReactNode;
  tone: InvestorMetricTone;
  label: string;
  value: string | number;
  detail: ReactNode;
  compact?: boolean;
}) {
  return (
    <section className={`metric-tile investor-metric${props.compact ? ' investor-metric-compact' : ''}`}>
      <div className="metric-head">
        <span className={`metric-icon ${props.tone}`}>{props.icon}</span>
        <span>{props.label}</span>
      </div>
      <Statistic value={props.value} />
      <Typography.Text type="secondary">{props.detail}</Typography.Text>
    </section>
  );
}

function sum(values: Array<number | string | null | undefined>) {
  return values.reduce<number>((total, value) => total + Number(value || 0), 0);
}

function money(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function optionalMoney(value?: number | string | null) {
  return value == null || value === '' ? '-' : money(value);
}

function percent(value?: number | string | null) {
  return `${Number(value || 0).toFixed(1)}%`;
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function dateOnlyText(value?: string | null) {
  return value ? value.slice(0, 10) : '-';
}

function monthValue(value?: string): Dayjs | null {
  return value ? dayjs(`${value}-01`) : null;
}

function emptyText(value?: string | number | null) {
  return value == null || value === '' ? '-' : value;
}

function assetStatusTag(value: Asset['status']) {
  const item = assetStatusMap[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function assetStatusText(value: Asset['status']) {
  return assetStatusMap[value].label;
}

const assetStatusMap: Record<Asset['status'], { label: string; color: string }> = {
  IDLE: { label: '空闲', color: 'blue' },
  RENTING: { label: '租赁中', color: 'green' },
  PENDING_REPAIR: { label: '待检修', color: 'orange' },
  REPAIRING: { label: '维修中', color: 'orange' },
  SCRAPPED: { label: '已报废', color: 'default' },
  SOLD: { label: '已售出', color: 'default' },
  EXCEPTION: { label: '异常', color: 'red' }
};

const assetStatusOptions = Object.entries(assetStatusMap).map(([value, item]) => ({ value, label: item.label }));

function assetTypeLabel(asset: Asset) {
  return asset.assetTypeName || assetTypeText(asset.assetType);
}

function assetTypeText(value: Asset['assetType']) {
  if (value === 'INTEGRATED_VEHICLE') return '车电一体';
  if (value === 'VEHICLE_FRAME') return '车架';
  if (value === 'BATTERY') return '电池';
  return '其他资产';
}

function incomeStatusTag(value: SettlementIncomeEntry['entryStatus']) {
  const item = incomeStatusMap[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function incomeStatusText(value: SettlementIncomeEntry['entryStatus']) {
  return incomeStatusMap[value].label;
}

const incomeStatusMap: Record<SettlementIncomeEntry['entryStatus'], { label: string; color: string }> = {
  PENDING: { label: '待结算', color: 'gold' },
  SETTLED: { label: '已结算', color: 'green' },
  FROZEN: { label: '已冻结', color: 'blue' }
};

function incomeSourceTag(value: SettlementIncomeEntry['sourceType']) {
  return value === 'EXTERNAL_ORDER'
    ? <Tag color="purple">补录订单</Tag>
    : <Tag color="blue">正式订单</Tag>;
}

function statementStatusTag(value: SettlementStatement['status']) {
  const item = statementStatusMap[value];
  return <Tag color={item.color}>{item.label}</Tag>;
}

function statementStatusText(value: SettlementStatement['status']) {
  return statementStatusMap[value].label;
}

const statementStatusMap: Record<SettlementStatement['status'], { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  RECONCILING: { label: '对账中', color: 'processing' },
  CONFIRMED: { label: '已确认', color: 'blue' },
  PAYABLE: { label: '待打款', color: 'gold' },
  PAID: { label: '已打款', color: 'green' },
  CLOSED: { label: '已关闭', color: 'red' }
};

const statementStatusOptions = Object.entries(statementStatusMap).map(([value, item]) => ({ value, label: item.label }));

function statementLineText(value: SettlementStatementLine['lineType']) {
  const map: Record<SettlementStatementLine['lineType'], string> = {
    MERCHANT_SIGN_FEE: '商户签单费',
    MERCHANT_RENT_SHARE: '商户租金分润',
    MERCHANT_MAINTENANCE_SHARE: '门店维修分润',
    MERCHANT_MAINTENANCE_REIMBURSE: '门店配件补回',
    MERCHANT_MAINTENANCE_DEDUCT: '商户维保扣减',
    MERCHANT_ADJUSTMENT: '商户调整',
    INVESTOR_GROSS_RENT: '出资方分润',
    INVESTOR_OPERATION_FEE: '历史运营扣减',
    INVESTOR_MAINTENANCE_DEDUCT: '历史维保扣减',
    INVESTOR_ADJUSTMENT: '结算调整'
  };
  return map[value] || value;
}

function statementSourceTag(value: string) {
  if (value === 'EXTERNAL_ORDER') return <Tag color="purple">补录订单</Tag>;
  if (value === 'MAINTENANCE') return <Tag color="orange">维修记录</Tag>;
  return <Tag color="blue">正式订单/账单</Tag>;
}

function statementSourceText(value: string) {
  if (value === 'EXTERNAL_ORDER') return '补录订单';
  if (value === 'MAINTENANCE') return '维修记录';
  return '正式订单/账单';
}

function signedMoney(value?: number | string | null) {
  const amount = Number(value || 0);
  return <span className={amount < 0 ? 'amount-negative' : 'amount-positive'}>{signedMoneyText(amount)}</span>;
}

function signedMoneyText(value?: number | string | null) {
  const amount = Number(value || 0);
  return amount >= 0 ? `+¥${amount.toFixed(2)}` : `-¥${Math.abs(amount).toFixed(2)}`;
}

function lineTypeText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
    CHANNEL_VERIFICATION_FEE: '渠道核销扣点',
    PLATFORM_SERVICE_FEE: '租赁平台扣点',
    STORE_OPERATION_SHARE: '门店运营分润',
    MAINTENANCE_FUND_SHARE: '门店维修分润',
    CHANNEL_REFERRAL_SHARE: '渠道引流分润',
    INVESTOR_SHARE: '出资方分润',
    MERCHANT_ORDER_FEE: '门店办单费',
    MERCHANT_RENT_SHARE: '门店租金分成',
    PLATFORM_RENT_SHARE: '平台租金分成',
    PLATFORM_OPERATION_FEE: '平台运营收益',
    MAINTENANCE_FEE: '历史维保费用',
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

function responsibilityTypeText(value?: AssetMaintenance['responsibilityType'] | null) {
  const map: Record<AssetMaintenance['responsibilityType'], string> = {
    ROUTINE_MAINTENANCE: '日常维修',
    CUSTOMER_DAMAGE: '用户损坏',
    MERCHANT_RESPONSIBILITY: '门店责任',
    PLATFORM_SUBSIDY: '平台补贴'
  };
  return value ? map[value] || value : '-';
}

function costBearerText(value?: AssetMaintenance['costBearerType'] | null) {
  const map: Record<string, string> = {
    USER: '用户',
    INVESTOR: '历史出资方承担',
    MERCHANT: '商户/门店',
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
        { title: '期数', dataIndex: 'periodNo', width: 70 },
        { title: '类型', dataIndex: 'billType', width: 110, render: billTypeText },
        { title: '状态', dataIndex: 'billStatus', width: 100, render: billStatusTag },
        { title: '应付', dataIndex: 'payableAmount', width: 110, render: money },
        { title: '已付', dataIndex: 'paidAmount', width: 110, render: money },
        { title: '逾期', dataIndex: 'overdueAmount', width: 110, render: money },
        { title: '到期', dataIndex: 'dueAt', width: 160, render: dateText }
      ]}
    />
  );
}

function billTypeText(value: string) {
  const map: Record<string, string> = {
    RENT: '租金',
    DEPOSIT: '押金',
    SIGN_FEE: '签单费',
    SUPPLEMENT: '补缴',
    OVERDUE: '逾期费用'
  };
  return map[value] || value;
}

function billStatusTag(value: string) {
  const map: Record<string, { label: string; color: string }> = {
    UNPAID: { label: '待支付', color: 'gold' },
    PART_PAID: { label: '部分支付', color: 'orange' },
    PAID: { label: '已支付', color: 'green' },
    OVERDUE: { label: '已逾期', color: 'red' },
    CANCELLED: { label: '已取消', color: 'default' },
    CLOSED: { label: '已关闭', color: 'default' }
  };
  const item = map[value];
  return item ? <Tag color={item.color}>{item.label}</Tag> : <Tag>{value}</Tag>;
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
        { title: '数量', dataIndex: 'quantity', width: 80 },
        { title: '单价', dataIndex: 'unitPrice', width: 110, render: money },
        { title: '金额', dataIndex: 'totalAmount', width: 110, render: money },
        { title: '备注', dataIndex: 'remark', render: emptyText }
      ]}
    />
  );
}
