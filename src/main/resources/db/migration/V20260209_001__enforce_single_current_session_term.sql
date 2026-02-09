-- Migration to enforce single current session and term per school
-- and clean up data inconsistencies

-- 1. Reset all current session status to start fresh
UPDATE academic_sessions SET is_current_session = false;

-- 2. Pick the single most recent active session for each school
UPDATE academic_sessions 
SET is_current_session = true
WHERE id IN (
    SELECT DISTINCT ON (school_id) id
    FROM academic_sessions 
    WHERE is_active = true
    ORDER BY school_id, start_date DESC
);

-- 3. Add a unique partial index to ensure we never have multiple current sessions per school again
DROP INDEX IF EXISTS unique_current_session_per_school;
CREATE UNIQUE INDEX unique_current_session_per_school 
ON academic_sessions (school_id) 
WHERE is_current_session = true AND is_active = true;

-- 4. Reset all current term status
UPDATE terms SET is_current_term = false;

-- 5. Pick the single most logical current term for each CURRENT session
-- Use dynamic SQL to handle term_number vs term_order safely
DO $$
DECLARE
    sort_col TEXT;
    sql_text TEXT;
BEGIN
    -- Determine which column to sort by
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='terms' AND column_name='term_number') THEN
        sort_col := 'term_number';
    ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='terms' AND column_name='term_order') THEN
        sort_col := 'term_order';
    ELSE
        sort_col := 'id'; -- Fallback to ID if neither exists
    END IF;

    -- Build and execute the update using dynamic SQL
    sql_text := 'UPDATE terms ' ||
                'SET is_current_term = true ' ||
                'WHERE id IN (' ||
                '    SELECT DISTINCT ON (t.academic_session_id) t.id ' ||
                '    FROM terms t ' ||
                '    JOIN academic_sessions s ON t.academic_session_id = s.id ' ||
                '    WHERE s.is_current_session = true ' ||
                '    AND t.is_active = true ' ||
                '    ORDER BY t.academic_session_id, t.' || sort_col || ' ASC' ||
                ')';
    
    EXECUTE sql_text;
END $$;

-- 6. Add a unique partial index to ensure we never have multiple current terms per session
DROP INDEX IF EXISTS unique_current_term_per_session;
CREATE UNIQUE INDEX unique_current_term_per_session
ON terms (academic_session_id)
WHERE is_current_term = true AND is_active = true;
