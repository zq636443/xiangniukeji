<template>
  <view class="page">
    <view class="topbar">
      <view>
        <view class="title">途派熊租车</view>
        <view class="subtitle">扫码门店、选择 SKU、完成支付与签约</view>
      </view>
      <button v-if="account" class="ghost-btn compact-btn" @tap="logout">退出</button>
    </view>

    <view v-if="account" class="panel account-panel">
      <view>
        <view class="section-title">当前账号</view>
        <view class="muted">{{ account.displayName }}</view>
      </view>
      <view class="account-meta">
        <text>{{ account.alipayUserId ? '已绑定支付宝' : '支付宝用户' }}</text>
      </view>
    </view>

    <view v-else class="panel">
      <view class="section-title">用户登录</view>
      <view class="muted">下单、查看订单、支付账单前需要先授权登录。</view>
      <button class="primary-btn" :loading="loginLoading" @tap="loginByAlipay">支付宝授权登录</button>
    </view>

    <view class="panel">
      <view class="section-title">门店核销码</view>
      <view class="field-row">
        <input v-model="storeCode" class="input" placeholder="输入或扫描门店二维码内容" />
        <button class="ghost-btn scan-btn" @tap="scanStoreCode">扫码</button>
      </view>
      <button class="primary-btn" :loading="productLoading" @tap="loadProducts">查询门店商品</button>
      <view v-if="products.length > 0" class="product-list">
        <view
          v-for="product in products"
          :key="product.id"
          class="product-item"
          :class="{ active: selectedProduct?.id === product.id }"
          @tap="selectProduct(product)"
        >
          <view class="item-main">
            <view>
              <view class="item-title">{{ product.displayName }}</view>
              <view class="muted">{{ product.storeName || '门店' }} / {{ saleModeText(product.saleMode) }}</view>
            </view>
            <view class="amount">{{ money(product.signFeeAmount) }}</view>
          </view>
          <view class="tag-row">
            <text class="tag">{{ product.needFrameAsset ? '含车架' : '不含车架' }}</text>
            <text class="tag">{{ product.needBatteryAsset ? '含电池' : '不含电池' }}</text>
            <text class="tag">签单费{{ product.signFeePayer === 'USER' ? '用户付' : '商户付' }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">请输入门店码后查询可租 SKU</view>
    </view>

    <view v-if="selectedProduct" class="panel">
      <view class="section-title">选择 SKU</view>
      <view v-if="selectedProduct.packages.length === 0" class="empty">该门店商品暂未配置 SKU</view>
      <view v-for="item in selectedProduct.packages" :key="item.id" class="package-item">
        <view>
          <view class="item-title">{{ item.packageName }}</view>
          <view class="muted">
            {{ leaseText(item.leaseUnit, item.leaseValue * packageMultiplier(item)) }} / {{ item.totalPeriods * packageMultiplier(item) }} 期
          </view>
	          <view class="muted">月租统一按30天计算；首期应付含租金、签单费和押金，{{ renewalText(item) }}。</view>
        </view>
        <view class="package-side">
          <view class="amount">{{ money(item.rentalAmount * packageMultiplier(item)) }}</view>
          <view class="multiplier-row">
            <text>租期倍数</text>
            <input
              class="multiplier-input"
              type="number"
              :value="packageMultiplier(item)"
              @input="onPackageMultiplierInput(item, $event)"
            />
          </view>
          <button class="mini-primary" :loading="createLoading" @tap.stop="createOrder(item)">下单</button>
          <button class="mini-ghost" @tap.stop="selectVoucherPackage(item)">核销</button>
        </view>
      </view>
    </view>

    <view v-if="selectedProduct && voucherPackage" class="panel">
      <view class="section-title">平台核销</view>
      <view class="muted">抖音、美团、闲鱼等外部平台已付商品主款时，先验码生成签单费账单，签单费支付成功后再完成平台核销。</view>
      <view class="field-stack">
        <picker :range="voucherPlatformLabels" :value="voucherPlatformIndex" @change="onVoucherPlatformChange">
          <view class="picker">{{ voucherPlatformLabels[voucherPlatformIndex] }}</view>
        </picker>
        <view class="field-row no-margin">
          <input v-model="voucherCode" class="input" placeholder="输入或扫描抖音/美团/闲鱼核销码" />
          <button class="ghost-btn scan-btn" @tap="scanVoucherCode">扫码</button>
        </view>
        <input v-model="voucherVerificationAmount" type="digit" class="input" placeholder="实际核销金额，可稍后由门店补录" />
      </view>
      <view class="action-row">
        <button class="ghost-btn flex-btn" :loading="voucherLoading" @tap="prepareVoucher">核销准备</button>
        <button class="ghost-btn flex-btn" :disabled="!currentVoucher || !!currentVoucher.orderId" :loading="voucherLoading" @tap="saveVoucherVerificationAmount">保存金额</button>
        <button class="ghost-btn flex-btn" :disabled="!currentVoucher" :loading="voucherLoading" @tap="verifyVoucher">确认验码</button>
        <button class="primary-btn flex-btn" :disabled="!currentVoucher" :loading="voucherLoading" @tap="consumeVoucher">支付后核销</button>
      </view>
      <view v-if="currentVoucher" class="auth-item">
        <view class="item-main">
          <view>
            <view class="item-title">{{ currentVoucher.voucherTitle || currentVoucher.voucherCode }}</view>
            <view class="muted">{{ platformText(currentVoucher.sourcePlatform) }} / {{ voucherStatusText(currentVoucher.verifyStatus) }}</view>
            <view class="muted">参考金额 {{ money(currentVoucher.voucherAmount) }} / 实际核销 {{ currentVoucher.verificationAmount == null ? '待补录' : money(currentVoucher.verificationAmount) }}</view>
            <view class="muted">签单费 {{ money(currentVoucher.signFeeAmount) }}</view>
          </view>
          <view class="amount small">{{ currentVoucher.orderId ? `订单 ${currentVoucher.orderId}` : '待生成' }}</view>
        </view>
      </view>
    </view>

    <view v-if="currentOrder" class="panel">
      <view class="section-title">当前订单</view>
      <view class="info-grid">
        <view>
          <text class="label">订单号</text>
          <text>{{ currentOrder.orderNo }}</text>
        </view>
        <view>
          <text class="label">状态</text>
          <text>{{ orderStatusText(currentOrder.orderStatus) }}</text>
        </view>
        <view>
          <text class="label">应付</text>
          <text>{{ money(currentOrder.payableAmount) }}</text>
        </view>
        <view>
          <text class="label">已付</text>
          <text>{{ money(currentOrder.paidAmount) }}</text>
        </view>
        <view>
          <text class="label">赠送租期</text>
          <text>{{ currentOrder.totalBonusDays }} 天</text>
        </view>
        <view>
          <text class="label">预计归还</text>
          <text>{{ dateText(currentOrder.expectedReturnAt) }}</text>
        </view>
      </view>
      <view v-if="currentOrder.totalBonusDays > 0" class="muted">
        好评赠送 {{ currentOrder.reviewBonusDays }} 天 / 活动赠送 {{ currentOrder.campaignBonusDays }} 天
      </view>
      <view class="action-row">
        <button class="primary-btn flex-btn" :disabled="!firstPayableBill" :loading="payLoading" @tap="payFirstBill">
          支付待付账单
        </button>
        <button class="ghost-btn flex-btn" :loading="agreementLoading" @tap="signAgreement">签约代扣</button>
      </view>
      <view class="action-row">
        <button class="ghost-btn flex-btn" :loading="fundAuthLoading" @tap="createFundAuth">押金/逾期授权</button>
      </view>
      <view v-if="fundAuths.length > 0" class="auth-list">
        <view v-for="item in fundAuths" :key="item.id" class="auth-item">
          <view class="item-main">
            <view>
              <view class="item-title">{{ item.authOrderNo }}</view>
              <view class="muted">{{ fundAuthStatusText(item.authStatus) }} / 授权 {{ money(item.authAmount) }}</view>
            </view>
            <view class="amount small">{{ money(Number(item.frozenAmount) - Number(item.capturedAmount) - Number(item.releasedAmount)) }}</view>
          </view>
        </view>
      </view>
      <view v-if="agreementUrl" class="sign-box">
        <view class="muted">签约链接已生成</view>
        <view class="link-text">{{ agreementUrl }}</view>
        <button class="ghost-btn" @tap="openAgreementUrl">打开签约</button>
      </view>
      <view class="compliance-box">
        <view class="section-title small-title">实名与合同</view>
        <view class="field-stack">
          <input v-model="identityForm.frontImageUrl" class="input" placeholder="身份证正面图片 URL" />
          <input v-model="identityForm.backImageUrl" class="input" placeholder="身份证反面图片 URL" />
          <input v-model="identityForm.realName" class="input" placeholder="姓名" />
          <input v-model="identityForm.idNo" class="input" placeholder="身份证号" />
        </view>
        <view class="action-row">
          <button class="ghost-btn flex-btn" :loading="identityLoading" @tap="submitIdentityImages">提交证件</button>
          <button class="ghost-btn flex-btn" :loading="identityLoading" @tap="confirmIdentity">确认实名</button>
        </view>
        <view v-if="identities.length > 0" class="muted">实名状态：{{ identityStatusText(identities[0].realNameStatus) }} / {{ identities[0].realNameMasked || '-' }}</view>
        <view v-if="contracts.length > 0" class="contract-list">
          <view v-for="contract in contracts" :key="contract.id" class="auth-item">
            <view class="item-main">
              <view>
                <view class="item-title">{{ contract.contractNo }}</view>
                <view class="muted">{{ contractStatusText(contract.contractStatus) }}</view>
                <view v-if="contract.signUrl" class="link-text compact-link">{{ contract.signUrl }}</view>
              </view>
              <view class="contract-actions">
                <button v-if="contract.signUrl" class="mini-ghost" @tap.stop="openContractSignUrl(contract.signUrl)">打开</button>
                <button v-if="contract.contractStatus !== 'SIGNED' && contract.contractStatus !== 'ARCHIVED'" class="mini-primary" @tap.stop="confirmContractSigned(contract.id)">确认</button>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-header">
        <view class="section-title">我的订单</view>
        <button class="ghost-btn compact-btn" :loading="orderLoading" @tap="loadOrders">刷新</button>
      </view>
      <view v-if="orders.length === 0" class="empty">暂无订单</view>
      <view
        v-for="order in orders"
        :key="order.id"
        class="list-item"
        :class="{ active: currentOrder?.id === order.id }"
        @tap="selectOrder(order)"
      >
        <view>
          <view class="item-title">{{ order.orderNo }}</view>
          <view class="muted">{{ order.storeName || '门店' }} / {{ order.storeSkuName || '商品' }}</view>
          <view class="muted">{{ leaseText(order.leaseUnit, order.leaseValue) }}<template v-if="order.totalBonusDays > 0"> + 赠送 {{ order.totalBonusDays }} 天</template> / 车架 {{ order.frameSerialNo || order.frameAssetCode || '-' }}</view>
        </view>
        <view class="right-text">
          <view>{{ orderStatusText(order.orderStatus) }}</view>
          <view class="amount small">{{ money(order.payableAmount) }}</view>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-header">
        <view class="section-title">{{ currentOrder ? '订单账单' : '我的账单' }}</view>
        <button class="ghost-btn compact-btn" :loading="billLoading" @tap="loadBillsForCurrent">刷新</button>
      </view>
      <view v-if="currentBills.length === 0" class="empty">暂无账单</view>
      <view v-for="bill in currentBills" :key="bill.id" class="bill-item">
        <view class="item-main">
          <view>
            <view class="item-title">{{ bill.billNo }}</view>
            <view class="muted">{{ billTypeText(bill.billType) }} / 第 {{ bill.periodNo }} 期 / {{ dateText(bill.dueAt) }}</view>
          </view>
          <view class="right-text">
            <view>{{ billStatusText(bill.billStatus) }}</view>
            <view class="amount small">{{ money(bill.payableAmount) }}</view>
          </view>
        </view>
        <view class="bill-lines">
          <text v-for="line in bill.items" :key="line.id">{{ line.itemName }} {{ money(line.amount) }}</text>
        </view>
        <button
          v-if="canPayBill(bill)"
          class="mini-primary"
          :loading="payLoading"
          @tap.stop="payBill(bill)"
        >
          支付本账单
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { request } from '../../services/request';
import type {
  AgreementSignResult,
  AlipayTradeCreateResult,
  CurrentAccount,
  FundAuthorization,
  FundAuthCreateResult,
  IdentityVerification,
  LoginResponse,
  OrderStatus,
  RentalBill,
  RentalContract,
  RentalOrder,
  StoreSku,
  StoreSkuPackage,
  UserOrderCreateResult,
  VoucherRecord
} from '../../types/api';

const account = ref<CurrentAccount | null>(null);
const storeCode = ref('');
const products = ref<StoreSku[]>([]);
const selectedProduct = ref<StoreSku | null>(null);
const currentOrder = ref<RentalOrder | null>(null);
const currentBills = ref<RentalBill[]>([]);
const orders = ref<RentalOrder[]>([]);
const fundAuths = ref<FundAuthorization[]>([]);
const identities = ref<IdentityVerification[]>([]);
const contracts = ref<RentalContract[]>([]);
const voucherPackage = ref<StoreSkuPackage | null>(null);
const currentVoucher = ref<VoucherRecord | null>(null);
const agreementUrl = ref('');
const voucherCode = ref('');
const voucherVerificationAmount = ref('');
const leaseMultipliers = reactive<Record<number, number>>({});
const voucherPlatformIndex = ref(0);
const voucherPlatformLabels = ['抖音券码', '美团券码', '闲鱼核销码'];
const voucherPlatforms: VoucherRecord['sourcePlatform'][] = ['DOUYIN', 'MEITUAN', 'XIANYU'];
const identityForm = reactive({
  frontImageUrl: '',
  backImageUrl: '',
  realName: '',
  idNo: ''
});

const loginLoading = ref(false);
const productLoading = ref(false);
const createLoading = ref(false);
const orderLoading = ref(false);
const billLoading = ref(false);
const payLoading = ref(false);
const agreementLoading = ref(false);
const fundAuthLoading = ref(false);
const identityLoading = ref(false);
const voucherLoading = ref(false);

const firstPayableBill = computed(() => currentBills.value.find((bill) => canPayBill(bill)) || null);

onMounted(async () => {
  const token = uni.getStorageSync('xniu_user_token');
  if (!token) {
    return;
  }
  try {
    account.value = await request<CurrentAccount>('/api/auth/me');
    await Promise.all([loadOrders(), loadBills()]);
  } catch {
    uni.removeStorageSync('xniu_user_token');
  }
});

async function loginByAlipay() {
  loginLoading.value = true;
  try {
    const authCode = await getAlipayAuthCode();
    const result = await request<LoginResponse>('/api/auth/alipay/login', {
      method: 'POST',
      data: {
        authCode,
        nickName: '支付宝用户'
      }
    });
    uni.setStorageSync('xniu_user_token', result.token);
    account.value = result.account;
    await Promise.all([loadOrders(), loadBills()]);
    uni.showToast({ title: '登录成功', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '登录失败'), icon: 'none' });
  } finally {
    loginLoading.value = false;
  }
}

