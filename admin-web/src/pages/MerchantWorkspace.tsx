import { DeleteOutlined, DownloadOutlined, EditOutlined, ExportOutlined, GiftOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tabs,
  Typography,
  message
} from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { AssetBatchImportModal, downloadAssetImportTemplate } from '../components/AssetBatchImportModal';
import { OrderBatchImportModal, OrderImportTemplateButton } from '../components/OrderBatchImportModal';
import { http } from '../services/request';
import { downloadCsv } from '../utils/csv';
import type {
  AssetDetail,
  AssetInvestorOption,
  AssetMaintenance,
  AssetRentalRecord,
  Asset,
  AssetStatus,
  AssetTypeDefinition,
  CollectionStatus,
  CurrentAccount,
  ExternalRentalOrder,
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
  Store,
  StoreSku
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

type LeaseBonusForm = {
  bonusType: 'REVIEW' | 'CAMPAIGN';
  bonusDays: number;
  remark?: string;
};

type CreateOrderForm = {
  userAccountId?: number;
  customerName: string;
  customerPhone: string;
  storeSkuId: number;
  packageId: number;
  leaseMultiplier: number;
  verificationAmount: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  orderedAt: Dayjs;
};

type SparePartTransferForm = {
  partId?: number;
  toStoreId?: number;
  quantity?: number;
  unitPrice?: number;
  remark?: string;
};

type MerchantAssetForm = {
  assetTypeId: number;
  serialNo: string;
  investorId: number;
  purchaseAmount: number;
  residualValue?: number;
  purchasedAt?: string;
};

type MerchantMaintenanceForm = {
  orderId?: number;
  maintenanceType: string;
  maintenanceStatus?: string;
  responsibilityType: 'ROUTINE_MAINTENANCE' | 'CUSTOMER_DAMAGE' | 'MERCHANT_RESPONSIBILITY' | 'PLATFORM_SUBSIDY';
  costBearerType: 'USER' | 'MERCHANT' | 'PLATFORM';
  costBearerId?: number;
  laborCost?: number;
  externalCost?: number;
  remark?: string;
  parts?: { partId?: number; quantity?: number; unitPrice?: number; remark?: string }[];
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

const assetResultStatusOptions = assetStatusOptions.filter((item) =>
  ['IDLE', 'PENDING_REPAIR', 'SCRAPPED', 'EXCEPTION'].includes(item.value)
);

const collectionStatusOptions: { label: string; value: CollectionStatus; color: string }[] = [
  { label: '待催缴', value: 'PENDING', color: 'orange' },
  { label: '已联系', value: 'CONTACTED', color: 'blue' },
  { label: '承诺付款', value: 'PROMISED', color: 'purple' },
  { label: '已解决', value: 'RESOLVED', color: 'green' },
  { label: '坏账', value: 'BAD_DEBT', color: 'red' }
];

export function MerchantDashboard({ account, storeId, stores }: MerchantPageProps) {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [externalOrders, setExternalOrders] = useState<ExternalRentalOrder[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [overdues, setOverdues] = useState<OverdueCase[]>([]);
  const [incomeEntries, setIncomeEntries] = useState<SettlementIncomeEntry[]>([]);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const canReadSettlement = account.permissions.includes('settlement.read') || account.permissions.includes('system.admin');

  async function loadData() {
    if (!storeId) {
      setOrders([]);
      setExternalOrders([]);
      setAssets([]);
      setOverdues([]);
      setIncomeEntries([]);
      setStatements([]);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [orderData, externalOrderData, assetData, overdueData, incomeData, statementData] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/merchant/orders', { params: { storeId } }),
        http.get<unknown, ExternalRentalOrder[]>('/api/merchant/external-orders', { params: { storeId } }),
        http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`),
        http.get<unknown, OverdueCase[]>('/api/merchant/overdues', { params: { storeId, overdueStatus: 'OPEN' } }),
        canReadSettlement
          ? http.get<unknown, SettlementIncomeEntry[]>('/api/merchant/settlement/income/entries', { params: { storeId } })
          : Promise.resolve([]),
        canReadSettlement
          ? http.get<unknown, SettlementStatement[]>('/api/merchant/settlement/statements', { params: { storeId } })
          : Promise.resolve([])
      ]);
      setOrders(orderData);
      setExternalOrders(externalOrderData);
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
  }, [storeId, canReadSettlement]);

  const metrics = useMemo(() => ({
    pendingPickup: orders.filter((item) => item.orderStatus === 'PENDING_PICKUP').length,
    renting: orders.filter((item) => item.orderStatus === 'RENTING').length
      + externalOrders.filter((item) => item.orderStatus === 'ACTIVE').length,
    externalCount: externalOrders.length,
    externalCollected: externalOrders.reduce((sum, item) => sum + Number(item.verificationAmount || 0), 0),
    overdue: overdues.length,
    idleAssets: assets.filter((item) => item.status === 'IDLE').length,
    exceptionAssets: assets.filter((item) => ['PENDING_REPAIR', 'REPAIRING', 'EXCEPTION'].includes(item.status)).length,
    pendingIncome: incomeEntries.filter((item) => item.entryStatus === 'PENDING').reduce((sum, item) => sum + Number(item.amount || 0), 0),
    latestStatementIncome: Number(statements[0]?.payableAmount || 0)
  }), [orders, externalOrders, assets, overdues, incomeEntries, statements]);

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
            {currentStore
              ? `${currentStore.storeName} / 现场履约、逾期跟进、资产${canReadSettlement ? '和收益' : ''}总览`
              : '按当前门店查看经营数据'}
          </Typography.Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新数据</Button>
      </section>

      {error ? <Alert type="error" message={error} showIcon /> : null}

      <Space size={16} wrap>
        <Metric title="待取车订单" value={metrics.pendingPickup} />
        <Metric title="租赁中订单（含补录）" value={metrics.renting} />
        <Metric title="补录订单" value={metrics.externalCount} />
        <Metric title="补录核销金额" value={money(metrics.externalCollected)} />
        <Metric title="逾期订单" value={metrics.overdue} />
        <Metric title="空闲资产" value={metrics.idleAssets} />
        <Metric title="异常/维修资产" value={metrics.exceptionAssets} />
        {canReadSettlement ? <Metric title="待结算收益" value={money(metrics.pendingIncome)} /> : null}
        {canReadSettlement ? <Metric title="最近月结金额" value={money(metrics.latestStatementIncome)} /> : null}
      </Space>

      <div className="section">
        <Typography.Title level={5}>最近补录订单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={externalOrders.slice(0, 8)}
          pagination={false}
          columns={[
            { title: '补录单号', dataIndex: 'recordNo' },
            { title: '客户', dataIndex: 'customerName' },
            { title: '状态', dataIndex: 'orderStatus', render: externalOrderStatusTag },
            { title: '实际核销金额', dataIndex: 'verificationAmount', render: money },
            { title: '起租时间', dataIndex: 'rentStartedAt', render: dateText },
            { title: '预计归还', dataIndex: 'expectedReturnAt', render: dateText }
          ]}
        />
      </div>

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

      {canReadSettlement ? (
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
              { title: '运营及维修分润', dataIndex: 'rentShareIncomeAmount', render: money },
              { title: '维保扣减', dataIndex: 'maintenanceDeductAmount', render: money },
              { title: '应结算', dataIndex: 'payableAmount', render: money },
              { title: '状态', dataIndex: 'status', render: statementStatusTag }
            ]}
          />
        </div>
      ) : null}
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

export function MerchantOrderWorkspace({ account, storeId, stores }: MerchantPageProps) {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [orderStatus, setOrderStatus] = useState<OrderStatus | undefined>();
  const [orderKeyword, setOrderKeyword] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<RentalOrder | null>(null);
  const [editingOrder, setEditingOrder] = useState<RentalOrder | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [leaseBonusOpen, setLeaseBonusOpen] = useState(false);
  const [pickupOpen, setPickupOpen] = useState(false);
  const [pickupMode, setPickupMode] = useState<'PICKUP' | 'SHIP'>('PICKUP');
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [bills, setBills] = useState<RentalBill[]>([]);
  const [settlement, setSettlement] = useState<SettlementSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [createForm] = Form.useForm<CreateOrderForm>();
  const [leaseBonusForm] = Form.useForm<LeaseBonusForm>();
  const [pickupForm] = Form.useForm<PickupForm>();
  const [replaceForm] = Form.useForm<ReplaceForm>();
  const [returnForm] = Form.useForm<ReturnForm>();
  const selectedStoreSkuId = Form.useWatch('storeSkuId', createForm);
  const selectedPackageId = Form.useWatch('packageId', createForm);
  const selectedLeaseMultiplier = Form.useWatch('leaseMultiplier', createForm) ?? 1;
  const selectedCreateFrameAssetId = Form.useWatch('frameAssetId', createForm);
  const selectedCreateBatteryAssetId = Form.useWatch('batteryAssetId', createForm);
  const selectedPickupFrameAssetId = Form.useWatch('frameAssetId', pickupForm);
  const selectedPickupBatteryAssetId = Form.useWatch('batteryAssetId', pickupForm);
  const replaceAssetType = Form.useWatch('assetType', replaceForm);
  const canCreateOrder = account.permissions.includes('order.create');
  const canOperateOrder = account.permissions.includes('order.operate');
  const currentStore = stores.find((item) => item.id === storeId);
  const selectedStoreSku = useMemo(
    () => storeSkus.find((item) => item.id === selectedStoreSkuId),
    [storeSkus, selectedStoreSkuId]
  );
  const selectedPackage = useMemo(
    () => selectedStoreSku?.packages.find((item) => item.packageId === selectedPackageId),
    [selectedPackageId, selectedStoreSku]
  );

  const storeSkuOptions = useMemo(() => storeSkus
    .filter((item) => item.status === 'ON_SHELF' || item.id === editingOrder?.storeSkuId)
    .map((item) => ({
    label: item.displayName,
    value: item.id
  })), [editingOrder, storeSkus]);

  const packageOptions = useMemo(() => (selectedStoreSku?.packages ?? [])
    .filter((item) => item.status === 'ENABLED' || item.packageId === editingOrder?.packageId)
    .map((item) => ({
      label: `${item.packageName} / ${money(item.rentalAmount)}`,
      value: item.packageId
    })), [editingOrder, selectedStoreSku]);

  const createFrameOptions = useMemo(() => {
    const batteryInvestorId = assets.find((item) => item.id === selectedCreateBatteryAssetId)?.investorId;
    return assets
      .filter((item) => item.assetType !== 'BATTERY'
        && (item.status === 'IDLE' || item.id === editingOrder?.frameAssetId)
        && item.currentStoreId === storeId
        && (batteryInvestorId == null || item.investorId === batteryInvestorId))
      .map((item) => ({ label: assetSelectLabel(item), value: item.id }));
  }, [assets, editingOrder, selectedCreateBatteryAssetId, storeId]);

  const createBatteryOptions = useMemo(() => {
    const frameInvestorId = assets.find((item) => item.id === selectedCreateFrameAssetId)?.investorId;
    return assets
      .filter((item) => item.assetType === 'BATTERY'
        && (item.status === 'IDLE' || item.id === editingOrder?.batteryAssetId)
        && item.currentStoreId === storeId
        && (frameInvestorId == null || item.investorId === frameInvestorId))
      .map((item) => ({ label: assetSelectLabel(item), value: item.id }));
  }, [assets, editingOrder, selectedCreateFrameAssetId, storeId]);

  const pickupFrameOptions = useMemo(() => {
    const batteryInvestorId = assets.find((item) => item.id === selectedPickupBatteryAssetId)?.investorId;
    return assets
      .filter((item) => item.assetType !== 'BATTERY'
        && item.status === 'IDLE'
        && (batteryInvestorId == null || item.investorId === batteryInvestorId))
      .map((item) => ({ label: assetSelectLabel(item), value: item.id }));
  }, [assets, selectedPickupBatteryAssetId]);

  const pickupBatteryOptions = useMemo(() => {
    const frameInvestorId = assets.find((item) => item.id === selectedPickupFrameAssetId)?.investorId;
    return assets
      .filter((item) => item.assetType === 'BATTERY'
        && item.status === 'IDLE'
        && (frameInvestorId == null || item.investorId === frameInvestorId))
      .map((item) => ({ label: assetSelectLabel(item), value: item.id }));
  }, [assets, selectedPickupFrameAssetId]);

  const replaceAssetOptions = useMemo(() => {
    const orderInvestorId = assets.find((item) => item.id === selectedOrder?.frameAssetId)?.investorId
      ?? assets.find((item) => item.id === selectedOrder?.batteryAssetId)?.investorId;
    return assets
      .filter((item) => {
        const typeMatches = replaceAssetType === 'VEHICLE_FRAME'
          ? item.assetType !== 'BATTERY'
          : item.assetType === 'BATTERY';
        return typeMatches
          && item.status === 'IDLE'
          && (orderInvestorId == null || item.investorId === orderInvestorId);
      })
      .map((item) => ({ label: assetSelectLabel(item), value: item.id }));
  }, [assets, replaceAssetType, selectedOrder]);

  const integratedCreateAssetSelected = useMemo(
    () => assets.some((item) => item.id === selectedCreateFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedCreateFrameAssetId]
  );
  useEffect(() => {
    if (integratedCreateAssetSelected) {
      createForm.setFieldValue('batteryAssetId', undefined);
      return;
    }
    const frameInvestorId = assets.find((item) => item.id === selectedCreateFrameAssetId)?.investorId;
    const batteryInvestorId = assets.find((item) => item.id === selectedCreateBatteryAssetId)?.investorId;
    if (frameInvestorId != null && batteryInvestorId != null && frameInvestorId !== batteryInvestorId) {
      createForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [assets, createForm, integratedCreateAssetSelected, selectedCreateBatteryAssetId, selectedCreateFrameAssetId]);

  useEffect(() => {
    const frameAsset = assets.find((item) => item.id === selectedPickupFrameAssetId);
    if (frameAsset?.assetType === 'INTEGRATED_VEHICLE') {
      pickupForm.setFieldValue('batteryAssetId', undefined);
      return;
    }
    const batteryInvestorId = assets.find((item) => item.id === selectedPickupBatteryAssetId)?.investorId;
    if (frameAsset?.investorId != null && batteryInvestorId != null && frameAsset.investorId !== batteryInvestorId) {
      pickupForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [assets, pickupForm, selectedPickupBatteryAssetId, selectedPickupFrameAssetId]);

  async function loadAll(keyword = orderKeyword) {
    if (!storeId) {
      setOrders([]);
      setAssets([]);
      setStoreSkus([]);
      return;
    }
    setLoading(true);
    try {
      const [orderData, assetData, storeSkuData] = await Promise.all([
        http.get<unknown, RentalOrder[]>('/api/merchant/orders', { params: { storeId, status: orderStatus, keyword: keyword.trim() || undefined } }),
        http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`),
        canCreateOrder
          ? http.get<unknown, StoreSku[]>('/api/merchant/products/store-skus', { params: { storeId } })
          : Promise.resolve<StoreSku[]>([])
      ]);
      setOrders(orderData);
      setAssets(assetData);
      setStoreSkus(storeSkuData);
    } finally {
      setLoading(false);
    }
  }

  function exportMerchantOrders() {
    downloadCsv(`门店订单-${currentStore?.storeCode || storeId}`, [
      '序号',
      '订单号',
      '客户姓名',
      '联系电话',
      '状态',
      '商品',
      'SKU',
      '主资产编号',
      '电池号',
      '实际核销金额',
      '应付金额',
      '已付金额',
      '租期',
      '自动续租',
      '好评赠送天数',
      '活动赠送天数',
      '赠送合计天数',
      '下单时间',
      '预计归还',
      '系统录入时间'
    ], orders.map((order, index) => [
      index + 1,
      order.orderNo,
      order.customerName,
      order.customerPhone,
      orderStatusOptions.find((item) => item.value === order.orderStatus)?.label || order.orderStatus,
      order.storeSkuName,
      order.packageName,
      order.frameSerialNo || order.frameAssetCode,
      order.batterySerialNo || order.batteryAssetCode,
      order.verificationAmount,
      order.payableAmount,
      order.paidAmount,
      `${order.leaseValue}${order.leaseUnit === 'DAY' ? '天' : '月'} / ${order.totalPeriods}期`,
      renewalText(order),
      order.reviewBonusDays,
      order.campaignBonusDays,
      order.totalBonusDays,
      order.orderedAt,
      order.expectedReturnAt,
      order.createdAt
    ]));
  }

  useEffect(() => {
    void loadAll();
  }, [storeId, orderStatus, canCreateOrder]);

  function openCreateOrder() {
    const firstStoreSku = storeSkus[0];
    const firstPackage = firstStoreSku?.packages.find((item) => item.status === 'ENABLED');
    createForm.resetFields();
    createForm.setFieldsValue({
      storeSkuId: firstStoreSku?.id,
      packageId: firstPackage?.packageId,
      leaseMultiplier: 1,
      verificationAmount: firstPackage ? Number(firstPackage.rentalAmount) : undefined,
      orderedAt: dayjs()
    });
    setEditingOrder(null);
    setCreateOpen(true);
  }

  function openEditOrder(order: RentalOrder) {
    setEditingOrder(order);
    createForm.resetFields();
    createForm.setFieldsValue({
      userAccountId: order.userAccountId ?? undefined,
      customerName: order.customerName || '',
      customerPhone: order.customerPhone || '',
      storeSkuId: order.storeSkuId,
      packageId: order.packageId,
      leaseMultiplier: order.leaseMultiplier || 1,
      verificationAmount: Number(order.verificationAmount),
      frameAssetId: order.frameAssetId ?? undefined,
      batteryAssetId: order.batteryAssetId ?? undefined,
      orderedAt: dayjs(order.orderedAt)
    });
    setCreateOpen(true);
  }

  function closeOrderForm() {
    setCreateOpen(false);
    setEditingOrder(null);
    createForm.resetFields();
  }

  async function submitCreateOrder(values: CreateOrderForm) {
    setActionLoading(true);
    try {
      const editablePayload = {
        userAccountId: values.userAccountId,
        customerName: values.customerName,
        customerPhone: values.customerPhone,
        storeSkuId: values.storeSkuId,
        packageId: values.packageId,
        leaseMultiplier: values.leaseMultiplier,
        verificationAmount: values.verificationAmount,
        frameAssetId: values.frameAssetId,
        batteryAssetId: values.batteryAssetId,
        orderedAt: values.orderedAt.format('YYYY-MM-DDTHH:mm:ss')
      };
      if (editingOrder) {
        await http.put(`/api/merchant/orders/${editingOrder.id}`, editablePayload);
        message.success('订单资料、账单和分润快照已同步更新');
      } else {
        await http.post('/api/merchant/orders', editablePayload);
        message.success('订单已创建，账单计划已生成');
      }
      closeOrderForm();
      await loadAll();
    } finally {
      setActionLoading(false);
    }
  }

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

  function openLeaseBonus(order: RentalOrder) {
    setSelectedOrder(order);
    leaseBonusForm.resetFields();
    leaseBonusForm.setFieldsValue({ bonusType: 'REVIEW', bonusDays: 2 });
    setLeaseBonusOpen(true);
  }

  async function submitLeaseBonus(values: LeaseBonusForm) {
    if (!selectedOrder) {
      return;
    }
    setActionLoading(true);
    try {
      const updated = await http.post<unknown, RentalOrder>(`/api/merchant/orders/${selectedOrder.id}/lease-bonuses`, values);
      setOrders((items) => items.map((item) => item.id === updated.id ? updated : item));
      setSelectedOrder(updated);
      setLeaseBonusOpen(false);
      leaseBonusForm.resetFields();
      message.success(`已赠送 ${values.bonusDays} 天租期`);
    } finally {
      setActionLoading(false);
    }
  }

  function openPickup(order: RentalOrder, mode: 'PICKUP' | 'SHIP') {
    setSelectedOrder(order);
    setPickupMode(mode);
    pickupForm.resetFields();
    pickupForm.setFieldsValue({
      frameAssetId: order.frameAssetId ?? undefined,
      batteryAssetId: order.batteryAssetId ?? undefined,
      remark: mode === 'SHIP' ? '商户 Web 免付款发货' : '商户 Web 取车绑定'
    });
    setPickupOpen(true);
  }

  async function submitPickup(values: PickupForm) {
    if (!selectedOrder) {
      return;
    }
    setActionLoading(true);
    try {
      const endpoint = pickupMode === 'SHIP' ? 'ship' : 'pickup-assets';
      await http.post(`/api/merchant/orders/${selectedOrder.id}/${endpoint}`, {
        frameAssetId: values.frameAssetId,
        batteryAssetId: values.batteryAssetId,
        remark: values.remark || (pickupMode === 'SHIP' ? '商户 Web 免付款发货' : '商户 Web 取车绑定')
      });
      message.success(pickupMode === 'SHIP' ? '订单已免付款发货并开始租赁' : '取车资产已绑定');
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
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>门店订单</Typography.Title>
        <Select
          allowClear
          placeholder="订单状态"
          value={orderStatus}
          style={{ width: 180 }}
          options={orderStatusOptions}
          onChange={setOrderStatus}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="订单号、客户、电话或资产号"
          value={orderKeyword}
          style={{ width: 250 }}
          onChange={(event) => setOrderKeyword(event.target.value)}
          onPressEnter={() => void loadAll()}
        />
        <Button icon={<SearchOutlined />} onClick={() => void loadAll()}>查询</Button>
        <Button onClick={() => {
          setOrderKeyword('');
          void loadAll('');
        }}>重置</Button>
        <Button icon={<ExportOutlined />} disabled={!orders.length} onClick={exportMerchantOrders}>导出订单</Button>
        <Button icon={<ReloadOutlined />} onClick={() => void loadAll()}>刷新</Button>
        {canCreateOrder ? (
          <>
            <OrderImportTemplateButton storeCode={currentStore?.storeCode} storeSkus={storeSkus} assets={assets} />
            <Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>批量导入</Button>
            <Button type="primary" icon={<PlusOutlined />} disabled={!storeSkus.length} onClick={openCreateOrder}>
              新建订单
            </Button>
          </>
        ) : null}
      </Space>

      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={orders}
          pagination={false}
          scroll={{ x: 1780 }}
          columns={[
            { title: '序号', width: 70, render: (_value, _record, index) => index + 1 },
            { title: '订单号', dataIndex: 'orderNo' },
            { title: '客户', dataIndex: 'customerName', render: (value?: string | null) => value || '-' },
            { title: '电话', dataIndex: 'customerPhone', render: (value?: string | null) => value || '-' },
            { title: '状态', dataIndex: 'orderStatus', render: orderStatusTag },
            { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'DAY' ? '天' : '月'} / ${record.totalPeriods}期` },
            { title: '主资产编号', render: (_, record) => assetText(record.frameSerialNo, record.frameAssetCode, record.frameAssetId) },
            { title: '电池号', render: (_, record) => assetText(record.batterySerialNo, record.batteryAssetCode, record.batteryAssetId) },
            { title: '实际核销金额', dataIndex: 'verificationAmount', render: money },
            { title: '应付', dataIndex: 'payableAmount', render: money },
            { title: '已付', dataIndex: 'paidAmount', render: money },
            { title: '赠送租期', dataIndex: 'totalBonusDays', render: (value: number) => `${value} 天` },
            { title: '下单时间', dataIndex: 'orderedAt', render: dateText },
            {
              title: '操作',
              width: 360,
              fixed: 'right',
              render: (_, record) => (
                <Space wrap>
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  {canCreateOrder && canEditOrder(record) ? (
                    <Button size="small" icon={<EditOutlined />} onClick={() => openEditOrder(record)}>编辑</Button>
                  ) : null}
                  {canOperateOrder && canGrantLeaseBonus(record) ? (
                    <Button size="small" icon={<GiftOutlined />} onClick={() => openLeaseBonus(record)}>
                      赠送租期
                    </Button>
                  ) : null}
                  {canOperateOrder && record.orderStatus === 'PENDING_PAYMENT' ? (
                    <Button size="small" onClick={() => openPickup(record, 'SHIP')}>免付款发货</Button>
                  ) : null}
                  {canOperateOrder && record.orderStatus === 'PENDING_PICKUP' ? (
                    <Button size="small" onClick={() => openPickup(record, 'PICKUP')}>取车</Button>
                  ) : null}
                  {canOperateOrder && canReplaceOrderAsset(record) ? <Button size="small" onClick={() => {
                    setSelectedOrder(record);
                    replaceForm.setFieldsValue({ assetType: 'VEHICLE_FRAME', oldAssetResultStatus: 'IDLE', remark: '商户 Web 更换资产' });
                    setReplaceOpen(true);
                  }}>
                    更换资产
                  </Button> : null}
                  {canOperateOrder && canReturnOrderAssets(record) ? <Button size="small" danger onClick={() => {
                    setSelectedOrder(record);
                    returnForm.setFieldsValue({ frameResultStatus: 'IDLE', batteryResultStatus: 'IDLE', remark: '商户 Web 归还结束订单' });
                    setReturnOpen(true);
                  }}>
                    归还结束
                  </Button> : null}
                </Space>
              )
            }
          ]}
        />
      </div>

      <Modal
        title={editingOrder ? '编辑订单' : '新建订单'}
        open={createOpen}
        onCancel={closeOrderForm}
        onOk={() => createForm.submit()}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" onFinish={submitCreateOrder}>
          <Form.Item name="userAccountId" label="用户账号 ID">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, message: '请输入客户姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="customerPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select
              options={storeSkuOptions}
              onChange={(value) => {
                const nextStoreSku = storeSkus.find((item) => item.id === value);
                const firstPackage = nextStoreSku?.packages.find((item) => item.status === 'ENABLED');
                createForm.setFieldsValue({
                  packageId: firstPackage?.packageId,
                  leaseMultiplier: 1,
                  verificationAmount: firstPackage ? Number(firstPackage.rentalAmount) : undefined,
                  frameAssetId: undefined,
                  batteryAssetId: undefined
                });
              }}
            />
          </Form.Item>
          <Form.Item name="packageId" label="租赁 SKU" rules={[{ required: true, message: '请选择租赁 SKU' }]}>
            <Select
              options={packageOptions}
              onChange={(value) => {
                const nextPackage = selectedStoreSku?.packages.find((item) => item.packageId === value);
                createForm.setFieldValue('verificationAmount', nextPackage ? Number(nextPackage.rentalAmount) * selectedLeaseMultiplier : undefined);
              }}
            />
          </Form.Item>
          <Form.Item
            name="leaseMultiplier"
            label="租期倍数"
            rules={[{ required: true, message: '请输入租期倍数' }]}
            extra={selectedPackage
              ? `最终租期：${selectedPackage.leaseValue * selectedLeaseMultiplier}${selectedPackage.leaseUnit === 'MONTH' ? '个月（每月30天）' : '天'} / ${selectedPackage.totalPeriods * selectedLeaseMultiplier}期`
              : '例如 1个月 SKU 选择 2 倍，即租用 2个月（60天）'}
          >
            <InputNumber
              min={1}
              max={120}
              precision={0}
              addonAfter="倍"
              style={{ width: '100%' }}
              onChange={(value) => createForm.setFieldValue(
                'verificationAmount',
                selectedPackage && value ? Number(selectedPackage.rentalAmount) * Number(value) : undefined
              )}
            />
          </Form.Item>
          <Form.Item
            name="verificationAmount"
            label="实际核销金额"
            rules={[{ required: true, message: '请输入实际核销金额' }]}
            extra={selectedPackage ? `当前 ${selectedLeaseMultiplier} 倍参考总价：${money(Number(selectedPackage.rentalAmount) * selectedLeaseMultiplier)}` : undefined}
          >
            <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              placeholder="输入序列号、资产编号或自定义类型搜索"
              notFoundContent="该门店暂无空闲主资产或自定义资产"
              options={createFrameOptions}
            />
          </Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              disabled={integratedCreateAssetSelected}
              placeholder={integratedCreateAssetSelected ? '车电一体无需独立电池' : '输入电池号或资产编号搜索，可不选'}
              notFoundContent="该门店暂无空闲电池资产"
              options={createBatteryOptions}
            />
          </Form.Item>
          <Form.Item name="orderedAt" label="下单时间" rules={[{ required: true, message: '请选择下单时间' }]}>
            <DatePicker
              showTime
              format="YYYY-MM-DD HH:mm"
              disabledDate={(current) => current.isAfter(dayjs(), 'day')}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>

      <OrderBatchImportModal
        open={batchImportOpen}
        endpoint={`/api/merchant/orders/stores/${storeId}/batch-import`}
        onClose={() => setBatchImportOpen(false)}
        onImported={loadAll}
      />

      <Modal
        title="赠送租期"
        open={leaseBonusOpen}
        onCancel={() => setLeaseBonusOpen(false)}
        onOk={() => leaseBonusForm.submit()}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={leaseBonusForm} layout="vertical" onFinish={submitLeaseBonus}>
          <Form.Item name="bonusType" label="赠送类型" rules={[{ required: true, message: '请选择赠送类型' }]}>
            <Select
              options={[
                { label: '好评赠送', value: 'REVIEW' },
                { label: '活动赠送', value: 'CAMPAIGN' }
              ]}
              onChange={(value: LeaseBonusForm['bonusType']) => {
                leaseBonusForm.setFieldValue('bonusDays', value === 'REVIEW' ? 2 : 15);
              }}
            />
          </Form.Item>
          <Form.Item name="bonusDays" label="赠送天数" rules={[{ required: true, message: '请输入赠送天数' }]}>
            <InputNumber min={1} max={999} precision={0} addonAfter="天" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={255} placeholder="如：客户完成平台好评、暑期活动赠送" />
          </Form.Item>
        </Form>
      </Modal>

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
              <Descriptions.Item label="门店">{selectedOrder.storeName || selectedOrder.storeId}</Descriptions.Item>
              <Descriptions.Item label="客户姓名">{selectedOrder.customerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="联系电话">{selectedOrder.customerPhone || '-'}</Descriptions.Item>
              <Descriptions.Item label="商品 / SKU">{selectedOrder.storeSkuName || '-'} / {selectedOrder.packageName || '-'}</Descriptions.Item>
              <Descriptions.Item label="主资产">{assetText(selectedOrder.frameSerialNo, selectedOrder.frameAssetCode, selectedOrder.frameAssetId)}</Descriptions.Item>
              <Descriptions.Item label="电池资产">{assetText(selectedOrder.batterySerialNo, selectedOrder.batteryAssetCode, selectedOrder.batteryAssetId)}</Descriptions.Item>
              <Descriptions.Item label="基础租期">{selectedOrder.leaseValue}{selectedOrder.leaseUnit === 'DAY' ? '天' : '个月'}</Descriptions.Item>
              <Descriptions.Item label="好评赠送">{selectedOrder.reviewBonusDays} 天</Descriptions.Item>
              <Descriptions.Item label="活动赠送">{selectedOrder.campaignBonusDays} 天</Descriptions.Item>
              <Descriptions.Item label="赠送合计">{selectedOrder.totalBonusDays} 天</Descriptions.Item>
              <Descriptions.Item label="实际核销金额">{money(selectedOrder.verificationAmount)}</Descriptions.Item>
              <Descriptions.Item label="应付金额">{money(selectedOrder.payableAmount)}</Descriptions.Item>
              <Descriptions.Item label="预计归还">{dateText(selectedOrder.expectedReturnAt)}</Descriptions.Item>
              <Descriptions.Item label="下单时间">{dateText(selectedOrder.orderedAt)}</Descriptions.Item>
              <Descriptions.Item label="系统录入时间">{dateText(selectedOrder.createdAt)}</Descriptions.Item>
            </Descriptions>
            <div className="section">
              <Typography.Title level={5}>赠送租期记录</Typography.Title>
              <Table
                rowKey="id"
                size="small"
                dataSource={selectedOrder.leaseBonuses}
                pagination={false}
                locale={{ emptyText: <Empty description="暂无赠送租期记录" /> }}
                columns={[
                  { title: '类型', dataIndex: 'bonusType', render: leaseBonusTypeText },
                  { title: '天数', dataIndex: 'bonusDays', render: (value: number) => `${value} 天` },
                  { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
                  { title: '顺延前', dataIndex: 'expectedReturnBefore', render: dateText },
                  { title: '顺延后', dataIndex: 'expectedReturnAfter', render: dateText },
                  { title: '操作时间', dataIndex: 'createdAt', render: dateText }
                ]}
              />
            </div>
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
                  {settlement.calculationVersion === 'PROFIT_V2' ? (
                    <>
                      <Descriptions.Item label="实际结算金额">{money(settlement.settlementBaseAmount)}</Descriptions.Item>
                      <Descriptions.Item label="来源渠道">{settlement.sourceChannel}</Descriptions.Item>
                      <Descriptions.Item label="渠道核销扣点">{money(settlement.channelFeeAmount)}</Descriptions.Item>
                      <Descriptions.Item label="租赁平台扣点">{money(settlement.platformFeeAmount)}</Descriptions.Item>
                      <Descriptions.Item label="门店运营分润">{money(settlement.storeOperationAmount)}</Descriptions.Item>
                      <Descriptions.Item label="门店维修分润">{money(settlement.maintenanceFundAmount)}</Descriptions.Item>
                      <Descriptions.Item label="门店合计分润">{money(Number(settlement.storeOperationAmount || 0) + Number(settlement.maintenanceFundAmount || 0))}</Descriptions.Item>
                      <Descriptions.Item label="渠道引流分润">{money(settlement.channelReferralAmount)}</Descriptions.Item>
                      <Descriptions.Item label="出资方分润">{money(settlement.investorShareAmount)}</Descriptions.Item>
                    </>
                  ) : (
                    <>
                      <Descriptions.Item label="门店收益">{money(settlement.merchantRentShareAmount)}</Descriptions.Item>
                      <Descriptions.Item label="签单费">{money(settlement.signFeeAmount)}</Descriptions.Item>
                      <Descriptions.Item label="平台收益">{money(settlement.platformRentShareAmount)}</Descriptions.Item>
                      <Descriptions.Item label="运营手续费">{money(settlement.investorOperationFeeAmount)}</Descriptions.Item>
                      <Descriptions.Item label="出资方净收益">{money(settlement.investorNetShareAmount)}</Descriptions.Item>
                    </>
                  )}
                </Descriptions>
              ) : (
                <Empty description="当前订单暂无分润快照" />
              )}
            </div>
          </Space>
        ) : null}
      </Modal>

      <Modal title={pickupMode === 'SHIP' ? '免付款发货' : '取车绑定资产'} open={pickupOpen} onCancel={() => setPickupOpen(false)} onOk={() => pickupForm.submit()} confirmLoading={actionLoading} destroyOnHidden>
        <Form form={pickupForm} layout="vertical" onFinish={submitPickup}>
          <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              placeholder="输入序列号、资产编号或自定义类型搜索"
              notFoundContent="该门店暂无空闲主资产或自定义资产"
              options={pickupFrameOptions}
              onChange={(value) => {
                if (assets.some((item) => item.id === value && item.assetType === 'INTEGRATED_VEHICLE')) {
                  pickupForm.setFieldValue('batteryAssetId', undefined);
                }
              }}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(previous, current) => previous.frameAssetId !== current.frameAssetId}>
            {({ getFieldValue }) => {
              const frameAssetId = getFieldValue('frameAssetId') as number | undefined;
              const integratedVehicleSelected = assets.some((item) => item.id === frameAssetId && item.assetType === 'INTEGRATED_VEHICLE');
              return (
                <Form.Item name="batteryAssetId" label="电池资产">
                  <Select
                    showSearch
                    allowClear
                    optionFilterProp="label"
                    disabled={integratedVehicleSelected}
                    placeholder={integratedVehicleSelected ? '车电一体无需独立电池' : '输入电池号或资产编号搜索'}
                    notFoundContent="该门店暂无空闲电池资产"
                    options={pickupBatteryOptions}
                  />
                </Form.Item>
              );
            }}
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
                { label: '主资产（含全部自定义类型）', value: 'VEHICLE_FRAME' },
                { label: '电池', value: 'BATTERY' }
              ]}
              onChange={() => replaceForm.setFieldValue('newAssetId', undefined)}
            />
          </Form.Item>
          <Form.Item name="newAssetId" label="新资产" rules={[{ required: true, message: '请选择新资产' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="输入资产编号或序列号搜索"
              notFoundContent="该门店暂无符合条件的空闲资产"
              options={replaceAssetOptions}
            />
          </Form.Item>
          <Form.Item name="oldAssetResultStatus" label="原资产状态" rules={[{ required: true, message: '请选择原资产状态' }]}>
            <Select options={assetResultStatusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="归还并结束订单" open={returnOpen} onCancel={() => setReturnOpen(false)} onOk={() => returnForm.submit()} confirmLoading={actionLoading} destroyOnHidden>
        <Form form={returnForm} layout="vertical" onFinish={submitReturn}>
          <Form.Item name="frameResultStatus" label="主资产归还状态" rules={[{ required: true, message: '请选择主资产状态' }]}>
            <Select options={assetResultStatusOptions} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="电池归还状态" rules={[{ required: true, message: '请选择电池状态' }]}>
            <Select options={assetResultStatusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

export function MerchantAssetWorkspace({ account, storeId, stores }: MerchantPageProps) {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [assetTypes, setAssetTypes] = useState<AssetTypeDefinition[]>([]);
  const [investorOptions, setInvestorOptions] = useState<AssetInvestorOption[]>([]);
  const [assetKeyword, setAssetKeyword] = useState('');
  const [assetTypeFilter, setAssetTypeFilter] = useState<number>();
  const [assetStatusFilter, setAssetStatusFilter] = useState<AssetStatus>();
  const [editingAsset, setEditingAsset] = useState<Asset | null>(null);
  const [maintenanceAsset, setMaintenanceAsset] = useState<Asset | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<AssetDetail | null>(null);
  const [maintenanceStocks, setMaintenanceStocks] = useState<StoreSparePartStock[]>([]);
  const [assetOpen, setAssetOpen] = useState(false);
  const [maintenanceOpen, setMaintenanceOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [maintenanceSaving, setMaintenanceSaving] = useState(false);
  const [assetForm] = Form.useForm<MerchantAssetForm>();
  const [maintenanceForm] = Form.useForm<MerchantMaintenanceForm>();
  const selectedAssetTypeId = Form.useWatch('assetTypeId', assetForm);
  const currentStore = stores.find((item) => item.id === storeId);
  const canImportAssets = account.permissions.includes('asset.import') || account.permissions.includes('system.admin');
  const canManageAssets = account.permissions.includes('asset.manage') || account.permissions.includes('system.admin');
  const canOperateMaintenance = account.permissions.includes('maintenance.operate') || account.permissions.includes('system.admin');
  const assetTypeFilterOptions = useMemo(() => assetTypes.map((type) => ({
    label: `${type.typeName}${type.status === 'DISABLED' ? '（已停用）' : ''}`,
    value: type.id
  })), [assetTypes]);
  const assetEntryTypeOptions = useMemo(() => assetTypes
    .filter((type) => {
      if (editingAsset) {
        return type.assetClass === editingAsset.assetType && (type.status === 'ENABLED' || type.id === editingAsset.assetTypeId);
      }
      return type.status === 'ENABLED';
    })
    .map((type) => ({ label: type.typeName, value: type.id })), [assetTypes, editingAsset]);
  const investorSelectOptions = useMemo(() => investorOptions.map((investor) => ({
    label: `${investor.investorName} / ${investor.investorCode}`,
    value: investor.id
  })), [investorOptions]);
  const maintenancePartOptions = useMemo(() => maintenanceStocks.map((stock) => ({
    label: `${stock.partName} / 库存 ${stock.stockQuantity}`,
    value: stock.partId
  })), [maintenanceStocks]);
  const selectedAssetType = useMemo(
    () => assetTypes.find((type) => type.id === selectedAssetTypeId),
    [assetTypes, selectedAssetTypeId]
  );
  const filteredAssets = useMemo(() => {
    const keyword = assetKeyword.trim().toLowerCase();
    return assets.filter((asset) => {
      if (assetTypeFilter && asset.assetTypeId !== assetTypeFilter) return false;
      if (assetStatusFilter && asset.status !== assetStatusFilter) return false;
      if (!keyword) return true;
      return asset.assetCode.toLowerCase().includes(keyword)
        || asset.serialNo.toLowerCase().includes(keyword)
        || String(asset.investorName || '').toLowerCase().includes(keyword)
        || String(asset.assetTypeName || '').toLowerCase().includes(keyword);
    });
  }, [assetKeyword, assetStatusFilter, assetTypeFilter, assets]);

  async function loadAssets() {
    if (!storeId) {
      setAssets([]);
      setMaintenanceStocks([]);
      return;
    }
    setLoading(true);
    try {
      const [assetData, typeData, investorData, stockData] = await Promise.all([
        http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`),
        http.get<unknown, AssetTypeDefinition[]>('/api/merchant/assets/types'),
        canManageAssets
          ? http.get<unknown, AssetInvestorOption[]>('/api/merchant/assets/investors')
          : Promise.resolve<AssetInvestorOption[]>([]),
        canOperateMaintenance
          ? http.get<unknown, StoreSparePartStock[]>('/api/merchant/spare-parts/store-stocks', { params: { storeId } })
          : Promise.resolve<StoreSparePartStock[]>([])
      ]);
      setAssets(assetData);
      setAssetTypes(typeData);
      setInvestorOptions(investorData);
      setMaintenanceStocks(stockData);
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

  function openCreateAsset() {
    setEditingAsset(null);
    assetForm.resetFields();
    assetForm.setFieldsValue({
      assetTypeId: assetTypes.find((type) => type.status === 'ENABLED')?.id,
      purchaseAmount: 0
    });
    setAssetOpen(true);
  }

  function openEditAsset(record: Asset) {
    setEditingAsset(record);
    assetForm.setFieldsValue({
      assetTypeId: record.assetTypeId,
      serialNo: record.serialNo,
      investorId: record.investorId,
      purchaseAmount: record.purchaseAmount,
      residualValue: record.residualValue ?? undefined,
      purchasedAt: record.purchasedAt ?? undefined
    });
    setAssetOpen(true);
  }

  function openMaintenance(record: Asset) {
    setMaintenanceAsset(record);
    maintenanceForm.resetFields();
    maintenanceForm.setFieldsValue({
      maintenanceType: 'REPAIR',
      maintenanceStatus: 'COMPLETED',
      responsibilityType: 'ROUTINE_MAINTENANCE',
      costBearerType: 'MERCHANT',
      laborCost: 0,
      externalCost: 0,
      parts: []
    });
    setMaintenanceOpen(true);
  }

  async function submitAsset(values: MerchantAssetForm) {
    if (!storeId) return;
    setSaving(true);
    try {
      if (editingAsset) {
        await http.put(`/api/merchant/assets/stores/${storeId}/${editingAsset.id}`, values);
        message.success('资产资料已更新');
      } else {
        await http.post(`/api/merchant/assets/stores/${storeId}`, values);
        message.success('资产已添加到当前门店');
      }
      assetForm.resetFields();
      setEditingAsset(null);
      setAssetOpen(false);
      await loadAssets();
    } finally {
      setSaving(false);
    }
  }

  async function deleteAsset(record: Asset) {
    if (!storeId) return;
    await http.delete(`/api/merchant/assets/stores/${storeId}/${record.id}`);
    message.success('资产已删除');
    await loadAssets();
  }

  async function submitMaintenance(values: MerchantMaintenanceForm) {
    if (!storeId || !maintenanceAsset) return;
    setMaintenanceSaving(true);
    try {
      await http.post('/api/merchant/maintenances', {
        ...values,
        assetId: maintenanceAsset.id,
        storeId,
        parts: (values.parts || []).filter((item) => item.partId && item.quantity)
      });
      message.success('维修记录已登记');
      setMaintenanceOpen(false);
      setMaintenanceAsset(null);
      maintenanceForm.resetFields();
      await loadAssets();
    } finally {
      setMaintenanceSaving(false);
    }
  }

  function exportMerchantAssets() {
    downloadCsv(`门店资产-${currentStore?.storeCode || storeId}`, [
      '序号',
      '资产编码',
      '资产类型',
      '车架号/电池号',
      '出资方',
      '状态',
      '采购金额',
      '报废残值',
      '采购日期'
    ], filteredAssets.map((asset, index) => [
      index + 1,
      asset.assetCode,
      asset.assetTypeName || assetTypeText(asset.assetType),
      asset.serialNo,
      asset.investorName,
      assetStatusOptions.find((item) => item.value === asset.status)?.label || asset.status,
      asset.purchaseAmount,
      asset.residualValue,
      asset.purchasedAt
    ]));
  }

  if (!storeId) {
    return <Empty description="请选择门店后查看资产" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>门店资产</Typography.Title>
        {canManageAssets ? <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAsset}>新增资产</Button> : null}
        <Button
          icon={<DownloadOutlined />}
          onClick={() => void downloadAssetImportTemplate({
            storeCode: currentStore?.storeCode,
            assetTypes,
            investors: investorOptions,
            stores: currentStore ? [currentStore] : []
          })}
        >
          下载模板
        </Button>
        {canImportAssets ? (
          <Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>批量录入</Button>
        ) : null}
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="资产编码、编号、类型或出资方"
          value={assetKeyword}
          style={{ width: 230 }}
          onChange={(event) => setAssetKeyword(event.target.value)}
        />
        <Select
          allowClear
          placeholder="资产类型"
          value={assetTypeFilter}
          options={assetTypeFilterOptions}
          style={{ width: 150 }}
          onChange={setAssetTypeFilter}
        />
        <Select
          allowClear
          placeholder="资产状态"
          value={assetStatusFilter}
          options={assetStatusOptions}
          style={{ width: 140 }}
          onChange={setAssetStatusFilter}
        />
        <Button onClick={() => {
          setAssetKeyword('');
          setAssetTypeFilter(undefined);
          setAssetStatusFilter(undefined);
        }}>重置</Button>
        <Button icon={<ExportOutlined />} disabled={!filteredAssets.length} onClick={exportMerchantAssets}>导出资产</Button>
        <Button icon={<ReloadOutlined />} onClick={loadAssets}>刷新</Button>
      </Space>
      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={filteredAssets}
          pagination={false}
          scroll={{ x: 1100 }}
          columns={[
            { title: '序号', width: 70, render: (_value, _record, index) => index + 1 },
            { title: '资产编码', dataIndex: 'assetCode' },
            { title: '类型', render: (_, record) => record.assetTypeName || assetTypeText(record.assetType) },
            { title: '资产编号', dataIndex: 'serialNo' },
            { title: '出资方', dataIndex: 'investorName', render: (value?: string | null) => value || '-' },
            { title: '状态', dataIndex: 'status', render: assetStatusTag },
            { title: '采购金额', dataIndex: 'purchaseAmount', render: money },
            { title: '残值', dataIndex: 'residualValue', render: optionalMoney },
            {
              title: '操作',
              width: 230,
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  {canManageAssets ? (
                    <Button
                      size="small"
                      icon={<EditOutlined />}
                      disabled={record.status === 'RENTING'}
                      title={record.status === 'RENTING' ? '租赁中的资产暂不能编辑' : undefined}
                      onClick={() => openEditAsset(record)}
                    >
                      编辑
                    </Button>
                  ) : null}
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  {canOperateMaintenance ? <Button size="small" onClick={() => openMaintenance(record)}>维修</Button> : null}
                  {canManageAssets ? (
                    <Popconfirm
                      title="删除资产"
                      description="仅空闲且没有订单、履约、维修或结算记录的资产可以删除。"
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => deleteAsset(record)}
                    >
                      <Button size="small" danger icon={<DeleteOutlined />} disabled={record.status !== 'IDLE'}>删除</Button>
                    </Popconfirm>
                  ) : null}
                </Space>
              )
            }
          ]}
        />
      </div>

      <AssetBatchImportModal
        open={batchImportOpen}
        endpoint={`/api/merchant/assets/stores/${storeId}/batch-import`}
        onClose={() => setBatchImportOpen(false)}
        onImported={loadAssets}
      />

      <Modal
        title={editingAsset ? '编辑门店资产' : '新增门店资产'}
        open={assetOpen}
        onCancel={() => {
          assetForm.resetFields();
          setEditingAsset(null);
          setAssetOpen(false);
        }}
        onOk={() => assetForm.submit()}
        confirmLoading={saving}
        forceRender
      >
        <Form form={assetForm} layout="vertical" onFinish={submitAsset}>
          <Form.Item name="assetTypeId" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
            <Select showSearch optionFilterProp="label" options={assetEntryTypeOptions} />
          </Form.Item>
          <Form.Item
            name="serialNo"
            label={selectedAssetType?.serialLabel || '资产编号'}
            rules={[{ required: true, message: `请输入${selectedAssetType?.serialLabel || '资产编号'}` }]}
          >
            <Input placeholder={selectedAssetType?.assetClass === 'INTEGRATED_VEHICLE' ? '车电一体仅录入车架号' : undefined} />
          </Form.Item>
          <Form.Item name="investorId" label="出资方" rules={[{ required: true, message: '请选择出资方' }]}>
            <Select showSearch optionFilterProp="label" options={investorSelectOptions} />
          </Form.Item>
          <Form.Item name="purchaseAmount" label="采购金额" rules={[{ required: true, message: '请输入采购金额' }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="residualValue" label="报废残值">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="purchasedAt" label="采购日期">
            <Input placeholder="2026-07-22" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={maintenanceAsset ? `${maintenanceAsset.assetCode} / 登记维修` : '登记维修'}
        open={maintenanceOpen}
        onCancel={() => {
          maintenanceForm.resetFields();
          setMaintenanceAsset(null);
          setMaintenanceOpen(false);
        }}
        onOk={() => maintenanceForm.submit()}
        confirmLoading={maintenanceSaving}
        width={860}
        destroyOnHidden
      >
        <Form form={maintenanceForm} layout="vertical" onFinish={submitMaintenance}>
          <Form.Item name="orderId" label="关联订单 ID">
            <InputNumber min={1} placeholder="需要绑定租赁订单时填写" style={{ width: '100%' }} />
          </Form.Item>
          <Space style={{ width: '100%' }} size={12} align="start">
            <Form.Item name="maintenanceType" label="维修类型" rules={[{ required: true, message: '请选择维修类型' }]} style={{ flex: 1 }}>
              <Select options={[
                { label: '维修', value: 'REPAIR' },
                { label: '保养', value: 'MAINTENANCE' },
                { label: '换件', value: 'REPLACE_PART' },
                { label: '检测', value: 'INSPECTION' }
              ]} />
            </Form.Item>
            <Form.Item name="responsibilityType" label="责任归因" rules={[{ required: true, message: '请选择责任归因' }]} style={{ flex: 1 }}>
              <Select onChange={(value) => maintenanceForm.setFieldValue('costBearerType', merchantMaintenanceCostBearerType(value))} options={[
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
          <Space style={{ width: '100%' }} size={12} align="start">
            <Form.Item name="costBearerType" label="成本承担方" rules={[{ required: true, message: '请选择成本承担方' }]} style={{ flex: 1 }}>
              <Select disabled options={[
                { label: '商户', value: 'MERCHANT' },
                { label: '用户', value: 'USER' },
                { label: '平台', value: 'PLATFORM' }
              ]} />
            </Form.Item>
            <Form.Item name="costBearerId" label="承担方 ID" style={{ flex: 1 }}>
              <InputNumber min={0} placeholder="默认按资产或订单归属" style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={12} align="start">
            <Form.Item name="laborCost" label="人工费" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="externalCost" label="外协费" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.List name="parts">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space align="center" className="toolbar">
                  <Typography.Title level={5}>消耗配件</Typography.Title>
                  <Button size="small" disabled={!maintenancePartOptions.length} onClick={() => add({ quantity: 1 })}>添加配件</Button>
                  {!maintenancePartOptions.length ? <Typography.Text type="secondary">当前门店暂无配件库存</Typography.Text> : null}
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} align="start" style={{ width: '100%' }}>
                    <Form.Item name={[field.name, 'partId']} rules={[{ required: true, message: '请选择配件' }]} style={{ width: 260 }}>
                      <Select placeholder="配件" options={maintenancePartOptions} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: '请输入数量' }]} style={{ width: 120 }}>
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
              <Descriptions.Item label="资产类型">{selectedAsset.asset.assetTypeName || assetTypeText(selectedAsset.asset.assetType)}</Descriptions.Item>
              <Descriptions.Item label={selectedAsset.asset.serialLabel || '资产编号'}>{selectedAsset.asset.serialNo}</Descriptions.Item>
              <Descriptions.Item label="当前状态">{assetStatusTag(selectedAsset.asset.status)}</Descriptions.Item>
              <Descriptions.Item label="出资方">{selectedAsset.asset.investorName || '-'}</Descriptions.Item>
              <Descriptions.Item label="所属门店">{selectedAsset.asset.storeName || '-'}</Descriptions.Item>
              <Descriptions.Item label="采购金额">{money(selectedAsset.asset.purchaseAmount)}</Descriptions.Item>
              <Descriptions.Item label="残值">{optionalMoney(selectedAsset.asset.residualValue)}</Descriptions.Item>
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
            { title: '来源', dataIndex: 'sourceType', render: (value) => value === 'EXTERNAL_ORDER' ? <Tag color="purple">补录订单</Tag> : <Tag color="blue">正式订单</Tag> },
            { title: '业务单号', render: (_, record) => record.sourceNo || record.sourceId },
            { title: '收益类型', dataIndex: 'lineType', render: incomeLineText },
            { title: '金额', dataIndex: 'amount', render: money },
            { title: '状态', dataIndex: 'entryStatus', render: incomeStatusTag },
            { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
            { title: '计入时间', dataIndex: 'occurredAt', render: dateText }
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
            { title: '运营及维修分润', dataIndex: 'rentShareIncomeAmount', render: money },
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
            { title: '来源', dataIndex: 'sourceType', render: (value) => value === 'EXTERNAL_ORDER' ? <Tag color="purple">补录订单</Tag> : <Tag color="blue">正式订单/账单</Tag> },
            { title: '来源ID', dataIndex: 'sourceId' },
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

function optionalMoney(value?: number | string | null) {
  return value == null || value === '' ? '-' : money(value);
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function renewalText(order: RentalOrder) {
  if (!order.autoRenewEnabled) return '未开启';
  const unit = order.renewalUnit === 'DAY' ? '天' : '个月';
  return `${order.renewalValue || 1}${unit} / ${money(order.renewalAmount)} / 已续 ${order.renewalCount} 次`;
}

function canGrantLeaseBonus(order: RentalOrder) {
  return !['OVERDUE', 'PENDING_SUPPLEMENT', 'COMPLETED', 'CANCELLED', 'EXCEPTION'].includes(order.orderStatus);
}

function canEditOrder(order: RentalOrder) {
  return order.orderStatus === 'PENDING_PAYMENT' && Number(order.paidAmount || 0) === 0;
}

function canReplaceOrderAsset(order: RentalOrder) {
  return ['RENTING', 'PENDING_RETURN', 'PENDING_SUPPLEMENT'].includes(order.orderStatus);
}

function canReturnOrderAssets(order: RentalOrder) {
  return ['RENTING', 'PENDING_RETURN', 'OVERDUE', 'PENDING_SUPPLEMENT'].includes(order.orderStatus);
}

function leaseBonusTypeText(value: 'REVIEW' | 'CAMPAIGN') {
  return value === 'REVIEW' ? '好评赠送' : '活动赠送';
}

function assetText(serialNo?: string | null, assetCode?: string | null, assetId?: number | null) {
  return serialNo || assetCode || (assetId ? `#${assetId}` : '-');
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

function externalOrderStatusTag(value: ExternalRentalOrder['orderStatus']) {
  const map: Record<ExternalRentalOrder['orderStatus'], { text: string; color: string }> = {
    ACTIVE: { text: '进行中', color: 'green' },
    COMPLETED: { text: '已完成', color: 'blue' },
    TERMINATED: { text: '已终止', color: 'default' }
  };
  const item = map[value];
  return <Tag color={item.color}>{item.text}</Tag>;
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

function assetTypeText(value: Asset['assetType']) {
  if (value === 'INTEGRATED_VEHICLE') return '车电一体';
  if (value === 'VEHICLE_FRAME') return '车架';
  if (value === 'BATTERY') return '电池';
  return '普通资产';
}

function assetSelectLabel(asset: Asset) {
  return `${asset.serialNo} / ${asset.assetCode} / ${asset.assetTypeName || assetTypeText(asset.assetType)}`;
}

function maintenanceColumns() {
  return [
    { title: '维修单号', dataIndex: 'maintenanceNo' },
    { title: '资产编码', dataIndex: 'assetCode' },
    { title: '资产类型', render: (_value: unknown, record: AssetMaintenance) => record.assetTypeName || assetTypeText(record.assetType) },
    { title: '维修类型', dataIndex: 'maintenanceType' },
    { title: '归因', dataIndex: 'responsibilityType', render: responsibilityText },
    { title: '配件成本', dataIndex: 'partsCost', render: money },
    { title: '人工+外协', render: (_: unknown, record: AssetMaintenance) => money(Number(record.laborCost || 0) + Number(record.externalCost || 0)) },
    { title: '总成本', dataIndex: 'totalCost', render: money },
    { title: '平台补门店', dataIndex: 'merchantReimbursementAmount', render: money },
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

function merchantMaintenanceCostBearerType(value: MerchantMaintenanceForm['responsibilityType']): MerchantMaintenanceForm['costBearerType'] {
  if (value === 'CUSTOMER_DAMAGE') return 'USER';
  if (value === 'PLATFORM_SUBSIDY') return 'PLATFORM';
  return 'MERCHANT';
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
    CHANNEL_VERIFICATION_FEE: '渠道核销扣点',
    PLATFORM_SERVICE_FEE: '租赁平台扣点',
    STORE_OPERATION_SHARE: '门店运营分润',
    MAINTENANCE_FUND_SHARE: '门店维修分润',
    CHANNEL_REFERRAL_SHARE: '渠道引流分润',
    INVESTOR_SHARE: '出资方分润',
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
    MERCHANT_MAINTENANCE_SHARE: '门店维修分润',
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
