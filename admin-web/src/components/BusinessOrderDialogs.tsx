import { Alert, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Table, Typography, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { Asset, ExternalRentalOrder, RentalOrder, StoreSku } from '../types/api';
import { storeOrderFeeNetAmount } from '../utils/storeRevenue';

export type DashboardBusinessRecord =
  | { sourceType: 'FORMAL'; order: RentalOrder }
  | { sourceType: 'EXTERNAL'; order: ExternalRentalOrder };

type Props = {
  detailRecord: DashboardBusinessRecord | null;
  editingRecord: DashboardBusinessRecord | null;
  storeSkus: StoreSku[];
  assets: Asset[];
  onCloseDetail: () => void;
  onCloseEdit: () => void;
  onUpdated: () => Promise<void> | void;
};

type FormalEditForm = {
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

type ExternalEditForm = {
  sourcePlatform: ExternalRentalOrder['sourcePlatform'];
  externalOrderNo?: string;
  storeSkuId: number;
  packageId: number;
  leaseMultiplier: number;
  customerName: string;
  customerPhone: string;
  rentStartedAt: Dayjs;
  expectedReturnAt?: Dayjs;
  frameAssetId?: number;
  batteryAssetId?: number;
  externalRentalAmount?: number;
  verificationAmount: number;
  signFeeAmount?: number;
  depositAmount?: number;
  remark?: string;
};

const sourceOptions = [
  { label: '抖音', value: 'DOUYIN' },
  { label: '美团', value: 'MEITUAN' },
  { label: '闲鱼', value: 'XIANYU' },
  { label: '线下', value: 'OFFLINE' },
  { label: '其他', value: 'OTHER' }
];

export function BusinessOrderDialogs(props: Props) {
  const [formalForm] = Form.useForm<FormalEditForm>();
  const [externalForm] = Form.useForm<ExternalEditForm>();
  const [submitting, setSubmitting] = useState(false);
  const formalOrder = props.editingRecord?.sourceType === 'FORMAL' ? props.editingRecord.order : null;
  const externalOrder = props.editingRecord?.sourceType === 'EXTERNAL' ? props.editingRecord.order : null;

  const formalStoreSkuId = Form.useWatch('storeSkuId', formalForm);
  const formalPackageId = Form.useWatch('packageId', formalForm);
  const formalLeaseMultiplier = Form.useWatch('leaseMultiplier', formalForm) ?? 1;
  const formalFrameAssetId = Form.useWatch('frameAssetId', formalForm);
  const formalBatteryAssetId = Form.useWatch('batteryAssetId', formalForm);
  const externalStoreSkuId = Form.useWatch('storeSkuId', externalForm);
  const externalPackageId = Form.useWatch('packageId', externalForm);
  const externalLeaseMultiplier = Form.useWatch('leaseMultiplier', externalForm) ?? 1;
  const externalFrameAssetId = Form.useWatch('frameAssetId', externalForm);
  const externalBatteryAssetId = Form.useWatch('batteryAssetId', externalForm);

  const formalStoreSku = useMemo(
    () => props.storeSkus.find((item) => item.id === formalStoreSkuId),
    [formalStoreSkuId, props.storeSkus]
  );
  const formalPackage = useMemo(
    () => formalStoreSku?.packages.find((item) => item.packageId === formalPackageId),
    [formalPackageId, formalStoreSku]
  );
  const externalStoreSku = useMemo(
    () => props.storeSkus.find((item) => item.id === externalStoreSkuId),
    [externalStoreSkuId, props.storeSkus]
  );
  const externalPackage = useMemo(
    () => externalStoreSku?.packages.find((item) => item.packageId === externalPackageId),
    [externalPackageId, externalStoreSku]
  );

  useEffect(() => {
    if (!formalOrder) return;
    formalForm.resetFields();
    formalForm.setFieldsValue({
      userAccountId: formalOrder.userAccountId ?? undefined,
      customerName: formalOrder.customerName || '',
      customerPhone: formalOrder.customerPhone || '',
      storeSkuId: formalOrder.storeSkuId,
      packageId: formalOrder.packageId,
      leaseMultiplier: formalOrder.leaseMultiplier || 1,
      verificationAmount: Number(formalOrder.verificationAmount),
      frameAssetId: formalOrder.frameAssetId ?? undefined,
      batteryAssetId: formalOrder.batteryAssetId ?? undefined,
      orderedAt: dayjs(formalOrder.orderedAt)
    });
  }, [formalForm, formalOrder]);

  useEffect(() => {
    if (!externalOrder) return;
    externalForm.resetFields();
    externalForm.setFieldsValue({
      sourcePlatform: externalOrder.sourcePlatform,
      externalOrderNo: externalOrder.externalOrderNo ?? undefined,
      storeSkuId: externalOrder.storeSkuId,
      packageId: externalOrder.packageId,
      leaseMultiplier: externalOrder.leaseMultiplier || 1,
      customerName: externalOrder.customerName,
      customerPhone: externalOrder.customerPhone,
      rentStartedAt: dayjs(externalOrder.rentStartedAt),
      expectedReturnAt: externalOrder.expectedReturnAt ? dayjs(externalOrder.expectedReturnAt) : undefined,
      frameAssetId: externalOrder.frameAssetId ?? undefined,
      batteryAssetId: externalOrder.batteryAssetId ?? undefined,
      externalRentalAmount: Number(externalOrder.externalRentalAmount),
      verificationAmount: Number(externalOrder.verificationAmount),
      signFeeAmount: Number(externalOrder.signFeeAmount),
      depositAmount: Number(externalOrder.depositAmount),
      remark: externalOrder.remark ?? undefined
    });
  }, [externalForm, externalOrder]);

  const formalStoreSkuOptions = useMemo(() => props.storeSkus
    .filter((item) => item.status === 'ON_SHELF' || item.id === formalOrder?.storeSkuId)
    .map((item) => ({ label: `${item.displayName}${item.storeName ? ` / ${item.storeName}` : ''}`, value: item.id })),
  [formalOrder, props.storeSkus]);
  const formalPackageOptions = useMemo(() => (formalStoreSku?.packages ?? [])
    .filter((item) => item.status === 'ENABLED' || item.packageId === formalOrder?.packageId)
    .map((item) => ({ label: `${item.packageName} / ${money(item.rentalAmount)}`, value: item.packageId })),
  [formalOrder, formalStoreSku]);
  const externalStoreSkuOptions = useMemo(() => props.storeSkus.map((item) => ({
    label: `${item.displayName}${item.storeName ? ` / ${item.storeName}` : ''}`,
    value: item.id
  })), [props.storeSkus]);
  const externalPackageOptions = useMemo(() => (externalStoreSku?.packages ?? []).map((item) => ({
    label: `${item.packageName} / ${item.leaseValue}${item.leaseUnit === 'DAY' ? '天' : '月'} / ${item.totalPeriods}期`,
    value: item.packageId
  })), [externalStoreSku]);

  const formalFrameOptions = useMemo(() => {
    const batteryInvestorId = props.assets.find((item) => item.id === formalBatteryAssetId)?.investorId;
    return props.assets.filter((item) => item.assetType !== 'BATTERY'
      && (item.status === 'IDLE' || item.id === formalOrder?.frameAssetId)
      && item.currentStoreId === formalStoreSku?.storeId
      && (batteryInvestorId == null || item.investorId === batteryInvestorId))
      .map(assetOption);
  }, [formalBatteryAssetId, formalOrder, formalStoreSku, props.assets]);
  const formalBatteryOptions = useMemo(() => {
    const frameInvestorId = props.assets.find((item) => item.id === formalFrameAssetId)?.investorId;
    return props.assets.filter((item) => item.assetType === 'BATTERY'
      && (item.status === 'IDLE' || item.id === formalOrder?.batteryAssetId)
      && item.currentStoreId === formalStoreSku?.storeId
      && (frameInvestorId == null || item.investorId === frameInvestorId))
      .map(assetOption);
  }, [formalFrameAssetId, formalOrder, formalStoreSku, props.assets]);
  const externalFrameOptions = useMemo(() => props.assets.filter((item) => {
    const isCurrent = item.id === externalOrder?.frameAssetId;
    return item.id !== externalBatteryAssetId && (isCurrent || (
      item.status === 'IDLE'
      && item.currentMerchantId === externalStoreSku?.merchantId
      && item.currentStoreId === externalStoreSku?.storeId
    ));
  }).map(assetOption), [externalBatteryAssetId, externalOrder, externalStoreSku, props.assets]);
  const externalBatteryOptions = useMemo(() => props.assets.filter((item) => {
    const isCurrent = item.id === externalOrder?.batteryAssetId;
    return item.id !== externalFrameAssetId && (isCurrent || (
      item.status === 'IDLE'
      && item.currentMerchantId === externalStoreSku?.merchantId
      && item.currentStoreId === externalStoreSku?.storeId
    ));
  }).map(assetOption), [externalFrameAssetId, externalOrder, externalStoreSku, props.assets]);

  const formalIntegratedVehicle = props.assets.some((item) => item.id === formalFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE');

  async function saveFormal(values: FormalEditForm) {
    if (!formalOrder) return;
    setSubmitting(true);
    try {
      await http.put(`/api/admin/orders/${formalOrder.id}`, {
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
      });
      message.success('订单资料、账单和分润快照已同步更新');
      props.onCloseEdit();
      await props.onUpdated();
    } finally {
      setSubmitting(false);
    }
  }

  async function saveExternal(values: ExternalEditForm) {
    if (!externalOrder) return;
    setSubmitting(true);
    try {
      await http.put(`/api/admin/external-orders/${externalOrder.id}`, {
        ...values,
        rentStartedAt: values.rentStartedAt.format('YYYY-MM-DDTHH:mm:ss'),
        expectedReturnAt: values.expectedReturnAt?.format('YYYY-MM-DDTHH:mm:ss')
      });
      message.success(externalOrder.orderStatus === 'ACTIVE'
        ? '补录订单已更新；如修改核销金额，仅从本次修改时间起影响后续续租，首期分润保持不变'
        : '已结束补录订单资料已更新；首期分润保持原快照，终态不会再产生后续续租收益');
      props.onCloseEdit();
      await props.onUpdated();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <Modal
        title={props.detailRecord?.sourceType === 'FORMAL' ? '正式订单详情' : '补录订单详情'}
        open={Boolean(props.detailRecord)}
        onCancel={props.onCloseDetail}
        footer={null}
        width={920}
        destroyOnHidden
      >
        {props.detailRecord?.sourceType === 'FORMAL'
          ? <FormalOrderDetail order={props.detailRecord.order} />
          : props.detailRecord?.sourceType === 'EXTERNAL'
            ? <ExternalOrderDetail order={props.detailRecord.order} />
            : null}
      </Modal>

      <Modal
        title="编辑正式订单"
        open={Boolean(formalOrder)}
        onCancel={props.onCloseEdit}
        onOk={() => formalForm.submit()}
        confirmLoading={submitting}
        okButtonProps={{ disabled: Boolean(formalOrder && !canEditFormalOrder(formalOrder)) }}
        destroyOnHidden
      >
        {formalOrder && !canEditFormalOrder(formalOrder) ? (
          <Alert type="warning" showIcon message="当前订单不可编辑" description={formalEditDisabledReason(formalOrder)} style={{ marginBottom: 16 }} />
        ) : null}
        <Form form={formalForm} layout="vertical" onFinish={saveFormal} disabled={Boolean(formalOrder && !canEditFormalOrder(formalOrder))}>
          <Form.Item name="userAccountId" label="用户账号 ID"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, message: '请输入客户姓名' }]}><Input /></Form.Item>
          <Form.Item name="customerPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]}><Input /></Form.Item>
          <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select options={formalStoreSkuOptions} onChange={(value) => {
              const nextStoreSku = props.storeSkus.find((item) => item.id === value);
              const firstPackage = nextStoreSku?.packages.find((item) => item.status === 'ENABLED');
              formalForm.setFieldsValue({
                packageId: firstPackage?.packageId,
                leaseMultiplier: 1,
                verificationAmount: firstPackage ? Number(firstPackage.rentalAmount) : undefined,
                frameAssetId: undefined,
                batteryAssetId: undefined
              });
            }} />
          </Form.Item>
          <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]}>
            <Select options={formalPackageOptions} onChange={(value) => {
              const nextPackage = formalStoreSku?.packages.find((item) => item.packageId === value);
              formalForm.setFieldValue('verificationAmount', nextPackage ? Number(nextPackage.rentalAmount) * formalLeaseMultiplier : undefined);
            }} />
          </Form.Item>
          <Form.Item name="leaseMultiplier" label="租期倍数" rules={[{ required: true, message: '请输入租期倍数' }]} extra={formalPackage ? leaseMultiplierText(formalPackage, formalLeaseMultiplier) : undefined}>
            <InputNumber min={1} max={120} precision={0} addonAfter="倍" style={{ width: '100%' }} onChange={(value) => formalForm.setFieldValue('verificationAmount', formalPackage && value ? Number(formalPackage.rentalAmount) * Number(value) : undefined)} />
          </Form.Item>
          <Form.Item name="verificationAmount" label="实际核销金额" rules={[{ required: true, message: '请输入实际核销金额' }]}>
            <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）">
            <Select showSearch allowClear optionFilterProp="label" options={formalFrameOptions} />
          </Form.Item>
          <Form.Item name="batteryAssetId" label="电池资产（选填）">
            <Select showSearch allowClear optionFilterProp="label" disabled={formalIntegratedVehicle} placeholder={formalIntegratedVehicle ? '车电一体无需独立电池' : undefined} options={formalBatteryOptions} />
          </Form.Item>
          <Form.Item name="orderedAt" label="下单时间" rules={[{ required: true, message: '请选择下单时间' }]}>
            <DatePicker showTime format="YYYY-MM-DD HH:mm" disabledDate={(current) => current.isAfter(dayjs(), 'day')} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑补录订单"
        open={Boolean(externalOrder)}
        onCancel={props.onCloseEdit}
        onOk={() => externalForm.submit()}
        confirmLoading={submitting}
        width={760}
        destroyOnHidden
      >
        {externalOrder && externalOrder.orderStatus !== 'ACTIVE' ? (
          <Alert type="info" showIcon message="正在修正已结束补录订单" description="终态不会再产生后续续租收益；修改核销金额只记录为历史时间线，首期分润保持原快照。可修正客户资料和来源平台，不能修改门店、资产、办单费或租期结构。" style={{ marginBottom: 16 }} />
        ) : null}
        <Form form={externalForm} layout="vertical" onFinish={saveExternal}>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="sourcePlatform" label="来源平台" rules={[{ required: true, message: '请选择来源平台' }]} style={{ flex: 1 }}><Select options={sourceOptions} /></Form.Item>
            <Form.Item name="externalOrderNo" label="外部订单号" style={{ flex: 1 }}><Input placeholder="可选" /></Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]} style={{ flex: 1 }}>
              <Select showSearch optionFilterProp="label" options={externalStoreSkuOptions} onChange={(value) => {
                const nextStoreSku = props.storeSkus.find((item) => item.id === value);
                const nextPackage = nextStoreSku?.packages.find((item) => item.status === 'ENABLED') ?? nextStoreSku?.packages[0];
                externalForm.setFieldsValue({
                  packageId: nextPackage?.packageId,
                  leaseMultiplier: 1,
                  frameAssetId: undefined,
                  batteryAssetId: undefined,
                  signFeeAmount: Number(nextStoreSku?.signFeeAmount || 0),
                  externalRentalAmount: Number(nextPackage?.rentalAmount || 0),
                  verificationAmount: Number(nextPackage?.rentalAmount || 0),
                  depositAmount: Number(nextPackage?.depositAmount || 0),
                  expectedReturnAt: calculateExpectedReturnAt(externalForm.getFieldValue('rentStartedAt'), nextPackage, 1)
                });
              }} />
            </Form.Item>
            <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]} style={{ flex: 1 }}>
              <Select options={externalPackageOptions} disabled={!externalStoreSku} onChange={(value) => {
                const nextPackage = externalStoreSku?.packages.find((item) => item.packageId === value);
                if (!nextPackage) return;
                externalForm.setFieldsValue({
                  externalRentalAmount: Number(nextPackage.rentalAmount || 0) * externalLeaseMultiplier,
                  verificationAmount: Number(nextPackage.rentalAmount || 0) * externalLeaseMultiplier,
                  depositAmount: Number(nextPackage.depositAmount || 0),
                  expectedReturnAt: calculateExpectedReturnAt(externalForm.getFieldValue('rentStartedAt'), nextPackage, externalLeaseMultiplier)
                });
              }} />
            </Form.Item>
          </Space>
          <Form.Item name="leaseMultiplier" label="租期倍数" rules={[{ required: true, message: '请输入租期倍数' }]} extra={externalPackage ? leaseMultiplierText(externalPackage, externalLeaseMultiplier) : undefined}>
            <InputNumber min={1} max={120} precision={0} addonAfter="倍" style={{ width: '100%' }} onChange={(value) => {
              const multiplier = value || 1;
              if (!externalPackage) return;
              externalForm.setFieldsValue({
                externalRentalAmount: Number(externalPackage.rentalAmount || 0) * multiplier,
                verificationAmount: Number(externalPackage.rentalAmount || 0) * multiplier,
                expectedReturnAt: calculateExpectedReturnAt(externalForm.getFieldValue('rentStartedAt'), externalPackage, multiplier)
              });
            }} />
          </Form.Item>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, message: '请输入客户姓名' }]} style={{ flex: 1 }}><Input /></Form.Item>
            <Form.Item name="customerPhone" label="客户手机号" rules={[{ required: true, message: '请输入客户手机号' }]} style={{ flex: 1 }}><Input /></Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="rentStartedAt" label="起租时间" rules={[{ required: true, message: '请选择起租时间' }]} style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} onChange={(value) => externalForm.setFieldValue('expectedReturnAt', calculateExpectedReturnAt(value || undefined, externalPackage, externalLeaseMultiplier))} />
            </Form.Item>
            <Form.Item name="expectedReturnAt" label="预计归还时间" style={{ flex: 1 }}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="externalRentalAmount" label="外部订单租金" style={{ flex: 1 }}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="verificationAmount" label="实际核销金额" rules={[{ required: true, message: '请输入实际核销金额' }]} style={{ flex: 1 }}><InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} /></Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="signFeeAmount" label="签单费" style={{ flex: 1 }}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="depositAmount" label="押金" style={{ flex: 1 }}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            {externalStoreSku?.needFrameAsset !== false ? <Form.Item name="frameAssetId" label="主资产（不限类型）" rules={externalStoreSku?.needFrameAsset ? [{ required: true, message: '请选择主资产' }] : undefined} style={{ flex: 1 }}><Select showSearch allowClear optionFilterProp="label" options={externalFrameOptions} /></Form.Item> : null}
            {externalStoreSku?.needBatteryAsset !== false ? <Form.Item name="batteryAssetId" label="第二资产（不限类型）" rules={externalStoreSku?.needBatteryAsset ? [{ required: true, message: '请选择第二资产' }] : undefined} style={{ flex: 1 }}><Select showSearch allowClear optionFilterProp="label" options={externalBatteryOptions} /></Form.Item> : null}
          </Space>
          <Form.Item name="remark" label="备注"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>
    </>
  );
}