function logout() {
  uni.removeStorageSync('xniu_user_token');
  account.value = null;
  currentOrder.value = null;
  currentBills.value = [];
  orders.value = [];
  agreementUrl.value = '';
}

async function scanStoreCode() {
  try {
    const result = await scanCode();
    storeCode.value = normalizeStoreCode(result);
    await loadProducts();
  } catch (error) {
    uni.showToast({ title: errorText(error, '扫码失败'), icon: 'none' });
  }
}

async function loadProducts() {
  const code = normalizeStoreCode(storeCode.value);
  if (!code) {
    uni.showToast({ title: '请输入门店码', icon: 'none' });
    return;
  }
  productLoading.value = true;
  try {
    products.value = await request<StoreSku[]>(`/api/user/products/stores/${encodeURIComponent(code)}`);
    selectedProduct.value = products.value[0] || null;
    storeCode.value = code;
    uni.showToast({ title: products.value.length > 0 ? '已加载' : '暂无商品', icon: 'none' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '查询失败'), icon: 'none' });
  } finally {
    productLoading.value = false;
  }
}

function selectProduct(product: StoreSku) {
  selectedProduct.value = product;
  voucherPackage.value = null;
  currentVoucher.value = null;
  voucherVerificationAmount.value = '';
}

function selectVoucherPackage(item: StoreSkuPackage) {
  voucherPackage.value = item;
  currentVoucher.value = null;
  voucherVerificationAmount.value = '';
  uni.showToast({ title: '已选择核销 SKU', icon: 'none' });
}

