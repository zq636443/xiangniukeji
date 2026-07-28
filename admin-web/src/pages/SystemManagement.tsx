import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { Button, Descriptions, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Tag, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
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

type RoleEditForm = {
  roleName: string;
  status: 'ENABLED' | 'DISABLED';
  permissionCodes: string[];
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
type AccountGroup = 'platform' | 'merchant' | 'investor' | 'consumer';

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
  const [editingRole, setEditingRole] = useState<SystemRole | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [scopeOpen, setScopeOpen] = useState(false);
  const [roleEditOpen, setRoleEditOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [permissionUpdatingAccountId, setPermissionUpdatingAccountId] = useState<number>();
  const [accountGroup, setAccountGroup] = useState<AccountGroup>('merchant');
  const [accountKeyword, setAccountKeyword] = useState('');
  const [accountRole, setAccountRole] = useState<string>();
  const [accountStatus, setAccountStatus] = useState<SystemAccount['status']>();
  const [createForm] = Form.useForm<CreateForm>();
  const [editForm] = Form.useForm<EditForm>();
  const [passwordForm] = Form.useForm<ResetPasswordForm>();
  const [roleForm] = Form.useForm<RoleForm>();
  const [scopeForm] = Form.useForm<ScopeForm>();
  const [roleEditForm] = Form.useForm<RoleEditForm>();
  const createRoleCode = Form.useWatch('roleCode', createForm);
  const createMerchantId = Form.useWatch('merchantId', createForm);

  useEffect(() => {
    void loadAll();
  }, []);

  const merchantOptions = useMemo(() => merchants.map((merchant) => ({
    label: merchant.merchantName,
    value: merchant.id
  })), [merchants]);

  const enabledMerchantOptions = useMemo(() => merchants
    .filter((merchant) => merchant.status === 'ENABLED')
    .map((merchant) => ({ label: merchant.merchantName, value: merchant.id })), [merchants]);

  const investorOptions = useMemo(() => investors.map((investor) => ({
    label: investor.investorName,
    value: investor.id
  })), [investors]);

  const scopedStoreOptions = useMemo(() => {
    if (!selectedAccount?.merchantId) {
      return [];
    }
    return stores
      .filter((store) => store.merchantId === selectedAccount.merchantId && store.status === 'ENABLED')
      .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }));
  }, [selectedAccount, stores]);

  const createStoreOptions = useMemo(() => stores
    .filter((store) => store.merchantId === createMerchantId && store.status === 'ENABLED')
    .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id })), [createMerchantId, stores]);

  const createMerchantScoped = Boolean(createRoleCode && merchantRoleOptions.some((item) => item.value === createRoleCode));
  const createInvestorScoped = createRoleCode === 'INVESTOR';
  const createNonOwnerMerchant = createMerchantScoped && createRoleCode !== 'MERCHANT_OWNER';
  const createRoleOptions = useMemo(() => roles
    .filter((role) => role.status === 'ENABLED' && role.roleCode !== 'CONSUMER')
    .map((role) => ({ label: role.roleName, value: role.roleCode as SystemAccountCreatePayload['roleCode'] })), [roles]);
  const createAccountRoleOptions = useMemo(() => createRoleOptions.filter((option) => (
    accountGroup === 'platform' ? platformRoleOptions.some((item) => item.value === option.value)
      : accountGroup === 'investor' ? investorRoleOptions.some((item) => item.value === option.value)
        : merchantRoleOptions.some((item) => item.value === option.value)
  )), [accountGroup, createRoleOptions]);
  const accountGroupRows = useMemo(() => accounts.filter((item) => accountGroupOf(item) === accountGroup), [accountGroup, accounts]);
  const filteredAccounts = useMemo(() => {
    const keyword = accountKeyword.trim().toLowerCase();
    return accountGroupRows.filter((item) => {
      if (accountStatus && item.status !== accountStatus) return false;
      if (accountRole && !item.roles.includes(accountRole)) return false;
      if (!keyword) return true;
      return [item.displayName, item.username, item.phone, item.merchantName, item.storeName, item.investorName]
        .some((value) => String(value || '').toLowerCase().includes(keyword));
    });
  }, [accountGroupRows, accountKeyword, accountRole, accountStatus]);
  const accountGroupTabs = useMemo(() => (['platform', 'merchant', 'investor', 'consumer'] as AccountGroup[]).map((key) => ({
    key,
    label: `${accountGroupText(key)}（${accounts.filter((item) => accountGroupOf(item) === key).length}）`
  })), [accounts]);
  const accountRoleOptions = useMemo(() => roles
    .filter((role) => accountTypeBelongsToGroup(role.roleCode, accountGroup))
    .map((role) => ({ label: role.roleName, value: role.roleCode })), [accountGroup, roles]);
  const permissionOptions = useMemo(() => permissions.map((permission) => ({
    label: `${permission.permissionName} / ${permission.permissionCode}`,
    value: permission.permissionCode
  })), [permissions]);

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
      roleCode: accountGroup === 'platform' ? 'PLATFORM_ADMIN' : accountGroup === 'investor' ? 'INVESTOR' : 'MERCHANT_OWNER',
      password: 'Tupaixiong@2026'
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

  function openRoleEdit(record: SystemRole) {
    setEditingRole(record);
    roleEditForm.setFieldsValue({
      roleName: record.roleName,
      status: record.status,
      permissionCodes: record.permissions
    });
    setRoleEditOpen(true);
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

  async function submitRoleEdit(values: RoleEditForm) {
    if (!editingRole) return;
    await http.put(`/api/admin/system/roles/${editingRole.id}`, values);
    message.success('角色已更新');
    roleEditForm.resetFields();
    setEditingRole(null);
    setRoleEditOpen(false);
    await loadAll();
  }

  async function deleteRole(record: SystemRole) {
    await http.delete(`/api/admin/system/roles/${record.id}`);
    message.success('角色已删除');
    await loadAll();
  }

  async function toggleStatus(record: SystemAccount) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/system/accounts/${record.id}/status`, null, { params: { status } });
    message.success(record.status === 'ENABLED' ? '账号已停用' : '账号已启用');
    await loadAll();
  }

  async function deleteAccount(record: SystemAccount) {
    await http.delete(`/api/admin/system/accounts/${record.id}`);
    message.success('账号已删除');
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

  const accountColumns: TableColumnsType<SystemAccount> = [
    { title: '账号ID', dataIndex: 'id', width: 84 },
    { title: '显示名称', dataIndex: 'displayName', width: 150 },
    { title: '登录账号', dataIndex: 'username', width: 150, render: (value) => value || '-' },
    { title: '手机号', dataIndex: 'phone', width: 135, render: (value) => value || '-' },
    { title: '类型', dataIndex: 'accountType', width: 120, render: accountTypeText },
    { title: '角色', dataIndex: 'roles', width: 160, render: (value: string[]) => value.length ? value.map((item) => <Tag key={item}>{accountTypeText(item)}</Tag>) : '-' },
    ...(accountGroup === 'merchant' ? [
      {
        title: '新建订单',
        width: 110,
        align: 'center' as const,
        render: (_: unknown, record: SystemAccount) => record.roles.includes('STORE_MANAGER')
          ? <Tag color="green">店长默认</Tag>
          : (
            <Switch
              checked={record.directPermissions.includes('order.create')}
              loading={permissionUpdatingAccountId === record.id}
              onChange={(checked) => void toggleOrderCreatePermission(record, checked)}
            />
          )
      },
      { title: '所属商户', dataIndex: 'merchantName', width: 180, render: (value: unknown) => value || '-' },
      { title: '默认门店', dataIndex: 'storeName', width: 180, render: (value: unknown) => value || '-' }
    ] : []),
    ...(accountGroup === 'investor' ? [
      { title: '所属出资方', dataIndex: 'investorName', width: 200, render: (value: unknown) => value || '-' }
    ] : []),
    { title: '状态', dataIndex: 'status', width: 90, render: statusTag },
    { title: '最后登录', dataIndex: 'lastLoginAt', width: 150, render: dateText },
    {
      title: '操作',
      fixed: 'right',
      width: accountGroup === 'merchant' ? 440 : 360,
      render: (_: unknown, record: SystemAccount) => (
        <Space wrap>
          <Button size="small" onClick={() => openDetail(record)}>详情</Button>
          <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
          {record.username ? <Button size="small" onClick={() => openResetPassword(record)}>重置密码</Button> : null}
          <Button size="small" onClick={() => openRole(record)}>角色</Button>
          {record.merchantId && record.roles[0] !== 'MERCHANT_OWNER' ? (
            <Button size="small" onClick={() => openScopes(record)}>范围</Button>
          ) : null}
          <Button size="small" onClick={() => toggleStatus(record)}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
          <Popconfirm
            title="删除账号"
            description="删除后账号将立即退出登录并从账号列表移除，历史业务记录仍会保留。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteAccount(record)}
          >
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  const pageMeta = pageMetaMap[mode];

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>{pageMeta.title}</Typography.Title>
        <Typography.Text type="secondary">{pageMeta.description}</Typography.Text>
        {mode === 'accounts' && accountGroup !== 'consumer' ? (
          <Button type="primary" onClick={openCreate}>新增{accountGroupText(accountGroup)}</Button>
        ) : null}
      </Space>

      <section className="section">
        {mode === 'accounts' ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Tabs
              activeKey={accountGroup}
              items={accountGroupTabs}
              onChange={(key) => {
                setAccountGroup(key as AccountGroup);
                setAccountRole(undefined);
                setAccountStatus(undefined);
              }}
            />
            <Space wrap>
              <Input
                allowClear
                value={accountKeyword}
                onChange={(event) => setAccountKeyword(event.target.value)}
                placeholder="名称、账号、手机号或所属主体"
                style={{ width: 280 }}
              />
              <Select
                allowClear
                value={accountRole}
                onChange={setAccountRole}
                options={accountRoleOptions}
                placeholder="全部角色"
                style={{ width: 160 }}
              />
              <Select
                allowClear
                value={accountStatus}
                onChange={setAccountStatus}
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' }
                ]}
                placeholder="全部状态"
                style={{ width: 130 }}
              />
              <Button onClick={() => {
                setAccountKeyword('');
                setAccountRole(undefined);
                setAccountStatus(undefined);
              }}>重置筛选</Button>
              <Typography.Text type="secondary">当前显示 {filteredAccounts.length} 个账号</Typography.Text>
            </Space>
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              dataSource={filteredAccounts}
              scroll={{ x: accountGroup === 'merchant' ? 1700 : 1250 }}
              pagination={{
                defaultPageSize: 20,
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50, 100],
                showTotal: (total) => `共 ${total} 个账号`
              }}
              columns={accountColumns}
            />
          </Space>
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
              { title: '权限点', dataIndex: 'permissions', render: (value: string[]) => value.length ? value.map((item) => <Tag key={item}>{item}</Tag>) : '-' },
              {
                title: '操作',
                render: (_, record) => (
                  <Space>
                    <Button size="small" icon={<EditOutlined />} onClick={() => openRoleEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="删除角色"
                      description={record.roleCode === 'PLATFORM_ADMIN' ? '平台管理员角色不能删除。' : '仅未分配给任何账号的角色可以删除。'}
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      disabled={record.roleCode === 'PLATFORM_ADMIN'}
                      onConfirm={() => deleteRole(record)}
                    >
                      <Button size="small" danger icon={<DeleteOutlined />} disabled={record.roleCode === 'PLATFORM_ADMIN'}>删除</Button>
                    </Popconfirm>
                  </Space>
                )
              }
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
            <Select options={roleOptions(selectedAccount, roles)} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑角色"
        open={roleEditOpen}
        onCancel={() => {
          roleEditForm.resetFields();
          setEditingRole(null);
          setRoleEditOpen(false);
        }}
        onOk={() => roleEditForm.submit()}
        destroyOnHidden
        width={680}
      >
        <Form form={roleEditForm} layout="vertical" onFinish={submitRoleEdit}>
          <Form.Item label="角色编码">
            <Input disabled value={editingRole?.roleCode} />
          </Form.Item>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="角色状态" rules={[{ required: true, message: '请选择角色状态' }]}>
            <Select
              disabled={editingRole?.roleCode === 'PLATFORM_ADMIN'}
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' }
              ]}
            />
          </Form.Item>
          <Form.Item name="permissionCodes" label="角色权限">
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              options={permissionOptions}
              placeholder="选择该角色默认拥有的权限"
            />
          </Form.Item>
          {editingRole?.roleCode === 'PLATFORM_ADMIN' ? (
            <Typography.Text type="secondary">平台管理员角色始终保持启用，并保留系统管理权限。</Typography.Text>
          ) : null}
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
              {selectedAccount.permissions.includes('order.create')
                ? <Tag color="green">{selectedAccount.roles.includes('STORE_MANAGER') ? '店长角色默认' : '已授权'}</Tag>
                : <Tag>未授权</Tag>}
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
              const roleCode = changedValues.roleCode as SystemAccountCreatePayload['roleCode'];
              const merchantScoped = merchantRoleOptions.some((item) => item.value === roleCode);
              createForm.setFieldValue('investorId', undefined);
              if (!merchantScoped) {
                createForm.setFieldsValue({ merchantId: undefined, storeIds: undefined });
              } else if (roleCode === 'MERCHANT_OWNER') {
                createForm.setFieldValue('storeIds', undefined);
              }
            }
            if ('merchantId' in changedValues) {
              createForm.setFieldValue('storeIds', undefined);
            }
          }}
        >
          <Form.Item name="roleCode" label="账号角色" rules={[{ required: true, message: '请选择账号角色' }]}>
            <Select options={createAccountRoleOptions} />
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
          {createMerchantScoped ? (
            <Form.Item preserve={false} name="merchantId" label="所属商户" rules={[{ required: true, message: '请选择所属商户' }]}>
              <Select options={enabledMerchantOptions} />
            </Form.Item>
          ) : null}
          {createInvestorScoped ? (
            <Form.Item preserve={false} name="investorId" label="所属出资方" rules={[{ required: true, message: '请选择所属出资方' }]}>
              <Select options={investorOptions} />
            </Form.Item>
          ) : null}
          {createNonOwnerMerchant ? (
            <Form.Item preserve={false} name="storeIds" label="授权门店" rules={[{ required: true, message: '请至少选择一个门店' }]}>
              <Select
                mode="multiple"
                options={createStoreOptions}
                placeholder={createMerchantId ? '选择该账号可访问的门店' : '请先选择所属商户'}
              />
            </Form.Item>
          ) : null}
          {createRoleCode === 'MERCHANT_OWNER' ? (
            <Form.Item label="授权门店">
              <Typography.Text type="secondary">商户老板自动继承所属商户当前及以后新增的全部门店。</Typography.Text>
            </Form.Item>
          ) : null}
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

function roleOptions(account: SystemAccount | null, roles: SystemRole[]) {
  if (!account) {
    return [];
  }
  let allowedCodes: string[];
  if (account.merchantId) {
    allowedCodes = merchantRoleOptions.map((item) => item.value);
  } else if (account.investorId) {
    allowedCodes = investorRoleOptions.map((item) => item.value);
  } else if (!account.username) {
    allowedCodes = ['CONSUMER'];
  } else {
    allowedCodes = platformRoleOptions.map((item) => item.value);
  }
  return roles
    .filter((role) => allowedCodes.includes(role.roleCode) && (role.status === 'ENABLED' || account.roles.includes(role.roleCode)))
    .map((role) => ({ label: role.roleName, value: role.roleCode }));
}

function statusTag(status: 'ENABLED' | 'DISABLED') {
  return <Tag color={status === 'ENABLED' ? 'green' : 'red'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function accountGroupOf(account: SystemAccount): AccountGroup {
  if (account.accountType === 'CONSUMER') return 'consumer';
  if (account.accountType === 'INVESTOR' || account.investorId) return 'investor';
  if (merchantRoleOptions.some((item) => item.value === account.accountType) || account.merchantId) return 'merchant';
  return 'platform';
}

function accountTypeBelongsToGroup(accountType: string, group: AccountGroup) {
  if (group === 'platform') return platformRoleOptions.some((item) => item.value === accountType);
  if (group === 'merchant') return merchantRoleOptions.some((item) => item.value === accountType);
  if (group === 'investor') return accountType === 'INVESTOR';
  return accountType === 'CONSUMER';
}

function accountGroupText(group: AccountGroup) {
  const map: Record<AccountGroup, string> = {
    platform: '平台账号',
    merchant: '商户账号',
    investor: '出资方账号',
    consumer: '消费者账号'
  };
  return map[group];
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
