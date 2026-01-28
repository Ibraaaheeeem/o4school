# Community Data Import Template

This document outlines the structure for Excel files used to import community data (Students, Parents, and Staff) into the 4School platform.

## Import File Structure

**Use a single Excel file (.xlsx) with three separate sheets:**
- Sheet 1: **Students** (containing student records)
- Sheet 2: **Parents** (containing parent/guardian records)
- Sheet 3: **Staff** (containing staff records)

Each sheet should follow the format specified below for its respective category.

## 1. Student Import Template

Use this format to import student records.

| Column Name | Required | Data Type | Description | Example |
| :--- | :---: | :--- | :--- | :--- |
| **FirstName** | Yes | Text | Student's first name | `Ibrahim` |
| **LastName** | Yes | Text | Student's last name | `Musa` |
| **MiddleName** | No | Text | Student's middle name | `Aliyu` |
| **Gender** | Yes | Text | `M` or `F` | `M` |
| **DateOfBirth** | Yes | Date | Format: `DD/MM/YYYY` | `15/05/2010` |
| **AdmissionNumber** | No | Text | If empty, system will auto-generate | `ADM/2024/001` |
| **Address** | No | Text | Residential address | `123 Lagos Street, Kano` |

---

## 2. Parent Import Template

Use this format to import parent/guardian records. **Import this before students** so the system can link them.

| Column Name | Required | Data Type | Description | Example |
| :--- | :---: | :--- | :--- | :--- |
| **FirstName** | Yes | Text | Parent's first name | `Fatima` |
| **LastName** | Yes | Text | Parent's last name | `Musa` |
| **Email** | Yes | Email | Unique email address (used for login & linking) | `parent@example.com` |
| **PhoneNumber** | Yes | Text | Phone number with or without country code | `08012345678` |
| **Address** | No | Text | Residential address | `123 Lagos Street, Kano` |

---

## 3. Staff Import Template

Use this format to import teaching and non-teaching staff.

| Column Name | Required | Data Type | Description | Example |
| :--- | :---: | :--- | :--- | :--- |
| **FirstName** | Yes | Text | Staff's first name | `Chioma` |
| **LastName** | Yes | Text | Staff's last name | `Okonkwo` |
| **Email** | Yes | Email | Unique email address | `teacher@school.com` |
| **PhoneNumber** | Yes | Text | Phone number | `07098765432` |
| **Designation** | Yes | Text | `Teacher`, `Bursar`, `Cleaner` |
| **DateOfHire** | No | Date | Format: `DD/MM/YYYY` | `10/01/2022` |

## Notes for Administrators
1. **Date Format**: Ensure all dates are in `DD/MM/YYYY` format (e.g., `15/05/2010` for May 15, 2010) to avoid errors.
2. **Duplicates**: The system will use Email (for Parents/Staff) and Admission Number (for Students) to detect duplicates.
3. **Post-Import Setup**: After importing students, you can assign them to classes, link them to parents, and set grade levels within the application.
