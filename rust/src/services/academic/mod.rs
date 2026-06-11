use uuid::Uuid;

use crate::db::repositories::ScoringSchemeRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::academic::{CreateScoringSchemeRequest, UpdateScoringSchemeRequest, ScoringScheme};

pub struct AcademicService;

impl AcademicService {
    /// Get a scoring scheme by ID
    pub async fn get_scoring_scheme(
        db: &Database,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
    ) -> Result<Option<ScoringScheme>, ApiError> {
        ScoringSchemeRepository::get_by_id(db.pool(), school_id, scoring_scheme_id).await
    }

    /// Get the active scoring scheme for a class in a specific session and term
    pub async fn get_scoring_scheme_for_class(
        db: &Database,
        school_id: Uuid,
        class_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Option<ScoringScheme>, ApiError> {
        ScoringSchemeRepository::get_by_class_session_term(
            db.pool(),
            school_id,
            class_id,
            academic_session_id,
            term_id,
        )
        .await
    }

    /// List all scoring schemes for a class
    pub async fn list_scoring_schemes_for_class(
        db: &Database,
        school_id: Uuid,
        class_id: Uuid,
    ) -> Result<Vec<ScoringScheme>, ApiError> {
        ScoringSchemeRepository::list_by_class(db.pool(), school_id, class_id).await
    }

    /// Create a new scoring scheme
    pub async fn create_scoring_scheme(
        db: &Database,
        request: &CreateScoringSchemeRequest,
    ) -> Result<Vec<ScoringScheme>, ApiError> {
        // Validate scoring scheme components
        Self::validate_scoring_components(&request.scoring_scheme)?;

        let class_ids = Self::resolve_target_class_ids(&request.class_ids)?;
        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let mut created = Vec::with_capacity(class_ids.len());
        for class_id in class_ids {
            let scheme = ScoringSchemeRepository::create_in_transaction(
                &mut tx,
                request.school_id,
                class_id,
                request,
            )
            .await?;
            created.push(scheme);
        }

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(created)
    }

    fn resolve_target_class_ids(class_ids: &[Uuid]) -> Result<Vec<Uuid>, ApiError> {
        if class_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one class ID is required".to_string(),
            ));
        }

        let mut normalized = Vec::with_capacity(class_ids.len());
        let mut seen = std::collections::HashSet::new();
        for class_id in class_ids {
            if seen.insert(class_id) {
                normalized.push(*class_id);
            }
        }

        Ok(normalized)
    }

    /// Update a scoring scheme
    pub async fn update_scoring_scheme(
        db: &Database,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
        request: &UpdateScoringSchemeRequest,
    ) -> Result<ScoringScheme, ApiError> {
        // Validate scoring scheme components
        Self::validate_scoring_components(&request.scoring_scheme)?;

        let scoring_scheme_json = serde_json::to_value(&request.scoring_scheme)
            .map_err(|e| ApiError::DatabaseError(format!("Failed to serialize scoring scheme: {}", e)))?;

        ScoringSchemeRepository::update(
            db.pool(),
            school_id,
            scoring_scheme_id,
            scoring_scheme_json,
            None,
        )
        .await
    }

    /// Delete a scoring scheme (soft delete)
    pub async fn delete_scoring_scheme(
        db: &Database,
        school_id: Uuid,
        scoring_scheme_id: Uuid,
    ) -> Result<(), ApiError> {
        ScoringSchemeRepository::delete(db.pool(), school_id, scoring_scheme_id).await
    }

    /// Validate scoring scheme components
    fn validate_scoring_components(components: &[crate::models::academic::ScoringComponent]) -> Result<(), ApiError> {
        if components.is_empty() {
            return Err(ApiError::ValidationError(
                "Scoring scheme must have at least one component".to_string(),
            ));
        }

        let mut total_max = 0;
        let mut seen_ids = std::collections::HashSet::new();

        for component in components {
            // Validate max score is positive
            if component.max <= 0 {
                return Err(ApiError::ValidationError(format!(
                    "Component '{}' max score must be positive",
                    component.name
                )));
            }

            // Validate unique IDs
            if !seen_ids.insert(component.id) {
                return Err(ApiError::ValidationError(format!(
                    "Duplicate component ID: {}",
                    component.id
                )));
            }

            total_max += component.max;
        }

        // Optional: validate total max (e.g., should sum to 100)
        if total_max != 100 {
            return Err(ApiError::ValidationError(format!(
                "Total of component max scores must be 100, got {}",
                total_max
            )));
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::AcademicService;
    use crate::errors::ApiError;
    use crate::models::academic::{CreateScoringSchemeRequest, ScoringComponent, UpdateScoringSchemeRequest};
    use uuid::Uuid;

    #[test]
    fn create_scoring_scheme_requires_at_least_one_class_id() {
        let resolved = AcademicService::resolve_target_class_ids(&[]);

        match resolved {
            Err(ApiError::ValidationError(message)) => {
                assert!(message.contains("At least one class ID is required"));
            }
            other => panic!("expected validation error, got {:?}", other),
        }
    }

    #[test]
    fn create_scoring_scheme_deduplicates_target_classes() {
        let first = Uuid::new_v4();
        let second = Uuid::new_v4();
        let ids = vec![first, second, first, second];

        let resolved = AcademicService::resolve_target_class_ids(&ids).unwrap();

        assert_eq!(resolved, vec![first, second]);
    }

    #[test]
    fn create_scoring_scheme_validation_accepts_20_20_60() {
        let request = CreateScoringSchemeRequest {
            school_id: Uuid::new_v4(),
            class_ids: vec![Uuid::new_v4()],
            academic_session_id: None,
            term_id: None,
            scoring_scheme: vec![
                ScoringComponent {
                    id: 1,
                    name: "Continuous Assessment I".to_string(),
                    alias: "CA I".to_string(),
                    max: 20,
                },
                ScoringComponent {
                    id: 2,
                    name: "Continuous Assessment II".to_string(),
                    alias: "CA II".to_string(),
                    max: 20,
                },
                ScoringComponent {
                    id: 3,
                    name: "End of term Examination".to_string(),
                    alias: "Exam".to_string(),
                    max: 60,
                },
            ],
            notes: None,
        };

        let result = AcademicService::validate_scoring_components(&request.scoring_scheme);

        assert!(result.is_ok(), "expected valid scoring scheme, got: {:?}", result);
    }

    #[test]
    fn update_scoring_scheme_validation_rejects_invalid_total() {
        let request = UpdateScoringSchemeRequest {
            scoring_scheme: vec![
                ScoringComponent {
                    id: 1,
                    name: "Continuous Assessment I".to_string(),
                    alias: "CA I".to_string(),
                    max: 30,
                },
                ScoringComponent {
                    id: 2,
                    name: "Continuous Assessment II".to_string(),
                    alias: "CA II".to_string(),
                    max: 20,
                },
                ScoringComponent {
                    id: 3,
                    name: "End of term Examination".to_string(),
                    alias: "Exam".to_string(),
                    max: 40,
                },
            ],
        };

        let result = AcademicService::validate_scoring_components(&request.scoring_scheme);

        match result {
            Err(ApiError::ValidationError(message)) => {
                assert!(message.contains("must be 100"));
            }
            other => panic!("expected validation error, got {:?}", other),
        }
    }

    #[test]
    fn update_scoring_scheme_validation_rejects_duplicate_component_ids() {
        let request = UpdateScoringSchemeRequest {
            scoring_scheme: vec![
                ScoringComponent {
                    id: 1,
                    name: "Continuous Assessment I".to_string(),
                    alias: "CA I".to_string(),
                    max: 50,
                },
                ScoringComponent {
                    id: 1,
                    name: "End of term Examination".to_string(),
                    alias: "Exam".to_string(),
                    max: 50,
                },
            ],
        };

        let result = AcademicService::validate_scoring_components(&request.scoring_scheme);

        match result {
            Err(ApiError::ValidationError(message)) => {
                assert!(message.contains("Duplicate component ID"));
            }
            other => panic!("expected validation error, got {:?}", other),
        }
    }
}
