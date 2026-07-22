<template>
  <view class="page">
    <view class="title">商户工作台</view>
    <view class="subtitle">订单、取还车、资产更换、配件维保、分润和逾期处理</view>

    <view v-if="account" class="panel">
      <view class="row">
        <text class="label">账号</text>
        <text>{{ account.displayName }}</text>
      </view>
      <view class="row">
        <text class="label">角色</text>
        <text>{{ account.roles.join('、') }}</text>
      </view>
      <view class="row">
        <text class="label">门店范围</text>
        <text>{{ scopeText }}</text>
      </view>
      <view class="store-list">
        <view
          v-for="store in stores"
          :key="store.id"
          class="store-item"
          :class="{ active: currentStoreId === store.id }"
          @tap="selectStore(store.id)"
        >
          <view class="store-name">{{ store.storeName }}</view>
          <view class="store-address">{{ store.address }}</view>
          <view class="store-code">{{ store.qrContent }}</view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">门店订单</view>
        <view class="filter-row">
          <picker :range="orderStatusLabels" :value="orderStatusIndex" @change="onOrderStatusChange">
            <view class="picker compact-picker">{{ orderStatusLabels[orderStatusIndex] }}</view>
          </picker>
          <input v-model="orderKeyword" class="filter-input" placeholder="搜索订单、客户、电话或资产编号" />
          <button class="mini-btn" :loading="orderLoading" @tap="loadOrders">刷新</button>
        </view>
        <view v-if="canCreateOrder" class="action-row order-create-action">
          <button class="mini-btn" :disabled="storeSkus.length === 0" @tap="toggleOrderCreate">
            {{ orderCreateVisible ? '收起新建订单' : '新建订单' }}
          </button>
        </view>
        <view v-if="canCreateOrder && orderCreateVisible" class="asset-item order-create-form">
          <view class="field compact">
            <text>用户账号 ID</text>
            <input v-model="orderCreateForm.userAccountId" type="number" placeholder="可不填写" />
          </view>
          <view class="field compact">
            <text>客户姓名</text>
            <input v-model="orderCreateForm.customerName" placeholder="请输入客户姓名" />
          </view>
          <view class="field compact">
            <text>联系电话</text>
            <input v-model="orderCreateForm.customerPhone" type="number" placeholder="请输入联系电话" />
          </view>
          <view class="field compact">
            <text>门店商品</text>
            <picker :range="storeSkuLabels" :value="orderCreateForm.storeSkuIndex" @change="onOrderStoreSkuChange">
              <view class="picker">{{ storeSkuLabels[orderCreateForm.storeSkuIndex] || '请选择门店商品' }}</view>
            </picker>
          </view>
          <view class="field compact">
            <text>租赁 SKU</text>
            <picker :range="orderPackageLabels" :value="orderCreateForm.packageIndex" @change="onOrderPackageChange">
              <view class="picker">{{ orderPackageLabels[orderCreateForm.packageIndex] || '请选择租赁 SKU' }}</view>
            </picker>
          </view>
          <view class="field compact">
            <text>实际核销金额</text>
            <input v-model="orderCreateForm.verificationAmount" type="digit" placeholder="请输入实际核销金额" />
          </view>
          <view class="field compact">
            <text>主资产（支持全部自定义类型）</text>
            <picker :range="orderFrameAssetLabels" :value="orderCreateForm.frameAssetIndex" @change="onOrderFrameAssetChange">
              <view class="picker">{{ orderFrameAssetLabels[orderCreateForm.frameAssetIndex] }}</view>
            </picker>
          </view>
          <view class="field compact">
            <text>电池资产</text>
            <picker :range="orderBatteryAssetLabels" :value="orderCreateForm.batteryAssetIndex" :disabled="orderUsesIntegratedVehicle" @change="onOrderBatteryAssetChange">
              <view class="picker">{{ orderUsesIntegratedVehicle ? '车电一体无需独立电池' : orderBatteryAssetLabels[orderCreateForm.batteryAssetIndex] }}</view>
            </picker>
          </view>
          <button class="primary" :loading="orderCreateSubmitting" @tap="createMerchantOrder">创建订单</button>
        </view>
        <view v-if="filteredOrders.length === 0" class="empty">当前筛选暂无订单</view>
        <view
          v-for="order in filteredOrders"
          :key="order.id"
          class="asset-item"
          :class="{ active: selectedOrder?.id === order.id }"
          @tap="selectOrder(order)"
        >
          <view class="asset-main">
            <text>{{ order.orderNo }}</text>
            <text class="asset-status">{{ orderStatusText(order.orderStatus) }}</text>
          </view>
          <view class="asset-sub">{{ order.customerName || '未填姓名' }} / {{ order.customerPhone || '未填电话' }}</view>
          <view class="asset-sub">{{ order.storeSkuName || '商品' }} / {{ order.packageName || 'SKU' }} / {{ leaseText(order.leaseUnit, order.leaseValue) }}</view>
          <view class="asset-sub">实际核销 {{ money(order.verificationAmount) }} / 应付 {{ money(order.payableAmount) }} / 已付 {{ money(order.paidAmount) }}</view>
          <view class="asset-sub">主资产 {{ assetText(order.frameSerialNo, order.frameAssetCode, order.frameAssetId) }} / 电池 {{ assetText(order.batterySerialNo, order.batteryAssetCode, order.batteryAssetId) }}</view>
        </view>
      </view>
      <view v-if="selectedOrder" class="asset-panel">
        <view class="section-title">订单详情</view>
        <view class="asset-item active">
          <view class="asset-main">
            <text>{{ selectedOrder.orderNo }}</text>
            <text class="asset-status">{{ orderStatusText(selectedOrder.orderStatus) }}</text>
          </view>
          <view class="asset-sub">创建时间：{{ dateText(selectedOrder.createdAt) }}</view>
          <view class="asset-sub">客户：{{ selectedOrder.customerName || '-' }} / {{ selectedOrder.customerPhone || '-' }}</view>
          <view class="asset-sub">主资产：{{ assetText(selectedOrder.frameSerialNo, selectedOrder.frameAssetCode, selectedOrder.frameAssetId) }} / 电池：{{ assetText(selectedOrder.batterySerialNo, selectedOrder.batteryAssetCode, selectedOrder.batteryAssetId) }}</view>
          <view class="asset-sub">预计归还：{{ dateText(selectedOrder.expectedReturnAt) }}</view>
          <view class="asset-sub">赠送租期：好评 {{ selectedOrder.reviewBonusDays }} 天 / 活动 {{ selectedOrder.campaignBonusDays }} 天 / 合计 {{ selectedOrder.totalBonusDays }} 天</view>
          <view class="asset-sub">实际核销 {{ money(selectedOrder.verificationAmount) }} / 租金 {{ money(selectedOrder.rentalAmount) }}</view>
          <view class="asset-sub">签单费 {{ money(selectedOrder.signFeeAmount) }} / 押金 {{ money(selectedOrder.depositAmount) }}</view>
          <view class="asset-sub">{{ renewalText(selectedOrder) }}</view>
          <view v-if="selectedOrder.items.length > 0" class="tag-row">
            <text v-for="item in selectedOrder.items" :key="item.id" class="tag">{{ item.itemName }} {{ money(item.totalAmount) }}</text>
          </view>
        </view>
        <view class="section-subtitle">赠送租期</view>
        <view v-if="selectedOrder.leaseBonuses.length === 0" class="empty">暂无赠送记录</view>
        <view v-for="bonus in selectedOrder.leaseBonuses" :key="bonus.id" class="bill-row">
          <view>
            <view class="bill-title">{{ leaseBonusTypeText(bonus.bonusType) }} {{ bonus.bonusDays }} 天</view>
            <view class="asset-sub">{{ bonus.remark || '-' }} / {{ dateText(bonus.createdAt) }}</view>
          </view>
          <view class="bill-amount">{{ dateText(bonus.expectedReturnAfter) }}</view>
        </view>
        <view v-if="canOperateOrder && canGrantSelectedOrderBonus" class="lease-bonus-form">
          <view class="field compact">
            <text>赠送类型</text>
            <picker :range="leaseBonusTypeLabels" :value="leaseBonusForm.bonusTypeIndex" @change="onLeaseBonusTypeChange">
              <view class="picker">{{ leaseBonusTypeLabels[leaseBonusForm.bonusTypeIndex] }}</view>
            </picker>
          </view>
          <view class="field compact">
            <text>赠送天数</text>
            <input v-model="leaseBonusForm.bonusDays" type="number" placeholder="请输入赠送天数" />
          </view>
          <view class="field compact">
            <text>备注</text>
            <input v-model="leaseBonusForm.remark" placeholder="如：客户好评、暑期活动" />
          </view>
          <button class="primary" :loading="leaseBonusSubmitting" @tap="grantLeaseBonus">确认赠送</button>
        </view>
        <view class="section-subtitle">账单</view>
        <view v-if="orderBills.length === 0" class="empty">当前订单暂无账单</view>
        <view v-for="bill in orderBills" :key="bill.id" class="bill-row">
          <view>
            <view class="bill-title">{{ bill.billNo }}</view>
            <view class="asset-sub">{{ billTypeText(bill.billType) }} / 第 {{ bill.periodNo }} 期 / {{ dateText(bill.dueAt) }}</view>
          </view>
          <view class="bill-amount">
            <view>{{ billStatusText(bill.billStatus) }}</view>
            <view>{{ money(bill.payableAmount) }}</view>
          </view>
        </view>
        <view class="section-subtitle">分润快照</view>
        <view v-if="settlement" class="settlement-grid">
          <template v-if="settlement.calculationVersion === 'PROFIT_V2'">
            <view>
              <text>实际结算金额</text>
              <text>{{ money(settlement.settlementBaseAmount) }}</text>
            </view>
            <view>
              <text>渠道核销扣点</text>
              <text>{{ money(settlement.channelFeeAmount) }}</text>
            </view>
            <view>
              <text>租赁平台扣点</text>
              <text>{{ money(settlement.platformFeeAmount) }}</text>
            </view>
            <view>
              <text>门店运营分润</text>
              <text>{{ money(settlement.storeOperationAmount) }}</text>
            </view>
            <view>
              <text>维修基金</text>
              <text>{{ money(settlement.maintenanceFundAmount) }}</text>
            </view>
            <view>
              <text>渠道引流分润</text>
              <text>{{ money(settlement.channelReferralAmount) }}</text>
            </view>
            <view>
              <text>出资方分润</text>
              <text>{{ money(settlement.investorShareAmount) }}</text>
            </view>
          </template>
          <template v-else>
            <view>
              <text>办单费</text>
              <text>{{ money(settlement.merchantOrderFeeAmount) }}</text>
            </view>
            <view>
              <text>门店租金分成</text>
              <text>{{ money(settlement.merchantRentShareAmount) }}</text>
            </view>
            <view>
              <text>平台租金分成</text>
              <text>{{ money(settlement.platformRentShareAmount) }}</text>
            </view>
            <view>
              <text>出资方净收益</text>
              <text>{{ money(settlement.investorNetShareAmount) }}</text>
            </view>
          </template>
        </view>
        <view v-else class="empty">暂无分润快照</view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">门店资产库存</view>
        <view v-if="assets.length === 0" class="empty">当前门店暂无资产</view>
        <view v-for="asset in assets" :key="asset.id" class="asset-item">
          <view class="asset-main">
            <text>{{ asset.assetTypeName || assetTypeText(asset.assetType) }}</text>
            <text class="asset-status">{{ statusText(asset.status) }}</text>
          </view>
          <view class="asset-sub">{{ asset.serialNo }}</view>
          <view class="asset-sub">出资方：{{ asset.investorName || '-' }}</view>
          <view class="asset-sub">残值：{{ asset.residualValue == null ? '-' : asset.residualValue }}</view>
          <view class="action-row">
            <button class="mini-btn" @tap="prepareMaintenance(asset.id)">登记维修</button>
          </view>
          <view v-if="asset.status === 'IDLE'" class="action-row">
            <button v-if="asset.assetType !== 'BATTERY'" class="mini-btn" @tap="fillFrameAsset(asset.id)">填入主资产</button>
            <button v-if="asset.assetType === 'BATTERY'" class="mini-btn" @tap="fillBatteryAsset(asset.id)">填入电池</button>
            <button class="mini-btn" @tap="fillNewAsset(asset.id, asset.assetType)">作为更换资产</button>
          </view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">门店配件库存</view>
        <view v-if="inventoryLoading" class="asset-sub">配件数据加载中...</view>
        <view v-if="spareStocks.length === 0" class="empty">当前门店暂无配件库存</view>
        <view v-for="item in spareStocks" :key="`${item.storeId}-${item.partId}`" class="asset-item">
          <view class="asset-main">
            <text>{{ item.partName }}</text>
            <text class="asset-status">库存 {{ item.stockQuantity }}</text>
          </view>
          <view class="asset-sub">门店均价 {{ money(item.avgUnitPrice) }} / 库存金额 {{ money(item.stockAmount) }}</view>
        </view>

        <view class="section-subtitle">最近配件流水</view>
        <view v-if="spareLogs.length === 0" class="empty">当前门店暂无配件流水</view>
        <view v-for="log in spareLogs" :key="log.id" class="asset-item">
          <view class="asset-main">
            <text>{{ log.partName }}</text>
            <text class="asset-status">{{ stockLogText(log.changeType) }}</text>
          </view>
          <view class="asset-sub">数量变化 {{ log.quantityChange }} / 单价 {{ money(log.unitPrice) }} / 金额 {{ money(log.amount) }}</view>
          <view class="asset-sub">备注：{{ log.remark || '-' }}</view>
          <view class="asset-sub">时间：{{ dateText(log.createdAt) }}</view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">发起维修</view>
        <view class="asset-sub">建议先在上面的资产卡片点“登记维修”，系统会自动带入资产 ID。</view>
        <view class="field compact">
          <text>资产 ID</text>
          <input v-model="maintenanceForm.assetId" type="number" placeholder="请输入资产 ID" />
        </view>
        <view class="field compact">
          <text>关联订单 ID</text>
          <input v-model="maintenanceForm.orderId" type="number" placeholder="可空，默认可带入当前选中订单" />
        </view>
        <view class="field compact">
          <text>维修类型</text>
          <picker :range="maintenanceTypeLabels" :value="maintenanceForm.maintenanceTypeIndex" @change="onMaintenanceTypeChange">
            <view class="picker">{{ maintenanceTypeLabels[maintenanceForm.maintenanceTypeIndex] }}</view>
          </picker>
        </view>
        <view class="field compact">
          <text>责任归因</text>
          <picker :range="responsibilityLabels" :value="maintenanceForm.responsibilityIndex" @change="onResponsibilityChange">
            <view class="picker">{{ responsibilityLabels[maintenanceForm.responsibilityIndex] }}</view>
          </picker>
        </view>
        <view class="field compact">
          <text>人工费</text>
          <input v-model="maintenanceForm.laborCost" type="digit" placeholder="默认 0" />
        </view>
        <view class="field compact">
          <text>外协费</text>
          <input v-model="maintenanceForm.externalCost" type="digit" placeholder="默认 0" />
        </view>
        <view class="field compact">
          <text>备注</text>
          <input v-model="maintenanceForm.remark" placeholder="请输入维修备注" />
        </view>
        <view class="section-subtitle">消耗配件</view>
        <view v-if="spareStocks.length === 0" class="empty">当前门店没有可用配件库存</view>
        <view v-for="(item, index) in maintenanceForm.parts" :key="index" class="asset-item">
          <view class="field compact">
            <text>配件</text>
            <picker :range="sparePartLabels" :value="item.partIndex" @change="onMaintenancePartChange($event, index)">
              <view class="picker">{{ sparePartLabels[item.partIndex] || '请选择配件' }}</view>
            </picker>
          </view>
          <view class="field compact">
            <text>数量</text>
            <input v-model="item.quantity" type="number" placeholder="请输入数量" />
          </view>
          <view class="field compact">
            <text>备注</text>
            <input v-model="item.remark" placeholder="可空" />
          </view>
          <button v-if="maintenanceForm.parts.length > 1" class="mini-btn" @tap="removeMaintenancePart(index)">删除这一项</button>
        </view>
        <view class="action-row">
          <button class="mini-btn" @tap="addMaintenancePart">新增配件项</button>
        </view>
        <button class="primary" :loading="maintenanceSubmitting" @tap="submitMaintenance">提交维修</button>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">维修记录</view>
        <view v-if="maintenanceRecords.length === 0" class="empty">当前门店暂无维修记录</view>
        <view v-for="record in maintenanceRecords" :key="record.id" class="asset-item">
          <view class="asset-main">
            <text>{{ record.maintenanceNo }}</text>
            <text class="asset-status">{{ record.maintenanceStatus }}</text>
          </view>
          <view class="asset-sub">资产 {{ record.assetCode }} / {{ assetTypeText(record.assetType) }} / {{ responsibilityText(record.responsibilityType) }}</view>
          <view class="asset-sub">配件费 {{ money(record.partsCost) }} / 总费用 {{ money(record.totalCost) }}</view>
          <view class="asset-sub">补门店 {{ money(record.merchantReimbursementAmount) }} / 扣出资方 {{ money(record.investorDeductAmount) }}</view>
          <view class="asset-sub">备注：{{ record.remark || '-' }}</view>
          <view v-if="record.parts.length > 0" class="tag-row">
            <text v-for="part in record.parts" :key="part.id" class="tag">{{ part.partNameSnapshot }} x{{ part.quantity }} / {{ money(part.totalAmount) }}</text>
          </view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">逾期订单</view>
        <view v-if="overdues.length === 0" class="empty">当前门店暂无逾期</view>
        <view v-for="item in overdues" :key="item.id" class="asset-item">
          <view class="asset-main">
            <text>{{ item.caseNo }}</text>
            <text class="asset-status">{{ collectionText(item.collectionStatus) }}</text>
          </view>
          <view class="asset-sub">订单 {{ item.orderId }} / 账单 {{ item.billId }}</view>
          <view class="asset-sub">未补缴：{{ item.unpaidAmount }} / 失败 {{ item.failCount }} 次</view>
          <view class="asset-sub">原因：{{ item.lastFailReason || '-' }}</view>
          <view class="action-row">
            <button class="mini-btn" @tap="updateCollection(item.id, 'CONTACTED')">已联系</button>
            <button class="mini-btn" @tap="updateCollection(item.id, 'PROMISED')">承诺付款</button>
            <button class="mini-btn" @tap="updateCollection(item.id, 'RESOLVED')">已解决</button>
          </view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">平台核销协助</view>
        <view class="filter-row">
          <picker :range="voucherStatusLabels" :value="voucherStatusIndex" @change="onVoucherStatusChange">
            <view class="picker compact-picker">{{ voucherStatusLabels[voucherStatusIndex] }}</view>
          </picker>
          <button class="mini-btn" :loading="voucherLoading" @tap="loadVouchers">刷新</button>
        </view>
        <view v-if="vouchers.length === 0" class="empty">当前门店暂无核销记录</view>
        <view v-for="item in vouchers" :key="item.id" class="asset-item">
          <view class="asset-main">
            <text>{{ platformText(item.sourcePlatform) }} {{ item.voucherCode }}</text>
            <text class="asset-status">{{ voucherStatusText(item.verifyStatus) }}</text>
          </view>
          <view class="asset-sub">用户 {{ item.userAccountId || '-' }} / 订单 {{ item.orderId || '-' }} / 签单费账单 {{ item.signFeeBillId || '-' }}</view>
          <view class="asset-sub">参考金额 {{ money(item.voucherAmount) }} / 实际核销 {{ item.verificationAmount == null ? '待补录' : money(item.verificationAmount) }}</view>
          <view class="asset-sub">签单费 {{ money(item.signFeeAmount) }}</view>
          <view v-if="item.failureReason" class="asset-sub">失败原因：{{ item.failureReason }}</view>
          <view v-if="item.exceptionReason" class="asset-sub">异常原因：{{ item.exceptionReason }}</view>
          <view v-if="!item.orderId && item.verifyStatus !== 'CONSUMING' && item.verifyStatus !== 'CONSUMED'" class="field compact">
            <text>核销金额</text>
            <input v-model="verificationAmountInputs[item.id]" type="digit" placeholder="客户未填写时由门店补录" />
          </view>
          <view class="action-row">
            <button v-if="!item.orderId && item.verifyStatus !== 'CONSUMING' && item.verifyStatus !== 'CONSUMED'" class="mini-btn" @tap="saveVerificationAmount(item)">保存金额</button>
            <button class="mini-btn" @tap="markVoucherException(item.id)">标记异常</button>
          </view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">门店收益明细</view>
        <view class="filter-row">
          <picker :range="incomeStatusLabels" :value="incomeStatusIndex" @change="onIncomeStatusChange">
            <view class="picker compact-picker">{{ incomeStatusLabels[incomeStatusIndex] }}</view>
          </picker>
          <button class="mini-btn" :loading="incomeLoading" @tap="loadIncomeEntries">刷新</button>
        </view>
        <view v-if="incomeEntries.length === 0" class="empty">当前门店暂无收益明细</view>
        <view v-for="item in incomeEntries" :key="item.id" class="asset-item">
          <view class="asset-main">
            <text>{{ incomeLineText(item.lineType) }}</text>
            <text class="asset-status">{{ incomeStatusText(item.entryStatus) }}</text>
          </view>
          <view class="asset-sub">订单 {{ item.orderId }} / {{ item.entryNo }}</view>
          <view class="asset-sub">金额：{{ money(item.amount) }} / {{ item.remark || '-' }}</view>
          <view class="asset-sub">结算时间：{{ dateText(item.settledAt) }}</view>
        </view>
      </view>
      <view v-if="currentStoreId" class="asset-panel">
        <view class="section-title">订单资产履约</view>
        <view v-if="selectedOrder" class="asset-sub">当前订单：{{ selectedOrder.orderNo }}，履约动作将绑定此订单。</view>
        <view class="field compact">
          <text>订单 ID</text>
          <input v-model="fulfillment.orderId" type="number" placeholder="请输入订单 ID" />
        </view>
        <view class="field compact">
          <text>主资产 ID</text>
          <input v-model="fulfillment.frameAssetId" type="number" placeholder="取车绑定主资产，可空" />
        </view>
        <view class="field compact">
          <text>电池资产 ID</text>
          <input v-model="fulfillment.batteryAssetId" type="number" placeholder="取车绑定电池，可空" />
        </view>
        <button v-if="selectedOrder?.orderStatus === 'PENDING_PAYMENT'" class="primary" :loading="fulfillmentLoading" @tap="shipWithoutPayment">免付款发货</button>
        <button v-if="selectedOrder?.orderStatus === 'PENDING_PICKUP'" class="primary" :loading="fulfillmentLoading" @tap="pickupAssets">取车绑定</button>
        <view class="field compact">
          <text>更换类型</text>
          <picker :range="replaceTypeLabels" :value="replaceTypeIndex" @change="onReplaceTypeChange">
            <view class="picker">{{ replaceTypeLabels[replaceTypeIndex] }}</view>
          </picker>
        </view>
        <view class="field compact">
          <text>新资产 ID</text>
          <input v-model="fulfillment.newAssetId" type="number" placeholder="请输入新资产 ID" />
        </view>
        <button v-if="canReplaceSelectedOrder" class="secondary" :loading="fulfillmentLoading" @tap="replaceAsset">更换资产</button>
        <button v-if="canReturnSelectedOrder" class="secondary" :loading="fulfillmentLoading" @tap="returnAssets">归还并结束订单</button>
        <view v-if="lastFulfillmentNo" class="asset-sub">最近凭证：{{ lastFulfillmentNo }}</view>
      </view>
      <button class="secondary" @tap="logout">退出登录</button>
    </view>

    <view v-else class="panel">
      <view class="field">
        <text>账号</text>
        <input v-model="form.username" placeholder="请输入商户账号" />
      </view>
      <view class="field">
        <text>密码</text>
        <input v-model="form.password" password placeholder="请输入密码" />
      </view>
      <button class="primary" :loading="loading" @tap="login">登录商户版</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { request } from '../../services/request';
