-- Add account details and terms acceptance to school_subscriptions table
ALTER TABLE school_subscriptions 
ADD COLUMN account_number VARCHAR(255),
ADD COLUMN bank_name VARCHAR(255),
ADD COLUMN terms_accepted BOOLEAN NOT NULL DEFAULT FALSE;
