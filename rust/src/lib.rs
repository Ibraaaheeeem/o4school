pub mod config;
pub mod db;
pub mod errors;
pub mod handlers;
pub mod middleware;
pub mod models;
pub mod services;
pub mod utils;

pub use config::Config;
pub use db::Database;
pub use errors::ApiError;
pub use services::{AuthService, UserService, SchoolService, StudentService, HealthService};
