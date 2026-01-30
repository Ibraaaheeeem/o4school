-- Insert Subjects
INSERT INTO subjects (name) VALUES ('Mathematics') ON CONFLICT (name) DO NOTHING;
INSERT INTO subjects (name) VALUES ('English Language') ON CONFLICT (name) DO NOTHING;
INSERT INTO subjects (name) VALUES ('Basic Science') ON CONFLICT (name) DO NOTHING;

-- Insert Data for Mathematics, Week 1, Term 1
WITH math AS (SELECT id FROM subjects WHERE name = 'Mathematics' LIMIT 1)
INSERT INTO weeks (week, term, subject_id, name, theme) 
SELECT 1, 1, id, 'Introduction to Algebra', 'Algebraic Thinking' FROM math
ON CONFLICT (subject_id, term, week) DO NOTHING;

-- Topics for Math
WITH math AS (SELECT id FROM subjects WHERE name = 'Mathematics' LIMIT 1)
INSERT INTO topics (name, description, subject_id, week, term, generated_by_ai)
SELECT 'Algebraic Expressions', 'Understanding variables and constants', id, 1, 1, false FROM math
ON CONFLICT DO NOTHING;

-- Subtopics
WITH topic AS (SELECT id FROM topics WHERE name = 'Algebraic Expressions' LIMIT 1)
INSERT INTO subtopics (name, description, topic_id, generated_by_ai)
SELECT 'Variables', 'What are variables?', id, false FROM topic
UNION ALL
SELECT 'Constants', 'What are constants?', id, false FROM topic;

-- Lessons
WITH math AS (SELECT id FROM subjects WHERE name = 'Mathematics' LIMIT 1)
INSERT INTO lessons (title, introduction, topic, subject_id, week, term, generated_by_ai)
SELECT 'Intro to Variables', 'Content for variables...', 'Algebraic Expressions', id, 1, 1, false FROM math;


-- Insert Data for English, Week 1, Term 1
WITH eng AS (SELECT id FROM subjects WHERE name = 'English Language' LIMIT 1)
INSERT INTO weeks (week, term, subject_id, name, theme)
SELECT 1, 1, id, 'Parts of Speech', 'Grammar Foundations' FROM eng
ON CONFLICT (subject_id, term, week) DO NOTHING;

WITH eng AS (SELECT id FROM subjects WHERE name = 'English Language' LIMIT 1)
INSERT INTO topics (name, description, subject_id, week, term, generated_by_ai)
SELECT 'Nouns', 'Types of nouns', id, 1, 1, false FROM eng;

WITH topic AS (SELECT id FROM topics WHERE name = 'Nouns' LIMIT 1)
INSERT INTO subtopics (name, description, topic_id, generated_by_ai)
SELECT 'Proper Nouns', 'Specific names', id, false FROM topic
UNION ALL
SELECT 'Common Nouns', 'General names', id, false FROM topic;

WITH eng AS (SELECT id FROM subjects WHERE name = 'English Language' LIMIT 1)
INSERT INTO lessons (title, introduction, topic, subject_id, week, term, generated_by_ai)
SELECT 'Understanding Nouns', 'Lesson content...', 'Nouns', id, 1, 1, false FROM eng;
