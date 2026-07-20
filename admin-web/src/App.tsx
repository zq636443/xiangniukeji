import {
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  DollarOutlined,
  FileDoneOutlined,
  FileProtectOutlined,
  FileSearchOutlined,
  LockOutlined,
  LogoutOutlined,
  PayCircleOutlined,
  ProductOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  SolutionOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  ConfigProvider,
  Dropdown,
  Form,
  Input,
  Layout,
  Menu,
  Select,
  Segmented,
  message,
  theme,
  Typography
} from 'antd';
import type { MenuProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  MerchantAssetWorkspace,
  MerchantDashboard,
  MerchantIncomeWorkspace,
  MerchantMaintenanceWorkspace,
  MerchantOrderWorkspace,
  MerchantOverdueWorkspace,
  MerchantSparePartWorkspace,
  MerchantStoreList
} from './pages/MerchantWorkspace';
import { InvestorAssetsPage, InvestorDashboard, InvestorIncomePage } from './pages/InvestorWorkspace';
import { http } from './services/request';
import { AgreementDeductManagement } from './pages/AgreementDeductManagement';
import { AssetFulfillmentManagement } from './pages/AssetFulfillmentManagement';
import { AssetManagement } from './pages/AssetManagement';
import { BillManagement } from './pages/BillManagement';
import { ComplianceManagement } from './pages/ComplianceManagement';
import { Dashboard } from './pages/Dashboard';
import { ExternalOrderManagement } from './pages/ExternalOrderManagement';
import { FundAuthManagement } from './pages/FundAuthManagement';
import { MerchantManagement } from './pages/MerchantManagement';
import { OrderManagement } from './pages/OrderManagement';
import { OpsManagement } from './pages/OpsManagement';
import { OverdueManagement } from './pages/OverdueManagement';
import { PaymentManagement } from './pages/PaymentManagement';
import { ProductManagement } from './pages/ProductManagement';
import { SettlementManagement } from './pages/SettlementManagement';
import { SparePartManagement } from './pages/SparePartManagement';
import { SystemManagement } from './pages/SystemManagement';
import { VoucherManagement } from './pages/VoucherManagement';
import type { CurrentAccount, LoginResponse, Store } from './types/api';

type WorkspaceMode = 'admin' | 'merchant' | 'investor';

type LoginMode = 'merchant' | 'investor';

type NavItem = {
  key: string;
  label: string;
  permission?: string;
  icon?: ReactNode;
  children?: NavItem[];
};

const merchantAccountTypes = new Set([
  'MERCHANT_OWNER',
  'STORE_MANAGER',
  'STORE_OPERATOR',
  'STORE_STAFF',
  'MAINTENANCE_STAFF',
  'WAREHOUSE_STAFF'
]);

