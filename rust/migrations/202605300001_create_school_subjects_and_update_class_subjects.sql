-- Create school_subjects linking table (school-specific subject selection)
CREATE TABLE IF NOT EXISTS school_subjects (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    school_id UUID NOT NULL REFERENCES schools(id),
    subject_id UUID NOT NULL REFERENCES subjects(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_school_subjects_school_subject_unique
    ON school_subjects(school_id, subject_id);

-- Add school_subject_id to class_subjects and backfill from existing subject_id where possible
ALTER TABLE class_subjects
    ADD COLUMN IF NOT EXISTS school_subject_id UUID;

-- Seed school_subjects from existing class_subjects rows that use subject_id
INSERT INTO school_subjects (id, created_at, is_active, updated_at, school_id, subject_id)
SELECT DISTINCT md5(cs.school_id::text || cs.subject_id::text)::uuid, NOW(), true, NOW(), cs.school_id, cs.subject_id
FROM class_subjects cs
WHERE cs.subject_id IS NOT NULL
ON CONFLICT DO NOTHING;

UPDATE class_subjects cs
SET school_subject_id = ss.id
FROM school_subjects ss
WHERE cs.school_id = ss.school_id
  AND cs.subject_id = ss.subject_id
  AND cs.school_subject_id IS NULL;

ALTER TABLE class_subjects
    ADD CONSTRAINT fk_class_subjects_school_subject
    FOREIGN KEY (school_subject_id) REFERENCES school_subjects(id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_class_subjects_class_school_subject_unique
    ON class_subjects(class_id, school_subject_id)
    WHERE school_subject_id IS NOT NULL;
