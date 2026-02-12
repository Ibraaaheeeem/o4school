-- Add new UUID columns
ALTER TABLE assessments ADD COLUMN academic_session_id UUID;
ALTER TABLE assessments ADD COLUMN term_id UUID;

-- Migrate existing data
UPDATE assessments a
SET academic_session_id = s.id
FROM academic_sessions s
WHERE a.session = s.session_year AND a.school_id = s.school_id;

UPDATE assessments a
SET term_id = t.id
FROM terms t
WHERE a.academic_session_id = t.academic_session_id AND a.term = t.term_name AND a.school_id = t.school_id;

-- Add NOT NULL constraints (after migration, assuming data exists or defaults were string-based)
-- We might need to handle cases where migration fails/misses, but for string-driven systems usually it matches.
ALTER TABLE assessments ALTER COLUMN academic_session_id SET NOT NULL;
ALTER TABLE assessments ALTER COLUMN term_id SET NOT NULL;

-- Remove old unique constraint and indexes
ALTER TABLE assessments DROP CONSTRAINT IF EXISTS uq_assessment_school_student_term;
DROP INDEX IF EXISTS idx_assessment_school_session;

-- Add new unique constraint and indexes
ALTER TABLE assessments ADD CONSTRAINT uq_assessment_school_student_term_id UNIQUE (school_id, admission_number, academic_session_id, term_id);
CREATE INDEX idx_assessment_school_session_id ON assessments (school_id, academic_session_id, term_id);

-- Drop old columns
ALTER TABLE assessments DROP COLUMN session;
ALTER TABLE assessments DROP COLUMN term;

-- Add Foreign Key constraints
ALTER TABLE assessments ADD CONSTRAINT fk_assessment_academic_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);
ALTER TABLE assessments ADD CONSTRAINT fk_assessment_term FOREIGN KEY (term_id) REFERENCES terms(id);
