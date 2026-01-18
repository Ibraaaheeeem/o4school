# Fix: Invalid UUID in Optional Fee Toggle

## Issue
The user reported a `MethodArgumentTypeMismatchException` when toggling optional fees. The error `Invalid UUID string: STU0001` indicated that the frontend was sending the student's display ID (String) instead of the internal UUID to the controller.

## Cause
The `parent-dashboard.html` template was using `${childFee.studentId}` in the `hx-post` URL. In the `FinancialService` data model, `studentId` maps to the string ID (e.g., "STU0001"), while `studentIdObj` maps to the UUID.

## Fix
Updated `parent-dashboard.html` to use `${childFee.studentIdObj}` for the `hx-post` URL parameter.

```html
<!-- Before -->
th:attr="hx-post=@{/parent/student/{id}/toggle-fee(id=${childFee.studentId})}"

<!-- After -->
th:attr="hx-post=@{/parent/student/{id}/toggle-fee(id=${childFee['studentIdObj']})}"
```

Note: Used bracket notation `['studentIdObj']` to ensure correct Map key access in Thymeleaf.
