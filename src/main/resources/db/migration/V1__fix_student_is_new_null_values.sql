-- Fix NULL values in students.is_new column
UPDATE students SET is_new = true WHERE is_new IS NULL;

-- Ensure the column is not nullable
ALTER TABLE students ALTER COLUMN is_new SET NOT NULL;
ALTER TABLE students ALTER COLUMN is_new SET DEFAULT true;