const adminMenuItems: NavItem[] = [
  { key: 'dashboard', label: '经营工作台', permission: '', icon: <DashboardOutlined /> },
  {
    key: 'merchant-group',
    label: '商户体系',
    permission: 'merchant.read',
    icon: <ShopOutlined />,
    children: [
      { key: 'merchants', label: '商户管理', permission: 'merchant.read', icon: <TeamOutlined /> },
      { key: 'stores', label: '门店管理', permission: 'store.read', icon: <ShopOutlined /> },
      { key: 'employees', label: '员工账号', permission: 'merchant.read', icon: <UserOutlined /> }
    ]
  },
  {
    key: 'asset-group',
    label: '资产管理',
    permission: 'asset.read',
    icon: <BankOutlined />,
    children: [
      { key: 'investors', label: '出资方管理', permission: 'investor.read', icon: <SolutionOutlined /> },
      { key: 'assets', label: '资产台账', permission: 'asset.read', icon: <BankOutlined /> },
      { key: 'spareParts', label: '配件仓库', permission: 'inventory.read', icon: <ToolOutlined /> },
      { key: 'assetFulfillment', label: '履约凭证', permission: 'asset.read', icon: <FileDoneOutlined /> }
    ]
  },
  {
    key: 'product-group',
    label: '商品管理',
    permission: 'store.read',
    icon: <ProductOutlined />,
    children: [
      { key: 'skus', label: '链接管理', permission: 'store.read', icon: <ProductOutlined /> },
      { key: 'packages', label: 'SKU 管理', permission: 'store.read', icon: <ProfileOutlined /> },
      { key: 'storeSkus', label: '门店商品', permission: 'store.read', icon: <ShopOutlined /> }
    ]
  },
  {
    key: 'system-group',
    label: '系统管理',
    permission: 'system.admin',
    icon: <SettingOutlined />,
    children: [
      { key: 'systemAccounts', label: '账号管理', permission: 'system.admin', icon: <UserOutlined /> },
      { key: 'systemRoles', label: '角色管理', permission: 'system.admin', icon: <TeamOutlined /> },
      { key: 'systemPermissions', label: '权限管理', permission: 'system.admin', icon: <SafetyCertificateOutlined /> },
      { key: 'systemScopes', label: '数据范围', permission: 'system.admin', icon: <ShopOutlined /> }
    ]
  },
  {
    key: 'trade-group',
    label: '交易履约',
    permission: 'order.read',
    icon: <AppstoreOutlined />,
    children: [
      { key: 'orders', label: '订单管理', permission: 'order.read', icon: <ProfileOutlined /> },
      { key: 'bills', label: '账单管理', permission: 'order.read', icon: <FileSearchOutlined /> },
      { key: 'payments', label: '支付管理', permission: 'order.read', icon: <PayCircleOutlined /> },
      { key: 'deducts', label: '签约扣款', permission: 'order.read', icon: <FileProtectOutlined /> },
      { key: 'fundAuths', label: '资金授权', permission: 'order.read', icon: <SafetyCertificateOutlined /> },
      { key: 'compliance', label: '实名合同', permission: 'order.read', icon: <FileDoneOutlined /> },
      { key: 'vouchers', label: '团购核销', permission: 'order.read', icon: <AuditOutlined /> },
      { key: 'externalOrders', label: '外部补录订单', permission: 'order.read', icon: <ProfileOutlined /> },
      { key: 'overdues', label: '逾期汇总', permission: 'order.read', icon: <ClockCircleOutlined /> }
    ]
  },
  { key: 'settlement', label: '分润结算', permission: 'settlement.read', icon: <DollarOutlined /> },
  { key: 'ops', label: '运维审计', permission: 'system.admin', icon: <AuditOutlined /> }
];

const merchantMenuItems: NavItem[] = [
  { key: 'merchantDashboard', label: '商户工作台', permission: '', icon: <DashboardOutlined /> },
  {
    key: 'merchant-store-group',
    label: '门店运营',
    permission: 'order.read',
    icon: <ShopOutlined />,
    children: [
      { key: 'merchantStores', label: '我的门店', permission: 'merchant.read', icon: <ShopOutlined /> },
      { key: 'merchantExternalOrders', label: '外部补录订单', permission: 'order.read', icon: <ProfileOutlined /> },
      { key: 'merchantOrders', label: '门店订单', permission: 'order.read', icon: <ProfileOutlined /> },
      { key: 'merchantOverdues', label: '逾期订单', permission: 'order.read', icon: <ClockCircleOutlined /> }
    ]
  },
  {
    key: 'merchant-asset-group',
    label: '资产维保',
    permission: '',
    icon: <BankOutlined />,
    children: [
      { key: 'merchantAssets', label: '门店资产', permission: 'asset.read', icon: <BankOutlined /> },
      { key: 'merchantSpareParts', label: '门店配件', permission: 'inventory.read', icon: <ToolOutlined /> },
      { key: 'merchantMaintenances', label: '维修记录', permission: 'maintenance.read', icon: <AuditOutlined /> },
      { key: 'merchantIncome', label: '门店收益', permission: 'settlement.read', icon: <DollarOutlined /> }
    ]
  }
];

const investorMenuItems: NavItem[] = [
  { key: 'investorDashboard', label: '出资方工作台', permission: '', icon: <DashboardOutlined /> },
  {
    key: 'investor-asset-group',
    label: '资产与收益',
    permission: 'asset.read',
    icon: <BankOutlined />,
    children: [
      { key: 'investorAssets', label: '我的资产', permission: 'asset.read', icon: <BankOutlined /> },
      { key: 'investorIncome', label: '收益分成', permission: 'settlement.read', icon: <DollarOutlined /> }
    ]
  }
];

const loginMeta: Record<LoginMode, { title: string; eyebrow: string; description: string; endpoint: string }> = {
  merchant: {
    title: '运营后台登录',
    eyebrow: 'Operations Workspace',
    description: '平台管理员、商户老板和门店成员使用各自账号进入对应工作台。',
    endpoint: '/api/auth/workspace/login'
  },
  investor: {
    title: '出资方登录',
    eyebrow: 'Investor Workspace',
    description: '用于出资方查看自己名下资产状态与收益分成。',
    endpoint: '/api/auth/admin/login'
  }
};

