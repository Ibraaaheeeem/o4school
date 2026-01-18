-- Migration script to add missing columns to student_classes table
-- This script adds academic_session_id and term_id columns to support the new StudentClass entity structure

-- Add academic_session_id column
ALTER TABLE student_classes 
ADD COLUMN IF NOT EXISTS academic_session_id UUID;

-- Add term_id column  
ALTER TABLE student_classes 
ADD COLUMN IF NOT EXISTS term_id UUID;

-- Add foreign key constraints
ALTER TABLE student_classes 
ADD CONSTRAINT IF NOT EXISTS fk_student_classes_academic_session 
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);

ALTER TABLE student_classes 
ADD CONSTRAINT IF NOT EXISTS fk_student_classes_term 
FOREIGN KEY (term_id) REFERENCES terms(id);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_student_classes_session ON student_classes (academic_session_id, school_id);
CREATE INDEX IF NOT EXISTS idx_student_classes_term ON student_classes (term_id, school_id);
CREATE INDEX IF NOT EXISTS idx_student_classes_session_term ON student_classes (academic_session_id, term_id, school_id);

-- Update existing records to populate the new columns based on legacy fields
-- This assumes you have academic_year and term_name fields that can be used to find the corresponding IDs

-- Update academic_session_id based on academic_year
UPDATE student_classes 
SET academic_session_id = (
    SELECT id FROM academic_sessions 
    WHERE session_year = student_classes.academic_year 
    AND school_id = student_classes.school_id 
    AND is_active = true
    LIMIT 1
)
WHERE academic_session_id IS NULL AND academic_year IS NOT NULL;

-- Update term_id based on term_name and academic_session_id
UPDATE student_classes 
SET term_id = (
    SELECT t.id FROM terms t
    JOIN academic_sessions a ON t.academic_session_id = a.id
    WHERE t.term_name = student_classes.term_name 
    AND a.id = student_classes.academic_session_id
    AND t.is_active = true
    LIMIT 1
)
WHERE term_id IS NULL AND term_name IS NOT NULL AND academic_session_id IS NOT NULL;

-- For records that still don't have academic_session_id, try to use the current session
UPDATE student_classes 
SET academic_session_id = (
    SELECT id FROM academic_sessions 
    WHERE school_id = student_classes.school_id 
    AND is_current_session = true 
    AND is_active = true
    LIMIT 1
)
WHERE academic_session_id IS NULL;

-- For records that still don't have term_id, try to use the current term
UPDATE student_classes 
SET term_id = (
    SELECT t.id FROM terms t
    JOIN academic_sessions a ON t.academic_session_id = a.id
    WHERE a.id = student_classes.academic_session_id
    AND t.is_current_term = true 
    AND t.is_active = true
    LIMIT 1
)
WHERE term_id IS NULL AND academic_session_id IS NOT NULL;

-- Make the columns NOT NULL after populating them
-- Note: Only do this if all records have been successfully populated
-- ALTER TABLE student_classes ALTER COLUMN academic_session_id SET NOT NULL;
-- ALTER TABLE student_classes ALTER COLUMN term_id SET NOT NULL;

-- Add unique constraints (commented out initially to avoid conflicts)
-- ALTER TABLE student_classes 
-- ADD CONSTRAINT IF NOT EXISTS unique_student_track_session_term 
-- UNIQUE (student_id, track_id, academic_session_id, term_id, school_id);

-- ALTER TABLE student_classes 
-- ADD CONSTRAINT IF NOT EXISTS unique_admission_track_session_term 
-- UNIQUE (admission_number, track_id, academic_session_id, term_id, school_id);

COMMIT;