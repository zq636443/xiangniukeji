import { ReloadOutlined } from '@ant-design/icons';
import { Button, Select, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { http } from '../services/request';
import type { AssetChange, AssetHandover } from '../types/api';

export function AssetFulfillmentManagement() {
  const [handovers, setHandovers] = useState<AssetHandover[]>([]);
  const [changes, setChanges] = useState<AssetChange[]>([]);
  const [handoverType, setHandoverType] = useState<'PICKUP' | 'RETURN' | undefined>();

  useEffect(() => {
    void loadAll();
  }, []);

  async function loadAll() {
    const [handoverData, changeData] = await Promise.all([
      http.get<unknown, AssetHandover[]>('/api/admin/asset-fulfillments/handovers', { params: { handoverType } }),
      http.get<unknown, AssetChange[]>('/api/admin/asset-fulfillments/changes')
    ]);
    setHandovers(handoverData);
    setChanges(changeData);
  }

  async function reloadHandovers(value?: 'PICKUP' | 'RETURN') {
    setHandoverType(value);
    setHandovers(await http.get<unknown, AssetHandover[]>('/api/admin/asset-fulfillments/handovers', { params: { handoverType: value } }));
  }

  return (
    <Space direction="vertical" size={16} className="page-stack">
      <Space align="center" className="toolbar">
        <Typography.Title level={4}>资产履约凭证</Typography.Title>
        <Space>
          <Select
            allowClear
            placeholder="交接类型"
            value={handoverType}
            style={{ width: 140 }}
            options={[
              { label: '取车交接', value: 'PICKUP' },
              { label: '归还交接', value: 'RETURN' }
            ]}
            onChange={reloadHandovers}
          />
          <Button icon={<ReloadOutlined />} onClick={loadAll}>刷新</Button>
        </Space>
      </Space>

      <div className="section">
        <Typography.Title level={5}>交接单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={handovers}
          pagination={false}
          columns={[
            { title: '交接单号', dataIndex: 'handoverNo' },
            { title: '类型', dataIndex: 'handoverType', render: (value) => <Tag>{value === 'PICKUP' ? '取车' : '归还'}</Tag> },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '门店 ID', dataIndex: 'storeId' },
            { title: '车架', dataIndex: 'frameAssetId', render: (value) => value || '-' },
            { title: '电池', dataIndex: 'batteryAssetId', render: (value) => value || '-' },
            { title: '车架结果', dataIndex: 'frameResultStatus', render: (value) => value || '-' },
            { title: '电池结果', dataIndex: 'batteryResultStatus', render: (value) => value || '-' },
            { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
            { title: '时间', dataIndex: 'createdAt' }
          ]}
        />
      </div>

      <div className="section">
        <Typography.Title level={5}>更换单</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          dataSource={changes}
          pagination={false}
          columns={[
            { title: '更换单号', dataIndex: 'changeNo' },
            { title: '订单 ID', dataIndex: 'orderId' },
            { title: '门店 ID', dataIndex: 'storeId' },
            { title: '资产类型', dataIndex: 'assetType', render: (value) => value === 'INTEGRATED_VEHICLE' ? '车电一体' : value === 'VEHICLE_FRAME' ? '车架' : '电池' },
            { title: '原资产', dataIndex: 'oldAssetId', render: (value) => value || '-' },
            { title: '新资产', dataIndex: 'newAssetId' },
            { title: '原资产结果', dataIndex: 'oldAssetResultStatus' },
            { title: '备注', dataIndex: 'remark', render: (value) => value || '-' },
            { title: '时间', dataIndex: 'createdAt' }
          ]}
        />
      </div>
    </Space>
  );
}