export function canEditDashboardBusiness(record: DashboardBusinessRecord) {
  return record.sourceType === 'EXTERNAL' || canEditFormalOrder(record.order);
}

export function dashboardBusinessEditReason(record: DashboardBusinessRecord) {
  return record.sourceType === 'EXTERNAL' ? '' : formalEditDisabledReason(record.order);
}

function FormalOrderDetail({ order }: { order: RentalOrder }) {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Descriptions size="small" bordered column={2}>
        <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item>
        <Descriptions.Item label="订单状态">{formalStatusText(order.orderStatus)}</Descriptions.Item>
        <Descriptions.Item label="客户">{order.customerName || '-'}</Descriptions.Item>
        <Descriptions.Item label="手机号">{order.customerPhone || '-'}</Descriptions.Item>
        <Descriptions.Item label="门店">{order.storeName || `#${order.storeId}`}</Descriptions.Item>
        <Descriptions.Item label="门店商品">{order.storeSkuName || `#${order.storeSkuId}`}</Descriptions.Item>
        <Descriptions.Item label="SKU">{order.packageName || `#${order.packageId}`}</Descriptions.Item>
        <Descriptions.Item label="租期">{order.leaseValue}{order.leaseUnit === 'DAY' ? '天' : '个月'} / {order.totalPeriods}期</Descriptions.Item>
        <Descriptions.Item label="主资产">{order.frameSerialNo || order.frameAssetCode || '-'}</Descriptions.Item>
        <Descriptions.Item label="电池资产">{order.batterySerialNo || order.batteryAssetCode || '-'}</Descriptions.Item>
        <Descriptions.Item label="租金">{money(order.rentalAmount)}</Descriptions.Item>
        <Descriptions.Item label="实际核销金额">{money(order.verificationAmount)}</Descriptions.Item>
        <Descriptions.Item label="应付金额">{money(order.payableAmount)}</Descriptions.Item>
        <Descriptions.Item label="已付金额">{money(order.paidAmount)}</Descriptions.Item>
        <Descriptions.Item label="签单费">{money(order.signFeeAmount)}</Descriptions.Item>
        <Descriptions.Item label="押金">{money(order.depositAmount)}</Descriptions.Item>
        <Descriptions.Item label="下单时间">{dateText(order.orderedAt)}</Descriptions.Item>
        <Descriptions.Item label="开始租赁">{dateText(order.leaseStartedAt)}</Descriptions.Item>
        <Descriptions.Item label="预计归还">{dateText(order.expectedReturnAt)}</Descriptions.Item>
        <Descriptions.Item label="实际归还">{dateText(order.returnedAt)}</Descriptions.Item>
      </Descriptions>
      <div>
        <Typography.Title level={5}>订单项目</Typography.Title>
        <Table rowKey="id" size="small" dataSource={order.items} pagination={false} locale={{ emptyText: '暂无订单项目' }} columns={[
          { title: '类型', dataIndex: 'itemType' },
          { title: '名称', dataIndex: 'itemName' },
          { title: '数量', dataIndex: 'quantity' },
          { title: '单价', dataIndex: 'unitAmount', render: money },
          { title: '小计', dataIndex: 'totalAmount', render: money }
        ]} />
      </div>
      <div>
        <Typography.Title level={5}>操作记录</Typography.Title>
        <Table rowKey="id" size="small" dataSource={order.logs} pagination={false} locale={{ emptyText: '暂无操作记录' }} columns={[
          { title: '操作', dataIndex: 'operationType' },
          { title: '原状态', dataIndex: 'fromStatus', render: formalStatusText },
          { title: '新状态', dataIndex: 'toStatus', render: formalStatusText },
          { title: '备注', dataIndex: 'remark', render: textOrDash },
          { title: '时间', dataIndex: 'createdAt', render: dateText }
        ]} />
      </div>
    </Space>
  );
}

