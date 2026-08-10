import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  DollarOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  TeamOutlined,
  WalletOutlined,
  WarningOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Col,
  DatePicker,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message
} from 'antd';
import dayjs, { Dayjs } from 'dayjs';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type {
  Asset,
  ExternalRentalOrder,
  Investor,
  Merchant,
  ProfitRule,
  RentalOrder,
  SettlementIncomeEntry,
  SettlementOverview,
  SettlementSnapshot,
  SettlementStatement,
  SettlementStatementGenerateResult,
  SettlementStatementLine,
  StoreProfitOverview,
  Store,
  StoreSku
} from '../types/api';
import { downloadCsv } from '../utils/csv';

type RuleForm = {
  ruleName: string;
  storeId: number;
  sourceChannel?: string;
  priority: number;
  channelFeeRate: number;
  platformFeeRate: number;
  storeOperationRate: number;
  maintenanceFundRate: number;
  channelReferralRate: number;
  investorShareRate: number;
  effectiveAt: Dayjs;
  expiredAt?: Dayjs;
};

type PreviewForm = {
  storeSkuId: number;
  frameAssetId?: number;
  batteryAssetId?: number;
  rentalAmount: number;
  sourceChannel: string;
};

type StoreProfitStatus = SettlementStatement['status'] | 'NOT_GENERATED';

type StoreProfitRow = Omit<StoreProfitOverview, 'statementId' | 'statementNo' | 'status' | 'generatedAt'> & {
  statementId?: number;
  statementNo?: string;
  status: StoreProfitStatus;
  generatedAt?: string;
};

const sourceChannelOptions = [
  { label: '平台直租', value: 'DIRECT' },
  { label: '抖音', value: 'DOUYIN' },
  { label: '美团', value: 'MEITUAN' },
  { label: '闲鱼', value: 'XIANYU' }
];

const ruleChannelOptions = [
  ...sourceChannelOptions,
  { label: '线下', value: 'OFFLINE' },
  { label: '其他', value: 'OTHER' }
];

