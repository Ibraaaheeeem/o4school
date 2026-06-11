# Comprehensive Kotlin Entity Database Schema Mapping

This document contains a complete mapping of all database tables and their relationships extracted from the Kotlin JPA entities.

---

## BASE ENTITIES (Superclasses)

### BaseEntity (MappedSuperclass)
- **id**: UUID (nullable: no, primary key, auto-generated)
- **createdAt**: LocalDateTime (nullable: no, auto-generated, immutable)
- **updatedAt**: LocalDateTime (nullable: no, auto-generated)
- **isActive**: Boolean (nullable: no, default: true)

### GlobalEntity (MappedSuperclass, extends BaseEntity)
- Inherits all fields from BaseEntity
- No additional fields

### TenantAwareEntity (MappedSuperclass, extends BaseEntity)
- Inherits all fields from BaseEntity
- **schoolId**: UUID (nullable: no, indexed)

---

## TABLES

### ACADEMIC_SESSIONS
**Table Name**: academic_sessions
**Parent**: TenantAwareEntity

**Fields:**
- sessionName: String (nullable: no)
- sessionYear: String (nullable: no, unique with school_id)
- startDate: LocalDate (nullable: no)
- endDate: LocalDate (nullable: yes)
- isCurrentSession: Boolean (nullable: yes, default: false)
- status: String (nullable: yes, default: "active") - values: active, completed, planned
- notes: String (nullable: yes)

**Relationships:**
- terms: OneToMany -> Term (cascade: ALL, lazy)
- calendarEvents: OneToMany -> SchoolCalendar (cascade: ALL, lazy)

**Unique Constraints:**
- school_id + session_year

**Indexes:**
- school_id
- session_year
- is_current_session

---

### ACTIVITY_LOGS
**Table Name**: activity_logs
**Parent**: TenantAwareEntity

**Fields:**
- activityType: ActivityType (nullable: no, ENUM) - USER_CREATED, USER_UPDATED, USER_DELETED, USER_LOGIN, USER_LOGOUT, PASSWORD_CHANGED, STUDENT_ENROLLED, STUDENT_UPDATED, STUDENT_TRANSFERRED, STUDENT_GRADUATED, STUDENT_SUSPENDED, STAFF_HIRED, STAFF_UPDATED, STAFF_TERMINATED, STAFF_PROMOTED, PARENT_ADDED, PARENT_UPDATED, PARENT_REMOVED, CLASS_CREATED, CLASS_UPDATED, SUBJECT_CREATED, SUBJECT_UPDATED, ASSIGNMENT_CREATED, ASSIGNMENT_SUBMITTED, GRADE_ENTERED, EXAM_CREATED, EXAM_SCHEDULED, ATTENDANCE, FEE_CREATED, PAYMENT_RECEIVED
- title: String (nullable: no)
- description: TEXT (nullable: yes)
- userId: UUID (nullable: no, indexed) - who performed action
- userName: String (nullable: no)
- userRole: String (nullable: no)
- targetUserId: UUID (nullable: yes, indexed) - who was affected
- targetUserName: String (nullable: yes)
- entityType: String (nullable: yes) - Student, Staff, Parent, Class, etc.
- entityId: UUID (nullable: yes, indexed)
- metadata: TEXT (nullable: yes) - JSON string
- ipAddress: String (nullable: yes)
- userAgent: TEXT (nullable: yes)

**Relationships:**
- None directly, but references User via userId (not foreign key, cached)

**Indexes:**
- school_id + created_at
- user_id + created_at
- activity_type + created_at
- target_user_id + created_at

---

### ASSESSMENTS
**Table Name**: assessments
**Parent**: TenantAwareEntity

**Fields:**
- admissionNumber: String (nullable: no)
- attendance: Int (nullable: no, default: 0)
- fluency: Int (nullable: no, default: 0)
- handwriting: Int (nullable: no, default: 0)
- game: Int (nullable: no, default: 0)
- initiative: Int (nullable: no, default: 0)
- criticalThinking: Int (nullable: no, default: 0)
- punctuality: Int (nullable: no, default: 0)
- attentiveness: Int (nullable: no, default: 0)
- neatness: Int (nullable: no, default: 0)
- selfDiscipline: Int (nullable: no, default: 0)
- politeness: Int (nullable: no, default: 0)
- classTeacherComment: String (nullable: yes)
- headTeacherComment: String (nullable: yes)

**Relationships:**
- student: ManyToOne -> Student (lazy)
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- term: ManyToOne -> Term (lazy, not nullable)
- scores: OneToMany -> SubjectScore (cascade: ALL, lazy)

**Unique Constraints:**
- school_id + admission_number + academic_session_id + term_id

**Indexes:**
- school_id + academic_session_id + term_id
- admission_number

---

### ATTENDANCE
**Table Name**: attendance
**Parent**: TenantAwareEntity

**Fields:**
- attendanceDate: LocalDate (nullable: no)
- status: AttendanceStatus (nullable: no, ENUM) - PRESENT, ABSENT, LATE, EXCUSED
- arrivalTime: LocalDateTime (nullable: yes)
- departureTime: LocalDateTime (nullable: yes)
- notes: String (nullable: yes)

**Relationships:**
- student: ManyToOne -> Student (lazy, not nullable)
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- staff: ManyToOne -> Staff (lazy, not nullable)

**Backward Compatibility Properties:**
- teacherId: UUID (computed from staff.id)
- teacher: Staff (computed from staff)

**Unique Constraints:**
- student_id + class_id + attendance_date + school_id

**Indexes:**
- school_id + attendance_date
- class_id + attendance_date

---

### CLASS_FEE_ITEMS
**Table Name**: class_fee_items
**Parent**: TenantAwareEntity

**Fields:**
- academicYear: String (nullable: no)
- customAmount: BigDecimal (nullable: yes, precision: 10, scale: 2)
- isApplicable: Boolean (nullable: yes, default: true)
- notes: String (nullable: yes)

