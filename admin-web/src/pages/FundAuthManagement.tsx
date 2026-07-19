import { Button, Form, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { FundAuthorization, FundAuthNotify, FundAuthOperation, FundAuthStatus } from '../types/api';

type CaptureForm = { billId?: number; amount: number; remark?: string };
type UnfreezeForm = { amount: number; remark?: string };

const statusOptions: { label: string; value: FundAuthStatus; color: string }[] = [
  { label: '已创建', value: 'CREATED', color: 'default' },
  { label: '授权中', value: 'AUTHORIZING', color: 'blue' },
  { label: '已授权', value: 'AUTHORIZED', color: 'green' },
  { label: '失败', value: 'FAILED', color: 'red' },
  { label: '已撤销', value: 'CANCELLED', color: 'default' },
  { label: '已解冻', value: 'UNFROZEN', color: 'purple' },
  { label: '已扣费', value: 'CAPTURED', color: 'volcano' },
  { label: '已关闭', value: 'CLOSED', color: 'default' }
];

export function FundAuthManagement() {
  const [auths, setAuths] = useState<FundAuthorization[]>([]);
  const [operations, setOperations] = useState<FundAuthOperation[]>([]);
  const [notifies, setNotifies] = useState<FundAuthNotify[]>([]);
  const [status, setStatus] = useState<FundAuthStatus | undefined>();
  const [selected, setSelected] = useState<FundAuthorization | null>(null);
  const [captureOpen, setCaptureOpen] = useState(false);
  const [unfreezeOpen, setUnfreezeOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [captureForm] = Form.useForm<CaptureForm>();
  const [unfreezeForm] = Form.useForm<UnfreezeForm>();

  async function loadData(nextStatus = status) {
    setLoading(true);
    try {
      const [authData, notifyData] = await Promise.all([
        http.get<unknown, FundAuthorization[]>('/api/admin/fund-auths', { params: { status: nextStatus } }),
        http.get<unknown, FundAuthNotify[]>('/api/admin/fund-auths/notifies')
      ]);
      setAuths(authData);
      setNotifies(notifyData);
      if (selected) {
        await loadOperations(selected.id);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '资金授权数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function loadOperations(id: number) {
    const data = await http.get<unknown, FundAuthOperation[]>(`/api/admin/fund-auths/${id}/operations`);
    setOperations(data);
  }

  async function chooseAuth(record: FundAuthorization) {
    setSelected(record);
    await loadOperations(record.id);
  }

  async function queryAuth(record: FundAuthorization) {
    await http.post(`/api/admin/fund-auths/${record.id}/query`);
    message.success('授权状态已同步');
    await loadData();
  }

  async function capture(values: CaptureForm) {
    if (!selected) return;
    await http.post(`/api/admin/fund-auths/${selected.id}/capture`, values);
    setCaptureOpen(false);
    captureForm.resetFields();
    message.success('授权扣费已完成');
    await loadData();
  }

  async function unfreeze(values: UnfreezeForm) {
    if (!selected) return;
    await http.post(`/api/admin/fund-auths/${selected.id}/unfreeze`, values);
    setUnfreezeOpen(false);
    unfreezeForm.resetFields();
    message.success('授权解冻已完成');
    await loadData();
  }

  async function cancelAuth(record: FundAuthorization) {
    await http.post(`/api/admin/fund-auths/${record.id}/cancel`, { remark: '后台撤销授权' });
    message.success('授权已撤销');
    await loadData();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>资金授权</Typography.Title>
        <Space>
          <Select
            allowClear
            placeholder="授权状态"
            style={{ width: 160 }}
            value={status}
            options={statusOptions.map(({ label, value }) => ({ label, value }))}
            onChange={(value) => {
              setStatus(value);
              loadData(value);
            }}
          />
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => loadData()}>刷新</Button>
        </Space>
      </Space>

      <section className="section">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={auths}
          onRow={(record) => ({ onClick: () => chooseAuth(record) })}
          columns={[
            { title: '授权单号', dataIndex: 'authOrderNo' },
            { title: '订单', dataIndex: 'orderId' },
            { title: '用户', dataIndex: 'userAccountId' },
            { title: '状态', dataIndex: 'authStatus', render: statusTag },
            { title: '授权金额', dataIndex: 'authAmount' },
            { title: '已扣费', dataIndex: 'capturedAmount' },
            { title: '已解冻', dataIndex: 'releasedAmount' },
            { title: '支付宝授权号', dataIndex: 'alipayAuthNo', render: (value) => value || '-' },
            { title: '错误', dataIndex: 'lastError', ellipsis: true, render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Space onClick={(event) => event.stopPropagation()}>
                  <Button size="small" onClick={() => queryAuth(record)}>查询</Button>
                  <Button size="small" type="primary" disabled={record.authStatus !== 'AUTHORIZED'} onClick={() => { setSelected(record); setCaptureOpen(true); }}>
                    扣费
                  </Button>
                  <Button size="small" disabled={record.authStatus !== 'AUTHORIZED'} onClick={() => { setSelected(record); unfreezeForm.setFieldsValue({ amount: remainAmount(record) }); setUnfreezeOpen(true); }}>
                    解冻
                  </Button>
                  <Button size="small" danger disabled={!['CREATED', 'AUTHORIZING', 'AUTHORIZED'].includes(record.authStatus)} onClick={() => cancelAuth(record)}>
                    撤销
                  </Button>
                </Space>
              )
            }
          ]}
        />
      </section>

      <section className="section">
        <Typography.Title level={4}>操作流水 {selected ? selected.authOrderNo : ''}</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          pagination={{ pageSize: 6 }}
          dataSource={operations}
          columns={[
            { title: '流水号', dataIndex: 'operationNo' },
            { title: '类型', dataIndex: 'operationType' },
            { title: '状态', dataIndex: 'operationStatus', render: operationTag },
            { title: '金额', dataIndex: 'amount' },
            { title: '账单', dataIndex: 'billId', render: (value) => value || '-' },
            { title: '支付单', dataIndex: 'paymentId', render: (value) => value || '-' },
            { title: '支付宝交易', dataIndex: 'alipayTradeNo', render: (value) => value || '-' },
            { title: '失败原因', dataIndex: 'failureReason', ellipsis: true, render: (value) => value || '-' }
          ]}
        />
      </section>

      <section className="section">
        <Typography.Title level={4}>授权回调</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          pagination={{ pageSize: 6 }}
          dataSource={notifies}
          columns={[
            { title: '通知 ID', dataIndex: 'notifyId', render: (value) => value || '-' },
            { title: '授权单', dataIndex: 'outOrderNo', render: (value) => value || '-' },
            { title: '支付宝授权号', dataIndex: 'authNo', render: (value) => value || '-' },
            { title: '状态', dataIndex: 'authStatus', render: (value) => value || '-' },
            { title: '验签', dataIndex: 'verified', render: (value: boolean) => <Tag color={value ? 'green' : 'red'}>{value ? '通过' : '失败'}</Tag> },
            { title: '处理', dataIndex: 'processed', render: (value: boolean) => <Tag color={value ? 'green' : 'red'}>{value ? '完成' : '失败'}</Tag> },
            { title: '失败原因', dataIndex: 'failureReason', ellipsis: true, render: (value) => value || '-' }
          ]}
        />
      </section>

      <Modal title="授权扣费" open={captureOpen} onCancel={() => setCaptureOpen(false)} onOk={() => captureForm.submit()} destroyOnHidden>
        <Form form={captureForm} layout="vertical" onFinish={capture}>
          <Form.Item name="billId" label="关联账单 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="amount" label="扣费金额" rules={[{ required: true, message: '请输入扣费金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Select
              options={[
                { label: '逾期费用', value: '逾期费用' },
                { label: '损坏扣费', value: '损坏扣费' },
                { label: '押金转支付', value: '押金转支付' }
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="授权解冻" open={unfreezeOpen} onCancel={() => setUnfreezeOpen(false)} onOk={() => unfreezeForm.submit()} destroyOnHidden>
        <Form form={unfreezeForm} layout="vertical" onFinish={unfreeze}>
          <Form.Item name="amount" label="解冻金额" rules={[{ required: true, message: '请输入解冻金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Select options={[{ label: '归还后释放剩余额度', value: '归还后释放剩余额度' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function remainAmount(record: FundAuthorization) {
  return Number(record.frozenAmount || 0) - Number(record.capturedAmount || 0) - Number(record.releasedAmount || 0);
}

function statusTag(value: FundAuthStatus) {
  const option = statusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label || value}</Tag>;
}

function operationTag(value: FundAuthOperation['operationStatus']) {
  const color = value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'red' : 'blue';
  return <Tag color={color}>{value}</Tag>;
}