export function SettlementManagement() {
  const [activeTab, setActiveTab] = useState('storeProfit');
  const [loading, setLoading] = useState(false);
  const [statementLoading, setStatementLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string>();
  const [rules, setRules] = useState<ProfitRule[]>([]);
  const [snapshots, setSnapshots] = useState<SettlementSnapshot[]>([]);
  const [entries, setEntries] = useState<SettlementIncomeEntry[]>([]);
  const [overview, setOverview] = useState<SettlementOverview | null>(null);
  const [statements, setStatements] = useState<SettlementStatement[]>([]);
  const [storeProfits, setStoreProfits] = useState<StoreProfitOverview[]>([]);
  const [statementLines, setStatementLines] = useState<SettlementStatementLine[]>([]);
  const [statementMonth, setStatementMonth] = useState(currentMonth());
  const [statementGenerateOpen, setStatementGenerateOpen] = useState(false);
  const [statementGenerateMonth, setStatementGenerateMonth] = useState(currentMonth());
  const [statementDetailOpen, setStatementDetailOpen] = useState(false);
  const [selectedStatement, setSelectedStatement] = useState<SettlementStatement | null>(null);
  const [statementKeyword, setStatementKeyword] = useState('');
  const [statementBeneficiaryFilter, setStatementBeneficiaryFilter] = useState<SettlementStatement['beneficiaryType']>();
  const [statementStatusFilter, setStatementStatusFilter] = useState<SettlementStatement['status']>();
  const [statementMerchantFilter, setStatementMerchantFilter] = useState<number>();
  const [statementStoreFilter, setStatementStoreFilter] = useState<number>();
  const [statementLineKeyword, setStatementLineKeyword] = useState('');
  const [statementLineTypeFilter, setStatementLineTypeFilter] = useState<SettlementStatementLine['lineType']>();
  const [storeProfitKeyword, setStoreProfitKeyword] = useState('');
  const [storeProfitMerchantFilter, setStoreProfitMerchantFilter] = useState<number>();
  const [storeProfitStoreFilter, setStoreProfitStoreFilter] = useState<number>();
  const [storeProfitStatusFilter, setStoreProfitStatusFilter] = useState<StoreProfitStatus>();
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [investors, setInvestors] = useState<Investor[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [externalOrders, setExternalOrders] = useState<ExternalRentalOrder[]>([]);
  const [preview, setPreview] = useState<SettlementSnapshot | null>(null);
  const [snapshotDetailOpen, setSnapshotDetailOpen] = useState(false);
  const [selectedSnapshot, setSelectedSnapshot] = useState<SettlementSnapshot | null>(null);
  const [snapshotKeyword, setSnapshotKeyword] = useState('');
  const [snapshotSourceFilter, setSnapshotSourceFilter] = useState<SettlementSnapshot['sourceType']>();
  const [snapshotStoreFilter, setSnapshotStoreFilter] = useState<number>();
  const [snapshotChannelFilter, setSnapshotChannelFilter] = useState<string>();
  const [snapshotVersionFilter, setSnapshotVersionFilter] = useState<SettlementSnapshot['calculationVersion']>();
  const [ruleOpen, setRuleOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<ProfitRule | null>(null);
  const [ruleStoreFilter, setRuleStoreFilter] = useState<number>();
  const [ruleChannelFilter, setRuleChannelFilter] = useState<string>();
  const [ruleStatusFilter, setRuleStatusFilter] = useState<ProfitRule['status']>();
  const [incomeKeyword, setIncomeKeyword] = useState('');
  const [incomeMonth, setIncomeMonth] = useState(currentMonth());
  const [incomeStoreFilter, setIncomeStoreFilter] = useState<number>();
  const [incomeSourceFilter, setIncomeSourceFilter] = useState<SettlementIncomeEntry['sourceType']>();
  const [incomeBeneficiaryFilter, setIncomeBeneficiaryFilter] = useState<SettlementIncomeEntry['beneficiaryType']>();
  const [incomeStatusFilter, setIncomeStatusFilter] = useState<SettlementIncomeEntry['entryStatus']>();
  const [ruleForm] = Form.useForm<RuleForm>();
  const [previewForm] = Form.useForm<PreviewForm>();
  const [incomeForm] = Form.useForm<{ orderId: number }>();
  const selectedPreviewFrameAssetId = Form.useWatch('frameAssetId', previewForm);

  useEffect(() => {
    void loadAll();
  }, []);

  const merchantMap = useMemo(() => new Map(merchants.map((item) => [item.id, item])), [merchants]);
  const storeMap = useMemo(() => new Map(stores.map((item) => [item.id, item])), [stores]);
  const investorMap = useMemo(() => new Map(investors.map((item) => [item.id, item])), [investors]);
  const storeSkuMap = useMemo(() => new Map(storeSkus.map((item) => [item.id, item])), [storeSkus]);
  const assetMap = useMemo(() => new Map(assets.map((item) => [item.id, item])), [assets]);
  const ruleMap = useMemo(() => new Map(rules.map((item) => [item.id, item])), [rules]);
  const orderMap = useMemo(() => new Map(orders.map((item) => [item.id, item])), [orders]);
  const externalOrderMap = useMemo(() => new Map(externalOrders.map((item) => [item.id, item])), [externalOrders]);
  const storeSkuOptions = useMemo(() => storeSkus.map((item) => ({
    label: `${item.displayName} / ${item.storeName}`,
    value: item.id
  })), [storeSkus]);
  const frameAssetOptions = useMemo(() => assets
    .filter((item) => item.assetType === 'VEHICLE_FRAME' || item.assetType === 'INTEGRATED_VEHICLE')
    .map((item) => ({ label: `${item.serialNo} / ${item.assetType === 'INTEGRATED_VEHICLE' ? '车电一体' : '车架'}`, value: item.id })), [assets]);
  const batteryAssetOptions = useMemo(() => assets.filter((item) => item.assetType === 'BATTERY').map((item) => ({ label: item.serialNo, value: item.id })), [assets]);
  const integratedPreviewAssetSelected = useMemo(
    () => assets.some((item) => item.id === selectedPreviewFrameAssetId && item.assetType === 'INTEGRATED_VEHICLE'),
    [assets, selectedPreviewFrameAssetId]
  );
  const statementStoreOptions = useMemo(() => stores
    .filter((store) => !statementMerchantFilter || store.merchantId === statementMerchantFilter)
    .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id })), [statementMerchantFilter, stores]);
  const storeProfitStoreOptions = useMemo(() => stores
    .filter((store) => !storeProfitMerchantFilter || store.merchantId === storeProfitMerchantFilter)
    .map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id })), [storeProfitMerchantFilter, stores]);
  const storeProfitRows = useMemo<StoreProfitRow[]>(() => {
    const profitMap = new Map(storeProfits.map((profit) => [profit.storeId, profit]));
    const rows = stores.map((store) => {
      const profit = profitMap.get(store.id);
      if (profit) {
        return profit;
      }
      return {
        statementMonth,
        merchantId: store.merchantId,
        storeId: store.id,
        settlementBaseAmount: 0,
        signFeeAmount: 0,
        storeOperationAmount: 0,
        storeMaintenanceAmount: 0,
        batteryCostAmount: 0,
        maintenanceReimburseAmount: 0,
        maintenanceDeductAmount: 0,
        adjustmentAmount: 0,
        payableAmount: 0,
        orderCount: 0,
        billCount: 0,
        lineCount: 0,
        status: 'NOT_GENERATED' as const,
        confirmedAt: null,
        paidAt: null
      };
    });
    const knownStoreIds = new Set(stores.map((store) => store.id));
    storeProfits.filter((profit) => !knownStoreIds.has(profit.storeId)).forEach((profit) => rows.push(profit));
    return rows;
  }, [statementMonth, storeProfits, stores]);
  const filteredStoreProfitRows = useMemo(() => storeProfitRows.filter((row) => {
    if (storeProfitMerchantFilter && row.merchantId !== storeProfitMerchantFilter) {
      return false;
    }
    if (storeProfitStoreFilter && row.storeId !== storeProfitStoreFilter) {
      return false;
    }
    if (storeProfitStatusFilter && row.status !== storeProfitStatusFilter) {
      return false;
    }
    const keyword = storeProfitKeyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    const merchant = merchantMap.get(row.merchantId);
    const store = storeMap.get(row.storeId);
    return [merchant?.merchantName, merchant?.merchantCode, store?.storeName, store?.storeCode, row.statementNo]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword));
  }), [merchantMap, storeMap, storeProfitKeyword, storeProfitMerchantFilter, storeProfitRows, storeProfitStatusFilter, storeProfitStoreFilter]);
  const storeProfitTotals = useMemo(() => filteredStoreProfitRows.reduce((result, row) => {
    result.base += Number(row.settlementBaseAmount || 0);
    result.signFee += Number(row.signFeeAmount || 0);
    result.operation += Number(row.storeOperationAmount || 0);
    result.maintenance += Number(row.storeMaintenanceAmount || 0);
    result.batteryCost += Number(row.batteryCostAmount || 0);
    result.payable += Number(row.payableAmount || 0);
    if (row.status === 'PAID' || row.status === 'CLOSED') {
      result.paid += Number(row.payableAmount || 0);
    } else if (row.status !== 'NOT_GENERATED') {
      result.pending += Number(row.payableAmount || 0);
    }
    return result;
  }, { base: 0, signFee: 0, operation: 0, maintenance: 0, batteryCost: 0, payable: 0, paid: 0, pending: 0 }), [filteredStoreProfitRows]);
  const filteredRules = useMemo(() => rules.filter((rule) => {
    if (ruleStoreFilter && rule.storeId !== ruleStoreFilter) {
      return false;
    }
    if (ruleStatusFilter && rule.status !== ruleStatusFilter) {
      return false;
    }
    if (ruleChannelFilter === 'DEFAULT' && rule.sourceChannel) {
      return false;
    }
    if (ruleChannelFilter && ruleChannelFilter !== 'DEFAULT' && rule.sourceChannel !== ruleChannelFilter) {
      return false;
    }
    return true;
  }), [ruleChannelFilter, ruleStatusFilter, ruleStoreFilter, rules]);
  const ruleHealth = useMemo(() => {
    const activeStores = stores.filter((store) => store.status === 'ENABLED');
    const enabledRules = rules.filter((rule) => rule.status === 'ENABLED');
    const coveredStoreIds = new Set(enabledRules
      .filter((rule) => rule.ruleScope === 'STORE' && !rule.sourceChannel && rule.storeId)
      .map((rule) => rule.storeId as number));
    return {
      enabledRuleCount: enabledRules.length,
      activeStoreCount: activeStores.length,
      coveredStoreCount: activeStores.filter((store) => coveredStoreIds.has(store.id)).length,
      uncoveredStores: activeStores.filter((store) => !coveredStoreIds.has(store.id))
    };
  }, [rules, stores]);
  const filteredStatements = useMemo(() => statements.filter((statement) => {
    if (statementBeneficiaryFilter && statement.beneficiaryType !== statementBeneficiaryFilter) {
      return false;
    }
    if (statementStatusFilter && statement.status !== statementStatusFilter) {
      return false;
    }
    if (statementMerchantFilter && statement.merchantId !== statementMerchantFilter) {
      return false;
    }
    if (statementStoreFilter && statement.storeId !== statementStoreFilter) {
      return false;
    }
    const keyword = statementKeyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    const merchant = merchantMap.get(statement.merchantId);
    const store = storeMap.get(statement.storeId);
    const investor = investorMap.get(statement.beneficiaryId);
    return [
      statement.statementNo,
      statement.beneficiaryId,
      merchant?.merchantName,
      merchant?.merchantCode,
      store?.storeName,
      store?.storeCode,
      statement.beneficiaryType === 'INVESTOR' ? investor?.investorName : null,
      statement.beneficiaryType === 'INVESTOR' ? investor?.investorCode : null
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword));
  }), [investorMap, merchantMap, statementBeneficiaryFilter, statementKeyword, statementMerchantFilter, statementStatusFilter, statementStoreFilter, statements, storeMap]);
  const statementStatusCounts = useMemo(() => statements.reduce<Record<SettlementStatement['status'], number>>((result, statement) => {
    result[statement.status] += 1;
    return result;
  }, { DRAFT: 0, RECONCILING: 0, CONFIRMED: 0, PAYABLE: 0, PAID: 0, CLOSED: 0 }), [statements]);
  const statementLocked = statementStatusCounts.CONFIRMED + statementStatusCounts.PAYABLE + statementStatusCounts.PAID + statementStatusCounts.CLOSED > 0;
  const statementFinishedCount = statementStatusCounts.PAID + statementStatusCounts.CLOSED;
  const statementProgress = statements.length === 0 ? 0 : Math.round((statementFinishedCount / statements.length) * 100);
  const filteredSnapshots = useMemo(() => snapshots.filter((snapshot) => {
    if (snapshotSourceFilter && snapshot.sourceType !== snapshotSourceFilter) {
      return false;
    }
    if (snapshotStoreFilter && snapshot.storeId !== snapshotStoreFilter) {
      return false;
    }
    if (snapshotChannelFilter && snapshot.sourceChannel !== snapshotChannelFilter) {
      return false;
    }
    if (snapshotVersionFilter && snapshot.calculationVersion !== snapshotVersionFilter) {
      return false;
    }
    const keyword = snapshotKeyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    const rule = ruleMap.get(snapshot.matchedRuleId);
    const store = storeMap.get(snapshot.storeId);
    const storeSku = storeSkuMap.get(snapshot.storeSkuId);
    const sourceNo = snapshot.sourceType === 'ORDER'
      ? orderMap.get(snapshot.sourceId || 0)?.orderNo
      : snapshot.sourceType === 'EXTERNAL_ORDER'
        ? externalOrderMap.get(snapshot.sourceId || 0)?.recordNo
        : '人工测算';
    return [snapshot.snapshotNo, snapshot.sourceId, sourceNo, rule?.ruleName, rule?.ruleCode, store?.storeName, store?.storeCode, storeSku?.displayName]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword));
  }), [externalOrderMap, orderMap, ruleMap, snapshotChannelFilter, snapshotKeyword, snapshotSourceFilter, snapshotStoreFilter, snapshotVersionFilter, snapshots, storeMap, storeSkuMap]);
  const snapshotTotals = useMemo(() => filteredSnapshots.reduce((result, snapshot) => ({
    base: result.base + Number(snapshot.settlementBaseAmount || 0),
    platform: result.platform + Number(snapshot.platformFeeAmount || 0),
    store: result.store + Number(snapshot.storeOperationAmount || 0) + Number(snapshot.maintenanceFundAmount || 0),
    channel: result.channel + Number(snapshot.channelFeeAmount || 0) + Number(snapshot.channelReferralAmount || 0),
    investor: result.investor + Number(snapshot.investorShareAmount || 0)
  }), { base: 0, platform: 0, store: 0, channel: 0, investor: 0 }), [filteredSnapshots]);
  const filteredEntries = useMemo(() => entries.filter((entry) => {
    if (incomeMonth && dayjs(entry.occurredAt).format('YYYY-MM') !== incomeMonth) {
      return false;
    }
    if (!incomeSourceFilter && entry.sourceType === 'ORDER') {
      return false;
    }
    if (incomeStoreFilter && entry.storeId !== incomeStoreFilter) {
      return false;
    }
    if (incomeSourceFilter && entry.sourceType !== incomeSourceFilter) {
      return false;
    }
    if (incomeBeneficiaryFilter && entry.beneficiaryType !== incomeBeneficiaryFilter) {
      return false;
    }
    if (incomeStatusFilter && entry.entryStatus !== incomeStatusFilter) {
      return false;
    }
    const keyword = incomeKeyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    const store = storeMap.get(entry.storeId);
    const beneficiaryName = entry.beneficiaryType === 'INVESTOR'
      ? investorMap.get(entry.beneficiaryId || 0)?.investorName
      : entry.beneficiaryType === 'MERCHANT'
        ? merchantMap.get(entry.merchantId)?.merchantName
        : beneficiaryText(entry.beneficiaryType);
    return [entry.entryNo, entry.sourceNo, entry.sourceId, entry.orderId, store?.storeName, store?.storeCode, beneficiaryName, entry.remark]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword));
  }), [entries, incomeBeneficiaryFilter, incomeKeyword, incomeMonth, incomeSourceFilter, incomeStatusFilter, incomeStoreFilter, investorMap, merchantMap, storeMap]);
  const settlementPayableEntries = useMemo(
    () => filteredEntries.filter((entry) => entry.beneficiaryType === 'MERCHANT' || entry.beneficiaryType === 'INVESTOR'),
    [filteredEntries]
  );
  const incomeTotals = useMemo(() => settlementPayableEntries.reduce((result, entry) => {
    result[entry.entryStatus] += Number(entry.amount || 0);
    return result;
  }, { PENDING: 0, SETTLED: 0, FROZEN: 0 }), [settlementPayableEntries]);
  const incomeBusinessCount = useMemo(
    () => new Set(filteredEntries.map((entry) => `${entry.sourceType}:${entry.sourceId}`)).size,
    [filteredEntries]
  );
  const filteredStatementLines = useMemo(() => statementLines.filter((line) => {
    if (statementLineTypeFilter && line.lineType !== statementLineTypeFilter) {
      return false;
    }
    const keyword = statementLineKeyword.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    const store = storeMap.get(line.storeId);
    const asset = line.assetId ? assetMap.get(line.assetId) : null;
    const externalOrder = line.sourceType === 'EXTERNAL_ORDER' ? externalOrderMap.get(line.sourceId) : null;
    return [line.lineNo, line.sourceId, line.orderId, line.billId, line.remark, store?.storeName, asset?.serialNo, externalOrder?.recordNo]
      .some((value) => String(value ?? '').toLowerCase().includes(keyword));
  }), [assetMap, externalOrderMap, statementLineKeyword, statementLineTypeFilter, statementLines, storeMap]);
  const statementLineTotals = useMemo(() => statementLines.reduce((result, line) => {
    result[line.lineType] = (result[line.lineType] || 0) + Number(line.amount || 0);
    return result;
  }, {} as Partial<Record<SettlementStatementLine['lineType'], number>>), [statementLines]);

  useEffect(() => {
    if (integratedPreviewAssetSelected) {
      previewForm.setFieldValue('batteryAssetId', undefined);
    }
  }, [integratedPreviewAssetSelected, previewForm]);

  async function loadAll() {
    setLoading(true);
    try {
      const [
        ruleData,
        snapshotData,
        entryData,
        overviewData,
        statementData,
        storeProfitData,
        storeSkuData,
        storeData,
        merchantData,
        investorData,
        assetData,
        orderData,
        externalOrderData
      ] = await Promise.all([
        http.get<unknown, ProfitRule[]>('/api/admin/settlement/store-rules'),
        http.get<unknown, SettlementSnapshot[]>('/api/admin/settlement/snapshots'),
        http.get<unknown, SettlementIncomeEntry[]>('/api/admin/settlement/income/entries'),
        http.get<unknown, SettlementOverview>('/api/admin/settlement/statements/overview', { params: { month: statementMonth } }),
        http.get<unknown, SettlementStatement[]>('/api/admin/settlement/statements', { params: { month: statementMonth } }),
        http.get<unknown, StoreProfitOverview[]>('/api/admin/settlement/statements/store-profit-overview', { params: { month: statementMonth } }),
        http.get<unknown, StoreSku[]>('/api/admin/products/store-skus'),
        http.get<unknown, Store[]>('/api/admin/stores'),
        http.get<unknown, Merchant[]>('/api/admin/merchants'),
        http.get<unknown, Investor[]>('/api/admin/investors'),
        http.get<unknown, Asset[]>('/api/admin/assets'),
        http.get<unknown, RentalOrder[]>('/api/admin/orders'),
        http.get<unknown, ExternalRentalOrder[]>('/api/admin/external-orders')
      ]);
      setRules(ruleData);
      setSnapshots(snapshotData);
      setEntries(entryData);
      setOverview(overviewData);
      setStatements(statementData);
      setStoreProfits(storeProfitData);
      setStoreSkus(storeSkuData);
      setStores(storeData);
      setMerchants(merchantData);
      setInvestors(investorData);
      setAssets(assetData);
      setOrders(orderData);
      setExternalOrders(externalOrderData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '分润结算数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function reloadStatements(month = statementMonth) {
    setStatementLoading(true);
    try {
      const [overviewData, statementData, storeProfitData] = await Promise.all([
        http.get<unknown, SettlementOverview>('/api/admin/settlement/statements/overview', { params: { month } }),
        http.get<unknown, SettlementStatement[]>('/api/admin/settlement/statements', { params: { month } }),
        http.get<unknown, StoreProfitOverview[]>('/api/admin/settlement/statements/store-profit-overview', { params: { month } })
      ]);
      setOverview(overviewData);
      setStatements(statementData);
      setStoreProfits(storeProfitData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '月结数据加载失败');
    } finally {
      setStatementLoading(false);
    }
  }

  function openRuleCreator() {
    setEditingRule(null);
    ruleForm.setFieldsValue({
      ruleName: '',
      storeId: undefined,
      sourceChannel: undefined,
      priority: 0,
      channelFeeRate: 5,
      platformFeeRate: 3,
      storeOperationRate: 15,
      maintenanceFundRate: 10,
      channelReferralRate: 20,
      investorShareRate: 55,
      effectiveAt: dayjs(),
      expiredAt: undefined
    });
    setRuleOpen(true);
  }

  function openRuleEditor(record: ProfitRule) {
    setEditingRule(record);
    ruleForm.setFieldsValue({
      ruleName: record.ruleName,
      storeId: record.storeId || undefined,
      sourceChannel: record.sourceChannel || undefined,
      priority: record.priority,
      channelFeeRate: toPercentValue(record.channelFeeRate),
      platformFeeRate: toPercentValue(record.platformFeeRate),
      storeOperationRate: toPercentValue(record.storeOperationRate),
      maintenanceFundRate: toPercentValue(record.maintenanceFundRate),
      channelReferralRate: toPercentValue(record.channelReferralRate),
      investorShareRate: toPercentValue(record.investorShareRate),
      effectiveAt: dayjs(record.effectiveAt),
      expiredAt: record.expiredAt ? dayjs(record.expiredAt) : undefined
    });
    setRuleOpen(true);
  }

  async function saveRule(values: RuleForm) {
    if (values.channelFeeRate + values.platformFeeRate >= 100) {
      message.error('渠道核销扣点与租赁平台扣点之和必须小于 100%');
      return;
    }
    const distributionRate = values.storeOperationRate
      + values.maintenanceFundRate
      + values.channelReferralRate
      + values.investorShareRate;
    if (Math.abs(distributionRate - 100) > 0.001) {
      message.error('门店运营、门店维修、渠道引流、出资方比例之和必须等于 100%');
      return;
    }
    if (values.expiredAt && !values.expiredAt.isAfter(values.effectiveAt)) {
      message.error('失效时间必须晚于生效时间');
      return;
    }
    const payload = {
      ruleName: values.ruleName.trim(),
      ruleScope: 'STORE',
      sourceChannel: values.sourceChannel || null,
      priority: values.priority,
      skuId: null,
      merchantId: null,
      storeId: values.storeId,
      storeSkuId: null,
      channelFeeRate: fromPercentValue(values.channelFeeRate),
      platformFeeRate: fromPercentValue(values.platformFeeRate),
      storeOperationRate: fromPercentValue(values.storeOperationRate),
      maintenanceFundRate: fromPercentValue(values.maintenanceFundRate),
      channelReferralRate: fromPercentValue(values.channelReferralRate),
      investorShareRate: fromPercentValue(values.investorShareRate),
      effectiveAt: values.effectiveAt.format('YYYY-MM-DDTHH:mm:ss'),
      expiredAt: values.expiredAt?.format('YYYY-MM-DDTHH:mm:ss') || null
    };
    if (editingRule) {
      await http.put(`/api/admin/settlement/rules/${editingRule.id}`, payload);
    } else {
      await http.post('/api/admin/settlement/rules', payload);
    }
    setRuleOpen(false);
    setEditingRule(null);
    ruleForm.resetFields();
    message.success(editingRule ? '分润规则已更新，新规则仅用于之后生成的分润快照' : '分润规则已新增');
    await loadAll();
  }

  async function updateRuleStatus(record: ProfitRule) {
    const status = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/settlement/rules/${record.id}/status`, null, { params: { status } });
    message.success(status === 'ENABLED' ? '分润规则已启用' : '分润规则已停用');
    await loadAll();
  }

  async function deleteRule(record: ProfitRule) {
    await http.delete(`/api/admin/settlement/rules/${record.id}`);
    message.success('分润规则已删除');
    await loadAll();
  }

  async function previewSettlement(values: PreviewForm) {
    const data = await http.post<unknown, SettlementSnapshot>('/api/admin/settlement/preview', values);
    setPreview(data);
  }

  async function createSnapshot() {
    const values = await previewForm.validateFields();
    setActionLoading('snapshot-create');
    try {
      const data = await http.post<unknown, SettlementSnapshot>('/api/admin/settlement/snapshots', {
        ...values,
        sourceType: 'PREVIEW'
      });
      setPreview(data);
      message.success('测算快照已保存，可在分润快照中追溯');
      await loadAll();
    } finally {
      setActionLoading(undefined);
    }
  }

  async function generateIncome(values: { orderId: number }) {
    setActionLoading('income-generate');
    try {
      await http.post(`/api/admin/settlement/income/orders/${values.orderId}/generate`);
      message.success('收益台账已生成');
      incomeForm.resetFields();
      await loadAll();
    } finally {
      setActionLoading(undefined);
    }
  }

  async function updateEntryStatus(record: SettlementIncomeEntry, status: SettlementIncomeEntry['entryStatus']) {
    setActionLoading(`income-${record.id}`);
    try {
      await http.put(`/api/admin/settlement/income/entries/${record.id}/status`, null, { params: { status } });
      message.success(status === 'SETTLED' ? '已标记结算' : '收益已冻结');
      await loadAll();
    } finally {
      setActionLoading(undefined);
    }
  }

  function openStatementGenerate() {
    setStatementGenerateMonth(statementMonth);
    setStatementGenerateOpen(true);
  }

  async function generateStatements() {
    if (!statementGenerateMonth) {
      message.warning('请选择要生成月结单的月份');
      return;
    }
    setActionLoading('statement-generate');
    try {
      const month = statementGenerateMonth;
      const result = await http.post<unknown, SettlementStatementGenerateResult>('/api/admin/settlement/statements/generate', null, {
        params: { month }
      });
      message.success(`已按 ${month} 生成 ${result.merchantStatementCount} 张门店月结单，${result.investorStatementCount} 张出资方月结单`);
      setStatementGenerateOpen(false);
      setStatementMonth(month);
      await reloadStatements(month);
    } finally {
      setActionLoading(undefined);
    }
  }

  async function openStatement(record: SettlementStatement) {
    setActionLoading(`statement-detail-${record.id}`);
    try {
      const lines = await http.get<unknown, SettlementStatementLine[]>(`/api/admin/settlement/statements/${record.id}/lines`);
      setSelectedStatement(record);
      setStatementLines(lines);
      setStatementLineKeyword('');
      setStatementLineTypeFilter(undefined);
      setStatementDetailOpen(true);
    } finally {
      setActionLoading(undefined);
    }
  }

  async function updateStatementStatus(record: SettlementStatement, status: SettlementStatement['status']) {
    setActionLoading(`statement-${record.id}`);
    try {
      const updated = await http.put<unknown, SettlementStatement>(`/api/admin/settlement/statements/${record.id}/status`, null, { params: { status } });
      message.success(`月结单已更新为“${statementStatusText(status)}”`);
      await reloadStatements();
      if (selectedStatement?.id === record.id) {
        setSelectedStatement(updated);
      }
    } finally {
      setActionLoading(undefined);
    }
  }

  function handleStatementMonthChange(value: Dayjs | null) {
    const month = value?.format('YYYY-MM') || currentMonth();
    setStatementMonth(month);
    void reloadStatements(month);
  }

  function statementBeneficiaryName(record: SettlementStatement) {
    if (record.beneficiaryType === 'INVESTOR') {
      return investorMap.get(record.beneficiaryId)?.investorName || `出资方 #${record.beneficiaryId}`;
    }
    return storeMap.get(record.storeId)?.storeName
      || merchantMap.get(record.merchantId)?.merchantName
      || `商户 #${record.beneficiaryId}`;
  }

  function statementBeneficiaryCode(record: SettlementStatement) {
    if (record.beneficiaryType === 'INVESTOR') {
      return investorMap.get(record.beneficiaryId)?.investorCode || `ID ${record.beneficiaryId}`;
    }
    const store = storeMap.get(record.storeId);
    return store?.storeCode || merchantMap.get(record.merchantId)?.merchantCode || `ID ${record.beneficiaryId}`;
  }

  function snapshotSourceName(record: SettlementSnapshot) {
    if (record.sourceType === 'ORDER') {
      return orderMap.get(record.sourceId || 0)?.orderNo || `正式订单 #${record.sourceId || '-'}`;
    }
    if (record.sourceType === 'EXTERNAL_ORDER') {
      return externalOrderMap.get(record.sourceId || 0)?.recordNo || `补录订单 #${record.sourceId || '-'}`;
    }
    return '人工规则测算';
  }

  function incomeBeneficiaryName(record: SettlementIncomeEntry) {
    if (record.beneficiaryType === 'INVESTOR') {
      return investorMap.get(record.beneficiaryId || 0)?.investorName || `出资方 #${record.beneficiaryId || '-'}`;
    }
    if (record.beneficiaryType === 'MERCHANT') {
      return storeMap.get(record.storeId)?.storeName || merchantMap.get(record.merchantId)?.merchantName || `商户 #${record.merchantId}`;
    }
    return beneficiaryText(record.beneficiaryType);
  }

  function openSnapshot(record: SettlementSnapshot) {
    setSelectedSnapshot(record);
    setSnapshotDetailOpen(true);
  }

  async function openStoreProfit(record: StoreProfitRow) {
    if (!record.statementId) {
      message.info('该门店本月尚未生成月结单');
      return;
    }
    const statement = statements.find((item) => item.id === record.statementId);
    if (!statement) {
      message.error('月结单数据未加载，请刷新本月后重试');
      return;
    }
    await openStatement(statement);
  }

  function exportStoreProfits() {
    downloadCsv(`门店实际分润-${statementMonth}`, [
      '月份', '商户', '门店', '门店编码', '实际核销/实收基数', '签单费', '门店运营分润', '门店维修分润',
      '门店应付电池公司', '维修补回', '维保扣减', '人工调整', '实际应结算', '已打款', '待结算', '订单数', '账单数', '状态', '月结单号'
    ], filteredStoreProfitRows.map((record) => [
      record.statementMonth,
      merchantMap.get(record.merchantId)?.merchantName,
      storeMap.get(record.storeId)?.storeName,
      storeMap.get(record.storeId)?.storeCode,
      record.settlementBaseAmount,
      record.signFeeAmount,
      record.storeOperationAmount,
      record.storeMaintenanceAmount,
      record.batteryCostAmount,
      record.maintenanceReimburseAmount,
      record.maintenanceDeductAmount,
      record.adjustmentAmount,
      record.status === 'NOT_GENERATED' ? 0 : record.payableAmount,
      record.status === 'PAID' || record.status === 'CLOSED' ? record.payableAmount : 0,
      record.status !== 'NOT_GENERATED' && record.status !== 'PAID' && record.status !== 'CLOSED' ? record.payableAmount : 0,
      record.orderCount,
      record.billCount,
      storeProfitStatusText(record.status),
      record.statementNo
    ]));
  }

  function exportStatements() {
    downloadCsv(`月结中心-${statementMonth}`, [
      '月结单号', '月份', '对象类型', '结算对象', '对象编码', '商户', '门店', '实收租金基数', '签单费', '分润收益',
      '运营手续费', '门店应付电池公司', '维保扣减', '调整金额', '应结算金额', '订单数', '账单数', '明细数', '状态', '操作生成时间', '确认时间', '打款时间'
    ], filteredStatements.map((record) => [
      record.statementNo,
      record.statementMonth,
      statementBeneficiaryText(record.beneficiaryType),
      statementBeneficiaryName(record),
      statementBeneficiaryCode(record),
      merchantMap.get(record.merchantId)?.merchantName,
      storeMap.get(record.storeId)?.storeName,
      record.rentBaseAmount,
      record.signFeeIncomeAmount,
      record.rentShareIncomeAmount,
      record.operationFeeAmount,
      record.batteryCostAmount,
      record.maintenanceDeductAmount,
      record.adjustmentAmount,
      record.payableAmount,
      record.orderCount,
      record.billCount,
      record.lineCount,
      statementStatusText(record.status),
      record.generatedAt,
      record.confirmedAt,
      record.paidAt
    ]));
  }

  function exportSnapshots() {
    downloadCsv('分润快照', [
      '快照号', '业务来源', '来源类型', '来源渠道', '门店', '门店商品', '命中规则', '规则范围', '结算基数', '渠道核销扣点',
      '平台扣点', '门店运营', '门店维修', '渠道引流', '出资方', '计算版本', '生成时间'
    ], filteredSnapshots.map((record) => [
      record.snapshotNo,
      snapshotSourceName(record),
      snapshotSourceTypeText(record.sourceType),
      channelText(record.sourceChannel),
      storeMap.get(record.storeId)?.storeName,
      storeSkuMap.get(record.storeSkuId)?.displayName,
      ruleMap.get(record.matchedRuleId)?.ruleName || record.matchedRuleId,
      ruleScopeText(record.matchedRuleScope),
      record.settlementBaseAmount,
      record.channelFeeAmount,
      record.platformFeeAmount,
      record.storeOperationAmount,
      record.maintenanceFundAmount,
      record.channelReferralAmount,
      record.investorShareAmount,
      calculationVersionText(record.calculationVersion),
      record.createdAt
    ]));
  }

  function exportIncomeEntries() {
    downloadCsv('收益台账', [
      '收益单号', '来源', '业务单号', '门店', '收益方', '收益类型', '金额', '状态', '备注', '计入时间', '结算时间'
    ], filteredEntries.map((record) => [
      record.entryNo,
      incomeSourceText(record.sourceType),
      record.sourceNo || record.sourceId,
      storeMap.get(record.storeId)?.storeName,
      incomeBeneficiaryName(record),
      lineTypeText(record.lineType),
      record.amount,
      incomeStatusText(record.entryStatus),
      record.remark,
      record.occurredAt,
      record.settledAt
    ]));
  }

  function exportStatementLines() {
    if (!selectedStatement) {
      return;
    }
    downloadCsv(`月结明细-${selectedStatement.statementNo}`, [
      '明细号', '类型', '业务来源', '订单ID', '账单ID', '资产', '门店', '发生时间', '金额', '备注'
    ], filteredStatementLines.map((line) => [
      line.lineNo,
      statementLineText(line.lineType),
      statementLineSourceText(line, externalOrderMap),
      line.orderId,
      line.billId,
      line.assetId ? assetMap.get(line.assetId)?.serialNo || line.assetId : null,
      storeMap.get(line.storeId)?.storeName,
      line.occurredAt,
      line.amount,
      line.remark
    ]));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <div className="toolbar settlement-page-header">
        <div>
          <Typography.Text className="header-kicker">Settlement Control</Typography.Text>
          <Typography.Title level={3}>分润结算</Typography.Title>
          <Typography.Paragraph>按月对账、追溯每笔分润快照，并推进门店与出资方结算。</Typography.Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadAll()}>刷新全部</Button>
      </div>

      <Tabs
        className="settlement-tabs"
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'storeProfit',
            label: <span>门店分润 <span className="tab-count">{storeProfits.length}/{stores.length}</span></span>,
            children: (
              <Space direction="vertical" size={16} className="page-stack settlement-tab-content">
                <div className="section">
                  <div className="section-head settlement-action-head">
                    <div>
                      <Typography.Title level={4}>{statementMonth} 门店实际分润</Typography.Title>
                      <Typography.Text type="secondary">以正式月结单为口径，展示每个门店已经确认进入财务结算的数据。</Typography.Text>
                    </div>
                    <Space wrap>
                      <DatePicker
                        picker="month"
                        allowClear={false}
                        format="YYYY年MM月"
                        value={dayjs(`${statementMonth}-01`)}
                        onChange={handleStatementMonthChange}
                      />
                      <Button icon={<ReloadOutlined />} loading={statementLoading} onClick={() => void reloadStatements(statementMonth)}>刷新本月</Button>
                      <Button icon={<FileDoneOutlined />} onClick={() => setActiveTab('monthly')}>进入月结中心</Button>
                      <Button icon={<DownloadOutlined />} disabled={filteredStoreProfitRows.length === 0} onClick={exportStoreProfits}>导出门店分润</Button>
                    </Space>
                  </div>
                  {storeProfits.length === 0 && (
                    <Alert
                      className="settlement-lock-alert"
                      type="info"
                      showIcon
                      message={`${statementMonth} 尚未生成门店月结单`}
                      description="未生成月结的数据不会作为实际分润展示。请先进入月结中心生成草稿并完成对账。"
                      action={<Button size="small" type="primary" onClick={() => setActiveTab('monthly')}>去生成月结</Button>}
                    />
                  )}
                </div>

                <Row gutter={[12, 12]}>
                  <Col span={4}><SettlementMetric icon={<WalletOutlined />} tone="green" label="实际核销/实收基数" value={money(storeProfitTotals.base)} detail={`${storeProfits.length} 个门店已生成月结`} /></Col>
                  <Col span={4}><SettlementMetric icon={<ShopOutlined />} tone="blue" label="门店运营分润" value={money(storeProfitTotals.operation)} detail="按门店规则计算" /></Col>
                  <Col span={4}><SettlementMetric icon={<SafetyCertificateOutlined />} tone="violet" label="门店维修分润" value={money(storeProfitTotals.maintenance)} detail="用于门店日常维修" /></Col>
                  <Col span={4}><SettlementMetric icon={<DollarOutlined />} tone="orange" label="实收签单费" value={money(storeProfitTotals.signFee)} detail="签单费全额归门店" /></Col>
                  <Col span={4}><SettlementMetric icon={<FileDoneOutlined />} tone="green" label="门店实际应结算" value={money(storeProfitTotals.payable)} detail="已包含调整与扣减" /></Col>
                  <Col span={4}><SettlementMetric icon={<WarningOutlined />} tone="orange" label="应付电池公司" value={money(storeProfitTotals.batteryCost)} detail="不计入门店收益和平台收益" /></Col>
                </Row>

                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>各门店实际收益</Typography.Title>
                      <Typography.Text type="secondary">门店运营、门店维修和签单费分别展示，最终应结算金额以月结单为准。</Typography.Text>
                    </div>
                    <Space>
                      <Tag color="green">已生成 {storeProfits.length}</Tag>
                      <Tag color={Math.max(stores.length - storeProfits.length, 0) > 0 ? 'orange' : 'default'}>未生成 {Math.max(stores.length - storeProfits.length, 0)}</Tag>
                    </Space>
                  </div>
                  <div className="settlement-filter-bar">
                    <Input
                      allowClear
                      prefix={<FileSearchOutlined />}
                      placeholder="搜索商户、门店、编码或月结单号"
                      value={storeProfitKeyword}
                      onChange={(event) => setStoreProfitKeyword(event.target.value)}
                      style={{ width: 310 }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="所属商户"
                      value={storeProfitMerchantFilter}
                      onChange={(value) => {
                        setStoreProfitMerchantFilter(value);
                        if (storeProfitStoreFilter && stores.find((store) => store.id === storeProfitStoreFilter)?.merchantId !== value) {
                          setStoreProfitStoreFilter(undefined);
                        }
                      }}
                      options={merchants.map((merchant) => ({ label: `${merchant.merchantName} / ${merchant.merchantCode}`, value: merchant.id }))}
                      style={{ width: 230 }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="所属门店"
                      value={storeProfitStoreFilter}
                      onChange={setStoreProfitStoreFilter}
                      options={storeProfitStoreOptions}
                      style={{ width: 230 }}
                    />
                    <Select
                      allowClear
                      placeholder="结算状态"
                      value={storeProfitStatusFilter}
                      onChange={setStoreProfitStatusFilter}
                      options={[
                        { label: '尚未生成', value: 'NOT_GENERATED' },
                        ...(['DRAFT', 'RECONCILING', 'CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED'] as SettlementStatement['status'][]).map((status) => ({ label: statementStatusText(status), value: status }))
                      ]}
                      style={{ width: 150 }}
                    />
                    <Button onClick={() => {
                      setStoreProfitKeyword('');
                      setStoreProfitMerchantFilter(undefined);
                      setStoreProfitStoreFilter(undefined);
                      setStoreProfitStatusFilter(undefined);
                    }}>重置</Button>
                  </div>
                  <Table
                    rowKey={(record) => `${record.statementMonth}-${record.storeId}`}
                    size="small"
                    loading={loading || statementLoading}
                    dataSource={filteredStoreProfitRows}
                    pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 个门店` }}
                    scroll={{ x: 1740 }}
                    columns={[
                      {
                        title: '商户 / 门店',
                        fixed: 'left',
                        width: 245,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{storeMap.get(record.storeId)?.storeName || `门店 #${record.storeId}`}</Typography.Text>
                            <Typography.Text type="secondary">
                              {merchantMap.get(record.merchantId)?.merchantName || `商户 #${record.merchantId}`} · {storeMap.get(record.storeId)?.storeCode || '-'}
                            </Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '月结状态',
                        width: 125,
                        render: (_, record) => storeProfitStatusTag(record.status)
                      },
                      {
                        title: '实际核销/实收',
                        dataIndex: 'settlementBaseAmount',
                        width: 145,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{money(value)}</Typography.Text>
                            <Typography.Text type="secondary">{record.orderCount} 笔订单 / {record.billCount} 张账单</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '签单费', dataIndex: 'signFeeAmount', width: 120, render: money },
                      { title: '门店运营', dataIndex: 'storeOperationAmount', width: 125, render: (value) => <Typography.Text className="amount-positive">{money(value)}</Typography.Text> },
                      { title: '门店维修', dataIndex: 'storeMaintenanceAmount', width: 125, render: (value) => <Typography.Text className="amount-positive">{money(value)}</Typography.Text> },
                      { title: '应付电池公司', dataIndex: 'batteryCostAmount', width: 145, render: (value) => <Typography.Text type="warning" strong>{money(value)}</Typography.Text> },
                      {
                        title: '调整与扣减',
                        width: 170,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <span>补回 {money(record.maintenanceReimburseAmount)}</span>
                            <Typography.Text type={record.maintenanceDeductAmount > 0 ? 'danger' : 'secondary'}>
                              扣减 {money(record.maintenanceDeductAmount)} / 调整 {signedMoney(record.adjustmentAmount)}
                            </Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '实际应结算',
                        dataIndex: 'payableAmount',
                        width: 145,
                        render: (value, record) => record.status === 'NOT_GENERATED'
                          ? <Typography.Text type="secondary">待生成</Typography.Text>
                          : <Typography.Text strong className="settlement-payable">{money(value)}</Typography.Text>
                      },
                      {
                        title: '实际分润率',
                        width: 120,
                        render: (_, record) => record.settlementBaseAmount > 0
                          ? `${((Number(record.payableAmount || 0) / Number(record.settlementBaseAmount)) * 100).toFixed(2)}%`
                          : '-'
                      },
                      {
                        title: '打款情况',
                        width: 150,
                        render: (_, record) => record.status === 'PAID' || record.status === 'CLOSED'
                          ? <Typography.Text className="amount-positive">已打款 {money(record.payableAmount)}</Typography.Text>
                          : record.status === 'NOT_GENERATED'
                            ? <Typography.Text type="secondary">尚未进入月结</Typography.Text>
                            : <Typography.Text type="warning">待结算 {money(record.payableAmount)}</Typography.Text>
                      },
                      {
                        title: '月结单',
                        width: 180,
                        render: (_, record) => record.statementNo ? (
                          <Space direction="vertical" size={0}>
                            <Typography.Text copyable>{record.statementNo}</Typography.Text>
                            <Typography.Text type="secondary">{record.generatedAt ? formatDateTime(record.generatedAt) : '-'}</Typography.Text>
                          </Space>
                        ) : <Typography.Text type="secondary">尚未生成</Typography.Text>
                      },
                      {
                        title: '操作',
                        fixed: 'right',
                        width: 125,
                        render: (_, record) => record.statementId ? (
                          <Button
                            size="small"
                            icon={<EyeOutlined />}
                            loading={actionLoading === `statement-detail-${record.statementId}`}
                            onClick={() => void openStoreProfit(record)}
                          >查看明细</Button>
                        ) : <Button size="small" onClick={() => setActiveTab('monthly')}>去生成</Button>
                      }
                    ]}
                  />
                </div>
              </Space>
            )
          },
          {
            key: 'monthly',
            label: <span>月结中心 <span className="tab-count">{statements.length}</span></span>,
            children: (
              <Space direction="vertical" size={16} className="page-stack settlement-tab-content">
                <div className="section">
                  <div className="section-head settlement-action-head">
                    <div>
                      <Typography.Title level={4}>{statementMonth} 月结工作台</Typography.Title>
                      <Typography.Text type="secondary">月结以实际核销及实收数据为基础，已确认的月份会自动锁定。</Typography.Text>
                    </div>
                    <Space wrap>
                      <DatePicker
                        picker="month"
                        allowClear={false}
                        format="YYYY年MM月"
                        value={dayjs(`${statementMonth}-01`)}
                        onChange={handleStatementMonthChange}
                      />
                      <Button icon={<ReloadOutlined />} loading={statementLoading} onClick={() => void reloadStatements(statementMonth)}>刷新所选月份</Button>
                      <Button
                        type="primary"
                        icon={<FileDoneOutlined />}
                        loading={actionLoading === 'statement-generate'}
                        onClick={openStatementGenerate}
                      >
                        选择月份生成
                      </Button>
                      <Button icon={<DownloadOutlined />} disabled={filteredStatements.length === 0} onClick={exportStatements}>导出月结</Button>
                    </Space>
                  </div>
                  {statementLocked && (
                    <Alert
                      className="settlement-lock-alert"
                      type="warning"
                      showIcon
                      message="本月月结已锁定"
                      description="存在已确认、待打款、已打款或已关闭月结单。历史月结不会被重新生成覆盖。"
                    />
                  )}
                </div>

                <Row gutter={[12, 12]}>
                  <Col span={4}><SettlementMetric icon={<WalletOutlined />} tone="green" label="实际核销/实收基数" value={money(overview?.totalPaidRentAmount || 0)} detail={`签单费 ${money(overview?.totalSignFeeAmount || 0)}`} /></Col>
                  <Col span={4}><SettlementMetric icon={<ShopOutlined />} tone="blue" label="门店待结算" value={money(overview?.totalMerchantPayableAmount || 0)} detail={`${overview?.merchantStatementCount || 0} 张月结单`} /></Col>
                  <Col span={4}><SettlementMetric icon={<TeamOutlined />} tone="violet" label="出资方待结算" value={money(overview?.totalInvestorPayableAmount || 0)} detail={`${overview?.investorStatementCount || 0} 张月结单`} /></Col>
                  <Col span={4}><SettlementMetric icon={<WarningOutlined />} tone="orange" label="应付电池公司" value={money(overview?.totalBatteryCostAmount || 0)} detail="门店独立支付，不计入收益" /></Col>
                  <Col span={4}><SettlementMetric icon={<WarningOutlined />} tone="red" label="逾期未收" value={money(overview?.totalOpenOverdueAmount || 0)} detail={`维保调整 ${money(overview?.totalMaintenanceDeductAmount || 0)}`} /></Col>
                  <Col span={4}><SettlementMetric icon={<CheckCircleOutlined />} tone="green" label="结算完成度" value={`${statementFinishedCount}/${statements.length}`} detail={`已完成 ${statementProgress}%`} /></Col>
                </Row>

                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>月结单队列</Typography.Title>
                      <Typography.Text type="secondary">按草稿、对账、确认、待打款、已打款的顺序推进。</Typography.Text>
                    </div>
                    <div className="settlement-progress">
                      <Progress percent={statementProgress} size="small" status={statementProgress === 100 ? 'success' : 'active'} />
                    </div>
                  </div>
                  <div className="statement-status-strip">
                    {(['DRAFT', 'RECONCILING', 'CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED'] as SettlementStatement['status'][]).map((status) => (
                      <Tag key={status} color={statementStatusColor(status)}>{statementStatusText(status)} {statementStatusCounts[status]}</Tag>
                    ))}
                  </div>
                  <div className="settlement-filter-bar">
                    <Input
                      allowClear
                      prefix={<FileSearchOutlined />}
                      placeholder="搜索月结单号、门店、商户或出资方"
                      value={statementKeyword}
                      onChange={(event) => setStatementKeyword(event.target.value)}
                      style={{ width: 300 }}
                    />
                    <Select
                      allowClear
                      placeholder="结算对象"
                      value={statementBeneficiaryFilter}
                      onChange={setStatementBeneficiaryFilter}
                      options={[{ label: '门店/商户', value: 'MERCHANT' }, { label: '出资方', value: 'INVESTOR' }]}
                      style={{ width: 140 }}
                    />
                    <Select
                      allowClear
                      placeholder="月结状态"
                      value={statementStatusFilter}
                      onChange={setStatementStatusFilter}
                      options={(['DRAFT', 'RECONCILING', 'CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED'] as SettlementStatement['status'][]).map((status) => ({ label: statementStatusText(status), value: status }))}
                      style={{ width: 140 }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="所属商户"
                      value={statementMerchantFilter}
                      onChange={(value) => {
                        setStatementMerchantFilter(value);
                        if (statementStoreFilter && stores.find((store) => store.id === statementStoreFilter)?.merchantId !== value) {
                          setStatementStoreFilter(undefined);
                        }
                      }}
                      options={merchants.map((merchant) => ({ label: `${merchant.merchantName} / ${merchant.merchantCode}`, value: merchant.id }))}
                      style={{ width: 220 }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="所属门店"
                      value={statementStoreFilter}
                      onChange={setStatementStoreFilter}
                      options={statementStoreOptions}
                      style={{ width: 220 }}
                    />
                    <Button onClick={() => {
                      setStatementKeyword('');
                      setStatementBeneficiaryFilter(undefined);
                      setStatementStatusFilter(undefined);
                      setStatementMerchantFilter(undefined);
                      setStatementStoreFilter(undefined);
                    }}>重置</Button>
                  </div>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={loading || statementLoading}
                    dataSource={filteredStatements}
                    pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 张月结单` }}
                    scroll={{ x: 1550 }}
                    columns={[
                      {
                        title: '月结单',
                        dataIndex: 'statementNo',
                        fixed: 'left',
                        width: 190,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong copyable>{value}</Typography.Text>
                            <Typography.Text type="secondary">{record.statementMonth} · {record.lineCount} 条明细</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '结算对象',
                        width: 220,
                        render: (_, record) => (
                          <Space direction="vertical" size={2}>
                            <Space size={6}>
                              <Tag color={record.beneficiaryType === 'MERCHANT' ? 'blue' : 'purple'}>{statementBeneficiaryText(record.beneficiaryType)}</Tag>
                              <Typography.Text strong>{statementBeneficiaryName(record)}</Typography.Text>
                            </Space>
                            <Typography.Text type="secondary">{statementBeneficiaryCode(record)}</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '归属',
                        width: 210,
                        render: (_, record) => record.beneficiaryType === 'MERCHANT' ? (
                          <Space direction="vertical" size={0}>
                            <span>{merchantMap.get(record.merchantId)?.merchantName || '-'}</span>
                            <Typography.Text type="secondary">{storeMap.get(record.storeId)?.storeName || '-'}</Typography.Text>
                          </Space>
                        ) : <Typography.Text type="secondary">出资方独立结算</Typography.Text>
                      },
                      {
                        title: '业务量',
                        width: 145,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <span>{record.orderCount} 笔订单</span>
                            <Typography.Text type="secondary">{record.billCount} 张账单</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '收益构成',
                        width: 220,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            {record.signFeeIncomeAmount > 0 && <span>签单费 {money(record.signFeeIncomeAmount)}</span>}
                            <span>分润收益 {money(record.rentShareIncomeAmount)}</span>
                            {record.adjustmentAmount !== 0 && <Typography.Text type="secondary">调整 {signedMoney(record.adjustmentAmount)}</Typography.Text>}
                          </Space>
                        )
                      },
                      {
                        title: '扣减/费用',
                        width: 170,
                        render: (_, record) => Number(record.operationFeeAmount || 0) === 0 && Number(record.batteryCostAmount || 0) === 0 && Number(record.maintenanceDeductAmount || 0) === 0
                          ? <Typography.Text type="secondary">无</Typography.Text>
                          : (
                            <Space direction="vertical" size={0}>
                              {record.operationFeeAmount !== 0 && <span>运营费 {money(record.operationFeeAmount)}</span>}
                              {record.batteryCostAmount !== 0 && <Typography.Text type="warning">电池费 {money(record.batteryCostAmount)}</Typography.Text>}
                              {record.maintenanceDeductAmount !== 0 && <Typography.Text type="danger">维保 {signedMoney(record.maintenanceDeductAmount)}</Typography.Text>}
                            </Space>
                          )
                      },
                      { title: '应结算', dataIndex: 'payableAmount', width: 135, render: (value) => <Typography.Text strong className="settlement-payable">{money(value)}</Typography.Text> },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 115,
                        render: statementStatusTag
                      },
                      {
                        title: '关键时间',
                        width: 175,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <span>生成 {formatDateTime(record.generatedAt)}</span>
                            {record.confirmedAt && <Typography.Text type="secondary">确认 {formatDateTime(record.confirmedAt)}</Typography.Text>}
                            {record.paidAt && <Typography.Text type="secondary">打款 {formatDateTime(record.paidAt)}</Typography.Text>}
                          </Space>
                        )
                      },
                      {
                        title: '操作',
                        fixed: 'right',
                        width: 225,
                        render: (_, record) => {
                          const nextAction = nextStatementAction(record.status);
                          return (
                            <Space>
                              <Button
                                size="small"
                                icon={<EyeOutlined />}
                                loading={actionLoading === `statement-detail-${record.id}`}
                                onClick={() => openStatement(record)}
                              >明细</Button>
                              {nextAction && (
                                <Popconfirm title={`确认将月结单更新为“${nextAction.label}”？`} onConfirm={() => updateStatementStatus(record, nextAction.status)}>
                                  <Button
                                    size="small"
                                    type={nextAction.status === 'PAID' ? 'primary' : 'default'}
                                    icon={<ArrowRightOutlined />}
                                    loading={actionLoading === `statement-${record.id}`}
                                  >{nextAction.label}</Button>
                                </Popconfirm>
                              )}
                            </Space>
                          );
                        }
                      }
                    ]}
                  />
                </div>
              </Space>
            )
          },
          {
            key: 'snapshots',
            label: <span>分润快照 <span className="tab-count">{snapshots.length}</span></span>,
            children: (
              <Space direction="vertical" size={16} className="page-stack settlement-tab-content">
                <Row gutter={[12, 12]}>
                  <Col span={4}><SettlementMetric icon={<FileSearchOutlined />} tone="blue" label="快照数量" value={filteredSnapshots.length} detail="当前筛选结果" /></Col>
                  <Col span={4}><SettlementMetric icon={<WalletOutlined />} tone="green" label="结算基数" value={money(snapshotTotals.base)} detail="实际核销金额汇总" /></Col>
                  <Col span={4}><SettlementMetric icon={<DollarOutlined />} tone="orange" label="平台扣点" value={money(snapshotTotals.platform)} detail="租赁平台收入" /></Col>
                  <Col span={4}><SettlementMetric icon={<ShopOutlined />} tone="blue" label="门店合计" value={money(snapshotTotals.store)} detail="运营分润 + 维修分润" /></Col>
                  <Col span={4}><SettlementMetric icon={<SafetyCertificateOutlined />} tone="violet" label="渠道合计" value={money(snapshotTotals.channel)} detail="核销扣点 + 引流分润" /></Col>
                  <Col span={4}><SettlementMetric icon={<TeamOutlined />} tone="green" label="出资方合计" value={money(snapshotTotals.investor)} detail="按订单独立归属" /></Col>
                </Row>
                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>快照追溯</Typography.Title>
                      <Typography.Text type="secondary">快照锁定当时命中的门店规则与计算结果，后续修改规则不改写历史。</Typography.Text>
                    </div>
                    <Button icon={<DownloadOutlined />} disabled={filteredSnapshots.length === 0} onClick={exportSnapshots}>导出快照</Button>
                  </div>
                  <div className="settlement-filter-bar">
                    <Input
                      allowClear
                      prefix={<FileSearchOutlined />}
                      placeholder="搜索快照号、订单号、门店、SKU 或规则"
                      value={snapshotKeyword}
                      onChange={(event) => setSnapshotKeyword(event.target.value)}
                      style={{ width: 320 }}
                    />
                    <Select
                      allowClear
                      placeholder="业务来源"
                      value={snapshotSourceFilter}
                      onChange={setSnapshotSourceFilter}
                      options={[
                        { label: '正式订单', value: 'ORDER' },
                        { label: '补录订单', value: 'EXTERNAL_ORDER' },
                        { label: '人工测算', value: 'PREVIEW' }
                      ]}
                      style={{ width: 145 }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="所属门店"
                      value={snapshotStoreFilter}
                      onChange={setSnapshotStoreFilter}
                      options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                      style={{ width: 220 }}
                    />
                    <Select allowClear placeholder="来源渠道" value={snapshotChannelFilter} onChange={setSnapshotChannelFilter} options={ruleChannelOptions} style={{ width: 145 }} />
                    <Select
                      allowClear
                      placeholder="计算版本"
                      value={snapshotVersionFilter}
                      onChange={setSnapshotVersionFilter}
                      options={[{ label: '当前分润', value: 'PROFIT_V2' }, { label: '历史规则', value: 'LEGACY_V1' }]}
                      style={{ width: 145 }}
                    />
                    <Button onClick={() => {
                      setSnapshotKeyword('');
                      setSnapshotSourceFilter(undefined);
                      setSnapshotStoreFilter(undefined);
                      setSnapshotChannelFilter(undefined);
                      setSnapshotVersionFilter(undefined);
                    }}>重置</Button>
                  </div>
                  <Table
                    rowKey={(record) => record.id || record.snapshotNo}
                    size="small"
                    loading={loading}
                    dataSource={filteredSnapshots}
                    pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条快照` }}
                    scroll={{ x: 1690 }}
                    columns={[
                      {
                        title: '快照',
                        dataIndex: 'snapshotNo',
                        fixed: 'left',
                        width: 190,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong copyable>{value}</Typography.Text>
                            <Typography.Text type="secondary">{record.createdAt ? formatDateTime(record.createdAt) : '未保存测算'}</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '业务来源',
                        width: 190,
                        render: (_, record) => (
                          <Space direction="vertical" size={2}>
                            <Tag color={snapshotSourceColor(record.sourceType)}>{snapshotSourceTypeText(record.sourceType)}</Tag>
                            <Typography.Text>{snapshotSourceName(record)}</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '门店 / SKU',
                        width: 230,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{storeMap.get(record.storeId)?.storeName || `门店 #${record.storeId}`}</Typography.Text>
                            <Typography.Text type="secondary">{storeSkuMap.get(record.storeSkuId)?.displayName || `门店商品 #${record.storeSkuId}`}</Typography.Text>
                          </Space>
                        )
                      },
                      {
                        title: '命中规则',
                        width: 230,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{ruleMap.get(record.matchedRuleId)?.ruleName || `规则 #${record.matchedRuleId}`}</Typography.Text>
                            <Typography.Text type="secondary">{ruleScopeText(record.matchedRuleScope)} · {channelText(record.sourceChannel)}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '结算基数', dataIndex: 'settlementBaseAmount', width: 125, render: (value) => <Typography.Text strong>{money(value)}</Typography.Text> },
                      {
                        title: '渠道',
                        width: 150,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <span>核销 {money(record.channelFeeAmount)}</span>
                            <Typography.Text type="secondary">引流 {money(record.channelReferralAmount)}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '平台', dataIndex: 'platformFeeAmount', width: 110, render: money },
                      { title: '电池成本', dataIndex: 'batteryCostAmount', width: 115, render: money },
                      {
                        title: '门店',
                        width: 155,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <span>运营 {money(record.storeOperationAmount)}</span>
                            <Typography.Text type="secondary">维修 {money(record.maintenanceFundAmount)}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '出资方', dataIndex: 'investorShareAmount', width: 115, render: money },
                      { title: '版本', dataIndex: 'calculationVersion', width: 110, render: (value) => <Tag color={value === 'PROFIT_V2' ? 'green' : 'default'}>{calculationVersionText(value)}</Tag> },
                      {
                        title: '操作',
                        fixed: 'right',
                        width: 95,
                        render: (_, record) => <Button size="small" icon={<EyeOutlined />} onClick={() => openSnapshot(record)}>详情</Button>
                      }
                    ]}
                  />
                </div>
              </Space>
            )
          },
          {
            key: 'rules',
            label: <span>门店规则 <span className="tab-count">{rules.length}</span></span>,
            children: (
              <Space direction="vertical" size={16} className="page-stack settlement-tab-content">
                <Row gutter={[12, 12]}>
                  <Col span={8}><SettlementMetric icon={<SafetyCertificateOutlined />} tone="green" label="启用规则" value={ruleHealth.enabledRuleCount} detail={`共 ${rules.length} 条门店规则`} /></Col>
                  <Col span={8}><SettlementMetric icon={<ShopOutlined />} tone="blue" label="默认规则覆盖" value={`${ruleHealth.coveredStoreCount}/${ruleHealth.activeStoreCount}`} detail="已启用门店的全部渠道默认规则" /></Col>
                  <Col span={8}><SettlementMetric icon={<WarningOutlined />} tone={ruleHealth.uncoveredStores.length > 0 ? 'red' : 'green'} label="缺失默认规则" value={ruleHealth.uncoveredStores.length} detail={ruleHealth.uncoveredStores.length > 0 ? '需要管理员尽快补齐' : '所有启用门店均已覆盖'} /></Col>
                </Row>
                {ruleHealth.uncoveredStores.length > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    message={`${ruleHealth.uncoveredStores.length} 个启用门店缺少全部渠道默认规则`}
                    description={`请补齐：${ruleHealth.uncoveredStores.slice(0, 6).map((store) => store.storeName).join('、')}${ruleHealth.uncoveredStores.length > 6 ? '等' : ''}`}
                  />
                )}
                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>规则测算</Typography.Title>
                      <Typography.Text type="secondary">输入实际核销金额，验证指定门店、渠道和资产会命中哪条规则。</Typography.Text>
                    </div>
                  </div>
                  <Form
                    form={previewForm}
                    layout="vertical"
                    initialValues={{ sourceChannel: 'DIRECT' }}
                    onFinish={previewSettlement}
                    onValuesChange={() => setPreview(null)}
                  >
                    <Row gutter={12} align="bottom">
                      <Col span={6}>
                        <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}>
                          <Select showSearch optionFilterProp="label" placeholder="选择门店商品" options={storeSkuOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={3}>
                        <Form.Item name="sourceChannel" label="来源渠道" rules={[{ required: true, message: '请选择来源渠道' }]}>
                          <Select options={ruleChannelOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
                        <Form.Item name="frameAssetId" label="车架 / 车电一体">
                          <Select allowClear showSearch optionFilterProp="label" placeholder="可选" options={frameAssetOptions} />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
                        <Form.Item name="batteryAssetId" label="电池资产">
                          <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            disabled={integratedPreviewAssetSelected}
                            placeholder={integratedPreviewAssetSelected ? '车电一体无需选择' : '可选'}
                            options={batteryAssetOptions}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={3}>
                        <Form.Item name="rentalAmount" label="实际核销金额" rules={[{ required: true, message: '请输入实际核销金额' }]}>
                          <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
                        <Form.Item label="操作">
                          <Space>
                            <Button type="primary" htmlType="submit">开始测算</Button>
                            <Popconfirm
                              disabled={!preview}
                              title="确认保存这条人工测算快照？"
                              description="保存后会进入分润快照列表，便于后续追溯。"
                              onConfirm={createSnapshot}
                            >
                              <Button disabled={!preview} loading={actionLoading === 'snapshot-create'}>保存快照</Button>
                            </Popconfirm>
                          </Space>
                        </Form.Item>
                      </Col>
                    </Row>
                  </Form>
                  {preview && (
                    <div className="settlement-calculation-preview">
                      <Descriptions bordered size="small" column={4}>
                        <Descriptions.Item label="命中规则">{ruleMap.get(preview.matchedRuleId)?.ruleName || `规则 #${preview.matchedRuleId}`}</Descriptions.Item>
                        <Descriptions.Item label="规则范围">{ruleScopeText(preview.matchedRuleScope)}</Descriptions.Item>
                        <Descriptions.Item label="来源渠道">{channelText(preview.sourceChannel)}</Descriptions.Item>
                        <Descriptions.Item label="实际核销金额">{money(preview.settlementBaseAmount)}</Descriptions.Item>
                        <Descriptions.Item label="渠道核销扣点">{money(preview.channelFeeAmount)} / {percent(preview.channelFeeRate)}</Descriptions.Item>
                        <Descriptions.Item label="租赁平台扣点">{money(preview.platformFeeAmount)} / {percent(preview.platformFeeRate)}</Descriptions.Item>
                        <Descriptions.Item label="剩余可分配">{money(preview.distributableAmount)}</Descriptions.Item>
                        <Descriptions.Item label="门店合计">{money(Number(preview.storeOperationAmount || 0) + Number(preview.maintenanceFundAmount || 0))}</Descriptions.Item>
                      </Descriptions>
                      <SnapshotAllocation snapshot={preview} />
                    </div>
                  )}
                </div>

                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>门店分润规则</Typography.Title>
                      <Typography.Text type="secondary">每个门店至少保留一条当前生效的全部渠道默认规则。</Typography.Text>
                    </div>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openRuleCreator}>新增规则</Button>
                  </div>
                  <div className="settlement-filter-bar">
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="筛选门店"
                      value={ruleStoreFilter}
                      onChange={setRuleStoreFilter}
                      options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                      style={{ width: 240 }}
                    />
                    <Select
                      allowClear
                      placeholder="筛选渠道"
                      value={ruleChannelFilter}
                      onChange={setRuleChannelFilter}
                      options={[{ label: '全部渠道默认规则', value: 'DEFAULT' }, ...ruleChannelOptions]}
                      style={{ width: 190 }}
                    />
                    <Select
                      allowClear
                      placeholder="筛选状态"
                      value={ruleStatusFilter}
                      onChange={setRuleStatusFilter}
                      options={[{ label: '已启用', value: 'ENABLED' }, { label: '已停用', value: 'DISABLED' }]}
                      style={{ width: 140 }}
                    />
                    <Button onClick={() => {
                      setRuleStoreFilter(undefined);
                      setRuleChannelFilter(undefined);
                      setRuleStatusFilter(undefined);
                    }}>重置</Button>
                  </div>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={loading}
                    dataSource={filteredRules}
                    pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条规则` }}
                    scroll={{ x: 1900 }}
                    columns={[
                      {
                        title: '规则',
                        dataIndex: 'ruleName',
                        fixed: 'left',
                        width: 210,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{value}</Typography.Text>
                            <Typography.Text type="secondary" copyable>{record.ruleCode}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '门店', dataIndex: 'storeId', width: 180, render: (value) => storeMap.get(value)?.storeName || `门店 ${value}` },
                      { title: '门店编码', dataIndex: 'storeId', width: 150, render: (value) => storeMap.get(value)?.storeCode || '-' },
                      { title: '适用渠道', dataIndex: 'sourceChannel', width: 130, render: (value) => value ? channelText(value) : <Tag color="blue">全部渠道</Tag> },
                      { title: '优先级', dataIndex: 'priority', width: 90 },
                      { title: '渠道扣点', dataIndex: 'channelFeeRate', render: percent },
                      { title: '平台扣点', dataIndex: 'platformFeeRate', render: percent },
                      { title: '门店运营', dataIndex: 'storeOperationRate', render: percent },
                      { title: '门店维修', dataIndex: 'maintenanceFundRate', render: percent },
                      { title: '渠道引流', dataIndex: 'channelReferralRate', render: percent },
                      { title: '出资方', dataIndex: 'investorShareRate', render: percent },
                      {
                        title: '有效期',
                        dataIndex: 'effectiveAt',
                        width: 175,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <span>{formatDateTime(value)}</span>
                            <Typography.Text type="secondary">{record.expiredAt ? `至 ${formatDateTime(record.expiredAt)}` : '长期有效'}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '状态', dataIndex: 'status', width: 90, render: (value: ProfitRule['status']) => value === 'ENABLED' ? <Tag color="green">已启用</Tag> : <Tag>已停用</Tag> },
                      {
                        title: '操作',
                        fixed: 'right',
                        width: 260,
                        render: (_, record) => (
                          <Space>
                            <Button size="small" icon={<EditOutlined />} onClick={() => openRuleEditor(record)}>编辑</Button>
                            <Popconfirm title={record.status === 'ENABLED' ? '确认停用这条规则？' : '确认启用这条规则？'} onConfirm={() => updateRuleStatus(record)}>
                              <Button size="small" icon={<PoweroffOutlined />}>{record.status === 'ENABLED' ? '停用' : '启用'}</Button>
                            </Popconfirm>
                            <Popconfirm title="确认删除这条分润规则？" description="已生成分润快照或作为唯一默认规则时不能删除。" onConfirm={() => deleteRule(record)}>
                              <Button danger size="small" icon={<DeleteOutlined />}>删除</Button>
                            </Popconfirm>
                          </Space>
                        )
                      }
                    ]}
                  />
                </div>
              </Space>
            )
          },
          {
            key: 'income',
            label: <span>收益台账 <span className="tab-count">{filteredEntries.length}</span></span>,
            children: (
              <Space direction="vertical" size={16} className="page-stack settlement-tab-content">
                <Row gutter={[12, 12]}>
                  <Col span={8}><SettlementMetric icon={<ClockCircleOutlined />} tone="blue" label="待支付分润" value={money(incomeTotals.PENDING)} detail={`${incomeBusinessCount} 笔实收业务 / ${settlementPayableEntries.length} 条门店与出资方流水`} /></Col>
                  <Col span={8}><SettlementMetric icon={<WarningOutlined />} tone="red" label="已冻结分润" value={money(incomeTotals.FROZEN)} detail="仅统计门店与出资方口径" /></Col>
                  <Col span={8}><SettlementMetric icon={<CheckCircleOutlined />} tone="green" label="已结算分润" value={money(incomeTotals.SETTLED)} detail="月结打款后自动回写" /></Col>
                </Row>
                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>历史收益补生成</Typography.Title>
                      <Typography.Text type="secondary">仅补同步该订单已经支付的账单；未支付账单不会提前计入收益。</Typography.Text>
                    </div>
                    <Form form={incomeForm} layout="inline" onFinish={generateIncome}>
                      <Form.Item name="orderId" rules={[{ required: true, message: '请选择正式订单' }]}>
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="搜索订单号、客户或门店"
                          options={orders.map((order) => ({
                            label: `${order.orderNo} / ${order.customerName || '未填写客户'} / ${order.storeName || storeMap.get(order.storeId)?.storeName || '未知门店'}`,
                            value: order.id
                          }))}
                          style={{ width: 350 }}
                        />
                      </Form.Item>
                      <Button type="primary" htmlType="submit" loading={actionLoading === 'income-generate'}>补生成收益</Button>
                    </Form>
                  </div>
                </div>
                <div className="section">
                  <div className="section-head settlement-list-head">
                    <div>
                      <Typography.Title level={4}>收益流水</Typography.Title>
                      <Typography.Text type="secondary">默认展示所选月份的实收账单和补录订单；订单笔数与分润流水数分开统计。</Typography.Text>
                    </div>
                    <Button icon={<DownloadOutlined />} disabled={filteredEntries.length === 0} onClick={exportIncomeEntries}>导出收益</Button>
                  </div>
                  <div className="settlement-filter-bar">
                    <Input allowClear prefix={<FileSearchOutlined />} placeholder="搜索收益单号、业务单号、门店或收益方" value={incomeKeyword} onChange={(event) => setIncomeKeyword(event.target.value)} style={{ width: 320 }} />
                    <DatePicker picker="month" allowClear={false} value={dayjs(`${incomeMonth}-01`)} onChange={(value) => setIncomeMonth(value ? value.format('YYYY-MM') : currentMonth())} />
                    <Select allowClear showSearch optionFilterProp="label" placeholder="所属门店" value={incomeStoreFilter} onChange={setIncomeStoreFilter} options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))} style={{ width: 220 }} />
                    <Select allowClear placeholder="业务来源" value={incomeSourceFilter} onChange={setIncomeSourceFilter} options={[{ label: '实收账单', value: 'BILL' }, { label: '补录订单', value: 'EXTERNAL_ORDER' }, { label: '历史整单预计', value: 'ORDER' }]} style={{ width: 160 }} />
                    <Select
                      allowClear
                      placeholder="收益方"
                      value={incomeBeneficiaryFilter}
                      onChange={setIncomeBeneficiaryFilter}
                      options={[
                        { label: '门店/商户', value: 'MERCHANT' },
                        { label: '出资方', value: 'INVESTOR' },
                        { label: '平台', value: 'PLATFORM' },
                        { label: '渠道', value: 'CHANNEL' },
                        { label: '历史维修基金', value: 'MAINTENANCE_FUND' }
                      ]}
                      style={{ width: 160 }}
                    />
                    <Select allowClear placeholder="收益状态" value={incomeStatusFilter} onChange={setIncomeStatusFilter} options={[{ label: '待结算', value: 'PENDING' }, { label: '已结算', value: 'SETTLED' }, { label: '已冻结', value: 'FROZEN' }]} style={{ width: 140 }} />
                    <Button onClick={() => {
                      setIncomeKeyword('');
                      setIncomeMonth(currentMonth());
                      setIncomeStoreFilter(undefined);
                      setIncomeSourceFilter(undefined);
                      setIncomeBeneficiaryFilter(undefined);
                      setIncomeStatusFilter(undefined);
                    }}>重置</Button>
                  </div>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={loading}
                    dataSource={filteredEntries}
                    pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条收益` }}
                    scroll={{ x: 1420 }}
                    columns={[
                      {
                        title: '收益流水',
                        dataIndex: 'entryNo',
                        fixed: 'left',
                        width: 190,
                        render: (value, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong copyable>{value}</Typography.Text>
                            <Typography.Text type="secondary">{formatDateTime(record.occurredAt)}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '来源', dataIndex: 'sourceType', width: 130, render: incomeSourceTag },
                      { title: '业务单号', width: 170, render: (_, record) => record.sourceNo || `#${record.sourceId}` },
                      {
                        title: '门店 / 收益方',
                        width: 220,
                        render: (_, record) => (
                          <Space direction="vertical" size={0}>
                            <Typography.Text strong>{incomeBeneficiaryName(record)}</Typography.Text>
                            <Typography.Text type="secondary">{storeMap.get(record.storeId)?.storeName || '-'}</Typography.Text>
                          </Space>
                        )
                      },
                      { title: '收益类型', dataIndex: 'lineType', width: 170, render: lineTypeText },
                      { title: '金额', dataIndex: 'amount', width: 120, render: (value) => <Typography.Text strong>{money(value)}</Typography.Text> },
                      { title: '状态', dataIndex: 'entryStatus', width: 110, render: incomeStatusTag },
                      { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true, render: (value) => value || '-' },
                      { title: '结算时间', dataIndex: 'settledAt', width: 170, render: (value) => value ? formatDateTime(value) : '-' },
                      {
                        title: '操作',
                        fixed: 'right',
                        width: 180,
                        render: (_, record) => record.entryStatus === 'SETTLED' ? <Typography.Text type="secondary">已完成</Typography.Text> : (
                          <Space>
                            {record.entryStatus === 'FROZEN' ? (
                              <Button size="small" loading={actionLoading === `income-${record.id}`} onClick={() => updateEntryStatus(record, 'PENDING')}>解冻</Button>
                            ) : (
                              <Popconfirm title="确认冻结这条收益？" onConfirm={() => updateEntryStatus(record, 'FROZEN')}>
                                <Button size="small" danger loading={actionLoading === `income-${record.id}`}>冻结</Button>
                              </Popconfirm>
                            )}
                            <Popconfirm title="确认将这条收益标记为已结算？" onConfirm={() => updateEntryStatus(record, 'SETTLED')}>
                              <Button size="small" type="primary" loading={actionLoading === `income-${record.id}`}>结算</Button>
                            </Popconfirm>
                          </Space>
                        )
                      }
                    ]}
                  />
                </div>
              </Space>
            )
          }
        ]}
      />

      <Modal
        title={editingRule ? '编辑门店分润规则' : '新增门店分润规则'}
        open={ruleOpen}
        onCancel={() => {
          setRuleOpen(false);
          setEditingRule(null);
          ruleForm.resetFields();
        }}
        onOk={() => ruleForm.submit()}
        okText={editingRule ? '保存' : '新增'}
        width={820}
        styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
        destroyOnHidden
      >
        <Form form={ruleForm} layout="vertical" onFinish={saveRule}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
                <Input maxLength={128} placeholder="例如：王城大道店抖音分润规则" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="storeId" label="适用门店" rules={[{ required: true, message: '请选择门店' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择门店"
                  options={stores.map((store) => ({ label: `${store.storeName} / ${store.storeCode}`, value: store.id }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="sourceChannel" label="适用渠道">
                <Select allowClear placeholder="全部渠道" options={ruleChannelOptions} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请输入优先级' }]}>
                <InputNumber min={-10000} max={10000} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="effectiveAt" label="生效时间" rules={[{ required: true, message: '请选择生效时间' }]}>
                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="expiredAt" label="失效时间">
                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="channelFeeRate" label="渠道核销扣点 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="platformFeeRate" label="租赁平台扣点 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="storeOperationRate" label="剩余金额：门店运营 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maintenanceFundRate" label="剩余金额：门店维修 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="channelReferralRate" label="剩余金额：渠道引流 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="investorShareRate" label="剩余金额：出资方 (%)" rules={[{ required: true, message: '请输入比例' }]}>
                <InputNumber min={0} max={100} precision={2} step={1} suffix="%" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title={selectedSnapshot ? `分润快照详情 - ${selectedSnapshot.snapshotNo}` : '分润快照详情'}
        open={snapshotDetailOpen}
        onCancel={() => {
          setSnapshotDetailOpen(false);
          setSelectedSnapshot(null);
        }}
        footer={null}
        width={1080}
        destroyOnHidden
      >
        {selectedSnapshot && (
          <Space direction="vertical" size={18} className="page-stack">
            <Descriptions bordered size="small" column={3}>
              <Descriptions.Item label="业务来源">{snapshotSourceName(selectedSnapshot)}</Descriptions.Item>
              <Descriptions.Item label="来源类型"><Tag color={snapshotSourceColor(selectedSnapshot.sourceType)}>{snapshotSourceTypeText(selectedSnapshot.sourceType)}</Tag></Descriptions.Item>
              <Descriptions.Item label="来源渠道">{channelText(selectedSnapshot.sourceChannel)}</Descriptions.Item>
              <Descriptions.Item label="所属门店">{storeMap.get(selectedSnapshot.storeId)?.storeName || `门店 #${selectedSnapshot.storeId}`}</Descriptions.Item>
              <Descriptions.Item label="门店商品">{storeSkuMap.get(selectedSnapshot.storeSkuId)?.displayName || `门店商品 #${selectedSnapshot.storeSkuId}`}</Descriptions.Item>
              <Descriptions.Item label="计算版本">{calculationVersionText(selectedSnapshot.calculationVersion)}</Descriptions.Item>
              <Descriptions.Item label="命中规则">{ruleMap.get(selectedSnapshot.matchedRuleId)?.ruleName || `规则 #${selectedSnapshot.matchedRuleId}`}</Descriptions.Item>
              <Descriptions.Item label="规则编码">{ruleMap.get(selectedSnapshot.matchedRuleId)?.ruleCode || selectedSnapshot.matchedRuleId}</Descriptions.Item>
              <Descriptions.Item label="规则范围">{ruleScopeText(selectedSnapshot.matchedRuleScope)}</Descriptions.Item>
              <Descriptions.Item label="车架 / 车电一体">
                {selectedSnapshot.frameAssetId
                  ? `${assetMap.get(selectedSnapshot.frameAssetId)?.assetTypeName || '资产'} · ${assetMap.get(selectedSnapshot.frameAssetId)?.serialNo || `#${selectedSnapshot.frameAssetId}`}`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="电池资产">
                {selectedSnapshot.batteryAssetId
                  ? `${assetMap.get(selectedSnapshot.batteryAssetId)?.assetTypeName || '电池'} · ${assetMap.get(selectedSnapshot.batteryAssetId)?.serialNo || `#${selectedSnapshot.batteryAssetId}`}`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="生成时间">{selectedSnapshot.createdAt ? formatDateTime(selectedSnapshot.createdAt) : '未保存测算'}</Descriptions.Item>
            </Descriptions>

            <div>
              <Divider orientation="left">计算过程</Divider>
              <div className="snapshot-equation">
                <div><span>实际核销金额</span><strong>{money(selectedSnapshot.settlementBaseAmount)}</strong></div>
                <span className="snapshot-equation-symbol">-</span>
                <div><span>渠道核销扣点</span><strong>{money(selectedSnapshot.channelFeeAmount)}</strong></div>
                <span className="snapshot-equation-symbol">-</span>
                <div><span>租赁平台扣点</span><strong>{money(selectedSnapshot.platformFeeAmount)}</strong></div>
                <span className="snapshot-equation-symbol">-</span>
                <div><span>外卖车电池成本</span><strong>{money(selectedSnapshot.batteryCostAmount)}</strong></div>
                <span className="snapshot-equation-symbol">=</span>
                <div className="snapshot-equation-result"><span>剩余可分配</span><strong>{money(selectedSnapshot.distributableAmount)}</strong></div>
              </div>
              <SnapshotAllocation snapshot={selectedSnapshot} />
              <div className={`snapshot-balance-check ${Math.abs(snapshotBalanceDifference(selectedSnapshot)) > 0.01 ? 'warning' : ''}`}>
                {Math.abs(snapshotBalanceDifference(selectedSnapshot)) > 0.01 ? <WarningOutlined /> : <CheckCircleOutlined />}
                {Math.abs(snapshotBalanceDifference(selectedSnapshot)) > 0.01
                  ? `分配合计 ${money(snapshotAllocationTotal(selectedSnapshot))}，与实际核销金额相差 ${money(Math.abs(snapshotBalanceDifference(selectedSnapshot)))}，请复核。`
                  : `分配合计 ${money(snapshotAllocationTotal(selectedSnapshot))}，与实际核销金额一致。`}
              </div>
            </div>

            <div>
              <Divider orientation="left">锁定的规则摘要</Divider>
              <Typography.Paragraph code copyable className="snapshot-rule-summary">{selectedSnapshot.ruleSummary || '-'}</Typography.Paragraph>
            </div>
          </Space>
        )}
      </Modal>

      <Modal
        title="生成月结单"
        open={statementGenerateOpen}
        onCancel={() => setStatementGenerateOpen(false)}
        onOk={generateStatements}
        confirmLoading={actionLoading === 'statement-generate'}
        okText={statementGenerateMonth === statementMonth && statements.length > 0 ? '重新生成草稿' : '生成月结单'}
        destroyOnHidden
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Text type="secondary">请选择要生成月结单的结算月份。系统会按所选月份的实际核销、实收和补录订单数据生成月结单。</Typography.Text>
          <DatePicker
            picker="month"
            allowClear={false}
            format="YYYY年MM月"
            value={dayjs(`${statementGenerateMonth}-01`)}
            onChange={(value) => setStatementGenerateMonth(value ? value.format('YYYY-MM') : statementMonth)}
            style={{ width: '100%' }}
          />
          <Alert
            type="warning"
            showIcon
            message="重新生成会覆盖所选月份的草稿和对账中月结单，已确认、待打款或已完成的月份不能重新生成。"
          />
        </Space>
      </Modal>

      <Modal
        title={selectedStatement ? `月结单明细 - ${selectedStatement.statementNo}` : '月结单明细'}
        open={statementDetailOpen}
        onCancel={() => {
          setStatementDetailOpen(false);
          setSelectedStatement(null);
          setStatementLines([]);
          setStatementLineKeyword('');
          setStatementLineTypeFilter(undefined);
        }}
        footer={null}
        width={1180}
        destroyOnHidden
      >
        {selectedStatement && (
          <Space direction="vertical" size={16} className="page-stack">
            <Descriptions bordered size="small" column={4}>
              <Descriptions.Item label="结算月份">{selectedStatement.statementMonth}</Descriptions.Item>
              <Descriptions.Item label="结算对象">{statementBeneficiaryName(selectedStatement)}</Descriptions.Item>
              <Descriptions.Item label="对象编码">{statementBeneficiaryCode(selectedStatement)}</Descriptions.Item>
              <Descriptions.Item label="当前状态">{statementStatusTag(selectedStatement.status)}</Descriptions.Item>
              <Descriptions.Item label="所属商户">{merchantMap.get(selectedStatement.merchantId)?.merchantName || '-'}</Descriptions.Item>
              <Descriptions.Item label="所属门店">{storeMap.get(selectedStatement.storeId)?.storeName || '-'}</Descriptions.Item>
              <Descriptions.Item label="业务量">{selectedStatement.orderCount} 笔订单 / {selectedStatement.billCount} 张账单</Descriptions.Item>
              <Descriptions.Item label="明细数量">{selectedStatement.lineCount} 条</Descriptions.Item>
              <Descriptions.Item label="操作生成时间">{formatDateTime(selectedStatement.generatedAt)}</Descriptions.Item>
              <Descriptions.Item label="确认时间">{selectedStatement.confirmedAt ? formatDateTime(selectedStatement.confirmedAt) : '-'}</Descriptions.Item>
              <Descriptions.Item label="打款时间">{selectedStatement.paidAt ? formatDateTime(selectedStatement.paidAt) : '-'}</Descriptions.Item>
              <Descriptions.Item label="备注">{selectedStatement.remark || '-'}</Descriptions.Item>
            </Descriptions>

            <div className="statement-detail-summary">
              <div><span>实际核销/实收基数</span><strong>{money(selectedStatement.rentBaseAmount)}</strong></div>
              <div><span>签单费</span><strong>{money(selectedStatement.signFeeIncomeAmount)}</strong></div>
              <div><span>分润收益</span><strong>{money(selectedStatement.rentShareIncomeAmount)}</strong></div>
              <div><span>门店应付电池公司</span><strong>{money(selectedStatement.batteryCostAmount)}</strong></div>
              <div><span>调整与扣减</span><strong>{signedMoney(Number(selectedStatement.adjustmentAmount || 0) + Number(selectedStatement.maintenanceDeductAmount || 0) - Number(selectedStatement.operationFeeAmount || 0))}</strong></div>
              <div className="statement-detail-payable"><span>应结算金额</span><strong>{money(selectedStatement.payableAmount)}</strong></div>
            </div>

            <div className="statement-detail-actions">
              <div className="statement-status-strip">
                {(['DRAFT', 'RECONCILING', 'CONFIRMED', 'PAYABLE', 'PAID', 'CLOSED'] as SettlementStatement['status'][]).map((status) => (
                  <Tag key={status} color={status === selectedStatement.status ? statementStatusColor(status) : 'default'}>
                    {statementStatusText(status)}
                  </Tag>
                ))}
              </div>
              <Space>
                <Button icon={<DownloadOutlined />} onClick={exportStatementLines}>导出明细</Button>
                {nextStatementAction(selectedStatement.status) && (
                  <Popconfirm
                    title={`确认将月结单更新为“${nextStatementAction(selectedStatement.status)?.label}”？`}
                    onConfirm={() => {
                      const next = nextStatementAction(selectedStatement.status);
                      if (next) {
                        void updateStatementStatus(selectedStatement, next.status);
                      }
                    }}
                  >
                    <Button type="primary" icon={<ArrowRightOutlined />} loading={actionLoading === `statement-${selectedStatement.id}`}>
                      {nextStatementAction(selectedStatement.status)?.label}
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            </div>

            <div className="settlement-filter-bar statement-line-filter">
              <Input
                allowClear
                prefix={<FileSearchOutlined />}
                placeholder="搜索明细号、订单、资产、门店或备注"
                value={statementLineKeyword}
                onChange={(event) => setStatementLineKeyword(event.target.value)}
                style={{ width: 340 }}
              />
              <Select
                allowClear
                placeholder="明细类型"
                value={statementLineTypeFilter}
                onChange={setStatementLineTypeFilter}
                options={(Object.keys(statementLineTotals) as SettlementStatementLine['lineType'][]).map((type) => ({
                  label: `${statementLineText(type)} / ${signedMoney(statementLineTotals[type] || 0)}`,
                  value: type
                }))}
                style={{ width: 250 }}
              />
              <Typography.Text type="secondary">当前显示 {filteredStatementLines.length} / {statementLines.length} 条</Typography.Text>
            </div>

            <Table
              rowKey="id"
              size="small"
              dataSource={filteredStatementLines}
              pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条明细` }}
              scroll={{ x: 1250 }}
              columns={[
                {
                  title: '明细',
                  dataIndex: 'lineNo',
                  fixed: 'left',
                  width: 180,
                  render: (value, record) => (
                    <Space direction="vertical" size={0}>
                      <Typography.Text strong copyable>{value}</Typography.Text>
                      <Typography.Text type="secondary">{record.occurredAt ? formatDateTime(record.occurredAt) : '-'}</Typography.Text>
                    </Space>
                  )
                },
                { title: '类型', dataIndex: 'lineType', width: 180, render: statementLineText },
                {
                  title: '业务来源',
                  width: 210,
                  render: (_, line) => (
                    <Space direction="vertical" size={0}>
                      <Tag color={line.sourceType === 'EXTERNAL_ORDER' ? 'purple' : line.sourceType === 'MAINTENANCE' ? 'orange' : 'blue'}>{statementLineSourceTypeText(line.sourceType)}</Tag>
                      <Typography.Text>{statementLineSourceText(line, externalOrderMap)}</Typography.Text>
                    </Space>
                  )
                },
                {
                  title: '订单 / 账单',
                  width: 190,
                  render: (_, line) => (
                    <Space direction="vertical" size={0}>
                      <span>{line.orderId ? orderMap.get(line.orderId)?.orderNo || `订单 #${line.orderId}` : '-'}</span>
                      <Typography.Text type="secondary">{line.billId ? `账单 #${line.billId}` : '-'}</Typography.Text>
                    </Space>
                  )
                },
                {
                  title: '资产 / 门店',
                  width: 220,
                  render: (_, line) => (
                    <Space direction="vertical" size={0}>
                      <span>{line.assetId ? assetMap.get(line.assetId)?.serialNo || `资产 #${line.assetId}` : '-'}</span>
                      <Typography.Text type="secondary">{storeMap.get(line.storeId)?.storeName || '-'}</Typography.Text>
                    </Space>
                  )
                },
                { title: '金额', dataIndex: 'amount', width: 125, render: (value) => <Typography.Text strong className={Number(value) < 0 ? 'amount-negative' : 'amount-positive'}>{signedMoney(value)}</Typography.Text> },
                { title: '备注', dataIndex: 'remark', width: 260, ellipsis: true, render: (value) => value || '-' }
              ]}
            />
          </Space>
        )}
      </Modal>
    </Space>
  );
}

type SettlementMetricTone = 'green' | 'blue' | 'orange' | 'red' | 'violet';

function SettlementMetric({
  icon,
  tone,
  label,
  value,
  detail
}: {
  icon: ReactNode;
  tone: SettlementMetricTone;
  label: string;
  value: string | number;
  detail: string;
}) {
  return (
    <div className="metric-tile settlement-metric">
      <div className="metric-head">
        <span className={`metric-icon ${tone}`}>{icon}</span>
        <span>{label}</span>
      </div>
      <Statistic value={value} />
      <Typography.Text type="secondary">{detail}</Typography.Text>
    </div>
  );
}

function SnapshotAllocation({ snapshot }: { snapshot: SettlementSnapshot }) {
  const segments = [
    {
      key: 'store-operation',
      label: '门店运营',
      rate: snapshot.storeOperationRate,
      amount: snapshot.storeOperationAmount,
      className: 'store-operation'
    },
    {
      key: 'maintenance',
      label: '门店维修',
      rate: snapshot.maintenanceFundRate,
      amount: snapshot.maintenanceFundAmount,
      className: 'maintenance'
    },
    {
      key: 'channel',
      label: '渠道引流',
      rate: snapshot.channelReferralRate,
      amount: snapshot.channelReferralAmount,
      className: 'channel'
    },
    {
      key: 'investor',
      label: '出资方',
      rate: snapshot.investorShareRate,
      amount: snapshot.investorShareAmount,
      className: 'investor'
    }
  ];
  const storeAmount = Number(snapshot.storeOperationAmount || 0) + Number(snapshot.maintenanceFundAmount || 0);

  return (
    <div className="snapshot-allocation">
      <div className="snapshot-allocation-head">
        <div>
          <Typography.Text strong>剩余金额分配</Typography.Text>
          <Typography.Text type="secondary">{money(snapshot.distributableAmount)}</Typography.Text>
        </div>
        <Typography.Text type="secondary">门店合计 {money(storeAmount)}</Typography.Text>
      </div>
      <div className="snapshot-allocation-bar">
        {segments.map((segment) => (
          <Tooltip key={segment.key} title={`${segment.label} ${percent(segment.rate)} / ${money(segment.amount)}`}>
            <div
              className={`snapshot-allocation-segment ${segment.className}`}
              style={{ width: `${Math.max(Number(segment.rate || 0) * 100, 0)}%` }}
            />
          </Tooltip>
        ))}
      </div>
      <div className="snapshot-allocation-legend">
        {segments.map((segment) => (
          <div key={segment.key}>
            <span className={`snapshot-allocation-swatch ${segment.className}`} />
            <span>{segment.label}</span>
            <strong>{money(segment.amount)}</strong>
            <Typography.Text type="secondary">{percent(segment.rate)}</Typography.Text>
          </div>
        ))}
      </div>
    </div>
  );
}

function snapshotAllocationTotal(snapshot: SettlementSnapshot) {
  return Number(snapshot.channelFeeAmount || 0)
    + Number(snapshot.platformFeeAmount || 0)
    + Number(snapshot.batteryCostAmount || 0)
    + Number(snapshot.storeOperationAmount || 0)
    + Number(snapshot.maintenanceFundAmount || 0)
    + Number(snapshot.channelReferralAmount || 0)
    + Number(snapshot.investorShareAmount || 0);
}

function snapshotBalanceDifference(snapshot: SettlementSnapshot) {
  return snapshotAllocationTotal(snapshot) - Number(snapshot.settlementBaseAmount || 0);
}

function statementStatusText(value: SettlementStatement['status']) {
  const map: Record<SettlementStatement['status'], string> = {
    DRAFT: '草稿',
    RECONCILING: '对账中',
    CONFIRMED: '已确认',
    PAYABLE: '待打款',
    PAID: '已打款',
    CLOSED: '已关闭'
  };
  return map[value];
}

function storeProfitStatusText(value: StoreProfitStatus) {
  return value === 'NOT_GENERATED' ? '尚未生成' : statementStatusText(value);
}

function storeProfitStatusTag(value: StoreProfitStatus) {
  if (value === 'NOT_GENERATED') {
    return <Tag>尚未生成</Tag>;
  }
  return statementStatusTag(value);
}

function statementStatusColor(value: SettlementStatement['status']) {
  const map: Record<SettlementStatement['status'], string> = {
    DRAFT: 'default',
    RECONCILING: 'processing',
    CONFIRMED: 'blue',
    PAYABLE: 'gold',
    PAID: 'green',
    CLOSED: 'red'
  };
  return map[value];
}

function nextStatementAction(status: SettlementStatement['status']): {
  status: SettlementStatement['status'];
  label: string;
} | null {
  const map: Partial<Record<SettlementStatement['status'], { status: SettlementStatement['status']; label: string }>> = {
    DRAFT: { status: 'RECONCILING', label: '开始对账' },
    RECONCILING: { status: 'CONFIRMED', label: '确认结算' },
    CONFIRMED: { status: 'PAYABLE', label: '进入待打款' },
    PAYABLE: { status: 'PAID', label: '标记已打款' },
    PAID: { status: 'CLOSED', label: '关闭月结单' }
  };
  return map[status] || null;
}

function snapshotSourceTypeText(value: SettlementSnapshot['sourceType']) {
  const map: Record<SettlementSnapshot['sourceType'], string> = {
    PREVIEW: '人工测算',
    ORDER: '正式订单',
    EXTERNAL_ORDER: '补录订单'
  };
  return map[value];
}

function snapshotSourceColor(value: SettlementSnapshot['sourceType']) {
  const map: Record<SettlementSnapshot['sourceType'], string> = {
    PREVIEW: 'default',
    ORDER: 'blue',
    EXTERNAL_ORDER: 'purple'
  };
  return map[value];
}

function ruleScopeText(value: SettlementSnapshot['matchedRuleScope'] | ProfitRule['ruleScope']) {
  const map: Record<ProfitRule['ruleScope'], string> = {
    PLATFORM: '平台默认',
    SKU: 'SKU',
    STORE: '门店',
    STORE_SKU: '门店商品'
  };
  return map[value];
}

function calculationVersionText(value: SettlementSnapshot['calculationVersion']) {
  return value === 'PROFIT_V2' ? '当前分润' : '历史规则';
}

function incomeStatusText(value: SettlementIncomeEntry['entryStatus']) {
  const map: Record<SettlementIncomeEntry['entryStatus'], string> = {
    PENDING: '待结算',
    SETTLED: '已结算',
    FROZEN: '已冻结'
  };
  return map[value];
}

function incomeSourceText(value: SettlementIncomeEntry['sourceType']) {
  const map: Record<SettlementIncomeEntry['sourceType'], string> = {
    BILL: '实收账单',
    EXTERNAL_ORDER: '补录订单',
    ORDER: '历史整单预计'
  };
  return map[value];
}

function incomeSourceTag(value: SettlementIncomeEntry['sourceType']) {
  const color = value === 'BILL' ? 'green' : value === 'EXTERNAL_ORDER' ? 'purple' : 'default';
  return <Tag color={color}>{incomeSourceText(value)}</Tag>;
}

function statementLineSourceTypeText(value: string) {
  const map: Record<string, string> = {
    BILL: '正式订单账单',
    EXTERNAL_ORDER: '补录订单',
    MAINTENANCE: '维修记录'
  };
  return map[value] || value;
}

function statementLineSourceText(line: SettlementStatementLine, externalOrderMap: Map<number, ExternalRentalOrder>) {
  if (line.sourceType === 'EXTERNAL_ORDER') {
    return externalOrderMap.get(line.sourceId)?.recordNo || `补录订单 #${line.sourceId}`;
  }
  if (line.sourceType === 'MAINTENANCE') {
    return `维修记录 #${line.sourceId}`;
  }
  if (line.sourceType === 'BILL') {
    return `账单 #${line.sourceId}`;
  }
  return `${statementLineSourceTypeText(line.sourceType)} #${line.sourceId}`;
}

function percent(value: number) {
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function toPercentValue(value: number) {
  return Number((Number(value) * 100).toFixed(2));
}

function fromPercentValue(value: number) {
  return Number((Number(value) / 100).toFixed(4));
}

function money(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function formatDateTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

function beneficiaryText(value: SettlementIncomeEntry['beneficiaryType']) {
  const map: Record<SettlementIncomeEntry['beneficiaryType'], string> = {
    MERCHANT: '门店/商户',
    INVESTOR: '出资方',
    PLATFORM: '平台',
    CHANNEL: '渠道',
    MAINTENANCE_FUND: '历史维修基金'
  };
  return map[value] || value;
}

function lineTypeText(value: SettlementIncomeEntry['lineType']) {
  const map: Record<SettlementIncomeEntry['lineType'], string> = {
    CHANNEL_VERIFICATION_FEE: '渠道核销扣点',
    PLATFORM_SERVICE_FEE: '租赁平台扣点',
    PLATFORM_ORDER_FEE_SERVICE_FEE: '办单费手续费',
    STORE_OPERATION_SHARE: '门店运营分润',
    MAINTENANCE_FUND_SHARE: '门店维修分润',
    CHANNEL_REFERRAL_SHARE: '渠道引流分润',
    INVESTOR_SHARE: '出资方分润',
    MERCHANT_ORDER_FEE: '门店办单费',
    MERCHANT_RENT_SHARE: '门店租金分成',
    PLATFORM_RENT_SHARE: '平台租金分成',
    PLATFORM_OPERATION_FEE: '运营手续费',
    MAINTENANCE_FEE: '维保费',
    INVESTOR_NET_RENT: '出资方净收益'
  };
  return map[value] || value;
}

function incomeStatusTag(value: SettlementIncomeEntry['entryStatus']) {
  const color = value === 'SETTLED' ? 'green' : value === 'FROZEN' ? 'red' : 'blue';
  return <Tag color={color}>{incomeStatusText(value)}</Tag>;
}

function statementBeneficiaryText(value: SettlementStatement['beneficiaryType']) {
  return value === 'MERCHANT' ? '商户/门店' : '出资方';
}

function statementStatusTag(value: SettlementStatement['status']) {
  return <Tag color={statementStatusColor(value)}>{statementStatusText(value)}</Tag>;
}

function statementLineText(value: SettlementStatementLine['lineType']) {
  const map: Record<SettlementStatementLine['lineType'], string> = {
    MERCHANT_SIGN_FEE: '商户签单费',
    MERCHANT_RENT_SHARE: '商户租金分润',
    MERCHANT_MAINTENANCE_SHARE: '门店维修分润',
    MERCHANT_BATTERY_COST_PAYABLE: '门店应付电池公司',
    MERCHANT_MAINTENANCE_REIMBURSE: '门店配件补回',
    MERCHANT_MAINTENANCE_DEDUCT: '商户维保扣减',
    MERCHANT_ADJUSTMENT: '商户调整',
    INVESTOR_GROSS_RENT: '出资方租金毛收益',
    INVESTOR_OPERATION_FEE: '出资方运营手续费',
    INVESTOR_MAINTENANCE_DEDUCT: '出资方维保扣减',
    INVESTOR_ADJUSTMENT: '出资方调整'
  };
  return map[value] || value;
}

function signedMoney(value: number) {
  const amount = Number(value || 0);
  return amount >= 0 ? `+¥${amount.toFixed(2)}` : `-¥${Math.abs(amount).toFixed(2)}`;
}

function currentMonth() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}`;
}

function channelText(value?: string | null) {
  const map: Record<string, string> = {
    DIRECT: '平台直租',
    DOUYIN: '抖音',
    MEITUAN: '美团',
    XIANYU: '闲鱼',
    OFFLINE: '线下',
    OTHER: '其他'
  };
  return value ? (map[value] || value) : '全部渠道';
}
