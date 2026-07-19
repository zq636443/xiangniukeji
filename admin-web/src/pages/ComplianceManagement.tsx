import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { ContractNotifyRecord, ContractTemplate, IdentityVerification, RentalContractRecord } from '../types/api';

type TemplateForm = {
  templateCode: string;
  templateName: string;
  contractType: 'RENTAL' | 'SALE';
  versionNo: string;
  providerTemplateId?: string;
  content: string;
  remark?: string;
};

type GenerateForm = { orderId: number; templateId?: number };
type SignForm = { provider?: string; externalFlowId?: string; signUrl?: string };
type ArchiveForm = { archivePdfUrl: string };

const defaultTemplate = [
  '享牛电车租赁合同',
  '订单号：{{orderNo}}',
  '承租人：{{userName}}，证件号：{{idNo}}',
  '租期：{{leaseText}}，期数：{{totalPeriods}}',
  '租金：{{rentalAmount}}，签单费：{{signFeeAmount}}，押金：{{depositAmount}}，应付：{{payableAmount}}',
  '本合同不写入具体车架号、电池号；实际交付资产以取车交接单、资产更换单、归还单为准。',
  '签署日期：{{signDate}}'
].join('\\n');

export function ComplianceManagement() {
  const [identities, setIdentities] = useState<IdentityVerification[]>([]);
  const [templates, setTemplates] = useState<ContractTemplate[]>([]);
  const [contracts, setContracts] = useState<RentalContractRecord[]>([]);
  const [notifies, setNotifies] = useState<ContractNotifyRecord[]>([]);
  const [selectedContract, setSelectedContract] = useState<RentalContractRecord | null>(null);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [signOpen, setSignOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [templateForm] = Form.useForm<TemplateForm>();
  const [generateForm] = Form.useForm<GenerateForm>();
  const [signForm] = Form.useForm<SignForm>();
  const [archiveForm] = Form.useForm<ArchiveForm>();

  async function loadData() {
    setLoading(true);
    try {
      const [identityData, templateData, contractData, notifyData] = await Promise.all([
        http.get<unknown, IdentityVerification[]>('/api/admin/identities'),
        http.get<unknown, ContractTemplate[]>('/api/admin/contracts/templates'),
        http.get<unknown, RentalContractRecord[]>('/api/admin/contracts'),
        http.get<unknown, ContractNotifyRecord[]>('/api/admin/contracts/notifies')
      ]);
      setIdentities(identityData);
      setTemplates(templateData);
      setContracts(contractData);
      setNotifies(notifyData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '实名合同数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function createTemplate(values: TemplateForm) {
    await http.post('/api/admin/contracts/templates', values);
    setTemplateOpen(false);
    templateForm.resetFields();
    message.success('合同模板已创建');
    await loadData();
  }

  async function generateContract(values: GenerateForm) {
    await http.post('/api/admin/contracts/generate', values);
    setGenerateOpen(false);
    generateForm.resetFields();
    message.success('订单合同已生成');
    await loadData();
  }

  async function startSign(values: SignForm) {
    if (!selectedContract) return;
    await http.post(`/api/admin/contracts/${selectedContract.id}/start-sign`, values);
    setSignOpen(false);
    signForm.resetFields();
    message.success('签署流程已发起');
    await loadData();
  }

  async function archive(values: ArchiveForm) {
    if (!selectedContract) return;
    await http.post(`/api/admin/contracts/${selectedContract.id}/archive`, values);
    setArchiveOpen(false);
    archiveForm.resetFields();
    message.success('合同已归档');
    await loadData();
  }

  async function updateTemplateStatus(record: ContractTemplate) {
    const nextStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    await http.put(`/api/admin/contracts/templates/${record.id}/status?status=${nextStatus}`);
    message.success(nextStatus === 'ENABLED' ? '模板已启用' : '模板已停用');
    await loadData();
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={3}>实名合同</Typography.Title>
        <Space>
          <Button onClick={() => { templateForm.setFieldsValue({ contractType: 'RENTAL', versionNo: 'v1', content: defaultTemplate }); setTemplateOpen(true); }}>
            新建模板
          </Button>
          <Button type="primary" onClick={() => setGenerateOpen(true)}>生成合同</Button>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
        </Space>
      </Space>

      <Tabs
        items={[
          {
            key: 'identity',
            label: '实名记录',
            children: (
              <section className="section">
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={identities}
                  columns={[
                    { title: '用户', dataIndex: 'userAccountId' },
                    { title: '订单', dataIndex: 'orderId', render: (value) => value || '-' },
                    { title: '姓名', dataIndex: 'realNameMasked', render: (value) => value || '-' },
                    { title: '证件号', dataIndex: 'idNoMasked', render: (value) => value || '-' },
                    { title: 'OCR', dataIndex: 'ocrStatus', render: statusTag },
                    { title: '实名', dataIndex: 'realNameStatus', render: statusTag },
                    { title: '完成时间', dataIndex: 'verifiedAt', render: (value) => value || '-' }
                  ]}
                />
              </section>
            )
          },
          {
            key: 'template',
            label: '合同模板',
            children: (
              <section className="section">
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={templates}
                  columns={[
                    { title: '编码', dataIndex: 'templateCode' },
                    { title: '名称', dataIndex: 'templateName' },
                    { title: '类型', dataIndex: 'contractType' },
                    { title: '版本', dataIndex: 'versionNo' },
                    { title: '状态', dataIndex: 'status', render: statusTag },
                    { title: '服务商模板', dataIndex: 'providerTemplateId', render: (value) => value || '-' },
                    {
                      title: '操作',
                      render: (_, record) => (
                        <Button size="small" onClick={() => updateTemplateStatus(record)}>
                          {record.status === 'ENABLED' ? '停用' : '启用'}
                        </Button>
                      )
                    }
                  ]}
                />
              </section>
            )
          },
          {
            key: 'contract',
            label: '订单合同',
            children: (
              <section className="section">
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={contracts}
                  expandable={{ expandedRowRender: (record) => <pre className="contract-preview">{record.renderedContent}</pre> }}
                  columns={[
                    { title: '合同号', dataIndex: 'contractNo' },
                    { title: '订单', dataIndex: 'orderId' },
                    { title: '用户', dataIndex: 'userAccountId' },
                    { title: '状态', dataIndex: 'contractStatus', render: statusTag },
                    { title: '签署链接', dataIndex: 'signUrl', ellipsis: true, render: (value) => value || '-' },
                    { title: '归档 PDF', dataIndex: 'archivePdfUrl', ellipsis: true, render: (value) => value || '-' },
                    {
                      title: '操作',
                      render: (_, record) => (
                        <Space>
                          <Button size="small" onClick={() => { setSelectedContract(record); setSignOpen(true); }}>发起签署</Button>
                          <Button size="small" onClick={() => { setSelectedContract(record); setArchiveOpen(true); }}>归档</Button>
                        </Space>
                      )
                    }
                  ]}
                />
              </section>
            )
          },
          {
            key: 'notify',
            label: '签署回调',
            children: (
              <section className="section">
                <Table
                  rowKey="id"
                  size="small"
                  loading={loading}
                  dataSource={notifies}
                  expandable={{ expandedRowRender: (record) => <pre className="contract-preview">{record.rawPayload}</pre> }}
                  columns={[
                    { title: '合同', dataIndex: 'contractId', render: (value) => value || '-' },
                    { title: '流程 ID', dataIndex: 'externalFlowId', ellipsis: true, render: (value) => value || '-' },
                    { title: '通知 ID', dataIndex: 'notifyId', ellipsis: true, render: (value) => value || '-' },
                    { title: '状态', dataIndex: 'contractStatus', render: (value) => value ? statusTag(value) : '-' },
                    { title: '验签', dataIndex: 'verified', render: (value) => value ? <Tag color="green">通过</Tag> : <Tag color="red">失败</Tag> },
                    { title: '处理', dataIndex: 'processed', render: (value) => value ? <Tag color="green">已处理</Tag> : <Tag color="red">未处理</Tag> },
                    { title: '失败原因', dataIndex: 'failureReason', render: (value) => value || '-' },
                    { title: '接收时间', dataIndex: 'receivedAt' }
                  ]}
                />
              </section>
            )
          }
        ]}
      />

      <Modal title="新建合同模板" open={templateOpen} onCancel={() => setTemplateOpen(false)} onOk={() => templateForm.submit()} width={760} destroyOnHidden>
        <Form form={templateForm} layout="vertical" onFinish={createTemplate}>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="contractType" label="合同类型" rules={[{ required: true }]}><Select options={[{ label: '租赁', value: 'RENTAL' }, { label: '售卖', value: 'SALE' }]} /></Form.Item>
          <Form.Item name="versionNo" label="版本号" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="providerTemplateId" label="服务商模板 ID"><Input /></Form.Item>
          <Form.Item name="content" label="模板内容" rules={[{ required: true }]}><Input.TextArea rows={8} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="生成订单合同" open={generateOpen} onCancel={() => setGenerateOpen(false)} onOk={() => generateForm.submit()} destroyOnHidden>
        <Form form={generateForm} layout="vertical" onFinish={generateContract}>
          <Form.Item name="orderId" label="订单 ID" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="templateId" label="模板">
            <Select allowClear options={templates.map((item) => ({ label: `${item.templateName} / ${item.versionNo}`, value: item.id }))} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="发起签署" open={signOpen} onCancel={() => setSignOpen(false)} onOk={() => signForm.submit()} destroyOnHidden>
        <Form form={signForm} layout="vertical" onFinish={startSign}>
          <Form.Item name="provider" label="服务商"><Input placeholder="ESIGN / ALIPAY_ESIGN" /></Form.Item>
          <Form.Item name="externalFlowId" label="外部流程 ID"><Input /></Form.Item>
          <Form.Item name="signUrl" label="签署链接"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="归档 PDF" open={archiveOpen} onCancel={() => setArchiveOpen(false)} onOk={() => archiveForm.submit()} destroyOnHidden>
        <Form form={archiveForm} layout="vertical" onFinish={archive}>
          <Form.Item name="archivePdfUrl" label="PDF 地址" rules={[{ required: true }]}><Input /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function statusTag(value: string) {
  const color = ['VERIFIED', 'SUCCESS', 'ENABLED', 'SIGNED', 'ARCHIVED'].includes(value) ? 'green' : ['FAILED', 'DISABLED'].includes(value) ? 'red' : 'blue';
  return <Tag color={color}>{value}</Tag>;
}
