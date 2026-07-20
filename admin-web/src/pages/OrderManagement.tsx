import { ExportOutlined, PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { OrderBatchImportModal, OrderImportTemplateButton } from '../components/OrderBatchImportModal';
import { http } from '../services/request';
import type { Asset, OrderStatus, RentalOrder, StoreSku } from '../types/api';
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

type CreateForm = {
  userAccountId?: number;
  customerName: string;
  customerPhone: string;
  storeSkuId: number;
  packageId: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  orderedAt: Dayjs;
  expectedPickupAt?: Dayjs;
};

type TransitionForm = {
  targetStatus: OrderStatus;
  remark?: string;
};

type ReasonForm = {
  reason: string;
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
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<RentalOrder | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const [transitionOpen, setTransitionOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [createForm] = Form.useForm<CreateForm>();
  const [transitionForm] = Form.useForm<TransitionForm>();
  const [cancelForm] = Form.useForm<ReasonForm>();
  const [exceptionForm] = Form.useForm<ReasonForm>();
  const [filterForm] = Form.useForm<OrderFilterForm>();
  const selectedStoreSkuId = Form.useWatch('storeSkuId', createForm);
  const selectedFrameAssetId = Form.useWatch('frameAssetId', createForm);

  useEffect(() => {
    void loadAll({});
  }, []);

  const storeSkuOptions = useMemo(() => storeSkus.map((item) => ({
    label: `${item.displayName} / ${item.storeName}`,
    value: item.id
  })), [storeSkus]);

  const storeOptions = useMemo(() => Array.from(new Map(storeSkus.map((item) => [item.storeId, {
    label: item.storeName || `门店 #${item.storeId}`,
    value: item.storeId
  }])).values()), [storeSkus]);

  const selectedStoreSku = useMemo(
    () => storeSkus.find((item) => item.id === selectedStoreSkuId),
    [storeSkus, selectedStoreSkuId]
  );

  const packageOptions = useMemo(() => (selectedStoreSku?.packages ?? [])
    .filter((item) => item.status === 'ENABLED')
    .map((pkg) => ({
      label: `${pkg.packageName} / ${moneyText(pkg.rentalAmount)}`,
      value: pkg.packageId
    })), [selectedStoreSku]);

  const frameAssetOptions = useMemo(() => assets.filter((item) => (item.assetType === 'VEHICLE_FRAME' || item.assetType === 'INTEGRATED_VEHICLE')
    && item.status === 'IDLE'
    && item.currentStoreId === selectedStoreSku?.storeId).map((item) => ({
    label: `${item.serialNo} / ${item.assetType === 'INTEGRATED_VEHICLE' ? '车电一体' : '车架'}`,
    value: item.id
  })), [assets, selectedStoreSku]);

  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY'
    && item.status === 'IDLE'
    && item.currentStoreId === selectedStoreSku?.storeId).map((item) => ({
    label: item.serialNo,
    value: item.id
  })), [assets, selectedStoreSku]);

  const integratedVehicleSelected = useMemo(
    () => assets.some((item) => item.id === selectedFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedFrameAssetId]
  );

  useEffect(() => {
    if (integratedVehicleSelected) {
      createForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [createForm, integratedVehicleSelected]);

  async function loadAll(filters: OrderFilterForm = filterForm.getFieldsValue()) {
    const [orderData, storeSkuData, assetData] = await Promise.all([
      http.get<unknown, RentalOrder[]>('/api/admin/orders', { params: filters }),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, Asset[]>('/api/admin/assets')
    ]);
    setOrders(orderData);
    setStoreSkus(storeSkuData);
    setAssets(assetData);
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
      '车架号',
      '电池号',
      '租金',
      '签单费',
      '押金',
      '应付金额',
      '已付金额',
      '租期',
      '自动续租',
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
      order.signFeeAmount,
      order.depositAmount,
      order.payableAmount,
      order.paidAmount,
      leaseText(order),
      renewalText(order),
      order.orderedAt,
      order.expectedPickupAt,
      order.leaseStartedAt,
      order.expectedReturnAt,
      order.returnedAt,
      order.createdAt
    ]));
  }

  function openCreateOrder() {
    const firstStoreSku = storeSkus[0];
    const firstPackage = firstStoreSku?.packages.find((item) => item.status === 'ENABLED');
    createForm.resetFields();
    createForm.setFieldsValue({
      storeSkuId: firstStoreSku?.id,
      packageId: firstPackage?.packageId,
      orderedAt: dayjs()
    });
    setCreateOpen(true);
  }

  async function createOrder(values: CreateForm) {
    await http.post('/api/admin/orders', {
      ...values,
      orderedAt: values.orderedAt.format('YYYY-MM-DDTHH:mm:ss'),
      expectedPickupAt: values.expectedPickupAt?.format('YYYY-MM-DDTHH:mm:ss')
    });
    setCreateOpen(false);
    createForm.resetFields();
    message.success('订单已创建，账单计划已生成');
    await loadAll();
  }

  function openTransition(order: RentalOrder) {
    setSelectedOrder(order);
    transitionForm.resetFields();
    setTransitionOpen(true);
  }

  async function submitTransition(values: TransitionForm) {
    if (!selectedOrder) return;
    await http.post(`/api/admin/orders/${selectedOrder.id}/transition`, values);
    setTransitionOpen(false);
    message.success('订单状态已流转');
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

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>订单账单</Typography.Title>
        <OrderImportTemplateButton storeSkus={storeSkus} assets={assets} />
        <Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>批量导入</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateOrder}>新建订单</Button>
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
          scroll={{ x: 1980 }}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" className="page-stack">
                <Descriptions size="small" column={4} bordered>
                  <Descriptions.Item label="客户姓名">{record.customerName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="联系电话">{record.customerPhone || '-'}</Descriptions.Item>
                  <Descriptions.Item label="用户账号">{record.userAccountId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="门店">{record.storeName || `#${record.storeId}`}</Descriptions.Item>
                  <Descriptions.Item label="商品">{record.storeSkuName || `#${record.storeSkuId}`}</Descriptions.Item>
                  <Descriptions.Item label="SKU">{record.packageName || `#${record.packageId}`}</Descriptions.Item>
                  <Descriptions.Item label="车架资产">{assetText(record.frameSerialNo, record.frameAssetCode, record.frameAssetId)}</Descriptions.Item>
                  <Descriptions.Item label="电池资产">{assetText(record.batterySerialNo, record.batteryAssetCode, record.batteryAssetId)}</Descriptions.Item>
                  <Descriptions.Item label="租期">{leaseText(record)}</Descriptions.Item>
                  <Descriptions.Item label="自动续租">{renewalText(record)}</Descriptions.Item>
                  <Descriptions.Item label="下单时间">{dateText(record.orderedAt)}</Descriptions.Item>
                  <Descriptions.Item label="系统录入时间">{dateText(record.createdAt)}</Descriptions.Item>
                  <Descriptions.Item label="预计取车">{dateText(record.expectedPickupAt)}</Descriptions.Item>
                  <Descriptions.Item label="开始租赁">{dateText(record.leaseStartedAt)}</Descriptions.Item>
                  <Descriptions.Item label="预计归还">{dateText(record.expectedReturnAt)}</Descriptions.Item>
                  <Descriptions.Item label="实际归还">{dateText(record.returnedAt)}</Descriptions.Item>
                  <Descriptions.Item label="租金">{moneyText(record.rentalAmount)}</Descriptions.Item>
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
            { title: '车架号', width: 160, render: (_, record) => assetText(record.frameSerialNo, record.frameAssetCode, record.frameAssetId) },
            { title: '电池号', width: 160, render: (_, record) => assetText(record.batterySerialNo, record.batteryAssetCode, record.batteryAssetId) },
            { title: '应付', dataIndex: 'payableAmount', width: 100, render: moneyText },
            { title: '已付', dataIndex: 'paidAmount', width: 100, render: moneyText },
            { title: '预计归还', dataIndex: 'expectedReturnAt', width: 170, render: dateText },
            { title: '下单时间', dataIndex: 'orderedAt', width: 170, render: dateText },
            {
              title: '操作',
              width: 190,
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openTransition(record)}>流转</Button>
                  <Button size="small" onClick={() => { setSelectedOrder(record); setCancelOpen(true); }}>取消</Button>
                  <Button size="small" danger onClick={() => { setSelectedOrder(record); setExceptionOpen(true); }}>异常</Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <Modal title="新建订单" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => createForm.submit()} destroyOnHidden>
        <Form form={createForm} layout="vertical" onFinish={createOrder}>
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
                  frameAssetId: undefined,
                  batteryAssetId: undefined
                });
              }}
            />
          </Form.Item>
          <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]}><Select options={packageOptions} /></Form.Item>
          <Form.Item name="frameAssetId" label="车架 / 车电一体资产"><Select allowClear options={frameAssetOptions} /></Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产">
            <Select
              allowClear
              disabled={integratedVehicleSelected}
              placeholder={integratedVehicleSelected ? '车电一体无需独立电池' : undefined}
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
          <Form.Item name="expectedPickupAt" label="预计取车时间">
            <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <OrderBatchImportModal
        open={batchImportOpen}
        endpoint="/api/admin/orders/batch-import"
        onClose={() => setBatchImportOpen(false)}
        onImported={loadAll}
      />

      <Modal title="订单状态流转" open={transitionOpen} onCancel={() => setTransitionOpen(false)} onOk={() => transitionForm.submit()} destroyOnHidden>
        <Form form={transitionForm} layout="vertical" onFinish={submitTransition}>
          <Form.Item name="targetStatus" label="目标状态" rules={[{ required: true, message: '请选择目标状态' }]}><Select options={statusOptions} /></Form.Item>
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
