-- Add phone country code to users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_country_code VARCHAR(10);
