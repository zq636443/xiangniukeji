import { DownloadOutlined, ExportOutlined, PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { AssetBatchImportModal, downloadAssetImportTemplate } from '../components/AssetBatchImportModal';
import { http } from '../services/request';
import type { Asset, AssetDetail, AssetLog, AssetMaintenance, AssetRentalRecord, AssetStatus, AssetType, CurrentAccount, Investor, Merchant, SparePart, Store } from '../types/api';
import { downloadCsv } from '../utils/csv';

const assetTypeOptions: { label: string; value: AssetType }[] = [
  { label: '车架', value: 'VEHICLE_FRAME' },
  { label: '电池', value: 'BATTERY' },
  { label: '车电一体', value: 'INTEGRATED_VEHICLE' }
];

const assetStatusOptions: { label: string; value: AssetStatus }[] = [
  { label: '空闲', value: 'IDLE' },
  { label: '租赁中', value: 'RENTING' },
  { label: '待检修', value: 'PENDING_REPAIR' },
  { label: '维修中', value: 'REPAIRING' },
  { label: '已报废', value: 'SCRAPPED' },
  { label: '已售出', value: 'SOLD' },
  { label: '异常', value: 'EXCEPTION' }
];

type InvestorForm = {
  investorName: string;
  contactName: string;
  contactPhone: string;
  operationFeeRate: number;
  createAccount?: boolean;
  username?: string;
  displayName?: string;
  phone?: string;
  password?: string;
};

type AssetForm = {
  assetType: AssetType;
  serialNo: string;
  investorId: number;
  currentMerchantId?: number;
  currentStoreId?: number;
  purchaseAmount: number;
  residualValue?: number;
  purchasedAt?: string;
};

type TransferForm = {
  merchantId: number;
  storeId: number;
  remark?: string;
};

type StatusForm = {
  status: AssetStatus;
  remark?: string;
};

type InvestorChangeForm = {
  investorId: number;
  remark?: string;
};

type AssetFilterForm = {
  keyword?: string;
  assetType?: AssetType;
  status?: AssetStatus;
  investorId?: number;
  merchantId?: number;
  storeId?: number;
};

type MaintenanceForm = {
  assetId: number;
  orderId?: number;
  storeId?: number;
  maintenanceType: string;
  maintenanceStatus?: string;
  responsibilityType?: 'ROUTINE_MAINTENANCE' | 'CUSTOMER_DAMAGE' | 'MERCHANT_RESPONSIBILITY' | 'PLATFORM_SUBSIDY';
  costBearerType?: 'USER' | 'INVESTOR' | 'MERCHANT' | 'PLATFORM';
  costBearerId?: number;
  laborCost?: number;
  externalCost?: number;
  remark?: string;
  parts?: { partId?: number; quantity?: number; unitPrice?: number; remark?: string }[];
};

type AssetManagementProps = {
  account: CurrentAccount;
  mode?: 'all' | 'investors' | 'assets';
};

export function AssetManagement({ account, mode = 'all' }: AssetManagementProps) {
  const [investors, setInvestors] = useState<Investor[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [spareParts, setSpareParts] = useState<SparePart[]>([]);
  const [logs, setLogs] = useState<AssetLog[]>([]);
  const [assetDetail, setAssetDetail] = useState<AssetDetail | null>(null);
  const [editingInvestor, setEditingInvestor] = useState<Investor | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<Asset | null>(null);
  const [investorOpen, setInvestorOpen] = useState(false);
  const [assetOpen, setAssetOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const [investorChangeOpen, setInvestorChangeOpen] = useState(false);
  const [logsOpen, setLogsOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [maintenanceOpen, setMaintenanceOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [investorForm] = Form.useForm<InvestorForm>();
  const [assetForm] = Form.useForm<AssetForm>();
  const [transferForm] = Form.useForm<TransferForm>();
  const [statusForm] = Form.useForm<StatusForm>();
  const [investorChangeForm] = Form.useForm<InvestorChangeForm>();
  const [maintenanceForm] = Form.useForm<MaintenanceForm>();
  const [assetFilterForm] = Form.useForm<AssetFilterForm>();
  const showInvestors = account.accountType !== 'INVESTOR' && mode !== 'assets';
  const showAssets = mode !== 'investors';
  const canImportAssets = account.permissions.includes('asset.import') || account.permissions.includes('system.admin');

  useEffect(() => {
    void loadAll({});
  }, []);

  const investorOptions = useMemo(() => investors.map((investor) => ({
    label: investor.investorName,
    value: investor.id
  })), [investors]);

  const merchantOptions = useMemo(() => merchants.map((merchant) => ({
    label: merchant.merchantName,
    value: merchant.id
  })), [merchants]);

  const storeOptions = useMemo(() => stores.map((store) => ({
    label: `${store.storeName} / ${store.storeCode}`,
    value: store.id
  })), [stores]);

  const sparePartOptions = useMemo(() => spareParts.map((part) => ({
    label: `${part.partName}${part.spec ? ` / ${part.spec}` : ''} / 库存 ${part.stockQuantity}`,
    value: part.id
  })), [spareParts]);

  async function loadAll(filters: AssetFilterForm = assetFilterForm.getFieldsValue()) {
    setLoading(true);
    try {
      if (account.accountType === 'INVESTOR') {
        const assetData = await http.get<unknown, Asset[]>('/api/investor/assets');
        setAssets(filterAssetRows(assetData, filters));
        return;
      }
      const [investorData, merchantData, storeData, assetData, partData] = await Promise.all([
        account.permissions.includes('investor.read') || account.permissions.includes('system.admin') ? http.get<unknown, Investor[]>('/api/admin/investors') : Promise.resolve([]),
        account.permissions.includes('merchant.read') || account.permissions.includes('system.admin') ? http.get<unknown, Merchant[]>('/api/admin/merchants') : Promise.resolve([]),
        account.permissions.includes('store.read') || account.permissions.includes('system.admin') ? http.get<unknown, Store[]>('/api/admin/stores') : Promise.resolve([]),
        http.get<unknown, Asset[]>('/api/admin/assets', { params: filters }),
        account.permissions.includes('inventory.read') || account.permissions.includes('system.admin') ? http.get<unknown, SparePart[]>('/api/admin/spare-parts') : Promise.resolve([])
      ]);
      setInvestors(investorData);
      setMerchants(merchantData);
      setStores(storeData);
      setAssets(assetData);
      setSpareParts(partData);
    } finally {
      setLoading(false);
    }
  }

  async function resetAssetFilters() {
    assetFilterForm.resetFields();
    await loadAll({});
  }

  function exportAssets() {
    downloadCsv('资产台账', [
      '序号',
      '资产编码',
      '资产类型',
      '车架号/电池号',
      '出资方',
      '商户',
      '门店',
      '状态',
      '采购金额',
      '报废残值',
      '采购日期'
    ], assets.map((asset, index) => [
      index + 1,
      asset.assetCode,
      typeLabel(asset.assetType),
      asset.serialNo,
      asset.investorName,
      asset.merchantName,
      asset.storeName,
      assetStatusText(asset.status),
      asset.purchaseAmount,
      asset.residualValue,
      asset.purchasedAt
    ]));
  }

  function openCreateInvestor() {
    setEditingInvestor(null);
    investorForm.resetFields();
    investorForm.setFieldsValue({ operationFeeRate: 0.08, createAccount: true, password: 'Xniu@2026' });
    setInvestorOpen(true);
  }

  function openEditInvestor(record: Investor) {
    setEditingInvestor(record);
    investorForm.setFieldsValue(record);
    setInvestorOpen(true);
  }

  function openCreateAsset() {
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetType: 'VEHICLE_FRAME',
      purchaseAmount: 0
    });
    setAssetOpen(true);
  }

  function openTransfer(record: Asset) {
    setSelectedAsset(record);
    transferForm.setFieldsValue({
      merchantId: record.currentMerchantId ?? undefined,
      storeId: record.currentStoreId ?? undefined,
      remark: '门店调拨'
    });
    setTransferOpen(true);
  }

  function openStatus(record: Asset) {
    setSelectedAsset(record);
    statusForm.setFieldsValue({ status: record.status, remark: '状态变更' });
    setStatusOpen(true);
  }

  function openInvestorChange(record: Asset) {
    setSelectedAsset(record);
    investorChangeForm.setFieldsValue({ investorId: record.investorId, remark: '变更出资方' });
    setInvestorChangeOpen(true);
  }

  async function openLogs(record: Asset) {
    setSelectedAsset(record);
    const data = await http.get<unknown, AssetLog[]>(`/api/admin/assets/${record.id}/logs`);
    setLogs(data);
    setLogsOpen(true);
  }

  async function openDetail(record: Asset) {
    setSelectedAsset(record);
    const data = await http.get<unknown, AssetDetail>(`/api/admin/assets/${record.id}/detail`);
    setAssetDetail(data);
    setDetailOpen(true);
  }

  function openMaintenance(record: Asset) {
    setSelectedAsset(record);
    maintenanceForm.resetFields();
    maintenanceForm.setFieldsValue({
      assetId: record.id,
      storeId: record.currentStoreId ?? undefined,
      maintenanceType: 'REPAIR',
      maintenanceStatus: 'COMPLETED',
      responsibilityType: 'ROUTINE_MAINTENANCE',
      costBearerType: 'INVESTOR',
      laborCost: 0,
      externalCost: 0,
      parts: []
    });
    setMaintenanceOpen(true);
  }

  async function submitInvestor(values: InvestorForm) {
    if (editingInvestor) {
      await http.put(`/api/admin/investors/${editingInvestor.id}`, values);
      message.success('出资方已更新');
    } else {
      await http.post('/api/admin/investors', values);
      message.success('出资方已创建');
    }
    setInvestorOpen(false);
    await loadAll();
  }

  async function toggleInvestorStatus(record: Investor) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/investors/${record.id}/status`, null, { params: { status } });
    await loadAll();
  }

  async function submitAsset(values: AssetForm) {
    await http.post('/api/admin/assets', values);
    setAssetOpen(false);
    message.success('资产已入库');
    await loadAll();
  }

  async function submitTransfer(values: TransferForm) {
    if (!selectedAsset) return;
    await http.put(`/api/admin/assets/${selectedAsset.id}/transfer`, values);
    setTransferOpen(false);
    message.success('资产已调拨');
    await loadAll();
  }

  async function submitStatus(values: StatusForm) {
    if (!selectedAsset) return;
    await http.put(`/api/admin/assets/${selectedAsset.id}/status`, values);
    setStatusOpen(false);
    message.success('资产状态已更新');
    await loadAll();
  }

  async function submitInvestorChange(values: InvestorChangeForm) {
    if (!selectedAsset) return;
    await http.put(`/api/admin/assets/${selectedAsset.id}/investor`, values);
    setInvestorChangeOpen(false);
    message.success('资产出资方已变更');
    await loadAll();
  }

  async function submitMaintenance(values: MaintenanceForm) {
    await http.post('/api/admin/maintenances', {
      ...values,
      parts: (values.parts || []).filter((item) => item.partId && item.quantity)
    });
    setMaintenanceOpen(false);
    message.success('维修记录已登记，配件库存已扣减');
    await loadAll();
    if (selectedAsset && detailOpen) {
      await openDetail(selectedAsset);
    }
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>{mode === 'investors' ? '出资方管理' : mode === 'assets' ? '资产台账' : '资产管理'}</Typography.Title>
        {showInvestors && <Button type="primary" icon={<PlusOutlined />} onClick={openCreateInvestor}>新建出资方</Button>}
        {showAssets && account.accountType !== 'INVESTOR' ? (
          <>
            <Button icon={<DownloadOutlined />} onClick={() => downloadAssetImportTemplate()}>下载模板</Button>
            {canImportAssets ? <Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>批量录入</Button> : null}
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAsset}>资产入库</Button>
          </>
        ) : null}
      </Space>

      {showInvestors && <div className="section">
        <Typography.Title level={5}>出资方</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={investors}
          loading={loading}
          pagination={false}
          columns={[
            { title: '编码', dataIndex: 'investorCode' },
            { title: '名称', dataIndex: 'investorName' },
            { title: '联系人', dataIndex: 'contactName' },
            { title: '电话', dataIndex: 'contactPhone' },
            { title: '运营手续费', dataIndex: 'operationFeeRate', render: (rate: number) => `${(rate * 100).toFixed(2)}%` },
            { title: '状态', dataIndex: 'status', render: enabledTag },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openEditInvestor(record)}>编辑</Button>
                  <Button size="small" onClick={() => toggleInvestorStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
                </Space>
              )
            }
          ]}
        />
      </div>}

      {showAssets && <div className="section">
        <Space align="center" className="toolbar" wrap>
          <Typography.Title level={5}>{account.accountType === 'INVESTOR' ? '我的资产' : '资产台账'}</Typography.Title>
          <Form form={assetFilterForm} layout="inline" onFinish={(values) => void loadAll(values)}>
            <Form.Item name="keyword">
              <Input allowClear prefix={<SearchOutlined />} placeholder="资产编码或车架/电池号" style={{ width: 230 }} />
            </Form.Item>
            <Form.Item name="assetType">
              <Select allowClear placeholder="资产类型" options={assetTypeOptions} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="status">
              <Select allowClear placeholder="资产状态" options={assetStatusOptions} style={{ width: 140 }} />
            </Form.Item>
            {account.accountType !== 'INVESTOR' ? (
              <>
                <Form.Item name="investorId">
                  <Select allowClear showSearch optionFilterProp="label" placeholder="出资方" options={investorOptions} style={{ width: 160 }} />
                </Form.Item>
                <Form.Item name="merchantId">
                  <Select allowClear showSearch optionFilterProp="label" placeholder="商户" options={merchantOptions} style={{ width: 160 }} />
                </Form.Item>
                <Form.Item name="storeId">
                  <Select allowClear showSearch optionFilterProp="label" placeholder="门店" options={storeOptions} style={{ width: 180 }} />
                </Form.Item>
              </>
            ) : null}
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
            <Button onClick={() => void resetAssetFilters()}>重置</Button>
          </Form>
          <Button icon={<ExportOutlined />} disabled={!assets.length} onClick={exportAssets}>导出资产</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={assets}
          pagination={false}
          scroll={{ x: 1200 }}
          columns={[
            { title: '序号', width: 70, render: (_value, _record, index) => index + 1 },
            { title: '资产编码', dataIndex: 'assetCode' },
            { title: '类型', dataIndex: 'assetType', render: typeLabel },
            { title: '车架号 / 电池号', dataIndex: 'serialNo' },
            { title: '出资方', dataIndex: 'investorName' },
            { title: '所在门店', dataIndex: 'storeName', render: (value) => value || '-' },
            { title: '状态', dataIndex: 'status', render: statusTag },
            { title: '残值', dataIndex: 'residualValue', render: optionalMoney },
            {
              title: '操作',
              fixed: 'right',
              render: (_, record) => account.accountType === 'INVESTOR' ? '-' : (
                <Space>
                  <Button size="small" onClick={() => openTransfer(record)}>调拨</Button>
                  <Button size="small" onClick={() => openStatus(record)}>状态</Button>
                  <Button size="small" onClick={() => openInvestorChange(record)}>出资方</Button>
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  <Button size="small" onClick={() => openMaintenance(record)}>维修</Button>
                  <Button size="small" onClick={() => openLogs(record)}>日志</Button>
                </Space>
              )
            }
          ]}
        />
      </div>}

      <Modal
        title={editingInvestor ? '编辑出资方' : '新建出资方'}
        open={investorOpen}
        onCancel={() => {
          investorForm.resetFields();
          setEditingInvestor(null);
          setInvestorOpen(false);
        }}
        onOk={() => investorForm.submit()}
        destroyOnHidden
      >
        <Form
          form={investorForm}
          layout="vertical"
          onFinish={submitInvestor}
          onValuesChange={(changedValues) => {
            if (Object.prototype.hasOwnProperty.call(changedValues, 'createAccount') && !changedValues.createAccount) {
              investorForm.setFieldsValue({
                username: undefined,
                displayName: undefined,
                phone: undefined,
                password: undefined
              });
            }
          }}
        >
          <Form.Item name="investorName" label="出资方名称" rules={[{ required: true, message: '请输入出资方名称' }]}><Input /></Form.Item>
          <Form.Item name="contactName" label="联系人" rules={[{ required: true, message: '请输入联系人' }]}><Input /></Form.Item>
          <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]}><Input /></Form.Item>
          <Form.Item name="operationFeeRate" label="运营手续费比例" rules={[{ required: true, message: '请输入运营手续费比例' }]}>
            <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          {!editingInvestor ? (
            <>
              <Form.Item name="createAccount" valuePropName="checked">
                <Checkbox>同步创建出资方账号</Checkbox>
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.createAccount !== next.createAccount}>
                {({ getFieldValue }) => getFieldValue('createAccount') ? (
                  <>
                    <Form.Item preserve={false} name="username" label="登录账号" rules={[{ required: true, message: '请输入登录账号' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="phone" label="账号手机号" rules={[{ required: true, message: '请输入账号手机号' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }]}>
                      <Input.Password />
                    </Form.Item>
                  </>
                ) : null}
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Modal title="资产入库" open={assetOpen} onCancel={() => setAssetOpen(false)} onOk={() => assetForm.submit()} forceRender>
        <Form form={assetForm} layout="vertical" onFinish={submitAsset}>
          <Form.Item name="assetType" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}><Select options={assetTypeOptions} /></Form.Item>
          <Form.Item noStyle shouldUpdate={(previous, current) => previous.assetType !== current.assetType}>
            {({ getFieldValue }) => {
              const assetType = getFieldValue('assetType') as AssetType | undefined;
              return (
                <Form.Item
                  name="serialNo"
                  label={assetType === 'BATTERY' ? '电池号' : '车架号'}
                  rules={[{ required: true, message: assetType === 'BATTERY' ? '请输入电池号' : '请输入车架号' }]}
                >
                  <Input placeholder={assetType === 'INTEGRATED_VEHICLE' ? '车电一体仅录入车架号' : undefined} />
                </Form.Item>
              );
            }}
          </Form.Item>
          <Form.Item name="investorId" label="出资方" rules={[{ required: true, message: '请选择出资方' }]}><Select options={investorOptions} /></Form.Item>
          <Form.Item name="currentMerchantId" label="商户"><Select allowClear options={merchantOptions} /></Form.Item>
          <Form.Item name="currentStoreId" label="门店"><Select allowClear options={storeOptions} /></Form.Item>
          <Form.Item name="purchaseAmount" label="采购金额" rules={[{ required: true, message: '请输入采购金额' }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="residualValue" label="报废残值"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="purchasedAt" label="采购日期"><Input placeholder="2026-06-25" /></Form.Item>
        </Form>
      </Modal>

      <AssetBatchImportModal
        open={batchImportOpen}
        endpoint="/api/admin/assets/batch-import"
        onClose={() => setBatchImportOpen(false)}
        onImported={loadAll}
      />

      <Modal title="资产调拨" open={transferOpen} onCancel={() => setTransferOpen(false)} onOk={() => transferForm.submit()} destroyOnHidden>
        <Form form={transferForm} layout="vertical" onFinish={submitTransfer}>
          <Form.Item name="merchantId" label="调拨到商户" rules={[{ required: true, message: '请选择商户' }]}><Select options={merchantOptions} /></Form.Item>
          <Form.Item name="storeId" label="调拨到门店" rules={[{ required: true, message: '请选择门店' }]}><Select options={storeOptions} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="状态变更" open={statusOpen} onCancel={() => setStatusOpen(false)} onOk={() => statusForm.submit()} destroyOnHidden>
        <Form form={statusForm} layout="vertical" onFinish={submitStatus}>
          <Form.Item name="status" label="资产状态" rules={[{ required: true, message: '请选择状态' }]}><Select options={assetStatusOptions} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="变更出资方" open={investorChangeOpen} onCancel={() => setInvestorChangeOpen(false)} onOk={() => investorChangeForm.submit()} destroyOnHidden>
        <Form form={investorChangeForm} layout="vertical" onFinish={submitInvestorChange}>
          <Form.Item name="investorId" label="新出资方" rules={[{ required: true, message: '请选择出资方' }]}><Select options={investorOptions} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`${selectedAsset?.assetCode ?? ''} / 资产日志`} open={logsOpen} onCancel={() => setLogsOpen(false)} footer={null} width={760}>
        <Table
          rowKey={(record) => `${record.logType}-${record.id}`}
          size="small"
          dataSource={logs}
          pagination={false}
          columns={[
            { title: '类型', dataIndex: 'logType' },
            { title: '原值', dataIndex: 'fromValue', render: (value) => value || '-' },
            { title: '新值', dataIndex: 'toValue', render: (value) => value || '-' },
            { title: '备注', dataIndex: 'remark' },
            { title: '时间', dataIndex: 'createdAt' }
          ]}
        />
      </Modal>

      <Modal title={`${selectedAsset?.assetCode ?? ''} / 资产详情`} open={detailOpen} onCancel={() => setDetailOpen(false)} footer={null} width={1120}>
        {assetDetail && <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <section className="section">
            <Typography.Title level={5}>基础信息</Typography.Title>
            <Space wrap>
              <Tag>{typeLabel(assetDetail.asset.assetType)}</Tag>
              {statusTag(assetDetail.asset.status)}
              <Typography.Text>编号：{assetDetail.asset.serialNo}</Typography.Text>
              <Typography.Text>出资方：{assetDetail.asset.investorName || '-'}</Typography.Text>
              <Typography.Text>门店：{assetDetail.asset.storeName || '-'}</Typography.Text>
              <Typography.Text>残值：{optionalMoney(assetDetail.asset.residualValue)}</Typography.Text>
            </Space>
          </section>
          <section className="section">
            <Typography.Title level={5}>租赁记录</Typography.Title>
            <Table
              rowKey={(record) => `${record.recordType}-${record.orderId}`}
              size="small"
              dataSource={assetDetail.rentals}
              pagination={false}
              expandable={{ expandedRowRender: rentalBillsTable, rowExpandable: (record) => record.bills.length > 0 }}
              columns={[
                { title: '订单号', dataIndex: 'orderNo' },
                { title: '记录类型', dataIndex: 'recordType', render: rentalRecordTypeTag },
                { title: '来源', dataIndex: 'sourcePlatform', render: rentalSourceText },
                { title: '状态', dataIndex: 'orderStatus', render: rentalOrderStatusTag },
                { title: '外部单号', dataIndex: 'externalOrderNo', render: (value) => value || '-' },
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
              columns={[
                { title: '维修单', dataIndex: 'maintenanceNo' },
                { title: '类型', dataIndex: 'maintenanceType', render: maintenanceTypeText },
                { title: '归因', dataIndex: 'responsibilityType', render: responsibilityTypeText },
                { title: '状态', dataIndex: 'maintenanceStatus' },
                { title: '配件费', dataIndex: 'partsCost', render: money },
                { title: '人工费', dataIndex: 'laborCost', render: money },
                { title: '外协费', dataIndex: 'externalCost', render: money },
                { title: '总费用', dataIndex: 'totalCost', render: money },
                { title: '补门店', dataIndex: 'merchantReimbursementAmount', render: money },
                { title: '扣出资方', dataIndex: 'investorDeductAmount', render: money },
                { title: '承担方', dataIndex: 'costBearerType', render: costBearerText },
                { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
                { title: '时间', dataIndex: 'createdAt', render: dateText }
              ]}
            />
          </section>
        </Space>}
      </Modal>

      <Modal title={`${selectedAsset?.assetCode ?? ''} / 登记维修`} open={maintenanceOpen} onCancel={() => setMaintenanceOpen(false)} onOk={() => maintenanceForm.submit()} width={860} destroyOnHidden>
        <Form form={maintenanceForm} layout="vertical" onFinish={submitMaintenance}>
          <Form.Item name="assetId" hidden><InputNumber /></Form.Item>
          <Form.Item name="storeId" hidden><InputNumber /></Form.Item>
          <Form.Item name="orderId" label="关联订单ID">
            <InputNumber min={1} placeholder="如维修需要绑定某笔租赁订单，可填写" style={{ width: '100%' }} />
          </Form.Item>
          <Space style={{ width: '100%' }} size={12}>
            <Form.Item name="maintenanceType" label="维修类型" rules={[{ required: true, message: '请选择维修类型' }]} style={{ flex: 1 }}>
              <Select options={[
                { label: '维修', value: 'REPAIR' },
                { label: '保养', value: 'MAINTENANCE' },
                { label: '换件', value: 'REPLACE_PART' },
                { label: '检测', value: 'INSPECTION' }
              ]} />
            </Form.Item>
            <Form.Item name="responsibilityType" label="责任归因" rules={[{ required: true, message: '请选择责任归因' }]} style={{ flex: 1 }}>
              <Select options={[
                { label: '日常资产维护', value: 'ROUTINE_MAINTENANCE' },
                { label: '客户损坏', value: 'CUSTOMER_DAMAGE' },
                { label: '门店责任', value: 'MERCHANT_RESPONSIBILITY' },
                { label: '平台兜底', value: 'PLATFORM_SUBSIDY' }
              ]} />
            </Form.Item>
            <Form.Item name="maintenanceStatus" label="状态" style={{ flex: 1 }}>
              <Select options={[
                { label: '已完成', value: 'COMPLETED' },
                { label: '处理中', value: 'PROCESSING' },
                { label: '待处理', value: 'PENDING' }
              ]} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={12}>
            <Form.Item name="costBearerType" label="成本承担方" rules={[{ required: true, message: '请选择成本承担方' }]} style={{ flex: 1 }}>
              <Select options={[
                { label: '出资方', value: 'INVESTOR' },
                { label: '商户', value: 'MERCHANT' },
                { label: '用户', value: 'USER' },
                { label: '平台', value: 'PLATFORM' }
              ]} />
            </Form.Item>
            <Form.Item name="costBearerId" label="承担方ID" style={{ flex: 1 }}>
              <InputNumber min={0} placeholder="默认自动取资产/订单归属" style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={12}>
            <Form.Item name="laborCost" label="人工费" style={{ flex: 1 }}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="externalCost" label="外协费" style={{ flex: 1 }}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          </Space>
          <Form.List name="parts">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space align="center" className="toolbar">
                  <Typography.Title level={5}>消耗配件</Typography.Title>
                  <Button size="small" onClick={() => add({ quantity: 1 })}>添加配件</Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} align="start" style={{ width: '100%' }}>
                    <Form.Item name={[field.name, 'partId']} rules={[{ required: true, message: '请选择配件' }]} style={{ width: 280 }}>
                      <Select placeholder="配件" options={sparePartOptions} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: '数量' }]} style={{ width: 120 }}>
                      <InputNumber min={1} placeholder="数量" style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'unitPrice']} style={{ width: 140 }}>
                      <InputNumber min={0} precision={2} placeholder="单价可空" style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'remark']} style={{ flex: 1 }}>
                      <Input placeholder="备注" />
                    </Form.Item>
                    <Button danger onClick={() => remove(field.name)}>删除</Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="维修说明"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function enabledTag(status: 'ENABLED' | 'DISABLED') {
  return <Tag color={status === 'ENABLED' ? 'green' : 'red'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function statusTag(status: AssetStatus) {
  return <Tag>{assetStatusText(status)}</Tag>;
}

function assetStatusText(status: AssetStatus) {
  return assetStatusOptions.find((item) => item.value === status)?.label ?? status;
}

function typeLabel(type: AssetType) {
  return assetTypeOptions.find((item) => item.value === type)?.label ?? type;
}

function filterAssetRows(rows: Asset[], filters: AssetFilterForm) {
  const keyword = filters.keyword?.trim().toLowerCase();
  return rows.filter((asset) => {
    if (filters.assetType && asset.assetType !== filters.assetType) return false;
    if (filters.status && asset.status !== filters.status) return false;
    if (filters.investorId && asset.investorId !== filters.investorId) return false;
    if (filters.merchantId && asset.currentMerchantId !== filters.merchantId) return false;
    if (filters.storeId && asset.currentStoreId !== filters.storeId) return false;
    if (!keyword) return true;
    return asset.assetCode.toLowerCase().includes(keyword) || asset.serialNo.toLowerCase().includes(keyword);
  });
}

function money(value?: number | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function optionalMoney(value?: number | null) {
  return value == null ? '-' : money(value);
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
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

function rentalOrderStatusTag(value: AssetRentalRecord['orderStatus']) {
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

function responsibilityTypeText(value?: AssetMaintenance['responsibilityType'] | null) {
  const map: Record<string, string> = {
    ROUTINE_MAINTENANCE: '日常资产维护',
    CUSTOMER_DAMAGE: '客户损坏',
    MERCHANT_RESPONSIBILITY: '门店责任',
    PLATFORM_SUBSIDY: '平台兜底'
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
