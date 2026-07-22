export type ApiResult<T> = {
  code: number;
  message: string;
  data: T;
};

export type PageResult<T> = {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
};

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

export type Merchant = {
  id: number;
  merchantCode: string;
  merchantName: string;
  contactName: string;
  contactPhone: string;
  businessLicenseNo?: string | null;
  status: 'ENABLED' | 'DISABLED';
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

export type ExternalRentalOrderStatus = 'ACTIVE' | 'COMPLETED' | 'TERMINATED';

export type ExternalRentalOrderSourcePlatform = 'DOUYIN' | 'MEITUAN' | 'XIANYU' | 'OFFLINE' | 'OTHER';

export type ExternalRentalOrderLog = {
  id: number;
  externalOrderId: number;
  fromStatus?: ExternalRentalOrderStatus | null;
  toStatus: ExternalRentalOrderStatus;
  operationType: 'CREATE' | 'COMPLETE' | 'TERMINATE';
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type ExternalRentalOrder = {
  id: number;
  recordNo: string;
  sourcePlatform: ExternalRentalOrderSourcePlatform;
  externalOrderNo?: string | null;
  merchantId: number;
  merchantName?: string | null;
  storeId: number;
  storeName?: string | null;
  storeSkuId: number;
  storeSkuDisplayName?: string | null;
  skuId: number;
  skuName?: string | null;
  packageId: number;
  packageName?: string | null;
  customerName: string;
  customerPhone: string;
  frameAssetId?: number | null;
  frameAssetSerialNo?: string | null;
  batteryAssetId?: number | null;
  batteryAssetSerialNo?: string | null;
  orderStatus: ExternalRentalOrderStatus;
  externalRentalAmount: number;
  signFeeAmount: number;
  depositAmount: number;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  rentStartedAt: string;
  expectedReturnAt?: string | null;
  finishedAt?: string | null;
  returnStoreId?: number | null;
  returnStoreName?: string | null;
  terminationReason?: string | null;
  remark?: string | null;
  createdByAccountId?: number | null;
  updatedByAccountId?: number | null;
  createdAt: string;
  updatedAt: string;
  logs: ExternalRentalOrderLog[];
};

export type Employee = {
  id: number;
  merchantId: number;
  storeId?: number | null;
  username: string;
  displayName: string;
  phone: string;
  accountType: 'MERCHANT_OWNER' | 'STORE_MANAGER' | 'STORE_OPERATOR' | 'STORE_STAFF' | 'MAINTENANCE_STAFF' | 'WAREHOUSE_STAFF';
  status: 'ENABLED' | 'DISABLED';
  authorizedStores: Store[];
};

export type SystemAccount = {
  id: number;
  accountType:
    | 'PLATFORM_ADMIN'
    | 'FINANCE'
    | 'MERCHANT_OWNER'
    | 'STORE_MANAGER'
    | 'STORE_OPERATOR'
    | 'STORE_STAFF'
    | 'MAINTENANCE_STAFF'
    | 'WAREHOUSE_STAFF'
    | 'INVESTOR'
    | 'CONSUMER';
  username?: string | null;
  phone?: string | null;
  displayName: string;
  merchantId?: number | null;
  merchantName?: string | null;
  storeId?: number | null;
  storeName?: string | null;
  investorId?: number | null;
  investorName?: string | null;
  status: 'ENABLED' | 'DISABLED';
  lastLoginAt?: string | null;
  createdAt: string;
  roles: string[];
  permissions: string[];
  directPermissions: string[];
  storeScopes: StoreScope[];
};

export type SystemRole = {
  id: number;
  roleCode: string;
  roleName: string;
  roleScope: string;
  status: 'ENABLED' | 'DISABLED';
  createdAt: string;
  permissions: string[];
};

export type SystemPermission = {
  id: number;
  permissionCode: string;
  permissionName: string;
  moduleCode: string;
  createdAt: string;
};

export type SystemAccountCreatePayload = {
  roleCode:
    | 'PLATFORM_ADMIN'
    | 'FINANCE'
    | 'MERCHANT_OWNER'
    | 'STORE_MANAGER'
    | 'STORE_OPERATOR'
    | 'STORE_STAFF'
    | 'MAINTENANCE_STAFF'
    | 'WAREHOUSE_STAFF'
    | 'INVESTOR';
  username: string;
  displayName: string;
  phone: string;
  password: string;
  merchantId?: number;
  investorId?: number;
  storeIds?: number[];
};

export type SystemAccountUpdatePayload = {
  username?: string;
  displayName: string;
  phone: string;
};

export type SystemAccountResetPasswordPayload = {
  password: string;
};

export type Investor = {
  id: number;
  investorCode: string;
  investorName: string;
  contactName: string;
  contactPhone: string;
  operationFeeRate: number;
  status: 'ENABLED' | 'DISABLED';
};

export type AssetType = 'VEHICLE_FRAME' | 'BATTERY' | 'INTEGRATED_VEHICLE';

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
  assetType: AssetType;
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

export type AssetBatchImportRowResult = {
  lineNo?: number | null;
  success: boolean;
  assetId?: number | null;
  assetCode?: string | null;
  serialNo?: string | null;
  message: string;
};

export type AssetBatchImportResult = {
  totalCount: number;
  successCount: number;
  failedCount: number;
  results: AssetBatchImportRowResult[];
};

export type AssetLog = {
  id: number;
  assetId: number;
  logType: 'STATUS' | 'LOCATION' | 'OWNERSHIP';
  fromValue?: string | null;
  toValue?: string | null;
  remark?: string | null;
  createdAt: string;
};

export type SparePart = {
  id: number;
  partCode: string;
  partName: string;
  spec?: string | null;
  unit: string;
  procurementPrice: number;
  unitPrice: number;
  buybackPrice: number;
  stockQuantity: number;
  stockAmount: number;
  status: 'ENABLED' | 'DISABLED';
  createdAt: string;
  updatedAt: string;
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
  assetType: AssetType;
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

export type AssetRentalBill = {
  id: number;
  billNo: string;
  billType: string;
  periodNo: number;
  billStatus: BillStatus;
  dueAt: string;
  payableAmount: number;
  paidAmount: number;
  overdueAmount: number;
};

export type AssetRentalRecord = {
  recordType: 'FORMAL' | 'EXTERNAL';
  orderId: number;
  orderNo: string;
  sourcePlatform?: ExternalRentalOrderSourcePlatform | null;
  externalOrderNo?: string | null;
  userAccountId?: number | null;
  storeId: number;
  customerName?: string | null;
  customerPhone?: string | null;
  orderStatus: OrderStatus | ExternalRentalOrderStatus;
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  rentalAmount: number;
  signFeeAmount: number;
  paidAmount: number;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  leaseStartedAt?: string | null;
  expectedReturnAt?: string | null;
  returnedAt?: string | null;
  createdAt: string;
  bills: AssetRentalBill[];
};

export type AssetDetail = {
  asset: Asset;
  rentals: AssetRentalRecord[];
  maintenances: AssetMaintenance[];
};

export type ExternalRentalOrderImportRowResult = {
  lineNo?: number | null;
  success: boolean;
  orderId?: number | null;
  recordNo?: string | null;
  message: string;
};

export type ExternalRentalOrderBatchImportResult = {
  totalCount: number;
  successCount: number;
  failedCount: number;
  results: ExternalRentalOrderImportRowResult[];
};

export type ProductCategory = {
  id: number;
  categoryCode: string;
  categoryName: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
};

export type ProductSku = {
  id: number;
  skuCode: string;
  categoryId: number;
  categoryName?: string | null;
  skuName: string;
  skuType: 'RENTAL' | 'SALE';
  description?: string | null;
  needFrameAsset: boolean;
  needBatteryAsset: boolean;
  supportCrossStoreReturn: boolean;
  status: 'ENABLED' | 'DISABLED';
};

export type ProductPackage = {
  id: number;
  packageCode: string;
  skuId: number;
  skuName?: string | null;
  packageName: string;
  priceAmount: number;
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  billDayMode: 'PAYMENT_DAY' | 'FIXED_DAY';
  billDay?: number | null;
  status: 'ENABLED' | 'DISABLED';
};

export type StoreSkuPackage = {
  id: number;
  packageId: number;
  packageCode: string;
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

export type ProfitRule = {
  id: number;
  ruleCode: string;
  ruleName: string;
  ruleScope: 'PLATFORM' | 'SKU' | 'STORE' | 'STORE_SKU';
  sourceChannel?: string | null;
  priority: number;
  skuId?: number | null;
  merchantId?: number | null;
  storeId?: number | null;
  storeSkuId?: number | null;
  channelFeeRate: number;
  platformFeeRate: number;
  storeOperationRate: number;
  maintenanceFundRate: number;
  channelReferralRate: number;
  investorShareRate: number;
  effectiveAt: string;
  expiredAt?: string | null;
  status: 'ENABLED' | 'DISABLED';
};

export type SettlementSnapshot = {
  id?: number | null;
  snapshotNo: string;
  sourceType: 'PREVIEW' | 'ORDER';
  sourceId?: number | null;
  calculationVersion: 'LEGACY_V1' | 'PROFIT_V2';
  sourceChannel: string;
  storeSkuId: number;
  skuId: number;
  merchantId: number;
  storeId: number;
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  matchedRuleId: number;
  matchedRuleScope: 'PLATFORM' | 'SKU' | 'STORE' | 'STORE_SKU';
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
  createdAt?: string | null;
};

export type SettlementIncomeEntry = {
  id: number;
  entryNo: string;
  orderId: number;
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
  settledAt?: string | null;
  createdAt: string;
};

export type SettlementStatement = {
  id: number;
  statementNo: string;
  statementMonth: string;
  beneficiaryType: 'MERCHANT' | 'INVESTOR';
  beneficiaryId: number;
  merchantId: number;
  storeId: number;
  rentBaseAmount: number;
  signFeeIncomeAmount: number;
  rentShareIncomeAmount: number;
  operationFeeAmount: number;
  maintenanceDeductAmount: number;
  adjustmentAmount: number;
  payableAmount: number;
  orderCount: number;
  billCount: number;
  status: 'DRAFT' | 'RECONCILING' | 'CONFIRMED' | 'PAYABLE' | 'PAID' | 'CLOSED';
  generatedAt: string;
  confirmedAt?: string | null;
  paidAt?: string | null;
  remark?: string | null;
  lineCount: number;
};

export type SettlementStatementLine = {
  id: number;
  statementId: number;
  lineNo: string;
  sourceType: string;
  sourceId: number;
  orderId?: number | null;
  billId?: number | null;
  assetId?: number | null;
  merchantId: number;
  storeId: number;
  investorId: number;
  lineType:
    | 'MERCHANT_SIGN_FEE'
    | 'MERCHANT_RENT_SHARE'
    | 'MERCHANT_MAINTENANCE_REIMBURSE'
    | 'MERCHANT_MAINTENANCE_DEDUCT'
    | 'MERCHANT_ADJUSTMENT'
    | 'INVESTOR_GROSS_RENT'
    | 'INVESTOR_OPERATION_FEE'
    | 'INVESTOR_MAINTENANCE_DEDUCT'
    | 'INVESTOR_ADJUSTMENT';
  amount: number;
  occurredAt?: string | null;
  remark?: string | null;
  createdAt: string;
};

export type SettlementOverview = {
  statementMonth: string;
  totalPaidRentAmount: number;
  totalSignFeeAmount: number;
  totalMerchantPayableAmount: number;
  totalInvestorPayableAmount: number;
  totalOperationFeeAmount: number;
  totalMaintenanceDeductAmount: number;
  totalOpenOverdueAmount: number;
  merchantStatementCount: number;
  investorStatementCount: number;
};

export type SettlementStatementGenerateResult = {
  statementMonth: string;
  merchantStatementCount: number;
  investorStatementCount: number;
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

export type OrderItem = {
  id: number;
  itemType: string;
  refId?: number | null;
  itemName: string;
  quantity: number;
  unitAmount: number;
  totalAmount: number;
};

export type OrderLog = {
  id: number;
  orderId: number;
  fromStatus?: OrderStatus | null;
  toStatus: OrderStatus;
  operationType: string;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type OrderLeaseBonusType = 'REVIEW' | 'CAMPAIGN';

export type OrderLeaseBonus = {
  id: number;
  orderId: number;
  bonusType: OrderLeaseBonusType;
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
  orderedAt: string;
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
  cancelledAt?: string | null;
  cancelReason?: string | null;
  exceptionReason?: string | null;
  createdAt: string;
  items: OrderItem[];
  leaseBonuses: OrderLeaseBonus[];
  logs: OrderLog[];
};

export type OrderBatchImportRowResult = {
  lineNo?: number | null;
  success: boolean;
  orderId?: number | null;
  orderNo?: string | null;
  customerName?: string | null;
  customerPhone?: string | null;
  message: string;
};

export type OrderBatchImportResult = {
  totalCount: number;
  successCount: number;
  failedCount: number;
  results: OrderBatchImportRowResult[];
};

export type BillType = 'INITIAL' | 'PERIODIC' | 'RENEWAL' | 'OVERDUE';

export type BillStatus =
  | 'PENDING_PAYMENT'
  | 'PAYING'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED'
  | 'FAILED';

export type BillItem = {
  id: number;
  itemType: 'RENT' | 'RENEWAL_RENT' | 'SIGN_FEE' | 'DEPOSIT' | 'OVERDUE_FEE';
  itemName: string;
  amount: number;
};

export type BillLog = {
  id: number;
  billId: number;
  fromStatus?: BillStatus | null;
  toStatus: BillStatus;
  operationType: string;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type RentalBill = {
  id: number;
  billNo: string;
  orderId: number;
  userAccountId?: number | null;
  merchantId: number;
  storeId: number;
  billType: BillType;
  periodNo: number;
  billStatus: BillStatus;
  dueAt: string;
  payableAmount: number;
  paidAmount: number;
  overdueAmount: number;
  paidAt?: string | null;
  cancelledAt?: string | null;
  remark?: string | null;
  generatedBatchNo?: string | null;
  createdAt: string;
  items: BillItem[];
  logs: BillLog[];
};

export type BillBatch = {
  id: number;
  batchNo: string;
  generationType: 'INITIAL' | 'PERIODIC' | 'PLAN' | 'RENEWAL' | 'OVERDUE' | 'MANUAL';
  orderId?: number | null;
  generatedCount: number;
  remark?: string | null;
  createdAt: string;
};

export type BillGenerationResult = {
  batch: BillBatch;
  bills: RentalBill[];
};

export type PayStatus =
  | 'CREATED'
  | 'PAYING'
  | 'PAID'
  | 'FAILED'
  | 'CLOSED'
  | 'REFUNDING'
  | 'REFUNDED';

export type PaymentOrder = {
  id: number;
  paymentNo: string;
  billId: number;
  orderId: number;
  userAccountId?: number | null;
  merchantId: number;
  storeId: number;
  payChannel: 'ALIPAY';
  payStatus: PayStatus;
  payAmount: number;
  paidAmount: number;
  subject: string;
  payerAlipayUserId?: string | null;
  alipayTradeNo?: string | null;
  refundAmount: number;
  paidAt?: string | null;
  closedAt?: string | null;
  lastError?: string | null;
  createdAt: string;
};

export type PaymentCallback = {
  id: number;
  paymentId?: number | null;
  notifyId?: string | null;
  outTradeNo?: string | null;
  alipayTradeNo?: string | null;
  tradeStatus?: string | null;
  totalAmount?: number | null;
  verified: boolean;
  processed: boolean;
  failureReason?: string | null;
  receivedAt: string;
};

export type AgreementStatus = 'SIGNING' | 'SIGNED' | 'UNSIGNED' | 'INVALID' | 'FAILED';

export type PayAgreement = {
  id: number;
  agreementNo?: string | null;
  externalAgreementNo: string;
  userAccountId: number;
  alipayUserId: string;
  orderId?: number | null;
  merchantId: number;
  storeId: number;
  agreementType: 'CYCLE_PAY';
  agreementStatus: AgreementStatus;
  personalProductCode: string;
  signScene: string;
  maxSingleAmount: number;
  signTime?: string | null;
  validTime?: string | null;
  invalidTime?: string | null;
  lastError?: string | null;
  createdAt: string;
};

export type AgreementNotify = {
  id: number;
  agreementId?: number | null;
  notifyId?: string | null;
  externalAgreementNo?: string | null;
  agreementNo?: string | null;
  agreementStatus?: string | null;
  verified: boolean;
  processed: boolean;
  failureReason?: string | null;
  receivedAt: string;
};

export type DeductStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';

export type DeductBatch = {
  id: number;
  batchNo: string;
  batchStatus: 'PROCESSING' | 'FINISHED';
  plannedCount: number;
  successCount: number;
  failedCount: number;
  remark?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt: string;
};

export type RenewalRunResponse = {
  scannedCount: number;
  generatedCount: number;
  batchId?: number | null;
  batchNo?: string | null;
};

export type DeductRecord = {
  id: number;
  deductNo: string;
  batchNo?: string | null;
  billId: number;
  orderId: number;
  agreementId: number;
  agreementNo: string;
  paymentId?: number | null;
  deductStatus: DeductStatus;
  deductAmount: number;
  retryCount: number;
  nextRetryAt?: string | null;
  alipayTradeNo?: string | null;
  lastError?: string | null;
  requestedAt?: string | null;
  successAt?: string | null;
  createdAt: string;
};

export type OverdueStatus = 'OPEN' | 'RESOLVED' | 'CLOSED';

export type CollectionStatus = 'PENDING' | 'CONTACTED' | 'PROMISED' | 'RESOLVED' | 'BAD_DEBT';

export type OverdueCollectionLog = {
  id: number;
  overdueCaseId: number;
  collectionStatus: CollectionStatus;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type OverdueCase = {
  id: number;
  caseNo: string;
  statMonth: string;
  orderId: number;
  billId: number;
  userAccountId?: number | null;
  merchantId: number;
  storeId: number;
  storeSkuId: number;
  skuId: number;
  overdueAmount: number;
  unpaidAmount: number;
  failCount: number;
  lastFailReason?: string | null;
  lastDeductAt?: string | null;
  overdueStatus: OverdueStatus;
  collectionStatus: CollectionStatus;
  collectionRemark?: string | null;
  resolvedAt?: string | null;
  createdAt: string;
  logs: OverdueCollectionLog[];
};

export type AssetHandover = {
  id: number;
  handoverNo: string;
  orderId: number;
  merchantId: number;
  storeId: number;
  userAccountId?: number | null;
  handoverType: 'PICKUP' | 'RETURN';
  frameAssetId?: number | null;
  batteryAssetId?: number | null;
  frameResultStatus?: AssetStatus | null;
  batteryResultStatus?: AssetStatus | null;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type AssetChange = {
  id: number;
  changeNo: string;
  orderId: number;
  merchantId: number;
  storeId: number;
  assetType: AssetType;
  oldAssetId?: number | null;
  newAssetId: number;
  oldAssetResultStatus: AssetStatus;
  operatorAccountId?: number | null;
  remark?: string | null;
  createdAt: string;
};

export type FundAuthStatus = 'CREATED' | 'AUTHORIZING' | 'AUTHORIZED' | 'FAILED' | 'CANCELLED' | 'UNFROZEN' | 'CAPTURED' | 'CLOSED';

export type FundAuthorization = {
  id: number;
  authOrderNo: string;
  orderId: number;
  userAccountId: number;
  alipayUserId: string;
  merchantId: number;
  storeId: number;
  authType: 'ALIPAY_FUND_AUTH' | 'ZHIMA_CREDIT';
  authStatus: FundAuthStatus;
  authAmount: number;
  frozenAmount: number;
  capturedAmount: number;
  releasedAmount: number;
  outRequestNo: string;
  alipayAuthNo?: string | null;
  alipayOperationId?: string | null;
  orderStr?: string | null;
  subject: string;
  lastError?: string | null;
  authorizedAt?: string | null;
  closedAt?: string | null;
  createdAt: string;
};

export type FundAuthOperation = {
  id: number;
  operationNo: string;
  authOrderId: number;
  billId?: number | null;
  paymentId?: number | null;
  operationType: 'FREEZE' | 'QUERY' | 'CAPTURE' | 'UNFREEZE' | 'CANCEL';
  operationStatus: 'PENDING' | 'SUCCESS' | 'FAILED';
  amount: number;
  outRequestNo: string;
  alipayTradeNo?: string | null;
  alipayOperationId?: string | null;
  remark?: string | null;
  failureReason?: string | null;
  createdAt: string;
};

export type FundAuthNotify = {
  id: number;
  authOrderId?: number | null;
  notifyId?: string | null;
  outOrderNo?: string | null;
  outRequestNo?: string | null;
  authNo?: string | null;
  operationId?: string | null;
  authStatus?: string | null;
  totalFreezeAmount?: number | null;
  restAmount?: number | null;
  verified: boolean;
  processed: boolean;
  failureReason?: string | null;
  receivedAt: string;
};

export type IdentityVerification = {
  id: number;
  userAccountId: number;
  orderId?: number | null;
  frontImageUrl?: string | null;
  backImageUrl?: string | null;
  ocrStatus: 'PENDING' | 'SUCCESS' | 'FAILED';
  realNameStatus: 'PENDING' | 'VERIFIED' | 'FAILED';
  realNameMasked?: string | null;
  idNoMasked?: string | null;
  gender?: string | null;
  birthDate?: string | null;
  addressMasked?: string | null;
  failureReason?: string | null;
  verifiedAt?: string | null;
  createdAt: string;
};

export type ContractTemplate = {
  id: number;
  templateCode: string;
  templateName: string;
  contractType: 'RENTAL' | 'SALE';
  versionNo: string;
  providerTemplateId?: string | null;
  content: string;
  status: 'ENABLED' | 'DISABLED';
  remark?: string | null;
  createdAt: string;
};

export type RentalContractRecord = {
  id: number;
  contractNo: string;
  orderId: number;
  userAccountId: number;
  merchantId: number;
  storeId: number;
  templateId: number;
  contractType: 'RENTAL' | 'SALE';
  contractStatus: 'DRAFT' | 'SIGNING' | 'SIGNED' | 'ARCHIVED' | 'FAILED' | 'CANCELLED';
  provider?: string | null;
  externalFlowId?: string | null;
  signUrl?: string | null;
  archivePdfUrl?: string | null;
  renderedContent: string;
  failureReason?: string | null;
  sentAt?: string | null;
  signedAt?: string | null;
  archivedAt?: string | null;
  createdAt: string;
};

export type ContractNotifyRecord = {
  id: number;
  contractId?: number | null;
  externalFlowId?: string | null;
  notifyId?: string | null;
  contractStatus?: string | null;
  verified: boolean;
  processed: boolean;
  rawPayload: string;
  failureReason?: string | null;
  receivedAt: string;
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
  externalPrepareId?: string | null;
  externalVerifyId?: string | null;
  externalConsumeId?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  retryCount: number;
  failureReason?: string | null;
  verifiedAt?: string | null;
  consumedAt?: string | null;
  exceptionReason?: string | null;
  createdAt: string;
};

export type AuditLogRecord = {
  id: number;
  accountId?: number | null;
  accountType?: string | null;
  requestMethod: string;
  requestUri: string;
  queryString?: string | null;
  httpStatus?: number | null;
  success: boolean;
  errorMessage?: string | null;
  clientIp?: string | null;
  userAgent?: string | null;
  createdAt: string;
};

export type ExportTaskRecord = {
  id: number;
  taskNo: string;
  exportType: string;
  requestParams?: string | null;
  taskStatus: string;
  fileUrl?: string | null;
  failureReason?: string | null;
  createdBy?: number | null;
  createdAt: string;
  finishedAt?: string | null;
};

export type ReconciliationBatchRecord = {
  id: number;
  batchNo: string;
  channel: string;
  billDate: string;
  batchStatus: string;
  platformTotalAmount: number;
  channelTotalAmount: number;
  diffCount: number;
  remark?: string | null;
  createdBy?: number | null;
  createdAt: string;
  finishedAt?: string | null;
};
