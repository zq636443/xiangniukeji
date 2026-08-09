import {
  BankOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  DollarOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FileDoneOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
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
  arrivalBatchNo?: string;
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

type ArrivalBatchSummary = {
  key: string;
  label: string;
  assets: Asset[];
  purchaseAmount: number;
  residualValue: number;
  firstPurchasedAt?: string;
  lastPurchasedAt?: string;
};

const ALL_ARRIVAL_BATCHES = '__ALL_ARRIVAL_BATCHES__';
const UNSET_ARRIVAL_BATCH = '__UNSET_ARRIVAL_BATCH__';

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

  const actualEntries = useMemo(() => entries.filter((item) => item.sourceType !== 'ORDER'), [entries]);
  const incomeMetrics = useMemo(() => ({
    total: sum(actualEntries.map((item) => item.amount)),
    settled: sum(actualEntries.filter((item) => item.entryStatus === 'SETTLED').map((item) => item.amount)),
    pending: sum(actualEntries.filter((item) => item.entryStatus === 'PENDING').map((item) => item.amount)),
    frozen: sum(actualEntries.filter((item) => item.entryStatus === 'FROZEN').map((item) => item.amount)),
    paidStatement: sum(statements.filter((item) => item.status === 'PAID').map((item) => item.payableAmount)),
    payableStatement: sum(statements.filter((item) => ['CONFIRMED', 'PAYABLE'].includes(item.status)).map((item) => item.payableAmount))
  }), [actualEntries, statements]);

  const lineTypeOptions = useMemo(() => [...new Set(entries.map((item) => item.lineType))].map((value) => ({
    value,
    label: lineTypeText(value)
  })), [entries]);

  const filteredEntries = useMemo(() => {
    const keyword = entryKeyword.trim().toLowerCase();
    return [...entries]
      .filter((item) => {
        if (!entrySource && item.sourceType === 'ORDER') return false;
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
      incomeSourceText(item.sourceType),
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
                      { label: '实收账单', value: 'BILL' },
                      { label: '补录订单', value: 'EXTERNAL_ORDER' },
                      { label: '历史整单预计', value: 'ORDER' }
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
  const [arrivalBatchFilter, setArrivalBatchFilter] = useState(ALL_ARRIVAL_BATCHES);
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

  useEffect(() => {
    if (arrivalBatchFilter !== ALL_ARRIVAL_BATCHES && !assets.some((asset) => arrivalBatchKey(asset) === arrivalBatchFilter)) {
      setArrivalBatchFilter(ALL_ARRIVAL_BATCHES);
    }
  }, [arrivalBatchFilter, assets]);

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

  const arrivalBatchSummaries = useMemo(() => buildArrivalBatchSummaries(assets), [assets]);

  const arrivalBatchOptions = useMemo(() => [
    { value: ALL_ARRIVAL_BATCHES, label: `全部批次（${arrivalBatchSummaries.length}）` },
    ...arrivalBatchSummaries.map((batch) => ({ value: batch.key, label: `${batch.label}（${batch.assets.length} 台）` }))
  ], [arrivalBatchSummaries]);

  const arrivalBatchAssets = useMemo(() => {
    if (arrivalBatchFilter === ALL_ARRIVAL_BATCHES) return assets;
    return assets.filter((asset) => arrivalBatchKey(asset) === arrivalBatchFilter);
  }, [arrivalBatchFilter, assets]);

  const arrivalBatchMetrics = useMemo(() => ({
    batchCount: arrivalBatchFilter === ALL_ARRIVAL_BATCHES ? arrivalBatchSummaries.length : Math.min(arrivalBatchAssets.length, 1),
    assetCount: arrivalBatchAssets.length,
    purchaseAmount: sum(arrivalBatchAssets.map((asset) => asset.purchaseAmount)),
    residualValue: sum(arrivalBatchAssets.map((asset) => asset.residualValue)),
    purchaseDateRange: purchaseDateRangeText(arrivalBatchAssets)
  }), [arrivalBatchAssets, arrivalBatchFilter, arrivalBatchSummaries.length]);

  const selectedArrivalBatchLabel = arrivalBatchFilter === ALL_ARRIVAL_BATCHES
    ? '全部到车批次'
    : arrivalBatchSummaries.find((batch) => batch.key === arrivalBatchFilter)?.label || '未设置批次';

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
      return [asset.assetCode, asset.serialNo, asset.arrivalBatchNo, asset.assetTypeName, asset.assetTypeCode, asset.merchantName, asset.storeName]
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
      arrivalBatchNo: record.arrivalBatchNo ?? undefined,
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
        arrivalBatchNo: values.arrivalBatchNo?.trim() ?? '',
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
      '资产编码', '资产类型', '序列号', '到车批次号', '商户', '门店', '状态', '采购金额', '参考残值', '采购日期'
    ], filteredAssets.map((item) => [
      item.assetCode,
      assetTypeLabel(item),
      item.serialNo,
      item.arrivalBatchNo || '',
      item.merchantName || '',
      item.storeName || '',
      assetStatusText(item.status),
      Number(item.purchaseAmount || 0).toFixed(2),
      item.residualValue == null ? '' : Number(item.residualValue).toFixed(2),
      dateText(item.purchasedAt)
    ]));
  }

  async function exportArrivalBatchExcel() {
    if (!arrivalBatchAssets.length) return;
    try {
      const XLSX = await import('xlsx');
      const workbook = XLSX.utils.book_new();
      const exportedSummaries = arrivalBatchFilter === ALL_ARRIVAL_BATCHES
        ? arrivalBatchSummaries
        : arrivalBatchSummaries.filter((batch) => batch.key === arrivalBatchFilter);
      const summarySheet = XLSX.utils.aoa_to_sheet([
        ['途派熊 · 到车批次资产汇总'],
        ['出资方', account.displayName],
        ['导出范围', selectedArrivalBatchLabel],
        ['生成时间', dayjs().format('YYYY-MM-DD HH:mm:ss')],
        [],
        ['到车批次号', '资产数量', '采购日期范围', '采购金额合计', '参考残值合计'],
        ...exportedSummaries.map((batch) => [
          batch.label,
          batch.assets.length,
          summaryPurchaseDateRange(batch),
          batch.purchaseAmount,
          batch.residualValue
        ])
      ]);
      summarySheet['!cols'] = [{ wch: 24 }, { wch: 16 }, { wch: 24 }, { wch: 18 }, { wch: 18 }];
      summarySheet['!autofilter'] = { ref: `A6:E${Math.max(6, exportedSummaries.length + 6)}` };
      setSheetMoneyFormat(summarySheet, ['D', 'E'], 7, exportedSummaries.length + 6);
      XLSX.utils.book_append_sheet(workbook, summarySheet, '批次汇总');

      const detailSheet = XLSX.utils.aoa_to_sheet([
        ['序号', '到车批次号', '资产编码', '资产类型', '资产编号', '采购金额', '采购日期', '参考残值'],
        ...arrivalBatchAssets.map((asset, index) => [
          index + 1,
          arrivalBatchLabel(asset),
          asset.assetCode,
          assetTypeLabel(asset),
          asset.serialNo,
          Number(asset.purchaseAmount || 0),
          dateOnlyText(asset.purchasedAt),
          asset.residualValue == null ? '' : Number(asset.residualValue)
        ])
      ]);
      detailSheet['!cols'] = [
        { wch: 8 }, { wch: 22 }, { wch: 22 }, { wch: 18 }, { wch: 28 },
        { wch: 16 }, { wch: 14 }, { wch: 16 }
      ];
      detailSheet['!autofilter'] = { ref: `A1:H${arrivalBatchAssets.length + 1}` };
      setSheetMoneyFormat(detailSheet, ['F', 'H'], 2, arrivalBatchAssets.length + 1);
      XLSX.utils.book_append_sheet(workbook, detailSheet, '资产明细');

      XLSX.writeFile(workbook, `途派熊-到车批次资产明细-${safeFileNamePart(selectedArrivalBatchLabel)}.xlsx`, { compression: true });
      message.success('到车批次资产明细 Excel 已导出');
    } catch (exportError) {
      message.error(exportError instanceof Error ? exportError.message : 'Excel 导出失败');
    }
  }

  function exportArrivalBatchPdf() {
    if (!arrivalBatchAssets.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      message.error('浏览器阻止了打印窗口，请允许弹出窗口后重试');
      return;
    }
    printWindow.opener = null;
    printWindow.document.open();
    printWindow.document.write(buildArrivalBatchPrintHtml({
      investorName: account.displayName,
      scopeLabel: selectedArrivalBatchLabel,
      assets: arrivalBatchAssets,
      summaries: arrivalBatchFilter === ALL_ARRIVAL_BATCHES
        ? arrivalBatchSummaries
        : arrivalBatchSummaries.filter((batch) => batch.key === arrivalBatchFilter),
      logoUrl: new URL('/tupaixiong-logo.png', window.location.origin).href
    }));
    printWindow.document.close();
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
          <Button icon={<DownloadOutlined />} disabled={filteredAssets.length === 0} onClick={exportAssets}>导出资产 CSV</Button>
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

      <section className="section arrival-batch-section">
        <div className="section-head investor-list-head arrival-batch-head">
          <div>
            <Typography.Text className="page-eyebrow">Arrival Batch Assets</Typography.Text>
            <Typography.Title level={4}>到车批次资产明细</Typography.Title>
            <Typography.Text type="secondary">系统按出资方和采购/到车日期自动归批，真实批次号可手工覆盖；本清单不包含租赁订单与履约信息。</Typography.Text>
          </div>
          <Space wrap>
            <Select
              showSearch
              optionFilterProp="label"
              value={arrivalBatchFilter}
              onChange={setArrivalBatchFilter}
              options={arrivalBatchOptions}
              className="arrival-batch-select"
            />
            <Button icon={<FileExcelOutlined />} disabled={!arrivalBatchAssets.length} onClick={() => void exportArrivalBatchExcel()}>导出 Excel</Button>
            <Button icon={<FilePdfOutlined />} disabled={!arrivalBatchAssets.length} onClick={exportArrivalBatchPdf}>导出 PDF</Button>
          </Space>
        </div>

        <div className="arrival-batch-kpis">
          <article>
            <span>当前范围</span>
            <strong>{selectedArrivalBatchLabel}</strong>
            <small>{arrivalBatchMetrics.batchCount} 个批次</small>
          </article>
          <article>
            <span>资产数量</span>
            <strong>{arrivalBatchMetrics.assetCount} 台</strong>
            <small>按资产编码逐台展示</small>
          </article>
          <article>
            <span>采购金额</span>
            <strong>{money(arrivalBatchMetrics.purchaseAmount)}</strong>
            <small>当前范围采购合计</small>
          </article>
          <article>
            <span>参考残值</span>
            <strong>{money(arrivalBatchMetrics.residualValue)}</strong>
            <small>未设置残值按 0 汇总</small>
          </article>
          <article>
            <span>采购日期</span>
            <strong>{arrivalBatchMetrics.purchaseDateRange}</strong>
            <small>当前范围最早至最晚日期</small>
          </article>
        </div>

        <Table
          rowKey="id"
          size="small"
          dataSource={arrivalBatchAssets}
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 台资产` }}
          locale={{ emptyText: <Empty description="暂无到车批次资产" /> }}
          scroll={{ x: 980 }}
          columns={[
            { title: '序号', width: 70, fixed: 'left', render: (_, __, index) => index + 1 },
            { title: '到车批次', dataIndex: 'arrivalBatchNo', width: 170, fixed: 'left', render: (value?: string | null) => value ? <Tag color="green">{value}</Tag> : <Tag>未设置批次</Tag> },
            { title: '资产编码', dataIndex: 'assetCode', width: 180 },
            { title: '资产类型', width: 150, render: (_, record) => assetTypeLabel(record) },
            { title: '资产编号', dataIndex: 'serialNo', width: 220 },
            { title: '采购金额', dataIndex: 'purchaseAmount', width: 130, render: money },
            { title: '采购日期', dataIndex: 'purchasedAt', width: 130, render: dateOnlyText },
            { title: '参考残值', dataIndex: 'residualValue', width: 130, render: optionalMoney }
          ]}
        />
      </section>

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
          scroll={{ x: 1320 }}
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
            { title: '到车批次', dataIndex: 'arrivalBatchNo', width: 150, render: (value?: string | null) => value || '未设置批次' },
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
          <Form.Item name="arrivalBatchNo" label="到车批次号" extra="留空将按出资方和采购/到车日期自动生成；真实批次号可直接填写">
            <Input maxLength={64} placeholder="例如：2026-08-LY-01" />
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
                <Descriptions.Item label="到车批次">{assetDetail.asset.arrivalBatchNo || '未设置批次'}</Descriptions.Item>
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

function arrivalBatchKey(asset: Asset) {
  return asset.arrivalBatchNo?.trim() || UNSET_ARRIVAL_BATCH;
}

function arrivalBatchLabel(asset: Asset) {
  return asset.arrivalBatchNo?.trim() || '未设置批次';
}

function buildArrivalBatchSummaries(assets: Asset[]): ArrivalBatchSummary[] {
  const groups = new Map<string, Asset[]>();
  assets.forEach((asset) => {
    const key = arrivalBatchKey(asset);
    groups.set(key, [...(groups.get(key) || []), asset]);
  });
  return [...groups.entries()].map(([key, batchAssets]) => {
    const purchaseDates = batchAssets
      .map((asset) => asset.purchasedAt?.slice(0, 10))
      .filter((value): value is string => Boolean(value))
      .sort();
    return {
      key,
      label: key === UNSET_ARRIVAL_BATCH ? '未设置批次' : key,
      assets: batchAssets,
      purchaseAmount: sum(batchAssets.map((asset) => asset.purchaseAmount)),
      residualValue: sum(batchAssets.map((asset) => asset.residualValue)),
      firstPurchasedAt: purchaseDates[0],
      lastPurchasedAt: purchaseDates[purchaseDates.length - 1]
    };
  }).sort((left, right) => {
    if (left.key === UNSET_ARRIVAL_BATCH) return 1;
    if (right.key === UNSET_ARRIVAL_BATCH) return -1;
    return String(right.lastPurchasedAt || '').localeCompare(String(left.lastPurchasedAt || ''))
      || right.label.localeCompare(left.label, 'zh-CN');
  });
}

function purchaseDateRangeText(assets: Asset[]) {
  const dates = assets
    .map((asset) => asset.purchasedAt?.slice(0, 10))
    .filter((value): value is string => Boolean(value))
    .sort();
  if (!dates.length) return '-';
  const lastDate = dates[dates.length - 1];
  return dates[0] === lastDate ? dates[0] : `${dates[0]} 至 ${lastDate}`;
}

function summaryPurchaseDateRange(summary: ArrivalBatchSummary) {
  if (!summary.firstPurchasedAt) return '-';
  return summary.firstPurchasedAt === summary.lastPurchasedAt
    ? summary.firstPurchasedAt
    : `${summary.firstPurchasedAt} 至 ${summary.lastPurchasedAt}`;
}

function setSheetMoneyFormat(sheet: import('xlsx').WorkSheet, columns: string[], startRow: number, endRow: number) {
  columns.forEach((column) => {
    for (let row = startRow; row <= endRow; row++) {
      const cell = sheet[`${column}${row}`];
      if (cell && cell.v !== '') cell.z = '¥#,##0.00';
    }
  });
}

function safeFileNamePart(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, '-').slice(0, 48) || '全部批次';
}

function buildArrivalBatchPrintHtml(input: {
  investorName: string;
  scopeLabel: string;
  assets: Asset[];
  summaries: ArrivalBatchSummary[];
  logoUrl: string;
}) {
  const purchaseAmount = sum(input.assets.map((asset) => asset.purchaseAmount));
  const residualValue = sum(input.assets.map((asset) => asset.residualValue));
  const generatedAt = dayjs().format('YYYY-MM-DD HH:mm:ss');
  const summaryRows = input.summaries.map((batch) => `
    <tr>
      <td>${escapeHtml(batch.label)}</td>
      <td class="number">${batch.assets.length}</td>
      <td>${escapeHtml(summaryPurchaseDateRange(batch))}</td>
      <td class="money">${escapeHtml(plainMoney(batch.purchaseAmount))}</td>
      <td class="money">${escapeHtml(plainMoney(batch.residualValue))}</td>
    </tr>
  `).join('');
  const detailRows = input.assets.map((asset, index) => `
    <tr>
      <td class="number">${index + 1}</td>
      <td>${escapeHtml(arrivalBatchLabel(asset))}</td>
      <td>${escapeHtml(asset.assetCode)}</td>
      <td>${escapeHtml(assetTypeLabel(asset))}</td>
      <td>${escapeHtml(asset.serialNo)}</td>
      <td class="money">${escapeHtml(plainMoney(asset.purchaseAmount))}</td>
      <td>${escapeHtml(dateOnlyText(asset.purchasedAt))}</td>
      <td class="money">${asset.residualValue == null ? '-' : escapeHtml(plainMoney(asset.residualValue))}</td>
    </tr>
  `).join('');
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>途派熊-到车批次资产明细-${escapeHtml(input.scopeLabel)}</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; color: #182230; background: #eef4f1; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
    .toolbar { position: sticky; top: 0; z-index: 2; padding: 12px 20px; display: flex; justify-content: flex-end; background: rgba(255,255,255,.95); border-bottom: 1px solid #dfe8e4; }
    .toolbar button { padding: 9px 18px; border: 0; border-radius: 8px; color: #fff; background: #0f9f7a; font-size: 14px; font-weight: 700; cursor: pointer; }
    .page { width: min(1320px, calc(100% - 32px)); margin: 24px auto; padding: 28px; background: #fff; border: 1px solid #dfe8e4; border-radius: 14px; box-shadow: 0 12px 30px rgba(16,24,40,.08); }
    .hero { padding: 24px 26px; display: flex; align-items: center; justify-content: space-between; gap: 24px; border-radius: 12px; color: #fff; background: radial-gradient(circle at 92% 0%, rgba(255,255,255,.22), transparent 32%), linear-gradient(135deg, #0a745a, #0f9f7a); }
    .brand { display: flex; align-items: center; gap: 14px; }
    .brand img { width: 58px; height: 58px; object-fit: contain; border-radius: 14px; background: rgba(255,255,255,.96); padding: 5px; }
    .eyebrow { margin: 0 0 4px; font-size: 11px; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; opacity: .78; }
    h1 { margin: 0; font-size: 26px; }
    .hero-meta { text-align: right; font-size: 13px; line-height: 1.7; }
    .metrics { margin: 18px 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
    .metric { min-height: 96px; padding: 15px 16px; border: 1px solid #dfe8e4; border-radius: 10px; background: linear-gradient(180deg, #fff, #fbfefd); }
    .metric span { display: block; color: #667085; font-size: 12px; }
    .metric strong { display: block; margin-top: 8px; color: #0a745a; font-size: 21px; line-height: 1.25; }
    .panel { margin-top: 18px; }
    .panel-head { margin-bottom: 10px; display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
    h2 { margin: 0; font-size: 18px; }
    .note { color: #667085; font-size: 12px; }
    table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 11px; }
    th { padding: 9px 8px; color: #344054; background: #edf8f4; border: 1px solid #d9e8e2; text-align: left; font-weight: 800; }
    td { padding: 8px; border: 1px solid #e4ebe8; vertical-align: top; word-break: break-all; }
    tbody tr:nth-child(even) { background: #fbfdfc; }
    .number { text-align: center; }
    .money { text-align: right; white-space: nowrap; }
    .footer { margin-top: 18px; padding-top: 12px; display: flex; justify-content: space-between; gap: 16px; color: #667085; border-top: 1px solid #e4ebe8; font-size: 11px; }
    @media print {
      @page { size: A4 landscape; margin: 10mm; }
      body { background: #fff; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      .toolbar { display: none; }
      .page { width: 100%; margin: 0; padding: 0; border: 0; border-radius: 0; box-shadow: none; }
      .hero { break-inside: avoid; }
      .metrics { break-inside: avoid; }
      thead { display: table-header-group; }
      tr { break-inside: avoid; }
    }
  </style>
</head>
<body>
  <div class="toolbar"><button type="button" onclick="window.print()">打印 / 保存为 PDF</button></div>
  <main class="page">
    <header class="hero">
      <div class="brand">
        <img src="${escapeHtml(input.logoUrl)}" alt="途派熊" />
        <div><p class="eyebrow">TUPAIXIONG ASSET REPORT</p><h1>到车批次资产明细</h1></div>
      </div>
      <div class="hero-meta"><div>出资方：${escapeHtml(input.investorName)}</div><div>导出范围：${escapeHtml(input.scopeLabel)}</div><div>生成时间：${generatedAt}</div></div>
    </header>
    <section class="metrics">
      <div class="metric"><span>批次数量</span><strong>${input.summaries.length} 个</strong></div>
      <div class="metric"><span>资产数量</span><strong>${input.assets.length} 台</strong></div>
      <div class="metric"><span>采购金额合计</span><strong>${escapeHtml(plainMoney(purchaseAmount))}</strong></div>
      <div class="metric"><span>参考残值合计</span><strong>${escapeHtml(plainMoney(residualValue))}</strong></div>
    </section>
    <section class="panel">
      <div class="panel-head"><h2>批次汇总</h2><span class="note">采购日期按批次内资产的最早至最晚日期展示</span></div>
      <table><thead><tr><th>到车批次号</th><th>资产数量</th><th>采购日期范围</th><th>采购金额合计</th><th>参考残值合计</th></tr></thead><tbody>${summaryRows}</tbody></table>
    </section>
    <section class="panel">
      <div class="panel-head"><h2>资产明细</h2><span class="note">本清单不包含租赁订单、客户及履约信息</span></div>
      <table><thead><tr><th style="width:5%">序号</th><th style="width:15%">到车批次号</th><th style="width:15%">资产编码</th><th style="width:11%">资产类型</th><th style="width:21%">资产编号</th><th style="width:12%">采购金额</th><th style="width:10%">采购日期</th><th style="width:11%">参考残值</th></tr></thead><tbody>${detailRows}</tbody></table>
    </section>
    <footer class="footer"><span>途派熊租赁运营平台 · 出资方资产报告</span><span>数据生成时间：${generatedAt}</span></footer>
  </main>
  <script>window.addEventListener('load', function () { window.setTimeout(function () { window.print(); }, 350); });</script>
</body>
</html>`;
}

function plainMoney(value?: number | string | null) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function escapeHtml(value?: string | number | null) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
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
  if (value === 'BILL') return <Tag color="green">实收账单</Tag>;
  if (value === 'EXTERNAL_ORDER') return <Tag color="purple">补录订单</Tag>;
  return <Tag>历史整单预计</Tag>;
}

function incomeSourceText(value: SettlementIncomeEntry['sourceType']) {
  if (value === 'BILL') return '实收账单';
  if (value === 'EXTERNAL_ORDER') return '补录订单';
  return '历史整单预计';
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
    PLATFORM_ORDER_FEE_SERVICE_FEE: '办单费手续费',
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
