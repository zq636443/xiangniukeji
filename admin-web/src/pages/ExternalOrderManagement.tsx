import { DeleteOutlined, DollarOutlined, EditOutlined } from '@ant-design/icons';
import { Alert, Button, Checkbox, DatePicker, Descriptions, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  ExternalRentalOrder,
  ExternalRentalOrderBatchImportResult,
  ExternalOrderPricingBatchResult,
  ExternalOrderPricingPreview,
  ExternalOrderPricingRevision,
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
  storeId?: number;
  status?: ExternalRentalOrderStatus;
  sourcePlatform?: ExternalRentalOrderSourcePlatform;
  storeSkuId?: number;
  packageId?: number;
  rentStartedRange?: [Dayjs, Dayjs];
  expectedReturnRange?: [Dayjs, Dayjs];
  keyword?: string;
};

type PricingForm = {
  autoRenewEnabled: boolean;
  renewalUnit?: 'DAY' | 'MONTH';
  renewalValue?: number;
  renewalAmount?: number;
  renewalBillingMode: 'PERIOD' | 'DAILY_CAPPED';
  renewalDailyAmount?: number;
  renewalDailyCapEnabled: boolean;
  renewalGraceHours: number;
  overdueDailyAmount?: number;
  reason: string;
  customerConfirmed: boolean;
  confirmationMethod?: 'WECHAT' | 'PHONE' | 'PAPER' | 'OTHER';
  confirmationReference?: string;
  customerConfirmedAt?: Dayjs;
};

type ConfirmPricingForm = {
  confirmationMethod: 'WECHAT' | 'PHONE' | 'PAPER' | 'OTHER';
  confirmationReference: string;
  customerConfirmedAt?: Dayjs;
};

