import { Button, Descriptions, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Investor,
  Merchant,
  Store,
  SystemAccount,
  SystemAccountCreatePayload,
  SystemAccountResetPasswordPayload,
  SystemAccountUpdatePayload,
  SystemPermission,
  SystemRole
} from '../types/api';

type RoleForm = {
  roleCode: SystemAccount['accountType'];
};

type ScopeForm = {
  storeIds: number[];
};

type CreateForm = SystemAccountCreatePayload;
type EditForm = SystemAccountUpdatePayload;
type ResetPasswordForm = SystemAccountResetPasswordPayload & { confirmPassword: string };

const merchantRoleOptions: { label: string; value: SystemAccount['accountType'] }[] = [
  { label: '商户老板', value: 'MERCHANT_OWNER' },
  { label: '门店店长', value: 'STORE_MANAGER' },
  { label: '门店运营', value: 'STORE_OPERATOR' },
  { label: '门店员工', value: 'STORE_STAFF' },
  { label: '维修人员', value: 'MAINTENANCE_STAFF' },
  { label: '仓库人员', value: 'WAREHOUSE_STAFF' }
] ;

const platformRoleOptions: { label: string; value: SystemAccount['accountType'] }[] = [
  { label: '平台管理员', value: 'PLATFORM_ADMIN' },
  { label: '财务人员', value: 'FINANCE' }
] ;

const investorRoleOptions: { label: string; value: SystemAccount['accountType'] }[] = [
  { label: '出资方', value: 'INVESTOR' }
] ;

type SystemManagementMode = 'accounts' | 'roles' | 'permissions' | 'scopes';

type SystemManagementProps = {
  mode: SystemManagementMode;
};

