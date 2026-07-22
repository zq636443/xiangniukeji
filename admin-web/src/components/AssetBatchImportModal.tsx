import { InboxOutlined } from '@ant-design/icons';
import { Modal, Space, Table, Tag, Typography, Upload, message } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { AssetBatchImportResult } from '../types/api';

type AssetImportRow = {
  lineNo: number;
  assetType: string;
  serialNo: string;
  investorCode: string;
  storeCode: string;
  purchaseAmount: string;
  residualValue: string;
  purchasedAt: string;
};

type AssetBatchImportModalProps = {
  open: boolean;
  endpoint: string;
  onClose: () => void;
  onImported: () => void | Promise<void>;
};

type TemplateOptions = {
  storeCode?: string;
};

const templateHeaders = [
  '资产类型(填写类型名称或编码)',
  '资产编号',
  '出资方编码',
  '门店编码',
  '采购金额',
  '报废残值',
  '采购日期(YYYY-MM-DD)'
];

const headerAliases: Record<keyof Omit<AssetImportRow, 'lineNo'>, string[]> = {
  assetType: ['资产类型(填写类型名称或编码)', '资产类型（填写类型名称或编码）', '资产类型(车架/电池/车电一体)', '资产类型（车架/电池/车电一体）', '资产类型(车架/电池)', '资产类型（车架/电池）', '资产类型', 'assetType'],
  serialNo: ['资产编号', '车架号/电池号(车电一体填车架号)', '车架号/电池号（车电一体填车架号）', '车架号/电池号', '车架号／电池号', '序列号', 'serialNo'],
  investorCode: ['出资方编码', 'investorCode'],
  storeCode: ['门店编码', 'storeCode'],
  purchaseAmount: ['采购金额', 'purchaseAmount'],
  residualValue: ['报废残值', '残值', 'residualValue'],
  purchasedAt: ['采购日期(YYYY-MM-DD)', '采购日期（YYYY-MM-DD）', '采购日期', 'purchasedAt']
};

export function AssetBatchImportModal({ open, endpoint, onClose, onImported }: AssetBatchImportModalProps) {
  const [rows, setRows] = useState<AssetImportRow[]>([]);
  const [fileName, setFileName] = useState('');
  const [result, setResult] = useState<AssetBatchImportResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setRows([]);
      setFileName('');
      setResult(null);
    }
  }, [open]);

  async function readFile(file: File) {
    try {
      const parsedRows = parseAssetImportCsv(await file.text());
      if (parsedRows.length === 0) {
        throw new Error('模板中没有可导入的资产数据');
      }
      if (parsedRows.length > 500) {
        throw new Error('单次最多导入500条资产');
      }
      setRows(parsedRows);
      setFileName(file.name);
      setResult(null);
      message.success(`已读取 ${parsedRows.length} 条资产数据`);
    } catch (error) {
      setRows([]);
      setFileName('');
      setResult(null);
      message.error(error instanceof Error ? error.message : '模板解析失败');
    }
  }

  async function submitImport() {
    if (!rows.length) {
      message.error('请先选择资产模板');
      return;
    }
    setSubmitting(true);
    try {
      const importResult = await http.post<unknown, AssetBatchImportResult>(endpoint, { rows });
      setResult(importResult);
      if (importResult.failedCount === 0) {
        message.success(`成功导入 ${importResult.successCount} 条资产`);
      } else if (importResult.successCount > 0) {
        message.warning(`成功 ${importResult.successCount} 条，失败 ${importResult.failedCount} 条`);
      } else {
        message.error('本次资产导入全部失败');
      }
      await onImported();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="资产批量录入"
      open={open}
      onCancel={onClose}
      onOk={submitImport}
      okText={result ? '导入完成' : '开始导入'}
      confirmLoading={submitting}
      okButtonProps={{ disabled: rows.length === 0 || result !== null }}
      width={980}
      destroyOnHidden
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Upload.Dragger
          accept=".csv,text/csv,.txt,text/plain"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(file) => {
            void readFile(file);
            return Upload.LIST_IGNORE;
          }}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">选择资产批量录入模板</p>
        </Upload.Dragger>

        {rows.length > 0 ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Typography.Text strong>{fileName} / 待导入 {rows.length} 条</Typography.Text>
            <Table
              rowKey={(row) => `${row.lineNo}-${row.serialNo}`}
              size="small"
              dataSource={rows}
              pagination={{ pageSize: 5, hideOnSinglePage: true }}
              scroll={{ x: 980 }}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 70 },
                { title: '类型', dataIndex: 'assetType', width: 100 },
                { title: '资产编号', dataIndex: 'serialNo', width: 190 },
                { title: '出资方编码', dataIndex: 'investorCode', width: 140 },
                { title: '门店编码', dataIndex: 'storeCode', width: 130, render: textOrDash },
                { title: '采购金额', dataIndex: 'purchaseAmount', width: 110 },
                { title: '残值', dataIndex: 'residualValue', width: 100, render: textOrDash },
                { title: '采购日期', dataIndex: 'purchasedAt', width: 120, render: textOrDash }
              ]}
            />
          </Space>
        ) : null}

        {result ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Typography.Text strong>
              导入结果：成功 {result.successCount} 条，失败 {result.failedCount} 条
            </Typography.Text>
            <Table
              rowKey={(row, index) => `${row.lineNo ?? index}-${row.serialNo ?? ''}`}
              size="small"
              dataSource={result.results}
              pagination={false}
              scroll={{ y: 280 }}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 80 },
                { title: '资产编号', dataIndex: 'serialNo', width: 220, render: textOrDash },
                { title: '资产编码', dataIndex: 'assetCode', width: 170, render: textOrDash },
                {
                  title: '状态',
                  dataIndex: 'success',
                  width: 90,
                  render: (success: boolean) => <Tag color={success ? 'green' : 'red'}>{success ? '成功' : '失败'}</Tag>
                },
                { title: '结果', dataIndex: 'message' }
              ]}
            />
          </Space>
        ) : null}
      </Space>
    </Modal>
  );
}

