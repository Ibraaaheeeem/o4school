-- Fix ParentWallet table to allow nulls for asynchronous generation
ALTER TABLE parent_wallets ALTER COLUMN account_number DROP NOT NULL;
ALTER TABLE parent_wallets ALTER COLUMN account_name DROP NOT NULL;
ALTER TABLE parent_wallets ALTER COLUMN bank_name DROP NOT NULL;