import type {
  Asset,
  AssetMaintenance,
  AssetChange,
  AssetHandover,
  AssetStatus,
  CollectionStatus,
  CurrentAccount,
  LoginResponse,
  OrderStatus,
  OverdueCase,
  RentalBill,
  RentalOrder,
  SparePartStockLog,
  StoreSparePartStock,
  SettlementIncomeEntry,
  SettlementSnapshot,
  Store,
  StoreSku,
  VoucherRecord
} from '../../types/api';

const loading = ref(false);
const orderLoading = ref(false);
const fulfillmentLoading = ref(false);
const account = ref<CurrentAccount | null>(null);
const stores = ref<Store[]>([]);
const storeSkus = ref<StoreSku[]>([]);
const assets = ref<Asset[]>([]);
const spareStocks = ref<StoreSparePartStock[]>([]);
const spareLogs = ref<SparePartStockLog[]>([]);
const maintenanceRecords = ref<AssetMaintenance[]>([]);
const overdues = ref<OverdueCase[]>([]);
const vouchers = ref<VoucherRecord[]>([]);
const verificationAmountInputs = reactive<Record<number, string>>({});
const incomeEntries = ref<SettlementIncomeEntry[]>([]);
const orders = ref<RentalOrder[]>([]);
const selectedOrder = ref<RentalOrder | null>(null);
const orderBills = ref<RentalBill[]>([]);
const settlement = ref<SettlementSnapshot | null>(null);
const currentStoreId = ref<number | null>(uni.getStorageSync('xniu_current_store_id') || null);
const orderKeyword = ref('');
const orderStatusIndex = ref(0);
const orderCreateVisible = ref(false);
const orderCreateSubmitting = ref(false);
const leaseBonusSubmitting = ref(false);
const voucherLoading = ref(false);
const incomeLoading = ref(false);
const inventoryLoading = ref(false);
const maintenanceSubmitting = ref(false);
const voucherStatusIndex = ref(0);
const incomeStatusIndex = ref(0);
const form = reactive({
  username: 'merchant_demo',
  password: 'admin123'
});
const fulfillment = reactive({
  orderId: '',
  frameAssetId: '',
  batteryAssetId: '',
  newAssetId: ''
});
const orderCreateForm = reactive({
  userAccountId: '',
  customerName: '',
  customerPhone: '',
  storeSkuIndex: 0,
  packageIndex: 0,
  verificationAmount: '',
  frameAssetIndex: 0,
  batteryAssetIndex: 0
});
const leaseBonusForm = reactive({
  bonusTypeIndex: 0,
  bonusDays: '2',
  remark: ''
});
const maintenanceForm = reactive({
  assetId: '',
  orderId: '',
  maintenanceTypeIndex: 0,
  responsibilityIndex: 0,
  laborCost: '',
  externalCost: '',
  remark: '',
  parts: [{ partIndex: 0, quantity: '', remark: '' }] as Array<{ partIndex: number; quantity: string; remark: string }>
});
const replaceTypeIndex = ref(0);
const replaceTypes = ['VEHICLE_FRAME', 'BATTERY'] as const;
const replaceTypeLabels = ['主资产', '电池'];
const maintenanceTypeValues = ['REPAIR', 'MAINTENANCE', 'REPLACE_PART', 'INSPECTION'] as const;
const maintenanceTypeLabels = ['维修', '保养', '换件', '检测'];
const responsibilityValues = ['ROUTINE_MAINTENANCE', 'CUSTOMER_DAMAGE', 'MERCHANT_RESPONSIBILITY', 'PLATFORM_SUBSIDY'] as const;
const responsibilityLabels = ['日常资产维护', '客户损坏', '门店责任', '平台兜底'];
const leaseBonusTypeValues = ['REVIEW', 'CAMPAIGN'] as const;
const leaseBonusTypeLabels = ['好评赠送', '活动赠送'];
const orderStatusValues = ['', 'PENDING_PAYMENT', 'PENDING_PICKUP', 'RENTING', 'PENDING_RETURN', 'PENDING_SUPPLEMENT', 'COMPLETED', 'CANCELLED', 'EXCEPTION'] as const;
const orderStatusLabels = ['全部订单', '待支付', '待取车', '租赁中', '待归还', '待补缴', '已完成', '已取消', '异常'];
const voucherStatusValues = ['', 'PREPARED', 'WAITING_SIGN_FEE', 'CONSUMED', 'FAILED', 'EXCEPTION'] as const;
const voucherStatusLabels = ['全部核销', '已核销准备', '待签单费', '已核销', '失败', '异常'];
const incomeStatusValues = ['', 'PENDING', 'SETTLED', 'FROZEN'] as const;
const incomeStatusLabels = ['全部收益', '待结算', '已结算', '已冻结'];
const lastFulfillmentNo = ref('');

