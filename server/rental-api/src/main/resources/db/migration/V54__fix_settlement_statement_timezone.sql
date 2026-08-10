-- System-generated settlement timestamps were written by UTC containers.
-- Business timestamps entered by users are intentionally left unchanged.
UPDATE settlement_statement
SET generated_at = DATE_ADD(generated_at, INTERVAL 8 HOUR),
    confirmed_at = CASE WHEN confirmed_at IS NULL THEN NULL ELSE DATE_ADD(confirmed_at, INTERVAL 8 HOUR) END,
    paid_at = CASE WHEN paid_at IS NULL THEN NULL ELSE DATE_ADD(paid_at, INTERVAL 8 HOUR) END,
    created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
    updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);
