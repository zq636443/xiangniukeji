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
  assetTypes?: Array<{
    typeCode: string;
    typeName: string;
    assetClass: string;
    serialLabel: string;
    status?: string;
  }>;
  investors?: Array<{
    investorCode: string;
    investorName: string;
  }>;
  stores?: Array<{
    storeCode: string;
    storeName: string;
  }>;
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

const requiredImportFields: Array<keyof Omit<AssetImportRow, 'lineNo'>> = [
  'assetType',
  'serialNo',
  'investorCode',
  'purchaseAmount'
];

const templateColumnWidths = [28, 28, 18, 18, 14, 14, 22];

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
      if (file.size > 10 * 1024 * 1024) {
        throw new Error('文件不能超过10MB');
      }
      const parsedRows = await parseAssetImportFile(file);
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
          accept=".xlsx,.xls,.csv,.tsv,.txt,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv,text/tab-separated-values,text/plain"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(file) => {
            void readFile(file);
            return Upload.LIST_IGNORE;
          }}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">选择 Excel 或 CSV 资产模板</p>
          <p className="ant-upload-hint">支持 xlsx、xls、csv、tsv，单次最多500条</p>
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

export async function downloadAssetImportTemplate(options: TemplateOptions = {}) {
  try {
    const XLSX = await import('xlsx');
    const workbook = XLSX.utils.book_new();
    const importSheet = XLSX.utils.aoa_to_sheet([
      templateHeaders,
      ['', '', '', options.storeCode || '', '', '', '']
    ]);
    configureSheet(importSheet, templateColumnWidths, 'A1:G1');
    XLSX.utils.book_append_sheet(workbook, importSheet, '资产导入');

    const exampleSheet = XLSX.utils.aoa_to_sheet([
      templateHeaders,
      ...buildExampleRows(options)
    ]);
    configureSheet(exampleSheet, templateColumnWidths, 'A1:G1');
    XLSX.utils.book_append_sheet(workbook, exampleSheet, '填写示例');

    const instructionSheet = XLSX.utils.aoa_to_sheet(buildInstructionRows(options));
    configureSheet(instructionSheet, [24, 16, 28, 54], 'A1:D1');
    XLSX.utils.book_append_sheet(workbook, instructionSheet, '填写说明');

    const typeSheet = XLSX.utils.aoa_to_sheet(buildAssetTypeRows(options));
    configureSheet(typeSheet, [24, 24, 22, 24], 'A1:D1');
    XLSX.utils.book_append_sheet(workbook, typeSheet, '资产类型');

    if (options.investors?.length) {
      const investorSheet = XLSX.utils.aoa_to_sheet([
        ['出资方编码', '出资方名称'],
        ...options.investors.map((investor) => [investor.investorCode, investor.investorName])
      ]);
      configureSheet(investorSheet, [24, 32], 'A1:B1');
      XLSX.utils.book_append_sheet(workbook, investorSheet, '出资方编码');
    }

    const templateStores = options.stores?.length
      ? options.stores
      : options.storeCode
        ? [{ storeCode: options.storeCode, storeName: '当前门店' }]
        : [];
    if (templateStores.length) {
      const storeSheet = XLSX.utils.aoa_to_sheet([
        ['门店编码', '门店名称'],
        ...templateStores.map((store) => [store.storeCode, store.storeName])
      ]);
      configureSheet(storeSheet, [24, 32], 'A1:B1');
      XLSX.utils.book_append_sheet(workbook, storeSheet, '门店编码');
    }

    XLSX.writeFile(
      workbook,
      options.storeCode ? `资产批量录入模板-${options.storeCode}.xlsx` : '资产批量录入模板.xlsx',
      { compression: true }
    );
    message.success('资产批量录入模板已下载');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '模板下载失败');
  }
}