**Relationships:**
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- feeItem: ManyToOne -> FeeItem (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- termId: ManyToOne -> Term (lazy, nullable)

**Computed Property:**
- effectiveAmount: BigDecimal (customAmount if set, otherwise feeItem.amount)

**Unique Constraints:**
- class_id + fee_item_id + academic_session_id + term_id

**Indexes:**
- school_id + class_id
- fee_item_id

---

### CLASS_SUBJECTS
**Table Name**: class_subjects
**Parent**: TenantAwareEntity

**Fields:**
- assignedBy: UUID (nullable: yes)
- assignedAt: LocalDateTime (nullable: no, default: now)

**Relationships:**
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- subject: ManyToOne -> Subject (lazy, not nullable)
- staff: ManyToOne -> Staff (lazy, nullable)

**Backward Compatibility Properties:**
- teacherId: UUID (computed from staff?.id)
- teacher: Staff (computed from staff)

**Unique Constraints:**
- class_id + subject_id + school_id

**Indexes:**
- school_id + class_id + subject_id
- staff_id + school_id

---

### CLASS_TEACHERS
**Table Name**: class_teachers
**Parent**: TenantAwareEntity

**Fields:**
- None beyond parent class

**Relationships:**
- staff: ManyToOne -> Staff (lazy, not nullable)
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- term: ManyToOne -> Term (lazy, not nullable)

**Unique Constraints:**
- staff_id + class_id + academic_session_id + term_id + school_id

**Indexes:**
- school_id + staff_id + academic_session_id + term_id
- class_id + academic_session_id + term_id
- academic_session_id + term_id

---

### CLASSES
**Table Name**: classes
**Parent**: TenantAwareEntity

**Fields:**
- className: String (nullable: no)
- classCode: String (nullable: yes)
- gradeLevel: Int (nullable: yes)
- term: String (nullable: yes)
- maxCapacity: Int (nullable: no, default: 30)
- currentEnrollment: Int (nullable: no, default: 0)
- classroomLocation: String (nullable: yes)
- classStaffId: UUID (nullable: yes)
- scoringScheme: TEXT (nullable: yes)

**Relationships:**
- department: ManyToOne -> Department (lazy, nullable)
- track: ManyToOne -> EducationTrack (lazy, nullable)
- subjectAssignments: OneToMany -> ClassSubject (cascade: ALL, lazy)
- studentEnrollments: OneToMany -> StudentClass (cascade: ALL, lazy)
- attendanceRecords: OneToMany -> Attendance (cascade: ALL, lazy)
- exams: OneToMany -> Exam (cascade: ALL, lazy)

**Unique Constraints:**
- class_name + school_id

**Indexes:**
- school_id + grade_level
- school_id + department_id

**Nested Enum:**
- GradeLevel (KINDERGARTEN to SSS_3 with values -3 to 12)

---

### DEPARTMENTS
**Table Name**: departments
**Parent**: TenantAwareEntity

**Fields:**
- name: String (nullable: no)
- description: String (nullable: yes)

**Relationships:**
- track: ManyToOne -> EducationTrack (lazy, nullable)
- classes: OneToMany -> SchoolClass (cascade: ALL, lazy)

**Unique Constraints:**
- name + school_id + track_id

**Indexes:**
- school_id + track_id

---

### DESIGNATION_PERMISSIONS
**Table Name**: designation_permissions
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- canRead: Boolean (nullable: no, default: false)
- canWrite: Boolean (nullable: no, default: false)
- canDelete: Boolean (nullable: no, default: false)
- canApprove: Boolean (nullable: no, default: false)

**Relationships:**
- designation: ManyToOne -> Designation (lazy, not nullable)
- permission: ManyToOne -> Permission (lazy, not nullable)

---

### DESIGNATIONS
**Table Name**: designations
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- name: String (nullable: no)
- description: String (nullable: yes)

**Relationships:**
- school: ManyToOne -> School (lazy, not nullable)
- permissions: OneToMany -> DesignationPermission (cascade: ALL, lazy)

---

### EDUCATION_TRACKS
**Table Name**: education_tracks
**Parent**: TenantAwareEntity

**Fields:**
- name: String (nullable: no, unique with school_id)
- description: String (nullable: yes)

**Relationships:**
- departments: OneToMany -> Department (cascade: ALL, lazy)
- classes: OneToMany -> SchoolClass (cascade: ALL, lazy)

**Unique Constraints:**
- name + school_id

**Indexes:**
- school_id

---

### EXAMINATIONS
**Table Name**: examinations
**Parent**: TenantAwareEntity

**Fields:**
- title: String (nullable: no) - e.g., "First Term CA 1 2024/2025"
- examType: String (nullable: no) - CA 1, CA 2, Final Examination
- isOnline: Boolean (nullable: no, default: false)
- createdBy: UUID (nullable: no)
- isPublished: Boolean (nullable: yes, default: false)
- startTime: LocalDateTime (nullable: yes)
- endTime: LocalDateTime (nullable: yes)
- durationMinutes: Int (nullable: yes, default: 60)
- totalMarks: Int (nullable: yes)
- questionCount: Int (formula-based) - COUNT of questions
- submissionCount: Int (formula-based) - COUNT of submissions

**Relationships:**
- subject: ManyToOne -> Subject (lazy, not nullable)
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- term: ManyToOne -> Term (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- questions: OneToMany -> Question (cascade: ALL, lazy, ordered by createdAt)
- submissions: OneToMany -> ExaminationSubmission (cascade: ALL, lazy)

**Indexes:**
- school_id + class_id + subject_id + term_id + session_id

---

### EXAMINATION_SUBMISSIONS
**Table Name**: examination_submissions
**Parent**: TenantAwareEntity

**Fields:**
- status: String (nullable: no, default: "in_progress") - in_progress, submitted, graded
- score: Double (nullable: yes)
- attemptCount: Int (nullable: yes, default: 1)
- startedAt: LocalDateTime (nullable: yes)
- submittedAt: LocalDateTime (nullable: yes)
- answersJson: TEXT (nullable: yes)

**Relationships:**
- examination: ManyToOne -> Examination (lazy, not nullable)
- student: ManyToOne -> Student (lazy, not nullable)

**Indexes:**
- examination_id + student_id

---

### EXAMS
**Table Name**: exams
**Parent**: TenantAwareEntity

**Fields:**
- examName: String (nullable: no)
- subject: String (nullable: no)
- examDate: LocalDate (nullable: no)
- durationMinutes: Int (nullable: yes)
- totalMarks: Int (nullable: no)
- passingMarks: Int (nullable: yes)
- examType: String (nullable: no) - quiz, test, midterm, final, assignment

**Relationships:**
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- staff: ManyToOne -> Staff (lazy, not nullable)
- term: ManyToOne -> Term (lazy, nullable)
- results: OneToMany -> ExamResult (cascade: ALL, lazy)

**Backward Compatibility Properties:**
- teacherId: UUID (computed from staff.id)
- teacher: Staff (computed from staff)

**Indexes:**
- school_id + class_id + exam_date
- school_id + subject

---

### EXAM_RESULTS
**Table Name**: exam_results
**Parent**: TenantAwareEntity

**Fields:**
- marksObtained: Int (nullable: no)
- grade: String (nullable: yes) - A+, A, B+, B, C, D, F
- percentage: Int (nullable: yes)
- position: Int (nullable: yes) - position in class
- gradedAt: LocalDateTime (nullable: no, default: now)
- remarks: String (nullable: yes)

**Relationships:**
- exam: ManyToOne -> Exam (lazy, not nullable)
- student: ManyToOne -> Student (lazy, not nullable)
- gradedByStaff: ManyToOne -> Staff (lazy, not nullable)

**Backward Compatibility Properties:**
- gradedByTeacher: Staff (computed from gradedByStaff)

**Unique Constraints:**
- exam_id + student_id + school_id

**Indexes:**
- school_id + exam_id
- student_id + school_id

---

### FEE_ITEMS
**Table Name**: fee_items
**Parent**: TenantAwareEntity

**Fields:**
- name: String (nullable: no)
- amount: BigDecimal (nullable: no, precision: 10, scale: 2)
- description: String (nullable: yes)
- isMandatory: Boolean (nullable: yes, default: true)
- genderEligibility: GenderEligibility (nullable: no, ENUM) - ALL, MALE, FEMALE
- studentStatusEligibility: StudentStatusEligibility (nullable: no, ENUM) - ALL, NEW, RETURNING
- staffDiscountType: DiscountType (nullable: no, ENUM) - NONE, PERCENTAGE, FLAT_AMOUNT
- staffDiscountAmount: BigDecimal (nullable: no, precision: 10, scale: 2, default: 0)

**Relationships:**
- classFeeItems: OneToMany -> ClassFeeItem (cascade: ALL, lazy)

**Unique Constraints:**
- name + school_id

**Indexes:**
- school_id + is_active

**Enums Used:**
- GenderEligibility: ALL, MALE, FEMALE
- StudentStatusEligibility: ALL, NEW, RETURNING
- DiscountType: NONE, PERCENTAGE, FLAT_AMOUNT

---

### FEE_STRUCTURES
**Table Name**: fee_structures
**Parent**: TenantAwareEntity

**Fields:**
- feeName: String (nullable: no)
- feeCategory: String (nullable: no) - tuition, transport, meal, uniform, etc.
- amount: Int (nullable: no) - in kobo/cents
- term: String (nullable: yes) - null means applies to all terms
- gradeLevel: String (nullable: yes) - null means applies to all grades
- dueDate: String (nullable: yes) - MM-DD format
- description: String (nullable: yes)
- isMandatory: Boolean (nullable: yes, default: true)

**Relationships:**
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- invoiceItems: OneToMany -> InvoiceItem (cascade: ALL, lazy)

**Unique Constraints:**
- fee_name + school_id + academic_session_id

**Indexes:**
- school_id + academic_session_id
- school_id + fee_category

---

### GLOBAL_SUBJECTS
**Table Name**: global_subjects
**Parent**: BaseEntity

**Fields:**
- name: String (nullable: no)
- code: String (nullable: yes)
- minGradeLevel: Int (nullable: no, default: 1)
- maxGradeLevel: Int (nullable: no, default: 12)
- category: String (nullable: yes)
- isCore: Boolean (nullable: no, default: false)

**Relationships:**
- None

**Unique Constraints:**
- name
- code

---

### INTERNAL_MESSAGE_PARTICIPANTS
**Table Name**: internal_message_participants
**Parent**: TenantAwareEntity

**Fields:**
- unreadCount: Int (nullable: no, default: 0)
- lastReadAt: LocalDateTime (nullable: yes)

**Relationships:**
- thread: ManyToOne -> InternalMessageThread (lazy, not nullable)
- user: ManyToOne -> User (lazy, not nullable)

**Unique Constraints:**
- thread_id + user_id

**Indexes:**
- user_id + unread_count

---

### INTERNAL_MESSAGE_THREADS
**Table Name**: internal_message_threads
**Parent**: TenantAwareEntity

**Fields:**
- subject: String (nullable: no)
- lastMessagePreview: String (nullable: yes)

**Relationships:**
- participants: OneToMany -> InternalMessageParticipant (cascade: ALL, lazy)
- messages: OneToMany -> InternalMessage (cascade: ALL, lazy)

**Indexes:**
- school_id + created_at

---

### INTERNAL_MESSAGES
**Table Name**: internal_messages
**Parent**: TenantAwareEntity

**Fields:**
- content: TEXT (nullable: no)

**Relationships:**
- thread: ManyToOne -> InternalMessageThread (lazy, not nullable)
- sender: ManyToOne -> User (lazy, not nullable)

**Indexes:**
- thread_id + created_at

---

### INVOICES
**Table Name**: invoices
**Parent**: TenantAwareEntity

**Fields:**
- invoiceNumber: String (nullable: no)
- totalAmount: Int (nullable: no) - in kobo/cents
- amountPaid: Int (nullable: yes, default: 0)
- balanceDue: Int (nullable: yes, default: 0)
- issueDate: LocalDate (nullable: no)
- dueDate: LocalDate (nullable: no)
- status: InvoiceStatus (nullable: no, ENUM) - DRAFT, SENT, PAID, OVERDUE, CANCELLED
- term: String (nullable: yes)
- notes: String (nullable: yes)

**Relationships:**
- student: ManyToOne -> Student (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- items: OneToMany -> InvoiceItem (cascade: ALL, lazy)
- paymentNotifications: OneToMany -> PaymentNotification (cascade: ALL, lazy)

**Unique Constraints:**
- invoice_number + school_id

**Indexes:**
- school_id + student_id + status
- school_id + due_date + status
- school_id + academic_session_id + term

**Enums Used:**
- InvoiceStatus: DRAFT, SENT, PAID, OVERDUE, CANCELLED

---

### INVOICE_ITEMS
**Table Name**: invoice_items
**Parent**: TenantAwareEntity

**Fields:**
- description: String (nullable: no)
- quantity: Int (nullable: no, default: 1)
- unitAmount: Int (nullable: no) - in kobo/cents
- totalAmount: Int (nullable: no) - in kobo/cents

**Relationships:**
- invoice: ManyToOne -> Invoice (lazy, not nullable)
- feeStructure: ManyToOne -> FeeStructure (lazy, nullable)

**Indexes:**
- school_id + invoice_id

---

### PARENT_STUDENT_RELATIONSHIPS
**Table Name**: parent_student_relationships
**Parent**: TenantAwareEntity

**Fields:**
- relationshipType: String (nullable: no) - biological, adoptive, guardian, etc.

**Relationships:**
- parent: ManyToOne -> Parent (lazy, not nullable)
- student: ManyToOne -> Student (lazy, not nullable)

**Unique Constraints:**
- parent_id + student_id + school_id

**Indexes:**
- school_id + parent_id + student_id

---

### PARENTS
**Table Name**: parents
**Parent**: TenantAwareEntity

**Fields:**
- isPrimaryContact: Boolean (nullable: yes, default: false)
- isEmergencyContact: Boolean (nullable: yes, default: true)
- isFinanciallyResponsible: Boolean (nullable: yes, default: true)
- receiveAcademicUpdates: Boolean (nullable: yes, default: true)
- receiveFinancialUpdates: Boolean (nullable: yes, default: true)
- receiveDisciplinaryUpdates: Boolean (nullable: yes, default: true)
- paymentDistributionType: String (nullable: yes, default: "SPREAD") - SPREAD or SEQUENTIAL
- paymentPriorityOrder: TEXT (nullable: yes) - comma-separated Student IDs
- totalBalance: BigDecimal (transient, computed)

**Relationships:**
- user: ManyToOne -> User (lazy, not nullable)
- studentRelationships: OneToMany -> ParentStudent (cascade: ALL, lazy)
- paymentNotifications: OneToMany -> PaymentNotification (cascade: ALL, lazy)
- paystackWallet: OneToOne -> PaystackParentWallet (cascade: ALL, lazy, nullable)
- squadWallet: OneToOne -> SquadParentWallet (cascade: ALL, lazy, nullable)

**Computed Property:**
- activeStudentRelationships: List<ParentStudent> (filtered where isActive)

**Unique Constraints:**
- user_id + school_id

**Indexes:**
- school_id + is_active

---

### PAYMENT_ALLOCATIONS
**Table Name**: payment_allocations
**Parent**: TenantAwareEntity

**Fields:**
- allocatedAmount: BigDecimal (nullable: no)
- allocationOrder: Int (nullable: no)
- allocationMethod: String (nullable: no, default: "SEQUENTIAL") - SEQUENTIAL, PROPORTIONAL, EQUAL
- remainingBalanceBefore: BigDecimal (nullable: no)
- remainingBalanceAfter: BigDecimal (nullable: no)
- allocationDate: LocalDateTime (nullable: yes, default: now)
- notes: String (nullable: yes)

**Relationships:**
- settlement: ManyToOne -> Settlement (lazy, not nullable)
- student: ManyToOne -> Student (lazy, not nullable)

**Indexes:**
- settlement_id
- student_id
- allocation_order

---

### PAYMENT_NOTIFICATIONS
**Table Name**: payment_notifications
**Parent**: TenantAwareEntity

**Fields:**
- amount: Int (nullable: no) - in kobo/cents
- paymentMethod: String (nullable: no)
- paymentReference: String (nullable: yes)
- proofOfPaymentUrl: String (nullable: yes)
- notes: String (nullable: yes)
- status: PaymentStatus (nullable: no, ENUM) - PENDING, APPROVED, REJECTED
- reviewedBy: UUID (nullable: yes)
- reviewedAt: LocalDateTime (nullable: yes)
- reviewNotes: String (nullable: yes)

**Relationships:**
- invoice: ManyToOne -> Invoice (lazy, not nullable)
- parent: ManyToOne -> Parent (lazy, not nullable)

**Indexes:**
- school_id + status + created_at
- school_id + parent_id

**Enums Used:**
- PaymentStatus: PENDING, APPROVED, REJECTED

---

### PAYSTACK_PARENT_WALLETS
**Table Name**: paystack_parent_wallets
**Parent**: TenantAwareEntity

**Fields:**
- customerCode: String (nullable: no)
- accountNumber: String (nullable: yes, unique)
- accountName: String (nullable: yes)
- bankName: String (nullable: yes)
- bankSlug: String (nullable: yes)
- bankId: Int (nullable: yes)
- balance: BigDecimal (nullable: no, default: 0)
- currency: String (nullable: no, default: "NGN")
- paystackAccountId: Long (nullable: yes)
- assignedAt: LocalDateTime (nullable: yes)

**Relationships:**
- parent: ManyToOne -> Parent (lazy, not nullable)

**Unique Constraints:**
- parent_id
- account_number

**Indexes:**
- parent_id
- account_number
- customer_code

**Enums Used in Computed Methods:**
- DebtStatus: CLEARED, LOW, MEDIUM, HIGH

---

### PERMISSIONS
**Table Name**: permissions
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- name: String (nullable: no, unique)
- description: String (nullable: yes)
- module: String (nullable: no) - STUDENT_MANAGEMENT, ACADEMIC, FINANCE, REPORTS, ADMINISTRATION, etc.

**Relationships:**
- None (but used in DesignationPermission)

**Enums Used:**
- SystemPermission (reference enum): VIEW_STUDENTS, CREATE_STUDENTS, EDIT_STUDENTS, DELETE_STUDENTS, VIEW_STAFF, CREATE_STAFF, EDIT_STAFF, DELETE_STAFF, VIEW_CLASSES, MANAGE_CLASSES, VIEW_SUBJECTS, MANAGE_SUBJECTS, VIEW_GRADES, MANAGE_GRADES, VIEW_FEES, MANAGE_FEES, VIEW_PAYMENTS, PROCESS_PAYMENTS, VIEW_REPORTS, GENERATE_REPORTS, MANAGE_SCHOOL_SETTINGS, MANAGE_USERS, MANAGE_PERMISSIONS, VIEW_AUDIT_LOGS

---

### QUESTIONS
**Table Name**: questions
**Parent**: TenantAwareEntity

**Fields:**
- instruction: TEXT (nullable: yes)
- explanation: TEXT (nullable: yes)
- questionText: TEXT (nullable: no)
- questionImageUrl: String (nullable: yes)
- optionA: TEXT (nullable: no)
- optionB: TEXT (nullable: no)
- optionC: TEXT (nullable: yes)
- optionD: TEXT (nullable: yes)
- optionE: TEXT (nullable: yes)
- correctAnswer: String (nullable: no) - A, B, C, D, E
- marks: Double (nullable: yes, default: 1.0)

**Relationships:**
- examination: ManyToOne -> Examination (lazy, not nullable)

**Indexes:**
- examination_id

---

### ROLES
**Table Name**: roles
**Parent**: BaseEntity

**Fields:**
- name: String (nullable: no)
- roleType: RoleType (nullable: no, ENUM) - ADMIN, STUDENT, PARENT, STAFF, SCHOOL_ADMIN
- description: String (nullable: yes)
- isSystemRole: Boolean (nullable: no, default: false)

**Relationships:**
- None (but used in UserGlobalRole and UserSchoolRole)

**Enums Used:**
- RoleType: ADMIN, STUDENT, PARENT, STAFF, SCHOOL_ADMIN

---

### SCHOOL_BANK_ACCOUNTS
**Table Name**: school_bank_accounts
**Parent**: BaseEntity

**Fields:**
- bankName: String (nullable: no)
- accountNumber: String (nullable: no)
- accountName: String (nullable: no)
- bankCode: String (nullable: yes)
- recipientCode: String (nullable: yes)

**Relationships:**
- school: ManyToOne -> School (lazy, not nullable)

---

### SCHOOL_CALENDAR
**Table Name**: school_calendar
**Parent**: TenantAwareEntity

**Fields:**
- eventName: String (nullable: no)
- eventType: CalendarEventType (nullable: no, ENUM) - TERM, HOLIDAY, EXAM, EVENT, RESUMPTION, VACATION
- startDate: LocalDate (nullable: no)
- endDate: LocalDate (nullable: yes)
- description: String (nullable: yes)
- isHoliday: Boolean (nullable: yes, default: false)
- isExamPeriod: Boolean (nullable: yes, default: false)
- color: String (nullable: yes, default: "#3b82f6")

**Relationships:**
- session: ManyToOne -> AcademicSession (lazy, not nullable)
- term: ManyToOne -> Term (lazy, nullable)

**Unique Constraints:**
- session_id + event_name + start_date

**Indexes:**
- school_id + session_id
- event_type + start_date
- start_date + end_date

**Enums Used:**
- CalendarEventType: TERM, HOLIDAY, EXAM, EVENT, RESUMPTION, VACATION

---

### SCHOOL_SUBSCRIPTIONS
**Table Name**: school_subscriptions
**Parent**: BaseEntity

**Fields:**
- feeCollectionActive: Boolean (nullable: no, default: false)
- whatsappBalance: Int (nullable: no, default: 0)
- smsBalance: Int (nullable: no, default: 0)
- aiTokenBalance: Int (nullable: no, default: 0)
- accountNumber: String (nullable: yes)
- bankName: String (nullable: yes)
- termsAccepted: Boolean (nullable: no, default: false)
- subscriptionStatus: SubscriptionStatus (nullable: no, ENUM) - ACTIVE, EXPIRED, GRACE_PERIOD
- validUntil: LocalDateTime (nullable: yes)
- lastUpdated: LocalDateTime (nullable: no, default: now)

**Relationships:**
- school: OneToOne -> School (lazy, not nullable)

**Unique Constraints:**
- school_id

**Enums Used:**
- SubscriptionStatus: ACTIVE, EXPIRED, GRACE_PERIOD

---

### SCHOOL_TIMETABLES
**Table Name**: school_timetable
**Parent**: TenantAwareEntity

**Fields:**
- dayOfWeek: DayOfWeek (nullable: no, ENUM) - MONDAY to SUNDAY
- startTime: LocalTime (nullable: no)
- endTime: LocalTime (nullable: no)
- activityName: String (nullable: no)
- description: String (nullable: yes)
- activityType: TimetableActivityType (nullable: no, ENUM) - ACADEMIC, BREAK, ASSEMBLY, SPORTS, EXTRACURRICULAR, ADMINISTRATIVE
- isBreak: Boolean (nullable: yes, default: false)
- sortOrder: Int (nullable: yes, default: 0)

**Relationships:**
- None

**Unique Constraints:**
- school_id + day_of_week + start_time

**Indexes:**
- school_id + day_of_week
- start_time + end_time
- is_active

**Enums Used:**
- DayOfWeek: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
- TimetableActivityType: ACADEMIC, BREAK, ASSEMBLY, SPORTS, EXTRACURRICULAR, ADMINISTRATIVE

---

### SCHOOL_WALLETS
**Table Name**: school_wallets
**Parent**: BaseEntity

**Fields:**
- customerCode: String (nullable: no)
- accountNumber: String (nullable: no, unique)
- accountName: String (nullable: no)
- bankName: String (nullable: no)
- bankSlug: String (nullable: yes)
- bankId: Int (nullable: yes)
- balance: BigDecimal (nullable: no, default: 0)
- currency: String (nullable: no, default: "NGN")
- paystackAccountId: Long (nullable: yes)
- assignedAt: LocalDateTime (nullable: yes)

**Relationships:**
- school: OneToOne -> School (lazy, not nullable)

**Unique Constraints:**
- school_id
- account_number

**Indexes:**
- school_id
- account_number
- customer_code

---

### SCHOOLS
**Table Name**: schools
**Parent**: BaseEntity

**Fields:**
- name: String (nullable: no)
- slug: String (nullable: no, unique)
- email: String (nullable: no)
- phone: String (nullable: no)
- website: String (nullable: yes)
- addressLine1: String (nullable: no)
- addressLine2: String (nullable: yes)
- city: String (nullable: no)
- state: String (nullable: no)
- postalCode: String (nullable: no)
- country: String (nullable: no, default: "Nigeria")
- subdomain: String (nullable: yes)
- customDomain: String (nullable: yes)
- sslEnabled: Boolean (nullable: yes, default: false)
- status: String (nullable: yes, default: "pending") - pending, active, suspended, inactive
- timezone: String (nullable: yes, default: "Africa/Lagos")
- currency: String (nullable: yes, default: "NGN")
- language: String (nullable: yes, default: "en")
- academicYearStart: String (nullable: yes, default: "09-01") - MM-DD format
- academicYearEnd: String (nullable: yes, default: "07-31") - MM-DD format
- currentAcademicYear: String (nullable: yes) - 2023-2024 format
- adminUserId: UUID (nullable: yes)
- adminName: String (nullable: no)
- adminEmail: String (nullable: no)
- adminPhone: String (nullable: no)
- logoUrl: String (nullable: yes)
- bannerUrl: String (nullable: yes)
- primaryColor: String (nullable: yes, default: "#007bff")
- secondaryColor: String (nullable: yes, default: "#6c757d")
- schoolMotto: String (nullable: yes)
- admissionPrefix: String (nullable: yes, unique)

**Relationships:**
- None (but referenced by many entities)

---

### SERVICE_USAGE_LOGS
**Table Name**: service_usage_logs
**Parent**: BaseEntity

**Fields:**
- serviceType: ServiceFeature (nullable: no, ENUM) - FEE_COLLECTION, WHATSAPP_MESSAGING, SMS_MESSAGING, AI_TOKENS
- amount: Int (nullable: no, default: 1)
- description: String (nullable: yes)
- timestamp: LocalDateTime (nullable: no, default: now)

**Relationships:**
- school: ManyToOne -> School (lazy, not nullable)
- user: ManyToOne -> User (lazy, not nullable)

**Indexes:**
- school_id
- user_id
- service_type

**Enums Used:**
- ServiceFeature: FEE_COLLECTION, WHATSAPP_MESSAGING, SMS_MESSAGING, AI_TOKENS

---

### SETTLEMENTS
**Table Name**: settlements
**Parent**: TenantAwareEntity

**Fields:**
- amount: BigDecimal (nullable: no)
- currency: String (nullable: no, default: "NGN")
- reference: String (nullable: no, unique)
- status: String (nullable: no)
- paymentChannel: String (nullable: yes)
- payerEmail: String (nullable: yes)
- transactionDate: LocalDateTime (nullable: no, default: now)
- rawPayload: TEXT (nullable: yes)
- reimbursed: Boolean (nullable: no, default: false)
- settlementType: SettlementType (nullable: yes, ENUM) - PAYSTACK, SQUAD, MANUAL

**Relationships:**
- paystackWallet: ManyToOne -> PaystackParentWallet (lazy, nullable)
- squadWallet: ManyToOne -> SquadParentWallet (lazy, nullable)
- parent: ManyToOne -> Parent (lazy, nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- term: ManyToOne -> Term (lazy, nullable)

**Indexes:**
- reference
- paystack_wallet_id
- squad_wallet_id

**Enums Used:**
- SettlementType: PAYSTACK, SQUAD, MANUAL

---

### SMS_MESSAGES
**Table Name**: sms_messages
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- recipientPhone: String (nullable: no)
- content: TEXT (nullable: no)
- direction: MessageDirection (nullable: no, ENUM) - OUTGOING, INCOMING
- status: String (nullable: no, default: "PENDING") - PENDING, SENT, FAILED, DELIVERED
- externalMessageId: String (nullable: yes)
- triggerFallback: Boolean (nullable: no, default: false)
- isFallback: Boolean (nullable: no, default: false)
- templateName: String (nullable: yes)
- paramsJson: TEXT (nullable: yes)
- fallbackChannel: String (nullable: yes)
- broadcastId: UUID (nullable: yes)

**Relationships:**
- user: ManyToOne -> User (lazy, nullable)
- school: ManyToOne -> School (lazy, nullable)

**Enums Used:**
- MessageDirection: OUTGOING, INCOMING

---

### SQUAD_PARENT_WALLETS
**Table Name**: squad_parent_wallets
**Parent**: TenantAwareEntity

**Fields:**
- customerIdentifier: String (nullable: yes)
- accountNumber: String (nullable: yes, unique)
- accountName: String (nullable: yes)
- bankName: String (nullable: yes)
- balance: BigDecimal (nullable: no, default: 0)
- currency: String (nullable: no, default: "NGN")
- assignedAt: LocalDateTime (nullable: yes)

**Relationships:**
- parent: ManyToOne -> Parent (lazy, not nullable)

**Unique Constraints:**
- parent_id
- account_number

**Indexes:**
- parent_id
- account_number

**Enums Used in Computed Methods:**
- DebtStatus: CLEARED, LOW, MEDIUM, HIGH

---

### STAFF
**Table Name**: staff
**Parent**: TenantAwareEntity

**Fields:**
- staffId: String (nullable: no)
- employeeNumber: String (nullable: yes)
- designation: String (nullable: no, default: "Teacher") - Teacher, Cleaner, Gateman, etc.
- hireDate: LocalDate (nullable: no)
- terminationDate: LocalDate (nullable: yes)
- employmentStatus: String (nullable: yes, default: "active")
- employmentType: String (nullable: yes, default: "full_time")
- highestDegree: String (nullable: yes)
- department: String (nullable: yes)
- isClassTeacher: Boolean (nullable: yes, default: false)
- isSubjectTeacher: Boolean (nullable: yes, default: false)
- bankName: String (nullable: yes)
- accountName: String (nullable: yes)
- accountNumber: String (nullable: yes)
- monthlyDeduction: Double (nullable: yes, default: 0.0)
- classTeacherFor: UUID (nullable: yes)
- yearsOfExperience: Int (nullable: yes, default: 0)

**Relationships:**
- user: ManyToOne -> User (lazy, not nullable)
- subjectAssignments: OneToMany -> ClassSubject (cascade: ALL, lazy)
- attendanceRecords: OneToMany -> Attendance (cascade: ALL, lazy)
- exams: OneToMany -> Exam (cascade: ALL, lazy)
- gradedResults: OneToMany -> ExamResult (cascade: ALL, lazy)
- studentFeedbacks: OneToMany -> StudentFeedback (cascade: ALL, lazy)
- classTeacherAssignments: OneToMany -> ClassTeacher (cascade: ALL, lazy)
- subjectTeacherAssignments: OneToMany -> SubjectTeacher (cascade: ALL, lazy)

**Unique Constraints:**
- user_id + school_id
- staff_id + school_id

**Indexes:**
- school_id + department + is_active
- school_id + designation + is_active

---

### STUDENTS
**Table Name**: students
**Parent**: TenantAwareEntity

**Fields:**
- studentId: String (nullable: no)
- admissionNumber: String (nullable: yes)
- admissionDate: LocalDate (nullable: no)
- graduationDate: LocalDate (nullable: yes)
- academicStatus: AcademicStatus (nullable: no, ENUM) - ENROLLED, GRADUATED, TRANSFERRED, EXPELLED, SUSPENDED
- currentGradeLevel: String (nullable: yes)
- dateOfBirth: LocalDate (nullable: yes)
- gender: Gender (nullable: yes, ENUM) - MALE, FEMALE
- previousSchool: String (nullable: yes)
- specialNeedsDescription: String (nullable: yes)
- transportationMethod: String (nullable: yes)
- passportPhotoUrl: String (nullable: yes)
- isNew: Boolean (nullable: yes, default: true)
- hasSpecialNeeds: Boolean (nullable: yes, default: false)

**Relationships:**
- user: ManyToOne -> User (fetch: EAGER, not nullable)
- classEnrollments: OneToMany -> StudentClass (cascade: ALL, lazy)
- parentRelationships: OneToMany -> ParentStudent (cascade: ALL, lazy)
- attendanceRecords: OneToMany -> Attendance (cascade: ALL, lazy)
- examResults: OneToMany -> ExamResult (cascade: ALL, lazy)
- feedbacks: OneToMany -> StudentFeedback (cascade: ALL, lazy)
- assessments: OneToMany -> Assessment (cascade: ALL, lazy)

**Unique Constraints:**
- user_id + school_id
- student_id + school_id

**Indexes:**
- school_id + is_active

**Enums Used:**
- AcademicStatus: ENROLLED, GRADUATED, TRANSFERRED, EXPELLED, SUSPENDED
- Gender: MALE, FEMALE

---

### STUDENT_CLASSES
**Table Name**: student_classes
**Parent**: TenantAwareEntity

**Fields:**
- enrollmentDate: LocalDate (nullable: no, default: now)

**Relationships:**
- student: ManyToOne -> Student (lazy, not nullable)
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- term: ManyToOne -> Term (lazy, not nullable)

**Unique Constraints:**
- student_id + class_id + academic_session_id + term_id + school_id

**Indexes:**
- school_id + is_active
- student_id + is_active
- class_id + is_active
- academic_session_id + school_id
- term_id + school_id
- academic_session_id + term_id + school_id

---

### STUDENT_FEEDBACK
**Table Name**: student_feedback
**Parent**: TenantAwareEntity

**Fields:**
- feedbackType: String (nullable: no) - academic, behavioral, general
- subject: String (nullable: yes)
- content: String (nullable: no)
- rating: String (nullable: yes) - excellent, good, satisfactory, needs_improvement
- feedbackDate: LocalDate (nullable: no, default: now)
- term: String (nullable: yes)

**Relationships:**
- student: ManyToOne -> Student (lazy, not nullable)
- staff: ManyToOne -> Staff (lazy, not nullable)

**Backward Compatibility Properties:**
- teacherId: UUID (computed from staff.id)
- teacher: Staff (computed from staff)

**Indexes:**
- school_id + student_id + feedback_date
- school_id + staff_id + feedback_date

---

### STUDENT_OPTIONAL_FEES
**Table Name**: student_optional_fees
**Parent**: TenantAwareEntity

**Fields:**
- optedInAt: LocalDateTime (nullable: no, default: now)
- optedInBy: String (nullable: yes)
- isLocked: Boolean (nullable: yes, default: false)
- customAmount: BigDecimal (nullable: yes, precision: 10, scale: 2)
- notes: String (nullable: yes)

**Relationships:**
- student: ManyToOne -> Student (lazy, not nullable)
- classFeeItem: ManyToOne -> ClassFeeItem (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- term: ManyToOne -> Term (lazy, nullable)

**Unique Constraints:**
- student_id + class_fee_item_id

**Indexes:**
- student_id
- class_fee_item_id

---

### SUBJECT_MAPPINGS
**Table Name**: subject_mappings
**Parent**: GlobalEntity

**Fields:**
- gradeLevel: Int (nullable: no) - e.g., 7 for JSS1, 10 for SS1
- elearnerSubjectId: UUID (nullable: no)

**Relationships:**
- subject: ManyToOne -> Subject (lazy, not nullable)

**Unique Constraints:**
- subject_id + grade_level

**Indexes:**
- subject_id

---

### SUBJECT_SCORES
**Table Name**: subject_scores
**Parent**: TenantAwareEntity

**Fields:**
- scoresJson: TEXT (nullable: yes) - JSON string
- grade: String (nullable: yes)
- position: Int (nullable: yes)
- remark: String (nullable: yes)

**Relationships:**
- assessment: ManyToOne -> Assessment (lazy, not nullable)
- subject: ManyToOne -> Subject (lazy, not nullable)
- classSubject: ManyToOne -> ClassSubject (lazy, nullable)

**Unique Constraints:**
- assessment_id + subject_id

**Indexes:**
- assessment_id
- subject_id
- class_subject_id

**Computed Method:**
- getTotalScore(): Int? - calculates sum from scoresJson

---

### SUBJECT_TEACHERS
**Table Name**: subject_teachers
**Parent**: TenantAwareEntity

**Fields:**
- None beyond parent class

**Relationships:**
- staff: ManyToOne -> Staff (lazy, not nullable)
- subject: ManyToOne -> Subject (lazy, not nullable)
- schoolClass: ManyToOne -> SchoolClass (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- term: ManyToOne -> Term (lazy, not nullable)

**Unique Constraints:**
- staff_id + subject_id + class_id + academic_session_id + term_id + school_id

**Indexes:**
- school_id + staff_id + academic_session_id + term_id
- class_id + subject_id + academic_session_id + term_id
- academic_session_id + term_id

---

### SUBJECTS
**Table Name**: subjects
**Parent**: GlobalEntity

**Fields:**
- subjectName: String (nullable: no)
- subjectCode: String (nullable: yes)
- description: String (nullable: yes)
- isCoreSubject: Boolean (nullable: yes, default: false)
- creditHours: Int (nullable: yes, default: 1)
- minGradeLevel: Int (nullable: yes, default: 1)
- maxGradeLevel: Int (nullable: yes, default: 12)
- category: String (nullable: yes)

**Relationships:**
- classAssignments: OneToMany -> ClassSubject (cascade: ALL, lazy)
- examinations: OneToMany -> Examination (cascade: ALL, lazy)
- subjectScores: OneToMany -> SubjectScore (cascade: ALL, lazy)
- mappings: OneToMany -> SubjectMapping (cascade: ALL, lazy)

**Unique Constraints:**
- subject_name (global, not tenant-scoped)

**Indexes:**
- subject_code

---

### TERMS
**Table Name**: terms
**Parent**: TenantAwareEntity

**Fields:**
- termName: String (nullable: no)
- startDate: LocalDate (nullable: no)
- endDate: LocalDate (nullable: yes)
- isCurrentTerm: Boolean (nullable: no, default: false)
- termNumber: Int (nullable: yes)
- termOrder: Int (nullable: yes)
- status: String (nullable: no, default: "planned") - planned, active, completed
- description: String (nullable: yes)
- durationInDays: Long (transient, computed from dates)

**Relationships:**
- academicSession: ManyToOne -> AcademicSession (lazy, not nullable)
- calendarEvents: OneToMany -> SchoolCalendar (cascade: ALL, lazy)
- examinations: OneToMany -> Exam (cascade: ALL, lazy)
- assessments: OneToMany -> Assessment (cascade: ALL, lazy)

**Unique Constraints:**
- academic_session_id + term_name

**Indexes:**
- school_id + academic_session_id
- academic_session_id + is_current_term

---

### USERS
**Table Name**: users
**Parent**: BaseEntity

**Fields:**
- phoneNumber: String (nullable: yes)
- passwordHash: String (nullable: yes)
- email: String (nullable: no, unique)
- firstName: String (nullable: yes)
- lastName: String (nullable: yes)
- middleName: String (nullable: yes)
- dateOfBirth: LocalDate (nullable: yes)
- gender: String (nullable: yes)
- profilePictureUrl: String (nullable: yes)
- addressLine1: String (nullable: yes)
- addressLine2: String (nullable: yes)
- city: String (nullable: yes)
- state: String (nullable: yes)
- postalCode: String (nullable: yes)
- country: String (nullable: no, default: "Nigeria")
- status: UserStatus (nullable: no, ENUM) - PENDING, ACTIVE, INACTIVE, SUSPENDED, APPROVED
- isVerified: Boolean (nullable: no, default: false)
- verificationStatus: String (nullable: yes, default: "unverified")
- approvalStatus: String (nullable: yes, default: "pending")
- verifiedAt: LocalDateTime (nullable: yes)
- approvedAt: LocalDateTime (nullable: yes)
- approvedBy: UUID (nullable: yes)
- lastLoginAt: LocalDateTime (nullable: yes)
- emailVerified: Boolean (nullable: no, default: false)
- emailVerificationToken: String (nullable: yes)
- emailVerificationExpires: LocalDateTime (nullable: yes)
- otpCode: String (nullable: yes)
- otpExpires: LocalDateTime (nullable: yes)
- lastOtpSent: LocalDateTime (nullable: yes)
- otpAttempts: Int (nullable: no, default: 0)
- intendedRole: String (nullable: yes)
- intendedSchoolSlug: String (nullable: yes)
- fullName: String (transient, computed from name fields)

**Relationships:**
- schoolRoles: OneToMany -> UserSchoolRole (cascade: ALL, fetch: EAGER)
- globalRoles: OneToMany -> UserGlobalRole (cascade: ALL, fetch: EAGER)
- studentProfiles: OneToMany -> Student (cascade: ALL, fetch: EAGER)
- parentProfiles: OneToMany -> Parent (cascade: ALL, fetch: EAGER)
- staffProfiles: OneToMany -> Staff (cascade: ALL, fetch: EAGER)

**Unique Constraints:**
- email

**Enums Used:**
- UserStatus: PENDING, ACTIVE, INACTIVE, SUSPENDED, APPROVED

---

### USER_GLOBAL_ROLES
**Table Name**: user_global_roles
**Parent**: BaseEntity

**Fields:**
- assignedAt: LocalDateTime (nullable: no, default: now)
- assignedBy: UUID (nullable: yes)

**Relationships:**
- user: ManyToOne -> User (fetch: EAGER, not nullable)
- role: ManyToOne -> Role (fetch: EAGER, not nullable)

**Unique Constraints:**
- user_id + role_id

---

### USER_SCHOOL_ROLES
**Table Name**: user_school_roles
**Parent**: TenantAwareEntity

**Fields:**
- assignedBy: UUID (nullable: yes)
- assignedAt: LocalDateTime (nullable: no, default: now)
- expiresAt: LocalDateTime (nullable: yes)
- isPrimary: Boolean (nullable: yes, default: false)

**Relationships:**
- user: ManyToOne -> User (fetch: EAGER, not nullable)
- role: ManyToOne -> Role (fetch: EAGER, not nullable)

**Unique Constraints:**
- user_id + school_id + role_id

**Indexes:**
- user_id + school_id + is_active

---

### WHATSAPP_MESSAGES
**Table Name**: whatsapp_messages
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- recipientPhone: String (nullable: no)
- content: TEXT (nullable: no)
- direction: MessageDirection (nullable: no, ENUM) - OUTGOING, INCOMING
- status: String (nullable: no, default: "PENDING") - PENDING, SENT, DELIVERED, READ, FAILED
- metaMessageId: String (nullable: yes)
- triggerFallback: Boolean (nullable: no, default: false)
- isFallback: Boolean (nullable: no, default: false)
- templateName: String (nullable: yes)
- paramsJson: TEXT (nullable: yes)
- fallbackChannel: String (nullable: yes)
- broadcastId: UUID (nullable: yes)

**Relationships:**
- user: ManyToOne -> User (lazy, nullable)
- school: ManyToOne -> School (lazy, nullable)

**Enums Used:**
- MessageDirection: OUTGOING, INCOMING

---

### WHATSAPP_TEMPLATES
**Table Name**: whatsapp_templates
**Parent**: BaseEntity

**Fields:**
- templateId: String (nullable: no, unique)
- templateName: String (nullable: no)
- language: String (nullable: no)
- category: String (nullable: no)
- parameterCount: Int (nullable: no, default: 0)
- parameterMapping: TEXT (nullable: yes)
- componentsJson: TEXT (nullable: yes)
- status: String (nullable: no, default: "PENDING")
- lastSyncedAt: LocalDateTime (nullable: no, default: now)
- isForBroadcast: Boolean (nullable: no, default: false)
- targetRole: String (nullable: no, default: "GENERAL")

**Relationships:**
- None

---

### SCHOOL_REIMBURSEMENTS
**Table Name**: school_reimbursements
**Parent**: BaseEntity

**Fields:**
- amount: BigDecimal (nullable: no)
- currency: String (nullable: no, default: "NGN")
- reference: String (nullable: no, unique)
- status: String (nullable: no) - PENDING, COMPLETED, FAILED
- reimbursementDate: LocalDateTime (nullable: no, default: now)
- notes: TEXT (nullable: yes)

**Relationships:**
- school: ManyToOne -> School (lazy, not nullable)
- academicSession: ManyToOne -> AcademicSession (lazy, nullable)
- term: ManyToOne -> Term (lazy, nullable)
- recordedBy: ManyToOne -> User (lazy, nullable)

---

### FEE_REMINDER_SCHEDULES
**Table Name**: fee_reminder_schedules
**Parent**: GlobalEntity (not TenantAware)

**Fields:**
- schoolId: UUID (nullable: no)
- frequency: String (nullable: no) - DAILY, WEEKLY, MONTHLY, WEEKENDS
- isActive: Boolean (nullable: no, default: true)
- lastRunAt: LocalDateTime (nullable: yes)

**Relationships:**
- None

---

## SUMMARY OF KEY RELATIONSHIPS

### One-to-Many (OneToMany)
- AcademicSession -> Term, SchoolCalendar
- Assessment -> SubjectScore
- Attendance -> None (but is target)
- ClassFeeItem -> None (but is target)
- ClassSubject -> None (but is target)
- ClassTeacher -> None (but is target)
- Department -> SchoolClass
- Designation -> DesignationPermission
- EducationTrack -> Department, SchoolClass
- Examination -> Question, ExaminationSubmission
- Exam -> ExamResult
- ExamResult -> None (but is target)
- FeeItem -> ClassFeeItem
- FeeStructure -> InvoiceItem
- Invoice -> InvoiceItem, PaymentNotification
- Parent -> ParentStudent, PaymentNotification, PaystackParentWallet(?), SquadParentWallet(?)
- Question -> None (but is target)
- SchoolClass -> ClassSubject, StudentClass, Attendance, Exam
- SchoolCalendar -> None (but is target)
- Settlement -> PaymentAllocation
- Staff -> ClassSubject, Attendance, Exam, ExamResult, StudentFeedback, ClassTeacher, SubjectTeacher
- Student -> StudentClass, ParentStudent, Attendance, ExamResult, StudentFeedback, Assessment
- StudentClass -> None (but is target)
- StudentFeedback -> None (but is target)
- Subject -> ClassSubject, Examination, SubjectScore, SubjectMapping
- SubjectMapping -> None (but is target)
- SubjectScore -> None (but is target)
- SubjectTeacher -> None (but is target)
- Term -> SchoolCalendar, Exam, Assessment
- User -> UserSchoolRole, UserGlobalRole, Student, Parent, Staff
- UserGlobalRole -> None (but is target)
- UserSchoolRole -> None (but is target)

### Many-to-One (ManyToOne)
- Assessment -> Student, AcademicSession, Term
- Attendance -> Student, SchoolClass, Staff
- ClassFeeItem -> SchoolClass, FeeItem, AcademicSession, Term
- ClassSubject -> SchoolClass, Subject, Staff
- ClassTeacher -> Staff, SchoolClass, AcademicSession, Term
- Department -> EducationTrack
- DesignationPermission -> Designation, Permission
- Exam -> SchoolClass, Staff, Term
- ExamResult -> Exam, Student, Staff
- Examination -> Subject, SchoolClass, Term, AcademicSession
- ExaminationSubmission -> Examination, Student
- InternalMessage -> InternalMessageThread, User
- InternalMessageParticipant -> InternalMessageThread, User
- Invoice -> Student, AcademicSession
- InvoiceItem -> Invoice, FeeStructure
- Parent -> User
- ParentStudent -> Parent, Student
- PaymentAllocation -> Settlement, Student
- PaymentNotification -> Invoice, Parent
- PaystackParentWallet -> Parent
- Question -> Examination
- SchoolBankAccount -> School
- SchoolCalendar -> AcademicSession, Term
- SchoolClass -> Department, EducationTrack
- SchoolReimbursement -> School, AcademicSession, Term, User
- Settlement -> PaystackParentWallet, SquadParentWallet, Parent, AcademicSession, Term
- SmsMessage -> User, School
- SquadParentWallet -> Parent
- Staff -> User
- Student -> User
- StudentClass -> Student, SchoolClass, AcademicSession, Term
- StudentFeedback -> Student, Staff
- StudentOptionalFee -> Student, ClassFeeItem, AcademicSession, Term
- Subject -> None (is base, OneToMany only)
- SubjectMapping -> Subject
- SubjectScore -> Assessment, Subject, ClassSubject
- SubjectTeacher -> Staff, Subject, SchoolClass, AcademicSession, Term
- Term -> AcademicSession
- UserGlobalRole -> User, Role
- UserSchoolRole -> User, Role
- WhatsAppMessage -> User, School

### One-to-One (OneToOne)
- Parent -> PaystackParentWallet, SquadParentWallet (mapped by parent)
- SchoolSubscription -> School
- SchoolWallet -> School

---

## ENUMS USED IN ENTITIES

1. **ActivityType** - activity_logs
2. **AttendanceStatus** - attendance
3. **CalendarEventType** - school_calendar
4. **DayOfWeek** - school_timetable
5. **DebtStatus** - used in PaystackParentWallet and SquadParentWallet
6. **DiscountType** - fee_items
7. **Gender** - students
8. **GenderEligibility** - fee_items
9. **InvoiceStatus** - invoices
10. **MessageDirection** - sms_messages, whatsapp_messages
11. **PaymentStatus** - payment_notifications
12. **RoleType** - roles
13. **SettlementType** - settlements
14. **SubscriptionStatus** - school_subscriptions
15. **StudentStatusEligibility** - fee_items
16. **SystemPermission** - reference enum for Permission module
17. **TimetableActivityType** - school_timetable
18. **AcademicStatus** - students
19. **UserRole** - reference enum
20. **UserStatus** - users
21. **ServiceFeature** - service_usage_logs
22. **RoleType** - roles

---

## DATABASE SCHEMA NOTES

### Tenant Architecture
- Most entities inherit from `TenantAwareEntity` which includes `schoolId` field
- Enables multi-tenant support where data is scoped per school

### Audit Fields
- All entities have `createdAt`, `updatedAt`, `isActive` from `BaseEntity`
- Managed via `@CreatedDate`, `@LastModifiedDate` annotations
- Auditing configured via `@EntityListeners(AuditingEntityListener::class)`

### ID Generation
- All primary keys use UUID strategy (`@GeneratedValue(strategy = GenerationType.UUID)`)
- No sequential integer IDs

### Lazy Loading
- All relationships use `FetchType.LAZY` for performance
- Student entity uses `FetchType.EAGER` for user relationship
- User entity uses `FetchType.EAGER` for role relationships

### Cascading
- Most relationships use `CascadeType.ALL` for deletion cascades
- Deleting parent should cascade to children

### Monetary Values
- Invoice amounts stored in "kobo/cents" (smallest unit) as Int
- BigDecimal used for wallet balances and payments

### JSON Storage
- `scoresJson` in SubjectScore stores JSON mapping of scores
- `parameterMapping`, `componentsJson` in WhatsAppTemplate
- `paramsJson` in SmsMessage and WhatsAppMessage
- `metadata` in ActivityLog
- `rawPayload` in Settlement
- `paymentPriorityOrder` in Parent

### Formula-Based Fields
- Calculated columns in Examination using `@org.hibernate.annotations.Formula`
- `questionCount`, `submissionCount` computed from counts

---