const canCreateOrder = computed(() => account.value?.permissions.includes('order.create') ?? false);
const canOperateOrder = computed(() => account.value?.permissions.includes('order.operate') ?? false);
const canGrantSelectedOrderBonus = computed(() => {
  if (!selectedOrder.value) {
    return false;
  }
  return !['OVERDUE', 'PENDING_SUPPLEMENT', 'COMPLETED', 'CANCELLED', 'EXCEPTION'].includes(selectedOrder.value.orderStatus);
});
const selectedOrderStoreSku = computed(() => storeSkus.value[orderCreateForm.storeSkuIndex]);
const orderPackages = computed(() => (selectedOrderStoreSku.value?.packages ?? []).filter((item) => item.status === 'ENABLED'));
const orderFrameAssets = computed(() => assets.value.filter((item) => item.assetType !== 'BATTERY' && item.status === 'IDLE'));
const orderBatteryAssets = computed(() => assets.value.filter((item) => item.assetType === 'BATTERY' && item.status === 'IDLE'));
const storeSkuLabels = computed(() => storeSkus.value.map((item) => item.displayName));
const orderPackageLabels = computed(() => orderPackages.value.map((item) => `${item.packageName} / ${money(item.rentalAmount)}`));
const orderFrameAssetLabels = computed(() => ['暂不绑定', ...orderFrameAssets.value.map((item) => `${item.serialNo} / ${item.assetTypeName || assetTypeText(item.assetType)}`)]);
const orderBatteryAssetLabels = computed(() => ['暂不绑定', ...orderBatteryAssets.value.map((item) => item.serialNo)]);
const selectedOrderFrameAsset = computed(() => orderCreateForm.frameAssetIndex > 0 ? orderFrameAssets.value[orderCreateForm.frameAssetIndex - 1] : undefined);
const orderUsesIntegratedVehicle = computed(() => selectedOrderFrameAsset.value?.assetType === 'INTEGRATED_VEHICLE');
const canReplaceSelectedOrder = computed(() => Boolean(selectedOrder.value && ['RENTING', 'PENDING_RETURN', 'PENDING_SUPPLEMENT'].includes(selectedOrder.value.orderStatus)));
const canReturnSelectedOrder = computed(() => Boolean(selectedOrder.value && ['RENTING', 'PENDING_RETURN', 'OVERDUE', 'PENDING_SUPPLEMENT'].includes(selectedOrder.value.orderStatus)));

