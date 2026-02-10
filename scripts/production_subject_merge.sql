-- =============================================================================
-- SUBJECT GLOBALIZATION AND DATA MERGE SCRIPT
-- =============================================================================
-- This script merges local "Master" subjects into production while preserving
-- existing production IDs and relationships.
-- =============================================================================

-- 1. PREPARE THE PRODUCTION SUBJECTS TABLE (GLOBALIZATION)
-- -----------------------------------------------------------------------------
-- Add new columns if missing
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS min_grade_level INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS max_grade_level INTEGER DEFAULT 12 NOT NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS category VARCHAR(255);
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS is_core_subject BOOLEAN DEFAULT false;

-- Deduplicate by name (merging duplicates into the oldest record)
DO $$
DECLARE
    r RECORD;
    master_id UUID;
BEGIN
    FOR r IN SELECT subject_name FROM subjects GROUP BY subject_name HAVING COUNT(*) > 1 LOOP
        SELECT id INTO master_id FROM subjects WHERE subject_name = r.subject_name ORDER BY created_at ASC LIMIT 1;
        
        UPDATE class_subjects SET subject_id = master_id WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);
        UPDATE subject_scores SET subject_id = master_id WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);
        UPDATE examinations SET subject_id = master_id WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);
        UPDATE subject_mappings SET subject_id = master_id WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);
        
        DELETE FROM subjects WHERE subject_name = r.subject_name AND id != master_id;
    END LOOP;
END $$;

-- Drop school_id and make name unique
ALTER TABLE subjects DROP COLUMN IF EXISTS school_id;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'unique_subject_name') THEN
        ALTER TABLE subjects ADD CONSTRAINT unique_subject_name UNIQUE (subject_name);
    END IF;
END $$;


-- 2. STAGING: CREATE TEMPORARY TABLES FOR LOCAL DATA
-- -----------------------------------------------------------------------------
-- These tables will hold your local data temporarily for the merge
CREATE TABLE IF NOT EXISTS staging_local_subjects (
    local_id UUID,
    subject_name VARCHAR(255),
    subject_code VARCHAR(255),
    description TEXT,
    is_core_subject BOOLEAN,
    min_grade_level INTEGER,
    max_grade_level INTEGER,
    category VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS staging_local_mappings (
    local_subject_id UUID,
    grade_level INTEGER,
    elearner_subject_id UUID
);

-- NOTE: You will import your local data into these 'staging_' tables.


-- 3. THE MERGE: UPDATE PRODUCTION DATA WITH LOCAL STANDARDS
-- -----------------------------------------------------------------------------
-- Insert missing subjects from staging into production
INSERT INTO subjects (subject_name, subject_code, description, is_core_subject, min_grade_level, max_grade_level, category)
SELECT subject_name, subject_code, description, is_core_subject, min_grade_level, max_grade_level, category
FROM staging_local_subjects
ON CONFLICT (subject_name) DO UPDATE SET
    subject_code = EXCLUDED.subject_code,
    description = EXCLUDED.description,
    is_core_subject = EXCLUDED.is_core_subject,
    min_grade_level = EXCLUDED.min_grade_level,
    max_grade_level = EXCLUDED.max_grade_level,
    category = EXCLUDED.category;

-- 4. UPDATE MAPPINGS: LINK PROD SUBJECTS TO E-LEARNER IDS
-- -----------------------------------------------------------------------------
-- Clear existing mappings and recreate them using production IDs
DELETE FROM subject_mappings;

INSERT INTO subject_mappings (id, subject_id, grade_level, elearner_subject_id, is_active, created_at, updated_at)
SELECT 
    gen_random_uuid(),
    p.id,                     -- Use the Production ID (even for local data)
    m.grade_level,
    m.elearner_subject_id,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM staging_local_mappings m
JOIN staging_local_subjects s ON m.local_subject_id = s.local_id
JOIN subjects p ON s.subject_name = p.subject_name;


-- 5. CLEANUP
-- -----------------------------------------------------------------------------
-- DROP TABLE IF EXISTS staging_local_subjects;
-- DROP TABLE IF EXISTS staging_local_mappings;
