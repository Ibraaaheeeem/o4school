# UI Polish Commit Sequence

Generated from current modified templates under webapp/src/main/resources/templates.

## Commit 1 - Shared foundation and auth surfaces (25 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/auth/activate-account-otp.html \
  webapp/src/main/resources/templates/auth/activate-account.html \
  webapp/src/main/resources/templates/auth/forgot-password.html \
  webapp/src/main/resources/templates/auth/fragments/auth-steps.html \
  webapp/src/main/resources/templates/auth/login.html \
  webapp/src/main/resources/templates/auth/register.html \
  webapp/src/main/resources/templates/auth/select-role.html \
  webapp/src/main/resources/templates/auth/select-school.html \
  webapp/src/main/resources/templates/fragments/academic-sidebar.html \
  webapp/src/main/resources/templates/fragments/activity-widget.html \
  webapp/src/main/resources/templates/fragments/admin-sidebar.html \
  webapp/src/main/resources/templates/fragments/assessment-sidebar.html \
  webapp/src/main/resources/templates/fragments/common.html \
  webapp/src/main/resources/templates/fragments/community-sidebar.html \
  webapp/src/main/resources/templates/fragments/csrf.html \
  webapp/src/main/resources/templates/fragments/empty.html \
  webapp/src/main/resources/templates/fragments/error-toast.html \
  webapp/src/main/resources/templates/fragments/error.html \
  webapp/src/main/resources/templates/fragments/financial-sidebar.html \
  webapp/src/main/resources/templates/fragments/header-scripts.html \
  webapp/src/main/resources/templates/fragments/header.html \
  webapp/src/main/resources/templates/fragments/messaging-sidebar.html \
  webapp/src/main/resources/templates/fragments/navigation.html \
  webapp/src/main/resources/templates/fragments/sidebar.html \
  webapp/src/main/resources/templates/fragments/success.html
```

Suggested commit message:

```text
UI polish: shared foundation and auth surfaces
```

## Commit 2 - Role dashboards and user-facing role pages (31 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/dashboard/admin-dashboard.html \
  webapp/src/main/resources/templates/dashboard/home.html \
  webapp/src/main/resources/templates/dashboard/internal-messaging-fragments.html \
  webapp/src/main/resources/templates/dashboard/internal-messaging.html \
  webapp/src/main/resources/templates/dashboard/messaging-fragments.html \
  webapp/src/main/resources/templates/dashboard/messaging.html \
  webapp/src/main/resources/templates/dashboard/multimodal-messaging.html \
  webapp/src/main/resources/templates/dashboard/parent-dashboard.html \
  webapp/src/main/resources/templates/dashboard/sms-fragments.html \
  webapp/src/main/resources/templates/dashboard/sms-messaging.html \
  webapp/src/main/resources/templates/dashboard/staff-dashboard.html \
  webapp/src/main/resources/templates/dashboard/student-dashboard.html \
  webapp/src/main/resources/templates/dashboard/system-admin-dashboard.html \
  webapp/src/main/resources/templates/staff/attendance-history.html \
  webapp/src/main/resources/templates/staff/attendance-report.html \
  webapp/src/main/resources/templates/staff/class-assessments.html \
  webapp/src/main/resources/templates/staff/class-details.html \
  webapp/src/main/resources/templates/staff/class-questions.html \
  webapp/src/main/resources/templates/staff/class-reports.html \
  webapp/src/main/resources/templates/staff/classes.html \
  webapp/src/main/resources/templates/staff/examination-questions-full.html \
  webapp/src/main/resources/templates/staff/examination-questions.html \
  webapp/src/main/resources/templates/staff/student-profile.html \
  webapp/src/main/resources/templates/staff/take-attendance.html \
  webapp/src/main/resources/templates/student/lesson-view.html \
  webapp/src/main/resources/templates/student/profile-view.html \
  webapp/src/main/resources/templates/student/report-card.html \
  webapp/src/main/resources/templates/student/take-examination.html \
  webapp/src/main/resources/templates/system-admin/financial/reimbursements-list.html \
  webapp/src/main/resources/templates/system-admin/financial/school-reimbursements.html \
  webapp/src/main/resources/templates/system-admin/users/list.html
```

Suggested commit message:

```text
UI polish: role dashboards and user-facing role pages
```

