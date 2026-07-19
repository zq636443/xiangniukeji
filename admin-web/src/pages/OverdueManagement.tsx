import { DownloadOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { CollectionStatus, OverdueCase, OverdueStatus } from '../types/api';

const overdueStatusOptions: { label: string; value: OverdueStatus; color: string }[] = [
  { label: '处理中', value: 'OPEN', color: 'red' },
  { label: '已解决', value: 'RESOLVED', color: 'green' },
  { label: '已关闭', value: 'CLOSED', color: 'default' }
];

const collectionStatusOptions: { label: string; value: CollectionStatus; color: string }[] = [
  { label: '待催缴', value: 'PENDING', color: 'orange' },
  { label: '已联系', value: 'CONTACTED', color: 'blue' },
  { label: '承诺付款', value: 'PROMISED', color: 'purple' },
  { label: '已解决', value: 'RESOLVED', color: 'green' },
  { label: '坏账', value: 'BAD_DEBT', color: 'red' }
];

type CollectionForm = {
  collectionStatus: CollectionStatus;
  remark?: string;
};

export function OverdueManagement() {
  const [cases, setCases] = useState<OverdueCase[]>([]);
  const [overdueStatus, setOverdueStatus] = useState<OverdueStatus | undefined>();
  const [collectionStatus, setCollectionStatus] = useState<CollectionStatus | undefined>();
  const [statMonth, setStatMonth] = useState('');
  const [selectedCase, setSelectedCase] = useState<OverdueCase | null>(null);
  const [collectionOpen, setCollectionOpen] = useState(false);
  const [collectionForm] = Form.useForm<CollectionForm>();

  useEffect(() => {
    void loadCases();
  }, []);

  async function loadCases() {
    setCases(await http.get<unknown, OverdueCase[]>('/api/admin/overdues', {
      params: {
        statMonth: statMonth || undefined,
        overdueStatus,
        collectionStatus
      }
    }));
  }

  async function updateCollection(values: CollectionForm) {
    if (!selectedCase) return;
    await http.post(`/api/admin/overdues/${selectedCase.id}/collection`, values);
    setCollectionOpen(false);
    collectionForm.resetFields();
    message.success('催缴状态已更新');
    await loadCases();
  }

  function exportCsv() {
    const header = ['案件号', '月份', '订单ID', '账单ID', '门店ID', '逾期金额', '未补缴', '失败次数', '逾期状态', '催缴状态', '失败原因'];
    const rows = cases.map((item) => [
      item.caseNo,
      item.statMonth,
      item.orderId,
      item.billId,
      item.storeId,
      item.overdueAmount,
      item.unpaidAmount,
      item.failCount,
      item.overdueStatus,
      item.collectionStatus,
      item.lastFailReason || ''
    ]);
    const csv = [header, ...rows].map((row) => row.map((cell) => `"${String(cell).split('"').join('""')}"`).join(',')).join('\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `overdues-${statMonth || 'all'}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={4}>逾期汇总</Typography.Title>
        <Space>
          <Input placeholder="月份 YYYY-MM" value={statMonth} onChange={(event) => setStatMonth(event.target.value)} style={{ width: 140 }} />
          <Select
            allowClear
            placeholder="逾期状态"
            value={overdueStatus}
            options={overdueStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={setOverdueStatus}
          />
          <Select
            allowClear
            placeholder="催缴状态"
            value={collectionStatus}
            options={collectionStatusOptions.map(({ label, value }) => ({ label, value }))}
            style={{ width: 140 }}
            onChange={setCollectionStatus}
          />
          <Button icon={<ReloadOutlined />} onClick={loadCases}>查询</Button>
          <Button icon={<DownloadOutlined />} onClick={exportCsv}>导出</Button>
        </Space>
      </Space>

      <div className="section">
        <Table
          rowKey="id"
          size="small"
          dataSource={cases}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Table
                rowKey="id"
                size="small"
                dataSource={record.logs}
                pagination={false}
                columns={[
                  { title: '催缴状态', dataIndex: 'collectionStatus', render: collectionTag },
                  { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
                  { title: '操作人', dataIndex: 'operatorAccountId', render: (value) => value || '-' },
                  { title: '时间', dataIndex: 'createdAt' }
                ]}
              />
            )
          }}
          columns={[
            { title: '案件号', dataIndex: 'caseNo' },
            { title: '月份', dataIndex: 'statMonth' },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '账单 ID', dataIndex: 'billId' },
            { title: '门店 ID', dataIndex: 'storeId' },
            { title: '逾期金额', dataIndex: 'overdueAmount' },
            { title: '未补缴', dataIndex: 'unpaidAmount' },
            { title: '失败次数', dataIndex: 'failCount' },
            { title: '逾期状态', dataIndex: 'overdueStatus', render: overdueTag },
            { title: '催缴状态', dataIndex: 'collectionStatus', render: collectionTag },
            { title: '失败原因', dataIndex: 'lastFailReason', render: (value) => value || '-' },
            {
              title: '操作',
              render: (_, record) => (
                <Button
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => {
                    setSelectedCase(record);
                    collectionForm.setFieldsValue({ collectionStatus: record.collectionStatus, remark: record.collectionRemark || undefined });
                    setCollectionOpen(true);
                  }}
                >
                  催缴
                </Button>
              )
            }
          ]}
        />
      </div>

      <Modal title="催缴处理" open={collectionOpen} onCancel={() => setCollectionOpen(false)} onOk={() => collectionForm.submit()} destroyOnHidden>
        <Form form={collectionForm} layout="vertical" onFinish={updateCollection}>
          <Form.Item name="collectionStatus" label="催缴状态" rules={[{ required: true, message: '请选择催缴状态' }]}>
            <Select options={collectionStatusOptions.map(({ label, value }) => ({ label, value }))} />
          </Form.Item>
          <Form.Item name="remark" label="催缴备注"><Input.TextArea rows={4} /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function overdueTag(value: OverdueStatus) {
  const option = overdueStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}

function collectionTag(value: CollectionStatus) {
  const option = collectionStatusOptions.find((item) => item.value === value);
  return <Tag color={option?.color}>{option?.label ?? value}</Tag>;
}
