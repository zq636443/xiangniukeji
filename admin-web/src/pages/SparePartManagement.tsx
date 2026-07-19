import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { Merchant, SparePart, SparePartStockLog, Store, StoreSparePartStock } from '../types/api';

type PartForm = {
  partName: string;
  spec?: string;
  unit: string;
  procurementPrice: number;
  unitPrice: number;
  buybackPrice: number;
  initialQuantity?: number;
};

type StockForm = {
  storeId?: number;
  quantity: number;
  unitPrice?: number;
  remark?: string;
};

type StockFilters = {
  merchantId?: number;
  storeId?: number;
  partId?: number;
};

type LogFilters = {
  merchantId?: number;
  storeId?: number;
  partId?: number;
};

type ActiveTab = 'parts' | 'stocks' | 'purchase' | 'buyback' | 'logs';

export function SparePartManagement() {
  const [parts, setParts] = useState<SparePart[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [logs, setLogs] = useState<SparePartStockLog[]>([]);
  const [storeStocks, setStoreStocks] = useState<StoreSparePartStock[]>([]);
  const [selectedPart, setSelectedPart] = useState<SparePart | null>(null);
  const [activeTab, setActiveTab] = useState<ActiveTab>('parts');
  const [partOpen, setPartOpen] = useState(false);
  const [stockOpen, setStockOpen] = useState(false);
  const [stockMode, setStockMode] = useState<'inbound' | 'purchase' | 'buyback' | 'adjust'>('inbound');
  const [partKeyword, setPartKeyword] = useState('');
  const [partStatus, setPartStatus] = useState<'ALL' | SparePart['status']>('ALL');
  const [stockFilters, setStockFilters] = useState<StockFilters>({});
  const [logFilters, setLogFilters] = useState<LogFilters>({});
  const [partsLoading, setPartsLoading] = useState(false);
  const [stocksLoading, setStocksLoading] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [partForm] = Form.useForm<PartForm>();
  const [stockForm] = Form.useForm<StockForm>();

  useEffect(() => {
    void loadReferenceData();
    void loadParts();
    void loadStoreStocks();
    void loadLogs();
  }, []);

  useEffect(() => {
    void loadStoreStocks();
  }, [stockFilters.partId, stockFilters.merchantId, stockFilters.storeId]);

  useEffect(() => {
    void loadLogs();
  }, [logFilters.partId]);

  useEffect(() => {
    setStockFilters((current) => {
      if (!current.storeId) {
        return current;
      }
      const store = stores.find((item) => item.id === current.storeId);
      if (!store || (current.merchantId && store.merchantId !== current.merchantId)) {
        return { ...current, storeId: undefined };
      }
      return current;
    });
  }, [stores, stockFilters.merchantId]);

  useEffect(() => {
    setLogFilters((current) => {
      if (!current.storeId) {
        return current;
      }
      const store = stores.find((item) => item.id === current.storeId);
      if (!store || (current.merchantId && store.merchantId !== current.merchantId)) {
        return { ...current, storeId: undefined };
      }
      return current;
    });
  }, [stores, logFilters.merchantId]);

  async function loadReferenceData() {
    try {
      const [merchantData, storeData] = await Promise.all([
        http.get<unknown, Merchant[]>('/api/admin/merchants').catch(() => []),
        http.get<unknown, Store[]>('/api/admin/stores').catch(() => [])
      ]);
      setMerchants(merchantData);
      setStores(storeData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '基础数据加载失败');
    }
  }

  async function loadParts() {
    setPartsLoading(true);
    try {
      const partData = await http.get<unknown, SparePart[]>('/api/admin/spare-parts');
      setParts(partData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '配件列表加载失败');
    } finally {
      setPartsLoading(false);
    }
  }

  async function loadStoreStocks() {
    setStocksLoading(true);
    try {
      const data = await http.get<unknown, StoreSparePartStock[]>('/api/admin/spare-parts/store-stocks', {
        params: cleanFilters(stockFilters)
      });
      setStoreStocks(data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '门店库存加载失败');
    } finally {
      setStocksLoading(false);
    }
  }

  async function loadLogs() {
    setLogsLoading(true);
    try {
      const data = await http.get<unknown, SparePartStockLog[]>('/api/admin/spare-parts/logs', {
        params: cleanFilters({ partId: logFilters.partId })
      });
      setLogs(data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '库存流水加载失败');
    } finally {
      setLogsLoading(false);
    }
  }

  async function reloadAll() {
    await Promise.all([loadParts(), loadStoreStocks(), loadLogs()]);
  }

  function openCreate() {
    setSelectedPart(null);
    partForm.resetFields();
    partForm.setFieldsValue({ unit: '个', procurementPrice: 0, unitPrice: 0, buybackPrice: 0, initialQuantity: 0 });
    setPartOpen(true);
  }

  function openEdit(record: SparePart) {
    setSelectedPart(record);
    partForm.setFieldsValue({
      partName: record.partName,
      spec: record.spec || undefined,
      unit: record.unit,
      procurementPrice: record.procurementPrice,
      unitPrice: record.unitPrice,
      buybackPrice: record.buybackPrice
    });
    setPartOpen(true);
  }

  function openStock(record: SparePart, mode: 'inbound' | 'purchase' | 'buyback' | 'adjust') {
    setSelectedPart(record);
    setStockMode(mode);
    stockForm.resetFields();
    stockForm.setFieldsValue({
      storeId: stores[0]?.id,
      quantity: 1,
      unitPrice:
        mode === 'inbound'
          ? record.procurementPrice
          : mode === 'buyback'
            ? record.buybackPrice
            : record.unitPrice
    });
    setStockOpen(true);
  }

  async function submitPart(values: PartForm) {
    if (selectedPart) {
      await http.put(`/api/admin/spare-parts/${selectedPart.id}`, values);
      message.success('配件已更新');
    } else {
      await http.post('/api/admin/spare-parts', values);
      message.success('配件已创建');
    }
    setPartOpen(false);
    await reloadAll();
  }

  async function submitStock(values: StockForm) {
    if (!selectedPart) {
      return;
    }
    await http.post(`/api/admin/spare-parts/${selectedPart.id}/${stockMode}`, values);
    message.success(stockModeSuccessText(stockMode));
    setStockOpen(false);
    await reloadAll();
  }

  const merchantMap = useMemo(() => new Map(merchants.map((item) => [item.id, item.merchantName])), [merchants]);
  const partOptions = useMemo(
    () => parts.map((part) => ({ label: `${part.partName} / ${part.partCode}`, value: part.id })),
    [parts]
  );
  const merchantOptions = useMemo(
    () => merchants.map((merchant) => ({ label: merchant.merchantName, value: merchant.id })),
    [merchants]
  );
  const stockStoreOptions = useMemo(
    () =>
      stores
        .filter((store) => !stockFilters.merchantId || store.merchantId === stockFilters.merchantId)
        .map((store) => ({
          label: `${store.storeName} / ${store.storeCode}`,
          value: store.id
        })),
    [stores, stockFilters.merchantId]
  );
  const logStoreOptions = useMemo(
    () =>
      stores
        .filter((store) => !logFilters.merchantId || store.merchantId === logFilters.merchantId)
        .map((store) => ({
          label: `${store.storeName} / ${store.storeCode}`,
          value: store.id
        })),
    [stores, logFilters.merchantId]
  );

  const filteredParts = useMemo(() => {
    const keyword = partKeyword.trim().toLowerCase();
    return parts.filter((part) => {
      const matchesKeyword =
        !keyword ||
        part.partName.toLowerCase().includes(keyword) ||
        part.partCode.toLowerCase().includes(keyword) ||
        (part.spec || '').toLowerCase().includes(keyword);
      const matchesStatus = partStatus === 'ALL' || part.status === partStatus;
      return matchesKeyword && matchesStatus;
    });
  }, [parts, partKeyword, partStatus]);

  const filteredLogs = useMemo(
    () =>
      logs.filter((log) => {
        if (logFilters.merchantId && log.merchantId !== logFilters.merchantId) {
          return false;
        }
        if (logFilters.storeId && log.storeId !== logFilters.storeId) {
          return false;
        }
        return true;
      }),
    [logs, logFilters.merchantId, logFilters.storeId]
  );

  const purchaseLogs = useMemo(
    () => filteredLogs.filter((log) => log.changeType === 'STORE_PURCHASE_IN'),
    [filteredLogs]
  );
  const buybackLogs = useMemo(
    () => filteredLogs.filter((log) => log.changeType === 'STORE_BUYBACK_OUT'),
    [filteredLogs]
  );

  const totalPlatformStock = useMemo(() => parts.reduce((sum, item) => sum + item.stockQuantity, 0), [parts]);
  const totalPlatformAmount = useMemo(() => parts.reduce((sum, item) => sum + item.stockAmount, 0), [parts]);
  const totalStoreStockAmount = useMemo(() => storeStocks.reduce((sum, item) => sum + item.stockAmount, 0), [storeStocks]);

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <div>
          <Typography.Title level={3}>配件仓库</Typography.Title>
          <Typography.Text type="secondary">
            平台仓、门店仓、采购退仓流水统一在这里管理。
          </Typography.Text>
        </div>
        <Space wrap>
          <Tag color="green">平台库存 {totalPlatformStock}</Tag>
          <Tag color="blue">平台库存金额 {money(totalPlatformAmount)}</Tag>
          <Tag color="purple">门店库存金额 {money(totalStoreStockAmount)}</Tag>
          <Button onClick={reloadAll}>刷新</Button>
          <Button type="primary" onClick={openCreate}>新增配件</Button>
        </Space>
      </Space>

      <section className="section">
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as ActiveTab)}
          items={[
            {
              key: 'parts',
              label: '配件种类',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Space wrap>
                    <Input
                      style={{ width: 260 }}
                      placeholder="搜索配件名称、编码、规格"
                      value={partKeyword}
                      onChange={(event) => setPartKeyword(event.target.value)}
                    />
                    <Select
                      style={{ width: 160 }}
                      value={partStatus}
                      onChange={(value) => setPartStatus(value)}
                      options={[
                        { label: '全部状态', value: 'ALL' },
                        { label: '启用', value: 'ENABLED' },
                        { label: '停用', value: 'DISABLED' }
                      ]}
                    />
                  </Space>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={partsLoading}
                    dataSource={filteredParts}
                    pagination={false}
                    columns={[
                      { title: '配件编码', dataIndex: 'partCode', width: 120 },
                      { title: '配件名称', dataIndex: 'partName', width: 180 },
                      { title: '规格', dataIndex: 'spec', render: (value) => value || '-' },
                      { title: '单位', dataIndex: 'unit', width: 80 },
                      { title: '采购价', dataIndex: 'procurementPrice', render: money, width: 110 },
                      { title: '门店领用价', dataIndex: 'unitPrice', render: money, width: 120 },
                      { title: '回收价', dataIndex: 'buybackPrice', render: money, width: 110 },
                      {
                        title: '平台库存',
                        dataIndex: 'stockQuantity',
                        width: 100,
                        render: (value: number) => <Tag color={value <= 0 ? 'red' : value < 5 ? 'gold' : 'green'}>{value}</Tag>
                      },
                      { title: '平台库存金额', dataIndex: 'stockAmount', render: money, width: 130 },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 90,
                        render: (value: SparePart['status']) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>
                      },
                      {
                        title: '操作',
                        width: 440,
                        render: (_, record) => (
                          <Space wrap>
                            <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                            <Button size="small" onClick={() => openStock(record, 'inbound')}>平台入库</Button>
                            <Button size="small" onClick={() => openStock(record, 'purchase')}>门店采购</Button>
                            <Button size="small" onClick={() => openStock(record, 'buyback')}>门店退仓</Button>
                            <Button size="small" onClick={() => openStock(record, 'adjust')}>库存调整</Button>
                            <Button
                              size="small"
                              onClick={() => {
                                setActiveTab('stocks');
                                setStockFilters((current) => ({ ...current, partId: record.id }));
                              }}
                            >
                              看门店库存
                            </Button>
                            <Button
                              size="small"
                              onClick={() => {
                                setActiveTab('logs');
                                setLogFilters((current) => ({ ...current, partId: record.id }));
                              }}
                            >
                              看流水
                            </Button>
                          </Space>
                        )
                      }
                    ]}
                  />
                </Space>
              )
            },
            {
              key: 'stocks',
              label: '门店库存',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Space wrap>
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 180 }}
                      placeholder="筛选商户"
                      optionFilterProp="label"
                      value={stockFilters.merchantId}
                      options={merchantOptions}
                      onChange={(value) => setStockFilters((current) => ({ ...current, merchantId: value, storeId: undefined }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选门店"
                      optionFilterProp="label"
                      value={stockFilters.storeId}
                      options={stockStoreOptions}
                      onChange={(value) => setStockFilters((current) => ({ ...current, storeId: value }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选配件"
                      optionFilterProp="label"
                      value={stockFilters.partId}
                      options={partOptions}
                      onChange={(value) => setStockFilters((current) => ({ ...current, partId: value }))}
                    />
                    <Button onClick={() => setStockFilters({})}>重置筛选</Button>
                  </Space>
                  <Table
                    rowKey={(record) => `${record.storeId}-${record.partId}`}
                    size="small"
                    loading={stocksLoading}
                    dataSource={storeStocks}
                    pagination={false}
                    columns={[
                      { title: '商户', dataIndex: 'merchantName', width: 180 },
                      { title: '门店', dataIndex: 'storeName', width: 180 },
                      { title: '配件', dataIndex: 'partName', width: 180 },
                      {
                        title: '库存数量',
                        dataIndex: 'stockQuantity',
                        width: 110,
                        render: (value: number) => <Tag color={value <= 0 ? 'red' : value < 5 ? 'gold' : 'green'}>{value}</Tag>
                      },
                      { title: '门店入仓单价', dataIndex: 'avgUnitPrice', render: money, width: 130 },
                      { title: '库存金额', dataIndex: 'stockAmount', render: money, width: 130 }
                    ]}
                  />
                </Space>
              )
            },
            {
              key: 'purchase',
              label: '门店采购',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Space wrap>
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 180 }}
                      placeholder="筛选商户"
                      optionFilterProp="label"
                      value={logFilters.merchantId}
                      options={merchantOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, merchantId: value, storeId: undefined }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选门店"
                      optionFilterProp="label"
                      value={logFilters.storeId}
                      options={logStoreOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, storeId: value }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选配件"
                      optionFilterProp="label"
                      value={logFilters.partId}
                      options={partOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, partId: value }))}
                    />
                    <Button onClick={() => setLogFilters({})}>重置筛选</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={logsLoading}
                    dataSource={purchaseLogs}
                    pagination={false}
                    columns={movementColumns()}
                  />
                </Space>
              )
            },
            {
              key: 'buyback',
              label: '门店退仓',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Space wrap>
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 180 }}
                      placeholder="筛选商户"
                      optionFilterProp="label"
                      value={logFilters.merchantId}
                      options={merchantOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, merchantId: value, storeId: undefined }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选门店"
                      optionFilterProp="label"
                      value={logFilters.storeId}
                      options={logStoreOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, storeId: value }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选配件"
                      optionFilterProp="label"
                      value={logFilters.partId}
                      options={partOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, partId: value }))}
                    />
                    <Button onClick={() => setLogFilters({})}>重置筛选</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={logsLoading}
                    dataSource={buybackLogs}
                    pagination={false}
                    columns={movementColumns()}
                  />
                </Space>
              )
            },
            {
              key: 'logs',
              label: '库存流水',
              children: (
                <Space direction="vertical" size={16} className="page-stack">
                  <Space wrap>
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 180 }}
                      placeholder="筛选商户"
                      optionFilterProp="label"
                      value={logFilters.merchantId}
                      options={merchantOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, merchantId: value, storeId: undefined }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选门店"
                      optionFilterProp="label"
                      value={logFilters.storeId}
                      options={logStoreOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, storeId: value }))}
                    />
                    <Select
                      allowClear
                      showSearch
                      style={{ width: 220 }}
                      placeholder="筛选配件"
                      optionFilterProp="label"
                      value={logFilters.partId}
                      options={partOptions}
                      onChange={(value) => setLogFilters((current) => ({ ...current, partId: value }))}
                    />
                    <Button onClick={() => setLogFilters({})}>重置筛选</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={logsLoading}
                    dataSource={filteredLogs}
                    pagination={false}
                    columns={[
                      { title: '类型', dataIndex: 'changeType', width: 130, render: stockTypeTag },
                      { title: '商户', dataIndex: 'merchantName', width: 180, render: (value) => value || '-' },
                      { title: '门店', dataIndex: 'storeName', width: 180, render: (value) => value || '-' },
                      { title: '配件', dataIndex: 'partName', width: 180 },
                      { title: '数量变化', dataIndex: 'quantityChange', width: 100 },
                      { title: '单价', dataIndex: 'unitPrice', render: money, width: 110 },
                      { title: '金额', dataIndex: 'amount', render: money, width: 110 },
                      { title: '关联', width: 120, render: (_, record) => `${record.refType || '-'} ${record.refId || ''}` },
                      { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
                      { title: '时间', dataIndex: 'createdAt', width: 150, render: dateText }
                    ]}
                  />
                </Space>
              )
            }
          ]}
        />
      </section>

      <Modal title={selectedPart ? '编辑配件' : '新增配件'} open={partOpen} onCancel={() => setPartOpen(false)} onOk={() => partForm.submit()} destroyOnHidden>
        <Form form={partForm} layout="vertical" onFinish={submitPart}>
          <Form.Item name="partName" label="配件名称" rules={[{ required: true, message: '请输入配件名称' }]}><Input /></Form.Item>
          <Form.Item name="spec" label="规格"><Input /></Form.Item>
          <Form.Item name="unit" label="单位" rules={[{ required: true, message: '请输入单位' }]}><Input /></Form.Item>
          <Form.Item name="procurementPrice" label="平台采购价" rules={[{ required: true, message: '请输入平台采购价' }]}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="unitPrice" label="门店领用价" rules={[{ required: true, message: '请输入门店领用价' }]}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="buybackPrice" label="门店回收价" rules={[{ required: true, message: '请输入门店回收价' }]}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          {!selectedPart && <Form.Item name="initialQuantity" label="平台初始库存"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>}
        </Form>
      </Modal>

      <Modal title={`${selectedPart?.partName ?? ''} / ${stockTitle(stockMode)}`} open={stockOpen} onCancel={() => setStockOpen(false)} onOk={() => stockForm.submit()} destroyOnHidden>
        <Form form={stockForm} layout="vertical" onFinish={submitStock}>
          {(stockMode === 'purchase' || stockMode === 'buyback' || stockMode === 'adjust') && stores.length > 0 && (
            <Form.Item name="storeId" label="所属门店" rules={[{ required: true, message: '请选择门店' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                options={stores.map((store) => ({
                  label: `${merchantMap.get(store.merchantId) || '未知商户'} / ${store.storeName} / ${store.storeCode}`,
                  value: store.id
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="quantity" label={quantityLabel(stockMode)} rules={[{ required: true, message: '请输入数量' }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="unitPrice" label="单价"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function movementColumns() {
  return [
    { title: '时间', dataIndex: 'createdAt', width: 150, render: dateText },
    { title: '商户', dataIndex: 'merchantName', width: 180, render: (value: string | null | undefined) => value || '-' },
    { title: '门店', dataIndex: 'storeName', width: 180, render: (value: string | null | undefined) => value || '-' },
    { title: '配件', dataIndex: 'partName', width: 180 },
    { title: '数量', dataIndex: 'quantityChange', width: 100, render: (value: number) => Math.abs(value) },
    { title: '单价', dataIndex: 'unitPrice', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', width: 110, render: money },
    { title: '备注', dataIndex: 'remark', render: (value: string | null | undefined) => value || '-' }
  ];
}

function stockModeSuccessText(mode: 'inbound' | 'purchase' | 'buyback' | 'adjust') {
  return {
    inbound: '平台库存已入库',
    purchase: '门店采购入库已完成',
    buyback: '门店退仓回收已完成',
    adjust: '库存已调整'
  }[mode];
}

function cleanFilters<T extends Record<string, number | undefined>>(filters: T) {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== undefined));
}

function money(value?: number | null) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
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

function stockTitle(mode: 'inbound' | 'purchase' | 'buyback' | 'adjust') {
  return {
    inbound: '平台入库',
    purchase: '门店采购',
    buyback: '门店退仓',
    adjust: '库存调整'
  }[mode];
}

function quantityLabel(mode: 'inbound' | 'purchase' | 'buyback' | 'adjust') {
  return {
    inbound: '入库数量',
    purchase: '采购数量',
    buyback: '退仓数量',
    adjust: '调整数量'
  }[mode];
}
