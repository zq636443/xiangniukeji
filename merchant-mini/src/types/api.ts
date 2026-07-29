export type ApiResult<T> = {
  code: number;
  message: string;
  data: T;
};

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PENDING_REAL_NAME'
  | 'PENDING_AGREEMENT'
  | 'PENDING_DEPOSIT_AUTH'
  | 'PENDING_VERIFY'
  | 'PENDING_PICKUP'
  | 'RENTING'
  | 'PENDING_RETURN'
  | 'OVERDUE'
  | 'PENDING_SUPPLEMENT'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXCEPTION';

export type StoreScope = {
  merchantId: number;
  storeId?: number | null;
  scopeType: 'ALL_MERCHANT_STORES' | 'SINGLE_STORE';
};

export type CurrentAccount = {
  id: number;
  accountType: string;
  username?: string | null;
  phone?: string | null;
  alipayUserId?: string | null;
  displayName: string;
  merchantId?: number | null;
  storeId?: number | null;
  investorId?: number | null;
  roles: string[];
  permissions: string[];
  storeScopes: StoreScope[];
};

export type LoginResponse = {
  token: string;
  expiresAt: string;
  account: CurrentAccount;
};

export type Store = {
  id: number;
  merchantId: number;
  storeCode: string;
  storeName: string;
  address: string;
  businessHours?: string | null;
  longitude?: number | null;
  latitude?: number | null;
  qrContent: string;
  status: 'ENABLED' | 'DISABLED';
};

export type StoreSkuPackage = {
  id: number;
  packageId: number;
  packageName: string;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  billDayMode: 'PAYMENT_DAY' | 'FIXED_DAY';
  billDay?: number | null;
  rentalAmount: number;
  periodAmount: number;
  depositAmount: number;
  autoRenewEnabled: boolean;
  renewalUnit?: 'DAY' | 'MONTH' | null;
  renewalValue?: number | null;
  renewalAmount?: number | null;
  status: 'ENABLED' | 'DISABLED';
};

export type StoreSku = {
  id: number;
  merchantId: number;
  merchantName?: string | null;
  storeId: number;
  storeName?: string | null;
  skuId: number;
  skuName?: string | null;
  storeSkuCode: string;
  saleMode: 'RENTAL' | 'SALE';
  displayName: string;
  signFeeAmount: number;
  signFeePayer: 'USER' | 'MERCHANT';
  needFrameAsset: boolean;
  needBatteryAsset: boolean;
  supportCrossStoreReturn: boolean;
  status: 'ON_SHELF' | 'OFF_SHELF';
  packages: StoreSkuPackage[];
};

export type AssetStatus =
  | 'IDLE'
  | 'RENTING'
  | 'PENDING_REPAIR'
  | 'REPAIRING'
  | 'SCRAPPED'
  | 'SOLD'
  | 'EXCEPTION';

export type Asset = {
  id: number;
  assetCode: string;
  assetType: 'VEHICLE_FRAME' | 'BATTERY' | 'INTEGRATED_VEHICLE' | 'GENERAL';
  assetTypeId: number;
  assetTypeCode: string;
  assetTypeName: string;
  serialLabel: string;
  serialNo: string;
  investorId: number;
  investorName?: string | null;
  currentMerchantId?: number | null;
  merchantName?: string | null;
  currentStoreId?: number | null;
  storeName?: string | null;
  status: AssetStatus;
  purchaseAmount: number;
  maintenanceFeeAmount: number;
  residualValue?: number | null;
  purchasedAt?: string | null;
  scrappedAt?: string | null;
  soldAt?: string | null;
};

export type StoreSparePartStock = {
  merchantId: number;
  merchantName: string;
  storeId: number;
  storeName: string;
  partId: number;
  partName: string;
  stockQuantity: number;
  avgUnitPrice: number;
  stockAmount: number;
};

