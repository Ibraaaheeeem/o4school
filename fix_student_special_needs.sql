-- Fix null values for hasSpecialNeeds field in students table (PostgreSQL)
-- This ensures all existing students have a proper boolean value instead of null

-- Update null values to false
UPDATE students 
SET has_special_needs = false 
WHERE has_special_needs IS NULL;

-- Verify the update
SELECT 
    COUNT(*) as total_students, 
    COUNT(CASE WHEN has_special_needs = true THEN 1 END) as with_special_needs,
    COUNT(CASE WHEN has_special_needs = false THEN 1 END) as without_special_needs,
    COUNT(CASE WHEN has_special_needs IS NULL THEN 1 END) as null_values
FROM students 
WHERE is_active = true;
