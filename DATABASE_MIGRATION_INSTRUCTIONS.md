# Database Migration Instructions

## Overview
This migration adds `academic_session_id` and `term_id` columns to the teacher assignment tables (`class_teachers` and `subject_teachers`) and the `student_classes` table, replacing the string-based `academic_year` approach with proper foreign key relationships.

## Prerequisites
- PostgreSQL database access
- Backup of your current database (recommended)

## Migration Steps

### Option 1: Run the SQL Script Directly

1. **Backup your database first:**
   ```bash
   pg_dump -h localhost -U abuhaneefayn -d myschool > backup_before_migration.sql
   ```

2. **Run the migration script:**
   ```bash
   psql -h localhost -U abuhaneefayn -d myschool -f database_migration_teacher_assignments.sql
   ```

### Option 2: Run via psql Interactive Session

1. **Connect to your database:**
   ```bash
   psql -h localhost -U abuhaneefayn -d myschool
   ```

2. **Run the migration script:**
   ```sql
   \i database_migration_teacher_assignments.sql
   ```

### Option 3: Run via Application (if you have admin access)

You can also execute the SQL commands through a database administration tool like pgAdmin or DBeaver.

## What the Migration Does

1. **Adds new columns** to `class_teachers`, `subject_teachers`, and `student_classes` tables
2. **Creates default academic sessions and terms** for each school if they don't exist
3. **Migrates existing data** by linking records to the appropriate session and term
4. **Adds foreign key constraints** to ensure data integrity
5. **Updates unique constraints** to use the new columns
6. **Creates indexes** for better query performance

## Verification

After running the migration, verify that:

1. **New columns exist:**
   ```sql
   \d class_teachers
   \d subject_teachers  
   \d student_classes
   ```

2. **Data has been migrated:**
   ```sql
   SELECT COUNT(*) FROM class_teachers WHERE academic_session_id IS NOT NULL;
   SELECT COUNT(*) FROM subject_teachers WHERE academic_session_id IS NOT NULL;
   SELECT COUNT(*) FROM student_classes WHERE academic_session_id IS NOT NULL;
   ```

3. **Academic sessions and terms exist:**
   ```sql
   SELECT school_id, session_name, is_current_session FROM academic_sessions;
   SELECT academic_session_id, term_name, is_current_term FROM terms;
   ```

## Rollback (if needed)

If you need to rollback the migration:

1. **Restore from backup:**
   ```bash
   dropdb myschool
   createdb myschool
   psql -h localhost -U abuhaneefayn -d myschool < backup_before_migration.sql
   ```

## Post-Migration

After successful migration:

1. **Restart your application** to ensure the new schema is recognized
2. **Test teacher assignment functionality** to ensure everything works correctly
3. **Monitor application logs** for any issues

## Troubleshooting

### Common Issues:

1. **Permission denied:** Ensure your database user has sufficient privileges
2. **Constraint violations:** Check if there are any orphaned records in the tables
3. **Column already exists:** The script uses `IF NOT EXISTS` clauses to handle this

### If you encounter errors:

1. Check the PostgreSQL logs for detailed error messages
2. Ensure all referenced tables (`schools`, `academic_sessions`, `terms`) exist
3. Verify that the database user has CREATE, ALTER, and INSERT permissions

## Support

If you encounter any issues during migration, please:

1. Check the error messages carefully
2. Ensure you have a backup before attempting fixes
3. Consider running the migration on a test database first