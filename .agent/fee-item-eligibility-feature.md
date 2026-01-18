# Fee Item Eligibility Features

## Overview
Added support for defining fee item eligibility based on Gender and Student Status using a custom tri-state switch UI.

## Changes Implemented

### 1. Entity Update
Modified `FeeItem` entity in `src/main/kotlin/com/haneef/_school/entity/FeeItem.kt`:
- Added `GenderEligibility` enum: `ALL`, `MALE`, `FEMALE`
- Added `StudentStatusEligibility` enum: `ALL`, `NEW`, `RETURNING`
- Added fields `genderEligibility` and `studentStatusEligibility` to `FeeItem` class.

### 2. DTO Update
Updated `FeeItemDto` in `src/main/kotlin/com/haneef/_school/dto/FinancialDtos.kt` to include the new eligibility fields.

### 3. UI Component
Created a "Segmented Control" (Tri-State Switch) CSS component in `src/main/resources/static/css/financial.css`.
- Supports 3 states (e.g., Boy/All/Girl, New/All/Returning).
- Visual feedback with color coding (Blue/Pink/Purple for gender, Green/Orange/Blue for status).
- Fully responsive and accessible (uses standard radio inputs).

### 4. Frontend Update
Updated `src/main/resources/templates/admin/financial/fee-item-modal.html`:
- Added "Eligibility Criteria" section.
- Implemented the segmented control switches for Gender and Student Status.
- Uses FontAwesome icons for visual cues.
- **Fix**: Renamed `session` loop variable to `acadSession` to avoid conflict with Thymeleaf reserved word.

### 5. Controller Update
Updated `FinancialController` in `src/main/kotlin/com/haneef/_school/controller/FinancialController.kt`:
- `saveFeeItemHtmx` method now saves the eligibility preferences.
- Defaults to `ALL` if not specified.

## Usage
When creating or editing a fee item, administrators can now specify:
- **Gender**: Whether the fee applies to Boys only, Girls only, or All students.
- **Status**: Whether the fee applies to New students only, Returning students only, or All students.

This allows for more granular fee structures (e.g., "New Student Registration Fee" or "Uniform Fee (Girls)").
