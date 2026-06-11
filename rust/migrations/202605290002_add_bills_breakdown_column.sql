-- Add itemized bill breakdown storage.
-- Safe to re-run.

ALTER TABLE bills
ADD COLUMN IF NOT EXISTS breakdown TEXT;
