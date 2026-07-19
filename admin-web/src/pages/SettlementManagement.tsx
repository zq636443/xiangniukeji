import { Button, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  ProfitRule,
  ProductSku,
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
  ruleScope: 'PLATFORM' | 'SKU' | 'STORE' | 'STORE_SKU';
  skuId?: number;
  storeId?: number;
  storeSkuId?: number;
  merchantOrderFeeAmount: number;
  merchantRentShareRate: number;
  platformRentShareRate: number;
  investorRentShareRate: number;
};

type PreviewForm = {
  storeSkuId: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  rentalAmount: number;
};

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
  const [skus, setSkus] = useState<ProductSku[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [preview, setPreview] = useState<SettlementSnapshot | null>(null);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [ruleForm] = Form.useForm<RuleForm>();
  const [previewForm] = Form.useForm<PreviewForm>();
  const [incomeForm] = Form.useForm<{ orderId: number }>();

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
  const skuOptions = useMemo(() => skus.map((item) => ({ label: item.skuName, value: item.id })), [skus]);
  const storeOptions = useMemo(() => stores.map((item) => ({ label: item.storeName, value: item.id })), [stores]);
  const frameAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'VEHICLE_FRAME').map((item) => ({ label: item.serialNo, value: item.id })), [assets]);
  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY').map((item) => ({ label: item.serialNo, value: item.id })), [assets]);

  async function loadAll() {
    const [ruleData, snapshotData, entryData, overviewData, statementData, storeSkuData, skuData, storeData, assetData] = await Promise.all([
      http.get<unknown, ProfitRule[]>('/api/admin/settlement/rules'),
      http.get<unknown, SettlementSnapshot[]>('/api/admin/settlement/snapshots'),
      http.get<unknown, SettlementIncomeEntry[]>('/api/admin/settlement/income/entries'),
      http.get<unknown, SettlementOverview>('/api/admin/settlement/statements/overview', { params: { month: statementMonth } }),
      http.get<unknown, SettlementStatement[]>('/api/admin/settlement/statements', { params: { month: statementMonth } }),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, ProductSku[]>('/api/admin/products/skus'),
      http.get<unknown, Store[]>('/api/admin/stores'),
      http.get<unknown, Asset[]>('/api/admin/assets')
    ]);
    setRules(ruleData);
    setSnapshots(snapshotData);
    setEntries(entryData);
    setOverview(overviewData);
    setStatements(statementData);
    setStoreSkus(storeSkuData);
    setSkus(skuData);
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

  async function createRule(values: RuleForm) {
    const store = stores.find((item) => item.id === values.storeId);
    const storeSku = storeSkus.find((item) => item.id === values.storeSkuId);
    await http.post('/api/admin/settlement/rules', {
      ...values,
      merchantId: storeSku?.merchantId || store?.merchantId,
      effectiveAt: new Date().toISOString()
    });
    setRuleOpen(false);
    ruleForm.resetFields();
    message.success('分润规则已创建');
    await loadAll();
  }

  async function toggleRule(record: ProfitRule) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/settlement/rules/${record.id}/status`, null, { params: { status } });
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
        <Button type="primary" onClick={() => {
          ruleForm.setFieldsValue({
            ruleScope: 'STORE_SKU',
            merchantOrderFeeAmount: 0,
            merchantRentShareRate: 0.2,
            platformRentShareRate: 0.1,
            investorRentShareRate: 0.7
          });
          setRuleOpen(true);
        }}>新建规则</Button>
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
        <Form form={previewForm} layout="inline" onFinish={previewSettlement}>
          <Form.Item name="storeSkuId" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select placeholder="门店商品" options={storeSkuOptions} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="frameAssetId">
            <Select allowClear placeholder="车架资产" options={frameAssetOptions} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="batteryAssetId">
            <Select allowClear placeholder="电池资产" options={batteryAssetOptions} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="rentalAmount" rules={[{ required: true, message: '请输入租金' }]}>
            <InputNumber min={0} placeholder="租金" />
          </Form.Item>
          <Button type="primary" htmlType="submit">预览</Button>
          <Button onClick={createSnapshot} disabled={!preview}>生成快照</Button>
        </Form>
        {preview && (
          <Descriptions bordered size="small" column={3} className="preview-box">
            <Descriptions.Item label="命中规则">{preview.matchedRuleScope} / {preview.matchedRuleId}</Descriptions.Item>
            <Descriptions.Item label="租金">{preview.rentalAmount}</Descriptions.Item>
            <Descriptions.Item label="签单费">{preview.signFeeAmount}</Descriptions.Item>
            <Descriptions.Item label="门店收益">{preview.merchantRentShareAmount}</Descriptions.Item>
            <Descriptions.Item label="平台收益">{preview.platformRentShareAmount}</Descriptions.Item>
            <Descriptions.Item label="出资方净收益">{preview.investorNetShareAmount}</Descriptions.Item>
            <Descriptions.Item label="运营手续费">{preview.investorOperationFeeAmount}</Descriptions.Item>
            <Descriptions.Item label="维保费">{preview.maintenanceFeeAmount}</Descriptions.Item>
            <Descriptions.Item label="快照号">{preview.snapshotNo}</Descriptions.Item>
          </Descriptions>
        )}
      </div>

      <div className="section">
        <Typography.Title level={5}>分润规则</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={rules}
          pagination={false}
          columns={[
            { title: '编码', dataIndex: 'ruleCode' },
            { title: '名称', dataIndex: 'ruleName' },
            { title: '范围', dataIndex: 'ruleScope' },
            { title: '办单费', dataIndex: 'merchantOrderFeeAmount' },
            { title: '门店比例', dataIndex: 'merchantRentShareRate', render: percent },
            { title: '平台比例', dataIndex: 'platformRentShareRate', render: percent },
            { title: '出资方比例', dataIndex: 'investorRentShareRate', render: percent },
            { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'ENABLED' ? 'green' : 'red'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag> },
            { title: '操作', render: (_, record) => <Button size="small" onClick={() => toggleRule(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button> }
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
          columns={[
            { title: '快照号', dataIndex: 'snapshotNo' },
            { title: '来源', dataIndex: 'sourceType' },
            { title: '命中范围', dataIndex: 'matchedRuleScope' },
            { title: '租金', dataIndex: 'rentalAmount' },
            { title: '门店收益', dataIndex: 'merchantRentShareAmount' },
            { title: '平台收益', dataIndex: 'platformRentShareAmount' },
            { title: '出资方净收益', dataIndex: 'investorNetShareAmount' },
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

      <Modal title="新建分润规则" open={ruleOpen} onCancel={() => setRuleOpen(false)} onOk={() => ruleForm.submit()} destroyOnHidden>
        <Form form={ruleForm} layout="vertical" onFinish={createRule}>
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}><Input /></Form.Item>
          <Form.Item name="ruleScope" label="规则范围" rules={[{ required: true, message: '请选择规则范围' }]}>
            <Select options={[
              { label: '平台默认', value: 'PLATFORM' },
              { label: 'SKU 默认', value: 'SKU' },
              { label: '门店默认', value: 'STORE' },
              { label: '门店 SKU', value: 'STORE_SKU' }
            ]} />
          </Form.Item>
          <Form.Item name="skuId" label="SKU"><Select allowClear options={skuOptions} /></Form.Item>
          <Form.Item name="storeId" label="门店"><Select allowClear options={storeOptions} /></Form.Item>
          <Form.Item name="storeSkuId" label="门店商品"><Select allowClear options={storeSkuOptions} /></Form.Item>
          <Form.Item name="merchantOrderFeeAmount" label="门店办单费" rules={[{ required: true, message: '请输入门店办单费' }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="merchantRentShareRate" label="门店租金分成比例" rules={[{ required: true, message: '请输入比例' }]}><InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="platformRentShareRate" label="平台租金分成比例" rules={[{ required: true, message: '请输入比例' }]}><InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="investorRentShareRate" label="出资方收益比例" rules={[{ required: true, message: '请输入比例' }]}><InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} /></Form.Item>
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

function money(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function beneficiaryText(value: SettlementIncomeEntry['beneficiaryType']) {
  const map: Record<SettlementIncomeEntry['beneficiaryType'], string> = {
    MERCHANT: '门店/商户',
    INVESTOR: '出资方',
    PLATFORM: '平台'
  };
  return map[value] || value;
}

function lineTypeText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
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