async function parseAssetImportFile(file: File): Promise<AssetImportRow[]> {
  const extension = file.name.split('.').pop()?.toLowerCase();
  const buffer = await file.arrayBuffer();
  if (extension === 'xlsx' || extension === 'xls') {
    const XLSX = await import('xlsx');
    const workbook = XLSX.read(buffer, { type: 'array', cellDates: true });
    const sheetName = workbook.SheetNames.find((name) => normalizeHeader(name) === normalizeHeader('资产导入'))
      || workbook.SheetNames[0];
    if (!sheetName) {
      throw new Error('Excel 文件中没有可读取的工作表');
    }
    const matrix = XLSX.utils.sheet_to_json<unknown[]>(workbook.Sheets[sheetName], {
      header: 1,
      raw: true,
      defval: '',
      blankrows: true
    });
    return parseAssetImportTable(matrix);
  }

  let firstError: Error | undefined;
  for (const encoding of ['utf-8', 'gb18030']) {
    try {
      const content = new TextDecoder(encoding, { fatal: encoding === 'utf-8' }).decode(buffer);
      return parseAssetImportTable(parseDelimitedContent(content));
    } catch (error) {
      firstError ??= error instanceof Error ? error : new Error('模板解析失败');
    }
  }
  throw firstError || new Error('模板解析失败');
}

function parseAssetImportTable(matrix: unknown[][]): AssetImportRow[] {
  if (!matrix.length) {
    throw new Error('模板中没有可导入的资产数据');
  }
  const headerCandidates = matrix.slice(0, 30).map((row, index) => ({
    index,
    indexes: findHeaderIndexes(row)
  }));
  const header = headerCandidates.find((candidate) => requiredImportFields.every((field) => candidate.indexes[field] >= 0));
  if (!header) {
    const bestCandidate = headerCandidates.sort((left, right) => countMatchedHeaders(right.indexes) - countMatchedHeaders(left.indexes))[0];
    const missingHeaders = requiredImportFields
      .filter((field) => !bestCandidate || bestCandidate.indexes[field] < 0)
      .map((field) => headerAliases[field][0]);
    throw new Error(`未识别到资产导入表头，缺少：${missingHeaders.join('、')}`);
  }

  return matrix.slice(header.index + 1).map((values, rowOffset) => ({
    lineNo: header.index + rowOffset + 2,
    assetType: importCell(values, header.indexes.assetType, 'assetType'),
    serialNo: importCell(values, header.indexes.serialNo, 'serialNo'),
    investorCode: importCell(values, header.indexes.investorCode, 'investorCode'),
    storeCode: importCell(values, header.indexes.storeCode, 'storeCode'),
    purchaseAmount: importCell(values, header.indexes.purchaseAmount, 'purchaseAmount'),
    residualValue: importCell(values, header.indexes.residualValue, 'residualValue'),
    purchasedAt: importCell(values, header.indexes.purchasedAt, 'purchasedAt')
  })).filter((row) => requiredImportFields.some((field) => row[field].trim().length > 0));
}

function findHeaderIndexes(row: unknown[]) {
  const headers = row.map((value) => normalizeHeader(value));
  return Object.fromEntries(
    Object.entries(headerAliases).map(([key, aliases]) => {
      const normalizedAliases = aliases.map((alias) => normalizeHeader(alias));
      return [key, headers.findIndex((header) => normalizedAliases.includes(header))];
    })
  ) as Record<keyof Omit<AssetImportRow, 'lineNo'>, number>;
}

function countMatchedHeaders(indexes: Record<keyof Omit<AssetImportRow, 'lineNo'>, number>) {
  return Object.values(indexes).filter((index) => index >= 0).length;
}