const scopeText = computed(() => {
  if (!account.value || account.value.storeScopes.length === 0) {
    return '无门店授权';
  }
  return account.value.storeScopes
    .map((scope) => scope.storeId ? `门店 ${scope.storeId}` : `商户 ${scope.merchantId} 全部门店`)
    .join('、');
});

const sparePartLabels = computed(() =>
  spareStocks.value.map((item) => `${item.partName} / 库存 ${item.stockQuantity}`)
);

const filteredOrders = computed(() => {
  const keyword = orderKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return orders.value;
  }
  return orders.value.filter((order) => {
    return order.orderNo.toLowerCase().includes(keyword)
      || String(order.userAccountId || '').includes(keyword)
      || String(order.customerName || '').toLowerCase().includes(keyword)
      || String(order.customerPhone || '').includes(keyword)
      || String(order.frameSerialNo || order.frameAssetCode || '').toLowerCase().includes(keyword)
      || String(order.batterySerialNo || order.batteryAssetCode || '').toLowerCase().includes(keyword)
      || String(order.id).includes(keyword);
  });
});

onMounted(async () => {
  if (!uni.getStorageSync('xniu_merchant_token')) {
    return;
  }
  try {
    account.value = await request<CurrentAccount>('/api/auth/me');
    await loadStores();
  } catch {
    uni.removeStorageSync('xniu_merchant_token');
  }
});

