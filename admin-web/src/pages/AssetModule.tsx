import { Tabs } from 'antd';
import { AssetFulfillmentManagement } from './AssetFulfillmentManagement';
import { AssetManagement } from './AssetManagement';
import type { CurrentAccount } from '../types/api';

export function AssetModule({ account }: { account: CurrentAccount }) {
  return (
    <Tabs
      items={[
        { key: 'assets', label: '资产台账', children: <AssetManagement account={account} /> },
        { key: 'fulfillment', label: '履约凭证', children: <AssetFulfillmentManagement /> }
      ]}
    />
  );
}
