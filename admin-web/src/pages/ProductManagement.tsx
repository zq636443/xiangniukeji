import { Button, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { FormInstance } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { Merchant, ProductCategory, ProductPackage, ProductSku, Store, StoreSku } from '../types/api';

type CategoryForm = { categoryName: string; sortOrder?: number };
type SkuForm = {
  categoryId: number;
  skuName: string;
  skuType: 'RENTAL' | 'SALE';
  description?: string;
  needFrameAsset?: boolean;
  needBatteryAsset?: boolean;
  supportCrossStoreReturn?: boolean;
};
type PackageForm = {
  skuId: number;
  packageName: string;
  priceAmount: number;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  billDayMode: 'PAYMENT_DAY' | 'FIXED_DAY';
  billDay?: number;
};
type PackagePriceForm = {
  packageId: number;
  rentalAmount: number;
  periodAmount: number;
  depositAmount: number;
  autoRenewEnabled?: boolean;
  renewalUnit?: 'DAY' | 'MONTH';
  renewalValue?: number;
  renewalAmount?: number;
};
type StoreSkuForm = {
  merchantId: number;
  storeId: number;
  skuId: number;
  displayName: string;
  saleMode: 'RENTAL' | 'SALE';
  signFeeAmount: number;
  signFeePayer: 'USER' | 'MERCHANT';
  packages: PackagePriceForm[];
};
type BatchForm = {
  skuId: number;
  storeIds: number[];
  displayName?: string;
  saleMode: 'RENTAL' | 'SALE';
  signFeeAmount: number;
  signFeePayer: 'USER' | 'MERCHANT';
  packages: PackagePriceForm[];
};

type ProductManagementProps = {
  mode?: 'all' | 'skus' | 'packages' | 'storeSkus';
};

export function ProductManagement({ mode = 'all' }: ProductManagementProps) {
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [skus, setSkus] = useState<ProductSku[]>([]);
  const [packages, setPackages] = useState<ProductPackage[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [skuOpen, setSkuOpen] = useState(false);
  const [packageOpen, setPackageOpen] = useState(false);
  const [storeSkuOpen, setStoreSkuOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [editingStoreSku, setEditingStoreSku] = useState<StoreSku | null>(null);
  const [categoryForm] = Form.useForm<CategoryForm>();
  const [skuForm] = Form.useForm<SkuForm>();
  const [packageForm] = Form.useForm<PackageForm>();
  const [storeSkuForm] = Form.useForm<StoreSkuForm>();
  const [batchForm] = Form.useForm<BatchForm>();
  const showSkus = mode === 'all' || mode === 'skus';
  const showPackages = mode === 'all' || mode === 'packages';
  const showStoreSkus = mode === 'all' || mode === 'storeSkus';

  useEffect(() => {
    void loadAll();
  }, []);

  const categoryOptions = useMemo(() => categories.map((item) => ({ label: item.categoryName, value: item.id })), [categories]);
  const skuOptions = useMemo(() => skus.map((item) => ({ label: item.skuName, value: item.id })), [skus]);
  const merchantOptions = useMemo(() => merchants.map((item) => ({ label: item.merchantName, value: item.id })), [merchants]);
  const storeOptions = useMemo(() => stores.map((item) => ({ label: `${item.storeName} / ${item.storeCode}`, value: item.id })), [stores]);
  const selectedStoreSkuMerchantId = Form.useWatch('merchantId', storeSkuForm);
  const selectedStoreSkuSkuId = Form.useWatch('skuId', storeSkuForm);
  const selectedBatchSkuId = Form.useWatch('skuId', batchForm);

  const filteredStoreOptions = useMemo(
    () => storeOptions.filter((item) => !selectedStoreSkuMerchantId || stores.find((store) => store.id === item.value)?.merchantId === selectedStoreSkuMerchantId),
    [selectedStoreSkuMerchantId, storeOptions, stores]
  );
  const storeSkuPackageOptions = useMemo(
    () => packages
      .filter((item) => !selectedStoreSkuSkuId || item.skuId === selectedStoreSkuSkuId)
      .map((item) => ({ label: `${item.packageName} / ¥${item.priceAmount} / ${item.leaseValue}${item.leaseUnit === 'DAY' ? '天' : '个月'}`, value: item.id })),
    [packages, selectedStoreSkuSkuId]
  );
  const batchPackageOptions = useMemo(
    () => packages
      .filter((item) => !selectedBatchSkuId || item.skuId === selectedBatchSkuId)
      .map((item) => ({ label: `${item.packageName} / ¥${item.priceAmount} / ${item.leaseValue}${item.leaseUnit === 'DAY' ? '天' : '个月'}`, value: item.id })),
    [packages, selectedBatchSkuId]
  );

  async function loadAll() {
    const [categoryData, skuData, packageData, storeSkuData, merchantData, storeData] = await Promise.all([
      http.get<unknown, ProductCategory[]>('/api/admin/products/categories'),
      http.get<unknown, ProductSku[]>('/api/admin/products/skus'),
      http.get<unknown, ProductPackage[]>('/api/admin/products/packages'),
      http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
      http.get<unknown, Merchant[]>('/api/admin/merchants'),
      http.get<unknown, Store[]>('/api/admin/stores')
    ]);
    setCategories(categoryData);
    setSkus(skuData);
    setPackages(packageData);
    setStoreSkus(storeSkuData);
    setMerchants(merchantData);
    setStores(storeData);
  }

  async function createCategory(values: CategoryForm) {
    await http.post('/api/admin/products/categories', values);
    setCategoryOpen(false);
    categoryForm.resetFields();
    message.success('分类已创建');
    await loadAll();
  }

  async function createSku(values: SkuForm) {
    await http.post('/api/admin/products/skus', values);
    setSkuOpen(false);
    skuForm.resetFields();
    message.success('商品链接已创建');
    await loadAll();
  }

  async function createPackage(values: PackageForm) {
    await http.post('/api/admin/products/packages', values);
    setPackageOpen(false);
    packageForm.resetFields();
    message.success('SKU 已创建');
    await loadAll();
  }

  async function publishStoreSku(values: StoreSkuForm) {
    const store = stores.find((item) => item.id === values.storeId);
    await http.post('/api/admin/products/store-skus', {
      merchantId: values.merchantId || store?.merchantId,
      storeId: values.storeId,
      skuId: values.skuId,
      displayName: values.displayName,
      saleMode: values.saleMode,
      signFeeAmount: values.signFeeAmount,
      signFeePayer: values.signFeePayer,
      packages: values.packages
    });
    setStoreSkuOpen(false);
    setEditingStoreSku(null);
    storeSkuForm.resetFields();
    message.success(editingStoreSku ? '门店商品已更新' : '门店商品已上架');
    await loadAll();
  }

  async function batchPublish(values: BatchForm) {
    await http.post('/api/admin/products/store-skus/batch', {
      skuId: values.skuId,
      storeIds: values.storeIds,
      displayName: values.displayName,
      saleMode: values.saleMode,
      signFeeAmount: values.signFeeAmount,
      signFeePayer: values.signFeePayer,
      packages: values.packages
    });
    setBatchOpen(false);
    batchForm.resetFields();
    message.success('批量上架完成');
    await loadAll();
  }

  function openCreateStoreSku() {
    setEditingStoreSku(null);
    storeSkuForm.resetFields();
    storeSkuForm.setFieldsValue({
      saleMode: 'RENTAL',
      signFeePayer: 'USER',
      signFeeAmount: 0,
      packages: [defaultPackagePrice()]
    });
    setStoreSkuOpen(true);
  }

  function openEditStoreSku(record: StoreSku) {
    setEditingStoreSku(record);
    storeSkuForm.setFieldsValue({
      merchantId: record.merchantId,
      storeId: record.storeId,
      skuId: record.skuId,
      displayName: record.displayName,
      saleMode: record.saleMode,
      signFeeAmount: record.signFeeAmount,
      signFeePayer: record.signFeePayer,
      packages: record.packages.map((item) => ({
        packageId: item.packageId,
        rentalAmount: packages.find((template) => template.id === item.packageId)?.priceAmount ?? item.rentalAmount,
        periodAmount: item.periodAmount,
        depositAmount: item.depositAmount,
        autoRenewEnabled: item.autoRenewEnabled,
        renewalUnit: item.renewalUnit ?? item.leaseUnit,
        renewalValue: item.renewalValue ?? defaultRenewalValue(item),
        renewalAmount: item.renewalAmount ?? item.periodAmount
      }))
    });
    setStoreSkuOpen(true);
  }

  async function toggleStoreSku(record: StoreSku) {
    const status = record.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF';
    await http.put(`/api/admin/products/store-skus/${record.id}/status`, null, { params: { status } });
    await loadAll();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>{mode === 'skus' ? '链接管理' : mode === 'packages' ? 'SKU 管理' : mode === 'storeSkus' ? '门店商品' : '商品管理'}</Typography.Title>
        {showSkus && <Button type={mode === 'skus' ? 'primary' : 'default'} onClick={() => setCategoryOpen(true)}>新建分类</Button>}
        {showSkus && <Button type={mode === 'skus' ? 'primary' : 'default'} onClick={() => {
          skuForm.setFieldsValue({ skuType: 'RENTAL', needFrameAsset: true, needBatteryAsset: true, supportCrossStoreReturn: false });
          setSkuOpen(true);
        }}>新建链接</Button>}
        {showPackages && <Button type={mode === 'packages' ? 'primary' : 'default'} onClick={() => {
          packageForm.setFieldsValue({ priceAmount: 0, leaseUnit: 'MONTH', leaseValue: 1, totalPeriods: 1, billDayMode: 'PAYMENT_DAY' });
          setPackageOpen(true);
        }}>新增 SKU</Button>}
        {showStoreSkus && <Button type={mode === 'storeSkus' ? 'primary' : 'default'} onClick={openCreateStoreSku}>门店上架</Button>}
        {showStoreSkus && <Button onClick={() => {
          batchForm.resetFields();
          batchForm.setFieldsValue({ saleMode: 'RENTAL', signFeePayer: 'USER', signFeeAmount: 0, packages: [defaultPackagePrice()] });
          setBatchOpen(true);
        }}>批量上架</Button>}
      </Space>

      {showSkus && <div className="section">
        <Typography.Title level={5}>商品链接</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={skus}
          pagination={false}
          columns={[
            { title: '链接编码', dataIndex: 'skuCode' },
            { title: '链接名称', dataIndex: 'skuName' },
            { title: '分类', dataIndex: 'categoryName' },
            { title: '类型', dataIndex: 'skuType', render: (value: ProductSku['skuType']) => value === 'RENTAL' ? '租赁' : '售卖' },
            {
              title: '包含 SKU',
              render: (_, record) => {
                const linkedSkus = packages.filter((item) => item.skuId === record.id);
                return linkedSkus.length ? (
                  <Space wrap size={[4, 4]}>{linkedSkus.map((item) => <Tag key={item.id}>{item.packageName} / ¥{item.priceAmount}</Tag>)}</Space>
                ) : '-';
              }
            },
            { title: '资产要求', render: (_, record) => `${record.needFrameAsset ? '车架' : ''}${record.needBatteryAsset ? ' 电池' : ''}`.trim() || '无' },
            { title: '跨店归还', dataIndex: 'supportCrossStoreReturn', render: (value: boolean) => value ? '支持' : '不支持' }
          ]}
        />
      </div>}

      {showPackages && <div className="section">
        <Typography.Title level={5}>链接 SKU</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={packages}
          pagination={false}
          columns={[
            { title: 'SKU 编码', dataIndex: 'packageCode' },
            { title: 'SKU 名称', dataIndex: 'packageName' },
            { title: '所属链接', dataIndex: 'skuName' },
            { title: 'SKU 价格', dataIndex: 'priceAmount', render: (value: number) => `¥${Number(value || 0).toFixed(2)}` },
            { title: '租期', render: (_, record) => `${record.leaseValue}${record.leaseUnit === 'DAY' ? '天' : '个月'}` },
            { title: '总期数', dataIndex: 'totalPeriods' },
            { title: '账单日', render: (_, record) => record.billDayMode === 'PAYMENT_DAY' ? '付款日' : `每月 ${record.billDay} 日` }
          ]}
        />
      </div>}

      {showStoreSkus && <div className="section">
        <Typography.Title level={5}>门店商品链接</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={storeSkus}
          pagination={false}
          scroll={{ x: 1100 }}
          columns={[
            { title: '门店', dataIndex: 'storeName' },
            { title: '商品名', dataIndex: 'displayName' },
            { title: '商品链接', dataIndex: 'skuName' },
            { title: '签单费', dataIndex: 'signFeeAmount' },
            { title: '承担方', dataIndex: 'signFeePayer', render: (value) => value === 'USER' ? '用户' : '商户' },
            {
              title: 'SKU 价格',
              render: (_, record) => (
                <Space direction="vertical" size={4}>
                  {record.packages.map((item) => (
                    <div key={item.id ?? item.packageId}>
                      <Typography.Text strong>{item.packageName}</Typography.Text>
                      <Typography.Text type="secondary">
                        {`  价格 ${item.rentalAmount} / 分期 ${item.periodAmount} / 押金 ${item.depositAmount} / ${renewalText(item)}`}
                      </Typography.Text>
                    </div>
                  ))}
                </Space>
              )
            },
            { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'ON_SHELF' ? 'green' : 'red'}>{value === 'ON_SHELF' ? '已上架' : '已下架'}</Tag> },
            {
              title: '操作',
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openEditStoreSku(record)}>编辑</Button>
                  <Button size="small" onClick={() => toggleStoreSku(record)}>{record.status === 'ON_SHELF' ? '下架' : '上架'}</Button>
                </Space>
              )
            }
          ]}
        />
      </div>}

      <Modal title="新建分类" open={categoryOpen} onCancel={() => setCategoryOpen(false)} onOk={() => categoryForm.submit()} destroyOnHidden>
        <Form form={categoryForm} layout="vertical" onFinish={createCategory}>
          <Form.Item name="categoryName" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}><Input /></Form.Item>
          <Form.Item name="sortOrder" label="排序"><InputNumber style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="新建商品链接" open={skuOpen} onCancel={() => setSkuOpen(false)} onOk={() => skuForm.submit()} destroyOnHidden>
        <Form form={skuForm} layout="vertical" onFinish={createSku}>
          <Form.Item name="categoryId" label="分类" rules={[{ required: true, message: '请选择分类' }]}><Select options={categoryOptions} /></Form.Item>
          <Form.Item name="skuName" label="链接名称" rules={[{ required: true, message: '请输入链接名称' }]}><Input /></Form.Item>
          <Form.Item name="skuType" label="链接类型" rules={[{ required: true, message: '请选择链接类型' }]}><Select options={[{ label: '租赁', value: 'RENTAL' }, { label: '售卖', value: 'SALE' }]} /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="needFrameAsset" valuePropName="checked"><Checkbox>需要绑定车架</Checkbox></Form.Item>
          <Form.Item name="needBatteryAsset" valuePropName="checked"><Checkbox>需要绑定电池</Checkbox></Form.Item>
          <Form.Item name="supportCrossStoreReturn" valuePropName="checked"><Checkbox>支持跨店归还</Checkbox></Form.Item>
        </Form>
      </Modal>

      <Modal title="新增 SKU" open={packageOpen} onCancel={() => setPackageOpen(false)} onOk={() => packageForm.submit()} destroyOnHidden>
        <Form form={packageForm} layout="vertical" onFinish={createPackage}>
          <Form.Item name="skuId" label="所属链接" rules={[{ required: true, message: '请选择商品链接' }]}><Select options={skuOptions} /></Form.Item>
          <Form.Item name="packageName" label="SKU 名称" rules={[{ required: true, message: '请输入 SKU 名称' }]}><Input /></Form.Item>
          <Form.Item name="priceAmount" label="SKU 价格" rules={[{ required: true, message: '请输入 SKU 价格' }]}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="leaseUnit" label="租期单位" rules={[{ required: true, message: '请选择租期单位' }]}><Select options={[{ label: '天', value: 'DAY' }, { label: '月', value: 'MONTH' }]} /></Form.Item>
          <Form.Item name="leaseValue" label="租期值" rules={[{ required: true, message: '请输入租期值' }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="totalPeriods" label="总期数" rules={[{ required: true, message: '请输入总期数' }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="billDayMode" label="账单日规则" rules={[{ required: true, message: '请选择账单日规则' }]}><Select options={[{ label: '付款日', value: 'PAYMENT_DAY' }, { label: '固定日期', value: 'FIXED_DAY' }]} /></Form.Item>
          <Form.Item name="billDay" label="固定账单日"><InputNumber min={1} max={28} style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingStoreSku ? '编辑门店商品' : '门店上架'}
        open={storeSkuOpen}
        onCancel={() => {
          setStoreSkuOpen(false);
          setEditingStoreSku(null);
          storeSkuForm.resetFields();
        }}
        onOk={() => storeSkuForm.submit()}
        destroyOnHidden
        width={760}
      >
        <Form form={storeSkuForm} layout="vertical" onFinish={publishStoreSku}>
          <Form.Item name="merchantId" label="商户" rules={[{ required: true, message: '请选择商户' }]}>
            <Select
              options={merchantOptions}
              disabled={Boolean(editingStoreSku)}
              onChange={() => {
                storeSkuForm.setFieldValue('storeId', undefined);
                storeSkuForm.setFieldValue('packages', [defaultPackagePrice()]);
              }}
            />
          </Form.Item>
          <Form.Item name="storeId" label="门店" rules={[{ required: true, message: '请选择门店' }]}>
            <Select options={filteredStoreOptions} disabled={Boolean(editingStoreSku)} />
          </Form.Item>
          <Form.Item name="skuId" label="商品链接" rules={[{ required: true, message: '请选择商品链接' }]}>
            <Select
              options={skuOptions}
              disabled={Boolean(editingStoreSku)}
              onChange={() => storeSkuForm.setFieldValue('packages', [defaultPackagePrice()])}
            />
          </Form.Item>
          <Form.Item name="displayName" label="门店商品名" rules={[{ required: true, message: '请输入门店商品名' }]}><Input /></Form.Item>
          {storeSkuFields(storeSkuPackageOptions, packages, storeSkuForm)}
        </Form>
      </Modal>

      <Modal
        title="批量上架"
        open={batchOpen}
        onCancel={() => {
          setBatchOpen(false);
          batchForm.resetFields();
        }}
        onOk={() => batchForm.submit()}
        destroyOnHidden
        width={760}
      >
        <Form form={batchForm} layout="vertical" onFinish={batchPublish}>
          <Form.Item name="storeIds" label="门店" rules={[{ required: true, message: '请选择门店' }]}><Select mode="multiple" options={storeOptions} /></Form.Item>
          <Form.Item name="skuId" label="商品链接" rules={[{ required: true, message: '请选择商品链接' }]}>
            <Select options={skuOptions} onChange={() => batchForm.setFieldValue('packages', [defaultPackagePrice()])} />
          </Form.Item>
          <Form.Item name="displayName" label="门店商品名"><Input placeholder="不填则使用链接名称" /></Form.Item>
          {storeSkuFields(batchPackageOptions, packages, batchForm)}
        </Form>
      </Modal>
    </Space>
  );
}

function storeSkuFields(
  packageOptions: { label: string; value: number }[],
  packageTemplates: ProductPackage[],
  form: FormInstance
) {
  return (
    <>
      <Form.Item name="saleMode" label="售卖模式" rules={[{ required: true, message: '请选择售卖模式' }]}><Select options={[{ label: '租赁', value: 'RENTAL' }, { label: '售卖', value: 'SALE' }]} /></Form.Item>
      <Form.Item name="signFeeAmount" label="签单费" rules={[{ required: true, message: '请输入签单费' }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="signFeePayer" label="签单费承担方" rules={[{ required: true, message: '请选择承担方' }]}><Select options={[{ label: '用户', value: 'USER' }, { label: '商户', value: 'MERCHANT' }]} /></Form.Item>
      <Form.List name="packages">
        {(fields, { add, remove }) => (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space align="center" style={{ justifyContent: 'space-between', width: '100%' }}>
              <Typography.Title level={5} style={{ margin: 0 }}>SKU 与价格</Typography.Title>
              <Button onClick={() => add(defaultPackagePrice())}>新增 SKU</Button>
            </Space>
            {fields.map((field, index) => (
              <div key={field.key} className="section" style={{ marginBottom: 0 }}>
                <Space align="center" style={{ justifyContent: 'space-between', width: '100%', marginBottom: 12 }}>
                  <Typography.Text strong>{`SKU ${index + 1}`}</Typography.Text>
                  <Button danger disabled={fields.length === 1} onClick={() => remove(field.name)}>删除 SKU</Button>
                </Space>
                <Form.Item
                  name={[field.name, 'packageId']}
                  label="SKU"
                  rules={[
                    { required: true, message: '请选择 SKU' },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        const selected = (getFieldValue('packages') || [])
                          .map((item: PackagePriceForm | undefined) => item?.packageId)
                          .filter(Boolean);
                        return value && selected.filter((item: number) => item === value).length > 1
                          ? Promise.reject(new Error('每个 SKU 只能配置一套价格'))
                          : Promise.resolve();
                      }
                    })
                  ]}
                >
                  <Select
                    options={packageOptions}
                    onChange={(packageId) => applyRenewalDefaults(form, field.name, packageTemplates.find((item) => item.id === packageId))}
                  />
                </Form.Item>
                <Space.Compact block>
                  <Form.Item
                    style={{ width: '34%' }}
                    name={[field.name, 'rentalAmount']}
                    label="SKU 价格"
                    rules={[{ required: true, message: '请输入金额' }]}
                  >
                    <InputNumber min={0} precision={2} disabled style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item
                    style={{ width: '33%' }}
                    name={[field.name, 'periodAmount']}
                    label="分期金额"
                    rules={[{ required: true, message: '请输入每期金额' }]}
                  >
                    <InputNumber
                      min={0}
                      style={{ width: '100%' }}
                      onChange={(value) => {
                        if (!form.getFieldValue(['packages', field.name, 'renewalAmount'])) {
                          form.setFieldValue(['packages', field.name, 'renewalAmount'], Number(value || 0));
                        }
                      }}
                    />
                  </Form.Item>
                  <Form.Item
                    style={{ width: '33%' }}
                    name={[field.name, 'depositAmount']}
                    label="押金"
                    rules={[{ required: true, message: '请输入押金' }]}
                  >
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Space.Compact>
                <Form.Item name={[field.name, 'autoRenewEnabled']} valuePropName="checked">
                  <Checkbox>开启自动续租</Checkbox>
                </Form.Item>
                <Space.Compact block>
                  <Form.Item
                    style={{ width: '30%' }}
                    name={[field.name, 'renewalUnit']}
                    label="续租周期单位"
                  >
                    <Select options={[{ label: '天', value: 'DAY' }, { label: '月', value: 'MONTH' }]} />
                  </Form.Item>
                  <Form.Item
                    style={{ width: '30%' }}
                    name={[field.name, 'renewalValue']}
                    label="续租周期"
                  >
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item
                    style={{ width: '40%' }}
                    name={[field.name, 'renewalAmount']}
                    label="续租金额"
                  >
                    <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                  </Form.Item>
                </Space.Compact>
              </div>
            ))}
          </Space>
        )}
      </Form.List>
    </>
  );
}

function defaultPackagePrice(): PackagePriceForm {
  return {
    packageId: undefined as never,
    rentalAmount: 0,
    periodAmount: 0,
    depositAmount: 0,
    autoRenewEnabled: true,
    renewalUnit: 'MONTH',
    renewalValue: 1,
    renewalAmount: 0
  };
}

function applyRenewalDefaults(form: FormInstance, fieldName: number, template?: ProductPackage) {
  if (!template) {
    return;
  }
  const current = form.getFieldValue(['packages', fieldName]) as PackagePriceForm | undefined;
  const skuPrice = Number(template.priceAmount || 0);
  const periodAmount = current?.periodAmount || Number((skuPrice / Math.max(template.totalPeriods, 1)).toFixed(2));
  form.setFieldValue(['packages', fieldName, 'rentalAmount'], skuPrice);
  form.setFieldValue(['packages', fieldName, 'periodAmount'], periodAmount);
  form.setFieldValue(['packages', fieldName, 'autoRenewEnabled'], current?.autoRenewEnabled ?? true);
  form.setFieldValue(['packages', fieldName, 'renewalUnit'], template.leaseUnit);
  form.setFieldValue(['packages', fieldName, 'renewalValue'], defaultRenewalValue(template));
  form.setFieldValue(['packages', fieldName, 'renewalAmount'], current?.renewalAmount || periodAmount);
}

function defaultRenewalValue(template: Pick<ProductPackage, 'leaseValue' | 'totalPeriods'>) {
  return Math.max(1, Math.floor(template.leaseValue / Math.max(template.totalPeriods, 1)));
}

function renewalText(item: { autoRenewEnabled?: boolean; renewalUnit?: 'DAY' | 'MONTH' | null; renewalValue?: number | null; renewalAmount?: number | null }) {
  if (!item.autoRenewEnabled) {
    return '续租关闭';
  }
  const unit = item.renewalUnit === 'DAY' ? '天' : '个月';
  return `续租 ${item.renewalValue || 1}${unit} / ${item.renewalAmount ?? 0}`;
}