export function downloadAssetImportTemplate(options: TemplateOptions = {}) {
  const rows = [
    templateHeaders,
    ['', '', '', '', '', '', '']
  ];
  const csv = rows.map((row) => row.map(escapeCsvCell).join(',')).join('\r\n');
  const blob = new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = options.storeCode ? `资产批量录入模板-${options.storeCode}.csv` : '资产批量录入模板.csv';
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  message.success('资产批量录入模板已下载');
}

function parseAssetImportCsv(content: string): AssetImportRow[] {
  const sourceLines = content
    .replace(/^\ufeff/, '')
    .split(/\r?\n/)
    .map((line, index) => ({ line, lineNo: index + 1 }))
    .filter(({ line }) => line.trim().length > 0);
  if (sourceLines.length < 2) {
    throw new Error('模板中没有可导入的资产数据');
  }
  const delimiter = sourceLines[0].line.includes('\t') ? '\t' : ',';
  const headers = parseDelimitedLine(sourceLines[0].line, delimiter).map((item) => item.trim());
  const indexes = Object.fromEntries(
    Object.entries(headerAliases).map(([key, aliases]) => [
      key,
      headers.findIndex((header) => aliases.includes(header))
    ])
  ) as Record<keyof Omit<AssetImportRow, 'lineNo'>, number>;
  const missingHeaders = Object.entries(indexes)
    .filter(([key, index]) => key !== 'residualValue' && index < 0)
    .map(([key]) => headerAliases[key as keyof typeof headerAliases][0]);
  if (missingHeaders.length > 0) {
    throw new Error(`模板缺少列：${missingHeaders.join('、')}`);
  }

  return sourceLines.slice(1).map(({ line, lineNo }) => {
    const values = parseDelimitedLine(line, delimiter).map((item) => item.trim());
    return {
      lineNo,
      assetType: values[indexes.assetType] ?? '',
      serialNo: values[indexes.serialNo] ?? '',
      investorCode: values[indexes.investorCode] ?? '',
      storeCode: values[indexes.storeCode] ?? '',
      purchaseAmount: values[indexes.purchaseAmount] ?? '',
      residualValue: values[indexes.residualValue] ?? '',
      purchasedAt: values[indexes.purchasedAt] ?? ''
    };
  }).filter((row) => Object.entries(row).some(([key, value]) => key !== 'lineNo' && String(value).trim().length > 0));
}

function parseDelimitedLine(line: string, delimiter: string) {
  const values: string[] = [];
  let current = '';
  let quoted = false;
  for (let index = 0; index < line.length; index++) {
    const character = line[index];
    if (character === '"') {
      if (quoted && line[index + 1] === '"') {
        current += '"';
        index++;
      } else {
        quoted = !quoted;
      }
    } else if (character === delimiter && !quoted) {
      values.push(current);
      current = '';
    } else {
      current += character;
    }
  }
  values.push(current);
  return values;
}

function escapeCsvCell(value: string) {
  const safeValue = /^[=+\-@]/.test(value) ? `'${value}` : value;
  return `"${safeValue.replace(/"/g, '""')}"`;
}

function textOrDash(value?: string | null) {
  return value || '-';
}
