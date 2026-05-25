use uuid::Uuid;
use sqlx::PgPool;
use sqlx::{Transaction, Postgres, Executor};
use crate::errors::ApiError;
use crate::models::Parent;

pub struct ParentRepository;

impl ParentRepository {
    /// Get parent by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Parent, ApiError> {
        sqlx::query_as::<_, Parent>(
            "SELECT * FROM parents WHERE id = $1"
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Parent with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Create a new parent record
    pub async fn create(pool: &PgPool, parent: &Parent) -> Result<Parent, ApiError> {
        sqlx::query_as::<_, Parent>(
            r#"
            INSERT INTO parents (
                id, created_at, is_active, updated_at, school_id, is_emergency_contact,
                is_financially_responsible, is_primary_contact, receive_academic_updates,
                receive_disciplinary_updates, receive_financial_updates, user_id,
                payment_distribution_type, payment_priority_order
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14
            )
            RETURNING *
            "#
        )
        .bind(parent.id)
        .bind(parent.created_at)
        .bind(parent.is_active)
        .bind(parent.updated_at)
        .bind(parent.school_id)
        .bind(parent.is_emergency_contact)
        .bind(parent.is_financially_responsible)
        .bind(parent.is_primary_contact)
        .bind(parent.receive_academic_updates)
        .bind(parent.receive_disciplinary_updates)
        .bind(parent.receive_financial_updates)
        .bind(parent.user_id)
        .bind(&parent.payment_distribution_type)
        .bind(&parent.payment_priority_order)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            log::error!("ParentRepository::create failed: {}", e);
            ApiError::DatabaseError(e.to_string())
        })
    }

    /// Create a new parent record within an existing transaction
        // transactional helpers removed; use `create` method on pool instead

    /// Get parent by user_id
    pub async fn get_by_user_id(pool: &PgPool, user_id: Uuid) -> Result<Parent, ApiError> {
        sqlx::query_as::<_, Parent>(
            "SELECT * FROM parents WHERE user_id = $1"
        )
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Parent for user_id {} not found", user_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }
}
