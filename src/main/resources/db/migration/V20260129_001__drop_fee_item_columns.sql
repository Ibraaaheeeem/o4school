-- Drop removed columns from fee_items table
ALTER TABLE fee_items
DROP COLUMN IF EXISTS fee_category,
DROP COLUMN IF EXISTS is_recurring,
DROP COLUMN IF EXISTS recurrence_type,
DROP COLUMN IF EXISTS academic_session_id,
DROP COLUMN IF EXISTS term_id;
