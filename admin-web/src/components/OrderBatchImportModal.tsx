import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { Button, Modal, Space, Table, Tag, Typography, Upload, message } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { Asset, OrderBatchImportResult, StoreSku } from '../types/api';

type OrderImportRow = {
  lineNo: number;
  customerName: string;
  customerPhone: string;
  userAccountId: string;
  storeSkuCode: string;
  packageCode: string;
  verificationAmount: string;
  frameSerialNo: string;
  batterySerialNo: string;
  orderedAt: string;
  expectedPickupAt: string;
};

type OrderBatchImportModalProps = {
  open: boolean;
  endpoint: string;
  onClose: () => void;
  onImported: () => void | Promise<void>;
};

type TemplateOptions = {
  storeCode?: string;
  storeSkus: StoreSku[];
  assets: Asset[];
};

const templateHeaders = [
  '客户姓名',
  '联系电话',
  '用户账号ID(选填)',
  '门店商品编码',
  'SKU编码',
  '实际核销金额',
  '车架号(车电一体填此列)',
  '电池号(选填)',
  '下单时间(YYYY-MM-DD HH:mm)',
  '预计取车时间(选填)'
];

const headerAliases: Record<keyof Omit<OrderImportRow, 'lineNo'>, string[]> = {
  customerName: ['客户姓名', 'customerName'],
  customerPhone: ['联系电话', '客户电话', 'customerPhone'],
  userAccountId: ['用户账号ID(选填)', '用户账号ID（选填）', '用户账号ID', 'userAccountId'],
  storeSkuCode: ['门店商品编码', 'storeSkuCode'],
  packageCode: ['SKU编码', 'SKU 编码', '套餐编码', 'packageCode'],
  verificationAmount: ['实际核销金额', '核销金额', 'verificationAmount'],
  frameSerialNo: ['车架号(车电一体填此列)', '车架号（车电一体填此列）', '车架号(选填)', '车架号（选填）', '车架号', 'frameSerialNo'],
  batterySerialNo: ['电池号(选填)', '电池号（选填）', '电池号', 'batterySerialNo'],
  orderedAt: ['下单时间(YYYY-MM-DD HH:mm)', '下单时间（YYYY-MM-DD HH:mm）', '下单时间', 'orderedAt'],
  expectedPickupAt: ['预计取车时间(选填)', '预计取车时间（选填）', '预计取车时间', 'expectedPickupAt']
};

