# Student Boolean Null Value Fix

## Problem
After resolving the 403 authentication issue, a new error appeared:
```
java.lang.IllegalArgumentException: Can not set boolean field com.haneef._school.entity.Student.isNew to null value
```

This error occurred because the database had `NULL` values in the `students.is_new` column, but the entity field was defined as a primitive `boolean` which cannot accept `null` values.

## Root Cause
- Database column `students.is_new` contained `NULL` values
- Entity field `Student.isNew` was defined as `Boolean = true` (primitive, non-nullable)
- Hibernate tried to set `null` from database to primitive boolean field
- This caused `PropertyAccessException` during entity loading

## Solution
Modified the `Student` entity to handle `NULL` values gracefully by using custom getter/setter properties:

### Before (Problematic):
```kotlin
@Column(name = "is_new", nullable = false)
var isNew: Boolean = true,

@Column(name = "has_special_needs")
var hasSpecialNeeds: Boolean = false,
```

### After (Fixed):
```kotlin
@Column(name = "is_new")
private var _isNew: Boolean? = null

var isNew: Boolean
    get() = _isNew ?: true  // Default to true if null
    set(value) { _isNew = value }

@Column(name = "has_special_needs")
private var _hasSpecialNeeds: Boolean? = null

var hasSpecialNeeds: Boolean
    get() = _hasSpecialNeeds ?: false  // Default to false if null
    set(value) { _hasSpecialNeeds = value }
```

## How It Works
1. **Private nullable field**: `_isNew: Boolean?` can accept `null` from database
2. **Public non-null property**: `isNew: Boolean` provides clean API
3. **Safe getter**: Returns default value (`true` for `isNew`, `false` for `hasSpecialNeeds`) when database value is `null`
4. **Normal setter**: Allows setting values normally

## Benefits
- ✅ **Backward compatible**: Handles existing `NULL` values in database
- ✅ **Clean API**: Public properties still appear as non-nullable `Boolean`
- ✅ **Safe defaults**: Provides sensible defaults when database has `NULL`
- ✅ **No migration needed**: Works with existing data
- ✅ **Future-proof**: New records will have proper boolean values

## Database Migration (Optional)
Created `V1__fix_student_is_new_null_values.sql` to clean up existing `NULL` values:
```sql
UPDATE students SET is_new = true WHERE is_new IS NULL;
ALTER TABLE students ALTER COLUMN is_new SET NOT NULL;
ALTER TABLE students ALTER COLUMN is_new SET DEFAULT true;
```

## Testing
1. **Existing records**: Should load without errors, using default values for `NULL` fields
2. **New records**: Should save with proper boolean values
3. **API behavior**: Properties should behave as normal non-nullable booleans

## Similar Issues Prevention
This pattern can be applied to other boolean fields in entities if they encounter similar `NULL` value issues:
- `BaseEntity.isActive`
- `User.isVerified`
- `Staff.isClassTeacher`
- etc.

The fix ensures robust handling of database inconsistencies while maintaining clean entity APIs.