export function SystemManagement({ mode }: SystemManagementProps) {
  const [accounts, setAccounts] = useState<SystemAccount[]>([]);
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const [permissions, setPermissions] = useState<SystemPermission[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [investors, setInvestors] = useState<Investor[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<SystemAccount | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [scopeOpen, setScopeOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [permissionUpdatingAccountId, setPermissionUpdatingAccountId] = useState<number>();
  const [createForm] = Form.useForm<CreateForm>();
  const [editForm] = Form.useForm<EditForm>();
  const [passwordForm] = Form.useForm<ResetPasswordForm>();
  const [roleForm] = Form.useForm<RoleForm>();
  const [scopeForm] = Form.useForm<ScopeForm>();

  useEffect(() => {
    void loadAll();
  }, []);

  const merchantOptions = useMemo(() => merchants.map((merchant) => ({
    label: merchant.merchantName,
    value: merchant.id
  })), [merchants]);

  const investorOptions = useMemo(() => investors.map((investor) => ({
    label: investor.investorName,
    value: investor.id
  })), [investors]);

  const scopedStoreOptions = useMemo(() => {
    if (!selectedAccount?.merchantId) {
      return [];
    }
    return stores
      .filter((store) => store.merchantId === selectedAccount.merchantId)
      .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }));
  }, [selectedAccount, stores]);

  async function loadAll() {
    setLoading(true);
    try {
      const [accountData, roleData, permissionData, merchantData, investorData, storeData] = await Promise.all([
        http.get<unknown, SystemAccount[]>('/api/admin/system/accounts'),
        http.get<unknown, SystemRole[]>('/api/admin/system/roles'),
        http.get<unknown, SystemPermission[]>('/api/admin/system/permissions'),
        http.get<unknown, Merchant[]>('/api/admin/merchants'),
        http.get<unknown, Investor[]>('/api/admin/investors'),
        http.get<unknown, Store[]>('/api/admin/stores')
      ]);
      setAccounts(accountData);
      setRoles(roleData);
      setPermissions(permissionData);
      setMerchants(merchantData);
      setInvestors(investorData);
      setStores(storeData);
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    createForm.resetFields();
    createForm.setFieldsValue({
      roleCode: 'MERCHANT_OWNER',
      password: 'Xniu@2026'
    });
    setCreateOpen(true);
  }

  function openRole(record: SystemAccount) {
    setSelectedAccount(record);
    roleForm.setFieldsValue({ roleCode: record.roles[0] as SystemAccount['accountType'] });
    setRoleOpen(true);
  }

  function openDetail(record: SystemAccount) {
    setSelectedAccount(record);
    setDetailOpen(true);
  }

  function openEdit(record: SystemAccount) {
    setSelectedAccount(record);
    editForm.setFieldsValue({
      username: record.username ?? undefined,
      displayName: record.displayName,
      phone: record.phone ?? undefined
    });
    setEditOpen(true);
  }

  function openResetPassword(record: SystemAccount) {
    setSelectedAccount(record);
    passwordForm.resetFields();
    setPasswordOpen(true);
  }

  function openScopes(record: SystemAccount) {
    setSelectedAccount(record);
    scopeForm.setFieldsValue({
      storeIds: record.storeScopes.filter((scope) => scope.storeId).map((scope) => Number(scope.storeId))
    });
    setScopeOpen(true);
  }

  async function submitRole(values: RoleForm) {
    if (!selectedAccount) {
      return;
    }
    await http.put(`/api/admin/system/accounts/${selectedAccount.id}/role`, values);
    message.success('账号角色已更新');
    setRoleOpen(false);
    await loadAll();
  }

  async function submitScopes(values: ScopeForm) {
    if (!selectedAccount) {
      return;
    }
    await http.put(`/api/admin/system/accounts/${selectedAccount.id}/scopes`, values);
    message.success('门店范围已更新');
    setScopeOpen(false);
    await loadAll();
  }

  async function toggleStatus(record: SystemAccount) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/system/accounts/${record.id}/status`, null, { params: { status } });
    message.success(record.status === 'ENABLED' ? '账号已停用' : '账号已启用');
    await loadAll();
  }

  async function toggleOrderCreatePermission(record: SystemAccount, enabled: boolean) {
    const permissionCodes = enabled
      ? Array.from(new Set([...record.directPermissions, 'order.create']))
      : record.directPermissions.filter((permission) => permission !== 'order.create');
    setPermissionUpdatingAccountId(record.id);
    try {
      const updated = await http.put<unknown, SystemAccount>(`/api/admin/system/accounts/${record.id}/permissions`, {
        permissionCodes
      });
      setAccounts((items) => items.map((item) => item.id === updated.id ? updated : item));
      if (selectedAccount?.id === updated.id) {
        setSelectedAccount(updated);
      }
      message.success(enabled ? '已允许该账号新建订单' : '已收回该账号新建订单权限');
    } finally {
      setPermissionUpdatingAccountId(undefined);
    }
  }

  async function submitCreate(values: CreateForm) {
    await http.post('/api/admin/system/accounts', values);
    message.success('账号已创建');
    setCreateOpen(false);
    await loadAll();
  }

  async function submitEdit(values: EditForm) {
    if (!selectedAccount) {
      return;
    }
    const payload: SystemAccountUpdatePayload = {
      displayName: values.displayName,
      phone: values.phone
    };
    if (selectedAccount.username) {
      payload.username = values.username;
    }
    const updated = await http.put<unknown, SystemAccount>(`/api/admin/system/accounts/${selectedAccount.id}`, payload);
    message.success('账号资料已更新');
    setSelectedAccount(updated);
    setEditOpen(false);
    await loadAll();
  }

  async function submitResetPassword(values: ResetPasswordForm) {
    if (!selectedAccount) {
      return;
    }
    const updated = await http.put<unknown, SystemAccount>(`/api/admin/system/accounts/${selectedAccount.id}/password`, {
      password: values.password
    });
    message.success('密码已重置');
    setSelectedAccount(updated);
    setPasswordOpen(false);
    await loadAll();
  }

  const pageMeta = pageMetaMap[mode];

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>{pageMeta.title}</Typography.Title>
        <Typography.Text type="secondary">{pageMeta.description}</Typography.Text>
        {mode === 'accounts' ? <Button type="primary" onClick={openCreate}>新增账号</Button> : null}
      </Space>

      <section className="section">
        {mode === 'accounts' ? (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={accounts}
            scroll={{ x: 1640 }}
            pagination={false}
            columns={[
              { title: '账号ID', dataIndex: 'id', width: 84 },
              { title: '显示名称', dataIndex: 'displayName' },
              { title: '登录账号', dataIndex: 'username', render: (value) => value || '-' },
              { title: '手机号', dataIndex: 'phone', render: (value) => value || '-' },
              { title: '类型', dataIndex: 'accountType', render: accountTypeText },
              { title: '角色', dataIndex: 'roles', render: (value: string[]) => value.length ? value.map((item) => <Tag key={item}>{accountTypeText(item)}</Tag>) : '-' },
              {
                title: '新建订单',
                width: 110,
                align: 'center',
                render: (_, record) => record.merchantId ? (
                  <Switch
                    checked={record.directPermissions.includes('order.create')}
                    loading={permissionUpdatingAccountId === record.id}
                    onChange={(checked) => void toggleOrderCreatePermission(record, checked)}
                  />
                ) : '-'
              },
              { title: '所属商户', dataIndex: 'merchantName', render: (value) => value || '-' },
              { title: '默认门店', dataIndex: 'storeName', render: (value) => value || '-' },
              { title: '出资方', dataIndex: 'investorName', render: (value) => value || '-' },
              { title: '状态', dataIndex: 'status', render: statusTag },
              { title: '最后登录', dataIndex: 'lastLoginAt', render: dateText },
              {
                title: '操作',
                fixed: 'right',
                render: (_, record) => (
                  <Space wrap>
                    <Button size="small" onClick={() => openDetail(record)}>详情</Button>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    {record.username ? <Button size="small" onClick={() => openResetPassword(record)}>重置密码</Button> : null}
                    <Button size="small" onClick={() => openRole(record)}>角色</Button>
                    {record.merchantId && record.roles[0] !== 'MERCHANT_OWNER' ? (
                      <Button size="small" onClick={() => openScopes(record)}>范围</Button>
                    ) : null}
                    <Button size="small" onClick={() => toggleStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
                  </Space>
                )
              }
            ]}
          />
        ) : null}

        {mode === 'roles' ? (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={roles}
            pagination={false}
            columns={[
              { title: '角色编码', dataIndex: 'roleCode' },
              { title: '角色名称', dataIndex: 'roleName' },
              { title: '作用域', dataIndex: 'roleScope' },
              { title: '状态', dataIndex: 'status', render: statusTag },
              { title: '权限点', dataIndex: 'permissions', render: (value: string[]) => value.length ? value.map((item) => <Tag key={item}>{item}</Tag>) : '-' }
            ]}
          />
        ) : null}

        {mode === 'permissions' ? (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={permissions}
            pagination={false}
            columns={[
              { title: '权限编码', dataIndex: 'permissionCode' },
              { title: '权限名称', dataIndex: 'permissionName' },
              { title: '模块', dataIndex: 'moduleCode', render: (value) => <Tag>{value}</Tag> },
              { title: '创建时间', dataIndex: 'createdAt', render: dateText }
            ]}
          />
        ) : null}

        {mode === 'scopes' ? (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={accounts.filter((item) => Boolean(item.merchantId))}
            scroll={{ x: 1280 }}
            pagination={false}
            columns={[
              { title: '账号ID', dataIndex: 'id', width: 84 },
              { title: '显示名称', dataIndex: 'displayName' },
              { title: '角色', dataIndex: 'roles', render: (value: string[]) => value.length ? value.map((item) => <Tag key={item}>{accountTypeText(item)}</Tag>) : '-' },
              { title: '所属商户', dataIndex: 'merchantName', render: (value) => value || '-' },
              { title: '默认门店', dataIndex: 'storeName', render: (value) => value || '-' },
              { title: '当前范围', render: (_, record) => scopeText(record, stores) },
              {
                title: '可操作性',
                render: (_, record) => record.roles[0] === 'MERCHANT_OWNER'
                  ? <Tag color="blue">自动继承商户全部门店</Tag>
                  : <Tag color="green">可单独配置</Tag>
              },
              {
                title: '操作',
                render: (_, record) => record.roles[0] === 'MERCHANT_OWNER'
                  ? '-'
                  : <Button size="small" onClick={() => openScopes(record)}>调整范围</Button>
              }
            ]}
          />
        ) : null}
      </section>

      <Modal title="调整账号角色" open={roleOpen} onCancel={() => setRoleOpen(false)} onOk={() => roleForm.submit()} destroyOnHidden>
        <Form form={roleForm} layout="vertical" onFinish={submitRole}>
          <Form.Item label="账号主体">
            <Typography.Text>{selectedAccount?.displayName || '-'}</Typography.Text>
          </Form.Item>
          <Form.Item name="roleCode" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select options={roleOptions(selectedAccount)} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="账号详情"
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setSelectedAccount(null);
        }}
        footer={null}
        destroyOnHidden
        width={760}
      >
        {selectedAccount ? (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="账号ID">{selectedAccount.id}</Descriptions.Item>
            <Descriptions.Item label="账号类型">{accountTypeText(selectedAccount.accountType)}</Descriptions.Item>
            <Descriptions.Item label="显示名称">{selectedAccount.displayName}</Descriptions.Item>
            <Descriptions.Item label="登录账号">{selectedAccount.username || '-'}</Descriptions.Item>
            <Descriptions.Item label="手机号">{selectedAccount.phone || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{statusTag(selectedAccount.status)}</Descriptions.Item>
            <Descriptions.Item label="角色" span={2}>
              <Space wrap>
                {selectedAccount.roles.length
                  ? selectedAccount.roles.map((item) => <Tag key={item}>{accountTypeText(item)}</Tag>)
                  : '-'}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="新建订单权限">
              {selectedAccount.directPermissions.includes('order.create') ? <Tag color="green">已授权</Tag> : <Tag>未授权</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="直接权限">
              <Space wrap>
                {selectedAccount.directPermissions.length
                  ? selectedAccount.directPermissions.map((item) => <Tag key={item}>{item}</Tag>)
                  : '-'}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="所属商户">{selectedAccount.merchantName || '-'}</Descriptions.Item>
            <Descriptions.Item label="默认门店">{selectedAccount.storeName || '-'}</Descriptions.Item>
            <Descriptions.Item label="所属出资方">{selectedAccount.investorName || '-'}</Descriptions.Item>
            <Descriptions.Item label="最后登录">{dateText(selectedAccount.lastLoginAt)}</Descriptions.Item>
            <Descriptions.Item label="数据范围" span={2}>{scopeText(selectedAccount, stores)}</Descriptions.Item>
            <Descriptions.Item label="创建时间" span={2}>{dateText(selectedAccount.createdAt)}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Modal>

      <Modal
        title="编辑账号资料"
        open={editOpen}
        onCancel={() => {
          editForm.resetFields();
          setEditOpen(false);
          setSelectedAccount(null);
        }}
        onOk={() => editForm.submit()}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical" onFinish={submitEdit}>
          {selectedAccount?.username ? (
            <Form.Item name="username" label="登录账号" rules={[{ required: true, message: '请输入登录账号' }]}>
              <Input />
            </Form.Item>
          ) : (
            <Form.Item label="登录方式">
              <Typography.Text>支付宝用户授权账号</Typography.Text>
            </Form.Item>
          )}
          <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        open={passwordOpen}
        onCancel={() => {
          passwordForm.resetFields();
          setPasswordOpen(false);
          setSelectedAccount(null);
        }}
        onOk={() => passwordForm.submit()}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" onFinish={submitResetPassword}>
          <Form.Item label="账号主体">
            <Typography.Text>{selectedAccount?.displayName || '-'}</Typography.Text>
          </Form.Item>
          <Form.Item label="登录账号">
            <Typography.Text>{selectedAccount?.username || '-'}</Typography.Text>
          </Form.Item>
          <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                }
              })
            ]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="调整门店范围" open={scopeOpen} onCancel={() => setScopeOpen(false)} onOk={() => scopeForm.submit()} destroyOnHidden>
        <Form form={scopeForm} layout="vertical" onFinish={submitScopes}>
          <Form.Item label="所属商户">
            <Select disabled value={selectedAccount?.merchantId} options={merchantOptions} />
          </Form.Item>
          <Form.Item name="storeIds" label="授权门店" rules={[{ required: true, message: '请至少选择一个门店' }]}>
            <Select mode="multiple" options={scopedStoreOptions} placeholder="选择该账号可以访问的门店" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增账号"
        open={createOpen}
        onCancel={() => {
          createForm.resetFields();
          setCreateOpen(false);
        }}
        onOk={() => createForm.submit()}
        destroyOnHidden
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={submitCreate}
          onValuesChange={(changedValues) => {
            if ('roleCode' in changedValues) {
              createForm.setFieldsValue({ merchantId: undefined, investorId: undefined, storeIds: undefined });
            }
            if ('merchantId' in changedValues) {
              createForm.setFieldValue('storeIds', undefined);
            }
          }}
        >
          <Form.Item name="roleCode" label="账号角色" rules={[{ required: true, message: '请选择账号角色' }]}>
            <Select options={[...platformRoleOptions, ...merchantRoleOptions, ...investorRoleOptions]} />
          </Form.Item>
          <Form.Item name="username" label="登录账号" rules={[{ required: true, message: '请输入登录账号' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.roleCode !== next.roleCode}>
            {({ getFieldValue }) => {
              const roleCode = getFieldValue('roleCode') as SystemAccount['accountType'] | undefined;
              const merchantScoped = Boolean(roleCode && merchantRoleOptions.some((item) => item.value === roleCode));
              const investorScoped = roleCode === 'INVESTOR';
              const nonOwnerMerchant = merchantScoped && roleCode !== 'MERCHANT_OWNER';
              return (
                <>
                  {merchantScoped ? (
                    <Form.Item preserve={false} name="merchantId" label="所属商户" rules={[{ required: true, message: '请选择所属商户' }]}>
                      <Select options={merchantOptions} />
                    </Form.Item>
                  ) : null}
                  {investorScoped ? (
                    <Form.Item preserve={false} name="investorId" label="所属出资方" rules={[{ required: true, message: '请选择所属出资方' }]}>
                      <Select options={investorOptions} />
                    </Form.Item>
                  ) : null}
                  {nonOwnerMerchant ? (
                    <Form.Item preserve={false} name="storeIds" label="授权门店" rules={[{ required: true, message: '请至少选择一个门店' }]}>
                      <Select
                        mode="multiple"
                        options={stores
                          .filter((store) => store.merchantId === getFieldValue('merchantId'))
                          .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                      />
                    </Form.Item>
                  ) : null}
                </>
              );
            }}
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

const pageMetaMap: Record<SystemManagementMode, { title: string; description: string }> = {
  accounts: {
    title: '账号管理',
    description: '统一查看平台、商户、门店成员、出资方与消费者账号，并管理启停状态。'
  },
  roles: {
    title: '角色管理',
    description: '维护系统内置角色的职责边界，明确不同岗位默认具备的能力。'
  },
  permissions: {
    title: '权限管理',
    description: '统一查看系统权限点，作为角色授权和账号隔离的底层能力目录。'
  },
  scopes: {
    title: '数据范围',
    description: '管理商户体系账号的门店访问范围，确保车辆、配件、订单和维修数据按门店隔离。'
  }
};

function roleOptions(account: SystemAccount | null) {
  if (!account) {
    return [];
  }
  if (account.merchantId) {
    return merchantRoleOptions;
  }
  if (account.investorId) {
    return investorRoleOptions;
  }
  if (!account.username) {
    return [{ label: '消费者', value: 'CONSUMER' }];
  }
  return platformRoleOptions;
}

function statusTag(status: 'ENABLED' | 'DISABLED') {
  return <Tag color={status === 'ENABLED' ? 'green' : 'red'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function accountTypeText(value?: string | null) {
  const map: Record<string, string> = {
    PLATFORM_ADMIN: '平台管理员',
    FINANCE: '财务人员',
    MERCHANT_OWNER: '商户老板',
    STORE_MANAGER: '门店店长',
    STORE_OPERATOR: '门店运营',
    STORE_STAFF: '门店员工',
    MAINTENANCE_STAFF: '维修人员',
    WAREHOUSE_STAFF: '仓库人员',
    INVESTOR: '出资方',
    CONSUMER: '消费者'
  };
  return value ? map[value] || value : '-';
}

function scopeText(record: SystemAccount, stores: Store[]) {
  if (!record.storeScopes.length) {
    return '-';
  }
  return record.storeScopes.map((scope) => {
    if (scope.scopeType === 'ALL_MERCHANT_STORES') {
      return '商户全部门店';
    }
    const storeName = stores.find((store) => store.id === scope.storeId)?.storeName;
    return storeName || `门店#${scope.storeId}`;
  }).join('、');
}

function dateText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
