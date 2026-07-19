import { ReloadOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons';
import { Button, Form, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { PayStatus, PaymentCallback, PaymentOrder } from '../types/api';

const payStatusOptions: { label: string; value: PayStatus; color: string }[] = [
  { label: '已创建', value: 'CREATED', color: 'default' },
  { label: '支付中', value: 'PAYING', color: 'blue' },
  { label: '已支付', value: 'PAID', color: 'green' },
  { label: '失败', value: 'FAILED', color: 'red' },
  { label: '已关闭', value: 'CLOSED', color: 'default' },
  { label: '退款中', value: 'REFUNDING', color: 'gold' },
  { label: '已退款', value: 'REFUNDED', color: 'purple' }
];

type RefundForm = {
  refundAmount: number;
};

export function PaymentManagement() {
  const [payments, setPayments] = useState<PaymentOrder[]>([]);
  const [callbacks, setCallbacks] = useState<PaymentCallback[]>([]);
  const [statusFilter, setStatusFilter] = useState<PayStatus | undefined>();
  const [selectedPayment, setSelectedPayment] = useState<PaymentOrder | null>(null);
  const [refundOpen, setRefundOpen] = useState(false);
  const [refundForm] = Form.useForm<RefundForm>();

  useEffect(() => {
    void loadAll();
  }, []);

  async function loadAll() {
    const [paymentData, callbackData] = await Promise.all([
      http.get<unknown, PaymentOrder[]>('/api/admin/payments', { params: { status: statusFilter } }),
      http.get<unknown, PaymentCallback[]>('/api/admin/payments/callbacks')
    ]);
    setPayments(paymentData);
    setCallbacks(callbackData);
  }

  async function reloadWithStatus(value?: PayStatus) {
    setStatusFilter(value);
    const paymentData = await http.get<unknown, PaymentOrder[]>('/api/admin/payments', { params: { status: value } });
    setPayments(paymentData);
  }

  async function queryPayment(record: PaymentOrder) {
    await http.post(`/api/admin/payments/${record.id}/query`);
    message.success('支付状态已同步');
    await loadAll();
  }

  async function refundPayment(values: RefundForm) {
    if (!selectedPayment) return;
    await http.post(`/api/admin/payments/${selectedPayment.id}/refund`, values);
    setRefundOpen(false);
    refundForm.resetFields();
    message.success('退款已提交');
    await loadAll();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={4}>支付管理</Typography.Title>
        <Space>
          <Select
            allowClear
            placeholder="支付状态"
            value={statusFilter}
            options={payStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={reloadWithStatus}
          />
          <Button icon={<ReloadOutlined />} onClick={loadAll}>刷新</Button>
        </Space>
      </Space>

      <div className="section">
        <Typography.Title level={5}>支付单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={payments}
          pagination={false}
          columns={[
            { title: '支付单号', dataIndex: 'paymentNo' },
            { title: '账单 ID', dataIndex: 'billId' },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '渠道', dataIndex: 'payChannel' },
            { title: '状态', dataIndex: 'payStatus', render: statusTag },
            { title: '应付', dataIndex: 'payAmount' },
            { title: '已付', dataIndex: 'paidAmount' },
            { title: '已退', dataIndex: 'refundAmount' },
            { title: '支付宝交易号', dataIndex: 'alipayTradeNo', render: (value) => value || '-' },
            { title: '错误', dataIndex: 'lastError', render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" icon={<SearchOutlined />} onClick={() => queryPayment(record)}>查询</Button>
                  <Button
                    size="small"
                    icon={<UndoOutlined />}
                    disabled={record.payStatus !== 'PAID'}
                    onClick={() => {
                      setSelectedPayment(record);
                      refundForm.setFieldsValue({ refundAmount: record.paidAmount - record.refundAmount });
                      setRefundOpen(true);
                    }}
                  >
                    退款
                  </Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>支付宝回调记录</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={callbacks}
          pagination={false}
          columns={[
            { title: '通知 ID', dataIndex: 'notifyId', render: (value) => value || '-' },
            { title: '支付单 ID', dataIndex: 'paymentId', render: (value) => value || '-' },
            { title: '支付单号', dataIndex: 'outTradeNo', render: (value) => value || '-' },
            { title: '支付宝交易号', dataIndex: 'alipayTradeNo', render: (value) => value || '-' },
            { title: '交易状态', dataIndex: 'tradeStatus', render: (value) => value || '-' },
            { title: '金额', dataIndex: 'totalAmount', render: (value) => value ?? '-' },
            { title: '验签', dataIndex: 'verified', render: (value) => <Tag color={value ? 'green' : 'red'}>{value ? '通过' : '失败'}</Tag> },
            { title: '处理', dataIndex: 'processed', render: (value) => <Tag color={value ? 'green' : 'orange'}>{value ? '已处理' : '未处理'}</Tag> },
            { title: '失败原因', dataIndex: 'failureReason', render: (value) => value || '-' },
            { title: '接收时间', dataIndex: 'receivedAt' }
          ]}
        />
      </div>

      <Modal title="退款" open={refundOpen} onCancel={() => setRefundOpen(false)} onOk={() => refundForm.submit()} destroyOnHidden>
        <Form form={refundForm} layout="vertical" onFinish={refundPayment}>
          <Form.Item name="refundAmount" label="退款金额" rules={[{ required: true, message: '请输入退款金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function statusTag(value: PayStatus) {
  const option = payStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}