function ExternalOrderDetail({ order }: { order: ExternalRentalOrder }) {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Descriptions size="small" bordered column={2}>
        <Descriptions.Item label="台账号">{order.recordNo}</Descriptions.Item>
        <Descriptions.Item label="订单状态">{externalStatusText(order.orderStatus)}</Descriptions.Item>
        <Descriptions.Item label="来源平台">{sourceText(order.sourcePlatform)}</Descriptions.Item>
        <Descriptions.Item label="外部订单号">{textOrDash(order.externalOrderNo)}</Descriptions.Item>
        <Descriptions.Item label="客户">{order.customerName}</Descriptions.Item>
        <Descriptions.Item label="手机号">{order.customerPhone}</Descriptions.Item>
        <Descriptions.Item label="门店">{order.storeName || '-'}</Descriptions.Item>
        <Descriptions.Item label="门店商品">{order.storeSkuDisplayName || '-'}</Descriptions.Item>
        <Descriptions.Item label="SKU">{order.packageName || '-'}</Descriptions.Item>
        <Descriptions.Item label="租期">{order.leaseValue}{order.leaseUnit === 'DAY' ? '天' : '月'} / {order.totalPeriods}期</Descriptions.Item>
        <Descriptions.Item label="主资产">{order.frameAssetSerialNo || '-'}</Descriptions.Item>
        <Descriptions.Item label="第二资产">{order.batteryAssetSerialNo || '-'}</Descriptions.Item>
        <Descriptions.Item label="外部订单租金">{money(order.externalRentalAmount)}</Descriptions.Item>
        <Descriptions.Item label="当前人工核销金额">{money(order.verificationAmount)}</Descriptions.Item>
        <Descriptions.Item label="首期分润基数">{money(order.settlementBaseAmount)}</Descriptions.Item>
        <Descriptions.Item label="平台扣点">{money(order.platformFeeAmount)}</Descriptions.Item>
        <Descriptions.Item label="门店运营分润">{money(order.storeOperationAmount)}</Descriptions.Item>
        <Descriptions.Item label="门店维修分润">{money(order.maintenanceFundAmount)}</Descriptions.Item>
        <Descriptions.Item label="出资方分润">{money(order.investorShareAmount)}</Descriptions.Item>
        <Descriptions.Item label="办单费（原额）">{money(order.signFeeAmount)}</Descriptions.Item>
        <Descriptions.Item label="办单费门店净额（97%）">{money(order.storeOrderFeeAmount ?? storeOrderFeeNetAmount(order.signFeeAmount))}</Descriptions.Item>
        <Descriptions.Item label="门店收益合计">{money(order.storeRevenueAmount ?? (Number(order.storeOperationAmount || 0) + Number(order.maintenanceFundAmount || 0) + (order.storeOrderFeeAmount ?? storeOrderFeeNetAmount(order.signFeeAmount))))}</Descriptions.Item>
        <Descriptions.Item label="押金">{money(order.depositAmount)}</Descriptions.Item>
        <Descriptions.Item label="起租时间">{dateText(order.rentStartedAt)}</Descriptions.Item>
        <Descriptions.Item label="预计归还">{dateText(order.expectedReturnAt)}</Descriptions.Item>
        <Descriptions.Item label="实际结束">{dateText(order.finishedAt)}</Descriptions.Item>
        <Descriptions.Item label="备注">{order.remark || '-'}</Descriptions.Item>
      </Descriptions>
      <div>
        <Typography.Title level={5}>操作记录</Typography.Title>
        <Table rowKey="id" size="small" dataSource={order.logs} pagination={false} locale={{ emptyText: '暂无操作记录' }} columns={[
          { title: '操作', dataIndex: 'operationType' },
          { title: '原状态', dataIndex: 'fromStatus', render: externalStatusText },
          { title: '新状态', dataIndex: 'toStatus', render: externalStatusText },
          { title: '备注', dataIndex: 'remark', render: textOrDash },
          { title: '时间', dataIndex: 'createdAt', render: dateText }
        ]} />
      </div>
    </Space>
  );
}

