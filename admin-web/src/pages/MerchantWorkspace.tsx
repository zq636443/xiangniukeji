import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tabs,
  Typography,
  message
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  AssetDetail,
  AssetMaintenance,
  AssetRentalRecord,
  Asset,
  AssetStatus,
  CollectionStatus,
  CurrentAccount,
  OrderStatus,
  OverdueCase,
  RentalBill,
  RentalOrder,
  SettlementIncomeEntry,
  SettlementStatement,
  SettlementStatementLine,
  SettlementSnapshot,
  SparePartStockLog,
  StoreSparePartStock,
  Store
} from '../types/api';

type MerchantPageProps = {
  account: CurrentAccount;
  storeId?: number;
  stores: Store[];
};

type CollectionForm = {
  collectionStatus: CollectionStatus;
  remark?: string;
};

type PickupForm = {
  frameAssetId?: number;
  batteryAssetId?: number;
  remark?: string;
};

type ReplaceForm = {
  assetType: 'VEHICLE_FRAME' | 'BATTERY';
  newAssetId: number;
  oldAssetResultStatus: AssetStatus;
  remark?: string;
};

type ReturnForm = {
  frameResultStatus: AssetStatus;
  batteryResultStatus: AssetStatus;
  remark?: string;
};

type SparePartTransferForm = {
  partId?: number;
  toStoreId?: number;
  quantity?: number;
  unitPrice?: number;
  remark?: string;
};

const orderStatusOptions: { label: string; value: OrderStatus }[] = [
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '待实名', value: 'PENDING_REAL_NAME' },
  { label: '待签约', value: 'PENDING_AGREEMENT' },
  { label: '待免押', value: 'PENDING_DEPOSIT_AUTH' },
  { label: '待核销', value: 'PENDING_VERIFY' },
  { label: '待取车', value: 'PENDING_PICKUP' },
  { label: '租赁中', value: 'RENTING' },
  { label: '待归还', value: 'PENDING_RETURN' },
  { label: '已逾期', value: 'OVERDUE' },
  { label: '待补缴', value: 'PENDING_SUPPLEMENT' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '异常', value: 'EXCEPTION' }
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

const collectionStatusOptions: { label: string; value: CollectionStatus; color: string }[] = [
  { label: '待催缴', value: 'PENDING', color: 'orange' },
  { label: '已联系', value: 'CONTACTED', color: 'blue' },
  { label: '承诺付款', value: 'PROMISED', color: 'purple' },
  { label: '已解决', value: 'RESOLVED', color: 'green' },
  { label: '坏账', value: 'BAD_DEBT', color: 'red' }
];