## Commit 3 - Admin community module (38 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/admin/community/approvals.html \
  webapp/src/main/resources/templates/admin/community/bulk-import-preview.html \
  webapp/src/main/resources/templates/admin/community/bulk-import-result.html \
  webapp/src/main/resources/templates/admin/community/home.html \
  webapp/src/main/resources/templates/admin/community/overviews.html \
  webapp/src/main/resources/templates/admin/community/parents/assign-students-modal.html \
  webapp/src/main/resources/templates/admin/community/parents/assign-students.html \
  webapp/src/main/resources/templates/admin/community/parents/assign-success.html \
  webapp/src/main/resources/templates/admin/community/parents/create-success.html \
  webapp/src/main/resources/templates/admin/community/parents/form.html \
  webapp/src/main/resources/templates/admin/community/parents/home-modal-form.html \
  webapp/src/main/resources/templates/admin/community/parents/list.html \
  webapp/src/main/resources/templates/admin/community/parents/modal-form.html \
  webapp/src/main/resources/templates/admin/community/parents/parent-cards.html \
  webapp/src/main/resources/templates/admin/community/parents/parent-table.html \
  webapp/src/main/resources/templates/admin/community/staff/assign-class.html \
  webapp/src/main/resources/templates/admin/community/staff/assign-subjects.html \
  webapp/src/main/resources/templates/admin/community/staff/assign-success.html \
  webapp/src/main/resources/templates/admin/community/staff/assignment-response.html \
  webapp/src/main/resources/templates/admin/community/staff/assignments-modal.html \
  webapp/src/main/resources/templates/admin/community/staff/create-success.html \
  webapp/src/main/resources/templates/admin/community/staff/form.html \
  webapp/src/main/resources/templates/admin/community/staff/home-modal-form.html \
  webapp/src/main/resources/templates/admin/community/staff/list.html \
  webapp/src/main/resources/templates/admin/community/staff/modal-form.html \
  webapp/src/main/resources/templates/admin/community/staff/staff-card-single.html \
  webapp/src/main/resources/templates/admin/community/staff/staff-cards.html \
  webapp/src/main/resources/templates/admin/community/staff/staff-table.html \
  webapp/src/main/resources/templates/admin/community/students/assign-class-modal.html \
  webapp/src/main/resources/templates/admin/community/students/assign-class.html \
  webapp/src/main/resources/templates/admin/community/students/assign-success.html \
  webapp/src/main/resources/templates/admin/community/students/create-success.html \
  webapp/src/main/resources/templates/admin/community/students/form.html \
  webapp/src/main/resources/templates/admin/community/students/home-modal-form.html \
  webapp/src/main/resources/templates/admin/community/students/list.html \
  webapp/src/main/resources/templates/admin/community/students/modal-form.html \
  webapp/src/main/resources/templates/admin/community/students/student-cards.html \
  webapp/src/main/resources/templates/admin/community/students/student-table.html
```

Suggested commit message:

```text
UI polish: admin community module
```

## Commit 4 - Admin financial module (19 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/admin/financial/class-assignment-modal-basic.html \
  webapp/src/main/resources/templates/admin/financial/class-assignment-modal-fixed.html \
  webapp/src/main/resources/templates/admin/financial/class-assignment-modal-simple.html \
  webapp/src/main/resources/templates/admin/financial/class-assignment-modal.html \
  webapp/src/main/resources/templates/admin/financial/fee-item-modal.html \
  webapp/src/main/resources/templates/admin/financial/fee-items.html \
  webapp/src/main/resources/templates/admin/financial/fragments/class-assignment-response.html \
  webapp/src/main/resources/templates/admin/financial/fragments/fee-item-save-success.html \
  webapp/src/main/resources/templates/admin/financial/fragments/opted-students-list.html \
  webapp/src/main/resources/templates/admin/financial/fragments/payment-breakdown-modal.html \
  webapp/src/main/resources/templates/admin/financial/home-simple.html \
  webapp/src/main/resources/templates/admin/financial/home.html \
  webapp/src/main/resources/templates/admin/financial/manual-settlement-modal.html \
  webapp/src/main/resources/templates/admin/financial/optional-fees-simple.html \
  webapp/src/main/resources/templates/admin/financial/optional-fees.html \
  webapp/src/main/resources/templates/admin/financial/payment-analytics.html \
  webapp/src/main/resources/templates/admin/financial/payment-details-modal.html \
  webapp/src/main/resources/templates/admin/financial/payments-table-actions.html \
  webapp/src/main/resources/templates/admin/financial/payments.html
```

