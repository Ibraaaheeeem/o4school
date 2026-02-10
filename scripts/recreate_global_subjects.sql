-- 1. Create the table structure for Global Subjects
CREATE TABLE IF NOT EXISTS global_subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255),
    min_grade_level INTEGER NOT NULL DEFAULT 1,
    max_grade_level INTEGER NOT NULL DEFAULT 12,
    category VARCHAR(255),
    is_core BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT unique_global_subject_name UNIQUE (name),
    CONSTRAINT unique_global_subject_code UNIQUE (code)
);

-- 2. Populate the Master Catalog with standard curriculum
-- This data is used by the SchoolStructureService to automatically assign subjects to classes.
INSERT INTO global_subjects (name, code, category, is_core, min_grade_level, max_grade_level)
VALUES 
    ('Mathematics', 'MATH', 'Core', true, 1, 12),
    ('English Language', 'ENG', 'Core', true, 1, 12),
    ('Basic Science', 'SCI', 'Science', true, 1, 9),
    ('Social Studies', 'SS', 'Social Sciences', true, 1, 9),
    ('Introductory Technology', 'INT-TECH', 'Technical', true, 7, 9),
    ('Physics', 'PHY', 'Science', true, 10, 12),
    ('Chemistry', 'CHEM', 'Science', true, 10, 12),
    ('Biology', 'BIO', 'Science', true, 10, 12),
    ('Geography', 'GEO', 'Social Sciences', false, 7, 12),
    ('History', 'HIST', 'Arts', false, 1, 12),
    ('Economics', 'ECON', 'Commercial', true, 10, 12),
    ('Government', 'GOV', 'Arts', true, 10, 12),
    ('Literature in English', 'LIT', 'Arts', true, 10, 12),
    ('Agricultural Science', 'AGRIC', 'Science', false, 1, 12),
    ('Computer Science', 'CS', 'Vocational', true, 1, 12),
    ('Physical Education', 'PE', 'Vocational', false, 1, 12),
    ('Creative Arts', 'ART', 'Arts', false, 1, 9),
    ('Civic Education', 'CIVIC', 'Core', true, 1, 12)
ON CONFLICT (name) DO NOTHING;
