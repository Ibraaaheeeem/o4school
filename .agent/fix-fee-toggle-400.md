# Fix: Optional Fee Toggle 400 Bad Request

## Issue
The user received a `400 Bad Request` when toggling optional fees. The URL was `/parent/student//toggle-fee`, indicating that the student ID was missing (empty string).

## Cause
Thymeleaf was unable to correctly resolve `${childFee['studentIdObj']}` (which was a `UUID` object) into a string for the URL path variable. This resulted in an empty segment in the URL.

## Fix
1.  **FinancialService**: Renamed `studentIdObj` to `studentUuid` and stored it as a **String** (`student.id!!.toString()`) in the data map. Updated the sort logic to parse this string back to UUID.
2.  **Parent Dashboard Template**: Updated the `hx-post` URL to use `${childFee['studentUuid']}`.

## Verification
*   The map now contains a String for the UUID.
*   Thymeleaf can easily render the String.
*   The URL should now be correctly formed: `/parent/student/{uuid}/toggle-fee`.
