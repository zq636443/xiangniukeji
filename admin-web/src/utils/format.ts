export const formatAmount = (amountInCents: number) => {
  return `¥${(amountInCents / 100).toFixed(2)}`;
};

