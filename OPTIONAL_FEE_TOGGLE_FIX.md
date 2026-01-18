# Optional Fee Toggle Fix - Parent Dashboard

## Problem
The optional fee toggle functionality on the parent dashboard had issues where:
1. Unchecking a checked optional fee didn't properly set the `StudentOptionalFee.isActive` to `false`
2. The checkbox state logic was inverted in the frontend
3. The locking mechanism wasn't properly checking both `ClassFeeItem.isLocked` and `StudentOptionalFee.isLocked`

## Solution

### 1. Backend Controller Fix (`ParentDashboardController.kt`)

**Improved Logic:**
- **Better Lock Checking**: Now checks both `classFeeItem.isLocked` AND `selection?.isLocked` to determine if changes are allowed
- **Clearer Opt-In/Opt-Out Logic**: Separated the logic for opting in vs opting out
- **Enhanced Logging**: Added detailed logging to track the toggle process

**Key Changes:**
```kotlin
// Check if the fee item or selection is locked
val isLocked = classFeeItem.isLocked || (selection?.isLocked == true)

if (optedIn) {
    // User wants to opt in to the fee
    if (selection == null) {
        // Create new selection
        val newSelection = StudentOptionalFee(...)
        newSelection.isActive = true
        studentOptionalFeeRepository.save(newSelection)
    } else {
        // Reactivate existing selection
        if (!selection.isActive) {
            selection.isActive = true
            selection.optedInBy = customUser.user.id.toString()
            selection.optedInAt = LocalDateTime.now()
            studentOptionalFeeRepository.save(selection)
        }
    }
} else {
    // User wants to opt out of the fee
    if (selection != null && selection.isActive) {
        selection.isActive = false
        studentOptionalFeeRepository.save(selection)
    }
}
```

### 2. Frontend Template Fix (`parent-dashboard.html`)

**Fixed JavaScript Logic:**
- **Removed `event.preventDefault()`**: This was preventing the checkbox from actually changing state
- **Corrected `optedIn` value**: Now sends `event.target.checked` directly instead of `!event.target.checked`

**Before (Broken):**
```html
onclick="event.preventDefault()"
hx-vals='js:{"feeItemId": event.target.getAttribute("data-fee-id"), "optedIn": !event.target.checked}'
```

**After (Fixed):**
```html
hx-vals='js:{"feeItemId": event.target.getAttribute("data-fee-id"), "optedIn": event.target.checked}'
```

## How It Works Now

### 1. **Checking a Fee (Opt-In)**
- User clicks unchecked checkbox → checkbox becomes checked
- HTMX sends `optedIn: true` to backend
- Backend creates new `StudentOptionalFee` record with `isActive: true`
- OR reactivates existing record by setting `isActive: true`

### 2. **Unchecking a Fee (Opt-Out)**
- User clicks checked checkbox → checkbox becomes unchecked
- HTMX sends `optedIn: false` to backend
- Backend sets existing `StudentOptionalFee.isActive: false`
- Fee is no longer included in calculations

### 3. **Locked Fee Handling**
- If `ClassFeeItem.isLocked` OR `StudentOptionalFee.isLocked` is `true`
- Checkbox is disabled in UI (`th:disabled="${item.isLocked}"`)
- Backend rejects changes with appropriate error message

## Database Impact

### StudentOptionalFee Records
- **Soft Delete Approach**: Records are not deleted, just marked `isActive: false`
- **Audit Trail**: Maintains history of when fees were opted in/out
- **Reactivation**: Previously opted-out fees can be opted back in (if not locked)

### Fee Calculation
The `FinancialService.getFeeBreakdown()` method determines `isOptedIn` status:
```kotlin
val isOptedIn = if (isMandatory) true else 
    studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(
        student.id!!, classFeeItem.id!!, true
    )
```

## Testing Scenarios

### ✅ **Working Scenarios**
1. **Check optional fee** → Creates active StudentOptionalFee record
2. **Uncheck optional fee** → Sets StudentOptionalFee.isActive = false
3. **Re-check previously unchecked fee** → Reactivates existing record
4. **Locked fee interaction** → Shows error message, no changes made

### 🔒 **Locking Behavior**
- **ClassFeeItem.isLocked = true** → All students cannot modify this fee
- **StudentOptionalFee.isLocked = true** → Specific student cannot modify this fee selection
- **UI Indication** → Checkbox disabled + lock icon (🔒) shown

## Benefits
- ✅ **Proper State Management**: Checkbox state correctly reflects database state
- ✅ **Audit Trail**: Maintains history of fee selections
- ✅ **Flexible Locking**: Supports both global and individual fee locking
- ✅ **Real-time Updates**: HTMX provides immediate UI feedback
- ✅ **Error Handling**: Clear error messages for locked fees

The optional fee toggle now works correctly, allowing parents to opt in/out of optional fees while respecting locking mechanisms and maintaining proper audit trails.