function packageMultiplier(item: StoreSkuPackage) {
  return leaseMultipliers[item.packageId] || 1;
}

function onPackageMultiplierInput(item: StoreSkuPackage, event: unknown) {
  const value = (event as { detail?: { value?: string | number } }).detail?.value;
  const rawValue = Math.trunc(Number(value || 1));
  leaseMultipliers[item.packageId] = Math.min(120, Math.max(1, Number.isFinite(rawValue) ? rawValue : 1));
}

async function createOrder(item: StoreSkuPackage) {
  if (!account.value) {
    uni.showToast({ title: '请先登录', icon: 'none' });
    return;
  }
  if (!selectedProduct.value) {
    uni.showToast({ title: '请选择商品', icon: 'none' });
    return;
  }
  createLoading.value = true;
  try {
    const result = await request<UserOrderCreateResult>('/api/user/orders', {
      method: 'POST',
      data: {
        customerName: account.value.displayName,
        customerPhone: account.value.phone || undefined,
        storeSkuId: selectedProduct.value.id,
        packageId: item.packageId,
        leaseMultiplier: packageMultiplier(item)
      }
    });
    currentOrder.value = result.order;
    currentBills.value = result.bills;
    await loadFundAuths(result.order.id);
    agreementUrl.value = '';
    await loadOrders();
    uni.showToast({ title: '订单已创建', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '下单失败'), icon: 'none' });
  } finally {
    createLoading.value = false;
  }
}

