// ============================================================================
// SERVICES LAYER
// ============================================================================
// Business logic layer - contains application logic and orchestrates
// database operations, validation, and transformations

pub mod auth_service;
pub mod user_service;
pub mod school_service;
pub mod student_service;
pub mod health_service;

pub use auth_service::AuthService;
pub use user_service::UserService;
pub use school_service::SchoolService;
pub use student_service::StudentService;
pub use health_service::HealthService;