export default function App() {
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  const [loginMode, setLoginMode] = useState<LoginMode>('merchant');
  const [activeMenu, setActiveMenu] = useState('dashboard');
  const [loading, setLoading] = useState(false);
  const [merchantStores, setMerchantStores] = useState<Store[]>([]);
  const [activeStoreId, setActiveStoreId] = useState<number>();
  const [loginForm] = Form.useForm<{ username: string; password: string }>();

  useEffect(() => {
    const token = localStorage.getItem('xniu_admin_token');
    if (!token) {
      return;
    }
    http.get<unknown, CurrentAccount>('/api/auth/me')
      .then(setAccount)
      .catch(() => localStorage.removeItem('xniu_admin_token'));
  }, []);

  useEffect(() => {
    loginForm.resetFields();
  }, [loginMode, loginForm]);

  const workspaceMode = useMemo<WorkspaceMode | null>(() => {
    if (!account) {
      return null;
    }
    if (merchantAccountTypes.has(account.accountType)) {
      return 'merchant';
    }
    if (account.accountType === 'INVESTOR') {
      return 'investor';
    }
    return 'admin';
  }, [account]);

  const currentMenuItems = useMemo(() => {
    if (workspaceMode === 'merchant') {
      return merchantMenuItems;
    }
    if (workspaceMode === 'investor') {
      return investorMenuItems;
    }
    return adminMenuItems;
  }, [workspaceMode]);

  const allowedMenuItems = useMemo(() => {
    if (!account) {
      return [];
    }
    return filterMenuItems(currentMenuItems, account);
  }, [account, currentMenuItems]);

  const menuItems = useMemo(() => allowedMenuItems.map(toAntdMenuItem), [allowedMenuItems]);
  const openKeys = useMemo(() => allowedMenuItems.filter((item) => item.children?.length).map((item) => item.key), [allowedMenuItems]);
  const [menuOpenKeys, setMenuOpenKeys] = useState<string[]>([]);
  const activeTitle = useMemo(
    () => findMenuTitle(allowedMenuItems, activeMenu) || (workspaceMode === 'merchant' ? '商户工作台' : workspaceMode === 'investor' ? '出资方工作台' : '经营工作台'),
    [allowedMenuItems, activeMenu, workspaceMode]
  );

  useEffect(() => {
    setMenuOpenKeys(openKeys);
  }, [openKeys]);

  useEffect(() => {
    if (!account || !workspaceMode) {
      return;
    }
    const leafKeys = flattenLeafKeys(allowedMenuItems);
    if (!leafKeys.includes(activeMenu)) {
      setActiveMenu(leafKeys[0] || (workspaceMode === 'merchant' ? 'merchantDashboard' : workspaceMode === 'investor' ? 'investorDashboard' : 'dashboard'));
    }
  }, [account, workspaceMode, allowedMenuItems, activeMenu]);

  useEffect(() => {
    if (!account || workspaceMode !== 'merchant') {
      setMerchantStores([]);
      setActiveStoreId(undefined);
      return;
    }
    void loadMerchantStores(account);
  }, [account, workspaceMode]);

  async function loadMerchantStores(currentAccount: CurrentAccount) {
    try {
      const stores = await http.get<unknown, Store[]>('/api/merchant/workbench/stores');
      setMerchantStores(stores);
      const preferredStoreId = currentAccount.storeId ?? stores[0]?.id;
      setActiveStoreId(preferredStoreId ?? undefined);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '门店列表加载失败');
      setMerchantStores([]);
      setActiveStoreId(undefined);
    }
  }

  useEffect(() => {
    if (!merchantStores.length) {
      return;
    }
    if (!activeStoreId || !merchantStores.some((store) => store.id === activeStoreId)) {
      setActiveStoreId(merchantStores[0].id);
    }
  }, [merchantStores, activeStoreId]);

  async function handleLogin(values: { username: string; password: string }) {
    setLoading(true);
    try {
      const result = await http.post<unknown, LoginResponse>(loginMeta[loginMode].endpoint, values);
      localStorage.setItem('xniu_admin_token', result.token);
      setAccount(result.account);
      message.success('登录成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  const handleLogout = async () => {
    try {
      await http.post('/api/auth/logout');
    } finally {
      localStorage.removeItem('xniu_admin_token');
      setAccount(null);
      setMerchantStores([]);
      setActiveStoreId(undefined);
      setLoginMode('merchant');
    }
  };

  if (!account || !workspaceMode) {
    return (
      <ConfigProvider theme={appTheme}>
        <div className="login-page">
          <section className="login-visual">
            <div className="login-mark">XN</div>
            <Typography.Title level={1}>享牛租赁运营平台</Typography.Title>
            <Typography.Paragraph>
              总部看全局，商户看门店，统一在一套业务系统里协同履约、资产和收益。
            </Typography.Paragraph>
            <div className="login-kpis">
              <span>门店订单</span>
              <span>资产履约</span>
              <span>分润结算</span>
            </div>
          </section>
          <section className="login-panel">
            <Typography.Text className="login-eyebrow">{loginMeta[loginMode].eyebrow}</Typography.Text>
            <Typography.Title level={3}>{loginMeta[loginMode].title}</Typography.Title>
            <Typography.Paragraph className="login-tip">
              {loginMeta[loginMode].description}
            </Typography.Paragraph>
            <Segmented
              block
              value={loginMode}
              options={[
                { label: '商户登录', value: 'merchant' },
                { label: '出资方登录', value: 'investor' }
              ]}
              onChange={(value) => setLoginMode(value as LoginMode)}
            />
            <Form form={loginForm} layout="vertical" onFinish={handleLogin} className="login-form">
              <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
                <Input size="large" prefix={<UserOutlined />} placeholder="请输入账号" autoComplete="username" />
              </Form.Item>
              <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                <Input.Password size="large" prefix={<LockOutlined />} placeholder="请输入密码" autoComplete="current-password" />
              </Form.Item>
              <Button className="login-submit" type="primary" htmlType="submit" loading={loading} block>
                登录
              </Button>
            </Form>
          </section>
        </div>
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider theme={appTheme}>
      <Layout className="app-shell">
        <Layout.Sider width={248} className="sider" theme="dark">
          <div className="brand">
            <div className="brand-logo">享</div>
            <div>
              <div className="brand-title">享牛科技</div>
              <div className="brand-subtitle">{workspaceMode === 'merchant' ? 'Merchant Workspace' : 'Rental Console'}</div>
            </div>
          </div>
          <Menu
            className="side-menu"
            theme="dark"
            mode="inline"
            openKeys={menuOpenKeys}
            selectedKeys={[activeMenu]}
            items={menuItems}
            onClick={(event) => setActiveMenu(event.key)}
            onOpenChange={(keys) => setMenuOpenKeys(keys as string[])}
          />
        </Layout.Sider>
        <Layout>
          <Layout.Header className="header">
            <div>
              <Typography.Text className="header-kicker">
                {workspaceMode === 'merchant' ? '商户经营后台' : workspaceMode === 'investor' ? '出资方工作台' : '总部管理后台'}
              </Typography.Text>
              <Typography.Title level={4}>{activeTitle}</Typography.Title>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {workspaceMode === 'merchant' ? (
                <Select
                  value={activeStoreId}
                  style={{ width: 240 }}
                  placeholder="选择门店"
                  options={merchantStores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                  onChange={setActiveStoreId}
                />
              ) : null}
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'account',
                      disabled: true,
                      label: (
                        <div className="account-menu-card">
                          <strong>{account.displayName}</strong>
                          <span>{workspaceMode === 'merchant' ? '商户工作台' : workspaceMode === 'investor' ? '出资方工作台' : '总部工作台'}</span>
                        </div>
                      )
                    },
                    { type: 'divider' },
                    {
                      key: 'logout',
                      icon: <LogoutOutlined />,
                      label: '退出登录',
                      onClick: handleLogout
                    }
                  ]
                }}
              >
                <button className="account-trigger" type="button">
                  <Avatar className="account-avatar">{account.displayName.slice(0, 1)}</Avatar>
                  <div className="account-meta">
                    <span>{account.displayName}</span>
                    <small>{account.accountType}</small>
                  </div>
                </button>
              </Dropdown>
            </div>
          </Layout.Header>
          <Layout.Content className="content">
            {renderPage(activeMenu, account, workspaceMode, activeStoreId, merchantStores)}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}

