use uuid::Uuid;
use chrono::Utc;
use slug::slugify;
use crate::db::Database;
use crate::db::repositories::SchoolRepository;
use crate::errors::ApiError;
use crate::models::{School, CreateSchoolRequest};

pub struct SchoolService;

impl SchoolService {
    /// Get school by ID
    pub async fn get_school(db: &Database, school_id: Uuid) -> Result<School, ApiError> {
        SchoolRepository::get_by_id(db.pool(), school_id).await
    }

    /// Get school by slug
    pub async fn get_school_by_slug(db: &Database, slug: &str) -> Result<School, ApiError> {
        SchoolRepository::get_by_slug(db.pool(), slug).await
    }

    /// List all schools with pagination
    pub async fn list_schools(db: &Database, limit: i64, offset: i64) -> Result<Vec<School>, ApiError> {
        if limit > 100 {
            return Err(ApiError::BadRequest("Limit cannot exceed 100".to_string()));
        }
        SchoolRepository::get_all(db.pool(), limit, offset).await
    }

    /// Create a new school
    pub async fn create_school(db: &Database, req: CreateSchoolRequest) -> Result<School, ApiError> {
        // Validate inputs
        if req.name.is_empty() {
            return Err(ApiError::ValidationError("School name is required".to_string()));
        }
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }
        if req.phone.is_empty() {
            return Err(ApiError::ValidationError("Phone number is required".to_string()));
        }

        // Generate slug from name
        let slug = slugify(&req.name);

        // Create school object
        let school = School {
            id: Uuid::new_v4(),
            name: req.name,
            slug,
            address_line1: String::new(),
            address_line2: None,
            city: String::new(),
            state: String::new(),
            postal_code: None,
            country: String::new(),
            status: Some("PENDING".to_string()),
            timezone: Some("UTC".to_string()),
            currency: Some("USD".to_string()),
            language: Some("en".to_string()),
            website: None,
            admin_name: String::new(),
            admin_email: String::new(),
            admin_phone: String::new(),
            banner_url: None,
            logo_url: None,
            primary_color: None,
            secondary_color: None,
            school_motto: None,
            admission_prefix: None,
            staff_id_prefix: None,
            created_at: Utc::now().naive_utc(),
            updated_at: Utc::now().naive_utc(),
            is_active: true,
        };

        SchoolRepository::create(db.pool(), &school).await
    }

    /// Update school information
    pub async fn update_school(db: &Database, school_id: Uuid, updates: School) -> Result<School, ApiError> {
        // Verify school exists
        SchoolRepository::get_by_id(db.pool(), school_id).await?;

        SchoolRepository::update(db.pool(), school_id, &updates).await
    }

    /// Activate school
    pub async fn activate_school(db: &Database, school_id: Uuid) -> Result<School, ApiError> {
        let mut school = SchoolRepository::get_by_id(db.pool(), school_id).await?;
        
        school.status = Some("ACTIVE".to_string());
        school.is_active = true;
        school.updated_at = Utc::now().naive_utc();

        SchoolRepository::update(db.pool(), school_id, &school).await
    }

    /// Deactivate school (soft delete)
    pub async fn deactivate_school(db: &Database, school_id: Uuid) -> Result<(), ApiError> {
        SchoolRepository::delete(db.pool(), school_id).await
    }

    /// Count schools
    pub async fn count_schools(db: &Database) -> Result<i64, ApiError> {
        sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM schools WHERE is_active = true"
        )
        .fetch_one(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }
}