type CreateForm = {
  sourcePlatform: ExternalRentalOrderSourcePlatform;
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
  const [editingOrder, setEditingOrder] = useState<ExternalRentalOrder | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [pricingOpen, setPricingOpen] = useState(false);
  const [batchPricingOpen, setBatchPricingOpen] = useState(false);
  const [confirmPricingOpen, setConfirmPricingOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deletingOrderId, setDeletingOrderId] = useState<number | null>(null);
  const [importText, setImportText] = useState('');
  const [importResult, setImportResult] = useState<ExternalRentalOrderBatchImportResult | null>(null);
  const [pricingRevisions, setPricingRevisions] = useState<ExternalOrderPricingRevision[]>([]);
  const [pricingPreview, setPricingPreview] = useState<ExternalOrderPricingPreview | null>(null);
  const [batchPricingResult, setBatchPricingResult] = useState<ExternalOrderPricingBatchResult | null>(null);
  const [selectedPricingRevision, setSelectedPricingRevision] = useState<ExternalOrderPricingRevision | null>(null);
  const [selectedOrderIds, setSelectedOrderIds] = useState<number[]>([]);
  const [createForm] = Form.useForm<CreateForm>();
  const [completeForm] = Form.useForm<CompleteForm>();
  const [terminateForm] = Form.useForm<TerminateForm>();
  const [pricingForm] = Form.useForm<PricingForm>();
  const [batchPricingForm] = Form.useForm<PricingForm>();
  const [confirmPricingForm] = Form.useForm<ConfirmPricingForm>();

  const selectedStoreSkuId = Form.useWatch('storeSkuId', createForm);
  const selectedPackageId = Form.useWatch('packageId', createForm);
  const selectedLeaseMultiplier = Form.useWatch('leaseMultiplier', createForm) ?? 1;
  const selectedFrameAssetId = Form.useWatch('frameAssetId', createForm);
  const selectedBatteryAssetId = Form.useWatch('batteryAssetId', createForm);
  const pricingBillingMode = Form.useWatch('renewalBillingMode', pricingForm);
  const pricingEnabled = Form.useWatch('autoRenewEnabled', pricingForm);
  const pricingCustomerConfirmed = Form.useWatch('customerConfirmed', pricingForm);
  const batchPricingBillingMode = Form.useWatch('renewalBillingMode', batchPricingForm);
  const batchPricingEnabled = Form.useWatch('autoRenewEnabled', batchPricingForm);
  const batchCustomerConfirmed = Form.useWatch('customerConfirmed', batchPricingForm);
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
  }, [scope, storeId, filters.storeId, filters.status, filters.sourcePlatform, filters.storeSkuId, filters.packageId, filters.rentStartedRange, filters.expectedReturnRange, filters.keyword]);

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
      .filter((item) => {
        const isCurrentAsset = item.id === editingOrder?.frameAssetId;
        return item.id !== selectedBatteryAssetId
          && (isCurrentAsset || (
            item.status === 'IDLE'
            && item.currentMerchantId === selectedStoreSku?.merchantId
            && item.currentStoreId === selectedStoreSku?.storeId
          ));
      })
      .map((item) => ({ label: formatAssetLabel(item), value: item.id }));
  }, [assets, editingOrder, selectedBatteryAssetId, selectedStoreSku]);

  const batteryAssetOptions = useMemo(() => {
    return assets
      .filter((item) => {
        const isCurrentAsset = item.id === editingOrder?.batteryAssetId;
        return item.id !== selectedFrameAssetId
          && (isCurrentAsset || (
            item.status === 'IDLE'
            && item.currentMerchantId === selectedStoreSku?.merchantId
            && item.currentStoreId === selectedStoreSku?.storeId
          ));
      })
      .map((item) => ({ label: formatAssetLabel(item), value: item.id }));
  }, [assets, editingOrder, selectedFrameAssetId, selectedStoreSku]);

  const storeOptions = useMemo(() => stores.map((item) => ({
    label: `${item.storeName} / ${item.storeCode}`,
    value: item.id
  })), [stores]);

  const filterStoreSkus = useMemo(
    () => storeSkus.filter((item) => !filters.storeId || item.storeId === filters.storeId),
    [storeSkus, filters.storeId]
  );

  const filterPackages = useMemo(() => {
    const selected = storeSkus.find((item) => item.id === filters.storeSkuId);
    return selected?.packages ?? [];
  }, [storeSkus, filters.storeSkuId]);

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
            storeId: scope === 'merchant' ? storeId : filters.storeId,
            status: filters.status,
            sourcePlatform: filters.sourcePlatform,
            storeSkuId: filters.storeSkuId,
            packageId: filters.packageId,
            rentStartedFrom: filters.rentStartedRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
            rentStartedTo: filters.rentStartedRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
            expectedReturnFrom: filters.expectedReturnRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
            expectedReturnTo: filters.expectedReturnRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
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
      setSelectedOrderIds((previous) => {
        const visibleOrderIds = new Set(orderData.map((item) => item.id));
        return previous.filter((id) => visibleOrderIds.has(id));
      });
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
    const firstStoreSku = storeSkus[0];
    const firstPackage = firstStoreSku?.packages.find((item) => item.status === 'ENABLED') ?? firstStoreSku?.packages[0];
    const rentStartedAt = dayjs();
    createForm.resetFields();
    createForm.setFieldsValue({
      sourcePlatform: 'OFFLINE',
      storeSkuId: firstStoreSku?.id,
      packageId: firstPackage?.packageId,
      leaseMultiplier: 1,
      rentStartedAt,
      expectedReturnAt: calculateExpectedReturnAt(rentStartedAt, firstPackage, 1),
      signFeeAmount: Number(firstStoreSku?.signFeeAmount || 0),
      externalRentalAmount: Number(firstPackage?.rentalAmount || 0),
      depositAmount: Number(firstPackage?.depositAmount || 0)
    });
    setEditingOrder(null);
    setCreateOpen(true);
  }

  function openEdit(record: ExternalRentalOrder) {
    setEditingOrder(record);
    createForm.resetFields();
    createForm.setFieldsValue({
      sourcePlatform: record.sourcePlatform,
      externalOrderNo: record.externalOrderNo ?? undefined,
      storeSkuId: record.storeSkuId,
      packageId: record.packageId,
      leaseMultiplier: record.leaseMultiplier || 1,
      customerName: record.customerName,
      customerPhone: record.customerPhone,
      rentStartedAt: dayjs(record.rentStartedAt),
      expectedReturnAt: record.expectedReturnAt ? dayjs(record.expectedReturnAt) : undefined,
      frameAssetId: record.frameAssetId ?? undefined,
      batteryAssetId: record.batteryAssetId ?? undefined,
      externalRentalAmount: Number(record.externalRentalAmount),
      verificationAmount: Number(record.verificationAmount),
      signFeeAmount: Number(record.signFeeAmount),
      depositAmount: Number(record.depositAmount),
      remark: record.remark ?? undefined
    });
    setCreateOpen(true);
  }

  function closeOrderForm() {
    setCreateOpen(false);
    setEditingOrder(null);
    createForm.resetFields();
  }

  function handleStoreSkuChange(value: number) {
    const nextStoreSku = storeSkus.find((item) => item.id === value);
    const nextPackage = nextStoreSku?.packages.find((item) => item.status === 'ENABLED') ?? nextStoreSku?.packages[0];
    const retainCurrentAssets = editingOrder?.orderStatus === 'ACTIVE'
      && editingOrder.skuId === nextStoreSku?.skuId
      && editingOrder.merchantId === nextStoreSku?.merchantId;
    createForm.setFieldsValue({
      packageId: nextPackage?.packageId,
      leaseMultiplier: 1,
      frameAssetId: retainCurrentAssets ? editingOrder.frameAssetId ?? undefined : undefined,
      batteryAssetId: retainCurrentAssets ? editingOrder.batteryAssetId ?? undefined : undefined,
      signFeeAmount: Number(nextStoreSku?.signFeeAmount || 0),
      externalRentalAmount: Number(nextPackage?.rentalAmount || 0),
      depositAmount: Number(nextPackage?.depositAmount || 0),
      expectedReturnAt: calculateExpectedReturnAt(createForm.getFieldValue('rentStartedAt'), nextPackage, 1)
    });
  }

  function handlePackageChange(value: number) {
    const nextPackage = selectedStoreSku?.packages.find((item) => item.packageId === value);
    if (!nextPackage) {
      return;
    }
    const multiplier = createForm.getFieldValue('leaseMultiplier') || 1;
    createForm.setFieldsValue({
      externalRentalAmount: Number(nextPackage.rentalAmount || 0) * multiplier,
      depositAmount: Number(nextPackage.depositAmount || 0),
      expectedReturnAt: calculateExpectedReturnAt(createForm.getFieldValue('rentStartedAt'), nextPackage, multiplier)
    });
  }

  function handleLeaseMultiplierChange(value: number | null) {
    const multiplier = value || 1;
    if (!selectedPackage) {
      return;
    }
    createForm.setFieldsValue({
      externalRentalAmount: Number(selectedPackage.rentalAmount || 0) * multiplier,
      expectedReturnAt: calculateExpectedReturnAt(createForm.getFieldValue('rentStartedAt'), selectedPackage, multiplier)
    });
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

  function openPricing(record: ExternalRentalOrder) {
    setSelectedOrder(record);
    setPricingRevisions([]);
    pricingForm.resetFields();
    pricingForm.setFieldsValue(pricingFormValues(record));
    setPricingOpen(true);
    void loadPricingRevisions(record.id);
  }

  function openBatchPricing() {
    const referenceOrder = selectedOrderIds.length
      ? orders.find((item) => item.id === selectedOrderIds[0])
      : orders.find((item) => item.orderStatus === 'ACTIVE');
    batchPricingForm.resetFields();
    batchPricingForm.setFieldsValue(pricingFormValues(referenceOrder));
    setPricingPreview(null);
    setBatchPricingResult(null);
    setBatchPricingOpen(true);
  }

  async function loadPricingRevisions(orderId: number) {
    const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
    const revisions = await http.get<unknown, ExternalOrderPricingRevision[]>(
      `${endpoint}/${orderId}/renewal-pricing-revisions`
    );
    setPricingRevisions(revisions);
  }

  async function submitPricing(values: PricingForm) {
    if (!selectedOrder) return;
    setSubmitting(true);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      const revision = await http.post<unknown, ExternalOrderPricingRevision>(
        `${endpoint}/${selectedOrder.id}/renewal-pricing-adjustments`,
        pricingPayload(values)
      );
      if (revision.revisionStatus === 'APPLIED') {
        message.success('补录订单续租规则已生效');
      } else {
        message.warning('调价记录已保存，需完成人工客户确认后生效');
      }
      await loadPricingRevisions(selectedOrder.id);
      await loadAll();
      setPricingOpen(false);
    } finally {
      setSubmitting(false);
    }
  }

  async function previewBatchPricing() {
    const values = await batchPricingForm.validateFields();
    setSubmitting(true);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      const preview = await http.post<unknown, ExternalOrderPricingPreview>(
        `${endpoint}/renewal-pricing/batch-preview`,
        { filter: pricingFilterPayload(), adjustment: pricingPayload(values) }
      );
      setPricingPreview(preview);
      setBatchPricingResult(null);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitBatchPricing(values: PricingForm) {
    if (!pricingPreview) {
      message.warning('请先预览命中范围');
      return;
    }
    setSubmitting(true);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      const result = await http.post<unknown, ExternalOrderPricingBatchResult>(
        `${endpoint}/renewal-pricing/batch-adjust`,
        {
          filter: pricingFilterPayload(),
          adjustment: pricingPayload(values),
          expectedMatchedCount: pricingPreview.matchedCount
        }
      );
      setBatchPricingResult(result);
      setPricingPreview(null);
      message.success(`批次 ${result.batchNo} 已处理：成功 ${result.successCount}，失败 ${result.failedCount}`);
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  function openConfirmPricing(revision: ExternalOrderPricingRevision) {
    setSelectedPricingRevision(revision);
    confirmPricingForm.resetFields();
    confirmPricingForm.setFieldsValue({ confirmationMethod: 'WECHAT', customerConfirmedAt: dayjs() });
    setConfirmPricingOpen(true);
  }

  async function submitConfirmPricing(values: ConfirmPricingForm) {
    if (!selectedPricingRevision || !selectedOrder) return;
    setSubmitting(true);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      await http.post(`${endpoint}/renewal-pricing-revisions/${selectedPricingRevision.id}/confirm`, {
        ...values,
        customerConfirmedAt: values.customerConfirmedAt?.format('YYYY-MM-DDTHH:mm:ss')
      });
      message.success('人工确认已登记，续租规则已生效');
      setConfirmPricingOpen(false);
      setSelectedPricingRevision(null);
      await loadPricingRevisions(selectedOrder.id);
      await loadAll();
    } finally {
      setSubmitting(false);
    }
  }

  function pricingFilterPayload() {
    if (selectedOrderIds.length) {
      return {
        orderIds: selectedOrderIds,
        storeId: scope === 'merchant' ? storeId : undefined
      };
    }
    return {
      storeId: scope === 'merchant' ? storeId : filters.storeId,
      status: filters.status,
      sourcePlatform: filters.sourcePlatform,
      storeSkuId: filters.storeSkuId,
      packageId: filters.packageId,
      rentStartedFrom: filters.rentStartedRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      rentStartedTo: filters.rentStartedRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      expectedReturnFrom: filters.expectedReturnRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      expectedReturnTo: filters.expectedReturnRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      keyword: filters.keyword
    };
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
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      const payload = {
        ...values,
        rentStartedAt: values.rentStartedAt.format('YYYY-MM-DDTHH:mm:ss'),
        expectedReturnAt: values.expectedReturnAt?.format('YYYY-MM-DDTHH:mm:ss')
      };
      if (editingOrder) {
        await http.put(`${endpoint}/${editingOrder.id}`, payload);
        message.success('补录订单已更新，分润已按最新核销金额重新计算');
      } else {
        await http.post(endpoint, payload);
        message.success('补录订单已创建并计入分润');
      }
      closeOrderForm();
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

  async function deleteOrder(record: ExternalRentalOrder) {
    setDeletingOrderId(record.id);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      await http.delete(`${endpoint}/${record.id}`);
      message.success('补录订单已删除，未结算收益已同步撤销');
      if (selectedOrder?.id === record.id) {
        setSelectedOrder(null);
        setDetailOpen(false);
      }
      await loadAll();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补录订单删除失败');
    } finally {
      setDeletingOrderId(null);
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
        {scope === 'admin' ? (
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="门店"
            style={{ width: 190 }}
            options={storeOptions}
            value={filters.storeId}
            onChange={(value) => setFilters((prev) => ({ ...prev, storeId: value, storeSkuId: undefined, packageId: undefined }))}
          />
        ) : null}
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
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="门店商品"
          style={{ width: 210 }}
          options={filterStoreSkus.map((item) => ({ label: item.displayName, value: item.id }))}
          value={filters.storeSkuId}
          onChange={(value) => setFilters((prev) => ({ ...prev, storeSkuId: value, packageId: undefined }))}
        />
        <Select
          allowClear
          placeholder="SKU"
          style={{ width: 170 }}
          options={filterPackages.map((item) => ({ label: item.packageName, value: item.packageId }))}
          value={filters.packageId}
          disabled={!filters.storeSkuId}
          onChange={(value) => setFilters((prev) => ({ ...prev, packageId: value }))}
        />
        <DatePicker.RangePicker
          placeholder={['起租开始', '起租结束']}
          value={filters.rentStartedRange}
          onChange={(value) => setFilters((prev) => ({ ...prev, rentStartedRange: value as [Dayjs, Dayjs] | undefined }))}
        />
        <DatePicker.RangePicker
          placeholder={['预计归还开始', '预计归还结束']}
          value={filters.expectedReturnRange}
          onChange={(value) => setFilters((prev) => ({ ...prev, expectedReturnRange: value as [Dayjs, Dayjs] | undefined }))}
        />
        <Input.Search
          allowClear
          placeholder="搜索单号/客户/手机号/资产"
          style={{ width: 260 }}
          onSearch={(value) => setFilters((prev) => ({ ...prev, keyword: value || undefined }))}
        />
        <Button onClick={() => void loadAll()}>刷新</Button>
        <Button icon={<DollarOutlined />} onClick={openBatchPricing}>
          {selectedOrderIds.length ? `调价已选 ${selectedOrderIds.length} 单` : '按条件批量调价'}
        </Button>
        <Button onClick={openImport}>批量导入</Button>
        <Button type="primary" onClick={openCreate}>新建补录单</Button>
      </Space>

      <div className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={orders}
          rowSelection={{
            selectedRowKeys: selectedOrderIds,
            onChange: (keys) => setSelectedOrderIds(keys.map(Number))
          }}
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
                  <div>第二资产: {record.batteryAssetSerialNo || '-'}</div>
                </div>
              )
            },
            { title: '租金', dataIndex: 'externalRentalAmount', width: 110, render: moneyText },
            { title: '实际核销金额', dataIndex: 'verificationAmount', width: 130, render: moneyText },
            {
              title: '分润结果',
              width: 150,
              render: (_, record) => record.settlementSnapshotId ? (
                <div>
                  <div>门店 {moneyText(record.storeOperationAmount)}</div>
                  <div>出资方 {moneyText(record.investorShareAmount)}</div>
                </div>
              ) : <Tag color="warning">待计算</Tag>
            },
            { title: '签单费', dataIndex: 'signFeeAmount', width: 110, render: moneyText },
            { title: '续租规则', width: 260, render: (_, record) => externalRenewalText(record) },
            { title: '状态', dataIndex: 'orderStatus', width: 110, render: statusTag },
            { title: '起租时间', dataIndex: 'rentStartedAt', width: 170, render: dateText },
            { title: '预计归还', dataIndex: 'expectedReturnAt', width: 170, render: dateText },
            {
              title: '操作',
              width: 470,
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
                  {record.orderStatus === 'ACTIVE' ? (
                    <>
                      <Button size="small" icon={<DollarOutlined />} onClick={() => openPricing(record)}>续租调价</Button>
                      <Button size="small" onClick={() => openComplete(record)}>完结</Button>
                      <Button size="small" danger onClick={() => openTerminate(record)}>提前终止</Button>
                    </>
                  ) : null}
                  <Popconfirm
                    title="确认删除补录订单？"
                    description="删除会撤销未结算收益，并释放进行中订单占用的资产；已进入月结单或收益已结算的订单不能删除。"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => deleteOrder(record)}
                  >
                    <Button
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      loading={deletingOrderId === record.id}
                    >
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
          scroll={{ x: 2200 }}
        />
      </div>

      <Modal
        title={editingOrder ? '编辑补录订单' : '新建补录订单'}
        open={createOpen}
        onCancel={closeOrderForm}
        onOk={() => createForm.submit()}
        confirmLoading={submitting}
        width={760}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" onFinish={submitCreate}>
          {editingOrder && editingOrder.orderStatus !== 'ACTIVE' ? (
            <Alert
              type="info"
              showIcon
              message="正在修正已结束补录订单"
              description="保存后会按最新核销金额和资产归属重算分润，但不会重新占用已归还资产。"
              style={{ marginBottom: 16 }}
            />
          ) : null}
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
                onChange={handleStoreSkuChange}
              />
            </Form.Item>
            <Form.Item name="packageId" label="SKU" rules={[{ required: true, message: '请选择 SKU' }]} style={{ flex: 1 }}>
              <Select options={packageOptions} disabled={!selectedStoreSku} onChange={handlePackageChange} />
            </Form.Item>
          </Space>
          <Form.Item
            name="leaseMultiplier"
            label="租期倍数"
            rules={[{ required: true, message: '请输入租期倍数' }]}
            extra={selectedPackage
              ? `最终租期：${selectedPackage.leaseValue * selectedLeaseMultiplier}${selectedPackage.leaseUnit === 'MONTH' ? '个月（每月30天）' : '天'} / ${selectedPackage.totalPeriods * selectedLeaseMultiplier}期`
              : '例如 1个月 SKU 选择 2 倍，即租用 2个月（60天）'}
          >
            <InputNumber min={1} max={120} precision={0} addonAfter="倍" style={{ width: '100%' }} onChange={handleLeaseMultiplierChange} />
          </Form.Item>
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
                  createForm.setFieldValue('expectedReturnAt', calculateExpectedReturnAt(value, selectedPackage, selectedLeaseMultiplier));
                }}
              />
            </Form.Item>
            <Form.Item name="expectedReturnAt" label="预计归还时间" style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item name="externalRentalAmount" label="月租金额（默认 SKU 金额）" style={{ flex: 1 }}>
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
              <Form.Item name="frameAssetId" label="主资产（不限类型）" rules={selectedStoreSku?.needFrameAsset ? [{ required: true, message: '请选择主资产' }] : undefined} style={{ flex: 1 }}>
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  placeholder="输入序列号、资产编号或类型搜索"
                  notFoundContent={selectedStoreSku ? '该门店暂无可用空闲资产' : '请先选择门店商品'}
                  options={frameAssetOptions}
                />
              </Form.Item>
            ) : null}
            {selectedStoreSku?.needBatteryAsset !== false ? (
              <Form.Item name="batteryAssetId" label="第二资产（不限类型）" rules={selectedStoreSku?.needBatteryAsset ? [{ required: true, message: '请选择第二资产' }] : undefined} style={{ flex: 1 }}>
                <Select
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  placeholder="输入序列号、资产编号或类型搜索"
                  notFoundContent={selectedStoreSku ? '该门店暂无其他可用空闲资产' : '请先选择门店商品'}
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
            description="字段顺序：来源平台,外部订单号,门店商品ID,SKU ID,租期倍数,客户姓名,客户手机号,起租时间,预计归还时间,主资产ID,第二资产ID,外部订单租金,实际核销金额,签单费,押金,备注。两个资产栏均不限制资产类型；月租统一按30天计算；旧版不含租期倍数的15列格式仍兼容，默认1倍。"
          />
          <Input.TextArea
            rows={10}
            value={importText}
            onChange={(event) => setImportText(event.target.value)}
            placeholder="请粘贴正式订单数据，每行一单"
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
          <Form.Item name="frameResultStatus" label="主资产归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="第二资产归还状态">
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
          <Form.Item name="frameResultStatus" label="主资产归还状态">
            <Select allowClear options={returnStatusOptions as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <Form.Item name="batteryResultStatus" label="第二资产归还状态">
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
              <Descriptions.Item label="续租规则" span={2}>{externalRenewalText(selectedOrder)}</Descriptions.Item>
              <Descriptions.Item label="主资产">{selectedOrder.frameAssetSerialNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="第二资产">{selectedOrder.batteryAssetSerialNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="外部订单租金">{moneyText(selectedOrder.externalRentalAmount)}</Descriptions.Item>
              <Descriptions.Item label="实际核销金额">{moneyText(selectedOrder.verificationAmount)}</Descriptions.Item>
              <Descriptions.Item label="分润快照">{selectedOrder.settlementSnapshotNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="分润基数">{moneyText(selectedOrder.settlementBaseAmount)}</Descriptions.Item>
              <Descriptions.Item label="渠道核销扣点">{moneyText(selectedOrder.channelFeeAmount)}</Descriptions.Item>
              <Descriptions.Item label="平台扣点">{moneyText(selectedOrder.platformFeeAmount)}</Descriptions.Item>
              <Descriptions.Item label="门店运营分润">{moneyText(selectedOrder.storeOperationAmount)}</Descriptions.Item>
              <Descriptions.Item label="门店维修分润">{moneyText(selectedOrder.maintenanceFundAmount)}</Descriptions.Item>
              <Descriptions.Item label="门店合计分润">{moneyText(Number(selectedOrder.storeOperationAmount || 0) + Number(selectedOrder.maintenanceFundAmount || 0))}</Descriptions.Item>
              <Descriptions.Item label="渠道引流分润">{moneyText(selectedOrder.channelReferralAmount)}</Descriptions.Item>
              <Descriptions.Item label="出资方分润">{moneyText(selectedOrder.investorShareAmount)}</Descriptions.Item>
              <Descriptions.Item label="签单费">{moneyText(selectedOrder.signFeeAmount)}</Descriptions.Item>
              <Descriptions.Item label="押金">{moneyText(selectedOrder.depositAmount)}</Descriptions.Item>
              <Descriptions.Item label="起租时间">{dateText(selectedOrder.rentStartedAt)}</Descriptions.Item>
              <Descriptions.Item label="预计归还">{dateText(selectedOrder.expectedReturnAt)}</Descriptions.Item>
              <Descriptions.Item label="实际结束">{dateText(selectedOrder.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="归还门店">{selectedOrder.returnStoreName || '-'}</Descriptions.Item>
              <Descriptions.Item label="终止原因" span={2}>{selectedOrder.terminationReason || '-'}</Descriptions.Item>
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

      <Modal
        title="补录订单续租调价"
        open={pricingOpen}
        onCancel={() => setPricingOpen(false)}
        onOk={() => pricingForm.submit()}
        confirmLoading={submitting}
        width={820}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message={selectedOrder ? `${selectedOrder.recordNo} / ${selectedOrder.customerName}` : '续租调价'}
            description="降价、关闭自动续租等对客户有利的变更可立即生效；涨价、新增收费、切换计费模式等需登记人工客户确认，未确认前保持旧规则。"
          />
          <Form form={pricingForm} layout="vertical" onFinish={submitPricing}>
            {renewalPricingFields(pricingEnabled, pricingBillingMode, pricingCustomerConfirmed)}
          </Form>
          <div>
            <Typography.Title level={5}>调价历史</Typography.Title>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={pricingRevisions}
              columns={[
                { title: '批次', dataIndex: 'batchNo', render: textOrDash },
                { title: '状态', dataIndex: 'revisionStatus', render: pricingRevisionTag },
                { title: '原因', dataIndex: 'reason', ellipsis: true },
                { title: '确认方式', dataIndex: 'confirmationMethod', render: confirmationMethodText },
                { title: '创建时间', dataIndex: 'createdAt', render: dateText },
                {
                  title: '操作',
                  render: (_, record) => record.revisionStatus === 'PENDING_CUSTOMER_CONFIRMATION' ? (
                    <Button size="small" onClick={() => openConfirmPricing(record)}>登记确认并生效</Button>
                  ) : '-'
                }
              ]}
            />
          </div>
        </Space>
      </Modal>

      <Modal
        title="外部补录订单批量续租调价"
        open={batchPricingOpen}
        onCancel={() => setBatchPricingOpen(false)}
        width={960}
        destroyOnHidden
        footer={[
          <Button key="cancel" onClick={() => setBatchPricingOpen(false)}>关闭</Button>,
          <Button key="preview" loading={submitting} onClick={() => void previewBatchPricing()}>预览命中范围</Button>,
          <Popconfirm
            key="apply"
            title="确认执行批量调价？"
            description={pricingPreview ? `将按预览的 ${pricingPreview.matchedCount} 笔命中订单执行，数量变化时后端会拦截。` : '请先预览范围。'}
            onConfirm={() => batchPricingForm.submit()}
            disabled={!pricingPreview}
          >
            <Button type="primary" loading={submitting} disabled={!pricingPreview}>执行批量调价</Button>
          </Popconfirm>
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            message={selectedOrderIds.length ? `按已勾选的 ${selectedOrderIds.length} 笔订单处理` : '按当前页面筛选条件处理'}
            description="执行前必须先预览。如果勾选“已逐单人工确认”，请确保命中的每位客户均已确认；否则需确认的变更会批量生成待确认记录，不会直接生效。"
          />
          <Form
            form={batchPricingForm}
            layout="vertical"
            onFinish={submitBatchPricing}
            onValuesChange={() => { setPricingPreview(null); setBatchPricingResult(null); }}
          >
            {renewalPricingFields(batchPricingEnabled, batchPricingBillingMode, batchCustomerConfirmed)}
          </Form>
          {pricingPreview ? (
            <Alert
              type={pricingPreview.blockedPendingCount || pricingPreview.skippedInactiveCount ? 'warning' : 'success'}
              showIcon
              message={`命中 ${pricingPreview.matchedCount} 笔，可处理 ${pricingPreview.eligibleCount} 笔`}
              description={`立即生效 ${pricingPreview.immediateApplyCount} 笔；已确认生效 ${pricingPreview.confirmedApplyCount} 笔；待人工确认 ${pricingPreview.pendingConfirmationCount} 笔；规则相同 ${pricingPreview.unchangedCount} 笔；已有待确认 ${pricingPreview.blockedPendingCount} 笔；非进行中 ${pricingPreview.skippedInactiveCount} 笔。`}
            />
          ) : null}
          {batchPricingResult ? (
            <div className="section">
              <Typography.Text>批次：{batchPricingResult.batchNo}</Typography.Text>
              <Table
                rowKey="externalOrderId"
                size="small"
                pagination={{ pageSize: 10 }}
                dataSource={batchPricingResult.results}
                columns={[
                  { title: '台账号', dataIndex: 'recordNo' },
                  { title: '结果', dataIndex: 'success', render: (value) => <Tag color={value ? 'green' : 'red'}>{value ? '成功' : '失败'}</Tag> },
                  { title: '状态', dataIndex: 'revisionStatus', render: (value) => value ? pricingRevisionTag(value) : '-' },
                  { title: '说明', dataIndex: 'message' }
                ]}
              />
            </div>
          ) : null}
        </Space>
      </Modal>

      <Modal
        title="登记客户确认"
        open={confirmPricingOpen}
        onCancel={() => setConfirmPricingOpen(false)}
        onOk={() => confirmPricingForm.submit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={confirmPricingForm} layout="vertical" onFinish={submitConfirmPricing}>
          <Form.Item name="confirmationMethod" label="确认方式" rules={[{ required: true }]}>
            <Select options={confirmationMethodOptions} />
          </Form.Item>
          <Form.Item name="confirmationReference" label="确认凭证/备注" rules={[{ required: true, message: '请填写微信记录、电话记录、纸质文件编号或其他说明' }]}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
          <Form.Item name="customerConfirmedAt" label="确认时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function formatAssetLabel(asset: Asset) {
  const type = asset.assetTypeName || (asset.assetType === 'INTEGRATED_VEHICLE' ? '车电一体' : asset.assetType === 'VEHICLE_FRAME' ? '车架' : asset.assetType === 'BATTERY' ? '电池' : '自定义资产');
  return `${asset.serialNo} / ${asset.assetCode} / ${type}${asset.storeName ? ` / ${asset.storeName}` : ''}`;
}

function calculateExpectedReturnAt(startedAt: Dayjs | undefined, selectedPackage?: StoreSku['packages'][number], leaseMultiplier = 1) {
  if (!startedAt || !selectedPackage) {
    return undefined;
  }
  const leaseValue = selectedPackage.leaseValue * leaseMultiplier;
  return selectedPackage.leaseUnit === 'MONTH'
    ? startedAt.add(leaseValue * 30, 'day')
    : startedAt.add(leaseValue, 'day');
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
  if (value === 'EDIT') {
    return '编辑';
  }
  if (value === 'RENEWAL_PRICING_ADJUSTMENT') {
    return '续租调价';
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

const confirmationMethodOptions = [
  { label: '微信确认', value: 'WECHAT' },
  { label: '电话确认', value: 'PHONE' },
  { label: '纸质文件', value: 'PAPER' },
  { label: '其他方式', value: 'OTHER' }
];

function pricingFormValues(order?: ExternalRentalOrder): Partial<PricingForm> {
  return {
    autoRenewEnabled: order?.autoRenewEnabled ?? true,
    renewalUnit: order?.renewalUnit ?? 'MONTH',
    renewalValue: order?.renewalValue ?? 1,
    renewalAmount: order?.renewalAmount == null ? undefined : Number(order.renewalAmount),
    renewalBillingMode: order?.renewalBillingMode ?? 'PERIOD',
    renewalDailyAmount: order?.renewalDailyAmount == null ? undefined : Number(order.renewalDailyAmount),
    renewalDailyCapEnabled: order?.renewalDailyCapEnabled ?? true,
    renewalGraceHours: order?.renewalGraceHours ?? 0,
    overdueDailyAmount: order?.overdueDailyAmount == null ? undefined : Number(order.overdueDailyAmount),
    reason: '',
    customerConfirmed: false,
    confirmationMethod: 'WECHAT',
    confirmationReference: '',
    customerConfirmedAt: dayjs()
  };
}

function pricingPayload(values: PricingForm) {
  const enabled = Boolean(values.autoRenewEnabled);
  const dailyMode = enabled && values.renewalBillingMode === 'DAILY_CAPPED';
  const customerConfirmed = Boolean(values.customerConfirmed);
  return {
    autoRenewEnabled: enabled,
    renewalUnit: enabled ? values.renewalUnit : null,
    renewalValue: enabled ? values.renewalValue : null,
    renewalAmount: enabled ? values.renewalAmount : null,
    renewalBillingMode: enabled ? values.renewalBillingMode : 'PERIOD',
    renewalDailyAmount: dailyMode ? values.renewalDailyAmount : null,
    renewalDailyCapEnabled: dailyMode ? values.renewalDailyCapEnabled : true,
    renewalGraceHours: dailyMode ? values.renewalGraceHours : 0,
    overdueDailyAmount: dailyMode ? values.overdueDailyAmount : null,
    reason: values.reason.trim(),
    customerConfirmed,
    confirmationMethod: customerConfirmed ? values.confirmationMethod : null,
    confirmationReference: customerConfirmed ? values.confirmationReference?.trim() : null,
    customerConfirmedAt: customerConfirmed ? values.customerConfirmedAt?.format('YYYY-MM-DDTHH:mm:ss') : null
  };
}

function renewalPricingFields(
  enabled?: boolean,
  billingMode?: PricingForm['renewalBillingMode'],
  customerConfirmed?: boolean
) {
  return (
    <>
      <Form.Item name="autoRenewEnabled" valuePropName="checked">
        <Checkbox>开启自动续租</Checkbox>
      </Form.Item>
      <Space.Compact block>
        <Form.Item
          style={{ width: '34%' }}
          name="renewalUnit"
          label="整期单位"
          rules={enabled ? [{ required: true, message: '请选择整期单位' }] : []}
        >
          <Select
            disabled={!enabled}
            options={[{ label: '天', value: 'DAY' }, { label: '月', value: 'MONTH' }]}
          />
        </Form.Item>
        <Form.Item
          style={{ width: '33%' }}
          name="renewalValue"
          label="整期周期"
          rules={enabled ? [{ required: true, message: '请输入整期周期' }] : []}
        >
          <InputNumber disabled={!enabled} min={1} max={3650} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          style={{ width: '33%' }}
          name="renewalAmount"
          label="整期续租价"
          rules={enabled ? [{ required: true, message: '请输入整期续租价' }] : []}
        >
          <InputNumber disabled={!enabled} min={0.01} precision={2} addonBefore="¥" style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>
      <Form.Item name="renewalBillingMode" label="续租计费方式" rules={[{ required: true, message: '请选择续租计费方式' }]}>
        <Select
          disabled={!enabled}
          options={[
            { label: '只按整期续租', value: 'PERIOD' },
            { label: '按日计费（可选整期封顶）', value: 'DAILY_CAPPED' }
          ]}
        />
      </Form.Item>
      {enabled && billingMode === 'DAILY_CAPPED' ? (
        <>
          <Space.Compact block>
            <Form.Item
              style={{ width: '34%' }}
              name="renewalDailyAmount"
              label="正常日续租价"
              rules={[{ required: true, message: '请输入正常日续租价' }]}
            >
              <InputNumber min={0.01} precision={2} addonBefore="¥" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item style={{ width: '33%' }} name="overdueDailyAmount" label="逾期日占用费">
              <InputNumber min={0.01} precision={2} addonBefore="¥" placeholder="默认同日续租价" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              style={{ width: '33%' }}
              name="renewalGraceHours"
              label="宽限小时"
              rules={[{ required: true, message: '请输入宽限小时' }]}
            >
              <InputNumber min={0} max={72} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="renewalDailyCapEnabled" valuePropName="checked">
            <Checkbox>按日累计达到整期续租价后封顶</Checkbox>
          </Form.Item>
        </>
      ) : null}
      <Form.Item name="reason" label="调整原因" rules={[{ required: true, message: '请输入调价原因' }]}>
        <Input.TextArea rows={3} maxLength={255} showCount />
      </Form.Item>
      <Form.Item name="customerConfirmed" valuePropName="checked">
        <Checkbox>已逐单人工取得客户确认，本次直接生效</Checkbox>
      </Form.Item>
      {customerConfirmed ? (
        <>
          <Alert
            type="warning"
            showIcon
            message="请如实登记每位客户的确认凭证"
            description="批量操作勾选后，系统会把同一确认信息写入每笔命中订单的调价历史。建议在凭证说明中填写批次记录位置或逐单确认清单。"
            style={{ marginBottom: 16 }}
          />
          <Space.Compact block>
            <Form.Item
              style={{ width: '38%' }}
              name="confirmationMethod"
              label="确认方式"
              rules={[{ required: true, message: '请选择确认方式' }]}
            >
              <Select options={[...confirmationMethodOptions]} />
            </Form.Item>
            <Form.Item style={{ width: '62%' }} name="customerConfirmedAt" label="确认时间">
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
          </Space.Compact>
          <Form.Item
            name="confirmationReference"
            label="确认凭证/备注"
            rules={[{ required: true, message: '请填写微信记录、电话记录、纸质文件编号或其他说明' }]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </>
      ) : null}
    </>
  );
}

function externalRenewalText(order: ExternalRentalOrder) {
  if (!order.autoRenewEnabled) {
    return '不自动续租';
  }
  const unit = order.renewalUnit === 'DAY' ? '天' : '月';
  const periodText = `每 ${order.renewalValue || 1}${unit} ${moneyText(order.renewalAmount)}`;
  if (order.renewalBillingMode !== 'DAILY_CAPPED') {
    return periodText;
  }
  const capText = order.renewalDailyCapEnabled ? `，整期封顶 ${moneyText(order.renewalAmount)}` : '，不设整期封顶';
  const overdueText = order.overdueDailyAmount == null ? '' : `，逾期 ${moneyText(order.overdueDailyAmount)}/天`;
  const graceText = order.renewalGraceHours ? `，宽限 ${order.renewalGraceHours} 小时` : '';
  return `按日 ${moneyText(order.renewalDailyAmount)}/天${capText}${overdueText}${graceText}`;
}

function pricingRevisionTag(value?: string | null) {
  if (value === 'APPLIED') return <Tag color="success">已生效</Tag>;
  if (value === 'PENDING_CUSTOMER_CONFIRMATION') return <Tag color="warning">待客户确认</Tag>;
  if (value === 'CANCELLED') return <Tag>已取消</Tag>;
  return <Tag>{value || '-'}</Tag>;
}

function confirmationMethodText(value?: string | null) {
  if (!value) return '-';
  return confirmationMethodOptions.find((item) => item.value === value)?.label ?? value;
}

function parseImportRows(input: string) {
  return input
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      const columns = line.includes('\t') ? line.split('\t') : line.split(',');
      const values = columns.map((item) => item.trim());
      const hasLeaseMultiplier = values.length >= 16;
      const offset = hasLeaseMultiplier ? 1 : 0;
      return {
        lineNo: index + 1,
        sourcePlatform: values[0],
        externalOrderNo: emptyToUndefined(values[1]),
        storeSkuId: Number(values[2]),
        packageId: Number(values[3]),
        leaseMultiplier: hasLeaseMultiplier ? parseOptionalNumber(values[4]) : 1,
        customerName: values[4 + offset],
        customerPhone: values[5 + offset],
        rentStartedAt: normalizeDateText(values[6 + offset]),
        expectedReturnAt: normalizeDateText(values[7 + offset]),
        frameAssetId: parseOptionalNumber(values[8 + offset]),
        batteryAssetId: parseOptionalNumber(values[9 + offset]),
        externalRentalAmount: parseOptionalNumber(values[10 + offset]),
        verificationAmount: parseOptionalNumber(values[11 + offset]),
        signFeeAmount: parseOptionalNumber(values[12 + offset]),
        depositAmount: parseOptionalNumber(values[13 + offset]),
        remark: emptyToUndefined(values[14 + offset])
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