function canEditFormalOrder(order: RentalOrder) {
  return order.orderStatus === 'PENDING_PAYMENT' && Number(order.paidAmount || 0) === 0;
}

function formalEditDisabledReason(order: RentalOrder) {
  if (Number(order.paidAmount || 0) > 0) return '订单已有实付金额，不能直接修改；请通过退款、取消或业务流转处理。';
  return '只有待支付且未产生支付、代扣或核销流程的订单允许直接编辑。';
}

function calculateExpectedReturnAt(startedAt: Dayjs | undefined, selectedPackage?: StoreSku['packages'][number], leaseMultiplier = 1) {
  if (!startedAt || !selectedPackage) return undefined;
  const leaseValue = selectedPackage.leaseValue * leaseMultiplier;
  return selectedPackage.leaseUnit === 'MONTH' ? startedAt.add(leaseValue * 30, 'day') : startedAt.add(leaseValue, 'day');
}

function leaseMultiplierText(selectedPackage: StoreSku['packages'][number], multiplier: number) {
  return `最终租期：${selectedPackage.leaseValue * multiplier}${selectedPackage.leaseUnit === 'MONTH' ? '个月（每月30天）' : '天'} / ${selectedPackage.totalPeriods * multiplier}期`;
}

function assetOption(asset: Asset) {
  return { label: `${asset.serialNo} / ${asset.assetCode} / ${asset.assetTypeName || asset.assetType}`, value: asset.id };
}

function money(value?: number | string | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function dateText(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

function textOrDash(value?: string | null) {
  return value || '-';
}

function sourceText(value: ExternalRentalOrder['sourcePlatform']) {
  return sourceOptions.find((item) => item.value === value)?.label || value;
}

function formalStatusText(value?: RentalOrder['orderStatus'] | null) {
  if (!value) return '-';
  return ({
    PENDING_PAYMENT: '待支付', PENDING_REAL_NAME: '待实名', PENDING_AGREEMENT: '待签约',
    PENDING_DEPOSIT_AUTH: '待押金授权', PENDING_VERIFY: '待核销', PENDING_PICKUP: '待取车',
    RENTING: '租赁中', PENDING_RETURN: '待归还', OVERDUE: '已逾期', PENDING_SUPPLEMENT: '待补缴',
    COMPLETED: '已完成', CANCELLED: '已取消', EXCEPTION: '异常'
  } as Record<RentalOrder['orderStatus'], string>)[value];
}

function externalStatusText(value?: ExternalRentalOrder['orderStatus'] | null) {
  if (!value) return '-';
  return ({ ACTIVE: '履约中', COMPLETED: '已完成', TERMINATED: '已终止' } as Record<ExternalRentalOrder['orderStatus'], string>)[value];
}
