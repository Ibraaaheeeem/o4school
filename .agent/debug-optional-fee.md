# Debugging Optional Fee Issue

## Problem
The user reports that clicking the optional fee checkbox has "no backend effect".

## Investigation Steps
1.  **CSRF**: Verified that `SecurityConfig` enables CSRF and `fragments/header.html` includes the necessary HTMX configuration. This seems correct.
2.  **Controller Logic**: Reviewed `toggleFee` logic. It correctly handles opt-in (create/activate) and opt-out (deactivate).
3.  **Data Consistency**: Previously added `flush()` to ensure DB updates are visible to the subsequent `getFeeBreakdown` call.
4.  **UUID**: Previously fixed the UUID passing issue.

## Action Taken
Added debug logs to `ParentDashboardController.toggleFee` to track:
*   Incoming request parameters (`studentId`, `feeItemId`, `optedIn`).
*   Current state of the fee selection (`isActive`).
*   Lock status of the fee item.

## Next Steps
Please check the application logs when toggling the fee. Look for "Toggle Fee:" entries.
*   If logs are missing: The request is not reaching the controller (e.g., 403 Forbidden due to CSRF, or 404/500).
*   If logs show `optedIn=true` but `isActive` remains `false` (in subsequent reads): There might be a transaction or persistence issue.
*   If logs show success but UI reverts: The `getFeeBreakdown` logic might be filtering it out.
