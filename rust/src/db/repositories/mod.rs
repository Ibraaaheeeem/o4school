// ============================================================================
// REPOSITORY PATTERN - Data Access Abstraction
// ============================================================================
// Repositories provide an abstraction for data access operations

pub mod user_repository;
pub mod school_repository;
pub mod student_repository;
pub mod user_school_role_repository;
pub mod parent_repository;
pub mod staff_repository;

pub use user_repository::UserRepository;
pub use school_repository::SchoolRepository;
pub use student_repository::StudentRepository;
pub use user_school_role_repository::UserSchoolRoleRepository;
pub use parent_repository::ParentRepository;
pub use staff_repository::StaffRepository;
