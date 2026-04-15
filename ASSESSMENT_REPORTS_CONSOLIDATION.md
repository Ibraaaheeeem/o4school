# Assessment Reports Consolidation Guide

## Overview
The assessment reports functionality has been refactored to eliminate code duplication between staff reports (`class-reports.html`) and admin reports (`reports.html`). The solution uses:

1. **Shared Thymeleaf Fragment** - `fragments/assessment-reports.html`
2. **Shared JavaScript Module** - `static/js/assessment-reports.js`
3. **Template-Specific Implementations** - Staff and admin templates retain their unique filter logic and API endpoints

## Architecture

### File Structure

```
templates/
├── fragments/
│   └── assessment-reports.html        # Reusable reports layout fragment
├── staff/
│   └── class-reports.html             # Staff-specific reports (uses shared JS)
└── admin/assessments/
    └── reports.html                   # Admin-specific reports (uses shared JS)

static/js/
└── assessment-reports.js              # Shared JavaScript module
```

## Components

### 1. Shared Thymeleaf Fragment (`assessment-reports.html`)

**Purpose**: Provides the HTML structure for the assessment reports page

**Available Fragments**:
- `reports-layout` - Main two-column layout with sidebar and content
- `import-modal` - Modal for importing scores

**Usage in Templates**:
```html
<div th:replace="~{fragments/assessment-reports :: reports-layout(
    reportContext=${contextObject},
    showImportBtn=true,
    isAdminView=false
)}"></div>

<div th:replace="~{fragments/assessment-reports :: import-modal}"></div>
```

**Parameters**:
- `reportContext` - Object containing current session, term, classes, students
- `showImportBtn` - Boolean to show/hide import button (staff: true, admin: false)
- `isAdminView` - Boolean to customize UI for admin vs staff (default: false)

### 2. Shared JavaScript Module (`assessment-reports.js`)

**Purpose**: Contains all common functionality used by both templates

**Core Functions**:

#### Data Rendering
- `renderAssessmentTable()` - Renders assessment scores with dynamic columns
- `renderBehavioralAssessment()` - Renders 10 behavioral trait rating cards
- `updateStudentCard()` - Updates student information card
- `updateAttendanceDisplay()` - Shows attendance metrics
- `updateCommentsDisplay()` - Loads teacher/head teacher comments

#### Edit Mode
- `toggleEditMode()` - Toggle between read-only and editable modes
- `updateEditModeUI()` - Sync UI state with edit mode flag

#### Score Management
- `updateRowTotal(input)` - Recalculate totals when score changes
- `validateScoreInput(input)` - Ensure score is within valid range
- `getGrade(total)` - Convert total score to letter grade

#### Behavioral Assessment
- `selectBehavior(btn, key, value)` - Set behavioral trait rating (1-5)

#### Data Loading
- `loadStudentData(studentId, apiEndpoint)` - Fetch and load student assessment data

#### Utilities
- `openImportModal()` - Show import modal
- `closeModal(id)` - Close modal by ID
- `clearAllEntries()` - Reset all form fields

### 3. Template-Specific Files

#### staff/class-reports.html
- **Role**: Staff teacher's assessment entry interface
- **Unique Features**:
  - Session/Term/Class/Student cascading filters
  - Auto-selections for current session/term
  - Class filtering by teacher (from `teacherClasses`)
  - Student list filtered by selected class
  - API endpoints: `/staff/reports/*`
  
- **Unique Functions** (already implemented):
  - `handleSessionChange()` - Clear dependent filters when session changes
  - `handleTermChange()` - Clear dependent filters when term changes
  - `handleClassChange()` - Load students for selected class
  - `handleStudentChange()` - Load student assessment data
  - `loadClassesForTerm()` - Fetch classes for term
  - `saveAssessment()` (Staff-specific) - Save to `/staff/reports/save`

#### admin/assessments/reports.html
- **Role**: Admin user's assessment reporting interface
- **Unique Features**:
  - Track/Department/Class/Student hierarchical filters
  - Filter chip UI for selection flow
  - Download report functionality (CSV/PDF)
  - Bulk import for multiple students
  - API endpoints: `/admin/assessments/*`

- **Unique Functions** (already implemented):
  - `selectTrack()` - Set track and load classes
  - `selectClass()` - Set class and load students
  - `selectStudent()` - Load student data
  - `downloadReport()` - Generate and download reports
  - `executeImport()` - Bulk import scores with formulas
  - `saveAssessment()` (Admin-specific) - Save to `/admin/assessments/save`

