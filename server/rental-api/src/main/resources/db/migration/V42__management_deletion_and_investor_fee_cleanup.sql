ALTER TABLE sys_account
  ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
  ADD KEY idx_sys_account_deleted (deleted_at);