async function login() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    const result = await request<LoginResponse>('/api/auth/merchant/login', {
      method: 'POST',
      data: { username: form.username, password: form.password }
    });
    uni.setStorageSync('xniu_merchant_token', result.token);
    account.value = result.account;
    await loadStores();
    uni.showToast({ title: '登录成功', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '登录失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function logout() {
  uni.removeStorageSync('xniu_merchant_token');
  uni.removeStorageSync('xniu_current_store_id');
  account.value = null;
  stores.value = [];
  storeSkus.value = [];
  assets.value = [];
  spareStocks.value = [];
  spareLogs.value = [];
  maintenanceRecords.value = [];
  overdues.value = [];
  vouchers.value = [];
  incomeEntries.value = [];
  orders.value = [];
  selectedOrder.value = null;
  orderBills.value = [];
  settlement.value = null;
  orderCreateVisible.value = false;
  lastFulfillmentNo.value = '';
  currentStoreId.value = null;
}

async function loadStores() {
  stores.value = await request<Store[]>('/api/merchant/workbench/stores');
  if (stores.value.length > 0 && !currentStoreId.value) {
    await selectStore(stores.value[0].id);
  } else if (currentStoreId.value) {
    await Promise.all([
      loadAssets(currentStoreId.value),
      loadSpareInventory(currentStoreId.value),
      loadMaintenances(currentStoreId.value),
      loadOverdues(currentStoreId.value),
      loadVouchers(),
      loadIncomeEntries(),
      loadStoreSkus(currentStoreId.value),
      loadOrders()
    ]);
  }
}

async function selectStore(storeId: number) {
  currentStoreId.value = storeId;
  uni.setStorageSync('xniu_current_store_id', storeId);
  selectedOrder.value = null;
  orderBills.value = [];
  settlement.value = null;
  resetMaintenanceForm();
  resetOrderCreateForm();
  await Promise.all([
    loadAssets(storeId),
    loadSpareInventory(storeId),
    loadMaintenances(storeId),
    loadOverdues(storeId),
    loadVouchers(),
    loadIncomeEntries(),
    loadStoreSkus(storeId),
    loadOrders()
  ]);
}

async function loadStoreSkus(storeId: number) {
  if (!canCreateOrder.value) {
    storeSkus.value = [];
    return;
  }
  storeSkus.value = await request<StoreSku[]>(`/api/merchant/products/store-skus?storeId=${storeId}`);
  resetOrderCreateForm();
}

async function loadAssets(storeId: number) {
  assets.value = await request<Asset[]>(`/api/merchant/assets/stores/${storeId}`);
}

async function loadSpareInventory(storeId: number) {
  inventoryLoading.value = true;
  try {
    const [stockData, logData] = await Promise.all([
      request<StoreSparePartStock[]>(`/api/merchant/spare-parts/store-stocks?storeId=${storeId}`),
      request<SparePartStockLog[]>(`/api/merchant/spare-parts/logs?storeId=${storeId}`)
    ]);
    spareStocks.value = stockData;
    spareLogs.value = logData;
  } finally {
    inventoryLoading.value = false;
  }
}

async function loadMaintenances(storeId: number) {
  maintenanceRecords.value = await request<AssetMaintenance[]>(`/api/merchant/maintenances?storeId=${storeId}`);
}

async function loadOverdues(storeId: number) {
  overdues.value = await request<OverdueCase[]>(`/api/merchant/overdues?storeId=${storeId}&overdueStatus=OPEN`);
}

async function loadVouchers() {
  if (!currentStoreId.value) {
    return;
  }
  voucherLoading.value = true;
  try {
    const status = voucherStatusValues[voucherStatusIndex.value];
    const query = status ? `&status=${status}` : '';
    vouchers.value = await request<VoucherRecord[]>(`/api/merchant/vouchers?storeId=${currentStoreId.value}${query}`);
    vouchers.value.forEach((item) => {
      verificationAmountInputs[item.id] = item.verificationAmount == null ? '' : String(item.verificationAmount);
    });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '核销记录加载失败', icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function loadIncomeEntries() {
  if (!currentStoreId.value) {
    return;
  }
  incomeLoading.value = true;
  try {
    const status = incomeStatusValues[incomeStatusIndex.value];
    const query = status ? `&status=${status}` : '';
    incomeEntries.value = await request<SettlementIncomeEntry[]>(`/api/merchant/settlement/income/entries?storeId=${currentStoreId.value}${query}`);
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '收益加载失败', icon: 'none' });
  } finally {
    incomeLoading.value = false;
  }
}

async function loadOrders() {
  if (!currentStoreId.value) {
    return;
  }
  orderLoading.value = true;
  try {
    const status = orderStatusValues[orderStatusIndex.value];
    const query = status ? `&status=${status}` : '';
    orders.value = await request<RentalOrder[]>(`/api/merchant/orders?storeId=${currentStoreId.value}${query}`);
    if (orders.value.length === 0) {
      selectedOrder.value = null;
      orderBills.value = [];
      settlement.value = null;
      return;
    }
    if (!selectedOrder.value || !orders.value.some((order) => order.id === selectedOrder.value?.id)) {
      await selectOrder(orders.value[0], false);
    } else {
      const latest = orders.value.find((order) => order.id === selectedOrder.value?.id);
      if (latest) {
        selectedOrder.value = latest;
      }
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '订单加载失败', icon: 'none' });
  } finally {
    orderLoading.value = false;
  }
}

function toggleOrderCreate() {
  orderCreateVisible.value = !orderCreateVisible.value;
}

function resetOrderCreateForm() {
  orderCreateForm.userAccountId = '';
  orderCreateForm.customerName = '';
  orderCreateForm.customerPhone = '';
  orderCreateForm.storeSkuIndex = 0;
  orderCreateForm.packageIndex = 0;
  orderCreateForm.verificationAmount = orderPackages.value[0] ? String(orderPackages.value[0].rentalAmount) : '';
  orderCreateForm.frameAssetIndex = 0;
  orderCreateForm.batteryAssetIndex = 0;
}

function onOrderStoreSkuChange(event: { detail: { value: number } }) {
  orderCreateForm.storeSkuIndex = Number(event.detail.value);
  orderCreateForm.packageIndex = 0;
  const firstPackage = storeSkus.value[orderCreateForm.storeSkuIndex]?.packages.find((item) => item.status === 'ENABLED');
  orderCreateForm.verificationAmount = firstPackage ? String(firstPackage.rentalAmount) : '';
  orderCreateForm.frameAssetIndex = 0;
  orderCreateForm.batteryAssetIndex = 0;
}

