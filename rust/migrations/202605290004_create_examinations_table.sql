-- Create examinations table for class/subject examination records.
-- The migration is idempotent so startup migration runs can safely re-apply it.

CREATE TABLE IF NOT EXISTS examinations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    school_id UUID NOT NULL REFERENCES schools(id),
    created_by UUID NOT NULL REFERENCES users(id),
    duration_minutes INTEGER NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NULL,
    exam_type TEXT NOT NULL,
    is_published BOOLEAN NULL DEFAULT FALSE,
    start_time TIMESTAMP WITHOUT TIME ZONE NULL,
    title TEXT NOT NULL,
    total_marks INTEGER NULL,
    class_id UUID NOT NULL REFERENCES classes(id),
    subject_id UUID NOT NULL REFERENCES subjects(id),
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    session_id UUID NOT NULL REFERENCES academic_sessions(id),
    term_id UUID NOT NULL REFERENCES terms(id)
);

CREATE INDEX IF NOT EXISTS idx_examinations_school_id ON examinations (school_id);
CREATE INDEX IF NOT EXISTS idx_examinations_class_id ON examinations (class_id);
CREATE INDEX IF NOT EXISTS idx_examinations_subject_id ON examinations (subject_id);
CREATE INDEX IF NOT EXISTS idx_examinations_session_id ON examinations (session_id);
CREATE INDEX IF NOT EXISTS idx_examinations_term_id ON examinations (term_id);
CREATE INDEX IF NOT EXISTS idx_examinations_school_class_subject_session_term ON examinations (
    school_id, class_id, subject_id, session_id, term_id
);
