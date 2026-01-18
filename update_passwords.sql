-- SQL Script to update all user passwords to @Pass123
-- BCrypt hash for @Pass123: $2a$10$xYZvN8QXK9J5kP7wL2mHOuE8RqYvN8QXK9J5kP7wL2mHOuE8RqYvNO

-- Note: You need to generate the actual BCrypt hash for @Pass123
-- The hash below is a placeholder. Use the Kotlin script to generate the correct hash.

-- To use this script:
-- 1. First, run the Kotlin utility to generate the BCrypt hash
-- 2. Replace the hash below with the generated hash
-- 3. Connect to your PostgreSQL database
-- 4. Run this script

UPDATE users 
SET password_hash = '$2a$10$xYZvN8QXK9J5kP7wL2mHOuE8RqYvN8QXK9J5kP7wL2mHOuE8RqYvNO'
WHERE password_hash IS NOT NULL;

-- Verify the update
SELECT 
    id,
    phone_number,
    email,
    first_name,
    last_name,
    CASE 
        WHEN password_hash IS NOT NULL THEN 'Password Set'
        ELSE 'No Password'
    END as password_status
FROM users
ORDER BY created_at DESC
LIMIT 10;