export type SparePartStockLog = {
  id: number;
  partId: number;
  merchantId?: number | null;
  merchantName?: string | null;
  storeId?: number | null;
  storeName?: string | null;
  partName: string;
  changeType:
    | 'INBOUND'
    | 'CONSUME'
    | 'ADJUST'
    | 'PLATFORM_INBOUND'
    | 'PLATFORM_ADJUST'
    | 'STORE_PURCHASE_OUT'
    | 'STORE_PURCHASE_IN'
    | 'STORE_BUYBACK_OUT'
    | 'STORE_BUYBACK_IN'
    | 'STORE_CONSUME'
    | 'STORE_ADJUST'
    | 'STORE_TRANSFER_OUT'
    | 'STORE_TRANSFER_IN';
  quantityChange: number;
  unitPrice: number;
  amount: number;
  refType?: string | null;
  refId?: number | null;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type AssetMaintenancePart = {
  id: number;
  maintenanceId: number;
  partId: number;
  partNameSnapshot: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  remark?: string | null;
};

export type AssetMaintenance = {
  id: number;
  maintenanceNo: string;
  assetId: number;
  assetCode: string;
  assetType: 'VEHICLE_FRAME' | 'BATTERY' | 'INTEGRATED_VEHICLE' | 'GENERAL';
  assetTypeName?: string | null;
  serialNo: string;
  orderId?: number | null;
  storeId?: number | null;
  maintenanceType: string;
  maintenanceStatus: string;
  responsibilityType: 'ROUTINE_MAINTENANCE' | 'CUSTOMER_DAMAGE' | 'MERCHANT_RESPONSIBILITY' | 'PLATFORM_SUBSIDY';
  startedAt?: string | null;
  completedAt?: string | null;
  laborCost: number;
  externalCost: number;
  partsCost: number;
  totalCost: number;
  merchantReimbursementAmount: number;
  investorDeductAmount: number;
  customerChargeAmount: number;
  costBearerType: 'USER' | 'INVESTOR' | 'MERCHANT' | 'PLATFORM';
  costBearerId?: number | null;
  operatorAccountId?: number | null;
  operatorAccountName?: string | null;
  remark?: string | null;
  createdAt: string;
  parts: AssetMaintenancePart[];
};

export type CollectionStatus = 'PENDING' | 'CONTACTED' | 'PROMISED' | 'RESOLVED' | 'BAD_DEBT';

export type OverdueCase = {
  id: number;
  caseNo: string;
  statMonth: string;
  orderId: number;
  billId: number;
  storeId: number;
  overdueAmount: number;
  unpaidAmount: number;
  failCount: number;
  lastFailReason?: string | null;
  overdueStatus: 'OPEN' | 'RESOLVED' | 'CLOSED';
  collectionStatus: CollectionStatus;
  collectionRemark?: string | null;
};

export type AssetHandover = {
  id: number;
  handoverNo: string;
  orderId: number;
  storeId: number;
  handoverType: 'PICKUP' | 'RETURN';
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  frameResultStatus?: AssetStatus | null;
  batteryResultStatus?: AssetStatus | null;
  remark?: string | null;
};

export type AssetChange = {
  id: number;
  changeNo: string;
  orderId: number;
  assetType: 'VEHICLE_FRAME' | 'BATTERY' | 'INTEGRATED_VEHICLE' | 'GENERAL';
  oldAssetId?: number | null;
  newAssetId: number;
  oldAssetResultStatus: AssetStatus;
  remark?: string | null;
};

export type OrderItem = {
  id: number;
  itemType: string;
  refId?: number | null;
  itemName: string;
  quantity: number;
  unitAmount: number;
  totalAmount: number;
};

export type OrderLeaseBonus = {
  id: number;
  orderId: number;
  bonusType: 'REVIEW' | 'CAMPAIGN';
  bonusDays: number;
  operatorAccountId?: number | null;
  remark?: string | null;
  expectedReturnBefore?: string | null;
  expectedReturnAfter?: string | null;
  createdAt: string;
};

export type RentalOrder = {
  id: number;
  orderNo: string;
  userAccountId?: number | null;
  customerName?: string | null;
  customerPhone?: string | null;
  merchantId: number;
  storeId: number;
  storeName?: string | null;
  storeSkuId: number;
  storeSkuName?: string | null;
  skuId: number;
  packageId: number;
  packageName?: string | null;
  frameAssetId?: number | null;
  frameAssetCode?: string | null;
  frameSerialNo?: string | null;
  batteryAssetId?: number | null;
  batteryAssetCode?: string | null;
  batterySerialNo?: string | null;
  orderStatus: OrderStatus;
  rentalAmount: number;
  verificationAmount: number;
  signFeeAmount: number;
  depositAmount: number;
  payableAmount: number;
  paidAmount: number;
  settlementSnapshotId?: number | null;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  billDayMode: 'PAYMENT_DAY' | 'FIXED_DAY';
  billDay?: number | null;
  autoRenewEnabled: boolean;
  renewalUnit?: 'DAY' | 'MONTH' | null;
  renewalValue?: number | null;
  renewalAmount?: number | null;
  renewalCount: number;
  reviewBonusDays: number;
  campaignBonusDays: number;
  totalBonusDays: number;
  expectedPickupAt?: string | null;
  leaseStartedAt?: string | null;
  expectedReturnAt?: string | null;
  returnedAt?: string | null;
  createdAt: string;
  items: OrderItem[];
  leaseBonuses: OrderLeaseBonus[];
};

export type BillItem = {
  id: number;
  itemType: string;
  itemName: string;
  amount: number;
};

export type RentalBill = {
  id: number;
  billNo: string;
  orderId: number;
  billType: 'INITIAL' | 'PERIODIC' | 'RENEWAL' | 'OVERDUE' | 'VOUCHER_RENT';
  periodNo: number;
  billStatus: 'PENDING_PAYMENT' | 'PAYING' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'FAILED';
  dueAt: string;
  payableAmount: number;
  paidAmount: number;
  overdueAmount: number;
  items: BillItem[];
};

export type SettlementSnapshot = {
  id: number;
  snapshotNo: string;
  sourceType: string;
  sourceId: number;
  calculationVersion: 'LEGACY_V1' | 'PROFIT_V2';
  sourceChannel: string;
  storeSkuId: number;
  skuId: number;
  merchantId: number;
  storeId: number;
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  matchedRuleId?: number | null;
  matchedRuleScope: string;
  rentalAmount: number;
  settlementBaseAmount: number;
  signFeeAmount: number;
  merchantOrderFeeAmount: number;
  merchantRentShareRate: number;
  merchantRentShareAmount: number;
  platformRentShareRate: number;
  platformRentShareAmount: number;
  investorRentShareRate: number;
  investorGrossShareAmount: number;
  investorOperationFeeAmount: number;
  maintenanceFeeAmount: number;
  investorNetShareAmount: number;
  channelFeeRate: number;
  channelFeeAmount: number;
  platformFeeRate: number;
  platformFeeAmount: number;
  distributableAmount: number;
  storeOperationRate: number;
  storeOperationAmount: number;
  maintenanceFundRate: number;
  maintenanceFundAmount: number;
  channelReferralRate: number;
  channelReferralAmount: number;
  investorShareRate: number;
  investorShareAmount: number;
  ruleSummary: string;
  createdAt: string;
};

export type SettlementIncomeEntry = {
  id: number;
  entryNo: string;
  sourceType: 'ORDER' | 'BILL' | 'EXTERNAL_ORDER';
  sourceId: number;
  sourceNo?: string | null;
  orderId?: number | null;
  snapshotId: number;
  merchantId: number;
  storeId: number;
  beneficiaryType: 'MERCHANT' | 'INVESTOR' | 'PLATFORM' | 'CHANNEL' | 'MAINTENANCE_FUND';
  beneficiaryId?: number | null;
  lineType:
    | 'CHANNEL_VERIFICATION_FEE'
    | 'PLATFORM_SERVICE_FEE'
    | 'STORE_OPERATION_SHARE'
    | 'MAINTENANCE_FUND_SHARE'
    | 'CHANNEL_REFERRAL_SHARE'
    | 'INVESTOR_SHARE'
    | 'MERCHANT_ORDER_FEE'
    | 'MERCHANT_RENT_SHARE'
    | 'PLATFORM_RENT_SHARE'
    | 'PLATFORM_OPERATION_FEE'
    | 'MAINTENANCE_FEE'
    | 'INVESTOR_NET_RENT';
  amount: number;
  entryStatus: 'PENDING' | 'SETTLED' | 'FROZEN';
  remark?: string | null;
  occurredAt: string;
  settledAt?: string | null;
  createdAt: string;
};

export type VoucherRecord = {
  id: number;
  sourcePlatform: 'DOUYIN' | 'MEITUAN' | 'XIANYU';
  voucherCode: string;
  userAccountId?: number | null;
  merchantId: number;
  storeId: number;
  storeSkuId: number;
  packageId: number;
  orderId?: number | null;
  signFeeBillId?: number | null;
  verifyStatus: 'INPUT' | 'PREPARED' | 'VERIFIED' | 'WAITING_SIGN_FEE' | 'CONSUMING' | 'CONSUMED' | 'FAILED' | 'EXCEPTION';
  voucherTitle?: string | null;
  voucherAmount: number;
  verificationAmount?: number | null;
  signFeeAmount: number;
  failureReason?: string | null;
  consumedAt?: string | null;
  exceptionReason?: string | null;
  createdAt: string;
};
