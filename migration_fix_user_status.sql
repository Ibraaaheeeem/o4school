-- Fix users_status_check constraint to include APPROVED
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status::text = ANY (ARRAY['PENDING'::text, 'ACTIVE'::text, 'INACTIVE'::text, 'SUSPENDED'::text, 'APPROVED'::text]));