function onOrderPackageChange(event: { detail: { value: number } }) {
  orderCreateForm.packageIndex = Number(event.detail.value);
  const selectedPackage = orderPackages.value[orderCreateForm.packageIndex];
  orderCreateForm.verificationAmount = selectedPackage ? String(selectedPackage.rentalAmount) : '';
}

function onOrderFrameAssetChange(event: { detail: { value: number } }) {
  orderCreateForm.frameAssetIndex = Number(event.detail.value);
  if (orderUsesIntegratedVehicle.value) {
    orderCreateForm.batteryAssetIndex = 0;
  }
}

function onOrderBatteryAssetChange(event: { detail: { value: number } }) {
  if (orderUsesIntegratedVehicle.value) {
    orderCreateForm.batteryAssetIndex = 0;
    return;
  }
  orderCreateForm.batteryAssetIndex = Number(event.detail.value);
}

async function createMerchantOrder() {
  const storeSku = selectedOrderStoreSku.value;
  const rentalPackage = orderPackages.value[orderCreateForm.packageIndex];
  if (!storeSku || !rentalPackage) {
    uni.showToast({ title: '请选择门店商品和 SKU', icon: 'none' });
    return;
  }
  if (!orderCreateForm.customerName.trim() || !orderCreateForm.customerPhone.trim()) {
    uni.showToast({ title: '请填写客户姓名和电话', icon: 'none' });
    return;
  }
  const verificationAmount = Number(orderCreateForm.verificationAmount);
  if (!orderCreateForm.verificationAmount.trim() || !Number.isFinite(verificationAmount) || verificationAmount < 0) {
    uni.showToast({ title: '请填写正确的实际核销金额', icon: 'none' });
    return;
  }
  orderCreateSubmitting.value = true;
  try {
    const created = await request<RentalOrder>('/api/merchant/orders', {
      method: 'POST',
      data: {
        userAccountId: toOptionalNumber(orderCreateForm.userAccountId),
        customerName: orderCreateForm.customerName.trim(),
        customerPhone: orderCreateForm.customerPhone.trim(),
        storeSkuId: storeSku.id,
        packageId: rentalPackage.packageId,
        verificationAmount,
        frameAssetId: orderCreateForm.frameAssetIndex > 0 ? orderFrameAssets.value[orderCreateForm.frameAssetIndex - 1]?.id : undefined,
        batteryAssetId: !orderUsesIntegratedVehicle.value && orderCreateForm.batteryAssetIndex > 0 ? orderBatteryAssets.value[orderCreateForm.batteryAssetIndex - 1]?.id : undefined
      }
    });
    orderCreateVisible.value = false;
    resetOrderCreateForm();
    await loadOrders();
    await selectOrder(created, false);
    uni.showToast({ title: '订单已创建', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '订单创建失败', icon: 'none' });
  } finally {
    orderCreateSubmitting.value = false;
  }
}

function assetText(serialNo?: string | null, assetCode?: string | null, assetId?: number | null) {
  return serialNo || assetCode || (assetId ? `#${assetId}` : '-');
}

async function selectOrder(order: RentalOrder, toast = true) {
  selectedOrder.value = order;
  leaseBonusForm.bonusTypeIndex = 0;
  leaseBonusForm.bonusDays = '2';
  leaseBonusForm.remark = '';
  fulfillment.orderId = String(order.id);
  fulfillment.frameAssetId = order.frameAssetId ? String(order.frameAssetId) : '';
  fulfillment.batteryAssetId = order.batteryAssetId ? String(order.batteryAssetId) : '';
  await Promise.all([loadOrderBills(order.id), loadSettlement(order.id)]);
  if (toast) {
    uni.showToast({ title: '已切换订单', icon: 'none' });
  }
}

function onLeaseBonusTypeChange(event: { detail: { value: number } }) {
  leaseBonusForm.bonusTypeIndex = Number(event.detail.value);
  leaseBonusForm.bonusDays = leaseBonusForm.bonusTypeIndex === 0 ? '2' : '15';
}

async function grantLeaseBonus() {
  if (!selectedOrder.value) {
    return;
  }
  const bonusDays = Number(leaseBonusForm.bonusDays);
  if (!Number.isInteger(bonusDays) || bonusDays < 1 || bonusDays > 999) {
    uni.showToast({ title: '赠送天数请输入 1 到 999 的整数', icon: 'none' });
    return;
  }
  leaseBonusSubmitting.value = true;
  try {
    const updated = await request<RentalOrder>(`/api/merchant/orders/${selectedOrder.value.id}/lease-bonuses`, {
      method: 'POST',
      data: {
        bonusType: leaseBonusTypeValues[leaseBonusForm.bonusTypeIndex],
        bonusDays,
        remark: leaseBonusForm.remark.trim() || undefined
      }
    });
    selectedOrder.value = updated;
    orders.value = orders.value.map((order) => order.id === updated.id ? updated : order);
    leaseBonusForm.remark = '';
    uni.showToast({ title: `已赠送 ${bonusDays} 天`, icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '赠送租期失败', icon: 'none' });
  } finally {
    leaseBonusSubmitting.value = false;
  }
}

async function loadOrderBills(orderId: number) {
  orderBills.value = await request<RentalBill[]>(`/api/merchant/orders/${orderId}/bills`);
}

async function loadSettlement(orderId: number) {
  try {
    settlement.value = await request<SettlementSnapshot>(`/api/merchant/orders/${orderId}/settlement`);
  } catch {
    settlement.value = null;
  }
}

async function updateCollection(id: number, status: CollectionStatus) {
  await request(`/api/merchant/overdues/${id}/collection`, {
    method: 'POST',
    data: {
      collectionStatus: status,
      remark: collectionText(status)
    }
  });
  if (currentStoreId.value) {
    await loadOverdues(currentStoreId.value);
  }
  uni.showToast({ title: '已更新', icon: 'success' });
}

async function markVoucherException(id: number) {
  try {
    await request(`/api/merchant/vouchers/${id}/exception`, {
      method: 'POST',
      data: { reason: '门店现场标记异常核销' }
    });
    await loadVouchers();
    uni.showToast({ title: '已标记异常', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '标记失败', icon: 'none' });
  }
}

async function saveVerificationAmount(item: VoucherRecord) {
  const amount = Number(verificationAmountInputs[item.id]);
  if (!verificationAmountInputs[item.id]?.trim() || !Number.isFinite(amount) || amount < 0) {
    uni.showToast({ title: '请输入正确的核销金额', icon: 'none' });
    return;
  }
  voucherLoading.value = true;
  try {
    await request(`/api/merchant/vouchers/${item.id}/verification-amount`, {
      method: 'POST',
      data: { verificationAmount: amount }
    });
    await loadVouchers();
    uni.showToast({ title: '核销金额已保存', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '核销金额保存失败', icon: 'none' });
  } finally {
    voucherLoading.value = false;
  }
}

async function pickupAssets() {
  const orderId = ensureOrderId();
  if (!orderId) return;
  fulfillmentLoading.value = true;
  try {
    const result = await request<AssetHandover>(`/api/merchant/orders/${orderId}/pickup-assets`, {
      method: 'POST',
      data: {
        frameAssetId: toOptionalNumber(fulfillment.frameAssetId),
        batteryAssetId: toOptionalNumber(fulfillment.batteryAssetId),
        remark: '商户端取车绑定'
      }
    });
    lastFulfillmentNo.value = result.handoverNo;
    await refreshCurrentStore();
    await refreshSelectedOrder();
    uni.showToast({ title: '取车已绑定', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '取车失败', icon: 'none' });
  } finally {
    fulfillmentLoading.value = false;
  }
}

