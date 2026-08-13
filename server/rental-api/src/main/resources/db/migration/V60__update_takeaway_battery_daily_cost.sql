UPDATE product_sku
SET battery_cost_daily_amount = 6.80,
    battery_cost_monthly_amount = 200.00
WHERE battery_cost_daily_amount = 6.60
  AND battery_cost_monthly_amount = 200.00;
