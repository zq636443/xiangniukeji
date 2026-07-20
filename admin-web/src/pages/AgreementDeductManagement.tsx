import { DisconnectOutlined, FileAddOutlined, PlayCircleOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { AgreementNotify, AgreementStatus, DeductBatch, DeductRecord, DeductStatus, PayAgreement, RenewalRunResponse } from '../types/api';

const agreementStatusOptions: { label: string; value: AgreementStatus; color: string }[] = [
  { label: '签约中', value: 'SIGNING', color: 'blue' },
  { label: '已签约', value: 'SIGNED', color: 'green' },
  { label: '已解约', value: 'UNSIGNED', color: 'default' },
  { label: '已失效', value: 'INVALID', color: 'orange' },
  { label: '失败', value: 'FAILED', color: 'red' }
];

const deductStatusOptions: { label: string; value: DeductStatus; color: string }[] = [
  { label: '待扣款', value: 'PENDING', color: 'default' },
  { label: '扣款中', value: 'PROCESSING', color: 'blue' },
  { label: '成功', value: 'SUCCESS', color: 'green' },
  { label: '失败', value: 'FAILED', color: 'red' }
];

export function AgreementDeductManagement() {
  const [agreements, setAgreements] = useState<PayAgreement[]>([]);
  const [notifies, setNotifies] = useState<AgreementNotify[]>([]);
  const [batches, setBatches] = useState<DeductBatch[]>([]);
  const [records, setRecords] = useState<DeductRecord[]>([]);
  const [agreementStatus, setAgreementStatus] = useState<AgreementStatus | undefined>();
  const [deductStatus, setDeductStatus] = useState<DeductStatus | undefined>();
  const [runOpen, setRunOpen] = useState(false);
  const [limit, setLimit] = useState(50);

  useEffect(() => {
    void loadAll();
  }, []);

  async function loadAll() {
    const [agreementData, notifyData, batchData, recordData] = await Promise.all([
      http.get<unknown, PayAgreement[]>('/api/admin/agreements', { params: { status: agreementStatus } }),
      http.get<unknown, AgreementNotify[]>('/api/admin/agreements/notifies'),
      http.get<unknown, DeductBatch[]>('/api/admin/deductions/batches'),
      http.get<unknown, DeductRecord[]>('/api/admin/deductions/records', { params: { status: deductStatus } })
    ]);
    setAgreements(agreementData);
    setNotifies(notifyData);
    setBatches(batchData);
    setRecords(recordData);
  }

  async function reloadAgreements(value?: AgreementStatus) {
    setAgreementStatus(value);
    setAgreements(await http.get<unknown, PayAgreement[]>('/api/admin/agreements', { params: { status: value } }));
  }

  async function reloadRecords(value?: DeductStatus) {
    setDeductStatus(value);
    setRecords(await http.get<unknown, DeductRecord[]>('/api/admin/deductions/records', { params: { status: value } }));
  }

  async function queryAgreement(record: PayAgreement) {
    await http.post(`/api/admin/agreements/${record.id}/query`);
    message.success('协议状态已同步');
    await loadAll();
  }

  async function unsignAgreement(record: PayAgreement) {
    await http.post(`/api/admin/agreements/${record.id}/unsign`);
    message.success('协议已解约');
    await loadAll();
  }

  async function runDeduct() {
    await http.post('/api/admin/deductions/run', { limit, remark: '后台手动触发扣款' });
    setRunOpen(false);
    message.success('扣款任务已执行');
    await loadAll();
  }

  async function runRenewals() {
    const result = await http.post<unknown, RenewalRunResponse>('/api/admin/order-renewals/run', {
      limit,
      remark: '后台手动生成续租账单'
    });
    message.success(`续租扫描完成：扫描 ${result.scannedCount} 单，生成 ${result.generatedCount} 张`);
    await loadAll();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={4}>签约扣款</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadAll}>刷新</Button>
          <Button icon={<FileAddOutlined />} onClick={runRenewals}>生成续租账单</Button>
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => setRunOpen(true)}>执行到期扣款</Button>
        </Space>
      </Space>

      <div className="section">
        <Space align="center" className="toolbar">
          <Typography.Title level={5}>签约协议</Typography.Title>
          <Select
            allowClear
            placeholder="协议状态"
            value={agreementStatus}
            options={agreementStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={reloadAgreements}
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={agreements}
          pagination={false}
          columns={[
            { title: '外部协议号', dataIndex: 'externalAgreementNo' },
            { title: '支付宝协议号', dataIndex: 'agreementNo', render: (value) => value || '-' },
            { title: '订单 ID', dataIndex: 'orderId', render: (value) => value || '-' },
            { title: '用户 ID', dataIndex: 'userAccountId' },
            { title: '状态', dataIndex: 'agreementStatus', render: agreementStatusTag },
            { title: '单笔上限', dataIndex: 'maxSingleAmount' },
            { title: '签约时间', dataIndex: 'signTime', render: (value) => value || '-' },
            { title: '错误', dataIndex: 'lastError', render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button size="small" icon={<SearchOutlined />} onClick={() => queryAgreement(record)}>查询</Button>
                  <Button size="small" icon={<DisconnectOutlined />} disabled={record.agreementStatus !== 'SIGNED'} onClick={() => unsignAgreement(record)}>解约</Button>
                </Space>
              )
            }
          ]}
        />
      </div>

      <div className="section">
        <Space align="center" className="toolbar">
          <Typography.Title level={5}>扣款记录</Typography.Title>
          <Select
            allowClear
            placeholder="扣款状态"
            value={deductStatus}
            options={deductStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={reloadRecords}
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={records}
          pagination={false}
          columns={[
            { title: '扣款号', dataIndex: 'deductNo' },
            { title: '批次', dataIndex: 'batchNo', render: (value) => value || '-' },
            { title: '账单 ID', dataIndex: 'billId' },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '状态', dataIndex: 'deductStatus', render: deductStatusTag },
            { title: '金额', dataIndex: 'deductAmount' },
            { title: '重试', dataIndex: 'retryCount' },
            { title: '下次重试', dataIndex: 'nextRetryAt', render: (value) => value || '-' },
            { title: '支付宝交易号', dataIndex: 'alipayTradeNo', render: (value) => value || '-' },
            { title: '失败原因', dataIndex: 'lastError', render: (value) => value || '-' }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>扣款批次</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={batches}
          pagination={false}
          columns={[
            { title: '批次号', dataIndex: 'batchNo' },
            { title: '状态', dataIndex: 'batchStatus', render: (value) => <Tag>{value === 'FINISHED' ? '已完成' : '执行中'}</Tag> },
            { title: '计划数', dataIndex: 'plannedCount' },
            { title: '成功', dataIndex: 'successCount' },
            { title: '失败', dataIndex: 'failedCount' },
            { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
            { title: '开始时间', dataIndex: 'startedAt', render: (value) => value || '-' },
            { title: '结束时间', dataIndex: 'finishedAt', render: (value) => value || '-' }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>签约通知</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={notifies}
          pagination={false}
          columns={[
            { title: '通知 ID', dataIndex: 'notifyId', render: (value) => value || '-' },
            { title: '协议 ID', dataIndex: 'agreementId', render: (value) => value || '-' },
            { title: '外部协议号', dataIndex: 'externalAgreementNo', render: (value) => value || '-' },
            { title: '支付宝协议号', dataIndex: 'agreementNo', render: (value) => value || '-' },
            { title: '状态', dataIndex: 'agreementStatus', render: (value) => value || '-' },
            { title: '验签', dataIndex: 'verified', render: (value) => <Tag color={value ? 'green' : 'red'}>{value ? '通过' : '失败'}</Tag> },
            { title: '处理', dataIndex: 'processed', render: (value) => <Tag color={value ? 'green' : 'orange'}>{value ? '已处理' : '未处理'}</Tag> },
            { title: '失败原因', dataIndex: 'failureReason', render: (value) => value || '-' }
          ]}
        />
      </div>

      <Modal title="执行到期扣款" open={runOpen} onCancel={() => setRunOpen(false)} onOk={runDeduct}>
        <Space direction="vertical" className="page-stack">
          <Typography.Text>系统会先为到期未归还订单生成续租账单，再扫描到期未支付账单并按已签约协议主动扣款。</Typography.Text>
          <InputNumber min={1} max={50} value={limit} onChange={(value) => setLimit(Number(value || 50))} style={{ width: '100%' }} />
        </Space>
      </Modal>
    </Space>
  );
}

function agreementStatusTag(value: AgreementStatus) {
  const option = agreementStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}

function deductStatusTag(value: DeductStatus) {
  const option = deductStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}
