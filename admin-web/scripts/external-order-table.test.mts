import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(
  new URL('../src/pages/ExternalOrderManagement.tsx', import.meta.url),
  'utf8'
);

test('customer name and phone remain visible in the first fixed business column', () => {
  const customerColumnStart = source.indexOf("title: '客户信息'");
  const recordColumnStart = source.indexOf("title: '台账号'");

  assert.ok(customerColumnStart >= 0, '应存在客户信息列');
  assert.ok(recordColumnStart > customerColumnStart, '客户信息应位于首个业务列');

  const customerColumn = source.slice(customerColumnStart, recordColumnStart);
  assert.match(customerColumn, /fixed: useFixedTableColumns \? 'left' : undefined/);
  assert.match(customerColumn, /record\.customerName/);
  assert.match(customerColumn, /record\.customerPhone/);
  assert.match(source, /const useFixedTableColumns = screens\.md !== false/);
  assert.match(source, /rowSelection=\{\{\s*fixed: useFixedTableColumns/);
});

test('the fixed action column stays compact and keeps asset replacement prominent', () => {
  const actionColumn = source.match(/title: '操作',\s*width: (\d+),\s*fixed: useFixedTableColumns \? 'right' : undefined/);
  const tableScroll = source.match(/scroll=\{\{ x: (\d+) \}\}/);

  assert.ok(actionColumn, '应配置右侧固定操作列');
  assert.ok(Number(actionColumn[1]) <= 250, '操作列不应再遮挡客户信息');
  assert.ok(tableScroll, '表格应配置横向滚动宽度');
  assert.ok(Number(tableScroll[1]) >= 2500, '横向滚动宽度应覆盖所有列');
  assert.match(source, /key: 'edit',[\s\S]*?label: '编辑订单资料'/);
  assert.match(source, /type="primary" ghost icon=\{<SwapOutlined \/>\}[\s\S]*?\u66f4换资产/);
  assert.match(source, /<Dropdown[\s\S]*?items: moreActionItems\(record\)/);
});

test('dangerous and lifecycle actions remain available through the more menu', () => {
  for (const key of ['edit', 'pricing', 'manual-renewal', 'complete', 'terminate', 'delete']) {
    assert.match(source, new RegExp(`key: '${key}'`));
    assert.match(source, new RegExp(`key === '${key}'`));
  }
  assert.match(source, /record\.orderStatus === 'ACTIVE'[\s\S]*?activeItems/);
  assert.match(source, /record\.orderStatus === 'ACTIVE' && canOperate/);
  assert.match(source, /title="确认删除补录订单？"/);
  assert.match(source, /deleteOrderTarget\.recordNo/);
  assert.match(source, /deleteOrderTarget\.customerName/);
  assert.match(source, /deleteOrderTarget\.customerPhone/);
  assert.match(source, /closable=\{!deletingCurrentOrder\}/);
  assert.match(source, /maskClosable=\{!deletingCurrentOrder\}/);
  assert.match(source, /okButtonProps=\{\{ danger: true \}\}/);
});
