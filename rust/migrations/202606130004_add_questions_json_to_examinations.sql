-- Migration: Add questions_json column to examinations table
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS questions_json TEXT NULL;