export function MerchantDashboard({ storeId, stores }: MerchantPageProps) {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [overdues, setOverdues] = useState<OverdueCase[]>([]);
  const [incomeEntries, setIncomeEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadData() {
    if (!storeId) {
      setOrders([]);
      setAssets([]);
      setOverdues([]);
      setIncomeEntries([]);
      setStatements([]);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [orderData, assetData, overdueData, incomeData, statementData] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/merchant/orders', { params: { storeId } }),
        http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`),
        http.get<unknown, OverdueCase[]>('/api/merchant/overdues', { params: { storeId, overdueStatus: 'OPEN' } }),
        http.get<unknown, SettlementIncomeEntry[]>('/api/merchant/settlement/income/entries', { params: { storeId } }),
        http.get<unknown, SettlementStatement[]>('/api/merchant/settlement/statements', { params: { storeId } })
      ]);
      setOrders(orderData);
      setAssets(assetData);
      setOverdues(overdueData);
      setIncomeEntries(incomeData);
      setStatements(statementData);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '商户工作台加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, [storeId]);

  const metrics = useMemo(() => ({
    pendingPickup: orders.filter((item) => item.orderStatus === 'PENDING_PICKUP').length,
    renting: orders.filter((item) => item.orderStatus === 'RENTING').length,
    overdue: overdues.length,
    idleAssets: assets.filter((item) => item.status === 'IDLE').length,
    exceptionAssets: assets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length,
    pendingIncome: incomeEntries.filter((item) => item.entryStatus === 'PENDING').reduce((sum, item) => sum + Number(item.amount || 0), 0),
    latestStatementIncome: Number(statements[0]?.payableAmount || 0)
  }), [orders, assets, overdues, incomeEntries, statements]);

  const currentStore = stores.find((item) => item.id === storeId);

  if (!storeId) {
    return <Empty description="当前账号暂无可访问门店" />;
  }

  return (
    <Space direction="vertical" size={18} className="page-stack">
      <section className="dashboard-hero">
        <div>
          <Typography.Text className="page-eyebrow">Merchant Workspace</Typography.Text>
          <Typography.Title level={3}>商户工作台</Typography.Title>
          <Typography.Text type="secondary">
            {currentStore ? `${currentStore.storeName} / 现场履约、逾期跟进、资产和收益总览` : '按当前门店查看经营数据'}
          </Typography.Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新数据</Button>
      </section>

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <Space size={16} wrap>
        <Metric title="待取车订单" value={metrics.pendingPickup} />
        <Metric title="租赁中订单" value={metrics.renting} />
        <Metric title="逾期订单" value={metrics.overdue} />
        <Metric title="空闲资产" value={metrics.idleAssets} />
        <Metric title="异常/维修资产" value={metrics.exceptionAssets} />
        <Metric title="待结算收益" value={money(metrics.pendingIncome)} />
        <Metric title="最近月结金额" value={money(metrics.latestStatementIncome)} />
      </Space>

      <div className="section">
        <Typography.Title level={5}>待处理订单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={orders.filter((item) => ['PENDING_PICKUP', 'RENTING', 'OVERDUE', 'PENDING_RETURN'].includes(item.orderStatus)).slice(0, 8)}
          pagination={false}
          columns={[
            { title: '订单号', dataIndex: 'orderNo' },
            { title: '状态', dataIndex: 'orderStatus', render: orderStatusTag },
            { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'DAY' ? '天' : '月'} / ${record.totalPeriods}期` },
            { title: '应付', dataIndex: 'payableAmount', render: money },
            { title: '创建时间', dataIndex: 'createdAt', render: dateText }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>逾期跟进</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={overdues.slice(0, 8)}
          pagination={false}
          columns={[
            { title: '案件号', dataIndex: 'caseNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '未收金额', dataIndex: 'unpaidAmount', render: money },
            { title: '失败次数', dataIndex: 'failCount' },
            { title: '催缴状态', dataIndex: 'collectionStatus', render: collectionStatusTag }
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
            { title: '签单费', dataIndex: 'signFeeIncomeAmount', render: money },
            { title: '租金分润', dataIndex: 'rentShareIncomeAmount', render: money },
            { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
            { title: '应结算', dataIndex: 'payableAmount', render: money },
            { title: '状态', dataIndex: 'status', render: statementStatusTag }
          ]}
        />
      </div>
    </Space>
  );
}

export function MerchantStoreList({ stores }: MerchantPageProps) {
  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>我的门店</Typography.Title>
        <Typography.Text type="secondary">仅展示当前账号有权限访问的门店。</Typography.Text>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          dataSource={stores}
          pagination={false}
          columns={[
            { title: '门店编码', dataIndex: 'storeCode' },
            { title: '门店名称', dataIndex: 'storeName' },
            { title: '地址', dataIndex: 'address' },
            { title: '营业时间', dataIndex: 'businessHours', render: (value?: string | null) => value || '-' },
            { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'red'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag> }
          ]}
        />
      </div>
    </Space>
  );
}

export function MerchantOrderWorkspace({ storeId }: MerchantPageProps) {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [orderStatus, setOrderStatus] = useState<OrderStatus | undefined>();
  const [selectedOrder, setSelectedOrder] = useState<RentalOrder | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [pickupOpen, setPickupOpen] = useState(false);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [bills, setBills] = useState<RentalBill[]>([]);
  const [settlement, setSettlement] = useState<SettlementSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [pickupForm] = Form.useForm<PickupForm>();
  const [replaceForm] = Form.useForm<ReplaceForm>();
  const [returnForm] = Form.useForm<ReturnForm>();
  const replaceAssetType = Form.useWatch('assetType', replaceForm);

  const frameOptions = useMemo(() => assets
    .filter((item) => item.assetType === 'VEHICLE_FRAME' && item.status === 'IDLE')
    .map((item) => ({ label: item.serialNo, value: item.id })), [assets]);

  const batteryOptions = useMemo(() => assets
    .filter((item) => item.assetType === 'BATTERY' && item.status === 'IDLE')
    .map((item) => ({ label: item.serialNo, value: item.id })), [assets]);

  const replaceAssetOptions = useMemo(() => {
    return assets
      .filter((item) => item.assetType === replaceAssetType && item.status === 'IDLE')
      .map((item) => ({ label: item.serialNo, value: item.id }));
  }, [assets, replaceAssetType]);

  async function loadAll() {
    if (!storeId) {
      setOrders([]);
      setAssets([]);
      return;
    }
    setLoading(true);
    try {
      const [orderData, assetData] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/merchant/orders', { params: { storeId, status: orderStatus } }),
        http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`)
      ]);
      setOrders(orderData);
      setAssets(assetData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAll();
  }, [storeId, orderStatus]);

  async function openDetail(record: RentalOrder) {
    setSelectedOrder(record);
    setDetailOpen(true);
    const [billData, settlementData] = await Promise.all([
      http.get<unknown, RentalBill[]>(`/api/merchant/orders/${record.id}/bills`),
      http.get<unknown, SettlementSnapshot>(`/api/merchant/orders/${record.id}/settlement`).catch(() => null)
    ]);
    setBills(billData);
    setSettlement(settlementData);
  }

  async function refreshCurrentOrder(orderId: number) {
    const latest = await http.get<unknown, RentalOrder>(`/api/merchant/orders/${orderId}`);
    setOrders((items) => items.map((item) => item.id === orderId ? latest : item));
    setSelectedOrder(latest);
    if (detailOpen) {
      const [billData, settlementData] = await Promise.all([
        http.get<unknown, RentalBill[]>(`/api/merchant/orders/${orderId}/bills`),
        http.get<unknown, SettlementSnapshot>(`/api/merchant/orders/${orderId}/settlement`).catch(() => null)
      ]);
      setBills(billData);
      setSettlement(settlementData);
    }
  }

  async function submitPickup(values: PickupForm) {
    if (!selectedOrder) {
      return;
    }
    setActionLoading(true);
    try {
      await http.post(`/api/merchant/orders/${selectedOrder.id}/pickup-assets`, {
        frameAssetId: values.frameAssetId,
        batteryAssetId: values.batteryAssetId,
        remark: values.remark || '商户 Web 取车绑定'
      });
      message.success('取车资产已绑定');
      setPickupOpen(false);
      pickupForm.resetFields();
      await Promise.all([loadAll(), refreshCurrentOrder(selectedOrder.id)]);
    } finally {
      setActionLoading(false);
    }
  }

  async function submitReplace(values: ReplaceForm) {
    if (!selectedOrder) {
      return;
    }
    setActionLoading(true);
    try {
      await http.post(`/api/merchant/orders/${selectedOrder.id}/replace-asset`, {
        assetType: values.assetType,
        newAssetId: values.newAssetId,
        oldAssetResultStatus: values.oldAssetResultStatus,
        remark: values.remark || '商户 Web 更换资产'
      });
      message.success('资产已更换');
      setReplaceOpen(false);
      replaceForm.resetFields();
      await Promise.all([loadAll(), refreshCurrentOrder(selectedOrder.id)]);
    } finally {
      setActionLoading(false);
    }
  }

  async function submitReturn(values: ReturnForm) {
    if (!selectedOrder || !storeId) {
      return;
    }
    setActionLoading(true);
    try {
      await http.post(`/api/merchant/orders/${selectedOrder.id}/return-assets`, {
        returnStoreId: storeId,
        frameResultStatus: values.frameResultStatus,
        batteryResultStatus: values.batteryResultStatus,
        remark: values.remark || '商户 Web 归还结束订单'
      });
      message.success('订单已归还结束');
      setReturnOpen(false);
      returnForm.resetFields();
      await Promise.all([loadAll(), refreshCurrentOrder(selectedOrder.id)]);
    } finally {
      setActionLoading(false);
    }
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看订单" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>门店订单</Typography.Title>
        <Select
          allowClear
          placeholder="订单状态"
          value={orderStatus}
          style={{ width: 180 }}
          options={orderStatusOptions}
          onChange={setOrderStatus}
        />
        <Button icon={<ReloadOutlined />} onClick={loadAll}>刷新</Button>
      </Space>

      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={orders}
          pagination={false}
          columns={[
            { title: '订单号', dataIndex: 'orderNo' },
            { title: '状态', dataIndex: 'orderStatus', render: orderStatusTag },
            { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'DAY' ? '天' : '月'} / ${record.totalPeriods}期` },
            { title: '车架', dataIndex: 'frameAssetId', render: (value?: number | null) => value || '-' },
            { title: '电池', dataIndex: 'batteryAssetId', render: (value?: number | null) => value || '-' },
            { title: '应付', dataIndex: 'payableAmount', render: money },
            { title: '已付', dataIndex: 'paidAmount', render: money },
            { title: '创建时间', dataIndex: 'createdAt', render: dateText },
            {
              title: '操作',
              render: (_, record) => (
                <Space wrap>
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  <Button size="small" onClick={() => {
                    setSelectedOrder(record);
                    pickupForm.setFieldsValue({ frameAssetId: record.frameAssetId ?? undefined, batteryAssetId: record.batteryAssetId ?? undefined, remark: '商户 Web 取车绑定' });
                    setPickupOpen(true);
                  }}>
                    取车
                  </Button>
                  <Button size="small" onClick={() => {
                    setSelectedOrder(record);
                    replaceForm.setFieldsValue({ assetType: 'VEHICLE_FRAME', oldAssetResultStatus: 'IDLE', remark: '商户 Web 更换资产' });
                    setReplaceOpen(true);
                  }}>
                    更换资产
                  </Button>
                  <Button size="small" danger onClick={() => {
                    setSelectedOrder(record);
                    returnForm.setFieldsValue({ frameResultStatus: 'IDLE', batteryResultStatus: 'IDLE', remark: '商户 Web 归还结束订单' });
                    setReturnOpen(true);
                  }}>
                    归还结束
                  </Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <Modal
        title="订单详情"
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setBills([]);
          setSettlement(null);
        }}
        footer={null}
        width={980}
        destroyOnHidden
      >
        {selectedOrder ? (
          <Space direction="vertical" size={16} className="page-stack">
            <Descriptions bordered size="small" column={3}>
              <Descriptions.Item label="订单号">{selectedOrder.orderNo}</Descriptions.Item>
              <Descriptions.Item label="状态">{orderStatusTag(selectedOrder.orderStatus)}</Descriptions.Item>
              <Descriptions.Item label="门店">{selectedOrder.storeId}</Descriptions.Item>
              <Descriptions.Item label="车架资产">{selectedOrder.frameAssetId || '-'}</Descriptions.Item>
              <Descriptions.Item label="电池资产">{selectedOrder.batteryAssetId || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{dateText(selectedOrder.createdAt)}</Descriptions.Item>
            </Descriptions>
            <div className="section">
              <Typography.Title level={5}>账单列表</Typography.Title>
              <Table
                rowKey="id"
                size="small"
                dataSource={bills}
                pagination={false}
                columns={[
                  { title: '账单号', dataIndex: 'billNo' },
                  { title: '期数', dataIndex: 'periodNo' },
                  { title: '状态', dataIndex: 'billStatus', render: billStatusTag },
                  { title: '应付', dataIndex: 'payableAmount', render: money },
                  { title: '到期时间', dataIndex: 'dueAt', render: dateText }
                ]}
              />
            </div>
            <div className="section">
              <Typography.Title level={5}>分润快照</Typography.Title>
              {settlement ? (
                <Descriptions bordered size="small" column={3}>
                  <Descriptions.Item label="快照号">{settlement.snapshotNo}</Descriptions.Item>
                  <Descriptions.Item label="门店收益">{money(settlement.merchantRentShareAmount)}</Descriptions.Item>
                  <Descriptions.Item label="签单费">{money(settlement.signFeeAmount)}</Descriptions.Item>
                  <Descriptions.Item label="平台收益">{money(settlement.platformRentShareAmount)}</Descriptions.Item>
                  <Descriptions.Item label="运营手续费">{money(settlement.investorOperationFeeAmount)}</Descriptions.Item>
                  <Descriptions.Item label="出资方净收益">{money(settlement.investorNetShareAmount)}</Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty description="当前订单暂无分润快照" />
              )}
            </div>
          </Space>
        ) : null}
      </Modal>

      <Modal title="取车绑定资产" open={pickupOpen} onCancel={() => setPickupOpen(false)} onOk={() => pickupForm.submit()} confirmLoading={actionLoading} destroyOnHidden>
        <Form form={pickupForm} layout="vertical" onFinish={submitPickup}>
          <Form.Item name="frameAssetId" label="车架资产">
            <Select allowClear options={frameOptions} />
          </Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产">
            <Select allowClear options={batteryOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="更换资产" open={replaceOpen} onCancel={() => setReplaceOpen(false)} onOk={() => replaceForm.submit()} confirmLoading={actionLoading} destroyOnHidden>
        <Form form={replaceForm} layout="vertical" onFinish={submitReplace}>
          <Form.Item name="assetType" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
            <Select
              options={[
                { label: '车架', value: 'VEHICLE_FRAME' },
                { label: '电池', value: 'BATTERY' }
              ]}
              onChange={() => replaceForm.setFieldValue('newAssetId', undefined)}
            />
          </Form.Item>
          <Form.Item name="newAssetId" label="新资产" rules={[{ required: true, message: '请选择新资产' }]}>
            <Select options={replaceAssetOptions} />
          </Form.Item>
          <Form.Item name="oldAssetResultStatus" label="原资产状态" rules={[{ required: true, message: '请选择原资产状态' }]}>
            <Select options={assetStatusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="归还并结束订单" open={returnOpen} onCancel={() => setReturnOpen(false)} onOk={() => returnForm.submit()} confirmLoading={actionLoading} destroyOnHidden>
        <Form form={returnForm} layout="vertical" onFinish={submitReturn}>
          <Form.Item name="frameResultStatus" label="车架归还状态" rules={[{ required: true, message: '请选择车架状态' }]}>
            <Select options={assetStatusOptions} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="电池归还状态" rules={[{ required: true, message: '请选择电池状态' }]}>
            <Select options={assetStatusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

export function MerchantAssetWorkspace({ storeId }: MerchantPageProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<AssetDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loading, setLoading] = useState(false);

  async function loadAssets() {
    if (!storeId) {
      setAssets([]);
      return;
    }
    setLoading(true);
    try {
      setAssets(await http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAssets();
  }, [storeId]);

  async function openDetail(record: Asset) {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      setSelectedAsset(await http.get<unknown, AssetDetail>(`/api/merchant/assets/${record.id}/detail`));
    } finally {
      setDetailLoading(false);
    }
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看资产" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>门店资产</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={loadAssets}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={assets}
          pagination={false}
          columns={[
            { title: '资产编码', dataIndex: 'assetCode' },
            { title: '类型', dataIndex: 'assetType', render: (value: string) => value === 'VEHICLE_FRAME' ? '车架' : '电池' },
            { title: '序列号', dataIndex: 'serialNo' },
            { title: '出资方', dataIndex: 'investorName', render: (value?: string | null) => value || '-' },
            { title: '状态', dataIndex: 'status', render: assetStatusTag },
            { title: '采购金额', dataIndex: 'purchaseAmount', render: money },
            { title: '维保费', dataIndex: 'maintenanceFeeAmount', render: money },
            { title: '残值', dataIndex: 'residualValue', render: money },
            { title: '操作', render: (_, record) => <Button size="small" onClick={() => openDetail(record)}>详情</Button> }
          ]}
        />
      </div>

      <Modal
        title={selectedAsset ? `${selectedAsset.asset.assetCode} / 资产详情` : '资产详情'}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setSelectedAsset(null);
        }}
        footer={null}
        width={1040}
      >
        {selectedAsset ? (
          <Space direction="vertical" size={16} className="page-stack">
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="资产编码">{selectedAsset.asset.assetCode}</Descriptions.Item>
              <Descriptions.Item label="资产类型">{selectedAsset.asset.assetType === 'VEHICLE_FRAME' ? '车架' : '电池'}</Descriptions.Item>
              <Descriptions.Item label="序列号">{selectedAsset.asset.serialNo}</Descriptions.Item>
              <Descriptions.Item label="当前状态">{assetStatusTag(selectedAsset.asset.status)}</Descriptions.Item>
              <Descriptions.Item label="出资方">{selectedAsset.asset.investorName || '-'}</Descriptions.Item>
              <Descriptions.Item label="所属门店">{selectedAsset.asset.storeName || '-'}</Descriptions.Item>
              <Descriptions.Item label="采购金额">{money(selectedAsset.asset.purchaseAmount)}</Descriptions.Item>
              <Descriptions.Item label="维保费">{money(selectedAsset.asset.maintenanceFeeAmount)}</Descriptions.Item>
              <Descriptions.Item label="残值">{money(selectedAsset.asset.residualValue)}</Descriptions.Item>
              <Descriptions.Item label="购入时间">{dateText(selectedAsset.asset.purchasedAt)}</Descriptions.Item>
            </Descriptions>

            <div className="section">
              <Typography.Title level={5}>租赁记录</Typography.Title>
              <Table
                rowKey={(record) => `${record.recordType}-${record.orderId}`}
                size="small"
                dataSource={selectedAsset.rentals}
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
                  { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'DAY' ? '天' : '月'} / ${record.totalPeriods}期` },
                  { title: '租金', dataIndex: 'rentalAmount', render: money },
                  { title: '签单费', dataIndex: 'signFeeAmount', render: money },
                  { title: '已付', dataIndex: 'paidAmount', render: money },
                  { title: '开始', dataIndex: 'leaseStartedAt', render: dateText },
                  { title: '应还', dataIndex: 'expectedReturnAt', render: dateText },
                  { title: '归还', dataIndex: 'returnedAt', render: dateText }
                ]}
              />
            </div>

            <div className="section">
              <Typography.Title level={5}>维修记录</Typography.Title>
              <Table
                rowKey="id"
                size="small"
                dataSource={selectedAsset.maintenances}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无维修记录" /> }}
                expandable={{
                  expandedRowRender: (record) => (
                    <Table
                      rowKey="id"
                      size="small"
                      dataSource={record.parts}
                      pagination={false}
                      locale={{ emptyText: <Empty description="未消耗配件" /> }}
                      columns={[
                        { title: '配件', dataIndex: 'partNameSnapshot' },
                        { title: '数量', dataIndex: 'quantity' },
                        { title: '单价', dataIndex: 'unitPrice', render: money },
                        { title: '金额', dataIndex: 'totalAmount', render: money },
                        { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' }
                      ]}
                    />
                  )
                }}
                columns={maintenanceColumns()}
              />
            </div>
          </Space>
        ) : (
          <Empty description={detailLoading ? '正在加载资产详情' : '暂无资产详情'} />
        )}
      </Modal>
    </Space>
  );
}

export function MerchantSparePartWorkspace({ storeId, stores }: MerchantPageProps) {
  const [stocks, setStocks] = useState<StoreSparePartStock[]>([]);
  const [logs, setLogs] = useState<SparePartStockLog[]>([]);
  const [partId, setPartId] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [transferForm] = Form.useForm<SparePartTransferForm>();

  async function loadData() {
    if (!storeId) {
      setStocks([]);
      setLogs([]);
      return;
    }
    setLoading(true);
    try {
      const [stockData, logData] = await Promise.all([
        http.get<unknown, StoreSparePartStock[]>('/api/merchant/spare-parts/store-stocks', { params: { storeId, partId } }),
        http.get<unknown, SparePartStockLog[]>('/api/merchant/spare-parts/logs', { params: { storeId, partId } })
      ]);
      setStocks(stockData);
      setLogs(logData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, [storeId, partId]);

  useEffect(() => {
    transferForm.resetFields();
  }, [storeId, transferForm]);

  const partOptions = useMemo(
    () =>
      stocks.map((item) => ({
        label: `${item.partName} / 库存 ${item.stockQuantity}`,
        value: item.partId
      })),
    [stocks]
  );

  const stockAmount = useMemo(() => stocks.reduce((sum, item) => sum + Number(item.stockAmount || 0), 0), [stocks]);
  const targetStores = useMemo(() => stores.filter((item) => item.id !== storeId), [stores, storeId]);

  async function submitTransfer(values: SparePartTransferForm) {
    if (!storeId) {
      return;
    }
    const currentPart = stocks.find((item) => item.partId === values.partId);
    if (!currentPart) {
      message.error('请选择可调拨的配件');
      return;
    }
    setSubmitting(true);
    try {
      await http.post('/api/merchant/spare-parts/transfer', {
        partId: values.partId,
        fromStoreId: storeId,
        toStoreId: values.toStoreId,
        quantity: values.quantity,
        unitPrice: values.unitPrice ?? Number(currentPart.avgUnitPrice || 0),
        remark: values.remark || undefined
      });
      message.success('配件调拨已提交');
      transferForm.resetFields();
      setPartId(undefined);
      await loadData();
    } finally {
      setSubmitting(false);
    }
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看配件" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>门店配件</Typography.Title>
        <Space>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="筛选配件"
            style={{ width: 220 }}
            value={partId}
            options={partOptions}
            onChange={setPartId}
          />
          <Tag color="blue">库存金额 {money(stockAmount)}</Tag>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
        </Space>
      </Space>

      <section className="section">
        <Tabs
          items={[
            {
              key: 'stocks',
              label: '当前库存',
              children: (
                <Table
                  rowKey={(record) => `${record.storeId}-${record.partId}`}
                  size="small"
                  loading={loading}
                  dataSource={stocks}
                  pagination={false}
                  locale={{ emptyText: <Empty description="当前门店暂无配件库存" /> }}
                  columns={[
                    { title: '配件', dataIndex: 'partName' },
                    { title: '库存数量', dataIndex: 'stockQuantity', render: (value: number) => <Tag color={value <= 0 ? 'red' : value < 5 ? 'gold' : 'green'}>{value}</Tag> },
                    { title: '入仓均价', dataIndex: 'avgUnitPrice', render: money },
                    { title: '库存金额', dataIndex: 'stockAmount', render: money }
                  ]}
                />
              )
            },
            {
              key: 'logs',
              label: '库存流水',
              children: (
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={logs}
                  pagination={false}
                  locale={{ emptyText: <Empty description="当前门店暂无配件流水" /> }}
                  columns={[
                    { title: '类型', dataIndex: 'changeType', render: stockTypeTag },
                    { title: '配件', dataIndex: 'partName' },
                    { title: '数量变化', dataIndex: 'quantityChange' },
                    { title: '单价', dataIndex: 'unitPrice', render: money },
                    { title: '金额', dataIndex: 'amount', render: money },
                    { title: '关联', render: (_, record) => `${record.refType || '-'} ${record.refId || ''}` },
                    { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
                    { title: '时间', dataIndex: 'createdAt', render: dateText }
                  ]}
                />
              )
            },
            {
              key: 'transfer',
              label: '配件调拨',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Alert
                    showIcon
                    type="info"
                    message="调拨统一在商户后台发起，用于同一商户下门店之间的配件流转。提交后会同步生成调出和调入门店的库存流水。"
                  />
                  {targetStores.length === 0 ? (
                    <Empty description="当前商户仅有一个门店，暂无可调拨目标" />
                  ) : (
                    <div className="section">
                      <Form form={transferForm} layout="vertical" onFinish={submitTransfer}>
                        <Form.Item name="partId" label="调拨配件" rules={[{ required: true, message: '请选择调拨配件' }]}>
                          <Select
                            showSearch
                            optionFilterProp="label"
                            placeholder="请选择当前门店库存中的配件"
                            options={partOptions}
                            onChange={(value) => {
                              const currentPart = stocks.find((item) => item.partId === value);
                              transferForm.setFieldValue('unitPrice', currentPart ? Number(currentPart.avgUnitPrice || 0) : undefined);
                            }}
                          />
                        </Form.Item>
                        <Form.Item name="toStoreId" label="调入门店" rules={[{ required: true, message: '请选择调入门店' }]}>
                          <Select
                            showSearch
                            optionFilterProp="label"
                            placeholder="请选择同商户下的目标门店"
                            options={targetStores.map((item) => ({
                              label: `${item.storeName} / ${item.storeCode}`,
                              value: item.id
                            }))}
                          />
                        </Form.Item>
                        <Space size={16} wrap>
                          <Form.Item name="quantity" label="调拨数量" rules={[{ required: true, message: '请输入调拨数量' }]}>
                            <InputNumber min={1} precision={0} style={{ width: 220 }} placeholder="请输入数量" />
                          </Form.Item>
                          <Form.Item name="unitPrice" label="调拨单价" rules={[{ required: true, message: '请输入调拨单价' }]}>
                            <InputNumber min={0} precision={2} style={{ width: 220 }} placeholder="默认带出当前门店均价" />
                          </Form.Item>
                        </Space>
                        <Form.Item name="remark" label="备注">
                          <Input placeholder="如：A 店调拨给 B 店用于当日维修" />
                        </Form.Item>
                        <Space>
                          <Button type="primary" loading={submitting} onClick={() => transferForm.submit()}>
                            发起调拨
                          </Button>
                          <Button onClick={() => transferForm.resetFields()}>
                            重置
                          </Button>
                        </Space>
                      </Form>
                    </div>
                  )}
                </Space>
              )
            }
          ]}
        />
      </section>
    </Space>
  );
}

export function MerchantMaintenanceWorkspace({ storeId }: MerchantPageProps) {
  const [records, setRecords] = useState<AssetMaintenance[]>([]);
  const [loading, setLoading] = useState(false);

  async function loadRecords() {
    if (!storeId) {
      setRecords([]);
      return;
    }
    setLoading(true);
    try {
      setRecords(await http.get<unknown, AssetMaintenance[]>('/api/merchant/maintenances', { params: { storeId } }));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadRecords();
  }, [storeId]);

  if (!storeId) {
    return <Empty description="请选择门店后查看维修记录" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>维修记录</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={loadRecords}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={records}
          pagination={false}
          locale={{ emptyText: <Empty description="当前门店暂无维修记录" /> }}
          expandable={{
            expandedRowRender: (record) => (
              <Table
                rowKey="id"
                size="small"
                dataSource={record.parts}
                pagination={false}
                locale={{ emptyText: <Empty description="未消耗配件" /> }}
                columns={[
                  { title: '配件', dataIndex: 'partNameSnapshot' },
                  { title: '数量', dataIndex: 'quantity' },
                  { title: '单价', dataIndex: 'unitPrice', render: money },
                  { title: '金额', dataIndex: 'totalAmount', render: money },
                  { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' }
                ]}
              />
            )
          }}
          columns={maintenanceColumns()}
        />
      </div>
    </Space>
  );
}

export function MerchantOverdueWorkspace({ storeId }: MerchantPageProps) {
  const [cases, setCases] = useState<OverdueCase[]>([]);
  const [selectedCase, setSelectedCase] = useState<OverdueCase | null>(null);
  const [loading, setLoading] = useState(false);
  const [collectionOpen, setCollectionOpen] = useState(false);
  const [collectionForm] = Form.useForm<CollectionForm>();

  async function loadCases() {
    if (!storeId) {
      setCases([]);
      return;
    }
    setLoading(true);
    try {
      setCases(await http.get<unknown, OverdueCase[]>('/api/merchant/overdues', { params: { storeId } }));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadCases();
  }, [storeId]);

  async function updateCollection(values: CollectionForm) {
    if (!selectedCase) {
      return;
    }
    await http.post(`/api/merchant/overdues/${selectedCase.id}/collection`, values);
    message.success('催缴状态已更新');
    setCollectionOpen(false);
    collectionForm.resetFields();
    await loadCases();
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看逾期订单" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>逾期订单</Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={loadCases}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={cases}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Table
                rowKey="id"
                size="small"
                dataSource={record.logs}
                pagination={false}
                columns={[
                  { title: '催缴状态', dataIndex: 'collectionStatus', render: collectionStatusTag },
                  { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
                  { title: '操作人', dataIndex: 'operatorAccountId', render: (value?: number | null) => value || '-' },
                  { title: '时间', dataIndex: 'createdAt', render: dateText }
                ]}
              />
            )
          }}
          columns={[
            { title: '案件号', dataIndex: 'caseNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '未收金额', dataIndex: 'unpaidAmount', render: money },
            { title: '失败次数', dataIndex: 'failCount' },
            { title: '逾期状态', dataIndex: 'overdueStatus', render: (value: string) => <Tag>{value}</Tag> },
            { title: '催缴状态', dataIndex: 'collectionStatus', render: collectionStatusTag },
            {
              title: '操作',
              render: (_, record) => (
                <Button size="small" onClick={() => {
                  setSelectedCase(record);
                  collectionForm.setFieldsValue({
                    collectionStatus: record.collectionStatus,
                    remark: record.collectionRemark || undefined
                  });
                  setCollectionOpen(true);
                }}>
                  催缴处理
                </Button>
              )
            }
          ]}
        />
      </div>

      <Modal title="催缴处理" open={collectionOpen} onCancel={() => setCollectionOpen(false)} onOk={() => collectionForm.submit()} destroyOnHidden>
        <Form form={collectionForm} layout="vertical" onFinish={updateCollection}>
          <Form.Item name="collectionStatus" label="催缴状态" rules={[{ required: true, message: '请选择催缴状态' }]}>
            <Select options={collectionStatusOptions.map(({ label, value }) => ({ label, value }))} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

export function MerchantIncomeWorkspace({ storeId }: MerchantPageProps) {
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [statementLines, setStatementLines] = useState<SettlementStatementLine[]>([]);
  const [selectedStatement, setSelectedStatement] = useState<SettlementStatement | null>(null);
  const [statementOpen, setStatementOpen] = useState(false);
  const [status, setStatus] = useState<SettlementIncomeEntry['entryStatus'] | undefined>();
  const [statementStatus, setStatementStatus] = useState<SettlementStatement['status'] | undefined>();
  const [loading, setLoading] = useState(false);

  async function loadEntries() {
    if (!storeId) {
      setEntries([]);
      setStatements([]);
      return;
    }
    setLoading(true);
    try {
      const [entryData, statementData] = await Promise.all([
        http.get<unknown, SettlementIncomeEntry[]>('/api/merchant/settlement/income/entries', { params: { storeId, status } }),
        http.get<unknown, SettlementStatement[]>('/api/merchant/settlement/statements', { params: { storeId, status: statementStatus } })
      ]);
      setEntries(entryData);
      setStatements(statementData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadEntries();
  }, [storeId, status, statementStatus]);

  async function openStatement(record: SettlementStatement) {
    setSelectedStatement(record);
    setStatementOpen(true);
    setStatementLines(await http.get<unknown, SettlementStatementLine[]>(`/api/merchant/settlement/statements/${record.id}/lines`));
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看收益" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>门店收益</Typography.Title>
        <Select
          allowClear
          placeholder="收益状态"
          value={status}
          style={{ width: 160 }}
          options={[
            { label: '待结算', value: 'PENDING' },
            { label: '已结算', value: 'SETTLED' },
            { label: '已冻结', value: 'FROZEN' }
          ]}
          onChange={setStatus}
        />
        <Button icon={<ReloadOutlined />} onClick={loadEntries}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={entries}
          pagination={false}
          columns={[
            { title: '收益单号', dataIndex: 'entryNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '收益类型', dataIndex: 'lineType', render: incomeLineText },
            { title: '金额', dataIndex: 'amount', render: money },
            { title: '状态', dataIndex: 'entryStatus', render: incomeStatusTag },
            { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
            { title: '时间', dataIndex: 'createdAt', render: dateText }
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
            { title: '签单费', dataIndex: 'signFeeIncomeAmount', render: money },
            { title: '租金分润', dataIndex: 'rentShareIncomeAmount', render: money },
            { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
            { title: '应结算', dataIndex: 'payableAmount', render: money },
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

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function orderStatusTag(value: OrderStatus) {
  const colorMap: Partial<Record<OrderStatus, string>> = {
    PENDING_PICKUP: 'blue',
    RENTING: 'green',
    OVERDUE: 'red',
    PENDING_RETURN: 'orange',
    COMPLETED: 'default',
    EXCEPTION: 'volcano'
  };
  const label = orderStatusOptions.find((item) => item.value === value)?.label ?? value;
  return <Tag color={colorMap[value]}>{label}</Tag>;
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
        { title: '状态', dataIndex: 'billStatus', render: billStatusTag },
        { title: '应付', dataIndex: 'payableAmount', render: money },
        { title: '已付', dataIndex: 'paidAmount', render: money },
        { title: '逾期', dataIndex: 'overdueAmount', render: money },
        { title: '到期', dataIndex: 'dueAt', render: dateText }
      ]}
    />
  );
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

function collectionStatusTag(value: CollectionStatus) {
  const item = collectionStatusOptions.find((option) => option.value === value);
  return <Tag color={item?.color}>{item?.label || value}</Tag>;
}

function assetStatusTag(value: AssetStatus) {
  const label = assetStatusOptions.find((item) => item.value === value)?.label ?? value;
  const color = value === 'IDLE' ? 'blue' : value === 'RENTING' ? 'green' : ['PENDING_REPAIR', 'REPAIRING'].includes(value) ? 'orange' : value === 'EXCEPTION' ? 'red' : 'default';
  return <Tag color={color}>{label}</Tag>;
}

function maintenanceColumns() {
  return [
    { title: '维修单号', dataIndex: 'maintenanceNo' },
    { title: '资产编码', dataIndex: 'assetCode' },
    { title: '资产类型', dataIndex: 'assetType', render: (value: Asset['assetType']) => value === 'VEHICLE_FRAME' ? '车架' : '电池' },
    { title: '维修类型', dataIndex: 'maintenanceType' },
    { title: '归因', dataIndex: 'responsibilityType', render: responsibilityText },
    { title: '配件成本', dataIndex: 'partsCost', render: money },
    { title: '人工+外协', render: (_: unknown, record: AssetMaintenance) => money(Number(record.laborCost || 0) + Number(record.externalCost || 0)) },
    { title: '总成本', dataIndex: 'totalCost', render: money },
    { title: '补门店', dataIndex: 'merchantReimbursementAmount', render: money },
    { title: '扣出资方', dataIndex: 'investorDeductAmount', render: money },
    { title: '状态', dataIndex: 'maintenanceStatus', render: (value: string) => <Tag>{value}</Tag> },
    { title: '完成时间', dataIndex: 'completedAt', render: dateText }
  ];
}

function stockTypeTag(value: SparePartStockLog['changeType']) {
  const map = {
    INBOUND: { text: '历史入库', color: 'green' },
    CONSUME: { text: '历史消耗', color: 'red' },
    ADJUST: { text: '历史调整', color: 'blue' },
    PLATFORM_INBOUND: { text: '平台入库', color: 'green' },
    PLATFORM_ADJUST: { text: '平台调整', color: 'blue' },
    STORE_PURCHASE_OUT: { text: '门店采购出库', color: 'purple' },
    STORE_PURCHASE_IN: { text: '门店采购入库', color: 'purple' },
    STORE_BUYBACK_OUT: { text: '门店退仓出库', color: 'orange' },
    STORE_BUYBACK_IN: { text: '平台回收入库', color: 'orange' },
    STORE_CONSUME: { text: '维修消耗', color: 'red' },
    STORE_ADJUST: { text: '门店调整', color: 'cyan' },
    STORE_TRANSFER_OUT: { text: '门店调拨出库', color: 'magenta' },
    STORE_TRANSFER_IN: { text: '门店调拨入库', color: 'geekblue' }
  }[value];
  return <Tag color={map.color}>{map.text}</Tag>;
}

function responsibilityText(value: AssetMaintenance['responsibilityType']) {
  const map: Record<AssetMaintenance['responsibilityType'], string> = {
    ROUTINE_MAINTENANCE: '日常维保',
    CUSTOMER_DAMAGE: '客户损坏',
    MERCHANT_RESPONSIBILITY: '商户责任',
    PLATFORM_SUBSIDY: '平台补贴'
  };
  return map[value] || value;
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

function incomeLineText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
    MERCHANT_ORDER_FEE: '签单费',
    MERCHANT_RENT_SHARE: '租金分成',
    PLATFORM_RENT_SHARE: '平台租金分成',
    PLATFORM_OPERATION_FEE: '运营手续费',
    MAINTENANCE_FEE: '维保费',
    INVESTOR_NET_RENT: '出资方净收益'
  };
  return map[value] || value;
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
