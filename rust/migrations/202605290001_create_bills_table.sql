-- Create bills table for student billing records.
-- This migration is idempotent and safe to run multiple times.

CREATE TABLE IF NOT EXISTS bills (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    school_id UUID NOT NULL REFERENCES schools(id),
    student_id UUID NOT NULL REFERENCES students(id),
    academic_session_id UUID NULL REFERENCES academic_sessions(id),
    term_id UUID NULL REFERENCES terms(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    breakdown TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_bills_school_id ON bills (school_id);
CREATE INDEX IF NOT EXISTS idx_bills_student_id ON bills (student_id);
CREATE INDEX IF NOT EXISTS idx_bills_school_student_active ON bills (school_id, student_id, is_active);
