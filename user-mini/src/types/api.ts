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

export type PaymentOrder = {
  id: number;
  paymentNo: string;
  billId: number;
  orderId: number;
  payChannel: 'ALIPAY';
  payStatus: 'CREATED' | 'PAYING' | 'PAID' | 'FAILED' | 'CLOSED' | 'REFUNDING' | 'REFUNDED';
  payAmount: number;
  paidAmount: number;
  subject: string;
  alipayTradeNo?: string | null;
};

export type AlipayTradeCreateResult = {
  payment: PaymentOrder;
  tradeNo: string;
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
  leaseUnit: 'DAY' | 'MONTH';
  leaseValue: number;
  totalPeriods: number;
  leaseMultiplier: number;
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
  billType: 'INITIAL' | 'PERIODIC' | 'RENEWAL' | 'OVERDUE';
  periodNo: number;
  billStatus: 'PENDING_PAYMENT' | 'PAYING' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'FAILED';
  dueAt: string;
  payableAmount: number;
  paidAmount: number;
  overdueAmount: number;
  items: BillItem[];
};

export type UserOrderCreateResult = {
  order: RentalOrder;
  bills: RentalBill[];
};

export type Agreement = {
  id: number;
  agreementNo?: string | null;
  externalAgreementNo: string;
  orderId?: number | null;
  agreementStatus: 'SIGNING' | 'SIGNED' | 'UNSIGNED' | 'INVALID' | 'FAILED';
  maxSingleAmount: number;
};

export type AgreementSignResult = {
  agreement: Agreement;
  signUrl: string;
};

export type FundAuthorization = {
  id: number;
  authOrderNo: string;
  orderId: number;
  userAccountId: number;
  alipayUserId: string;
  merchantId: number;
  storeId: number;
  authType: 'ALIPAY_FUND_AUTH' | 'ZHIMA_CREDIT';
  authStatus: 'CREATED' | 'AUTHORIZING' | 'AUTHORIZED' | 'FAILED' | 'CANCELLED' | 'UNFROZEN' | 'CAPTURED' | 'CLOSED';
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

export type FundAuthCreateResult = {
  authorization: FundAuthorization;
  orderStr: string;
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

export type RentalContract = {
  id: number;
  contractNo: string;
  orderId: number;
  userAccountId: number;
  contractType: 'RENTAL' | 'SALE';
  contractStatus: 'DRAFT' | 'SIGNING' | 'SIGNED' | 'ARCHIVED' | 'FAILED' | 'CANCELLED';
  signUrl?: string | null;
  archivePdfUrl?: string | null;
  renderedContent: string;
  signedAt?: string | null;
  archivedAt?: string | null;
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
  verifiedAt?: string | null;
  consumedAt?: string | null;
  exceptionReason?: string | null;
  createdAt: string;
};
