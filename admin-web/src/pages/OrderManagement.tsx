import { CarOutlined, EditOutlined, ExportOutlined, GiftOutlined, PlusOutlined, RollbackOutlined, SearchOutlined, SwapOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { OrderBatchImportModal, OrderImportTemplateButton } from '../components/OrderBatchImportModal';
import { http } from '../services/request';
import type { Asset, AssetStatus, OrderStatus, RentalOrder, Store, StoreSku } from '../types/api';
import { downloadCsv } from '../utils/csv';

const statusOptions: { label: string; value: OrderStatus }[] = [
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '待实名', value: 'PENDING_REAL_NAME' },
  { label: '待签约', value: 'PENDING_AGREEMENT' },
  { label: '待免押', value: 'PENDING_DEPOSIT_AUTH' },
  { label: '待核销', value: 'PENDING_VERIFY' },
  { label: '待取车', value: 'PENDING_PICKUP' },
  { label: '租赁中', value: 'RENTING' },
  { label: '待还车', value: 'PENDING_RETURN' },
  { label: '已逾期', value: 'OVERDUE' },
  { label: '待补缴', value: 'PENDING_SUPPLEMENT' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '异常', value: 'EXCEPTION' }
];

const assetResultStatusOptions: { label: string; value: AssetStatus }[] = [
  { label: '空闲可用', value: 'IDLE' },
  { label: '待检修', value: 'PENDING_REPAIR' },
  { label: '异常', value: 'EXCEPTION' },
  { label: '已报废', value: 'SCRAPPED' }
];

const normalTransitionMap: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING_PAYMENT: ['PENDING_REAL_NAME'],
  PENDING_REAL_NAME: ['PENDING_AGREEMENT'],
  PENDING_AGREEMENT: ['PENDING_DEPOSIT_AUTH'],
  PENDING_DEPOSIT_AUTH: ['PENDING_VERIFY'],
  PENDING_VERIFY: ['PENDING_PICKUP'],
  RENTING: ['PENDING_RETURN', 'OVERDUE'],
  PENDING_RETURN: ['OVERDUE'],
  OVERDUE: ['PENDING_SUPPLEMENT']
};

type CreateForm = {
  userAccountId?: number;
  customerName: string;
  customerPhone: string;
  storeSkuId: number;
  packageId: number;
  verificationAmount: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  orderedAt: Dayjs;
};

type TransitionForm = {
  targetStatus: OrderStatus;
  remark?: string;
};

type ReasonForm = {
  reason: string;
};

type LeaseBonusForm = {
  bonusType: 'REVIEW' | 'CAMPAIGN';
  bonusDays: number;
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
  returnStoreId?: number;
  frameResultStatus?: AssetStatus;
  batteryResultStatus?: AssetStatus;
  remark?: string;
};

type OrderFilterForm = {
  keyword?: string;
  status?: OrderStatus;
  storeId?: number;
  userAccountId?: number;
};