const appTheme = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#0f9f7a',
    colorSuccess: '#0f9f7a',
    colorWarning: '#d98b00',
    colorError: '#c2410c',
    colorInfo: '#2563eb',
    colorText: '#182230',
    colorTextSecondary: '#667085',
    colorBgLayout: '#f4f6f8',
    borderRadius: 6,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
  },
  components: {
    Button: {
      controlHeight: 34,
      borderRadius: 6,
      fontWeight: 600
    },
    Input: {
      controlHeight: 34,
      borderRadius: 6
    },
    Select: {
      controlHeight: 34,
      borderRadius: 6
    },
    Table: {
      headerBg: '#f7f9fb',
      headerColor: '#475467',
      rowHoverBg: '#f5fbf9'
    }
  }
};

function renderPage(
  activeMenu: string,
  account: CurrentAccount,
  workspaceMode: WorkspaceMode,
  activeStoreId: number | undefined,
  merchantStores: Store[]
) {
  if (workspaceMode === 'merchant') {
    switch (activeMenu) {
      case 'merchantStores':
        return <MerchantStoreList account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantOrders':
        return <MerchantOrderWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantExternalOrders':
        return <ExternalOrderManagement scope="merchant" storeId={activeStoreId} />;
      case 'merchantAssets':
        return <MerchantAssetWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantSpareParts':
        return <MerchantSparePartWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantMaintenances':
        return <MerchantMaintenanceWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantOverdues':
        return <MerchantOverdueWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      case 'merchantIncome':
        return <MerchantIncomeWorkspace account={account} storeId={activeStoreId} stores={merchantStores} />;
      default:
        return <MerchantDashboard account={account} storeId={activeStoreId} stores={merchantStores} />;
    }
  }

  if (workspaceMode === 'investor') {
    switch (activeMenu) {
      case 'investorAssets':
        return <InvestorAssetsPage account={account} />;
      case 'investorIncome':
        return <InvestorIncomePage />;
      default:
        return <InvestorDashboard account={account} />;
    }
  }

  switch (activeMenu) {
    case 'merchants':
      return <MerchantManagement mode="merchants" />;
    case 'stores':
      return <MerchantManagement mode="stores" />;
    case 'employees':
      return <MerchantManagement mode="employees" />;
    case 'investors':
      return <AssetManagement account={account} mode="investors" />;
    case 'assets':
      return <AssetManagement account={account} mode="assets" />;
    case 'assetFulfillment':
      return <AssetFulfillmentManagement />;
    case 'spareParts':
      return <SparePartManagement />;
    case 'skus':
      return <ProductManagement mode="skus" />;
    case 'packages':
      return <ProductManagement mode="packages" />;
    case 'storeSkus':
      return <ProductManagement mode="storeSkus" />;
    case 'orders':
      return <OrderManagement />;
    case 'externalOrders':
      return <ExternalOrderManagement scope="admin" />;
    case 'bills':
      return <BillManagement />;
    case 'payments':
      return <PaymentManagement />;
    case 'deducts':
      return <AgreementDeductManagement />;
    case 'fundAuths':
      return <FundAuthManagement />;
    case 'compliance':
      return <ComplianceManagement />;
    case 'vouchers':
      return <VoucherManagement />;
    case 'overdues':
      return <OverdueManagement />;
    case 'settlement':
      return <SettlementManagement />;
    case 'systemAccounts':
      return <SystemManagement mode="accounts" />;
    case 'systemRoles':
      return <SystemManagement mode="roles" />;
    case 'systemPermissions':
      return <SystemManagement mode="permissions" />;
    case 'systemScopes':
      return <SystemManagement mode="scopes" />;
    case 'ops':
      return <OpsManagement />;
    default:
      return <Dashboard />;
  }
}

function filterMenuItems(items: NavItem[], account: CurrentAccount): NavItem[] {
  return items
    .map((item) => {
      const children = item.children ? filterMenuItems(item.children, account) : undefined;
      const allowed = !item.permission || account.permissions.includes(item.permission) || account.permissions.includes('system.admin');
      if (children?.length) {
        return { ...item, children };
      }
      return allowed ? { ...item, children: undefined } : null;
    })
    .filter((item): item is NavItem => Boolean(item));
}

type AntdMenuItem = NonNullable<MenuProps['items']>[number];

function toAntdMenuItem(item: NavItem): AntdMenuItem {
  return {
    key: item.key,
    label: item.label,
    icon: item.icon,
    children: item.children?.map(toAntdMenuItem)
  };
}

function findMenuTitle(items: NavItem[], key: string): string | undefined {
  for (const item of items) {
    if (item.key === key) {
      return item.label;
    }
    const childTitle = item.children ? findMenuTitle(item.children, key) : undefined;
    if (childTitle) {
      return childTitle;
    }
  }
  return undefined;
}

function flattenLeafKeys(items: NavItem[]): string[] {
  return items.flatMap((item) => item.children?.length ? flattenLeafKeys(item.children) : [item.key]);
}