## Global State

All templates share these global variables:

```javascript
let currentStudentData = null;    // Current student's assessment data
let isEditMode = false;            // Edit mode toggle state
```

## Behavioral Traits Model

All templates support assessment of 10 behavioral traits:

```javascript
const traits = [
    { key: 'fluency', label: 'Fluency' },
    { key: 'handwriting', label: 'Handwriting' },
    { key: 'game', label: 'Game/Sports' },
    { key: 'initiative', label: 'Initiative' },
    { key: 'criticalThinking', label: 'Critical Thinking' },
    { key: 'punctuality', label: 'Punctuality' },
    { key: 'attentiveness', label: 'Attentiveness' },
    { key: 'neatness', label: 'Neatness' },
    { key: 'selfDiscipline', label: 'Self Discipline' },
    { key: 'politeness', label: 'Politeness' }
];
```

Each trait is rated on a 1-5 scale.

## Expected Data Structure

When `loadStudentData(studentId, apiEndpoint)` is called, the API should return:

```json
{
  "studentName": "John Doe",
  "studentId": "STU-001",
  "admissionNumber": "ADM-2024-001",
  "className": "Form 3A",
  "attendance": 85,
  "daysPresent": 180,
  "daysAbsent": 10,
  "classTeacherComment": "...",
  "headTeacherComment": "...",
  "fluency": 4,
  "handwriting": 3,
  "game": 5,
  "initiative": 4,
  "criticalThinking": 3,
  "punctuality": 5,
  "attentiveness": 4,
  "neatness": 3,
  "selfDiscipline": 4,
  "politeness": 5,
  "subjects": [
    {
      "id": "SUB-001",
      "name": "Mathematics",
      "ca1": 15,
      "ca2": 18,
      "exam": 45
    },
    ...
  ]
}
```

## CSS Classes

The shared styling is defined in:
- `common.css` - Global styles
- `dashboard.css` - Dashboard layout
- `school-setup.css` - Setup page styles
- `assessments.css` - Assessment-specific styles

Key CSS classes used:
- `.reports-main-layout` - Two-column layout container
- `.reports-sidebar` - Left sidebar for filters
- `.reports-content` - Right content area
- `.assessment-table` - Score entry table
- `.behavioral-trait-card` - Single trait rating card
- `.student-card` - Student information card
- `.filter-section` - Filter group container

## Extending the System

### To Add a New Assessment Context

1. Create new template in appropriate folder
2. Include CSS links (common, dashboard, school-setup, assessments)
3. Import shared JavaScript: `<script th:src="@{/js/assessment-reports.js}"></script>`
4. Implement template-specific functions:
   - `saveAssessment()` - Send data to your API endpoint
   - Filter/selector change handlers
5. Call shared functions from your handlers:
   - `loadStudentData(studentId, apiEndpoint)`
   - `toggleEditMode()`
   - etc.

### To Override Shared Functions

Simply define the function in your template's JavaScript AFTER including the shared module:

```html
<script th:src="@{/js/assessment-reports.js}"></script>
<script>
  // Override with custom implementation
  async function saveAssessment() {
    // Custom save logic
  }
</script>
```

## Migration Notes

**Previous State**: Code was duplicated across both templates (~3500 lines each)

**Current State**: 
- Common HTML structure: Fragment (~200 lines)
- Common JavaScript: Shared module (~400 lines)
- Template-specific code: ~1500-1700 lines each

**Maintenance Benefits**:
- Bug fixes in shared functions apply to both templates
- New features can be added to module once
- UI consistency guaranteed
- Reduced file size overall

## Testing Checklist

When making changes to shared files, verify:

- [ ] Staff reports page loads and renders correctly
- [ ] Admin reports page loads and renders correctly
- [ ] Student selection filters work in both contexts
- [ ] Score entry and editing works
- [ ] Behavioral trait ratings save correctly
- [ ] Comments and attendance fields work
- [ ] Save functionality works for both endpoints
- [ ] Import modal displays correctly (only for staff)
- [ ] Edit mode toggle controls all fields correctly
- [ ] Tables show correct totals and grades

## Related Issues Resolved

- Eliminated ~1500+ lines of duplicated HTML
- Consolidated JavaScript logic for maintainability
- Unified styling across contexts
- Centralized behavioral trait definitions
- Single source of truth for assessment logic