export function OrderManagement() {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<RentalOrder | null>(null);
  const [editingOrder, setEditingOrder] = useState<RentalOrder | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const [transitionOpen, setTransitionOpen] = useState(false);
  const [leaseBonusOpen, setLeaseBonusOpen] = useState(false);
  const [pickupOpen, setPickupOpen] = useState(false);
  const [pickupMode, setPickupMode] = useState<'PICKUP' | 'SHIP'>('PICKUP');
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [createForm] = Form.useForm<CreateForm>();
  const [transitionForm] = Form.useForm<TransitionForm>();
  const [leaseBonusForm] = Form.useForm<LeaseBonusForm>();
  const [pickupForm] = Form.useForm<PickupForm>();
  const [replaceForm] = Form.useForm<ReplaceForm>();
  const [returnForm] = Form.useForm<ReturnForm>();
  const [cancelForm] = Form.useForm<ReasonForm>();
  const [exceptionForm] = Form.useForm<ReasonForm>();
  const [filterForm] = Form.useForm<OrderFilterForm>();
  const selectedStoreSkuId = Form.useWatch('storeSkuId', createForm);
  const selectedPackageId = Form.useWatch('packageId', createForm);
  const selectedFrameAssetId = Form.useWatch('frameAssetId', createForm);
  const selectedPickupFrameAssetId = Form.useWatch('frameAssetId', pickupForm);
  const replaceAssetType = Form.useWatch('assetType', replaceForm);

  useEffect(() => {
    void loadAll({});
  }, []);

  const storeSkuOptions = useMemo(() => storeSkus
    .filter((item) => item.status === 'ON_SHELF' || item.id === editingOrder?.storeSkuId)
    .map((item) => ({
    label: `${item.displayName} / ${item.storeName}`,
    value: item.id
  })), [editingOrder, storeSkus]);

  const activeStoreSkus = useMemo(() => storeSkus.filter((item) => item.status === 'ON_SHELF'), [storeSkus]);

  const storeOptions = useMemo(() => stores.map((item) => ({
    label: `${item.storeName} / ${item.storeCode}`,
    value: item.id
  })), [stores]);

  const selectedStoreSku = useMemo(
    () => storeSkus.find((item) => item.id === selectedStoreSkuId),
    [storeSkus, selectedStoreSkuId]
  );

  const selectedPackage = useMemo(
    () => selectedStoreSku?.packages.find((item) => item.packageId === selectedPackageId),
    [selectedPackageId, selectedStoreSku]
  );

  const packageOptions = useMemo(() => (selectedStoreSku?.packages ?? [])
    .filter((item) => item.status === 'ENABLED' || item.packageId === editingOrder?.packageId)
    .map((pkg) => ({
      label: `${pkg.packageName} / ${moneyText(pkg.rentalAmount)}`,
      value: pkg.packageId
    })), [editingOrder, selectedStoreSku]);

  const frameAssetOptions = useMemo(() => assets.filter((item) => item.assetType !== 'BATTERY'
    && (item.status === 'IDLE' || item.id === editingOrder?.frameAssetId)
    && item.currentStoreId === selectedStoreSku?.storeId).map((item) => ({
    label: `${item.serialNo} / ${item.assetCode} / ${item.assetTypeName || primaryAssetTypeText(item)}`,
    value: item.id
  })), [assets, editingOrder, selectedStoreSku]);

  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY'
    && (item.status === 'IDLE' || item.id === editingOrder?.batteryAssetId)
    && item.currentStoreId === selectedStoreSku?.storeId).map((item) => ({
    label: `${item.serialNo} / ${item.assetCode}`,
    value: item.id
  })), [assets, editingOrder, selectedStoreSku]);

  const integratedVehicleSelected = useMemo(
    () => assets.some((item) => item.id === selectedFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedFrameAssetId]
  );

  const pickupFrameAssetOptions = useMemo(() => assets
    .filter((item) => item.assetType !== 'BATTERY'
      && item.status === 'IDLE'
      && item.currentStoreId === selectedOrder?.storeId)
    .map((item) => ({ label: `${item.serialNo} / ${item.assetCode} / ${item.assetTypeName || primaryAssetTypeText(item)}`, value: item.id })), [assets, selectedOrder]);

  const pickupBatteryAssetOptions = useMemo(() => assets
    .filter((item) => item.assetType === 'BATTERY' && item.status === 'IDLE' && item.currentStoreId === selectedOrder?.storeId)
    .map((item) => ({ label: item.serialNo, value: item.id })), [assets, selectedOrder]);

  const pickupIntegratedVehicleSelected = useMemo(
    () => assets.some((item) => item.id === selectedPickupFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedPickupFrameAssetId]
  );

  const replaceAssetOptions = useMemo(() => assets
    .filter((item) => {
      const typeMatches = replaceAssetType === 'VEHICLE_FRAME'
        ? item.assetType !== 'BATTERY'
        : item.assetType === 'BATTERY';
      return typeMatches && item.status === 'IDLE' && item.currentStoreId === selectedOrder?.storeId;
    })
    .map((item) => ({ label: `${item.serialNo} / ${item.assetCode} / ${item.assetTypeName || primaryAssetTypeText(item)}`, value: item.id })),
  [assets, replaceAssetType, selectedOrder]);

  const selectedOrderStoreSku = useMemo(
    () => storeSkus.find((item) => item.id === selectedOrder?.storeSkuId),
    [selectedOrder, storeSkus]
  );

  const returnStoreOptions = useMemo(() => stores
    .filter((store) => store.merchantId === selectedOrder?.merchantId
      && store.status === 'ENABLED'
      && (store.id === selectedOrder?.storeId || selectedOrderStoreSku?.supportCrossStoreReturn === true))
    .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id })), [selectedOrder, selectedOrderStoreSku, stores]);

  useEffect(() => {
    if (integratedVehicleSelected) {
      createForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [createForm, integratedVehicleSelected]);

  useEffect(() => {
    if (pickupIntegratedVehicleSelected) {
      pickupForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [pickupForm, pickupIntegratedVehicleSelected]);

  async function loadAll(filters: OrderFilterForm = filterForm.getFieldsValue()) {
    const [orderData, storeSkuData, assetData, storeData] = await Promise.all([
      http.get<unknown, RentalOrder[]>('/api/admin/orders', { params: filters }),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, Asset[]>('/api/admin/assets'),
      http.get<unknown, Store[]>('/api/admin/stores')
    ]);
    setOrders(orderData);
    setStoreSkus(storeSkuData);
    setAssets(assetData);
    setStores(storeData);
  }

  async function resetFilters() {
    filterForm.resetFields();
    await loadAll({});
  }

  function exportOrders() {
    downloadCsv('订单台账', [
      '序号',
      '订单号',
      '客户姓名',
      '联系电话',
      '用户账号ID',
      '订单状态',
      '门店',
      '商品',
      'SKU',
      '主资产编号',
      '电池号',
      '租金',
      '实际核销金额',
      '签单费',
      '押金',
      '应付金额',
      '已付金额',
      '租期',
      '自动续租',
      '好评赠送天数',
      '活动赠送天数',
      '赠送合计天数',
      '下单时间',
      '预计取车',
      '开始租赁',
      '预计归还',
      '实际归还',
      '系统录入时间'
    ], orders.map((order, index) => [
      index + 1,
      order.orderNo,
      order.customerName,
      order.customerPhone,
      order.userAccountId,
      statusText(order.orderStatus),
      order.storeName || `#${order.storeId}`,
      order.storeSkuName || `#${order.storeSkuId}`,
      order.packageName || `#${order.packageId}`,
      order.frameSerialNo || order.frameAssetCode,
      order.batterySerialNo || order.batteryAssetCode,
      order.rentalAmount,
      order.verificationAmount,
      order.signFeeAmount,
      order.depositAmount,
      order.payableAmount,
      order.paidAmount,
      leaseText(order),
      renewalText(order),
      order.reviewBonusDays,
      order.campaignBonusDays,
      order.totalBonusDays,
      order.orderedAt,
      order.expectedPickupAt,
      order.leaseStartedAt,
      order.expectedReturnAt,
      order.returnedAt,
      order.createdAt
    ]));
  }

  function openCreateOrder() {
    const firstStoreSku = storeSkus.find((item) => item.status === 'ON_SHELF');
    const firstPackage = firstStoreSku?.packages.find((item) => item.status === 'ENABLED');
    createForm.resetFields();
    createForm.setFieldsValue({
      storeSkuId: firstStoreSku?.id,
      packageId: firstPackage?.packageId,
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

  async function submitOrder(values: CreateForm) {
    const editablePayload = {
      userAccountId: values.userAccountId,
      customerName: values.customerName,
      customerPhone: values.customerPhone,
      storeSkuId: values.storeSkuId,
      packageId: values.packageId,
      verificationAmount: values.verificationAmount,
      frameAssetId: values.frameAssetId,
      batteryAssetId: values.batteryAssetId,
      orderedAt: values.orderedAt.format('YYYY-MM-DDTHH:mm:ss')
    };
    if (editingOrder) {
      await http.put(`/api/admin/orders/${editingOrder.id}`, editablePayload);
      message.success('订单资料、账单和分润快照已同步更新');
    } else {
      await http.post('/api/admin/orders', editablePayload);
      message.success('订单已创建，账单计划已生成');
    }
    closeOrderForm();
    await loadAll();
  }

  function openTransition(order: RentalOrder) {
    setSelectedOrder(order);
    transitionForm.resetFields();
    const nextStatus = transitionOptionsFor(order.orderStatus)[0]?.value;
    if (nextStatus) {
      transitionForm.setFieldsValue({ targetStatus: nextStatus });
    }
    setTransitionOpen(true);
  }

  async function submitTransition(values: TransitionForm) {
    if (!selectedOrder) return;
    await http.post(`/api/admin/orders/${selectedOrder.id}/transition`, values);
    setTransitionOpen(false);
    message.success('订单状态已流转');
    await loadAll();
  }

  function openLeaseBonus(order: RentalOrder) {
    setSelectedOrder(order);
    leaseBonusForm.resetFields();
    leaseBonusForm.setFieldsValue({ bonusType: 'REVIEW', bonusDays: 2 });
    setLeaseBonusOpen(true);
  }

  async function submitLeaseBonus(values: LeaseBonusForm) {
    if (!selectedOrder) return;
    await http.post(`/api/admin/orders/${selectedOrder.id}/lease-bonuses`, values);
    setLeaseBonusOpen(false);
    leaseBonusForm.resetFields();
    message.success(`已赠送 ${values.bonusDays} 天租期`);
    await loadAll();
  }

  async function submitCancel(values: ReasonForm) {
    if (!selectedOrder) return;
    await http.post(`/api/admin/orders/${selectedOrder.id}/cancel`, values);
    setCancelOpen(false);
    message.success('订单已取消');
    await loadAll();
  }

  async function submitException(values: ReasonForm) {
    if (!selectedOrder) return;
    await http.post(`/api/admin/orders/${selectedOrder.id}/exception`, values);
    setExceptionOpen(false);
    message.success('订单已标记异常');
    await loadAll();
  }

  function openPickup(order: RentalOrder, mode: 'PICKUP' | 'SHIP') {
    setSelectedOrder(order);
    setPickupMode(mode);
    pickupForm.resetFields();
    pickupForm.setFieldsValue({
      frameAssetId: order.frameAssetId ?? undefined,
      batteryAssetId: order.batteryAssetId ?? undefined,
      remark: mode === 'SHIP' ? '总部免付款发货' : '总部取车交接'
    });
    setPickupOpen(true);
  }

  async function submitPickup(values: PickupForm) {
    if (!selectedOrder) return;
    if (!values.frameAssetId && !values.batteryAssetId) {
      message.error('请至少选择主资产、自定义资产或电池资产');
      return;
    }
    setActionLoading(true);
    try {
      const endpoint = pickupMode === 'SHIP' ? 'ship' : 'pickup-assets';
      await http.post(`/api/admin/orders/${selectedOrder.id}/${endpoint}`, values);
      message.success(pickupMode === 'SHIP' ? '订单已免付款发货并开始租赁' : '取车交接已完成');
      setPickupOpen(false);
      pickupForm.resetFields();
      await loadAll();
    } finally {
      setActionLoading(false);
    }
  }

  function openReplace(order: RentalOrder) {
    setSelectedOrder(order);
    replaceForm.resetFields();
    replaceForm.setFieldsValue({ assetType: 'VEHICLE_FRAME', oldAssetResultStatus: 'IDLE', remark: '总部更换资产' });
    setReplaceOpen(true);
  }

  async function submitReplace(values: ReplaceForm) {
    if (!selectedOrder) return;
    setActionLoading(true);
    try {
      await http.post(`/api/admin/orders/${selectedOrder.id}/replace-asset`, values);
      message.success('订单资产已更换');
      setReplaceOpen(false);
      replaceForm.resetFields();
      await loadAll();
    } finally {
      setActionLoading(false);
    }
  }

  function openReturn(order: RentalOrder) {
    setSelectedOrder(order);
    returnForm.resetFields();
    returnForm.setFieldsValue({
      returnStoreId: order.storeId,
      frameResultStatus: 'IDLE',
      batteryResultStatus: 'IDLE',
      remark: '总部归还资产并结束订单'
    });
    setReturnOpen(true);
  }

  async function submitReturn(values: ReturnForm) {
    if (!selectedOrder) return;
    setActionLoading(true);
    try {
      await http.post(`/api/admin/orders/${selectedOrder.id}/return-assets`, values);
      message.success('资产已归还，订单已完成');
      setReturnOpen(false);
      returnForm.resetFields();
      await loadAll();
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>订单管理</Typography.Title>
        <OrderImportTemplateButton storeSkus={activeStoreSkus} assets={assets} />
        <Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>批量导入</Button>
        <Button type="primary" icon={<PlusOutlined />} disabled={!activeStoreSkus.length} onClick={openCreateOrder}>新建订单</Button>
      </Space>

      <div className="section">
        <Space align="center" className="toolbar" wrap>
          <Typography.Title level={5}>订单列表</Typography.Title>
          <Form form={filterForm} layout="inline" onFinish={(values) => void loadAll(values)}>
            <Form.Item name="keyword">
              <Input allowClear prefix={<SearchOutlined />} placeholder="订单号、客户、电话或资产号" style={{ width: 260 }} />
            </Form.Item>
            <Form.Item name="status">
              <Select allowClear placeholder="订单状态" options={statusOptions} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="storeId">
              <Select allowClear showSearch optionFilterProp="label" placeholder="门店" options={storeOptions} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="userAccountId">
              <InputNumber min={1} precision={0} placeholder="用户账号 ID" style={{ width: 140 }} />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
            <Button onClick={() => void resetFilters()}>重置</Button>
          </Form>
          <Button icon={<ExportOutlined />} disabled={!orders.length} onClick={exportOrders}>导出订单</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={orders}
          pagination={false}
          scroll={{ x: 2320 }}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" className="page-stack">
                <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3, xl: 4 }} bordered>
                  <Descriptions.Item label="客户姓名">{record.customerName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="联系电话">{record.customerPhone || '-'}</Descriptions.Item>
                  <Descriptions.Item label="用户账号">{record.userAccountId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="门店">{record.storeName || `#${record.storeId}`}</Descriptions.Item>
                  <Descriptions.Item label="商品">{record.storeSkuName || `#${record.storeSkuId}`}</Descriptions.Item>
                  <Descriptions.Item label="SKU">{record.packageName || `#${record.packageId}`}</Descriptions.Item>
                  <Descriptions.Item label="主资产">{assetText(record.frameSerialNo, record.frameAssetCode, record.frameAssetId)}</Descriptions.Item>
                  <Descriptions.Item label="电池资产">{assetText(record.batterySerialNo, record.batteryAssetCode, record.batteryAssetId)}</Descriptions.Item>
                  <Descriptions.Item label="租期">{leaseText(record)}</Descriptions.Item>
                  <Descriptions.Item label="自动续租">{renewalText(record)}</Descriptions.Item>
                  <Descriptions.Item label="好评赠送">{record.reviewBonusDays} 天</Descriptions.Item>
                  <Descriptions.Item label="活动赠送">{record.campaignBonusDays} 天</Descriptions.Item>
                  <Descriptions.Item label="赠送合计">{record.totalBonusDays} 天</Descriptions.Item>
                  <Descriptions.Item label="下单时间">{dateText(record.orderedAt)}</Descriptions.Item>
                  <Descriptions.Item label="系统录入时间">{dateText(record.createdAt)}</Descriptions.Item>
                  <Descriptions.Item label="预计取车">{dateText(record.expectedPickupAt)}</Descriptions.Item>
                  <Descriptions.Item label="开始租赁">{dateText(record.leaseStartedAt)}</Descriptions.Item>
                  <Descriptions.Item label="预计归还">{dateText(record.expectedReturnAt)}</Descriptions.Item>
                  <Descriptions.Item label="实际归还">{dateText(record.returnedAt)}</Descriptions.Item>
                  <Descriptions.Item label="租金">{moneyText(record.rentalAmount)}</Descriptions.Item>
                  <Descriptions.Item label="实际核销金额">{moneyText(record.verificationAmount)}</Descriptions.Item>
                  <Descriptions.Item label="签单费">{moneyText(record.signFeeAmount)}</Descriptions.Item>
                  <Descriptions.Item label="押金">{moneyText(record.depositAmount)}</Descriptions.Item>
                  <Descriptions.Item label="分润快照">{record.settlementSnapshotId || '-'}</Descriptions.Item>
                </Descriptions>
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={record.items}
                  pagination={false}
                  columns={[
                    { title: '类型', dataIndex: 'itemType' },
                    { title: '名称', dataIndex: 'itemName' },
                    { title: '数量', dataIndex: 'quantity' },
                    { title: '单价', dataIndex: 'unitAmount' },
                    { title: '小计', dataIndex: 'totalAmount' }
                  ]}
                />
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={record.leaseBonuses}
                  pagination={false}
                  locale={{ emptyText: '暂无赠送租期记录' }}
                  columns={[
                    { title: '赠送类型', dataIndex: 'bonusType', render: leaseBonusTypeText },
                    { title: '天数', dataIndex: 'bonusDays', render: (value: number) => `${value} 天` },
                    { title: '备注', dataIndex: 'remark', render: (value?: string | null) => value || '-' },
                    { title: '顺延前', dataIndex: 'expectedReturnBefore', render: dateText },
                    { title: '顺延后', dataIndex: 'expectedReturnAfter', render: dateText },
                    { title: '操作人', dataIndex: 'operatorAccountId', render: (value?: number | null) => value || '-' },
                    { title: '时间', dataIndex: 'createdAt', render: dateText }
                  ]}
                />
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={record.logs}
                  pagination={false}
                  columns={[
                    { title: '操作', dataIndex: 'operationType' },
                    { title: '原状态', dataIndex: 'fromStatus', render: statusText },
                    { title: '新状态', dataIndex: 'toStatus', render: statusText },
                    { title: '备注', dataIndex: 'remark' },
                    { title: '时间', dataIndex: 'createdAt' }
                  ]}
                />
              </Space>
            )
          }}
          columns={[
            { title: '序号', width: 70, fixed: 'left', render: (_value, _record, index) => index + 1 },
            { title: '订单号', dataIndex: 'orderNo', width: 150, fixed: 'left' },
            { title: '客户姓名', dataIndex: 'customerName', width: 110, render: (value) => value || '-' },
            { title: '联系电话', dataIndex: 'customerPhone', width: 130, render: (value) => value || '-' },
            { title: '状态', dataIndex: 'orderStatus', width: 100, render: (value: OrderStatus) => <Tag>{statusText(value)}</Tag> },
            { title: '门店', width: 150, render: (_, record) => record.storeName || `#${record.storeId}` },
            { title: '商品', width: 170, render: (_, record) => record.storeSkuName || `#${record.storeSkuId}` },
            { title: 'SKU', width: 140, render: (_, record) => record.packageName || `#${record.packageId}` },
            { title: '主资产编号', width: 160, render: (_, record) => assetText(record.frameSerialNo, record.frameAssetCode, record.frameAssetId) },
            { title: '电池号', width: 160, render: (_, record) => assetText(record.batterySerialNo, record.batteryAssetCode, record.batteryAssetId) },
            { title: '实际核销金额', dataIndex: 'verificationAmount', width: 130, render: moneyText },
            { title: '应付', dataIndex: 'payableAmount', width: 100, render: moneyText },
            { title: '已付', dataIndex: 'paidAmount', width: 100, render: moneyText },
            { title: '赠送租期', dataIndex: 'totalBonusDays', width: 100, render: (value: number) => `${value} 天` },
            { title: '预计归还', dataIndex: 'expectedReturnAt', width: 170, render: dateText },
            { title: '下单时间', dataIndex: 'orderedAt', width: 170, render: dateText },
            {
              title: '操作',
              width: 360,
              fixed: 'right',
              render: (_, record) => (
                <Space wrap>
                  {canEditOrder(record) ? (
                    <Button size="small" icon={<EditOutlined />} onClick={() => openEditOrder(record)}>编辑</Button>
                  ) : null}
                  {transitionOptionsFor(record.orderStatus).length ? (
                    <Button size="small" onClick={() => openTransition(record)}>流转</Button>
                  ) : null}
                  {record.orderStatus === 'PENDING_PAYMENT' ? (
                    <Button size="small" icon={<CarOutlined />} onClick={() => openPickup(record, 'SHIP')}>免付款发货</Button>
                  ) : null}
                  {record.orderStatus === 'PENDING_PICKUP' ? (
                    <Button size="small" icon={<CarOutlined />} onClick={() => openPickup(record, 'PICKUP')}>取车交接</Button>
                  ) : null}
                  {canReplaceAsset(record) ? (
                    <Button size="small" icon={<SwapOutlined />} onClick={() => openReplace(record)}>更换资产</Button>
                  ) : null}
                  {canReturnAssets(record) ? (
                    <Button size="small" danger icon={<RollbackOutlined />} onClick={() => openReturn(record)}>归还结束</Button>
                  ) : null}
                  {canGrantLeaseBonus(record) ? (
                    <Button size="small" icon={<GiftOutlined />} onClick={() => openLeaseBonus(record)}>赠送租期</Button>
                  ) : null}
                  {canCancelOrder(record) ? (
                    <Button size="small" onClick={() => { setSelectedOrder(record); cancelForm.resetFields(); setCancelOpen(true); }}>取消</Button>
                  ) : null}
                  {canMarkException(record) ? (
                    <Button size="small" danger onClick={() => { setSelectedOrder(record); exceptionForm.resetFields(); setExceptionOpen(true); }}>异常</Button>
                  ) : null}
                </Space>
              )
            }
          ]}
        />
      </div>

      <Modal title={editingOrder ? '编辑订单' : '新建订单'} open={createOpen} onCancel={closeOrderForm} onOk={() => createForm.submit()} destroyOnHidden>
        <Form form={createForm} layout="vertical" onFinish={submitOrder}>
          <Form.Item name="userAccountId" label="用户账号 ID"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, message: '请输入客户姓名' }]}><Input /></Form.Item>
          <Form.Item name="customerPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]}><Input /></Form.Item>
          <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select
              options={storeSkuOptions}
              onChange={(value) => {
                const nextStoreSku = storeSkus.find((item) => item.id === value);
                const firstPackage = nextStoreSku?.packages.find((item) => item.status === 'ENABLED');
                createForm.setFieldsValue({
                  packageId: firstPackage?.packageId,
                  verificationAmount: firstPackage ? Number(firstPackage.rentalAmount) : undefined,
                  frameAssetId: undefined,
                  batteryAssetId: undefined
                });
              }}
            />
          </Form.Item>
          <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]}>
            <Select
              options={packageOptions}
              onChange={(value) => {
                const nextPackage = selectedStoreSku?.packages.find((item) => item.packageId === value);
                createForm.setFieldValue('verificationAmount', nextPackage ? Number(nextPackage.rentalAmount) : undefined);
              }}
            />
          </Form.Item>
          <Form.Item
            name="verificationAmount"
            label="实际核销金额"
            rules={[{ required: true, message: '请输入实际核销金额' }]}
            extra={selectedPackage ? `当前 SKU 参考价：${moneyText(selectedPackage.rentalAmount)}` : undefined}
          >
            <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              placeholder="输入序列号、资产编号或自定义类型搜索"
              notFoundContent={selectedStoreSku ? '该门店暂无空闲主资产或自定义资产' : '请先选择门店商品'}
              options={frameAssetOptions}
            />
          </Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产（选填）">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              disabled={integratedVehicleSelected}
              placeholder={integratedVehicleSelected ? '车电一体无需独立电池' : '输入电池号或资产编号搜索，可不选'}
              notFoundContent={selectedStoreSku ? '该门店暂无空闲电池资产' : '请先选择门店商品'}
              options={batteryAssetOptions}
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
        endpoint="/api/admin/orders/batch-import"
        onClose={() => setBatchImportOpen(false)}
        onImported={loadAll}
      />

      <Modal
        title="赠送租期"
        open={leaseBonusOpen}
        onCancel={() => setLeaseBonusOpen(false)}
        onOk={() => leaseBonusForm.submit()}
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
        title={pickupMode === 'SHIP' ? '免付款发货' : '取车交接'}
        open={pickupOpen}
        onCancel={() => { setPickupOpen(false); pickupForm.resetFields(); }}
        onOk={() => pickupForm.submit()}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={pickupForm} layout="vertical" onFinish={submitPickup}>
          <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）">
            <Select showSearch allowClear optionFilterProp="label" options={pickupFrameAssetOptions} />
          </Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产">
            <Select
              allowClear
              disabled={pickupIntegratedVehicleSelected}
              placeholder={pickupIntegratedVehicleSelected ? '车电一体无需独立电池' : undefined}
              options={pickupBatteryAssetOptions}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注"><Input maxLength={255} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title="更换资产"
        open={replaceOpen}
        onCancel={() => { setReplaceOpen(false); replaceForm.resetFields(); }}
        onOk={() => replaceForm.submit()}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
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
            <Select options={replaceAssetOptions} />
          </Form.Item>
          <Form.Item name="oldAssetResultStatus" label="原资产状态" rules={[{ required: true, message: '请选择原资产状态' }]}>
            <Select options={assetResultStatusOptions} />
          </Form.Item>
          <Form.Item name="remark" label="备注"><Input maxLength={255} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title="归还资产并结束订单"
        open={returnOpen}
        onCancel={() => { setReturnOpen(false); returnForm.resetFields(); }}
        onOk={() => returnForm.submit()}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={returnForm} layout="vertical" onFinish={submitReturn}>
          <Form.Item name="returnStoreId" label="归还门店" rules={[{ required: true, message: '请选择归还门店' }]}>
            <Select options={returnStoreOptions} />
          </Form.Item>
          {selectedOrder?.frameAssetId ? (
            <Form.Item name="frameResultStatus" label="主资产归还状态" rules={[{ required: true, message: '请选择主资产状态' }]}>
              <Select options={assetResultStatusOptions} />
            </Form.Item>
          ) : null}
          {selectedOrder?.batteryAssetId ? (
            <Form.Item name="batteryResultStatus" label="电池归还状态" rules={[{ required: true, message: '请选择电池状态' }]}>
              <Select options={assetResultStatusOptions} />
            </Form.Item>
          ) : null}
          <Form.Item name="remark" label="备注"><Input maxLength={255} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="订单状态流转" open={transitionOpen} onCancel={() => setTransitionOpen(false)} onOk={() => transitionForm.submit()} destroyOnHidden>
        <Form form={transitionForm} layout="vertical" onFinish={submitTransition}>
          <Form.Item name="targetStatus" label="目标状态" rules={[{ required: true, message: '请选择目标状态' }]}>
            <Select options={selectedOrder ? transitionOptionsFor(selectedOrder.orderStatus) : []} />
          </Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="取消订单" open={cancelOpen} onCancel={() => setCancelOpen(false)} onOk={() => cancelForm.submit()} destroyOnHidden>
        <Form form={cancelForm} layout="vertical" onFinish={submitCancel}>
          <Form.Item name="reason" label="取消原因" rules={[{ required: true, message: '请输入取消原因' }]}><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="标记异常" open={exceptionOpen} onCancel={() => setExceptionOpen(false)} onOk={() => exceptionForm.submit()} destroyOnHidden>
        <Form form={exceptionForm} layout="vertical" onFinish={submitException}>
          <Form.Item name="reason" label="异常原因" rules={[{ required: true, message: '请输入异常原因' }]}><Input /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function statusText(status?: OrderStatus | null) {
  if (!status) return '-';
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function assetText(serialNo?: string | null, assetCode?: string | null, assetId?: number | null) {
  return serialNo || assetCode || (assetId ? `#${assetId}` : '-');
}

function primaryAssetTypeText(asset: Asset) {
  if (asset.assetType === 'INTEGRATED_VEHICLE') return '车电一体';
  if (asset.assetType === 'VEHICLE_FRAME') return '车架';
  return '自定义资产';
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

function moneyText(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function leaseText(order: RentalOrder) {
  return `${order.leaseValue}${order.leaseUnit === 'DAY' ? '天' : '个月'} / ${order.totalPeriods} 期`;
}

function renewalText(order: RentalOrder) {
  if (!order.autoRenewEnabled) return '未开启';
  const unit = order.renewalUnit === 'DAY' ? '天' : '个月';
  return `${order.renewalValue || 1}${unit} / ${moneyText(order.renewalAmount)} / 已续 ${order.renewalCount} 次`;
}

function canGrantLeaseBonus(order: RentalOrder) {
  return !['OVERDUE', 'PENDING_SUPPLEMENT', 'COMPLETED', 'CANCELLED', 'EXCEPTION'].includes(order.orderStatus);
}

function canEditOrder(order: RentalOrder) {
  return order.orderStatus === 'PENDING_PAYMENT' && Number(order.paidAmount || 0) === 0;
}

function transitionOptionsFor(status: OrderStatus) {
  const allowed = normalTransitionMap[status] ?? [];
  return statusOptions.filter((item) => allowed.includes(item.value));
}

function canCancelOrder(order: RentalOrder) {
  return ['PENDING_PAYMENT', 'PENDING_REAL_NAME', 'PENDING_AGREEMENT', 'PENDING_DEPOSIT_AUTH', 'PENDING_VERIFY', 'PENDING_PICKUP'].includes(order.orderStatus);
}

function canMarkException(order: RentalOrder) {
  return !['COMPLETED', 'CANCELLED', 'EXCEPTION'].includes(order.orderStatus);
}

function canReplaceAsset(order: RentalOrder) {
  return ['RENTING', 'PENDING_RETURN', 'PENDING_SUPPLEMENT'].includes(order.orderStatus);
}

function canReturnAssets(order: RentalOrder) {
  return ['RENTING', 'PENDING_RETURN', 'OVERDUE', 'PENDING_SUPPLEMENT'].includes(order.orderStatus);
}

function leaseBonusTypeText(value: 'REVIEW' | 'CAMPAIGN') {
  return value === 'REVIEW' ? '好评赠送' : '活动赠送';
}
