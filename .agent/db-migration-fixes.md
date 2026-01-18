# Database Migration Fixes

## Issue
The user reported a `JpaSystemException` because newly added non-nullable fields (`isLocked` in `ClassFeeItem`) contained `NULL` values in the database for existing records. This occurred because `ddl-auto=update` added the columns but did not populate default values.

## Fixes Applied
Executed direct SQL updates to populate default values for existing records:

1.  **ClassFeeItem**:
    *   Set `is_locked = false` for all records where it was NULL.
    *   Command: `UPDATE class_fee_items SET is_locked = false WHERE is_locked IS NULL;`

2.  **FeeItem** (Proactive Fix):
    *   Set `staff_discount_type = 'NONE'` where NULL.
    *   Set `staff_discount_amount = 0` where NULL.
    *   Command: `UPDATE fee_items SET staff_discount_type = 'NONE' WHERE staff_discount_type IS NULL; UPDATE fee_items SET staff_discount_amount = 0 WHERE staff_discount_amount IS NULL;`

## Verification
*   `psql` commands returned successful update counts (`UPDATE 40`, `UPDATE 12`).
*   The application should now be able to load these entities without error.