async function scanVoucherCode() {
  try {
    voucherCode.value = await scanCode();
  } catch (error) {
    uni.showToast({ title: errorText(error, '扫码失败'), icon: 'none' });
  }
}

async function prepareVoucher() {
  if (!selectedProduct.value || !voucherPackage.value) {
    uni.showToast({ title: '请选择核销 SKU', icon: 'none' });
    return;
  }
  if (!voucherCode.value.trim()) {
    uni.showToast({ title: '请输入券码', icon: 'none' });
    return;
  }
  voucherLoading.value = true;
  try {
    currentVoucher.value = await request<VoucherRecord>('/api/user/vouchers/prepare', {
      method: 'POST',
      data: {
        sourcePlatform: voucherPlatforms[voucherPlatformIndex.value],
        voucherCode: voucherCode.value.trim(),
        storeSkuId: selectedProduct.value.id,
        packageId: voucherPackage.value.packageId,
        verificationAmount: optionalAmount(voucherVerificationAmount.value)
      }
    });
    voucherVerificationAmount.value = currentVoucher.value.verificationAmount == null
      ? ''
      : String(currentVoucher.value.verificationAmount);
    uni.showToast({ title: '核销准备成功', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '核销准备失败'), icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function saveVoucherVerificationAmount() {
  if (!currentVoucher.value) {
    uni.showToast({ title: '请先完成核销准备', icon: 'none' });
    return;
  }
  const amount = requiredAmount(voucherVerificationAmount.value);
  if (amount == null) return;
  voucherLoading.value = true;
  try {
    currentVoucher.value = await request<VoucherRecord>(`/api/user/vouchers/${currentVoucher.value.id}/verification-amount`, {
      method: 'POST',
      data: { verificationAmount: amount }
    });
    uni.showToast({ title: '核销金额已保存', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '核销金额保存失败'), icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function verifyVoucher() {
  if (!currentVoucher.value) {
    uni.showToast({ title: '请先完成核销准备', icon: 'none' });
    return;
  }
  voucherLoading.value = true;
  try {
    if (!currentVoucher.value.orderId && voucherVerificationAmount.value.trim()) {
      const amount = requiredAmount(voucherVerificationAmount.value);
      if (amount == null) return;
      currentVoucher.value = await request<VoucherRecord>(`/api/user/vouchers/${currentVoucher.value.id}/verification-amount`, {
        method: 'POST',
        data: { verificationAmount: amount }
      });
    }
    currentVoucher.value = await request<VoucherRecord>(`/api/user/vouchers/${currentVoucher.value.id}/verify`, { method: 'POST' });
    if (currentVoucher.value.orderId) {
      const order = await request<RentalOrder>(`/api/user/orders/${currentVoucher.value.orderId}`);
      await selectOrder(order, false);
    }
    uni.showToast({ title: '已生成签单费订单', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '确认验码失败'), icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function consumeVoucher() {
  if (!currentVoucher.value) {
    uni.showToast({ title: '请先验码', icon: 'none' });
    return;
  }
  voucherLoading.value = true;
  try {
    currentVoucher.value = await request<VoucherRecord>(`/api/user/vouchers/${currentVoucher.value.id}/consume`, { method: 'POST' });
    await loadOrders();
    if (currentVoucher.value.orderId) {
      const order = await request<RentalOrder>(`/api/user/orders/${currentVoucher.value.orderId}`);
      await selectOrder(order, false);
    }
    uni.showToast({ title: '核销成功', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '核销失败'), icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function loadOrders() {
  if (!account.value) {
    return;
  }
  orderLoading.value = true;
  try {
    orders.value = await request<RentalOrder[]>('/api/user/orders');
    if (!currentOrder.value && orders.value.length > 0) {
      await selectOrder(orders.value[0], false);
    }
  } catch (error) {
    uni.showToast({ title: errorText(error, '订单加载失败'), icon: 'none' });
  } finally {
    orderLoading.value = false;
  }
}

async function selectOrder(order: RentalOrder, toast = true) {
  currentOrder.value = order;
  agreementUrl.value = '';
  await Promise.all([loadBills(order.id), loadFundAuths(order.id), loadIdentities(order.id), loadContracts(order.id)]);
  if (toast) {
    uni.showToast({ title: '已切换订单', icon: 'none' });
  }
}

async function loadBillsForCurrent() {
  await loadBills(currentOrder.value?.id);
}

async function loadBills(orderId?: number) {
  if (!account.value) {
    return;
  }
  billLoading.value = true;
  try {
    const query = orderId ? `?orderId=${orderId}` : '';
    currentBills.value = await request<RentalBill[]>(`/api/user/bills${query}`);
  } catch (error) {
    uni.showToast({ title: errorText(error, '账单加载失败'), icon: 'none' });
  } finally {
    billLoading.value = false;
  }
}

async function loadFundAuths(orderId: number) {
  if (!account.value) {
    return;
  }
  fundAuths.value = await request<FundAuthorization[]>(`/api/user/fund-auths?orderId=${orderId}`);
}

async function loadIdentities(orderId: number) {
  identities.value = await request<IdentityVerification[]>(`/api/user/identities?orderId=${orderId}`);
}

async function loadContracts(orderId: number) {
  contracts.value = await request<RentalContract[]>(`/api/user/contracts?orderId=${orderId}`);
}

async function payFirstBill() {
  if (!firstPayableBill.value) {
    uni.showToast({ title: '暂无待付账单', icon: 'none' });
    return;
  }
  await payBill(firstPayableBill.value);
}

async function payBill(bill: RentalBill) {
  if (!canPayBill(bill)) {
    uni.showToast({ title: '账单无需支付', icon: 'none' });
    return;
  }
  payLoading.value = true;
  try {
    const result = await request<AlipayTradeCreateResult>('/api/user/payments/alipay-trade', {
      method: 'POST',
      data: { billId: bill.id }
    });
    await tradePay(result.tradeNo);
    await refreshAfterPay(bill.orderId);
    uni.showToast({ title: '支付已提交', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '支付失败'), icon: 'none' });
  } finally {
    payLoading.value = false;
  }
}

async function signAgreement() {
  if (!currentOrder.value) {
    uni.showToast({ title: '请选择订单', icon: 'none' });
    return;
  }
  agreementLoading.value = true;
  try {
    const maxSingleAmount = Math.max(Number(currentOrder.value.payableAmount || 0), 1);
    const result = await request<AgreementSignResult>('/api/user/agreements/sign', {
      method: 'POST',
      data: {
        orderId: currentOrder.value.id,
        maxSingleAmount
      }
    });
    agreementUrl.value = result.signUrl;
    await openAgreementUrl();
    uni.showToast({ title: '签约已发起', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '签约失败'), icon: 'none' });
  } finally {
    agreementLoading.value = false;
  }
}

async function createFundAuth() {
  if (!currentOrder.value) {
    uni.showToast({ title: '请选择订单', icon: 'none' });
    return;
  }
  const authAmount = Math.max(Number(currentOrder.value.depositAmount || 0), Number(currentOrder.value.rentalAmount || 0) / Math.max(currentOrder.value.totalPeriods, 1), 1);
  fundAuthLoading.value = true;
  try {
    const result = await request<FundAuthCreateResult>('/api/user/fund-auths', {
      method: 'POST',
      data: {
        orderId: currentOrder.value.id,
        authAmount
      }
    });
    await tradePayOrderStr(result.orderStr);
    await loadFundAuths(currentOrder.value.id);
    uni.showToast({ title: '授权已发起', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '授权失败'), icon: 'none' });
  } finally {
    fundAuthLoading.value = false;
  }
}

async function submitIdentityImages() {
  if (!currentOrder.value) {
    uni.showToast({ title: '请选择订单', icon: 'none' });
    return;
  }
  if (!identityForm.frontImageUrl || !identityForm.backImageUrl) {
    uni.showToast({ title: '请输入证件图片 URL', icon: 'none' });
    return;
  }
  identityLoading.value = true;
  try {
    await request<IdentityVerification>('/api/user/identities/images', {
      method: 'POST',
      data: {
        orderId: currentOrder.value.id,
        frontImageUrl: identityForm.frontImageUrl,
        backImageUrl: identityForm.backImageUrl
      }
    });
    await loadIdentities(currentOrder.value.id);
    uni.showToast({ title: '证件已提交', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '提交失败'), icon: 'none' });
  } finally {
    identityLoading.value = false;
  }
}

async function confirmIdentity() {
  if (!currentOrder.value || identities.value.length === 0) {
    uni.showToast({ title: '请先提交证件', icon: 'none' });
    return;
  }
  if (!identityForm.realName || !identityForm.idNo) {
    uni.showToast({ title: '请输入姓名和身份证号', icon: 'none' });
    return;
  }
  identityLoading.value = true;
  try {
    await request<IdentityVerification>(`/api/user/identities/${identities.value[0].id}/confirm`, {
      method: 'POST',
      data: {
        realName: identityForm.realName,
        idNo: identityForm.idNo
      }
    });
    await loadIdentities(currentOrder.value.id);
    uni.showToast({ title: '实名已确认', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: errorText(error, '实名失败'), icon: 'none' });
  } finally {
    identityLoading.value = false;
  }
}

async function confirmContractSigned(contractId: number) {
  if (!currentOrder.value) return;
  await request<RentalContract>(`/api/user/contracts/${contractId}/confirm-signed`, { method: 'POST' });
  await loadContracts(currentOrder.value.id);
  uni.showToast({ title: '合同已签署', icon: 'success' });
}

async function openContractSignUrl(signUrl: string) {
  await openAlipayUrl(signUrl);
}

async function openAgreementUrl() {
  if (!agreementUrl.value) {
    return;
  }
  await openAlipayUrl(agreementUrl.value);
}

async function openAlipayUrl(path: string) {
  const alipayRuntime = globalThis as unknown as {
    my?: {
      ap?: {
        navigateToAlipayPage?: (options: { path: string; success?: () => void; fail?: (error: unknown) => void }) => void;
      };
    };
  };
  if (alipayRuntime.my?.ap?.navigateToAlipayPage) {
    await new Promise<void>((resolve, reject) => {
      alipayRuntime.my?.ap?.navigateToAlipayPage?.({
        path,
        success: resolve,
        fail: reject
      });
    });
    return;
  }
  uni.setClipboardData({ data: path });
}

async function refreshAfterPay(orderId: number) {
  await Promise.all([loadOrders(), loadBills(orderId)]);
  if (currentOrder.value) {
    const latest = orders.value.find((item) => item.id === currentOrder.value?.id);
    if (latest) {
      currentOrder.value = latest;
    }
  }
}

function canPayBill(bill: RentalBill) {
  return !['PAID', 'CANCELLED'].includes(bill.billStatus);
}

function getAlipayAuthCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'alipay' as UniApp.LoginOptions['provider'],
      success: (result) => {
        if (result.code) {
          resolve(result.code);
          return;
        }
        reject(new Error('未获取到支付宝授权码'));
      },
      fail: reject
    });
  });
}

function scanCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      success: (result) => {
        resolve(result.result || '');
      },
      fail: reject
    });
  });
}

