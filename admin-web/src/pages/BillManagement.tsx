import { CalendarOutlined, CloseCircleOutlined, FileAddOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { Dayjs } from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { http } from '../services/request';
import type { BillBatch, BillGenerationResult, BillStatus, BillType, RentalBill, RentalOrder } from '../types/api';

const billTypeOptions: { label: string; value: BillType }[] = [
  { label: '首期账单', value: 'INITIAL' },
  { label: '周期账单', value: 'PERIODIC' },
  { label: '逾期账单', value: 'OVERDUE' }
];

const billStatusOptions: { label: string; value: BillStatus; color: string }[] = [
  { label: '待支付', value: 'PENDING_PAYMENT', color: 'orange' },
  { label: '支付中', value: 'PAYING', color: 'blue' },
  { label: '已支付', value: 'PAID', color: 'green' },
  { label: '已逾期', value: 'OVERDUE', color: 'red' },
  { label: '已关闭', value: 'CANCELLED', color: 'default' },
  { label: '支付失败', value: 'FAILED', color: 'red' }
];

type GenerateForm = {
  orderId: number;
  billType: BillType;
  periodNo?: number;
  overdueAmount?: number;
  dueAt?: Dayjs;
  remark?: string;
};

type PlanForm = {
  orderId: number;
  remark?: string;
};

type CancelForm = {
  remark?: string;
};

export function BillManagement() {
  const [bills, setBills] = useState<RentalBill[]>([]);
  const [batches, setBatches] = useState<BillBatch[]>([]);
  const [orders, setOrders] = useState<RentalOrder[]>([]);
  const [statusFilter, setStatusFilter] = useState<BillStatus | undefined>();
  const [generateOpen, setGenerateOpen] = useState(false);
  const [planOpen, setPlanOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [selectedBill, setSelectedBill] = useState<RentalBill | null>(null);
  const [generateForm] = Form.useForm<GenerateForm>();
  const [planForm] = Form.useForm<PlanForm>();
  const [cancelForm] = Form.useForm<CancelForm>();

  useEffect(() => {
    void loadAll();
  }, []);

  const orderOptions = useMemo(() => orders.map((item) => ({
    label: `${item.orderNo} / 门店 ${item.storeId} / 应付 ${item.payableAmount}`,
    value: item.id
  })), [orders]);

  async function loadAll() {
    const [billData, batchData, orderData] = await Promise.all([
      http.get<unknown, RentalBill[]>('/api/admin/bills', { params: { status: statusFilter } }),
      http.get<unknown, BillBatch[]>('/api/admin/bills/batches'),
      http.get<unknown, RentalOrder[]>('/api/admin/orders')
    ]);
    setBills(billData);
    setBatches(batchData);
    setOrders(orderData);
  }

  async function reloadWithStatus(value?: BillStatus) {
    setStatusFilter(value);
    const billData = await http.get<unknown, RentalBill[]>('/api/admin/bills', { params: { status: value } });
    setBills(billData);
  }

  async function generateBill(values: GenerateForm) {
    const result = await http.post<unknown, BillGenerationResult>('/api/admin/bills/generate', {
      ...values,
      dueAt: values.dueAt?.format('YYYY-MM-DDTHH:mm:ss')
    });
    setGenerateOpen(false);
    generateForm.resetFields();
    message.success(result.bills.length > 0 ? '账单已生成' : '该账单已存在，未重复生成');
    await loadAll();
  }

  async function generatePlan(values: PlanForm) {
    const result = await http.post<unknown, BillGenerationResult>('/api/admin/bills/generate-plan', values);
    setPlanOpen(false);
    planForm.resetFields();
    message.success(result.bills.length > 0 ? `已生成 ${result.bills.length} 笔账单` : '账单计划已存在，未重复生成');
    await loadAll();
  }

  async function cancelBill(values: CancelForm) {
    if (!selectedBill) return;
    await http.post(`/api/admin/bills/${selectedBill.id}/cancel`, values);
    setCancelOpen(false);
    cancelForm.resetFields();
    message.success('账单已关闭');
    await loadAll();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={4}>账单管理</Typography.Title>
        <Space>
          <Select
            allowClear
            placeholder="账单状态"
            value={statusFilter}
            options={billStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={reloadWithStatus}
          />
          <Button icon={<ReloadOutlined />} onClick={loadAll}>刷新</Button>
          <Button icon={<CalendarOutlined />} onClick={() => setPlanOpen(true)}>生成整单计划</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            generateForm.setFieldsValue({ billType: 'INITIAL' });
            setGenerateOpen(true);
          }}>生成单笔账单</Button>
        </Space>
      </Space>

      <div className="section">
        <Typography.Title level={5}>账单列表</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={bills}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" className="page-stack">
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={record.items}
                  pagination={false}
                  columns={[
                    { title: '明细类型', dataIndex: 'itemType', render: itemTypeText },
                    { title: '名称', dataIndex: 'itemName' },
                    { title: '金额', dataIndex: 'amount' }
                  ]}
                />
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={record.logs}
                  pagination={false}
                  columns={[
                    { title: '操作', dataIndex: 'operationType' },
                    { title: '原状态', dataIndex: 'fromStatus', render: statusTag },
                    { title: '新状态', dataIndex: 'toStatus', render: statusTag },
                    { title: '备注', dataIndex: 'remark' },
                    { title: '时间', dataIndex: 'createdAt' }
                  ]}
                />
              </Space>
            )
          }}
          columns={[
            { title: '账单号', dataIndex: 'billNo' },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '类型', dataIndex: 'billType', render: billTypeText },
            { title: '期数', dataIndex: 'periodNo' },
            { title: '状态', dataIndex: 'billStatus', render: statusTag },
            { title: '应付', dataIndex: 'payableAmount' },
            { title: '已付', dataIndex: 'paidAmount' },
            { title: '逾期金额', dataIndex: 'overdueAmount' },
            { title: '到期时间', dataIndex: 'dueAt' },
            { title: '批次', dataIndex: 'generatedBatchNo' },
            {
              title: '操作',
              render: (_, record) => (
                <Button
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  disabled={record.billStatus === 'PAID' || record.billStatus === 'CANCELLED'}
                  onClick={() => {
                    setSelectedBill(record);
                    cancelForm.resetFields();
                    setCancelOpen(true);
                  }}
                >
                  关闭
                </Button>
              )
            }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>生成批次</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={batches}
          pagination={false}
          columns={[
            { title: '批次号', dataIndex: 'batchNo' },
            { title: '类型', dataIndex: 'generationType', render: generationTypeText },
            { title: '订单 ID', dataIndex: 'orderId', render: (value) => value || '-' },
            { title: '生成数量', dataIndex: 'generatedCount' },
            { title: '备注', dataIndex: 'remark' },
            { title: '创建时间', dataIndex: 'createdAt' }
          ]}
        />
      </div>

      <Modal title="生成单笔账单" open={generateOpen} onCancel={() => setGenerateOpen(false)} onOk={() => generateForm.submit()} destroyOnHidden>
        <Form form={generateForm} layout="vertical" onFinish={generateBill}>
          <Form.Item name="orderId" label="订单" rules={[{ required: true, message: '请选择订单' }]}><Select showSearch options={orderOptions} optionFilterProp="label" /></Form.Item>
          <Form.Item name="billType" label="账单类型" rules={[{ required: true, message: '请选择账单类型' }]}><Select options={billTypeOptions} /></Form.Item>
          <Form.Item name="periodNo" label="期数"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="overdueAmount" label="逾期金额"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="dueAt" label="到期时间"><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="生成整单账单计划" open={planOpen} onCancel={() => setPlanOpen(false)} onOk={() => planForm.submit()} destroyOnHidden>
        <Form form={planForm} layout="vertical" onFinish={generatePlan}>
          <Form.Item name="orderId" label="订单" rules={[{ required: true, message: '请选择订单' }]}><Select showSearch options={orderOptions} optionFilterProp="label" /></Form.Item>
          <Form.Item name="remark" label="备注"><Input prefix={<FileAddOutlined />} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="关闭账单" open={cancelOpen} onCancel={() => setCancelOpen(false)} onOk={() => cancelForm.submit()} destroyOnHidden>
        <Form form={cancelForm} layout="vertical" onFinish={cancelBill}>
          <Form.Item name="remark" label="关闭原因"><Input /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function billTypeText(value: BillType) {
  return billTypeOptions.find((item) => item.value === value)?.label ?? value;
}

function statusTag(value?: BillStatus | null) {
  if (!value) return '-';
  const option = billStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}

function itemTypeText(value: string) {
  const map: Record<string, string> = {
    RENT: '租金',
    SIGN_FEE: '签单费',
    DEPOSIT: '押金',
    OVERDUE_FEE: '逾期费用'
  };
  return map[value] ?? value;
}

function generationTypeText(value: string) {
  const map: Record<string, string> = {
    INITIAL: '首期',
    PERIODIC: '周期',
    PLAN: '整单计划',
    OVERDUE: '逾期',
    MANUAL: '手动'
  };
  return map[value] ?? value;
}
