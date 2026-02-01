-- Migration to Global Subjects with Deduplication
-- This script transforms the subjects table to be global-only

-- 1. Add new columns for global subject properties
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS min_grade_level INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS max_grade_level INTEGER DEFAULT 12 NOT NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS category VARCHAR(255);

-- 2. Deduplicate Subjects
-- We will merge subjects with the same name into a single "Master" subject.
-- Ideally, we pick the one with the most assignments, or just the oldest one.

DO $$
DECLARE
    r RECORD;
    master_id UUID;
    min_grade INT;
    max_grade INT;
BEGIN
    -- Iterate over duplicated subject names
    FOR r IN 
        SELECT subject_name 
        FROM subjects 
        GROUP BY subject_name 
        HAVING COUNT(*) > 1
    LOOP
        -- Pick a master subject (e.g., the first created one)
        SELECT id INTO master_id 
        FROM subjects 
        WHERE subject_name = r.subject_name 
        ORDER BY created_at ASC 
        LIMIT 1;

        RAISE NOTICE 'Merging duplicate subjects for "%" into master ID %', r.subject_name, master_id;

        -- Update foreign keys to point to the master subject
        
        -- class_subjects: Handle potential conflicts (if master is already assigned to same class)
        -- In this case, since schools are different, there shouldn't be a unique constraint conflict on (class, subject) unless we assigned same subject twice to same class.
        -- classes are unique per school. So (class_id, new_master_subject_id) should be unique because class_id implies school.
        UPDATE class_subjects 
        SET subject_id = master_id 
        WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);
        
        -- subject_scores
        UPDATE subject_scores
        SET subject_id = master_id
        WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);

        -- examinations
        UPDATE examinations
        SET subject_id = master_id
        WHERE subject_id IN (SELECT id FROM subjects WHERE subject_name = r.subject_name AND id != master_id);

        -- Delete the duplicates
        DELETE FROM subjects 
        WHERE subject_name = r.subject_name AND id != master_id;
        
        -- Calculate consolidated grade range based on assigned classes
        -- Note: This depends on class grade levels. If no assignments, keep defaults.
        
        -- Use a temporary logic to find min/max grades from assignments
        -- If we can't join easily in this block, we can run a separate update later.
        
    END LOOP;
END $$;

-- 3. Calculate and Update Grade Ranges for ALL subjects based on existing assignments
-- Logic: Join with class_subjects -> classes. 
-- For each subject, find min(class.grade_level) and max(class.grade_level).
-- Only update if assignments exist.

WITH grade_ranges AS (
    SELECT 
        cs.subject_id,
        MIN(c.grade_level) as new_min,
        MAX(c.grade_level) as new_max
    FROM class_subjects cs
    JOIN classes c ON cs.class_id = c.id
    GROUP BY cs.subject_id
)
UPDATE subjects s
SET 
    min_grade_level = gr.new_min,
    max_grade_level = gr.new_max
FROM grade_ranges gr
WHERE s.id = gr.subject_id;


-- 4. Final Structure Changes
-- Drop school_id and existing school-specific constraints

-- Rename constraints and indexes if they exist (handling IF EXISTS cleanly)
DO $$
BEGIN
    -- Drop constraints
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'unique_subject_name_school') THEN
        ALTER TABLE subjects DROP CONSTRAINT unique_subject_name_school;
    END IF;
    
    -- Drop indexes (Postgres syntax)
    DROP INDEX IF EXISTS idx_subject_school_code;
    DROP INDEX IF EXISTS idx_subject_school_id;
END $$;

ALTER TABLE subjects DROP COLUMN IF EXISTS school_id;

-- Add new global unique constraint
ALTER TABLE subjects ADD CONSTRAINT unique_subject_name UNIQUE (subject_name);

-- Add index on subject_code
CREATE INDEX IF NOT EXISTS idx_subject_code ON subjects(subject_code);
