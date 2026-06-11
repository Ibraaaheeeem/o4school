// ============================================================================
// REPOSITORY PATTERN - Data Access Abstraction
// ============================================================================
// Repositories provide an abstraction for data access operations

pub mod user_repository;
pub mod school_repository;
pub mod user_school_role_repository;
pub mod finance;
pub mod community;
pub mod schedule;
pub mod assessment;
pub mod academic;
pub mod school_package_repository;

pub use community::student_repository;
pub use community::parent_repository;
pub use community::staff_repository;
pub use schedule::academic_calendar_repository;
pub use assessment::AssessmentRecordRepository;
pub use academic::ScoringSchemeRepository;
pub use assessment::ExaminationRepository;

pub use user_repository::UserRepository;
pub use school_repository::SchoolRepository;
pub use student_repository::StudentRepository;
pub use user_school_role_repository::UserSchoolRoleRepository;
pub use finance::FinanceRepository;
pub use parent_repository::ParentRepository;
pub use staff_repository::StaffRepository;
pub use academic_calendar_repository::AcademicCalendarRepository;
pub use school_package_repository::SchoolPackageRepository;