function importCell(
  values: unknown[],
  index: number,
  field: keyof Omit<AssetImportRow, 'lineNo'>
) {
  if (index < 0) return '';
  const value = values[index];
  if (value instanceof Date) {
    return formatDate(value.getFullYear(), value.getMonth() + 1, value.getDate());
  }
  if (field === 'purchasedAt' && typeof value === 'number' && value > 0) {
    const date = new Date(Date.UTC(1899, 11, 30) + Math.round(value * 86400000));
    return formatDate(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
  }
  return value == null ? '' : String(value).trim();
}

function normalizeHeader(value: unknown) {
  return String(value ?? '')
    .replace(/^\ufeff/, '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '')
    .replace(/[（]/g, '(')
    .replace(/[）]/g, ')')
    .replace(/[／]/g, '/')
    .replace(/[＊*]/g, '');
}

function parseDelimitedContent(content: string) {
  const normalized = content.replace(/^\ufeff/, '');
  const sampleLines = normalized.split(/\r?\n/).filter((line) => line.trim()).slice(0, 30);
  const delimiter = ['\t', ',', ';'].sort((left, right) => (
    maxDelimiterCount(sampleLines, right) - maxDelimiterCount(sampleLines, left)
  ))[0];
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let index = 0; index < normalized.length; index++) {
    const character = normalized[index];
    if (character === '"') {
      if (quoted && normalized[index + 1] === '"') {
        cell += '"';
        index++;
      } else {
        quoted = !quoted;
      }
    } else if (character === delimiter && !quoted) {
      row.push(cell);
      cell = '';
    } else if ((character === '\n' || character === '\r') && !quoted) {
      if (character === '\r' && normalized[index + 1] === '\n') index++;
      row.push(cell);
      if (row.some((value) => value.trim())) rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += character;
    }
  }
  row.push(cell);
  if (row.some((value) => value.trim())) rows.push(row);
  return rows;
}

function countDelimiter(line: string, delimiter: string) {
  return line.split(delimiter).length - 1;
}

function maxDelimiterCount(lines: string[], delimiter: string) {
  return Math.max(0, ...lines.map((line) => countDelimiter(line, delimiter)));
}

function buildExampleRows(options: TemplateOptions) {
  const types = enabledTemplateTypes(options);
  const investorCode = options.investors?.[0]?.investorCode || '';
  const storeCode = options.storeCode || options.stores?.[0]?.storeCode || '';
  const examples = [
    ['VEHICLE_FRAME', '车架示例-001', '2600', '300'],
    ['BATTERY', '电池示例-001', '1800', '200'],
    ['INTEGRATED_VEHICLE', '车电一体示例-001', '4200', '']
  ].map(([assetClass, serialNo, purchaseAmount, residualValue]) => {
    const type = types.find((item) => item.assetClass === assetClass);
    return [type?.typeName || assetClass, serialNo, investorCode, storeCode, purchaseAmount, residualValue, '2026-07-22'];
  });
  const customType = types.find((item) => !['VEHICLE_FRAME', 'BATTERY', 'INTEGRATED_VEHICLE'].includes(item.assetClass));
  if (customType) {
    examples.push([customType.typeName, '自定义资产示例-001', investorCode, storeCode, '399', '', '2026-07-22']);
  }
  return examples;
}

function buildInstructionRows(options: TemplateOptions) {
  const investorCode = options.investors?.[0]?.investorCode || '';
  const storeCode = options.storeCode || options.stores?.[0]?.storeCode || '';
  return [
    ['字段', '是否必填', '填写示例', '填写说明'],
    [templateHeaders[0], '是', '车架', '填写“资产类型”工作表中的类型名称或类型编码'],
    [templateHeaders[1], '是', '车架示例-001', '车架填车架号，电池填电池号，车电一体只填车架号；长编号建议设为文本格式'],
    [templateHeaders[2], '是', investorCode, '填写“出资方编码”工作表中的编码，不能填出资方名称'],
    [templateHeaders[3], '否', storeCode, options.storeCode ? '门店端导入时已锁定当前门店，可留空' : '留空表示暂不分配门店；填写时必须使用门店编码'],
    [templateHeaders[4], '是', '2600', '填写不小于0的数字，最多保留2位小数'],
    [templateHeaders[5], '否', '300', '可留空；填写时不能小于0'],
    [templateHeaders[6], '否', '2026-07-22', '留空默认当天，建议使用YYYY-MM-DD格式']
  ];
}

function buildAssetTypeRows(options: TemplateOptions) {
  return [
    ['类型名称', '类型编码', '业务归类', '编号字段'],
    ...enabledTemplateTypes(options).map((type) => [type.typeName, type.typeCode, type.assetClass, type.serialLabel])
  ];
}

function enabledTemplateTypes(options: TemplateOptions) {
  const configuredTypes = options.assetTypes?.filter((type) => type.status !== 'DISABLED');
  if (configuredTypes?.length) return configuredTypes;
  return [
    { typeCode: 'VEHICLE_FRAME', typeName: '车架', assetClass: 'VEHICLE_FRAME', serialLabel: '车架号' },
    { typeCode: 'BATTERY', typeName: '电池', assetClass: 'BATTERY', serialLabel: '电池号' },
    { typeCode: 'INTEGRATED_VEHICLE', typeName: '车电一体', assetClass: 'INTEGRATED_VEHICLE', serialLabel: '车架号' }
  ];
}

function configureSheet(
  sheet: import('xlsx').WorkSheet,
  widths: number[],
  filterRef: string
) {
  sheet['!cols'] = widths.map((width) => ({ wch: width }));
  sheet['!autofilter'] = { ref: filterRef };
}

function formatDate(year: number, month: number, day: number) {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function textOrDash(value?: string | null) {
  return value || '-';
}
