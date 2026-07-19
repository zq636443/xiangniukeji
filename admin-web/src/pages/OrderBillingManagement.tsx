import { Tabs } from 'antd';
import { BillManagement } from './BillManagement';
import { AgreementDeductManagement } from './AgreementDeductManagement';
import { ComplianceManagement } from './ComplianceManagement';
import { FundAuthManagement } from './FundAuthManagement';
import { OrderManagement } from './OrderManagement';
import { OverdueManagement } from './OverdueManagement';
import { PaymentManagement } from './PaymentManagement';
import { VoucherManagement } from './VoucherManagement';

export function OrderBillingManagement() {
  return (
    <Tabs
      items={[
        { key: 'orders', label: '订单', children: <OrderManagement /> },
        { key: 'bills', label: '账单', children: <BillManagement /> },
        { key: 'payments', label: '支付', children: <PaymentManagement /> },
        { key: 'deducts', label: '签约扣款', children: <AgreementDeductManagement /> },
        { key: 'fundAuths', label: '资金授权', children: <FundAuthManagement /> },
        { key: 'compliance', label: '实名合同', children: <ComplianceManagement /> },
        { key: 'vouchers', label: '团购核销', children: <VoucherManagement /> },
        { key: 'overdues', label: '逾期汇总', children: <OverdueManagement /> }
      ]}
    />
  );
}
