import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { Asset, OrderStatus, RentalOrder, StoreSku } from '../types/api';

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
  storeSkuId: number;
  packageId: number;
  frameAssetId?: number;
  batteryAssetId?: number;
};

type TransitionForm = {
  targetStatus: OrderStatus;
  remark?: string;
};

type ReasonForm = {
  reason: string;
};

export function OrderManagement() {
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<RentalOrder | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [transitionOpen, setTransitionOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [createForm] = Form.useForm<CreateForm>();
  const [transitionForm] = Form.useForm<TransitionForm>();
  const [cancelForm] = Form.useForm<ReasonForm>();
  const [exceptionForm] = Form.useForm<ReasonForm>();

  useEffect(() => {
    void loadAll();
  }, []);

  const storeSkuOptions = useMemo(() => storeSkus.map((item) => ({
    label: `${item.displayName} / ${item.storeName}`,
    value: item.id
  })), [storeSkus]);

  const packageOptions = useMemo(() => storeSkus.flatMap((item) => item.packages.map((pkg) => ({
    label: `${item.displayName} / ${pkg.packageName}`,
    value: pkg.packageId
  }))), [storeSkus]);

  const frameAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'VEHICLE_FRAME').map((item) => ({
    label: item.serialNo,
    value: item.id
  })), [assets]);

  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY').map((item) => ({
    label: item.serialNo,
    value: item.id
  })), [assets]);

  async function loadAll() {
    const [orderData, storeSkuData, assetData] = await Promise.all([
      http.get<unknown, RentalOrder[]>('/api/admin/orders'),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, Asset[]>('/api/admin/assets')
    ]);
    setOrders(orderData);
    setStoreSkus(storeSkuData);
    setAssets(assetData);
  }

  async function createOrder(values: CreateForm) {
    await http.post('/api/admin/orders', values);
    setCreateOpen(false);
    createForm.resetFields();
    message.success('订单已创建');
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
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>订单账单</Typography.Title>
        <Button type="primary" onClick={() => setCreateOpen(true)}>新建订单</Button>
      </Space>

      <div className="section">
        <Typography.Title level={5}>订单列表</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={orders}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" className="page-stack">
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
            { title: '订单号', dataIndex: 'orderNo' },
            { title: '状态', dataIndex: 'orderStatus', render: (value: OrderStatus) => <Tag>{statusText(value)}</Tag> },
            { title: '门店', dataIndex: 'storeId' },
            { title: '商品', dataIndex: 'storeSkuId' },
            { title: '应付', dataIndex: 'payableAmount' },
            { title: '已付', dataIndex: 'paidAmount' },
            { title: '快照', dataIndex: 'settlementSnapshotId', render: (value) => value || '-' },
            { title: '创建时间', dataIndex: 'createdAt' },
            {
              title: '操作',
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
          <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}><Select options={storeSkuOptions} /></Form.Item>
          <Form.Item name="packageId" label="套餐" rules={[{ required: true, message: '请选择套餐' }]}><Select options={packageOptions} /></Form.Item>
          <Form.Item name="frameAssetId" label="车架资产"><Select allowClear options={frameAssetOptions} /></Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产"><Select allowClear options={batteryAssetOptions} /></Form.Item>
        </Form>
      </Modal>

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
