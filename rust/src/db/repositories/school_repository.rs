use uuid::Uuid;
use sqlx::PgPool;
use crate::errors::ApiError;
use crate::models::School;

pub struct SchoolRepository;

impl SchoolRepository {
    /// Get school by ID
    pub async fn get_by_id(pool: &PgPool, school_id: Uuid) -> Result<School, ApiError> {
        sqlx::query_as::<sqlx::Postgres, School>(
            "SELECT * FROM schools WHERE id = $1"
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("School with id {} not found", school_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Get school by slug
    pub async fn get_by_slug(pool: &PgPool, slug: &str) -> Result<School, ApiError> {
        sqlx::query_as::<sqlx::Postgres, School>(
            "SELECT * FROM schools WHERE slug = $1"
        )
        .bind(slug)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("School with slug {} not found", slug))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Get all schools
    pub async fn get_all(pool: &PgPool, limit: i64, offset: i64) -> Result<Vec<School>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, School>(
            "SELECT * FROM schools WHERE is_active = true ORDER BY created_at DESC LIMIT $1 OFFSET $2"
        )
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Create a new school
    pub async fn create(pool: &PgPool, school: &School) -> Result<School, ApiError> {
        sqlx::query_as::<sqlx::Postgres, School>(
            r#"
            INSERT INTO schools (
                id, name, slug, address_line1, address_line2, city, state, postal_code, 
                country, status, timezone, currency, language, website, admin_name, 
                admin_email, admin_phone, banner_url, logo_url, primary_color, 
                secondary_color, school_motto, admission_prefix, staff_id_prefix,
                created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                $11, $12, $13, $14, $15, $16, $17, $18, $19, $20,
                $21, $22, $23, $24, $25, $26, $27
            )
            RETURNING *
            "#
        )
        .bind(school.id)
        .bind(&school.name)
        .bind(&school.slug)
        .bind(&school.address_line1)
        .bind(&school.address_line2)
        .bind(&school.city)
        .bind(&school.state)
        .bind(&school.postal_code)
        .bind(&school.country)
        .bind(&school.status)
        .bind(&school.timezone)
        .bind(&school.currency)
        .bind(&school.language)
        .bind(&school.website)
        .bind(&school.admin_name)
        .bind(&school.admin_email)
        .bind(&school.admin_phone)
        .bind(&school.banner_url)
        .bind(&school.logo_url)
        .bind(&school.primary_color)
        .bind(&school.secondary_color)
        .bind(&school.school_motto)
        .bind(&school.admission_prefix)
        .bind(&school.staff_id_prefix)
        .bind(school.created_at)
        .bind(school.updated_at)
        .bind(school.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Update school
    pub async fn update(pool: &PgPool, school_id: Uuid, updates: &School) -> Result<School, ApiError> {
        sqlx::query_as::<sqlx::Postgres, School>(
            r#"
            UPDATE schools SET
                name = $1, address_line1 = $2, city = $3, state = $4, 
                postal_code = $5, status = $6, timezone = $7, currency = $8,
                language = $9, website = $10, admin_name = $11, admin_email = $12,
                admin_phone = $13, logo_url = $14, banner_url = $15, 
                updated_at = $16, is_active = $17
            WHERE id = $18
            RETURNING *
            "#
        )
        .bind(&updates.name)
        .bind(&updates.address_line1)
        .bind(&updates.city)
        .bind(&updates.state)
        .bind(&updates.postal_code)
        .bind(&updates.status)
        .bind(&updates.timezone)
        .bind(&updates.currency)
        .bind(&updates.language)
        .bind(&updates.website)
        .bind(&updates.admin_name)
        .bind(&updates.admin_email)
        .bind(&updates.admin_phone)
        .bind(&updates.logo_url)
        .bind(&updates.banner_url)
        .bind(updates.updated_at)
        .bind(updates.is_active)
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Delete school (soft delete)
    pub async fn delete(pool: &PgPool, school_id: Uuid) -> Result<(), ApiError> {
        sqlx::query(
            "UPDATE schools SET is_active = false, updated_at = NOW() WHERE id = $1"
        )
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }
}
