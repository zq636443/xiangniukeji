ALTER TABLE product_package
  ADD COLUMN price_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER package_name;

UPDATE product_package pp
LEFT JOIN (
  SELECT package_id, MIN(rental_amount) AS price_amount
  FROM store_sku_package
  GROUP BY package_id
) configured_price ON configured_price.package_id = pp.id
SET pp.price_amount = COALESCE(configured_price.price_amount, 0.00);

UPDATE store_sku_package ssp
JOIN product_package pp ON pp.id = ssp.package_id
SET ssp.rental_amount = pp.price_amount;

UPDATE product_category
SET category_name = '租赁商品'
WHERE category_code = 'C-rental'
  AND category_name = '租赁套餐';