function onVoucherPlatformChange(event: { detail: { value: number } }) {
  voucherPlatformIndex.value = Number(event.detail.value);
}

function tradePay(tradeNo: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const alipayRuntime = globalThis as unknown as {
      my?: { tradePay?: (options: { tradeNO: string; success: () => void; fail: (error: unknown) => void }) => void };
    };
    if (!alipayRuntime.my?.tradePay) {
      resolve();
      return;
    }
    alipayRuntime.my.tradePay({ tradeNO: tradeNo, success: resolve, fail: reject });
  });
}

function tradePayOrderStr(orderStr: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const alipayRuntime = globalThis as unknown as {
      my?: { tradePay?: (options: { orderStr: string; success: () => void; fail: (error: unknown) => void }) => void };
    };
    if (!alipayRuntime.my?.tradePay) {
      resolve();
      return;
    }
    alipayRuntime.my.tradePay({ orderStr, success: resolve, fail: reject });
  });
}

function normalizeStoreCode(value: string) {
  const text = (value || '').trim();
  if (!text) {
    return '';
  }
  try {
    const url = new URL(text);
    const queryCode = url.searchParams.get('storeCode') || url.searchParams.get('code');
    if (queryCode) {
      return queryCode;
    }
    if (url.protocol === 'xniu:' && url.hostname === 'store') {
      return decodeURIComponent(url.pathname.replace(/^\/+/, ''));
    }
    return text;
  } catch {
    return text;
  }
}

