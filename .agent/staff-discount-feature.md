# Staff Discount Feature Implementation

## Overview
Implemented a feature to apply automatic discounts for students whose parents are staff members.

## Changes

### Backend
1.  **Entity (`FeeItem`)**:
    *   Added `staffDiscountType` (`DiscountType`: NONE, PERCENTAGE, FLAT_AMOUNT).
    *   Added `staffDiscountAmount` (`BigDecimal`).
    *   Added `DiscountType` enum.

2.  **DTO (`FeeItemDto`)**:
    *   Added corresponding fields to transfer data from the frontend.

3.  **Controller (`FinancialController`)**:
    *   Updated `saveFeeItemHtmx` to map the new fields from DTO to Entity.

4.  **Service (`FinancialService`)**:
    *   Injected `UserSchoolRoleRepository`.
    *   Added `isStaffChild(student)` helper method that checks if any parent of the student has `STAFF` or `SCHOOL_ADMIN` role in the school.
    *   Updated `calculateParentBalance` and `getFeeBreakdown` to apply the discount to the effective amount of fee items if the student is eligible.

### Frontend
1.  **Template (`fee-item-modal.html`)**:
    *   Added a "Staff Discount" section with a dropdown for discount type and an input for the amount.
    *   Added JavaScript to toggle the visibility of the amount input based on the selected type.

## Verification
*   **Build**: Successful.
*   **Logic**: The discount is applied dynamically when calculating balances and breakdowns, ensuring it reflects the current status of the parent and the fee item configuration.
