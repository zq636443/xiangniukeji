import { Alert, Button, DatePicker, Descriptions, Empty, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  ExternalRentalOrder,
  ExternalRentalOrderBatchImportResult,
  ExternalRentalOrderSourcePlatform,
  ExternalRentalOrderStatus,
  Store,
  StoreSku
} from '../types/api';

type Scope = 'admin' | 'merchant';

type Props = {
  scope: Scope;
  storeId?: number;
};

type FilterForm = {
  status?: ExternalRentalOrderStatus;
  sourcePlatform?: ExternalRentalOrderSourcePlatform;
  keyword?: string;
};

type CreateForm = {
  sourcePlatform: ExternalRentalOrderSourcePlatform;
  externalOrderNo?: string;
  storeSkuId: number;
  packageId: number;
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

type CompleteForm = {
  returnStoreId?: number;
  frameResultStatus?: 'IDLE' | 'PENDING_REPAIR' | 'EXCEPTION';
  batteryResultStatus?: 'IDLE' | 'PENDING_REPAIR' | 'EXCEPTION';
  remark?: string;
};

type TerminateForm = CompleteForm & {
  terminationReason: string;
};

type ImportResultRow = {
  lineNo?: number | null;
  success: boolean;
  recordNo?: string | null;
  message: string;
};

const statusOptions: { label: string; value: ExternalRentalOrderStatus; color: string }[] = [
  { label: '进行中', value: 'ACTIVE', color: 'processing' },
  { label: '已完结', value: 'COMPLETED', color: 'success' },
  { label: '已提前终止', value: 'TERMINATED', color: 'default' }
];

const sourceOptions: { label: string; value: ExternalRentalOrderSourcePlatform }[] = [
  { label: '抖音', value: 'DOUYIN' },
  { label: '美团', value: 'MEITUAN' },
  { label: '闲鱼', value: 'XIANYU' },
  { label: '线下', value: 'OFFLINE' },
  { label: '其他', value: 'OTHER' }
];

const returnStatusOptions = [
  { label: '空闲', value: 'IDLE' },
  { label: '待检修', value: 'PENDING_REPAIR' },
  { label: '异常', value: 'EXCEPTION' }
] as const;

export function ExternalOrderManagement({ scope, storeId }: Props) {
  const [orders, setOrders] = useState<ExternalRentalOrder[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [filters, setFilters] = useState<FilterForm>({});
  const [loading, setLoading] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<ExternalRentalOrder | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [importText, setImportText] = useState('');
  const [importResult, setImportResult] = useState<ExternalRentalOrderBatchImportResult | null>(null);
  const [createForm] = Form.useForm<CreateForm>();
  const [completeForm] = Form.useForm<CompleteForm>();
  const [terminateForm] = Form.useForm<TerminateForm>();

  const selectedStoreSkuId = Form.useWatch('storeSkuId', createForm);
  const selectedPackageId = Form.useWatch('packageId', createForm);
  const selectedFrameAssetId = Form.useWatch('frameAssetId', createForm);
  const selectedStoreSku = useMemo(() => storeSkus.find((item) => item.id === selectedStoreSkuId), [storeSkus, selectedStoreSkuId]);
  const selectedPackage = useMemo(
    () => selectedStoreSku?.packages.find((item) => item.packageId === selectedPackageId),
    [selectedStoreSku, selectedPackageId]
  );

  useEffect(() => {
    if (scope === 'merchant' && !storeId) {
      setOrders([]);
      setAssets([]);
      setStoreSkus([]);
      return;
    }
    void loadAll();
  }, [scope, storeId, filters.status, filters.sourcePlatform, filters.keyword]);

  useEffect(() => {
    if (!selectedStoreSku) {
      return;
    }
    createForm.setFieldsValue({
      signFeeAmount: Number(selectedStoreSku.signFeeAmount || 0),
      frameAssetId: selectedStoreSku.needFrameAsset ? createForm.getFieldValue('frameAssetId') : undefined,
      batteryAssetId: selectedStoreSku.needBatteryAsset ? createForm.getFieldValue('batteryAssetId') : undefined
    });
  }, [selectedStoreSku, createForm]);

  useEffect(() => {
    if (!selectedPackage) {
      return;
    }
    createForm.setFieldsValue({
      externalRentalAmount: Number(selectedPackage.rentalAmount || 0),
      verificationAmount: Number(selectedPackage.rentalAmount || 0),
      depositAmount: Number(selectedPackage.depositAmount || 0),
      expectedReturnAt: calculateExpectedReturnAt(createForm.getFieldValue('rentStartedAt'), selectedPackage)
    });
  }, [selectedPackage, createForm]);

  const storeSkuOptions = useMemo(() => {
    return storeSkus.map((item) => ({
      label: `${item.displayName}${item.storeName ? ` / ${item.storeName}` : ''}`,
      value: item.id
    }));
  }, [storeSkus]);

  const packageOptions = useMemo(() => {
    return (selectedStoreSku?.packages ?? []).map((item) => ({
      label: `${item.packageName} / ${item.leaseValue}${item.leaseUnit === 'DAY' ? '天' : '月'} / ${item.totalPeriods}期`,
      value: item.packageId
    }));
  }, [selectedStoreSku]);

  const frameAssetOptions = useMemo(() => {
    return assets
      .filter((item) => item.assetType !== 'BATTERY'
        && item.status === 'IDLE'
        && item.currentMerchantId === selectedStoreSku?.merchantId
        && item.currentStoreId === selectedStoreSku?.storeId)
      .map((item) => ({ label: formatAssetLabel(item), value: item.id }));
  }, [assets, selectedStoreSku]);

  const batteryAssetOptions = useMemo(() => {
    return assets
      .filter((item) => item.assetType === 'BATTERY'
        && item.status === 'IDLE'
        && item.currentMerchantId === selectedStoreSku?.merchantId
        && item.currentStoreId === selectedStoreSku?.storeId)
      .map((item) => ({ label: formatAssetLabel(item), value: item.id }));
  }, [assets, selectedStoreSku]);

  const integratedVehicleSelected = useMemo(
    () => assets.some((item) => item.id === selectedFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedFrameAssetId]
  );

  useEffect(() => {
    if (integratedVehicleSelected) {
      createForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [createForm, integratedVehicleSelected]);

  const storeOptions = useMemo(() => stores.map((item) => ({
    label: `${item.storeName} / ${item.storeCode}`,
    value: item.id
  })), [stores]);

  async function loadAll() {
    if (scope === 'merchant' && !storeId) {
      return;
    }
    setLoading(true);
    try {
      const orderUrl = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      const assetRequest = scope === 'merchant'
        ? http.get<unknown, Asset[]>(`/api/merchant/assets/stores/${storeId}`)
        : http.get<unknown, Asset[]>('/api/admin/assets');
      const storeRequest = scope === 'merchant'
        ? http.get<unknown, Store[]>('/api/merchant/workbench/stores')
        : http.get<unknown, Store[]>('/api/admin/stores');
      const [orderData, storeSkuData, assetData, storeData] = await Promise.all([
        http.get<unknown, ExternalRentalOrder[]>(orderUrl, {
          params: {
            ...(scope === 'merchant' ? { storeId } : {}),
            status: filters.status,
            sourcePlatform: filters.sourcePlatform,
            keyword: filters.keyword
          }
        }),
        http.get<unknown, StoreSku[]>(scope === 'merchant' ? '/api/merchant/products/store-skus' : '/api/admin/products/store-skus', {
          params: scope === 'merchant' ? { storeId } : {}
        }),
        assetRequest,
        storeRequest
      ]);
      setOrders(orderData);
      setStoreSkus(storeSkuData);
      setAssets(assetData);
      setStores(storeData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补录订单加载失败');
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    createForm.resetFields();
    createForm.setFieldsValue({
      sourcePlatform: 'OFFLINE',
      rentStartedAt: dayjs(),
      signFeeAmount: 0,
      externalRentalAmount: 0,
      verificationAmount: 0,
      depositAmount: 0
    });
    setCreateOpen(true);
  }

  function openDetail(record: ExternalRentalOrder) {
    setSelectedOrder(record);
    setDetailOpen(true);
  }

  function openImport() {
    setImportText('');
    setImportResult(null);
    setImportOpen(true);
  }

  function openComplete(record: ExternalRentalOrder) {
    setSelectedOrder(record);
    completeForm.resetFields();
    setCompleteOpen(true);
  }

  function openTerminate(record: ExternalRentalOrder) {
    setSelectedOrder(record);
    terminateForm.resetFields();
    setTerminateOpen(true);
  }

  async function submitCreate(values: CreateForm) {
    setSubmitting(true);
    try {
      await http.post(scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders', {
        ...values,
        rentStartedAt: values.rentStartedAt.format('YYYY-MM-DDTHH:mm:ss'),
        expectedReturnAt: values.expectedReturnAt?.format('YYYY-MM-DDTHH:mm:ss')
      });
      message.success('补录订单已创建');
      setCreateOpen(false);
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  async function submitComplete(values: CompleteForm) {
    if (!selectedOrder) {
      return;
    }
    setSubmitting(true);
    try {
      await http.post(`${scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders'}/${selectedOrder.id}/complete`, values);
      message.success('补录订单已完结');
      setCompleteOpen(false);
      setSelectedOrder(null);
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  async function submitTerminate(values: TerminateForm) {
    if (!selectedOrder) {
      return;
    }
    setSubmitting(true);
    try {
      await http.post(`${scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders'}/${selectedOrder.id}/terminate`, values);
      message.success('补录订单已提前终止');
      setTerminateOpen(false);
      setSelectedOrder(null);
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  async function submitImport() {
    const rows = parseImportRows(importText);
    if (!rows.length) {
      message.error('请先粘贴要导入的数据');
      return;
    }
    setSubmitting(true);
    try {
      const result = await http.post<unknown, ExternalRentalOrderBatchImportResult>(
        `${scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders'}/batch-import`,
        { rows }
      );
      setImportResult(result);
      if (result.failedCount === 0) {
        message.success(`成功导入 ${result.successCount} 条补录订单`);
      } else if (result.successCount > 0) {
        message.warning(`成功 ${result.successCount} 条，失败 ${result.failedCount} 条`);
      } else {
        message.error('导入失败，请检查导入内容');
      }
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  if (scope === 'merchant' && !storeId) {
    return <Empty description="当前账号暂无可操作门店" />;
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar" wrap>
        <Typography.Title level={3}>{scope === 'merchant' ? '外部补录订单' : '外部补录订单台账'}</Typography.Title>
        <Select
          allowClear
          placeholder="订单状态"
          style={{ width: 150 }}
          options={statusOptions.map((item) => ({ label: item.label, value: item.value }))}
          value={filters.status}
          onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
        />
        <Select
          allowClear
          placeholder="来源平台"
          style={{ width: 150 }}
          options={sourceOptions}
          value={filters.sourcePlatform}
          onChange={(value) => setFilters((prev) => ({ ...prev, sourcePlatform: value }))}
        />
        <Input.Search
          allowClear
          placeholder="搜索单号/客户/手机号/资产"
          style={{ width: 260 }}
          onSearch={(value) => setFilters((prev) => ({ ...prev, keyword: value || undefined }))}
        />
        <Button onClick={() => void loadAll()}>刷新</Button>
        <Button onClick={openImport}>批量导入</Button>
        <Button type="primary" onClick={openCreate}>新建补录单</Button>
      </Space>

      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={orders}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无补录订单" /> }}
          columns={[
            { title: '台账号', dataIndex: 'recordNo', width: 140 },
            { title: '来源', dataIndex: 'sourcePlatform', width: 100, render: sourceTag },
            { title: '外部单号', dataIndex: 'externalOrderNo', width: 160, render: textOrDash },
            { title: '客户', width: 150, render: (_, record) => `${record.customerName} / ${record.customerPhone}` },
            ...(scope === 'admin'
              ? [{ title: '门店', width: 180, render: (_: unknown, record: ExternalRentalOrder) => `${record.storeName || '-'} / ${record.storeSkuDisplayName || '-'}` }]
              : [{ title: '商品', width: 180, render: (_: unknown, record: ExternalRentalOrder) => `${record.storeSkuDisplayName || '-'} / ${record.packageName || '-'}` }]),
            {
              title: '绑定资产',
              width: 220,
              render: (_, record) => (
                <div>
                  <div>主资产: {record.frameAssetSerialNo || '-'}</div>
                  <div>电池: {record.batteryAssetSerialNo || '-'}</div>
                </div>
              )
            },
            { title: '租金', dataIndex: 'externalRentalAmount', width: 110, render: moneyText },
            { title: '实际核销金额', dataIndex: 'verificationAmount', width: 130, render: moneyText },
            { title: '签单费', dataIndex: 'signFeeAmount', width: 110, render: moneyText },
            { title: '状态', dataIndex: 'orderStatus', width: 110, render: statusTag },
            { title: '起租时间', dataIndex: 'rentStartedAt', width: 170, render: dateText },
            { title: '预计归还', dataIndex: 'expectedReturnAt', width: 170, render: dateText },
            {
              title: '操作',
              width: 220,
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  {record.orderStatus === 'ACTIVE' ? (
                    <>
                      <Button size="small" onClick={() => openComplete(record)}>完结</Button>
                      <Button size="small" danger onClick={() => openTerminate(record)}>提前终止</Button>
                    </>
                  ) : null}
                </Space>
              )
            }
          ]}
          scroll={{ x: 1700 }}
        />
      </div>

      <Modal
        title="新建补录订单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={submitting}
        width={760}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" onFinish={submitCreate}>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="sourcePlatform" label="来源平台" rules={[{ required: true, message: '请选择来源平台' }]} style={{ flex: 1 }}>
              <Select options={sourceOptions} />
            </Form.Item>
            <Form.Item name="externalOrderNo" label="外部订单号" style={{ flex: 1 }}>
              <Input placeholder="可选" />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]} style={{ flex: 1 }}>
              <Select
                showSearch
                optionFilterProp="label"
                options={storeSkuOptions}
                onChange={() => createForm.setFieldsValue({
                  packageId: undefined,
                  frameAssetId: undefined,
                  batteryAssetId: undefined
                })}
              />
            </Form.Item>
            <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]} style={{ flex: 1 }}>
              <Select options={packageOptions} disabled={!selectedStoreSku} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="customerName" label="客户姓名" rules={[{ required: true, message: '请输入客户姓名' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="customerPhone" label="客户手机号" rules={[{ required: true, message: '请输入客户手机号' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="rentStartedAt" label="起租时间" rules={[{ required: true, message: '请选择起租时间' }]} style={{ flex: 1 }}>
              <DatePicker
                showTime
                style={{ width: '100%' }}
                onChange={(value) => {
                  if (!selectedPackage) {
                    return;
                  }
                  createForm.setFieldValue('expectedReturnAt', calculateExpectedReturnAt(value, selectedPackage));
                }}
              />
            </Form.Item>
            <Form.Item name="expectedReturnAt" label="预计归还时间" style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="externalRentalAmount" label="外部订单租金" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="verificationAmount"
              label="实际核销金额"
              rules={[{ required: true, message: '请输入实际核销金额' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="signFeeAmount" label="签单费" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="depositAmount" label="押金" style={{ flex: 1 }}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            {selectedStoreSku?.needFrameAsset !== false ? (
              <Form.Item name="frameAssetId" label="主资产（支持全部自定义类型）" rules={selectedStoreSku?.needFrameAsset ? [{ required: true, message: '请选择主资产或自定义资产' }] : undefined} style={{ flex: 1 }}>
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  placeholder="输入序列号、资产编号或自定义类型搜索"
                  notFoundContent={selectedStoreSku ? '该门店暂无空闲主资产或自定义资产' : '请先选择门店商品'}
                  options={frameAssetOptions}
                />
              </Form.Item>
            ) : null}
            {selectedStoreSku?.needBatteryAsset !== false ? (
              <Form.Item name="batteryAssetId" label="电池资产" rules={selectedStoreSku?.needBatteryAsset && !integratedVehicleSelected ? [{ required: true, message: '请选择电池资产' }] : undefined} style={{ flex: 1 }}>
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  disabled={integratedVehicleSelected}
                  placeholder={integratedVehicleSelected ? '车电一体无需独立电池' : '输入电池号或资产编号搜索'}
                  notFoundContent={selectedStoreSku ? '该门店暂无空闲电池资产' : '请先选择门店商品'}
                  options={batteryAssetOptions}
                />
              </Form.Item>
            ) : null}
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量导入补录订单"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onOk={() => void submitImport()}
        confirmLoading={submitting}
        width={960}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="一行一单，支持英文逗号或 Tab 分隔"
            description="字段顺序：来源平台,外部订单号,门店商品ID,SKU ID,客户姓名,客户手机号,起租时间,预计归还时间,主资产ID,电池资产ID,外部订单租金,实际核销金额,签单费,押金,备注。主资产支持车架、车电一体和全部自定义类型。"
          />
          <Input.TextArea
            rows={10}
            value={importText}
            onChange={(event) => setImportText(event.target.value)}
            placeholder={[
              'MEITUAN,MT-001,1,2,张三,13800138000,2026-07-19 10:00:00,2026-08-19 10:00:00,101,202,399,368.50,30,0,历史在租订单',
              'OFFLINE,,1,1,李四,13900139000,2026-07-18 09:30:00,,103,,39,35,20,0,线下老单补录'
            ].join('\n')}
          />
          {importResult ? (
            <div className="section">
              <Space align="center" wrap>
                <Typography.Text>总计：{importResult.totalCount}</Typography.Text>
                <Typography.Text type="success">成功：{importResult.successCount}</Typography.Text>
                <Typography.Text type="danger">失败：{importResult.failedCount}</Typography.Text>
              </Space>
              <Table<ImportResultRow>
                rowKey={(_, index) => `${index}`}
                size="small"
                dataSource={importResult.results}
                pagination={false}
                columns={[
                  { title: '行号', dataIndex: 'lineNo', render: (value) => value ?? '-' },
                  { title: '结果', dataIndex: 'success', render: (value: boolean) => <Tag color={value ? 'green' : 'red'}>{value ? '成功' : '失败'}</Tag> },
                  { title: '台账号', dataIndex: 'recordNo', render: textOrDash },
                  { title: '说明', dataIndex: 'message' }
                ]}
              />
            </div>
          ) : null}
        </Space>
      </Modal>

      <Modal
        title="正常完结补录订单"
        open={completeOpen}
        onCancel={() => setCompleteOpen(false)}
        onOk={() => completeForm.submit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={completeForm} layout="vertical" onFinish={submitComplete}>
          <Form.Item name="returnStoreId" label="归还门店">
            <Select allowClear options={storeOptions} placeholder="不选则默认原提车门店" />
          </Form.Item>
          <Form.Item name="frameResultStatus" label="车架归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="电池归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="提前终止补录订单"
        open={terminateOpen}
        onCancel={() => setTerminateOpen(false)}
        onOk={() => terminateForm.submit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={terminateForm} layout="vertical" onFinish={submitTerminate}>
          <Form.Item name="terminationReason" label="终止原因" rules={[{ required: true, message: '请输入终止原因' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="returnStoreId" label="归还门店">
            <Select allowClear options={storeOptions} placeholder="不选则默认原提车门店" />
          </Form.Item>
          <Form.Item name="frameResultStatus" label="车架归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="电池归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="补录订单详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={920}
        destroyOnHidden
      >
        {selectedOrder ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="台账号">{selectedOrder.recordNo}</Descriptions.Item>
              <Descriptions.Item label="订单状态">{statusText(selectedOrder.orderStatus)}</Descriptions.Item>
              <Descriptions.Item label="来源平台">{sourceText(selectedOrder.sourcePlatform)}</Descriptions.Item>
              <Descriptions.Item label="外部订单号">{textOrDash(selectedOrder.externalOrderNo)}</Descriptions.Item>
              <Descriptions.Item label="客户">{selectedOrder.customerName}</Descriptions.Item>
              <Descriptions.Item label="手机号">{selectedOrder.customerPhone}</Descriptions.Item>
              <Descriptions.Item label="门店">{selectedOrder.storeName || '-'}</Descriptions.Item>
              <Descriptions.Item label="门店商品">{selectedOrder.storeSkuDisplayName || '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU">{selectedOrder.packageName || '-'}</Descriptions.Item>
              <Descriptions.Item label="租期">{leaseText(selectedOrder.leaseUnit, selectedOrder.leaseValue, selectedOrder.totalPeriods)}</Descriptions.Item>
              <Descriptions.Item label="主资产">{selectedOrder.frameAssetSerialNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="电池资产">{selectedOrder.batteryAssetSerialNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="外部订单租金">{moneyText(selectedOrder.externalRentalAmount)}</Descriptions.Item>
              <Descriptions.Item label="实际核销金额">{moneyText(selectedOrder.verificationAmount)}</Descriptions.Item>
              <Descriptions.Item label="签单费">{moneyText(selectedOrder.signFeeAmount)}</Descriptions.Item>
              <Descriptions.Item label="押金">{moneyText(selectedOrder.depositAmount)}</Descriptions.Item>
              <Descriptions.Item label="起租时间">{dateText(selectedOrder.rentStartedAt)}</Descriptions.Item>
              <Descriptions.Item label="预计归还">{dateText(selectedOrder.expectedReturnAt)}</Descriptions.Item>
              <Descriptions.Item label="实际结束">{dateText(selectedOrder.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="归还门店">{selectedOrder.returnStoreName || '-'}</Descriptions.Item>
              <Descriptions.Item label="终止原因">{selectedOrder.terminationReason || '-'}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{selectedOrder.remark || '-'}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5}>操作记录</Typography.Title>
              <Table
                rowKey="id"
                size="small"
                dataSource={selectedOrder.logs}
                pagination={false}
                columns={[
                  { title: '操作', dataIndex: 'operationType', render: operationText },
                  { title: '原状态', dataIndex: 'fromStatus', render: statusText },
                  { title: '新状态', dataIndex: 'toStatus', render: statusText },
                  { title: '备注', dataIndex: 'remark', render: textOrDash },
                  { title: '时间', dataIndex: 'createdAt', render: dateText }
                ]}
              />
            </div>
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
}

function formatAssetLabel(asset: Asset) {
  const type = asset.assetTypeName || (asset.assetType === 'INTEGRATED_VEHICLE' ? '车电一体' : asset.assetType === 'VEHICLE_FRAME' ? '车架' : asset.assetType === 'BATTERY' ? '电池' : '自定义资产');
  return `${asset.serialNo} / ${asset.assetCode} / ${type}${asset.storeName ? ` / ${asset.storeName}` : ''}`;
}

function calculateExpectedReturnAt(startedAt: Dayjs | undefined, selectedPackage?: StoreSku['packages'][number]) {
  if (!startedAt || !selectedPackage) {
    return undefined;
  }
  return selectedPackage.leaseUnit === 'MONTH'
    ? startedAt.add(selectedPackage.leaseValue, 'month')
    : startedAt.add(selectedPackage.leaseValue, 'day');
}

function sourceText(value?: ExternalRentalOrderSourcePlatform | null) {
  if (!value) {
    return '-';
  }
  return sourceOptions.find((item) => item.value === value)?.label ?? value;
}

function sourceTag(value?: ExternalRentalOrderSourcePlatform | null) {
  return <Tag>{sourceText(value)}</Tag>;
}

function statusText(value?: string | null) {
  if (!value) {
    return '-';
  }
  return statusOptions.find((item) => item.value === value)?.label ?? value;
}

function operationText(value?: string | null) {
  if (!value) {
    return '-';
  }
  if (value === 'CREATE') {
    return '创建';
  }
  if (value === 'COMPLETE') {
    return '正常完结';
  }
  if (value === 'TERMINATE') {
    return '提前终止';
  }
  return value;
}

function statusTag(value?: ExternalRentalOrderStatus | null) {
  const matched = statusOptions.find((item) => item.value === value);
  return <Tag color={matched?.color}>{matched?.label ?? value ?? '-'}</Tag>;
}

function moneyText(value?: number | null) {
  if (value === null || value === undefined) {
    return '-';
  }
  return `¥${Number(value).toFixed(2)}`;
}

function dateText(value?: string | null) {
  if (!value) {
    return '-';
  }
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

function textOrDash(value?: string | null) {
  return value || '-';
}

function leaseText(leaseUnit?: string | null, leaseValue?: number | null, totalPeriods?: number | null) {
  if (!leaseUnit || !leaseValue || !totalPeriods) {
    return '-';
  }
  return `${leaseValue}${leaseUnit === 'DAY' ? '天' : '月'} / ${totalPeriods}期`;
}

function parseImportRows(input: string) {
  return input
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      const columns = line.includes('\t') ? line.split('\t') : line.split(',');
      const values = columns.map((item) => item.trim());
      return {
        lineNo: index + 1,
        sourcePlatform: values[0],
        externalOrderNo: emptyToUndefined(values[1]),
        storeSkuId: Number(values[2]),
        packageId: Number(values[3]),
        customerName: values[4],
        customerPhone: values[5],
        rentStartedAt: normalizeDateText(values[6]),
        expectedReturnAt: normalizeDateText(values[7]),
        frameAssetId: parseOptionalNumber(values[8]),
        batteryAssetId: parseOptionalNumber(values[9]),
        externalRentalAmount: parseOptionalNumber(values[10]),
        verificationAmount: parseOptionalNumber(values[11]),
        signFeeAmount: parseOptionalNumber(values[12]),
        depositAmount: parseOptionalNumber(values[13]),
        remark: emptyToUndefined(values[14])
      };
    });
}

function parseOptionalNumber(value?: string) {
  return value ? Number(value) : undefined;
}

function normalizeDateText(value?: string) {
  return value ? value.replace(' ', 'T') : undefined;
}

function emptyToUndefined(value?: string) {
  return value ? value : undefined;
}
