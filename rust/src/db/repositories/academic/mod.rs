use sqlx::{PgPool, Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::academic::{ClassSubject, CreateScoringSchemeRequest, ScoringScheme};

pub struct ScoringSchemeRepository;

impl ScoringSchemeRepository {
    /// List class-subject assignments for a specific class, session, and term.
    pub async fn list_class_subjects_for_class(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
    ) -> Result<Vec<ClassSubject>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassSubject>(
                                                "SELECT id, school_id, class_id, school_subject_id, staff_id,
                                                                                created_at, updated_at, is_active
             FROM class_subjects
             WHERE school_id = $1
               AND class_id = $2
               AND is_active = true
             ORDER BY created_at ASC",
        )
        .bind(school_id)
        .bind(class_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Get a scoring scheme by ID
    pub async fn get_by_id(
        pool: &PgPool,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
    ) -> Result<Option<ScoringScheme>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ScoringScheme>(
            "SELECT id, created_at, updated_at, is_active, school_id, class_id, 
                    academic_session_id, term_id, scoring_scheme, notes
             FROM scoring_schemes 
             WHERE id = $1 AND school_id = $2 AND is_active = true"
        )
        .bind(scoring_scheme_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Get scoring scheme for a specific class, session, and term
    pub async fn get_by_class_session_term(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Option<ScoringScheme>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ScoringScheme>(
            "SELECT id, created_at, updated_at, is_active, school_id, class_id, 
                    academic_session_id, term_id, scoring_scheme, notes
             FROM scoring_schemes 
             WHERE school_id = $1 AND class_id = $2 
               AND academic_session_id IS NOT DISTINCT FROM $3
               AND term_id IS NOT DISTINCT FROM $4
               AND is_active = true
             LIMIT 1"
        )
        .bind(school_id)
        .bind(class_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// List all scoring schemes for a class
    pub async fn list_by_class(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
    ) -> Result<Vec<ScoringScheme>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ScoringScheme>(
            "SELECT id, created_at, updated_at, is_active, school_id, class_id, 
                    academic_session_id, term_id, scoring_scheme, notes
             FROM scoring_schemes 
             WHERE school_id = $1 AND class_id = $2 AND is_active = true
             ORDER BY created_at DESC"
        )
        .bind(school_id)
        .bind(class_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Create a new scoring scheme
    pub async fn create(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
        request: &CreateScoringSchemeRequest,
    ) -> Result<ScoringScheme, ApiError> {
        let mut tx = pool
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let scheme = Self::create_in_transaction(&mut tx, school_id, class_id, request).await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(scheme)
    }

    /// Create a new scoring scheme within an existing transaction
    pub async fn create_in_transaction(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        class_id: Uuid,
        request: &CreateScoringSchemeRequest,
    ) -> Result<ScoringScheme, ApiError> {
        let id = Uuid::new_v4();
        let now = chrono::Utc::now().naive_utc();
        let scoring_scheme_json = serde_json::to_value(&request.scoring_scheme)
            .map_err(|e| ApiError::DatabaseError(format!("Failed to serialize scoring scheme: {}", e)))?;

        sqlx::query_as::<sqlx::Postgres, ScoringScheme>(
            "INSERT INTO scoring_schemes 
             (id, created_at, updated_at, is_active, school_id, class_id, academic_session_id, term_id, scoring_scheme, notes)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
             RETURNING id, created_at, updated_at, is_active, school_id, class_id, academic_session_id, term_id, scoring_scheme, notes"
        )
        .bind(id)
        .bind(now)
        .bind(now)
        .bind(true)
        .bind(school_id)
        .bind(class_id)
        .bind(request.academic_session_id)
        .bind(request.term_id)
        .bind(scoring_scheme_json)
        .bind(&request.notes)
        .fetch_one(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(format!("Failed to create scoring scheme: {}", e)))
    }

    /// Update an existing scoring scheme
    pub async fn update(
        pool: &PgPool,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
        scoring_scheme_json: serde_json::Value,
        notes: Option<&str>,
    ) -> Result<ScoringScheme, ApiError> {
        let now = chrono::Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, ScoringScheme>(
            "UPDATE scoring_schemes 
             SET scoring_scheme = $1, notes = $2, updated_at = $3
             WHERE id = $4 AND school_id = $5 AND is_active = true
             RETURNING id, created_at, updated_at, is_active, school_id, class_id, academic_session_id, term_id, scoring_scheme, notes"
        )
        .bind(scoring_scheme_json)
        .bind(notes)
        .bind(now)
        .bind(scoring_scheme_id)
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(format!("Failed to update scoring scheme: {}", e)))
    }

    /// Soft delete a scoring scheme
    pub async fn delete(
        pool: &PgPool,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
    ) -> Result<(), ApiError> {
        let now = chrono::Utc::now().naive_utc();

        sqlx::query(
            "UPDATE scoring_schemes 
             SET is_active = false, updated_at = $1
             WHERE id = $2 AND school_id = $3"
        )
        .bind(now)
        .bind(scoring_scheme_id)
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(format!("Failed to delete scoring scheme: {}", e)))?;

        Ok(())
    }
}
