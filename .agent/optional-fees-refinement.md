# Optional Fees Refinement

## Changes
1.  **StudentOptionalFee Entity**:
    *   Added `academicSession` (ManyToOne)
    *   Added `term` (ManyToOne)
    *   Added `isLocked` (Boolean)
    *   Inherits `isActive` from `BaseEntity`.

2.  **ParentDashboardController**:
    *   Updated `toggleFee` to populate `academicSession` and `term` when creating a new `StudentOptionalFee` record.
    *   Updated `toggleFee` to use **soft delete** (toggling `isActive`) instead of hard delete.
    *   Updated `toggleFee` to check `StudentOptionalFee.isLocked` before allowing a parent to opt-out (deactivate).

3.  **FinancialService**:
    *   Updated fee calculation logic to only consider **active** optional fee selections (`existsBy...AndIsActive(..., true)`).

4.  **StudentOptionalFeeRepository**:
    *   Added `existsByStudentIdAndClassFeeItemIdAndIsActive` method.

## Logic
*   **Opt-In**:
    *   If record exists (active or inactive): Set `isActive = true`, update `optedInBy` and `optedInAt`.
    *   If record does not exist: Create new record with `isActive = true`.
*   **Opt-Out**:
    *   If record exists and is NOT locked: Set `isActive = false`.
    *   If record is locked: Prevent change.
*   **Fee Calculation**:
    *   Only `StudentOptionalFee` records with `isActive = true` are included in the total.
