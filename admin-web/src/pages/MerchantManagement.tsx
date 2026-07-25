import { Button, Checkbox, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import { http } from '../services/request';
import type { Employee, Merchant, Store } from '../types/api';

type MerchantForm = {
  merchantName: string;
  contactName: string;
  contactPhone: string;
  businessLicenseNo?: string;
  createOwnerAccount?: boolean;
  ownerUsername?: string;
  ownerDisplayName?: string;
  ownerPhone?: string;
  ownerPassword?: string;
};

type StoreForm = {
  merchantId: number;
  storeName: string;
  address: string;
  businessHours?: string;
  longitude?: number;
  latitude?: number;
};

type EmployeeForm = {
  merchantId: number;
  username: string;
  displayName: string;
  phone: string;
  password: string;
  roleCode: 'MERCHANT_OWNER' | 'STORE_MANAGER' | 'STORE_OPERATOR' | 'STORE_STAFF' | 'MAINTENANCE_STAFF' | 'WAREHOUSE_STAFF';
  storeIds?: number[];
};

type MerchantManagementProps = {
  mode?: 'all' | 'merchants' | 'stores' | 'employees';
};

export function MerchantManagement({ mode = 'all' }: MerchantManagementProps) {
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedMerchantId, setSelectedMerchantId] = useState<number>();
  const [merchantOpen, setMerchantOpen] = useState(false);
  const [storeOpen, setStoreOpen] = useState(false);
  const [employeeOpen, setEmployeeOpen] = useState(false);
  const [qrOpen, setQrOpen] = useState(false);
  const [editingMerchant, setEditingMerchant] = useState<Merchant | null>(null);
  const [editingStore, setEditingStore] = useState<Store | null>(null);
  const [selectedQrStore, setSelectedQrStore] = useState<Store | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [merchantForm] = Form.useForm<MerchantForm>();
  const [storeForm] = Form.useForm<StoreForm>();
  const [employeeForm] = Form.useForm<EmployeeForm>();
  const selectedMerchant = merchants.find((merchant) => merchant.id === selectedMerchantId);
  const employeeMerchantId = Form.useWatch('merchantId', employeeForm);
  const employeeRoleCode = Form.useWatch('roleCode', employeeForm);
  const showMerchants = mode === 'all' || mode === 'merchants';
  const showStores = mode === 'all' || mode === 'stores';
  const showEmployees = mode === 'all' || mode === 'employees';

  useEffect(() => {
    void loadMerchants();
    void loadStores();
  }, []);

  useEffect(() => {
    if (!selectedMerchantId) {
      setEmployees([]);
      return;
    }
    void loadEmployees(selectedMerchantId);
  }, [selectedMerchantId]);

  useEffect(() => {
    if (!selectedQrStore) {
      setQrDataUrl('');
      return;
    }
    if (isOfficialAlipayQr(selectedQrStore.qrContent)) {
      setQrDataUrl(selectedQrStore.qrContent);
      return;
    }
    if (isPendingAlipayQr(selectedQrStore.qrContent)) {
      setQrDataUrl('');
      return;
    }
    QRCode.toDataURL(selectedQrStore.qrContent, {
      width: 260,
      margin: 2,
      color: {
        dark: '#111715',
        light: '#ffffff'
      }
    }).then(setQrDataUrl).catch(() => {
      setQrDataUrl('');
      message.error('门店码生成失败');
    });
  }, [selectedQrStore]);

  const allMerchantOptions = useMemo(() => merchants
    .map((merchant) => ({ label: merchant.merchantName, value: merchant.id })), [merchants]);

  const merchantOptions = useMemo(() => merchants
    .filter((merchant) => merchant.status === 'ENABLED')
    .map((merchant) => ({ label: merchant.merchantName, value: merchant.id })), [merchants]);

  const selectedMerchantStores = useMemo(
    () => stores.filter((store) => store.merchantId === selectedMerchantId),
    [selectedMerchantId, stores]
  );

  const employeeStoreOptions = useMemo(() => stores
    .filter((store) => store.merchantId === employeeMerchantId && store.status === 'ENABLED')
    .map((store) => ({
      label: `${store.storeName} / ${store.storeCode}`,
      value: store.id
    })), [employeeMerchantId, stores]);

  async function loadMerchants() {
    setLoading(true);
    try {
      const data = await http.get<unknown, Merchant[]>('/api/admin/merchants');
      setMerchants(data);
      if (!selectedMerchantId && data.length > 0) {
        setSelectedMerchantId(data[0].id);
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadStores() {
    const data = await http.get<unknown, Store[]>('/api/admin/stores');
    setStores(data);
  }

  async function loadEmployees(merchantId: number) {
    const data = await http.get<unknown, Employee[]>('/api/admin/employees', { params: { merchantId } });
    setEmployees(data);
  }

  function openCreateMerchant() {
    setEditingMerchant(null);
    merchantForm.resetFields();
    merchantForm.setFieldsValue({ createOwnerAccount: true, ownerPassword: 'Xniu@2026' });
    setMerchantOpen(true);
  }

  function openEditMerchant(record: Merchant) {
    setEditingMerchant(record);
    merchantForm.setFieldsValue({
      merchantName: record.merchantName,
      contactName: record.contactName,
      contactPhone: record.contactPhone,
      businessLicenseNo: record.businessLicenseNo ?? undefined
    });
    setMerchantOpen(true);
  }

  function openCreateStore() {
    setEditingStore(null);
    storeForm.resetFields();
    storeForm.setFieldsValue({ merchantId: selectedMerchantId });
    setStoreOpen(true);
  }

  function openEditStore(record: Store) {
    setEditingStore(record);
    storeForm.setFieldsValue({
      merchantId: record.merchantId,
      storeName: record.storeName,
      address: record.address,
      businessHours: record.businessHours ?? undefined,
      longitude: record.longitude ?? undefined,
      latitude: record.latitude ?? undefined
    });
    setStoreOpen(true);
  }

  function openStoreQr(record: Store) {
    setSelectedQrStore(record);
    setQrOpen(true);
  }

  async function copyStoreQrContent() {
    if (!selectedQrStore) {
      return;
    }
    await navigator.clipboard.writeText(selectedQrStore.qrContent);
    message.success('门店码内容已复制');
  }

  async function regenerateStoreQr(record = selectedQrStore) {
    if (!record) {
      return;
    }
    try {
      const updated = await http.post<unknown, Store>(`/api/admin/stores/${record.id}/qrcode`);
      setStores((items) => items.map((item) => item.id === updated.id ? updated : item));
      setSelectedQrStore(updated);
      message.success('支付宝小程序门店码已生成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '支付宝小程序门店码生成失败');
    }
  }

  async function submitMerchant(values: MerchantForm) {
    if (editingMerchant) {
      await http.put(`/api/admin/merchants/${editingMerchant.id}`, values);
      message.success('商户已更新');
    } else {
      const created = await http.post<unknown, Merchant>('/api/admin/merchants', values);
      setSelectedMerchantId(created.id);
      message.success('商户已创建');
    }
    setMerchantOpen(false);
    setEditingMerchant(null);
    merchantForm.resetFields();
    await loadMerchants();
  }

  async function submitStore(values: StoreForm) {
    if (editingStore) {
      await http.put(`/api/admin/stores/${editingStore.id}`, values);
      message.success('门店已更新');
    } else {
      await http.post('/api/admin/stores', values);
      message.success('门店已创建');
    }
    setStoreOpen(false);
    setEditingStore(null);
    storeForm.resetFields();
    setSelectedMerchantId(values.merchantId);
    await loadStores();
  }

  async function createEmployee(values: EmployeeForm) {
    await http.post('/api/admin/employees', values);
    setEmployeeOpen(false);
    employeeForm.resetFields();
    message.success('员工账号已创建');
    setSelectedMerchantId(values.merchantId);
    await loadEmployees(values.merchantId);
  }

  async function toggleMerchantStatus(record: Merchant) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/merchants/${record.id}/status`, null, { params: { status } });
    await loadMerchants();
    await loadStores();
  }

  async function toggleStoreStatus(record: Store) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/stores/${record.id}/status`, null, { params: { status } });
    await loadStores();
  }

  async function deleteStore(record: Store) {
    await http.delete(`/api/admin/stores/${record.id}`);
    if (selectedQrStore?.id === record.id) {
      setQrOpen(false);
      setSelectedQrStore(null);
    }
    if (editingStore?.id === record.id) {
      setStoreOpen(false);
      setEditingStore(null);
      storeForm.resetFields();
    }
    message.success('门店已删除');
    await loadStores();
  }

  async function toggleEmployeeStatus(record: Employee) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/employees/${record.id}/status`, null, { params: { status } });
    await loadEmployees(record.merchantId);
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>{mode === 'merchants' ? '商户管理' : mode === 'stores' ? '门店管理' : mode === 'employees' ? '员工账号' : '商户体系'}</Typography.Title>
        {!showMerchants && (
          <Select
            value={selectedMerchantId}
            onChange={setSelectedMerchantId}
            style={{ width: 220 }}
            options={allMerchantOptions}
          />
        )}
        {showMerchants && <Button type="primary" onClick={openCreateMerchant}>新建商户</Button>}
        {showStores && <Button
          type={mode === 'stores' ? 'primary' : 'default'}
          onClick={openCreateStore}
          disabled={!selectedMerchantId || selectedMerchant?.status !== 'ENABLED'}
        >
          新建门店
        </Button>}
        {showEmployees && <Button
          type={mode === 'employees' ? 'primary' : 'default'}
          onClick={() => {
            employeeForm.resetFields();
            employeeForm.setFieldsValue({ merchantId: selectedMerchantId, roleCode: 'STORE_STAFF', password: 'Xniu@2026' });
            setEmployeeOpen(true);
          }}
          disabled={!selectedMerchantId || selectedMerchant?.status !== 'ENABLED'}
        >
          新增账号
        </Button>}
      </Space>

      {showMerchants && <div className="section">
        <Typography.Title level={5}>商户列表</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={merchants}
          pagination={false}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedMerchantId ? [selectedMerchantId] : [],
            onChange: (keys) => setSelectedMerchantId(Number(keys[0]))
          }}
          columns={[
            { title: '商户编码', dataIndex: 'merchantCode' },
            { title: '商户名称', dataIndex: 'merchantName' },
            { title: '联系人', dataIndex: 'contactName' },
            { title: '电话', dataIndex: 'contactPhone' },
            { title: '状态', dataIndex: 'status', render: statusTag },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openEditMerchant(record)}>编辑</Button>
                  <Button size="small" onClick={() => toggleMerchantStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
                </Space>
              )
            }
          ]}
        />
      </div>}

      {showStores && <div className="section">
        <Typography.Title level={5}>{selectedMerchant ? `${selectedMerchant.merchantName} / 门店` : '门店'}</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={selectedMerchantStores}
          pagination={false}
          scroll={{ x: 1100 }}
          columns={[
            { title: '门店编码', dataIndex: 'storeCode' },
            { title: '门店名称', dataIndex: 'storeName' },
            { title: '地址', dataIndex: 'address' },
            { title: '营业时间', dataIndex: 'businessHours' },
            {
              title: '门店码',
              dataIndex: 'qrContent',
              render: (_, record) => (
                <Space>
                  <Tag color="green">{record.storeCode}</Tag>
                  <Button size="small" onClick={() => openStoreQr(record)}>
                    {isPendingAlipayQr(record.qrContent) ? '待生成' : '查看二维码'}
                  </Button>
                </Space>
              )
            },
            { title: '状态', dataIndex: 'status', render: statusTag },
            {
              title: '操作',
              fixed: 'right',
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openStoreQr(record)}>门店码</Button>
                  <Button size="small" onClick={() => openEditStore(record)}>编辑</Button>
                  <Button size="small" onClick={() => toggleStoreStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
                  <Popconfirm
                    title="确认删除门店？"
                    description={`删除后将无法恢复，门店 ${record.storeName} 必须没有订单、资产、员工授权、商品和配件数据。`}
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => deleteStore(record)}
                  >
                    <Button size="small" danger>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
        />
      </div>}

      {showEmployees && <div className="section">
        <Typography.Title level={5}>员工账号</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={employees}
          pagination={false}
          columns={[
            { title: '账号', dataIndex: 'username' },
            { title: '姓名', dataIndex: 'displayName' },
            { title: '电话', dataIndex: 'phone' },
            { title: '角色', dataIndex: 'accountType' },
            { title: '授权门店', render: (_, record) => record.authorizedStores.map((store) => <Tag key={store.id}>{store.storeName}</Tag>) },
            { title: '状态', dataIndex: 'status', render: statusTag },
            { title: '操作', render: (_, record) => <Button size="small" onClick={() => toggleEmployeeStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button> }
          ]}
        />
      </div>}

      <Modal
        title={editingMerchant ? '编辑商户' : '新建商户'}
        open={merchantOpen}
        onCancel={() => {
          merchantForm.resetFields();
          setEditingMerchant(null);
          setMerchantOpen(false);
        }}
        onOk={() => merchantForm.submit()}
        destroyOnHidden
      >
        <Form
          form={merchantForm}
          layout="vertical"
          onFinish={submitMerchant}
          onValuesChange={(changedValues) => {
            if (Object.prototype.hasOwnProperty.call(changedValues, 'createOwnerAccount') && !changedValues.createOwnerAccount) {
              merchantForm.setFieldsValue({
                ownerUsername: undefined,
                ownerDisplayName: undefined,
                ownerPhone: undefined,
                ownerPassword: undefined
              });
            }
          }}
        >
          <Form.Item name="merchantName" label="商户名称" rules={[{ required: true, message: '请输入商户名称' }]}><Input /></Form.Item>
          <Form.Item name="contactName" label="联系人" rules={[{ required: true, message: '请输入联系人' }]}><Input /></Form.Item>
          <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]}><Input /></Form.Item>
          <Form.Item name="businessLicenseNo" label="营业执照号"><Input /></Form.Item>
          {!editingMerchant ? (
            <>
              <Form.Item name="createOwnerAccount" valuePropName="checked">
                <Checkbox>同步创建商户主账号</Checkbox>
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.createOwnerAccount !== next.createOwnerAccount}>
                {({ getFieldValue }) => getFieldValue('createOwnerAccount') ? (
                  <>
                    <Form.Item preserve={false} name="ownerUsername" label="主账号登录账号" rules={[{ required: true, message: '请输入主账号登录账号' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="ownerDisplayName" label="主账号姓名" rules={[{ required: true, message: '请输入主账号姓名' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="ownerPhone" label="主账号手机号" rules={[{ required: true, message: '请输入主账号手机号' }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item preserve={false} name="ownerPassword" label="主账号初始密码" rules={[{ required: true, message: '请输入主账号初始密码' }]}>
                      <Input.Password />
                    </Form.Item>
                  </>
                ) : null}
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Modal title={editingStore ? '编辑门店' : '新建门店'} open={storeOpen} onCancel={() => setStoreOpen(false)} onOk={() => storeForm.submit()} destroyOnHidden>
        <Form form={storeForm} layout="vertical" onFinish={submitStore}>
          <Form.Item name="merchantId" label="所属商户" rules={[{ required: true, message: '请选择商户' }]}>
            <Select options={editingStore ? allMerchantOptions : merchantOptions} disabled={Boolean(editingStore)} />
          </Form.Item>
          <Form.Item name="storeName" label="门店名称" rules={[{ required: true, message: '请输入门店名称' }]}><Input /></Form.Item>
          <Form.Item name="address" label="地址" rules={[{ required: true, message: '请输入地址' }]}><Input /></Form.Item>
          <Form.Item name="businessHours" label="营业时间"><Input placeholder="09:00-22:00" /></Form.Item>
          <Space>
            <Form.Item name="longitude" label="经度"><InputNumber /></Form.Item>
            <Form.Item name="latitude" label="纬度"><InputNumber /></Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal title="新增商户账号" open={employeeOpen} onCancel={() => setEmployeeOpen(false)} onOk={() => employeeForm.submit()} destroyOnHidden>
        <Form
          form={employeeForm}
          layout="vertical"
          onFinish={createEmployee}
          onValuesChange={(changedValues) => {
            if ('merchantId' in changedValues) {
              employeeForm.setFieldValue('storeIds', undefined);
            }
            if (changedValues.roleCode === 'MERCHANT_OWNER') {
              employeeForm.setFieldValue('storeIds', undefined);
            }
          }}
        >
          <Form.Item name="merchantId" label="所属商户" rules={[{ required: true, message: '请选择商户' }]}>
            <Select options={merchantOptions} />
          </Form.Item>
          <Form.Item name="username" label="登录账号" rules={[{ required: true, message: '请输入登录账号' }]}><Input /></Form.Item>
          <Form.Item name="displayName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}><Input /></Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}><Input /></Form.Item>
          <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }]}><Input.Password /></Form.Item>
          <Form.Item name="roleCode" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              options={[
                { label: '商户老板', value: 'MERCHANT_OWNER' },
                { label: '门店店长', value: 'STORE_MANAGER' },
                { label: '门店运营', value: 'STORE_OPERATOR' },
                { label: '门店员工', value: 'STORE_STAFF' },
                { label: '维修人员', value: 'MAINTENANCE_STAFF' },
                { label: '仓库人员', value: 'WAREHOUSE_STAFF' }
              ]}
            />
          </Form.Item>
          {employeeRoleCode === 'MERCHANT_OWNER' ? (
            <Form.Item label="授权门店">
              <Typography.Text type="secondary">商户老板自动拥有该商户当前及以后新增的全部门店。</Typography.Text>
            </Form.Item>
          ) : (
            <Form.Item name="storeIds" label="授权门店" rules={[{ required: true, message: '请至少选择一个门店' }]}>
              <Select mode="multiple" options={employeeStoreOptions} placeholder={employeeMerchantId ? '选择该账号可访问的门店' : '请先选择所属商户'} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal title="门店二维码" open={qrOpen} onCancel={() => setQrOpen(false)} footer={null} width={440}>
        {selectedQrStore ? (
          <div className="store-qr-modal">
            <div className="store-qr-card">
              {qrDataUrl ? <img src={qrDataUrl} alt={`${selectedQrStore.storeName} 门店二维码`} /> : <div className="store-qr-loading">待生成支付宝小程序码</div>}
            </div>
            <Typography.Title level={5}>{selectedQrStore.storeName}</Typography.Title>
            <Typography.Text type="secondary">{selectedQrStore.address}</Typography.Text>
            <div className="store-qr-content">
              <span>{isOfficialAlipayQr(selectedQrStore.qrContent) ? '支付宝小程序码地址' : '门店启动参数'}</span>
              <code>{selectedQrStore.qrContent}</code>
            </div>
            {isPendingAlipayQr(selectedQrStore.qrContent) ? (
              <Typography.Text type="secondary">
                当前未配置支付宝小程序密钥，配置后点击重新生成即可得到支付宝扫一扫可打开的小程序码。
              </Typography.Text>
            ) : null}
            <Space>
              <Button type="primary" onClick={() => regenerateStoreQr()}>重新生成支付宝码</Button>
              <Button onClick={copyStoreQrContent}>复制内容</Button>
              {qrDataUrl ? <Button href={qrDataUrl} download={`${selectedQrStore.storeCode}.png`}>下载二维码</Button> : null}
            </Space>
          </div>
        ) : null}
      </Modal>
    </Space>
  );
}

function statusTag(status: 'ENABLED' | 'DISABLED') {
  return <Tag color={status === 'ENABLED' ? 'green' : 'red'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function isPendingAlipayQr(value?: string) {
  return Boolean(value?.startsWith('ALIPAY_QRCODE_PENDING:'));
}

function isOfficialAlipayQr(value?: string) {
  return Boolean(value && /^https?:\/\//.test(value));
}