export function OrderBatchImportModal({ open, endpoint, onClose, onImported }: OrderBatchImportModalProps) {
  const [rows, setRows] = useState<OrderImportRow[]>([]);
  const [fileName, setFileName] = useState('');
  const [result, setResult] = useState<OrderBatchImportResult | null>(null);
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
      const parsedRows = parseOrderImportCsv(await file.text());
      if (parsedRows.length === 0) {
        throw new Error('模板中没有可导入的订单数据');
      }
      if (parsedRows.length > 500) {
        throw new Error('单次最多导入500条订单');
      }
      setRows(parsedRows);
      setFileName(file.name);
      setResult(null);
      message.success(`已读取 ${parsedRows.length} 条订单数据`);
    } catch (error) {
      setRows([]);
      setFileName('');
      setResult(null);
      message.error(error instanceof Error ? error.message : '模板解析失败');
    }
  }

  async function submitImport() {
    if (!rows.length) {
      message.error('请先选择订单模板');
      return;
    }
    setSubmitting(true);
    try {
      const importResult = await http.post<unknown, OrderBatchImportResult>(endpoint, { rows });
      setResult(importResult);
      if (importResult.failedCount === 0) {
        message.success(`成功导入 ${importResult.successCount} 条订单`);
      } else if (importResult.successCount > 0) {
        message.warning(`成功 ${importResult.successCount} 条，失败 ${importResult.failedCount} 条`);
      } else {
        message.error('本次订单导入全部失败');
      }
      await onImported();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="订单批量导入"
      open={open}
      onCancel={onClose}
      onOk={submitImport}
      okText={result ? '导入完成' : '开始导入'}
      confirmLoading={submitting}
      okButtonProps={{ disabled: rows.length === 0 || result !== null }}
      width={1120}
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
          <p className="ant-upload-text">选择订单批量导入模板</p>
        </Upload.Dragger>

        {rows.length > 0 ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Typography.Text strong>{fileName} / 待导入 {rows.length} 条</Typography.Text>
            <Table
              rowKey={(row) => `${row.lineNo}-${row.customerPhone}`}
              size="small"
              dataSource={rows}
              pagination={{ pageSize: 5, hideOnSinglePage: true }}
              scroll={{ x: 1250 }}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 70 },
                { title: '客户姓名', dataIndex: 'customerName', width: 120 },
                { title: '联系电话', dataIndex: 'customerPhone', width: 140 },
                { title: '商品编码', dataIndex: 'storeSkuCode', width: 190 },
                { title: 'SKU编码', dataIndex: 'packageCode', width: 160 },
                { title: '实际核销金额', dataIndex: 'verificationAmount', width: 130 },
                { title: '车架号', dataIndex: 'frameSerialNo', width: 160, render: textOrDash },
                { title: '电池号', dataIndex: 'batterySerialNo', width: 160, render: textOrDash },
                { title: '下单时间', dataIndex: 'orderedAt', width: 170, render: textOrDash },
                { title: '预计取车', dataIndex: 'expectedPickupAt', width: 170, render: textOrDash }
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
              rowKey={(row) => `${row.lineNo ?? 'line'}-${row.orderNo ?? row.customerPhone ?? ''}`}
              size="small"
              dataSource={result.results}
              pagination={false}
              scroll={{ y: 300 }}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 80 },
                { title: '客户', width: 220, render: (_, row) => `${row.customerName || '-'} / ${row.customerPhone || '-'}` },
                { title: '订单号', dataIndex: 'orderNo', width: 180, render: textOrDash },
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

export function OrderImportTemplateButton({ storeCode, storeSkus, assets }: TemplateOptions) {
  const [templateUrl, setTemplateUrl] = useState('');
  const fileName = storeCode ? `订单批量导入模板-${storeCode}.csv` : '订单批量导入模板.csv';

  useEffect(() => {
    const csv = buildOrderTemplateCsv(storeSkus, assets);
    const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }));
    setTemplateUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [storeSkus, assets]);

  return (
    <Button icon={<DownloadOutlined />} href={templateUrl || undefined} download={fileName} disabled={!templateUrl}>
      下载模板
    </Button>
  );
}

