import { DeleteOutlined, EditOutlined, PlusOutlined, PoweroffOutlined } from '@ant-design/icons';
import {
  Button,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message
} from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  ProfitRule,
  SettlementIncomeEntry,
  SettlementOverview,
  SettlementSnapshot,
  SettlementStatement,
  SettlementStatementGenerateResult,
  SettlementStatementLine,
  Store,
  StoreSku
} from '../types/api';

type RuleForm = {
  ruleName: string;
  storeId: number;
  sourceChannel?: string;
  priority: number;
  channelFeeRate: number;
  platformFeeRate: number;
  storeOperationRate: number;
  maintenanceFundRate: number;
  channelReferralRate: number;
  investorShareRate: number;
  effectiveAt: Dayjs;
  expiredAt?: Dayjs;
};

type PreviewForm = {
  storeSkuId: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  rentalAmount: number;
  sourceChannel: string;
};

const sourceChannelOptions = [
  { label: '平台直租', value: 'DIRECT' },
  { label: '抖音', value: 'DOUYIN' },
  { label: '美团', value: 'MEITUAN' },
  { label: '闲鱼', value: 'XIANYU' }
];

const ruleChannelOptions = [
  ...sourceChannelOptions,
  { label: '线下', value: 'OFFLINE' },
  { label: '其他', value: 'OTHER' }
];

