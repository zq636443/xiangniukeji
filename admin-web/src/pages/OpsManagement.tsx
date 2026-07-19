import { Button, Form, Input, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { AuditLogRecord, ExportTaskRecord, ReconciliationBatchRecord } from '../types/api';

type ExportForm = { exportType: string; requestParams?: string };
type ReconcileForm = { channel: string; billDate: string; channelTotalAmount?: number; remark?: string };

export function OpsManagement() {
  const [audits, setAudits] = useState<AuditLogRecord[]>([]);
  const [exports, setExports] = useState<ExportTaskRecord[]>([]);
  const [reconciliations, setReconciliations] = useState<ReconciliationBatchRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [exportForm] = Form.useForm<ExportForm>();
  const [reconcileForm] = Form.useForm<ReconcileForm>();

  async function loadData() {
    setLoading(true);
    try {
      const [auditData, exportData, reconciliationData] = await Promise.all([
        http.get<unknown, AuditLogRecord[]>('/api/admin/ops/audits'),
        http.get<unknown, ExportTaskRecord[]>('/api/admin/ops/exports'),
        http.get<unknown, ReconciliationBatchRecord[]>('/api/admin/ops/reconciliations')
      ]);
      setAudits(auditData);
      setExports(exportData);
      setReconciliations(reconciliationData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '运维数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function createExport(values: ExportForm) {
    await http.post('/api/admin/ops/exports', values);
    exportForm.resetFields();
    message.success('导出任务已创建');
    await loadData();
  }

  async function createReconciliation(values: ReconcileForm) {
    await http.post('/api/admin/ops/reconciliations', values);
    reconcileForm.resetFields();
    message.success('对账批次已生成');
    await loadData();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>运维审计</Typography.Title>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
      </Space>
      <Tabs items={[
        {
          key: 'reconcile',
          label: '对账批次',
          children: (
            <section className="section">
              <Form form={reconcileForm} layout="inline" onFinish={createReconciliation}>
                <Form.Item name="channel" rules={[{ required: true }]}><Select placeholder="渠道" style={{ width: 140 }} options={[{ label: '支付宝', value: 'ALIPAY' }]} /></Form.Item>
                <Form.Item name="billDate" rules={[{ required: true }]}><Input type="date" /></Form.Item>
                <Form.Item name="channelTotalAmount"><Input placeholder="渠道账单金额" type="number" /></Form.Item>
                <Form.Item name="remark"><Input placeholder="备注" /></Form.Item>
                <Button type="primary" htmlType="submit">生成对账</Button>
              </Form>
              <Table rowKey="id" size="small" loading={loading} dataSource={reconciliations} columns={[
                { title: '批次号', dataIndex: 'batchNo' },
                { title: '渠道', dataIndex: 'channel' },
                { title: '账单日', dataIndex: 'billDate' },
                { title: '平台金额', dataIndex: 'platformTotalAmount', render: money },
                { title: '渠道金额', dataIndex: 'channelTotalAmount', render: money },
                { title: '差异数', dataIndex: 'diffCount' },
                { title: '状态', dataIndex: 'batchStatus', render: statusTag },
                { title: '完成时间', dataIndex: 'finishedAt', render: valueOrDash }
              ]} />
            </section>
          )
        },
        {
          key: 'export',
          label: '导出任务',
          children: (
            <section className="section">
              <Form form={exportForm} layout="inline" onFinish={createExport}>
                <Form.Item name="exportType" rules={[{ required: true }]}><Select placeholder="导出类型" style={{ width: 180 }} options={[
                  { label: '订单', value: 'ORDERS' },
                  { label: '账单', value: 'BILLS' },
                  { label: '收益台账', value: 'SETTLEMENT_INCOME' },
                  { label: '核销记录', value: 'VOUCHERS' }
                ]} /></Form.Item>
                <Form.Item name="requestParams"><Input placeholder="筛选参数 JSON" /></Form.Item>
                <Button type="primary" htmlType="submit">创建导出</Button>
              </Form>
              <Table rowKey="id" size="small" loading={loading} dataSource={exports} columns={[
                { title: '任务号', dataIndex: 'taskNo' },
                { title: '类型', dataIndex: 'exportType' },
                { title: '状态', dataIndex: 'taskStatus', render: statusTag },
                { title: '文件', dataIndex: 'fileUrl', render: valueOrDash },
                { title: '创建人', dataIndex: 'createdBy', render: valueOrDash },
                { title: '完成时间', dataIndex: 'finishedAt', render: valueOrDash }
              ]} />
            </section>
          )
        },
        {
          key: 'audit',
          label: '操作审计',
          children: (
            <section className="section">
              <Table rowKey="id" size="small" loading={loading} dataSource={audits} columns={[
                { title: '账号', dataIndex: 'accountId', render: valueOrDash },
                { title: '类型', dataIndex: 'accountType', render: valueOrDash },
                { title: '方法', dataIndex: 'requestMethod' },
                { title: '接口', dataIndex: 'requestUri', ellipsis: true },
                { title: '状态码', dataIndex: 'httpStatus', render: valueOrDash },
                { title: '结果', dataIndex: 'success', render: (value) => value ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag> },
                { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: valueOrDash },
                { title: '时间', dataIndex: 'createdAt' }
              ]} />
            </section>
          )
        }
      ]} />
    </Space>
  );
}

function money(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function valueOrDash(value: unknown) {
  return value || '-';
}

function statusTag(value: string) {
  return <Tag color={value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>;
}
