-- Create scoring_schemes table for managing scoring/grading schemes per class, session, and term
-- This migration is idempotent and safe to run multiple times.

CREATE TABLE IF NOT EXISTS scoring_schemes (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT true,
    school_id UUID NOT NULL REFERENCES schools(id),
    class_id UUID NOT NULL REFERENCES classes(id),
    academic_session_id UUID NULL REFERENCES academic_sessions(id),
    term_id UUID NULL REFERENCES terms(id),
    scoring_scheme JSONB NOT NULL,
    notes TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_scoring_schemes_school_id ON scoring_schemes (school_id);
CREATE INDEX IF NOT EXISTS idx_scoring_schemes_class_id ON scoring_schemes (class_id);
CREATE INDEX IF NOT EXISTS idx_scoring_schemes_academic_session_id ON scoring_schemes (academic_session_id);
CREATE INDEX IF NOT EXISTS idx_scoring_schemes_term_id ON scoring_schemes (term_id);
CREATE INDEX IF NOT EXISTS idx_scoring_schemes_school_class_session_term ON scoring_schemes (school_id, class_id, academic_session_id, term_id);
