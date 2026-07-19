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
  assetType: 'VEHICLE_FRAME' | 'BATTERY';
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
  residualValue: number;
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
  assetType: 'VEHICLE_FRAME' | 'BATTERY';
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
  assetType: 'VEHICLE_FRAME' | 'BATTERY';
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

export type RentalOrder = {
  id: number;
  orderNo: string;
  userAccountId?: number | null;
  merchantId: number;
  storeId: number;
  storeSkuId: number;
  skuId: number;
  packageId: number;
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  orderStatus: OrderStatus;
  rentalAmount: number;
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
  expectedPickupAt?: string | null;
  leaseStartedAt?: string | null;
  expectedReturnAt?: string | null;
  returnedAt?: string | null;
  createdAt: string;
  items: OrderItem[];
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
  billType: 'INITIAL' | 'PERIODIC' | 'OVERDUE';
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
  storeSkuId: number;
  skuId: number;
  merchantId: number;
  storeId: number;
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  matchedRuleId?: number | null;
  matchedRuleScope: string;
  rentalAmount: number;
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
  ruleSummary: string;
  createdAt: string;
};

export type SettlementIncomeEntry = {
  id: number;
  entryNo: string;
  orderId: number;
  snapshotId: number;
  merchantId: number;
  storeId: number;
  beneficiaryType: 'MERCHANT' | 'INVESTOR' | 'PLATFORM';
  beneficiaryId?: number | null;
  lineType: 'MERCHANT_ORDER_FEE' | 'MERCHANT_RENT_SHARE' | 'PLATFORM_RENT_SHARE' | 'PLATFORM_OPERATION_FEE' | 'MAINTENANCE_FEE' | 'INVESTOR_NET_RENT';
  amount: number;
  entryStatus: 'PENDING' | 'SETTLED' | 'FROZEN';
  remark?: string | null;
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
  signFeeAmount: number;
  failureReason?: string | null;
  consumedAt?: string | null;
  exceptionReason?: string | null;
  createdAt: string;
};
