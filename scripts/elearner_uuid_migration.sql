-- Migration: Convert elearner.subjects.id from INT to UUID
-- Purpose: Enable seamless linking with myschool database

BEGIN;

-- 0. Ensure extension exists
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Add temporary UUID columns
ALTER TABLE subjects ADD COLUMN uuid_id UUID DEFAULT uuid_generate_v4();

-- Referencing tables
ALTER TABLE assessments ADD COLUMN subject_uuid_id UUID;
ALTER TABLE class_subjects ADD COLUMN subject_uuid_id UUID;
ALTER TABLE lessons ADD COLUMN subject_uuid_id UUID;
ALTER TABLE student_progress ADD COLUMN subject_uuid_id UUID;
ALTER TABLE subject_enrollments ADD COLUMN subject_uuid_id UUID;
ALTER TABLE teacher_assignments ADD COLUMN subject_uuid_id UUID;
ALTER TABLE topics ADD COLUMN subject_uuid_id UUID;
ALTER TABLE user_subjects ADD COLUMN subject_uuid_id UUID;
ALTER TABLE weeks ADD COLUMN subject_uuid_id UUID;

-- 2. Populate UUID columns
UPDATE assessments t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE class_subjects t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE lessons t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE student_progress t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE subject_enrollments t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE teacher_assignments t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE topics t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE user_subjects t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;
UPDATE weeks t SET subject_uuid_id = s.uuid_id FROM subjects s WHERE t.subject_id = s.id;

-- 3. Drop constraints (FKs and Uniques)
ALTER TABLE weeks DROP CONSTRAINT IF EXISTS unique_week_per_term_per_subject;

ALTER TABLE assessments DROP CONSTRAINT assessments_subject_id_fkey;
ALTER TABLE class_subjects DROP CONSTRAINT class_subjects_subject_id_fkey;
ALTER TABLE lessons DROP CONSTRAINT lessons_subject_id_fkey;
ALTER TABLE student_progress DROP CONSTRAINT student_progress_subject_id_fkey;
ALTER TABLE subject_enrollments DROP CONSTRAINT subject_enrollments_subject_id_fkey;
ALTER TABLE teacher_assignments DROP CONSTRAINT teacher_assignments_subject_id_fkey;
ALTER TABLE topics DROP CONSTRAINT topics_subject_id_fkey;
ALTER TABLE user_subjects DROP CONSTRAINT user_subjects_subject_id_fkey;
ALTER TABLE weeks DROP CONSTRAINT weeks_subject_id_fkey;

-- 4. Finalize Subject table
ALTER TABLE subjects DROP CONSTRAINT subjects_pkey;
ALTER TABLE subjects DROP COLUMN id;
ALTER TABLE subjects RENAME COLUMN uuid_id TO id;
ALTER TABLE subjects ADD PRIMARY KEY (id);

-- 5. Finalize referencing tables
-- assessments
ALTER TABLE assessments DROP COLUMN subject_id;
ALTER TABLE assessments RENAME COLUMN subject_uuid_id TO subject_id;
-- subject_id is nullable here
ALTER TABLE assessments ADD CONSTRAINT assessments_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- class_subjects
ALTER TABLE class_subjects DROP COLUMN subject_id;
ALTER TABLE class_subjects RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE class_subjects ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE class_subjects ADD CONSTRAINT class_subjects_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- lessons
ALTER TABLE lessons DROP COLUMN subject_id;
ALTER TABLE lessons RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE lessons ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE lessons ADD CONSTRAINT lessons_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- student_progress
ALTER TABLE student_progress DROP COLUMN subject_id;
ALTER TABLE student_progress RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE student_progress ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE student_progress ADD CONSTRAINT student_progress_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- subject_enrollments
ALTER TABLE subject_enrollments DROP COLUMN subject_id;
ALTER TABLE subject_enrollments RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE subject_enrollments ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE subject_enrollments ADD CONSTRAINT subject_enrollments_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- teacher_assignments
ALTER TABLE teacher_assignments DROP COLUMN subject_id;
ALTER TABLE teacher_assignments RENAME COLUMN subject_uuid_id TO subject_id;
-- subject_id is nullable here
ALTER TABLE teacher_assignments ADD CONSTRAINT teacher_assignments_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- topics
ALTER TABLE topics DROP COLUMN subject_id;
ALTER TABLE topics RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE topics ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE topics ADD CONSTRAINT topics_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- user_subjects
ALTER TABLE user_subjects DROP COLUMN subject_id;
ALTER TABLE user_subjects RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE user_subjects ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE user_subjects ADD CONSTRAINT user_subjects_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);

-- weeks
ALTER TABLE weeks DROP COLUMN subject_id;
ALTER TABLE weeks RENAME COLUMN subject_uuid_id TO subject_id;
ALTER TABLE weeks ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE weeks ADD CONSTRAINT weeks_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects(id);
ALTER TABLE weeks ADD CONSTRAINT unique_week_per_term_per_subject UNIQUE (subject_id, term, week);

COMMIT;
