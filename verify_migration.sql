-- Verification script to check if the migration was successful

-- Check if new columns exist in class_teachers
SELECT 
    column_name, 
    data_type, 
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'class_teachers' 
AND column_name IN ('academic_session_id', 'term_id');

-- Check if new columns exist in subject_teachers
SELECT 
    column_name, 
    data_type, 
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'subject_teachers' 
AND column_name IN ('academic_session_id', 'term_id');

-- Check if new columns exist in student_classes
SELECT 
    column_name, 
    data_type, 
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'student_classes' 
AND column_name IN ('academic_session_id', 'term_id');

-- Check foreign key constraints
SELECT 
    tc.constraint_name, 
    tc.table_name, 
    kcu.column_name, 
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY' 
AND tc.table_name IN ('class_teachers', 'subject_teachers', 'student_classes')
AND kcu.column_name IN ('academic_session_id', 'term_id');

-- Check data migration - count records with new columns populated
SELECT 'class_teachers' as table_name, COUNT(*) as total_records, 
       COUNT(academic_session_id) as with_session_id,
       COUNT(term_id) as with_term_id
FROM class_teachers
UNION ALL
SELECT 'subject_teachers' as table_name, COUNT(*) as total_records,
       COUNT(academic_session_id) as with_session_id, 
       COUNT(term_id) as with_term_id
FROM subject_teachers
UNION ALL
SELECT 'student_classes' as table_name, COUNT(*) as total_records,
       COUNT(academic_session_id) as with_session_id,
       COUNT(term_id) as with_term_id  
FROM student_classes;

-- Check academic sessions and terms
SELECT 
    s.name as school_name,
    acs.session_name,
    acs.is_current_session,
    t.term_name,
    t.is_current_term
FROM schools s
LEFT JOIN academic_sessions acs ON s.id = acs.school_id AND acs.is_active = true
LEFT JOIN terms t ON acs.id = t.academic_session_id AND t.is_active = true
ORDER BY s.name, acs.session_name, t.term_order;

-- Check unique constraints
SELECT 
    tc.constraint_name,
    tc.table_name,
    string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) as columns
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'UNIQUE'
AND tc.table_name IN ('class_teachers', 'subject_teachers', 'student_classes')
GROUP BY tc.constraint_name, tc.table_name
ORDER BY tc.table_name, tc.constraint_name;