function money(value: number | string | null | undefined) {
  const amount = Number(value || 0);
  return `¥${amount.toFixed(2)}`;
}

function optionalAmount(value: string) {
  const normalized = value.trim();
  return normalized ? Number(normalized) : undefined;
}

function requiredAmount(value: string) {
  const normalized = value.trim();
  const amount = Number(normalized);
  if (!normalized || !Number.isFinite(amount) || amount < 0) {
    uni.showToast({ title: '请输入正确的核销金额', icon: 'none' });
    return null;
  }
  return amount;
}

function dateText(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function leaseText(unit: 'DAY' | 'MONTH', value: number) {
  return `${value}${unit === 'DAY' ? '天' : '个月'}`;
}

function renewalText(item: { autoRenewEnabled?: boolean; renewalUnit?: 'DAY' | 'MONTH' | null; renewalValue?: number | null; renewalAmount?: number | null }) {
  if (!item.autoRenewEnabled) {
    return '到期未还不自动续租';
  }
  return `到期未还按 ${money(item.renewalAmount || 0)} / ${leaseText(item.renewalUnit || 'MONTH', item.renewalValue || 1)} 自动续租`;
}

function saleModeText(value: string) {
  return value === 'SALE' ? '售卖' : '租赁';
}

function platformText(value: VoucherRecord['sourcePlatform']) {
  const map: Record<VoucherRecord['sourcePlatform'], string> = {
    DOUYIN: '抖音',
    MEITUAN: '美团',
    XIANYU: '闲鱼'
  };
  return map[value];
}

function voucherStatusText(value: VoucherRecord['verifyStatus']) {
  const map: Record<VoucherRecord['verifyStatus'], string> = {
    INPUT: '已录入',
    PREPARED: '核销准备成功',
    VERIFIED: '已验码',
    WAITING_SIGN_FEE: '待支付签单费',
    CONSUMING: '核销中',
    CONSUMED: '已核销',
    FAILED: '核销失败',
    EXCEPTION: '异常'
  };
  return map[value] || value;
}

function billTypeText(value: string) {
	  const map: Record<string, string> = {
	    INITIAL: '首期账单',
	    PERIODIC: '周期账单',
	    RENEWAL: '续租账单',
	    OVERDUE: '逾期账单'
	  };
  return map[value] || value;
}

function billStatusText(value: string) {
  const map: Record<string, string> = {
    PENDING_PAYMENT: '待支付',
    PAYING: '支付中',
    PAID: '已支付',
    OVERDUE: '已逾期',
    CANCELLED: '已取消',
    FAILED: '扣款失败'
  };
  return map[value] || value;
}

function fundAuthStatusText(value: FundAuthorization['authStatus']) {
  const map: Record<FundAuthorization['authStatus'], string> = {
    CREATED: '已创建',
    AUTHORIZING: '授权中',
    AUTHORIZED: '已授权',
    FAILED: '授权失败',
    CANCELLED: '已撤销',
    UNFROZEN: '已解冻',
    CAPTURED: '已扣费',
    CLOSED: '已关闭'
  };
  return map[value] || value;
}

function identityStatusText(value: IdentityVerification['realNameStatus']) {
  return value === 'VERIFIED' ? '已实名' : value === 'FAILED' ? '实名失败' : '待实名';
}

function contractStatusText(value: RentalContract['contractStatus']) {
  const map: Record<RentalContract['contractStatus'], string> = {
    DRAFT: '待发起',
    SIGNING: '签署中',
    SIGNED: '已签署',
    ARCHIVED: '已归档',
    FAILED: '失败',
    CANCELLED: '已取消'
  };
  return map[value] || value;
}

function orderStatusText(value: OrderStatus) {
  const map: Record<OrderStatus, string> = {
    PENDING_PAYMENT: '待支付',
    PENDING_REAL_NAME: '待实名',
    PENDING_AGREEMENT: '待签约',
    PENDING_DEPOSIT_AUTH: '待押金授权',
    PENDING_VERIFY: '待审核',
    PENDING_PICKUP: '待取车',
    RENTING: '租赁中',
    PENDING_RETURN: '待归还',
    OVERDUE: '已逾期',
    PENDING_SUPPLEMENT: '待补缴',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    EXCEPTION: '异常'
  };
  return map[value] || value;
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.topbar,
.section-header,
.item-main,
.account-panel,
.package-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.topbar {
  margin-bottom: 20rpx;
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: #172033;
}

.subtitle,
.muted {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #667085;
  line-height: 1.45;
}

.panel {
  margin-bottom: 20rpx;
  padding: 24rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 8rpx;
  background: #ffffff;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #182230;
}

.small-title {
  font-size: 26rpx;
}

.account-meta,
.tag,
.right-text {
  font-size: 24rpx;
  color: #475467;
}

.field-row {
  display: flex;
  gap: 16rpx;
  margin: 22rpx 0 16rpx;
}

.field-stack {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 16rpx;
}

.no-margin {
  margin: 0;
}

.picker {
  height: 76rpx;
  padding: 0 20rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  background: #ffffff;
  color: #344054;
  font-size: 26rpx;
  line-height: 76rpx;
  box-sizing: border-box;
}

.input {
  flex: 1;
  height: 76rpx;
  padding: 0 20rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  background: #ffffff;
  font-size: 26rpx;
  box-sizing: border-box;
}

button {
  margin: 0;
  border-radius: 8rpx;
  font-size: 26rpx;
  line-height: 76rpx;
}

button::after {
  border: 0;
}

.primary-btn,
.mini-primary {
  height: 76rpx;
  background: #1677ff;
  color: #ffffff;
}

.ghost-btn {
  height: 76rpx;
  border: 1rpx solid #d0d5dd;
  background: #ffffff;
  color: #344054;
}

.compact-btn {
  min-width: 118rpx;
  height: 64rpx;
  line-height: 64rpx;
}

.scan-btn {
  width: 136rpx;
}

.flex-btn {
  flex: 1;
}

.mini-primary {
  width: 128rpx;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 24rpx;
}

.mini-ghost {
  width: 112rpx;
  height: 64rpx;
  line-height: 64rpx;
  border: 1rpx solid #d0d5dd;
  background: #ffffff;
  color: #344054;
  font-size: 24rpx;
}

.product-list {
  margin-top: 20rpx;
}

.product-item,
.list-item,
.bill-item,
.auth-item,
.package-item {
  padding: 20rpx 0;
  border-top: 1rpx solid #eef2f6;
}

.product-item:first-child,
.list-item:first-child,
.bill-item:first-child,
.auth-item:first-child,
.package-item:first-child {
  border-top: 0;
}

.auth-list {
  margin-top: 16rpx;
}

.product-item.active,
.list-item.active {
  margin: 0 -12rpx;
  padding-left: 12rpx;
  padding-right: 12rpx;
  border-radius: 8rpx;
  background: #f4f8ff;
}

.item-title {
  font-size: 28rpx;
  font-weight: 650;
  color: #1d2939;
}

.amount {
  flex-shrink: 0;
  font-size: 30rpx;
  font-weight: 700;
  color: #b42318;
}

.amount.small {
  margin-top: 8rpx;
  font-size: 24rpx;
}

.tag-row,
.action-row,
.bill-lines {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.tag {
  padding: 6rpx 12rpx;
  border-radius: 8rpx;
  background: #f2f4f7;
}

.package-side {
  min-width: 140rpx;
  text-align: right;
}

.multiplier-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  color: #667085;
  font-size: 22rpx;
}

.multiplier-input {
  width: 84rpx;
  height: 52rpx;
  padding: 0 10rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 10rpx;
  text-align: center;
  background: #fff;
}

.contract-actions {
  display: flex;
  flex-shrink: 0;
  gap: 10rpx;
}

.compact-link {
  max-width: 420rpx;
  word-break: break-all;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18rpx;
  margin-top: 18rpx;
}

.info-grid view {
  padding: 16rpx;
  border-radius: 8rpx;
  background: #f8fafc;
  font-size: 26rpx;
}

.label {
  display: block;
  margin-bottom: 8rpx;
  font-size: 22rpx;
  color: #667085;
}

.sign-box {
  margin-top: 18rpx;
  padding: 18rpx;
  border-radius: 8rpx;
  background: #f8fafc;
}

.compliance-box {
  margin-top: 18rpx;
  padding: 18rpx;
  border-radius: 8rpx;
  background: #f8fafc;
}

.contract-list {
  margin-top: 12rpx;
}

.link-text {
  margin: 12rpx 0;
  font-size: 22rpx;
  color: #344054;
  word-break: break-all;
}

.bill-lines text {
  padding: 6rpx 10rpx;
  border-radius: 8rpx;
  background: #f9fafb;
  font-size: 22rpx;
  color: #667085;
}

.empty {
  padding: 30rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #98a2b3;
}
</style>
