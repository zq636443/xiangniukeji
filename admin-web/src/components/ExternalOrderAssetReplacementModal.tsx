import { Alert, Descriptions, Form, Input, Modal, Select, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { Asset, AssetStatus, ExternalRentalOrder } from '../types/api';

type Scope = 'admin' | 'merchant';

type ReplacementForm = {
  assetType: 'VEHICLE_FRAME' | 'BATTERY';
  newAssetId: number;
  oldAssetResultStatus: AssetStatus;
  remark?: string;
};

type Props = {
  scope: Scope;
  order: ExternalRentalOrder | null;
  assets: Asset[];
  onClose: () => void;
  onReplaced: () => Promise<void> | void;
};

const oldAssetStatusOptions: { label: string; value: AssetStatus }[] = [
  { label: '空闲可用', value: 'IDLE' },
  { label: '待检修', value: 'PENDING_REPAIR' },
  { label: '异常', value: 'EXCEPTION' }
];

export function ExternalOrderAssetReplacementModal({ scope, order, assets, onClose, onReplaced }: Props) {
  const [form] = Form.useForm<ReplacementForm>();
  const [submitting, setSubmitting] = useState(false);
  const assetType = Form.useWatch('assetType', form);

  useEffect(() => {
    if (!order) return;
    form.resetFields();
    form.setFieldsValue({
      assetType: order.frameAssetId ? 'VEHICLE_FRAME' : 'BATTERY',
      oldAssetResultStatus: 'IDLE',
      remark: '补录订单更换资产'
    });
  }, [form, order]);

  const oldAssetId = assetType === 'BATTERY' ? order?.batteryAssetId : order?.frameAssetId;
  const otherAssetId = assetType === 'BATTERY' ? order?.frameAssetId : order?.batteryAssetId;
  const oldAsset = assets.find((item) => item.id === oldAssetId);
  const candidateOptions = useMemo(() => {
    if (!order) return [];
    return assets
      .filter((item) => item.status === 'IDLE'
        && item.currentStoreId === order.storeId
        && item.currentMerchantId === order.merchantId
        && item.id !== otherAssetId
        && item.id !== oldAssetId)
      .map((item) => ({ label: assetLabel(item), value: item.id }));
  }, [assets, oldAssetId, order, otherAssetId]);

  function closeModal() {
    if (submitting) return;
    form.resetFields();
    onClose();
  }

  async function submit(values: ReplacementForm) {
    if (!order) return;
    const expectedOldAssetId = values.assetType === 'BATTERY' ? order.batteryAssetId : order.frameAssetId;
    if (!expectedOldAssetId) {
      message.error('当前资产已变更，请刷新列表后重试');
      return;
    }
    setSubmitting(true);
    try {
      const endpoint = scope === 'merchant' ? '/api/merchant/external-orders' : '/api/admin/external-orders';
      await http.post(`${endpoint}/${order.id}/replace-asset`, {
        assetType: values.assetType,
        expectedOldAssetId,
        newAssetId: values.newAssetId,
        oldAssetResultStatus: values.oldAssetResultStatus,
        remark: values.remark?.trim() || '补录订单更换资产'
      });
      message.success(values.assetType === 'BATTERY' ? '补录订单第二资产已更换' : '补录订单主资产已更换');
      form.resetFields();
      onClose();
      try {
        await onReplaced();
      } catch {
        message.warning('资产已更换，但列表刷新失败，请手动刷新后查看');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补录订单资产更换失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="更换补录订单资产"
      open={Boolean(order)}
      onCancel={closeModal}
      onOk={() => form.submit()}
      confirmLoading={submitting}
      okText="确认更换"
      cancelText="取消"
      destroyOnHidden
    >
      {order ? (
        <>
          <Alert
            type="info"
            showIcon
            message="更换时间会作为续租资产归属的分界"
            description="补录首期分润保持创建时归属；进行中且未锁定的续租收益按更换时点拆分，已锁定续租保留原归属，后续新续租按新资产计算。"
            style={{ marginBottom: 16 }}
          />
          <Descriptions size="small" bordered column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="补录单号">{order.recordNo}</Descriptions.Item>
            <Descriptions.Item label="客户">{order.customerName}</Descriptions.Item>
            <Descriptions.Item label="门店">{order.storeName || `#${order.storeId}`}</Descriptions.Item>
            <Descriptions.Item label="当前主资产">{order.frameAssetSerialNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="当前第二资产">{order.batteryAssetSerialNo || '-'}</Descriptions.Item>
          </Descriptions>
        </>
      ) : null}
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item name="assetType" label="要更换的资产位" rules={[{ required: true, message: '请选择要更换的资产位' }]}>
          <Select
            options={[
              { label: '主资产', value: 'VEHICLE_FRAME', disabled: !order?.frameAssetId },
              { label: '第二资产', value: 'BATTERY', disabled: !order?.batteryAssetId }
            ]}
            onChange={() => form.setFieldValue('newAssetId', undefined)}
          />
        </Form.Item>
        <Form.Item label="当前资产">
          <Typography.Text>{oldAsset ? assetLabel(oldAsset) : currentAssetText(order, assetType)}</Typography.Text>
        </Form.Item>
        <Form.Item name="newAssetId" label="新资产" rules={[{ required: true, message: '请选择新资产' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="输入序列号、资产编号或类型搜索"
            notFoundContent="该门店暂无可更换的空闲资产"
            options={candidateOptions}
          />
        </Form.Item>
        <Form.Item name="oldAssetResultStatus" label="旧资产处理状态" rules={[{ required: true, message: '请选择旧资产处理状态' }]}>
          <Select options={oldAssetStatusOptions} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} maxLength={255} showCount placeholder="请记录更换原因、旧资产情况等" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function assetLabel(asset: Asset) {
  return `${asset.serialNo} / ${asset.assetCode} / ${asset.assetTypeName || asset.assetTypeCode}`;
}

function currentAssetText(order: ExternalRentalOrder | null, assetType?: ReplacementForm['assetType']) {
  if (!order) return '-';
  return assetType === 'BATTERY'
    ? order.batteryAssetSerialNo || (order.batteryAssetId ? `#${order.batteryAssetId}` : '-')
    : order.frameAssetSerialNo || (order.frameAssetId ? `#${order.frameAssetId}` : '-');
}
