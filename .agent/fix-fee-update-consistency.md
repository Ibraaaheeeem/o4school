# Fix: Optional Fee Update Consistency

## Issue
The user expected the total bill to update immediately when checking an optional fee, but it might not have been reflecting the change.

## Cause
The `toggleFee` method saves the `StudentOptionalFee` record and then immediately calls `parentDashboard` to refresh the view. Since both are transactional, the `save` might not have been flushed to the database (or visible to the subsequent query) before the `getFeeBreakdown` calculation ran.

## Fix
Added `studentOptionalFeeRepository.flush()` in `toggleFee` after saving the record. This forces the changes to be written to the persistence context/database, ensuring that the subsequent `existsBy...` query in `FinancialService` returns the correct result.

## Verification
*   `FinancialService` uses `existsByStudentIdAndClassFeeItemIdAndIsActive` which respects the `isActive` flag.
*   `ParentDashboardController` sets `isActive` correctly.
*   `flush()` ensures the state is synchronized.
