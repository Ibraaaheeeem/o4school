-- Enforce school_subject_id as the sole class-subject linkage

-- Remove rows that cannot be resolved to a school_subject link
DELETE FROM class_subjects
WHERE school_subject_id IS NULL;

ALTER TABLE class_subjects
    ALTER COLUMN school_subject_id SET NOT NULL;

-- Remove legacy direct subject linkage column
ALTER TABLE class_subjects
    DROP COLUMN IF EXISTS subject_id;