async function shipWithoutPayment() {
  const orderId = ensureOrderId();
  if (!orderId) return;
  if (!await confirmUnpaidShipment()) return;
  fulfillmentLoading.value = true;
  try {
    const result = await request<AssetHandover>(`/api/merchant/orders/${orderId}/ship`, {
      method: 'POST',
      data: {
        frameAssetId: toOptionalNumber(fulfillment.frameAssetId),
        batteryAssetId: toOptionalNumber(fulfillment.batteryAssetId),
        remark: '商户端选择免付款发货'
      }
    });
    lastFulfillmentNo.value = result.handoverNo;
    await refreshCurrentStore();
    await refreshSelectedOrder();
    uni.showToast({ title: '已免付款发货', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '发货失败', icon: 'none' });
  } finally {
    fulfillmentLoading.value = false;
  }
}

function confirmUnpaidShipment(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认免付款发货',
      content: '该订单尚未付款，发货后将直接进入租赁中。确认继续吗？',
      confirmText: '确认发货',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

async function replaceAsset() {
  const orderId = ensureOrderId();
  if (!orderId || !fulfillment.newAssetId) {
    uni.showToast({ title: '请输入订单和新资产', icon: 'none' });
    return;
  }
  fulfillmentLoading.value = true;
  try {
    const result = await request<AssetChange>(`/api/merchant/orders/${orderId}/replace-asset`, {
      method: 'POST',
      data: {
        assetType: replaceTypes[replaceTypeIndex.value],
        newAssetId: Number(fulfillment.newAssetId),
        oldAssetResultStatus: 'IDLE',
        remark: '商户端更换资产'
      }
    });
    lastFulfillmentNo.value = result.changeNo;
    await refreshCurrentStore();
    await refreshSelectedOrder();
    uni.showToast({ title: '资产已更换', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '更换失败', icon: 'none' });
  } finally {
    fulfillmentLoading.value = false;
  }
}

async function returnAssets() {
  const orderId = ensureOrderId();
  if (!orderId) return;
  fulfillmentLoading.value = true;
  try {
    const result = await request<AssetHandover>(`/api/merchant/orders/${orderId}/return-assets`, {
      method: 'POST',
      data: {
        returnStoreId: currentStoreId.value,
        frameResultStatus: 'IDLE',
        batteryResultStatus: 'IDLE',
        remark: '商户端归还结束订单'
      }
    });
    lastFulfillmentNo.value = result.handoverNo;
    await refreshCurrentStore();
    await refreshSelectedOrder();
    uni.showToast({ title: '订单已结束', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '归还失败', icon: 'none' });
  } finally {
    fulfillmentLoading.value = false;
  }
}

async function refreshCurrentStore() {
  if (currentStoreId.value) {
    await Promise.all([
      loadAssets(currentStoreId.value),
      loadSpareInventory(currentStoreId.value),
      loadMaintenances(currentStoreId.value),
      loadOverdues(currentStoreId.value),
      loadVouchers(),
      loadIncomeEntries(),
      loadStoreSkus(currentStoreId.value),
      loadOrders()
    ]);
  }
}

async function refreshSelectedOrder() {
  if (!selectedOrder.value) {
    return;
  }
  const order = await request<RentalOrder>(`/api/merchant/orders/${selectedOrder.value.id}`);
  await selectOrder(order, false);
}

function ensureOrderId() {
  if (!fulfillment.orderId) {
    uni.showToast({ title: '请输入订单 ID', icon: 'none' });
    return null;
  }
  return Number(fulfillment.orderId);
}

function toOptionalNumber(value: string) {
  return value ? Number(value) : undefined;
}

function onReplaceTypeChange(event: { detail: { value: number } }) {
  replaceTypeIndex.value = Number(event.detail.value);
}

function onOrderStatusChange(event: { detail: { value: number } }) {
  orderStatusIndex.value = Number(event.detail.value);
  loadOrders();
}

function onVoucherStatusChange(event: { detail: { value: number } }) {
  voucherStatusIndex.value = Number(event.detail.value);
  loadVouchers();
}

function onIncomeStatusChange(event: { detail: { value: number } }) {
  incomeStatusIndex.value = Number(event.detail.value);
  loadIncomeEntries();
}

function fillFrameAsset(assetId: number) {
  fulfillment.frameAssetId = String(assetId);
  if (assets.value.find((item) => item.id === assetId)?.assetType === 'INTEGRATED_VEHICLE') {
    fulfillment.batteryAssetId = '';
  }
}

function fillBatteryAsset(assetId: number) {
  fulfillment.batteryAssetId = String(assetId);
}

function fillNewAsset(assetId: number, assetType: Asset['assetType']) {
  fulfillment.newAssetId = String(assetId);
  replaceTypeIndex.value = assetType === 'BATTERY' ? 1 : 0;
}

function prepareMaintenance(assetId: number) {
  maintenanceForm.assetId = String(assetId);
  maintenanceForm.orderId = selectedOrder.value ? String(selectedOrder.value.id) : '';
  uni.showToast({ title: `已带入资产 ${assetId}`, icon: 'none' });
}

function addMaintenancePart() {
  maintenanceForm.parts.push({ partIndex: 0, quantity: '', remark: '' });
}

function removeMaintenancePart(index: number) {
  maintenanceForm.parts.splice(index, 1);
}

async function submitMaintenance() {
  if (!currentStoreId.value) {
    return;
  }
  if (!maintenanceForm.assetId) {
    uni.showToast({ title: '请先选择资产', icon: 'none' });
    return;
  }
  maintenanceSubmitting.value = true;
  try {
    await request('/api/merchant/maintenances', {
      method: 'POST',
      data: {
        assetId: Number(maintenanceForm.assetId),
        storeId: currentStoreId.value,
        orderId: toOptionalNumber(maintenanceForm.orderId),
        maintenanceType: maintenanceTypeValues[maintenanceForm.maintenanceTypeIndex],
        responsibilityType: responsibilityValues[maintenanceForm.responsibilityIndex],
        maintenanceStatus: 'COMPLETED',
        laborCost: Number(maintenanceForm.laborCost || 0),
        externalCost: Number(maintenanceForm.externalCost || 0),
        remark: maintenanceForm.remark || undefined,
        parts: maintenanceForm.parts
          .filter((item) => spareStocks.value[item.partIndex] && Number(item.quantity || 0) > 0)
          .map((item) => ({
            partId: spareStocks.value[item.partIndex].partId,
            quantity: Number(item.quantity),
            remark: item.remark || undefined
          }))
      }
    });
    await refreshCurrentStore();
    resetMaintenanceForm();
    uni.showToast({ title: '维修已登记', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '维修登记失败', icon: 'none' });
  } finally {
    maintenanceSubmitting.value = false;
  }
}

function resetMaintenanceForm() {
  maintenanceForm.assetId = '';
  maintenanceForm.orderId = '';
  maintenanceForm.maintenanceTypeIndex = 0;
  maintenanceForm.responsibilityIndex = 0;
  maintenanceForm.laborCost = '';
  maintenanceForm.externalCost = '';
  maintenanceForm.remark = '';
  maintenanceForm.parts = [{ partIndex: 0, quantity: '', remark: '' }];
}

function onMaintenanceTypeChange(event: { detail: { value: number } }) {
  maintenanceForm.maintenanceTypeIndex = Number(event.detail.value);
}

function onResponsibilityChange(event: { detail: { value: number } }) {
  maintenanceForm.responsibilityIndex = Number(event.detail.value);
}

function onMaintenancePartChange(event: { detail: { value: number } }, index: number) {
  maintenanceForm.parts[index].partIndex = Number(event.detail.value);
}

function statusText(status: AssetStatus) {
  const map: Record<AssetStatus, string> = {
    IDLE: '空闲',
    RENTING: '租赁中',
    PENDING_REPAIR: '待检修',
    REPAIRING: '维修中',
    SCRAPPED: '已报废',
    SOLD: '已售出',
    EXCEPTION: '异常'
  };
  return map[status];
}

function assetTypeText(assetType: Asset['assetType']) {
  if (assetType === 'INTEGRATED_VEHICLE') return '车电一体';
  if (assetType === 'VEHICLE_FRAME') return '车架';
  if (assetType === 'BATTERY') return '电池';
  return '普通资产';
}

function collectionText(status: CollectionStatus) {
  const map: Record<CollectionStatus, string> = {
    PENDING: '待催缴',
    CONTACTED: '已联系',
    PROMISED: '承诺付款',
    RESOLVED: '已解决',
    BAD_DEBT: '坏账'
  };
  return map[status];
}

function orderStatusText(status: OrderStatus) {
  const map: Record<OrderStatus, string> = {
    PENDING_PAYMENT: '待支付',
    PENDING_REAL_NAME: '待实名',
    PENDING_AGREEMENT: '待签约',
    PENDING_DEPOSIT_AUTH: '待免押',
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
  return map[status];
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
    PREPARED: '核销准备',
    VERIFIED: '已验码',
    WAITING_SIGN_FEE: '待签单费',
    CONSUMING: '核销中',
    CONSUMED: '已核销',
    FAILED: '失败',
    EXCEPTION: '异常'
  };
  return map[value] || value;
}

function incomeStatusText(value: SettlementIncomeEntry['entryStatus']) {
  const map: Record<SettlementIncomeEntry['entryStatus'], string> = {
    PENDING: '待结算',
    SETTLED: '已结算',
    FROZEN: '已冻结'
  };
  return map[value] || value;
}

function incomeLineText(value: SettlementIncomeEntry['lineType']) {
  const map: Partial<Record<SettlementIncomeEntry['lineType'], string>> = {
    STORE_OPERATION_SHARE: '门店运营分润',
    MERCHANT_ORDER_FEE: '门店办单费',
    MERCHANT_RENT_SHARE: '门店租金分成'
  };
  return map[value] || value;
}

function stockLogText(value: SparePartStockLog['changeType']) {
  const map: Record<SparePartStockLog['changeType'], string> = {
    INBOUND: '历史入库',
    CONSUME: '历史消耗',
    ADJUST: '历史调整',
    PLATFORM_INBOUND: '平台入库',
    PLATFORM_ADJUST: '平台调整',
    STORE_PURCHASE_OUT: '门店采购出库',
    STORE_PURCHASE_IN: '门店采购入库',
    STORE_BUYBACK_OUT: '门店退仓出库',
    STORE_BUYBACK_IN: '平台回收入库',
    STORE_CONSUME: '维修消耗',
    STORE_ADJUST: '门店调整',
    STORE_TRANSFER_OUT: '门店调拨出库',
    STORE_TRANSFER_IN: '门店调拨入库'
  };
  return map[value] || value;
}

function responsibilityText(value: AssetMaintenance['responsibilityType']) {
  const map: Record<AssetMaintenance['responsibilityType'], string> = {
    ROUTINE_MAINTENANCE: '日常资产维护',
    CUSTOMER_DAMAGE: '客户损坏',
    MERCHANT_RESPONSIBILITY: '门店责任',
    PLATFORM_SUBSIDY: '平台兜底'
  };
  return map[value] || value;
}

function leaseText(unit: 'DAY' | 'MONTH', value: number) {
  return `${value}${unit === 'DAY' ? '天' : '个月'}`;
}

function renewalText(item: { autoRenewEnabled?: boolean; renewalUnit?: 'DAY' | 'MONTH' | null; renewalValue?: number | null; renewalAmount?: number | null; renewalCount?: number | null }) {
  if (!item.autoRenewEnabled) {
    return '到期未还不自动续租';
  }
  return `到期未还按 ${money(item.renewalAmount || 0)} / ${leaseText(item.renewalUnit || 'MONTH', item.renewalValue || 1)} 自动续租，已续 ${item.renewalCount || 0} 次`;
}

function leaseBonusTypeText(value: 'REVIEW' | 'CAMPAIGN') {
  return value === 'REVIEW' ? '好评赠送' : '活动赠送';
}

function dateText(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function money(value: number | string | null | undefined) {
  return `¥${Number(value || 0).toFixed(2)}`;
}
</script>

<style scoped>
.page {
  padding: 48rpx 32rpx;
  min-height: 100vh;
  background: #f5f7fb;
}

.title {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.3;
}

.subtitle {
  margin-top: 16rpx;
  color: #667085;
  font-size: 28rpx;
  line-height: 1.5;
}

.panel {
  margin-top: 36rpx;
  padding: 28rpx;
  border-radius: 12rpx;
  background: #ffffff;
  border: 1rpx solid #e6e9ef;
}

.field {
  margin-bottom: 24rpx;
  font-size: 28rpx;
}

.field input {
  margin-top: 12rpx;
  height: 76rpx;
  padding: 0 20rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  background: #ffffff;
}

.field.compact {
  margin-bottom: 16rpx;
}

.picker {
  margin-top: 12rpx;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 20rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  background: #ffffff;
}

.compact-picker {
  margin-top: 0;
  min-width: 150rpx;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.order-create-action {
  margin-bottom: 16rpx;
}

.order-create-form {
  background: #f8fafc;
}

.lease-bonus-form {
  margin-top: 16rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #e6e9ef;
}

.filter-input {
  flex: 1;
  height: 64rpx;
  padding: 0 16rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  font-size: 24rpx;
  box-sizing: border-box;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  padding: 14rpx 0;
  font-size: 28rpx;
}

.label {
  color: #667085;
}

.store-list {
  margin-top: 24rpx;
}

.store-item {
  padding: 22rpx;
  border: 1rpx solid #d0d5dd;
  border-radius: 8rpx;
  margin-bottom: 18rpx;
  background: #ffffff;
}

.store-item.active {
  border-color: #1677ff;
  background: #eef5ff;
}

.store-name {
  font-weight: 700;
  font-size: 30rpx;
}

.store-address,
.store-code {
  margin-top: 8rpx;
  color: #667085;
  font-size: 24rpx;
  line-height: 1.4;
}

.asset-panel {
  margin-top: 28rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.section-subtitle {
  margin-top: 22rpx;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.empty {
  color: #667085;
  font-size: 26rpx;
  padding: 18rpx 0;
}

.asset-item {
  padding: 20rpx;
  border: 1rpx solid #e6e9ef;
  border-radius: 8rpx;
  margin-bottom: 14rpx;
}

.asset-item.active {
  border-color: #1677ff;
  background: #f4f8ff;
}

.asset-main {
  display: flex;
  justify-content: space-between;
  font-size: 28rpx;
  font-weight: 700;
}

.asset-status {
  color: #1677ff;
}

.asset-sub {
  margin-top: 8rpx;
  color: #667085;
  font-size: 24rpx;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.mini-btn {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 24rpx;
  border-radius: 8rpx;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 14rpx;
}

.tag {
  padding: 6rpx 10rpx;
  border-radius: 8rpx;
  background: #eef2f6;
  color: #475467;
  font-size: 22rpx;
}

.bill-row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 16rpx 0;
  border-top: 1rpx solid #eef2f6;
}

.bill-title {
  font-size: 26rpx;
  font-weight: 700;
}

.bill-amount {
  flex-shrink: 0;
  text-align: right;
  color: #1677ff;
  font-size: 24rpx;
  font-weight: 700;
}

.settlement-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.settlement-grid view {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 16rpx;
  border-radius: 8rpx;
  background: #f8fafc;
  font-size: 24rpx;
}

.settlement-grid text:first-child {
  color: #667085;
}

.settlement-grid text:last-child {
  color: #101828;
  font-weight: 700;
}

.primary,
.secondary {
  margin-top: 20rpx;
  border-radius: 8rpx;
}

.primary {
  color: #ffffff;
  background: #1677ff;
}
</style>
