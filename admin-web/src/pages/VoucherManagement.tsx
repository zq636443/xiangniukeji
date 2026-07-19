import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { StoreSku, VoucherRecord } from '../types/api';

type ExceptionForm = { reason: string };
type XianyuIssueForm = {
  voucherCode: string;
  storeSkuId: number;
  packageId: number;
  voucherAmount: number;
  voucherTitle?: string;
};

export function VoucherManagement() {
  const [records, setRecords] = useState<VoucherRecord[]>([]);
  const [storeSkus, setStoreSkus] = useState<StoreSku[]>([]);
  const [selected, setSelected] = useState<VoucherRecord | null>(null);
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [issueOpen, setIssueOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [platform, setPlatform] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [form] = Form.useForm<ExceptionForm>();
  const [issueForm] = Form.useForm<XianyuIssueForm>();

  async function loadData() {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (platform) params.set('platform', platform);
      if (status) params.set('status', status);
      const query = params.toString() ? `?${params}` : '';
      const [voucherData, storeSkuData] = await Promise.all([
        http.get<unknown, VoucherRecord[]>(`/api/admin/vouchers${query}`),
        http.get<unknown, StoreSku[]>('/api/admin/products/store-skus')
      ]);
      setRecords(voucherData);
      setStoreSkus(storeSkuData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '核销记录加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function markException(values: ExceptionForm) {
    if (!selected) return;
    await http.post(`/api/admin/vouchers/${selected.id}/exception`, values);
    setExceptionOpen(false);
    form.resetFields();
    message.success('已标记异常');
    await loadData();
  }

  async function issueXianyuCode(values: XianyuIssueForm) {
    setIssuing(true);
    try {
      await http.post('/api/admin/vouchers/xianyu-codes', values);
      setIssueOpen(false);
      issueForm.resetFields();
      message.success('闲鱼核销码已下发');
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '闲鱼核销码下发失败');
    } finally {
      setIssuing(false);
    }
  }

  const storeSkuOptions = useMemo(
    () =>
      storeSkus.map((item) => ({
        label: `${item.storeName || '-'} / ${item.displayName}`,
        value: item.id
      })),
    [storeSkus]
  );
  const selectedStoreSkuId = Form.useWatch('storeSkuId', issueForm);
  const packageOptions = useMemo(
    () =>
      (storeSkus.find((item) => item.id === selectedStoreSkuId)?.packages || []).map((item) => ({
        label: `${item.packageName} / ${item.leaseValue}${item.leaseUnit === 'DAY' ? '天' : '月'} / 成交 ${moneyNumber(item.rentalAmount)}`,
        value: item.packageId,
        rentalAmount: item.rentalAmount
      })),
    [selectedStoreSkuId, storeSkus]
  );

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>平台核销</Typography.Title>
        <Space>
          <Button
            type="primary"
            onClick={() => {
              issueForm.setFieldsValue({ voucherAmount: 0 });
              setIssueOpen(true);
            }}
          >
            下发闲鱼核销码
          </Button>
          <Select allowClear placeholder="平台" value={platform} onChange={setPlatform} style={{ width: 140 }} options={[
            { label: '抖音', value: 'DOUYIN' },
            { label: '美团', value: 'MEITUAN' },
            { label: '闲鱼', value: 'XIANYU' }
          ]} />
          <Select allowClear placeholder="状态" value={status} onChange={setStatus} style={{ width: 180 }} options={[
            'INPUT', 'PREPARED', 'VERIFIED', 'WAITING_SIGN_FEE', 'CONSUMING', 'CONSUMED', 'FAILED', 'EXCEPTION'
          ].map((value) => ({ label: value, value }))} />
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>查询</Button>
        </Space>
      </Space>
      <section className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={records}
          columns={[
            { title: '平台', dataIndex: 'sourcePlatform', render: platformTag },
            { title: '券码', dataIndex: 'voucherCode' },
            { title: '状态', dataIndex: 'verifyStatus', render: statusTag },
            { title: '用户', dataIndex: 'userAccountId', render: (value) => value || '-' },
            { title: '门店', dataIndex: 'storeId' },
            { title: '订单', dataIndex: 'orderId', render: (value) => value || '-' },
            { title: '签单费账单', dataIndex: 'signFeeBillId', render: (value) => value || '-' },
            { title: '核销金额', dataIndex: 'voucherAmount', render: money },
            { title: '签单费', dataIndex: 'signFeeAmount', render: money },
            { title: '失败原因', dataIndex: 'failureReason', render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Button size="small" onClick={() => { setSelected(record); setExceptionOpen(true); }}>
                  标异常
                </Button>
              )
            }
          ]}
        />
      </section>
      <Modal title="标记异常核销" open={exceptionOpen} onCancel={() => setExceptionOpen(false)} onOk={() => form.submit()} destroyOnHidden>
        <Form form={form} layout="vertical" onFinish={markException}>
          <Form.Item name="reason" label="异常原因" rules={[{ required: true }]}><Input.TextArea rows={4} /></Form.Item>
        </Form>
      </Modal>
      <Modal
        title="下发闲鱼核销码"
        open={issueOpen}
        onCancel={() => setIssueOpen(false)}
        onOk={() => issueForm.submit()}
        confirmLoading={issuing}
        destroyOnHidden
      >
        <Form form={issueForm} layout="vertical" onFinish={issueXianyuCode}>
          <Form.Item name="voucherCode" label="闲鱼核销码" rules={[{ required: true, message: '请输入闲鱼核销码' }]}>
            <Input placeholder="请输入总部下发给用户的闲鱼核销码" />
          </Form.Item>
          <Form.Item name="storeSkuId" label="门店商品" rules={[{ required: true, message: '请选择门店商品' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={storeSkuOptions}
              onChange={() => {
                issueForm.setFieldValue('packageId', undefined);
                issueForm.setFieldValue('voucherAmount', 0);
              }}
            />
          </Form.Item>
          <Form.Item name="packageId" label="套餐" rules={[{ required: true, message: '请选择套餐' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={packageOptions}
              onChange={(value) => {
                const current = packageOptions.find((item) => item.value === value);
                issueForm.setFieldValue('voucherAmount', current?.rentalAmount ?? 0);
              }}
            />
          </Form.Item>
          <Form.Item name="voucherAmount" label="闲鱼成交金额" rules={[{ required: true, message: '请输入闲鱼成交金额' }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="voucherTitle" label="标题">
            <Input placeholder="可空，默认使用门店商品和套餐名称" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function money(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function moneyNumber(value: number) {
  return Number(value || 0).toFixed(2);
}

function platformTag(value: string) {
  const config: Record<string, { color: string; text: string }> = {
    DOUYIN: { color: 'magenta', text: '抖音' },
    MEITUAN: { color: 'gold', text: '美团' },
    XIANYU: { color: 'cyan', text: '闲鱼' }
  };
  const current = config[value] || { color: 'default', text: value };
  return <Tag color={current.color}>{current.text}</Tag>;
}

function statusTag(value: string) {
  const color = value === 'CONSUMED' ? 'green' : ['FAILED', 'EXCEPTION'].includes(value) ? 'red' : 'blue';
  return <Tag color={color}>{value}</Tag>;
}