function buildOrderTemplateCsv(storeSkus: StoreSku[], assets: Asset[]) {
  const width = templateHeaders.length;
  const firstStoreSku = storeSkus[0];
  const firstPackage = firstStoreSku?.packages.find((item) => item.status === 'ENABLED');
  const firstFrameAsset = assets.find((item) => item.status === 'IDLE'
    && (item.assetType === 'VEHICLE_FRAME' || item.assetType === 'INTEGRATED_VEHICLE'));
  const firstBatteryAsset = firstFrameAsset?.assetType === 'INTEGRATED_VEHICLE'
    ? undefined
    : assets.find((item) => item.status === 'IDLE' && item.assetType === 'BATTERY');
  const rows: string[][] = [
    templateHeaders,
    [
      '# 示例客户（请替换或删除本行）',
      '13800138000',
      '',
      firstStoreSku?.storeSkuCode ?? '',
      firstPackage?.packageCode ?? '',
      firstPackage ? String(firstPackage.rentalAmount) : '399',
      firstFrameAsset?.serialNo ?? '',
      firstBatteryAsset?.serialNo ?? '',
      '2026-07-01 10:00',
      ''
    ],
    Array(width).fill('')
  ];
  const packageReferences = storeSkus.flatMap((storeSku) => storeSku.packages
    .filter((item) => item.status === 'ENABLED')
    .map((item) => [
      '#商品SKU',
      `${storeSku.displayName} / ${item.packageName}`,
      '',
      storeSku.storeSkuCode,
      item.packageCode,
      String(item.rentalAmount),
      '',
      '',
      '',
      ''
    ]));
  if (packageReferences.length > 0) {
    rows.push(Array(width).fill(''), ['# 可用门店商品与SKU编码参考'], ...packageReferences);
  }
  const assetReferences = assets
    .filter((asset) => asset.status === 'IDLE')
    .map((asset) => [
      '#空闲资产',
      assetTypeText(asset.assetType),
      '',
      '',
      '',
      '',
      asset.assetType === 'VEHICLE_FRAME' || asset.assetType === 'INTEGRATED_VEHICLE' ? asset.serialNo : '',
      asset.assetType === 'BATTERY' ? asset.serialNo : '',
      '',
      ''
    ]);
  if (assetReferences.length > 0) {
    rows.push(Array(width).fill(''), ['# 当前空闲资产序列号参考'], ...assetReferences);
  }
  return rows.map((row) => row.map((value) => escapeCsvCell(String(value ?? ''))).join(',')).join('\r\n');
}

function parseOrderImportCsv(content: string): OrderImportRow[] {
  const sourceLines = content
    .replace(/^\ufeff/, '')
    .split(/\r?\n/)
    .map((line, index) => ({ line, lineNo: index + 1 }))
    .filter(({ line }) => line.trim().length > 0);
  if (sourceLines.length < 2) {
    throw new Error('模板中没有可导入的订单数据');
  }
  const delimiter = sourceLines[0].line.includes('\t') ? '\t' : ',';
  const headers = parseDelimitedLine(sourceLines[0].line, delimiter).map((item) => item.trim());
  const indexes = Object.fromEntries(
    Object.entries(headerAliases).map(([key, aliases]) => [
      key,
      headers.findIndex((header) => aliases.includes(header))
    ])
  ) as Record<keyof Omit<OrderImportRow, 'lineNo'>, number>;
  const missingHeaders = Object.entries(indexes)
    .filter(([, index]) => index < 0)
    .map(([key]) => headerAliases[key as keyof typeof headerAliases][0]);
  if (missingHeaders.length > 0) {
    throw new Error(`模板缺少列：${missingHeaders.join('、')}`);
  }

  return sourceLines.slice(1).flatMap(({ line, lineNo }) => {
    const values = parseDelimitedLine(line, delimiter).map((item) => item.trim());
    if (values[0] === '#商品SKU' || values[0] === '#商品套餐' || values[0] === '#空闲资产' || values[0]?.startsWith('# ')) {
      return [];
    }
    const row: OrderImportRow = {
      lineNo,
      customerName: values[indexes.customerName] ?? '',
      customerPhone: values[indexes.customerPhone] ?? '',
      userAccountId: values[indexes.userAccountId] ?? '',
      storeSkuCode: values[indexes.storeSkuCode] ?? '',
      packageCode: values[indexes.packageCode] ?? '',
      verificationAmount: values[indexes.verificationAmount] ?? '',
      frameSerialNo: values[indexes.frameSerialNo] ?? '',
      batterySerialNo: values[indexes.batterySerialNo] ?? '',
      orderedAt: values[indexes.orderedAt] ?? '',
      expectedPickupAt: values[indexes.expectedPickupAt] ?? ''
    };
    return Object.entries(row).some(([key, value]) => key !== 'lineNo' && String(value).trim().length > 0) ? [row] : [];
  });
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

function assetTypeText(assetType: Asset['assetType']) {
  if (assetType === 'INTEGRATED_VEHICLE') return '车电一体';
  return assetType === 'VEHICLE_FRAME' ? '车架' : '电池';
}
