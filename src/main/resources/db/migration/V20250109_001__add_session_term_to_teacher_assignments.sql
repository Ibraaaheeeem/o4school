-- Migration to add academic_session_id and term_id to teacher assignment tables
-- and migrate existing academic_year data

-- Add new columns to class_teachers table
ALTER TABLE class_teachers 
ADD COLUMN academic_session_id UUID,
ADD COLUMN term_id UUID;

-- Add new columns to subject_teachers table  
ALTER TABLE subject_teachers
ADD COLUMN academic_session_id UUID,
ADD COLUMN term_id UUID;

-- Create a function to get or create academic session based on academic year
CREATE OR REPLACE FUNCTION get_or_create_academic_session(school_uuid UUID, academic_year_str VARCHAR)
RETURNS UUID AS $$
DECLARE
    session_uuid UUID;
    start_year INTEGER;
    end_year INTEGER;
BEGIN
    -- Parse academic year string (e.g., "2024-2025" -> start_year=2024, end_year=2025)
    start_year := CAST(SPLIT_PART(academic_year_str, '-', 1) AS INTEGER);
    end_year := CAST(SPLIT_PART(academic_year_str, '-', 2) AS INTEGER);
    
    -- Try to find existing session
    SELECT id INTO session_uuid 
    FROM academic_sessions 
    WHERE school_id = school_uuid 
    AND session_year = academic_year_str
    AND is_active = true;
    
    -- If not found, create new session
    IF session_uuid IS NULL THEN
        session_uuid := gen_random_uuid();
        INSERT INTO academic_sessions (
            id, school_id, session_name, session_year, 
            start_date, end_date, is_current_session, 
            status, is_active, created_at, updated_at
        ) VALUES (
            session_uuid, school_uuid, academic_year_str, academic_year_str,
            MAKE_DATE(start_year, 9, 1), -- September 1st of start year
            MAKE_DATE(end_year, 8, 31),   -- August 31st of end year
            false, -- We'll set current session separately
            'active',
            true,
            NOW(),
            NOW()
        );
    END IF;
    
    RETURN session_uuid;
END;
$$ LANGUAGE plpgsql;

-- Create a function to get or create default term for a session
CREATE OR REPLACE FUNCTION get_or_create_default_term(session_uuid UUID)
RETURNS UUID AS $$
DECLARE
    term_uuid UUID;
BEGIN
    -- Try to find existing term
    SELECT id INTO term_uuid 
    FROM terms 
    WHERE academic_session_id = session_uuid 
    AND is_active = true
    ORDER BY term_order ASC
    LIMIT 1;
    
    -- If not found, create default term
    IF term_uuid IS NULL THEN
        term_uuid := gen_random_uuid();
        INSERT INTO terms (
            id, academic_session_id, term_name, term_order,
            start_date, end_date, is_current_term,
            status, is_active, created_at, updated_at
        ) VALUES (
            term_uuid, session_uuid, 'First Term', 1,
            (SELECT start_date FROM academic_sessions WHERE id = session_uuid),
            (SELECT start_date FROM academic_sessions WHERE id = session_uuid) + INTERVAL '4 months',
            false, -- We'll set current term separately
            'active',
            true,
            NOW(),
            NOW()
        );
    END IF;
    
    RETURN term_uuid;
END;
$$ LANGUAGE plpgsql;

-- Update class_teachers table with session and term data
UPDATE class_teachers 
SET 
    academic_session_id = get_or_create_academic_session(school_id, academic_year),
    term_id = get_or_create_default_term(get_or_create_academic_session(school_id, academic_year))
WHERE academic_year IS NOT NULL;

-- Update subject_teachers table with session and term data  
UPDATE subject_teachers
SET 
    academic_session_id = get_or_create_academic_session(school_id, academic_year),
    term_id = get_or_create_default_term(get_or_create_academic_session(school_id, academic_year))
WHERE academic_year IS NOT NULL;

-- Add foreign key constraints
ALTER TABLE class_teachers
ADD CONSTRAINT fk_class_teachers_academic_session 
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id),
ADD CONSTRAINT fk_class_teachers_term 
FOREIGN KEY (term_id) REFERENCES terms(id);

ALTER TABLE subject_teachers  
ADD CONSTRAINT fk_subject_teachers_academic_session
FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id),
ADD CONSTRAINT fk_subject_teachers_term
FOREIGN KEY (term_id) REFERENCES terms(id);

-- Make the new columns NOT NULL after data migration
ALTER TABLE class_teachers 
ALTER COLUMN academic_session_id SET NOT NULL,
ALTER COLUMN term_id SET NOT NULL;

ALTER TABLE subject_teachers
ALTER COLUMN academic_session_id SET NOT NULL, 
ALTER COLUMN term_id SET NOT NULL;

-- Update unique constraints to use new columns
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

-- Create indexes for better performance
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

-- Set one session as current for each school (the most recent one)
UPDATE academic_sessions 
SET is_current_session = true
WHERE id IN (
    SELECT DISTINCT ON (school_id) id
    FROM academic_sessions 
    WHERE is_active = true
    ORDER BY school_id, start_date DESC
);

-- Set one term as current for each current session
UPDATE terms
SET is_current_term = true  
WHERE id IN (
    SELECT DISTINCT ON (academic_session_id) t.id
    FROM terms t
    JOIN academic_sessions s ON t.academic_session_id = s.id
    WHERE s.is_current_session = true 
    AND t.is_active = true
    ORDER BY t.academic_session_id, t.term_order ASC
);

-- Clean up functions
DROP FUNCTION IF EXISTS get_or_create_academic_session(UUID, VARCHAR);
DROP FUNCTION IF EXISTS get_or_create_default_term(UUID);