// ============================================================================
// SERVICES LAYER
// ============================================================================
// Business logic layer - contains application logic and orchestrates
// database operations, validation, and transformations

pub mod auth_service;
pub mod dashboard_service;
pub mod email_service;
pub mod user_service;
pub mod school_service;
pub mod finance;
pub mod community;
pub mod schedule;
pub mod assessment;
pub mod academic;
pub mod school_package_service;
pub use community::student_service;
pub use community::parent_service;
pub use community::staff_service;
pub use schedule::ScheduleService;
pub use assessment::AssessmentService;
pub use assessment::ExaminationService;
pub use academic::AcademicService;
pub mod health_service;

pub use auth_service::AuthService;
pub use email_service::EmailService;
pub use user_service::UserService;
pub use school_service::SchoolService;
pub use finance::FinanceService;
pub use student_service::StudentService;
pub use parent_service::ParentService;
pub use staff_service::StaffService;
pub use health_service::HealthService;
pub use school_package_service::SchoolPackageService;
