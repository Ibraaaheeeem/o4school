/// Models module - Organized database entities
///
/// This module is split into focused submodules for maintainability:
/// - `enums`: All enumeration types (UserStatus, AcademicStatus, InvoiceStatus, etc.)
/// - `requests`: Request/Response DTOs (HealthResponse, CreateSchoolRequest, etc.)
/// - `users`: User management (User, Role, Permission, UserSchoolRole, etc.)
/// - `organizations`: School structure (School, Department, Designation, etc.)
/// - `people`: Student/Staff/Parent records (Student, Staff, Parent, etc.)
/// - `academic`: Academic structure (SchoolClass, Subject, Term, StudentClass, etc.)
/// - `assessments`: Academic assessments (Attendance, Exam, ExamResult, etc.)
/// - `finance`: Financial management (Invoice, Payment, Wallet, etc.)
/// - `messaging`: Communication systems (WhatsApp, SMS, Internal Messages, etc.)
/// - `system`: System utilities (ActivityLog, ServiceFeature, etc.)

pub mod auth;
pub mod enums;
pub mod requests;
pub mod users;
pub mod organizations;
pub mod people;
pub mod academic;
pub mod assessments;
pub mod finance;
pub mod messaging;
pub mod system;

// Re-export enums for convenient access
pub use enums::{
    ActivityType, AcademicStatus, AttendanceStatus, Gender, InvoiceStatus, PaymentStatus,
    RoleType, UserRole, UserStatus,
};

// Re-export auth models
pub use auth::{
    SignUpRequest, SignUpResponse, SignInRequest, SignInResponse, ActivationRequest,
    ActivationResponse, VerifyEmailRequest, VerifyEmailResponse, ForgotPasswordRequest,
    ForgotPasswordResponse, ResetPasswordRequest, ResetPasswordResponse, RefreshTokenRequest,
    RefreshTokenResponse, LogoutRequest, LogoutResponse, SendOtpRequest, SendOtpResponse,
    VerifyOtpRequest, VerifyOtpResponse, AuthErrorResponse,
};

// Re-export request/response models
pub use requests::{CreateSchoolRequest, CreateTenantRequest, HealthResponse};

// Re-export user management models
pub use users::{DesignationPermission, Permission, Role, User, UserGlobalRole, UserSchoolRole};

// Re-export organization models
pub use organizations::{
    Department, Designation, EducationTrack, School, SchoolCalendar, SchoolTimetable, SubjectMapping,
};

// Re-export people models
pub use people::{Parent, ParentStudent, Staff, Student};

// Re-export academic models
pub use academic::{
    AcademicSession, ClassSubject, ClassTeacher, SchoolClass, StudentClass, Subject, SubjectTeacher,
    Term, GlobalSubject,
};

// Re-export assessment models
pub use assessments::{
    Assessment, Attendance, Exam, ExamResult, Examination, ExaminationSubmission, Question,
    StudentFeedback, SubjectScore,
};

// Re-export finance models
pub use finance::{
    ClassFeeItem, FeeItem, FeeStructure, Invoice, InvoiceItem, PaymentAllocation,
    PaymentNotification, PaystackParentWallet, SchoolBankAccount, SchoolReimbursement,
    SchoolSubscription, SchoolWallet, Settlement, SquadParentWallet, StudentOptionalFee,
};

// Re-export messaging models
pub use messaging::{
    InternalMessage, InternalMessageParticipant, InternalMessageThread, SmsMessaging,
    WhatsAppMessaging, WhatsAppTemplate,
};

// Re-export system models
pub use system::{ActivityLog, ServiceFeature, ServiceUsageLog};