Suggested commit message:

```text
UI polish: admin financial module
```

## Commit 5 - Admin assessments module (8 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/admin/assessments/examination-modal-new.html \
  webapp/src/main/resources/templates/admin/assessments/examination-modal.html \
  webapp/src/main/resources/templates/admin/assessments/examinations.html \
  webapp/src/main/resources/templates/admin/assessments/home.html \
  webapp/src/main/resources/templates/admin/assessments/questions.html \
  webapp/src/main/resources/templates/admin/assessments/reports.html \
  webapp/src/main/resources/templates/admin/assessments/scoring-schemes.html \
  webapp/src/main/resources/templates/admin/assessments/submissions-modal.html
```

Suggested commit message:

```text
UI polish: admin assessments module
```

## Commit 6 - Admin setup, content, activities, academic (29 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/admin/academic/calendar-modal.html \
  webapp/src/main/resources/templates/admin/academic/calendar.html \
  webapp/src/main/resources/templates/admin/academic/fragments/promotion-student-list.html \
  webapp/src/main/resources/templates/admin/academic/fragments/promotion-success.html \
  webapp/src/main/resources/templates/admin/academic/fragments/term-promotion-list.html \
  webapp/src/main/resources/templates/admin/academic/home.html \
  webapp/src/main/resources/templates/admin/academic/htmx-responses.html \
  webapp/src/main/resources/templates/admin/academic/promotion.html \
  webapp/src/main/resources/templates/admin/academic/session-modal.html \
  webapp/src/main/resources/templates/admin/academic/sessions.html \
  webapp/src/main/resources/templates/admin/academic/term-modal.html \
  webapp/src/main/resources/templates/admin/academic/term-success.html \
  webapp/src/main/resources/templates/admin/academic/terms.html \
  webapp/src/main/resources/templates/admin/academic/timetable-modal.html \
  webapp/src/main/resources/templates/admin/academic/timetable.html \
  webapp/src/main/resources/templates/admin/activities/list.html \
  webapp/src/main/resources/templates/admin/school-content/edit.html \
  webapp/src/main/resources/templates/admin/school-content/manage.html \
  webapp/src/main/resources/templates/admin/school-setup/academic-structure.html \
  webapp/src/main/resources/templates/admin/school-setup/classes.html \
  webapp/src/main/resources/templates/admin/school-setup/custom-domain.html \
  webapp/src/main/resources/templates/admin/school-setup/departments.html \
  webapp/src/main/resources/templates/admin/school-setup/education-tracks.html \
  webapp/src/main/resources/templates/admin/school-setup/fragments/structure-tree.html \
  webapp/src/main/resources/templates/admin/school-setup/home.html \
  webapp/src/main/resources/templates/admin/school-setup/landing-page.html \
  webapp/src/main/resources/templates/admin/school-setup/school-details.html \
  webapp/src/main/resources/templates/admin/school-setup/subjects.html \
  webapp/src/main/resources/templates/admin/school-setup/subscriptions.html
```

Suggested commit message:

```text
UI polish: admin setup, content, activities, academic
```

## Commit 7 - Public and platform-facing templates (17 files)

Stage command:

```bash
git add \
  webapp/src/main/resources/templates/elearner/content.html \
  webapp/src/main/resources/templates/elearner/landing.html \
  webapp/src/main/resources/templates/elearner/lesson.html \
  webapp/src/main/resources/templates/error.html \
  webapp/src/main/resources/templates/error/simple-error.html \
  webapp/src/main/resources/templates/public/404.html \
  webapp/src/main/resources/templates/public/defaults/about-content.html \
  webapp/src/main/resources/templates/public/defaults/additional-sections.html \
  webapp/src/main/resources/templates/public/defaults/contact-content.html \
  webapp/src/main/resources/templates/public/defaults/features-content.html \
  webapp/src/main/resources/templates/public/defaults/hero-content.html \
  webapp/src/main/resources/templates/public/platform-home.html \
  webapp/src/main/resources/templates/public/privacy.html \
  webapp/src/main/resources/templates/public/school-landing-template.html \
  webapp/src/main/resources/templates/public/school-landing.html \
  webapp/src/main/resources/templates/school/02ba1e88-cb88-4e10-9d3f-81eb62912e1d/contents/hero-content.html \
  webapp/src/main/resources/templates/school/demo-school-id/contents/hero-content.html
```

Suggested commit message:

```text
UI polish: public and platform-facing templates
```
