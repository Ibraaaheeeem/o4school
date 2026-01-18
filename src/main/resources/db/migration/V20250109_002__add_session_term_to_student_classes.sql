-- Migration to add academic_session_id and term_id to student_classes table
-- and migrate existing academic_year data

-- Add new columns to student_classes table (if they don't exist)
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'student_classes' AND column_name = 'academic_session_id') THEN
        ALTER TABLE student_classes ADD COLUMN academic_session_id UUID;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'student_classes' AND column_name = 'term_id') THEN
        ALTER TABLE student_classes ADD COLUMN term_id UUID;
    END IF;
END $$;

-- Create a function to get or create academic session based on academic year
CREATE OR REPLACE FUNCTION get_or_create_academic_session_sc(school_uuid UUID, academic_year_str VARCHAR)
RETURNS UUID AS $$
DECLARE
    session_uuid UUID;
    start_year INTEGER;
    end_year INTEGER;
BEGIN
    -- Handle null or empty academic year
    IF academic_year_str IS NULL OR academic_year_str = '' THEN
        -- Use current year as default
        start_year := EXTRACT(YEAR FROM CURRENT_DATE);
        end_year := start_year + 1;
        academic_year_str := start_year || '-' || end_year;
    ELSE
        -- Parse academic year string (e.g., "2024-2025" -> start_year=2024, end_year=2025)
        start_year := CAST(SPLIT_PART(academic_year_str, '-', 1) AS INTEGER);
        end_year := CAST(SPLIT_PART(academic_year_str, '-', 2) AS INTEGER);
    END IF;
    
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
CREATE OR REPLACE FUNCTION get_or_create_default_term_sc(session_uuid UUID)
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

-- Update student_classes table with session and term data
UPDATE student_classes 
SET 
    academic_session_id = get_or_create_academic_session_sc(school_id, COALESCE(academic_year, '2024-2025')),
    term_id = get_or_create_default_term_sc(get_or_create_academic_session_sc(school_id, COALESCE(academic_year, '2024-2025')))
WHERE academic_session_id IS NULL OR term_id IS NULL;

-- Add foreign key constraints (if they don't exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints 
                   WHERE constraint_name = 'fk_student_classes_academic_session') THEN
        ALTER TABLE student_classes
        ADD CONSTRAINT fk_student_classes_academic_session 
        FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints 
                   WHERE constraint_name = 'fk_student_classes_term') THEN
        ALTER TABLE student_classes
        ADD CONSTRAINT fk_student_classes_term 
        FOREIGN KEY (term_id) REFERENCES terms(id);
    END IF;
END $$;

-- Make the new columns NOT NULL after data migration
ALTER TABLE student_classes 
ALTER COLUMN academic_session_id SET NOT NULL,
ALTER COLUMN term_id SET NOT NULL;

-- Update unique constraints to use new columns
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

-- Create indexes for better performance (if they don't exist)
CREATE INDEX IF NOT EXISTS idx_student_classes_school
ON student_classes (school_id, is_active);

CREATE INDEX IF NOT EXISTS idx_student_classes_student
ON student_classes (student_id, is_active);

CREATE INDEX IF NOT EXISTS idx_student_classes_track
ON student_classes (track_id, is_active);

CREATE INDEX IF NOT EXISTS idx_student_classes_admission
ON student_classes (admission_number, school_id);

CREATE INDEX IF NOT EXISTS idx_student_classes_class
ON student_classes (class_id, is_active);

CREATE INDEX IF NOT EXISTS idx_student_classes_session
ON student_classes (academic_session_id, school_id);

CREATE INDEX IF NOT EXISTS idx_student_classes_term
ON student_classes (term_id, school_id);

CREATE INDEX IF NOT EXISTS idx_student_classes_session_term
ON student_classes (academic_session_id, term_id, school_id);

-- Clean up functions
DROP FUNCTION IF EXISTS get_or_create_academic_session_sc(UUID, VARCHAR);
DROP FUNCTION IF EXISTS get_or_create_default_term_sc(UUID);