export function SettlementManagement() {
  const [rules, setRules] = useState<ProfitRule[]>([]);
  const [snapshots, setSnapshots] = useState<SettlementSnapshot[]>([]);
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [overview, setOverview] = useState<SettlementOverview | null>(null);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [statementLines, setStatementLines] = useState<SettlementStatementLine[]>([]);
  const [statementMonth, setStatementMonth] = useState(currentMonth());
  const [statementDetailOpen, setStatementDetailOpen] = useState(false);
  const [selectedStatement, setSelectedStatement] = useState<SettlementStatement | null>(null);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [preview, setPreview] = useState<SettlementSnapshot | null>(null);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<ProfitRule | null>(null);
  const [ruleStoreFilter, setRuleStoreFilter] = useState<number>();
  const [ruleChannelFilter, setRuleChannelFilter] = useState<string>();
  const [ruleStatusFilter, setRuleStatusFilter] = useState<ProfitRule['status']>();
  const [ruleForm] = Form.useForm<RuleForm>();
  const [previewForm] = Form.useForm<PreviewForm>();
  const [incomeForm] = Form.useForm<{ orderId: number }>();
  const selectedPreviewFrameAssetId = Form.useWatch('frameAssetId', previewForm);

  useEffect(() => {
    void loadAll();
  }, []);

  useEffect(() => {
    void reloadStatements(statementMonth);
  }, [statementMonth]);

  const storeSkuOptions = useMemo(() => storeSkus.map((item) => ({
    label: `${item.displayName} / ${item.storeName}`,
    value: item.id
  })), [storeSkus]);
  const frameAssetOptions = useMemo(() => assets
    .filter((item) => item.assetType === 'VEHICLE_FRAME' || item.assetType === 'INTEGRATED_VEHICLE')
    .map((item) => ({ label: `${item.serialNo} / ${item.assetType === 'INTEGRATED_VEHICLE' ? '车电一体' : '车架'}`, value: item.id })), [assets]);
  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY').map((item) => ({ label: item.serialNo, value: item.id })), [assets]);
  const integratedPreviewAssetSelected = useMemo(
    () => assets.some((item) => item.id === selectedPreviewFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedPreviewFrameAssetId]
  );
  const filteredRules = useMemo(() => rules.filter((rule) => {
    if (ruleStoreFilter && rule.storeId !== ruleStoreFilter) {
      return false;
    }
    if (ruleStatusFilter && rule.status !== ruleStatusFilter) {
      return false;
    }
    if (ruleChannelFilter === 'DEFAULT' && rule.sourceChannel) {
      return false;
    }
    if (ruleChannelFilter && ruleChannelFilter !== 'DEFAULT' && rule.sourceChannel !== ruleChannelFilter) {
      return false;
    }
    return true;
  }), [ruleChannelFilter, ruleStatusFilter, ruleStoreFilter, rules]);

  useEffect(() => {
    if (integratedPreviewAssetSelected) {
      previewForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [integratedPreviewAssetSelected, previewForm]);

  async function loadAll() {
    const [ruleData, snapshotData, entryData, overviewData, statementData, storeSkuData, storeData, assetData] = await Promise.all([
      http.get<unknown, ProfitRule[]>('/api/admin/settlement/store-rules'),
      http.get<unknown, SettlementSnapshot[]>('/api/admin/settlement/snapshots'),
      http.get<unknown, SettlementIncomeEntry[]>('/api/admin/settlement/income/entries'),
      http.get<unknown, SettlementOverview>('/api/admin/settlement/statements/overview', { params: { month: statementMonth } }),
      http.get<unknown, SettlementStatement[]>('/api/admin/settlement/statements', { params: { month: statementMonth } }),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, Store[]>('/api/admin/stores'),
      http.get<unknown, Asset[]>('/api/admin/assets')
    ]);
    setRules(ruleData);
    setSnapshots(snapshotData);
    setEntries(entryData);
    setOverview(overviewData);
    setStatements(statementData);
    setStoreSkus(storeSkuData);
    setStores(storeData);
    setAssets(assetData);
  }

  async function reloadStatements(month = statementMonth) {
    const [overviewData, statementData] = await Promise.all([
      http.get<unknown, SettlementOverview>('/api/admin/settlement/statements/overview', { params: { month } }),
      http.get<unknown, SettlementStatement[]>('/api/admin/settlement/statements', { params: { month } })
    ]);
    setOverview(overviewData);
    setStatements(statementData);
  }

  function openRuleCreator() {
    setEditingRule(null);
    ruleForm.setFieldsValue({
      ruleName: '',
      storeId: undefined,
      sourceChannel: undefined,
      priority: 0,
      channelFeeRate: 5,
      platformFeeRate: 3,
      storeOperationRate: 15,
      maintenanceFundRate: 10,
      channelReferralRate: 20,
      investorShareRate: 55,
      effectiveAt: dayjs(),
      expiredAt: undefined
    });
    setRuleOpen(true);
  }

  function openRuleEditor(record: ProfitRule) {
    setEditingRule(record);
    ruleForm.setFieldsValue({
      ruleName: record.ruleName,
      storeId: record.storeId || undefined,
      sourceChannel: record.sourceChannel || undefined,
      priority: record.priority,
      channelFeeRate: toPercentValue(record.channelFeeRate),
      platformFeeRate: toPercentValue(record.platformFeeRate),
      storeOperationRate: toPercentValue(record.storeOperationRate),
      maintenanceFundRate: toPercentValue(record.maintenanceFundRate),
      channelReferralRate: toPercentValue(record.channelReferralRate),
      investorShareRate: toPercentValue(record.investorShareRate),
      effectiveAt: dayjs(record.effectiveAt),
      expiredAt: record.expiredAt ? dayjs(record.expiredAt) : undefined
    });
    setRuleOpen(true);
  }

  async function saveRule(values: RuleForm) {
    if (values.channelFeeRate + values.platformFeeRate >= 100) {
      message.error('渠道核销扣点与租赁平台扣点之和必须小于 100%');
      return;
    }
    const distributionRate = values.storeOperationRate
      + values.maintenanceFundRate
      + values.channelReferralRate
      + values.investorShareRate;
    if (Math.abs(distributionRate - 100) > 0.001) {
      message.error('门店运营、维修基金、渠道引流、出资方比例之和必须等于 100%');
      return;
    }
    if (values.expiredAt && !values.expiredAt.isAfter(values.effectiveAt)) {
      message.error('失效时间必须晚于生效时间');
      return;
    }
    const payload = {
      ruleName: values.ruleName.trim(),
      ruleScope: 'STORE',
      sourceChannel: values.sourceChannel || null,
      priority: values.priority,
      skuId: null,
      merchantId: null,
      storeId: values.storeId,
      storeSkuId: null,
      channelFeeRate: fromPercentValue(values.channelFeeRate),
      platformFeeRate: fromPercentValue(values.platformFeeRate),
      storeOperationRate: fromPercentValue(values.storeOperationRate),
      maintenanceFundRate: fromPercentValue(values.maintenanceFundRate),
      channelReferralRate: fromPercentValue(values.channelReferralRate),
      investorShareRate: fromPercentValue(values.investorShareRate),
      effectiveAt: values.effectiveAt.format('YYYY-MM-DDTHH:mm:ss'),
      expiredAt: values.expiredAt?.format('YYYY-MM-DDTHH:mm:ss') || null
    };
    if (editingRule) {
      await http.put(`/api/admin/settlement/rules/${editingRule.id}`, payload);
    } else {
      await http.post('/api/admin/settlement/rules', payload);
    }
    setRuleOpen(false);
    setEditingRule(null);
    ruleForm.resetFields();
    message.success(editingRule ? '分润规则已更新，新规则仅用于之后生成的分润快照' : '分润规则已新增');
    await loadAll();
  }

  async function updateRuleStatus(record: ProfitRule) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/settlement/rules/${record.id}/status`, null, { params: { status } });
    message.success(status === 'ENABLED' ? '分润规则已启用' : '分润规则已停用');
    await loadAll();
  }

  async function deleteRule(record: ProfitRule) {
    await http.delete(`/api/admin/settlement/rules/${record.id}`);
    message.success('分润规则已删除');
    await loadAll();
  }

  async function previewSettlement(values: PreviewForm) {
    const data = await http.post<unknown, SettlementSnapshot>('/api/admin/settlement/preview', values);
    setPreview(data);
  }

  async function createSnapshot() {
    const values = previewForm.getFieldsValue();
    const data = await http.post<unknown, SettlementSnapshot>('/api/admin/settlement/snapshots', {
      ...values,
      sourceType: 'PREVIEW'
    });
    setPreview(data);
    message.success('规则快照已生成');
    await loadAll();
  }

  async function generateIncome(values: { orderId: number }) {
    await http.post(`/api/admin/settlement/income/orders/${values.orderId}/generate`);
    message.success('收益台账已生成');
    incomeForm.resetFields();
    await loadAll();
  }

  async function updateEntryStatus(record: SettlementIncomeEntry, status: SettlementIncomeEntry['entryStatus']) {
    await http.put(`/api/admin/settlement/income/entries/${record.id}/status`, null, { params: { status } });
    message.success(status === 'SETTLED' ? '已标记结算' : '已更新状态');
    await loadAll();
  }

  async function generateStatements() {
    const result = await http.post<unknown, SettlementStatementGenerateResult>('/api/admin/settlement/statements/generate', null, {
      params: { month: statementMonth }
    });
    message.success(`已生成 ${result.merchantStatementCount} 张商户月结单，${result.investorStatementCount} 张出资方月结单`);
    await reloadStatements();
  }

  async function openStatement(record: SettlementStatement) {
    const lines = await http.get<unknown, SettlementStatementLine[]>(`/api/admin/settlement/statements/${record.id}/lines`);
    setSelectedStatement(record);
    setStatementLines(lines);
    setStatementDetailOpen(true);
  }

  async function updateStatementStatus(record: SettlementStatement, status: SettlementStatement['status']) {
    await http.put(`/api/admin/settlement/statements/${record.id}/status`, null, { params: { status } });
    message.success('月结单状态已更新');
    await reloadStatements();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>分润结算</Typography.Title>
      </Space>

      <div className="section">
        <Space align="center" className="toolbar">
          <Typography.Title level={5}>月结中心</Typography.Title>
          <Input
            type="month"
            value={statementMonth}
            onChange={(event) => setStatementMonth(event.target.value || currentMonth())}
            style={{ width: 180 }}
          />
          <Button onClick={() => void reloadStatements(statementMonth)}>刷新月结</Button>
          <Button type="primary" onClick={generateStatements}>生成当月月结单</Button>
        </Space>
        {overview && (
          <Descriptions bordered size="small" column={4} className="preview-box">
            <Descriptions.Item label="月结月份">{overview.statementMonth}</Descriptions.Item>
            <Descriptions.Item label="当月实收租金">{money(overview.totalPaidRentAmount)}</Descriptions.Item>
            <Descriptions.Item label="当月签单费">{money(overview.totalSignFeeAmount)}</Descriptions.Item>
            <Descriptions.Item label="当月逾期未收">{money(overview.totalOpenOverdueAmount)}</Descriptions.Item>
            <Descriptions.Item label="商户待结算">{money(overview.totalMerchantPayableAmount)}</Descriptions.Item>
            <Descriptions.Item label="出资方待结算">{money(overview.totalInvestorPayableAmount)}</Descriptions.Item>
            <Descriptions.Item label="运营手续费">{money(overview.totalOperationFeeAmount)}</Descriptions.Item>
            <Descriptions.Item label="维保扣减">{money(overview.totalMaintenanceDeductAmount)}</Descriptions.Item>
            <Descriptions.Item label="商户月结单数">{overview.merchantStatementCount}</Descriptions.Item>
            <Descriptions.Item label="出资方月结单数">{overview.investorStatementCount}</Descriptions.Item>
          </Descriptions>
        )}
        <Table
          rowKey="id"
          size="small"
          dataSource={statements}
          pagination={{ pageSize: 8 }}
          columns={[
            { title: '月结单号', dataIndex: 'statementNo' },
            { title: '对象', dataIndex: 'beneficiaryType', render: statementBeneficiaryText },
            { title: '对象 ID', dataIndex: 'beneficiaryId' },
            { title: '门店', dataIndex: 'storeId', render: (value, record) => record.beneficiaryType === 'MERCHANT' ? value : '-' },
            { title: '实收租金基数', dataIndex: 'rentBaseAmount', render: money },
            { title: '签单费', dataIndex: 'signFeeIncomeAmount', render: money },
            { title: '租金收益', dataIndex: 'rentShareIncomeAmount', render: money },
            { title: '运营手续费', dataIndex: 'operationFeeAmount', render: money },
            { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
            { title: '应结算金额', dataIndex: 'payableAmount', render: money },
            { title: '状态', dataIndex: 'status', render: statementStatusTag },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openStatement(record)}>明细</Button>
                  <Button size="small" disabled={record.status !== 'DRAFT'} onClick={() => updateStatementStatus(record, 'CONFIRMED')}>确认</Button>
                  <Button size="small" disabled={!['CONFIRMED', 'PAYABLE'].includes(record.status)} onClick={() => updateStatementStatus(record, 'PAID')}>标记打款</Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>规则预览</Typography.Title>
        <Form form={previewForm} layout="inline" initialValues={{ sourceChannel: 'DIRECT' }} onFinish={previewSettlement}>
          <Form.Item name="storeSkuId" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select placeholder="门店商品" options={storeSkuOptions} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="sourceChannel" rules={[{ required: true, message: '请选择来源渠道' }]}>
            <Select placeholder="来源渠道" options={sourceChannelOptions} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="frameAssetId">
            <Select allowClear placeholder="车架 / 车电一体" options={frameAssetOptions} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="batteryAssetId">
            <Select
              allowClear
              disabled={integratedPreviewAssetSelected}
              placeholder={integratedPreviewAssetSelected ? '车电一体无需独立电池' : '电池资产'}
              options={batteryAssetOptions}
              style={{ width: 190 }}
            />
          </Form.Item>
          <Form.Item name="rentalAmount" rules={[{ required: true, message: '请输入实际核销或结算金额' }]}>
            <InputNumber min={0} placeholder="核销/结算金额" style={{ width: 150 }} />
          </Form.Item>
          <Button type="primary" htmlType="submit">预览</Button>
          <Button onClick={createSnapshot} disabled={!preview}>生成快照</Button>
        </Form>
        {preview && (
          <Descriptions bordered size="small" column={3} className="preview-box">
            <Descriptions.Item label="命中规则">{preview.matchedRuleScope} / {preview.matchedRuleId}</Descriptions.Item>
            <Descriptions.Item label="来源渠道">{channelText(preview.sourceChannel)}</Descriptions.Item>
            <Descriptions.Item label="实际结算基数">{money(preview.settlementBaseAmount)}</Descriptions.Item>
            <Descriptions.Item label="渠道核销扣点">{money(preview.channelFeeAmount)} / {percent(preview.channelFeeRate)}</Descriptions.Item>
            <Descriptions.Item label="租赁平台扣点">{money(preview.platformFeeAmount)} / {percent(preview.platformFeeRate)}</Descriptions.Item>
            <Descriptions.Item label="剩余可分配">{money(preview.distributableAmount)}</Descriptions.Item>
            <Descriptions.Item label="门店运营">{money(preview.storeOperationAmount)} / {percent(preview.storeOperationRate)}</Descriptions.Item>
            <Descriptions.Item label="维修基金">{money(preview.maintenanceFundAmount)} / {percent(preview.maintenanceFundRate)}</Descriptions.Item>
            <Descriptions.Item label="渠道引流">{money(preview.channelReferralAmount)} / {percent(preview.channelReferralRate)}</Descriptions.Item>
            <Descriptions.Item label="出资方">{money(preview.investorShareAmount)} / {percent(preview.investorShareRate)}</Descriptions.Item>
            <Descriptions.Item label="快照号">{preview.snapshotNo}</Descriptions.Item>
          </Descriptions>
        )}
      </div>

      <div className="section">
        <Space align="center" className="toolbar" wrap>
          <Typography.Title level={5}>门店分润规则</Typography.Title>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="筛选门店"
            value={ruleStoreFilter}
            onChange={setRuleStoreFilter}
            options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
            style={{ width: 220 }}
          />
          <Select
            allowClear
            placeholder="筛选渠道"
            value={ruleChannelFilter}
            onChange={setRuleChannelFilter}
            options={[{ label: '全部渠道默认规则', value: 'DEFAULT' }, ...ruleChannelOptions]}
            style={{ width: 180 }}
          />
          <Select
            allowClear
            placeholder="筛选状态"
            value={ruleStatusFilter}
            onChange={setRuleStatusFilter}
            options={[
              { label: '已启用', value: 'ENABLED' },
              { label: '已停用', value: 'DISABLED' }
            ]}
            style={{ width: 130 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openRuleCreator}>新增规则</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={filteredRules}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1900 }}
          columns={[
            {
              title: '规则',
              dataIndex: 'ruleName',
              fixed: 'left',
              width: 210,
              render: (value, record) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{value}</Typography.Text>
                  <Typography.Text type="secondary">{record.ruleCode}</Typography.Text>
                </Space>
              )
            },
            {
              title: '门店',
              dataIndex: 'storeId',
              width: 180,
              render: (value) => stores.find((item) => item.id === value)?.storeName || `门店 ${value}`
            },
            {
              title: '门店编码',
              dataIndex: 'storeId',
              width: 150,
              render: (value) => stores.find((item) => item.id === value)?.storeCode || '-'
            },
            {
              title: '适用渠道',
              dataIndex: 'sourceChannel',
              width: 130,
              render: (value) => value ? channelText(value) : <Tag color="blue">全部渠道</Tag>
            },
            { title: '优先级', dataIndex: 'priority', width: 90 },
            { title: '渠道扣点', dataIndex: 'channelFeeRate', render: percent },
            { title: '平台扣点', dataIndex: 'platformFeeRate', render: percent },
            { title: '门店运营', dataIndex: 'storeOperationRate', render: percent },
            { title: '维修基金', dataIndex: 'maintenanceFundRate', render: percent },
            { title: '渠道引流', dataIndex: 'channelReferralRate', render: percent },
            { title: '出资方', dataIndex: 'investorShareRate', render: percent },
            {
              title: '有效期',
              dataIndex: 'effectiveAt',
              width: 175,
              render: (value, record) => (
                <Space direction="vertical" size={0}>
                  <span>{formatDateTime(value)}</span>
                  <Typography.Text type="secondary">{record.expiredAt ? `至 ${formatDateTime(record.expiredAt)}` : '长期有效'}</Typography.Text>
                </Space>
              )
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (value: ProfitRule['status']) => value === 'ENABLED'
                ? <Tag color="green">已启用</Tag>
                : <Tag>已停用</Tag>
            },
            {
              title: '操作',
              fixed: 'right',
              width: 260,
              render: (_, record) => (
                <Space>
                  <Button size="small" icon={<EditOutlined />} onClick={() => openRuleEditor(record)}>编辑</Button>
                  <Popconfirm
                    title={record.status === 'ENABLED' ? '确认停用这条规则？' : '确认启用这条规则？'}
                    onConfirm={() => updateRuleStatus(record)}
                  >
                    <Button size="small" icon={<PoweroffOutlined />}>
                      {record.status === 'ENABLED' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title="确认删除这条分润规则？"
                    description="已生成分润快照或作为唯一默认规则时不能删除。"
                    onConfirm={() => deleteRule(record)}
                  >
                    <Button danger size="small" icon={<DeleteOutlined />}>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>规则快照</Typography.Title>
        <Table
          rowKey={(record) => record.id || record.snapshotNo}
          size="small"
          dataSource={snapshots}
          pagination={false}
          scroll={{ x: 1200 }}
          columns={[
            { title: '快照号', dataIndex: 'snapshotNo' },
            { title: '来源', dataIndex: 'sourceType' },
            { title: '渠道', dataIndex: 'sourceChannel', render: channelText },
            { title: '版本', dataIndex: 'calculationVersion', render: (value) => value === 'PROFIT_V2' ? '新版分润' : '历史规则' },
            { title: '命中范围', dataIndex: 'matchedRuleScope' },
            { title: '结算基数', dataIndex: 'settlementBaseAmount', render: money },
            { title: '渠道扣点', dataIndex: 'channelFeeAmount', render: money },
            { title: '平台扣点', dataIndex: 'platformFeeAmount', render: money },
            { title: '门店运营', dataIndex: 'storeOperationAmount', render: money },
            { title: '维修基金', dataIndex: 'maintenanceFundAmount', render: money },
            { title: '渠道引流', dataIndex: 'channelReferralAmount', render: money },
            { title: '出资方', dataIndex: 'investorShareAmount', render: money },
            { title: '生成时间', dataIndex: 'createdAt' }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>收益台账</Typography.Title>
        <Form form={incomeForm} layout="inline" onFinish={generateIncome} className="inline-form">
          <Form.Item name="orderId" rules={[{ required: true, message: '请输入订单 ID' }]}>
            <InputNumber min={1} placeholder="订单 ID" />
          </Form.Item>
          <Button type="primary" htmlType="submit">按订单生成收益</Button>
        </Form>
        <Table
          rowKey="id"
          size="small"
          dataSource={entries}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '收益单号', dataIndex: 'entryNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '收益方', dataIndex: 'beneficiaryType', render: beneficiaryText },
            { title: '收益方 ID', dataIndex: 'beneficiaryId', render: (value) => value ?? '-' },
            { title: '类型', dataIndex: 'lineType', render: lineTypeText },
            { title: '金额', dataIndex: 'amount', render: money },
            { title: '状态', dataIndex: 'entryStatus', render: incomeStatusTag },
            { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
            { title: '结算时间', dataIndex: 'settledAt', render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" disabled={record.entryStatus === 'SETTLED'} onClick={() => updateEntryStatus(record, 'SETTLED')}>结算</Button>
                  <Button size="small" disabled={record.entryStatus === 'FROZEN'} onClick={() => updateEntryStatus(record, 'FROZEN')}>冻结</Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <Modal
        title={editingRule ? '编辑门店分润规则' : '新增门店分润规则'}
        open={ruleOpen}
        onCancel={() => {
          setRuleOpen(false);
          setEditingRule(null);
          ruleForm.resetFields();
        }}
        onOk={() => ruleForm.submit()}
        okText={editingRule ? '保存' : '新增'}
        width={820}
        styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
        destroyOnHidden
      >
        <Form form={ruleForm} layout="vertical" onFinish={saveRule}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
                <Input maxLength={128} placeholder="例如：王城大道店抖音分润规则" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="storeId" label="适用门店" rules={[{ required: true, message: '请选择门店' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择门店"
                  options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="sourceChannel" label="适用渠道">
                <Select allowClear placeholder="全部渠道" options={ruleChannelOptions} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请输入优先级' }]}>
                <InputNumber min={-10000} max={10000} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="effectiveAt" label="生效时间" rules={[{ required: true, message: '请选择生效时间' }]}>
                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="expiredAt" label="失效时间">
                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="channelFeeRate" label="渠道核销扣点 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="platformFeeRate" label="租赁平台扣点 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="storeOperationRate" label="剩余金额：门店运营 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maintenanceFundRate" label="剩余金额：维修基金 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="channelReferralRate" label="剩余金额：渠道引流 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="investorShareRate" label="剩余金额：出资方 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title={selectedStatement ? `月结单明细 - ${selectedStatement.statementNo}` : '月结单明细'}
        open={statementDetailOpen}
        onCancel={() => {
          setStatementDetailOpen(false);
          setSelectedStatement(null);
          setStatementLines([]);
        }}
        footer={null}
        width={1100}
        destroyOnHidden
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={statementLines}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '明细号', dataIndex: 'lineNo' },
            { title: '类型', dataIndex: 'lineType', render: statementLineText },
            { title: '订单', dataIndex: 'orderId', render: (value) => value ?? '-' },
            { title: '账单', dataIndex: 'billId', render: (value) => value ?? '-' },
            { title: '资产', dataIndex: 'assetId', render: (value) => value ?? '-' },
            { title: '门店', dataIndex: 'storeId', render: (value) => value || '-' },
            { title: '发生时间', dataIndex: 'occurredAt', render: (value) => value || '-' },
            { title: '金额', dataIndex: 'amount', render: signedMoney },
            { title: '备注', dataIndex: 'remark', render: (value) => value || '-' }
          ]}
        />
      </Modal>
    </Space>
  );
}

function percent(value: number) {
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function toPercentValue(value: number) {
  return Number((Number(value) * 100).toFixed(2));
}

function fromPercentValue(value: number) {
  return Number((Number(value) / 100).toFixed(4));
}

function money(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function formatDateTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

function beneficiaryText(value: SettlementIncomeEntry['beneficiaryType']) {
  const map: Record<SettlementIncomeEntry['beneficiaryType'], string> = {
    MERCHANT: '门店/商户',
    INVESTOR: '出资方',
    PLATFORM: '平台',
    CHANNEL: '渠道',
    MAINTENANCE_FUND: '维修基金'
  };
  return map[value] || value;
}

function lineTypeText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
    CHANNEL_VERIFICATION_FEE: '渠道核销扣点',
    PLATFORM_SERVICE_FEE: '租赁平台扣点',
    STORE_OPERATION_SHARE: '门店运营分润',
    MAINTENANCE_FUND_SHARE: '维修基金计提',
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

function incomeStatusTag(value: SettlementIncomeEntry['entryStatus']) {
  const color = value === 'SETTLED' ? 'green' : value === 'FROZEN' ? 'red' : 'blue';
  const label = value === 'SETTLED' ? '已结算' : value === 'FROZEN' ? '已冻结' : '待结算';
  return <Tag color={color}>{label}</Tag>;
}

function statementBeneficiaryText(value: SettlementStatement['beneficiaryType']) {
  return value === 'MERCHANT' ? '商户/门店' : '出资方';
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
  const target = map[value];
  return <Tag color={target.color}>{target.label}</Tag>;
}

function statementLineText(value: SettlementStatementLine['lineType']) {
  const map: Record<SettlementStatementLine['lineType'], string> = {
    MERCHANT_SIGN_FEE: '商户签单费',
    MERCHANT_RENT_SHARE: '商户租金分润',
    MERCHANT_MAINTENANCE_REIMBURSE: '门店配件补回',
    MERCHANT_MAINTENANCE_DEDUCT: '商户维保扣减',
    MERCHANT_ADJUSTMENT: '商户调整',
    INVESTOR_GROSS_RENT: '出资方租金毛收益',
    INVESTOR_OPERATION_FEE: '出资方运营手续费',
    INVESTOR_MAINTENANCE_DEDUCT: '出资方维保扣减',
    INVESTOR_ADJUSTMENT: '出资方调整'
  };
  return map[value] || value;
}

function signedMoney(value: number) {
  const amount = Number(value || 0);
  return amount >= 0 ? `+¥${amount.toFixed(2)}` : `-¥${Math.abs(amount).toFixed(2)}`;
}

function currentMonth() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}`;
}

function channelText(value?: string | null) {
  const map: Record<string, string> = {
    DIRECT: '平台直租',
    DOUYIN: '抖音',
    MEITUAN: '美团',
    XIANYU: '闲鱼',
    OFFLINE: '线下',
    OTHER: '其他'
  };
  return value ? (map[value] || value) : '全部渠道';
}
