-- Manual Database Migration Script
-- Run this script to update the database schema for teacher assignments

-- Step 1: Add new columns to class_teachers table
ALTER TABLE class_teachers 
ADD COLUMN IF NOT EXISTS academic_session_id UUID,
ADD COLUMN IF NOT EXISTS term_id UUID;

-- Step 2: Add new columns to subject_teachers table  
ALTER TABLE subject_teachers
ADD COLUMN IF NOT EXISTS academic_session_id UUID,
ADD COLUMN IF NOT EXISTS term_id UUID;

-- Step 3: Create default academic session and term for each school if they don't exist
INSERT INTO academic_sessions (id, school_id, session_name, session_year, start_date, end_date, is_current_session, status, is_active, created_at, updated_at)
SELECT 
    gen_random_uuid(),
    s.id,
    '2024-2025',
    '2024-2025',
    '2024-09-01'::date,
    '2025-08-31'::date,
    true,
    'active',
    true,
    NOW(),
    NOW()
FROM schools s
WHERE NOT EXISTS (
    SELECT 1 FROM academic_sessions 
    WHERE school_id = s.id AND session_year = '2024-2025'
);

-- Step 4: Create default terms for each academic session
INSERT INTO terms (id, academic_session_id, term_name, term_order, start_date, end_date, is_current_term, status, is_active, created_at, updated_at)
SELECT 
    gen_random_uuid(),
    acs.id,
    'First Term',
    1,
    acs.start_date,
    acs.start_date + INTERVAL '4 months',
    true,
    'active',
    true,
    NOW(),
    NOW()
FROM academic_sessions acs
WHERE NOT EXISTS (
    SELECT 1 FROM terms 
    WHERE academic_session_id = acs.id
);

-- Step 5: Update class_teachers with session and term references
UPDATE class_teachers 
SET 
    academic_session_id = (
        SELECT acs.id 
        FROM academic_sessions acs 
        WHERE acs.school_id = class_teachers.school_id 
        AND acs.is_current_session = true 
        LIMIT 1
    ),
    term_id = (
        SELECT t.id 
        FROM terms t 
        JOIN academic_sessions acs ON t.academic_session_id = acs.id
        WHERE acs.school_id = class_teachers.school_id 
        AND acs.is_current_session = true 
        AND t.is_current_term = true
        LIMIT 1
    )
WHERE academic_session_id IS NULL OR term_id IS NULL;

-- Step 6: Update subject_teachers with session and term references
UPDATE subject_teachers
SET 
    academic_session_id = (
        SELECT acs.id 
        FROM academic_sessions acs 
        WHERE acs.school_id = subject_teachers.school_id 
        AND acs.is_current_session = true 
        LIMIT 1
    ),
    term_id = (
        SELECT t.id 
        FROM terms t 
        JOIN academic_sessions acs ON t.academic_session_id = acs.id
        WHERE acs.school_id = subject_teachers.school_id 
        AND acs.is_current_session = true 
        AND t.is_current_term = true
        LIMIT 1
    )
WHERE academic_session_id IS NULL OR term_id IS NULL;

-- Step 7: Make the new columns NOT NULL
ALTER TABLE class_teachers 
ALTER COLUMN academic_session_id SET NOT NULL,
ALTER COLUMN term_id SET NOT NULL;

ALTER TABLE subject_teachers
ALTER COLUMN academic_session_id SET NOT NULL, 
ALTER COLUMN term_id SET NOT NULL;

-- Step 8: Add foreign key constraints
ALTER TABLE class_teachers
ADD CONSTRAINT IF NOT EXISTS fk_class_teachers_academic_session 
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);

ALTER TABLE class_teachers
ADD CONSTRAINT IF NOT EXISTS fk_class_teachers_term 
FOREIGN KEY (term_id) REFERENCES terms(id);

ALTER TABLE subject_teachers  
ADD CONSTRAINT IF NOT EXISTS fk_subject_teachers_academic_session
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);

ALTER TABLE subject_teachers
ADD CONSTRAINT IF NOT EXISTS fk_subject_teachers_term
FOREIGN KEY (term_id) REFERENCES terms(id);

-- Step 9: Update unique constraints
ALTER TABLE class_teachers 
DROP CONSTRAINT IF EXISTS unique_class_teacher_assignment;

ALTER TABLE class_teachers
ADD CONSTRAINT unique_class_teacher_assignment 
UNIQUE (staff_id, class_id, academic_session_id, term_id, school_id);

ALTER TABLE subject_teachers
DROP CONSTRAINT IF EXISTS unique_subject_teacher_assignment;

ALTER TABLE subject_teachers  
ADD CONSTRAINT unique_subject_teacher_assignment
UNIQUE (staff_id, subject_id, class_id, academic_session_id, term_id, school_id);

-- Step 10: Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_class_teacher_school_staff 
ON class_teachers (school_id, staff_id, academic_session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_class_teacher_class
ON class_teachers (class_id, academic_session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_class_teacher_session_term
ON class_teachers (academic_session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_subject_teacher_school_staff
ON subject_teachers (school_id, staff_id, academic_session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_subject_teacher_class_subject  
ON subject_teachers (class_id, subject_id, academic_session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_subject_teacher_session_term
ON subject_teachers (academic_session_id, term_id);

-- Step 11: Update student_classes table if needed
ALTER TABLE student_classes 
ADD COLUMN IF NOT EXISTS academic_session_id UUID,
ADD COLUMN IF NOT EXISTS term_id UUID;

-- Update student_classes with session and term references
UPDATE student_classes 
SET 
    academic_session_id = (
        SELECT acs.id 
        FROM academic_sessions acs 
        WHERE acs.school_id = student_classes.school_id 
        AND acs.is_current_session = true 
        LIMIT 1
    ),
    term_id = (
        SELECT t.id 
        FROM terms t 
        JOIN academic_sessions acs ON t.academic_session_id = acs.id
        WHERE acs.school_id = student_classes.school_id 
        AND acs.is_current_session = true 
        AND t.is_current_term = true
        LIMIT 1
    )
WHERE academic_session_id IS NULL OR term_id IS NULL;

-- Make student_classes columns NOT NULL
ALTER TABLE student_classes 
ALTER COLUMN academic_session_id SET NOT NULL,
ALTER COLUMN term_id SET NOT NULL;

-- Add foreign key constraints for student_classes
ALTER TABLE student_classes
ADD CONSTRAINT IF NOT EXISTS fk_student_classes_academic_session 
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);

ALTER TABLE student_classes
ADD CONSTRAINT IF NOT EXISTS fk_student_classes_term 
FOREIGN KEY (term_id) REFERENCES terms(id);

-- Update student_classes unique constraints
ALTER TABLE student_classes 
DROP CONSTRAINT IF EXISTS unique_student_track_session_term;

ALTER TABLE student_classes
ADD CONSTRAINT unique_student_track_session_term 
UNIQUE (student_id, track_id, academic_session_id, term_id, school_id);

ALTER TABLE student_classes
DROP CONSTRAINT IF EXISTS unique_admission_track_session_term;

ALTER TABLE student_classes  
ADD CONSTRAINT unique_admission_track_session_term
UNIQUE (admission_number, track_id, academic_session_id, term_id, school_id);

-- Create indexes for student_classes
CREATE INDEX IF NOT EXISTS idx_student_classes_session_term
ON student_classes (academic_session_id, term_id, school_id);

COMMIT;