use std::collections::{BTreeSet, HashMap};

use uuid::Uuid;

use crate::db::repositories::school_package_repository::SchoolPackageRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::{
    ClassSubject, CreateClassRequest, CreateDepartmentRequest, CreateEducationTrackRequest,
    Department, EducationTrack, InitializeDefaultStructureResponse, LinkSubjectClassesRequest,
    SaveSchoolSubjectsRequest, SchoolClass, SchoolSubject, UpdateSchoolDataRequest,
    AcademicStructureResponse,
};

pub struct SchoolPackageService;

#[derive(serde::Serialize)]
pub struct StatusResponse {
    pub status: String,
    pub message: String,
}

impl SchoolPackageService {
    pub async fn initialize_default_structure(
        db: &Database,
        school_id: Uuid,
    ) -> Result<InitializeDefaultStructureResponse, ApiError> {
        SchoolPackageRepository::initialize_default_structure_if_empty(db.pool(), school_id).await
    }

    pub async fn get_school_data(
        db: &Database,
        school_id: Uuid,
    ) -> Result<serde_json::Value, ApiError> {
        SchoolPackageRepository::get_school_data(db.pool(), school_id).await
    }

    pub async fn get_academic_structure(
        db: &Database,
        school_id: Uuid,
    ) -> Result<AcademicStructureResponse, ApiError> {
        SchoolPackageRepository::get_academic_structure(db.pool(), school_id).await
    }

    pub async fn list_all_subjects(db: &Database) -> Result<Vec<crate::models::academic::Subject>, ApiError> {
        SchoolPackageRepository::list_all_subjects(db.pool()).await
    }

    pub async fn get_school_subjects_with_classes(
        db: &Database,
        school_id: Uuid,
        filter: crate::models::academic::SchoolSubjectFilter,
    ) -> Result<Vec<crate::models::academic::SchoolSubjectResponse>, ApiError> {
        SchoolPackageRepository::get_school_subjects_with_classes(db.pool(), school_id, filter).await
    }
    pub async fn save_edited_school_data(
        db: &Database,
        school_id: Uuid,
        request: &UpdateSchoolDataRequest,
    ) -> Result<StatusResponse, ApiError> {
        if let Some(name) = &request.name {
            if name.trim().is_empty() {
                return Err(ApiError::ValidationError(
                    "School name cannot be empty".to_string(),
                ));
            }
        }

        if let Some(address_line1) = &request.address_line1 {
            if address_line1.trim().is_empty() {
                return Err(ApiError::ValidationError(
                    "address_line1 cannot be empty".to_string(),
                ));
            }
        }

        let updated =
            SchoolPackageRepository::update_school_data(db.pool(), school_id, request).await?;

        if !updated {
            return Err(ApiError::NotFound("School not found".to_string()));
        }

        Ok(StatusResponse {
            status: "Success".to_string(),
            message: "School data updated successfully".to_string(),
        })
    }

    pub async fn create_track_in_school(
        db: &Database,
        request: &CreateEducationTrackRequest,
    ) -> Result<EducationTrack, ApiError> {
        if request.name.trim().is_empty() {
            return Err(ApiError::ValidationError(
                "Track name is required".to_string(),
            ));
        }

        SchoolPackageRepository::create_track(db.pool(), request).await
    }

    pub async fn create_department_in_track(
        db: &Database,
        request: &CreateDepartmentRequest,
    ) -> Result<Department, ApiError> {
        if request.name.trim().is_empty() {
            return Err(ApiError::ValidationError(
                "Department name is required".to_string(),
            ));
        }

        let track: Option<EducationTrack> =
            SchoolPackageRepository::get_track(db.pool(), request.school_id, request.track_id).await?;

        if track.is_none() {
            return Err(ApiError::NotFound(
                "Track not found for this school".to_string(),
            ));
        }

        SchoolPackageRepository::create_department(db.pool(), request).await
    }

    pub async fn create_class_in_department(
        db: &Database,
        request: &CreateClassRequest,
    ) -> Result<SchoolClass, ApiError> {
        if request.class_name.trim().is_empty() {
            return Err(ApiError::ValidationError(
                "class_name is required".to_string(),
            ));
        }

        if let Some(grade_level) = request.grade_level {
            if grade_level < 0 {
                return Err(ApiError::ValidationError(
                    "grade_level must be non-negative".to_string(),
                ));
            }
        }

        if let Some(max_capacity) = request.max_capacity {
            if max_capacity < 0 {
                return Err(ApiError::ValidationError(
                    "max_capacity must be non-negative".to_string(),
                ));
            }
        }

        if let Some(current_enrollment) = request.current_enrollment {
            if current_enrollment < 0 {
                return Err(ApiError::ValidationError(
                    "current_enrollment must be non-negative".to_string(),
                ));
            }
        }

        let department: Option<Department> =
            SchoolPackageRepository::get_department(db.pool(), request.school_id, request.department_id)
                .await?;

        if department.is_none() {
            return Err(ApiError::NotFound(
                "Department not found for this school".to_string(),
            ));
        }

        let department = department.unwrap();
        let track_id = department.track_id.ok_or_else(|| {
            ApiError::ValidationError("Department is not linked to a track".to_string())
        })?;

        SchoolPackageRepository::create_class(db.pool(), request, track_id).await
    }

    pub async fn link_classes_to_subject(
        db: &Database,
        request: &LinkSubjectClassesRequest,
    ) -> Result<Vec<ClassSubject>, ApiError> {
        if request.class_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one class_id is required".to_string(),
            ));
        }

        let unique_class_ids = request
            .class_ids
            .iter()
            .copied()
            .collect::<BTreeSet<_>>()
            .into_iter()
            .collect::<Vec<_>>();

        let school_subject: Option<SchoolSubject> =
            SchoolPackageRepository::get_school_subject(db.pool(), request.school_id, request.school_subject_id)
                .await?;

        if school_subject.is_none() {
            return Err(ApiError::NotFound(
                "School subject not found for this school".to_string(),
            ));
        }

        let classes: Vec<SchoolClass> =
            SchoolPackageRepository::list_active_classes(db.pool(), request.school_id, &unique_class_ids)
                .await?;

        if classes.len() != unique_class_ids.len() {
            return Err(ApiError::NotFound(
                "One or more classes were not found for this school".to_string(),
            ));
        }

        let mut class_map = HashMap::with_capacity(classes.len());
        for class in classes {
            class_map.insert(class.id, class);
        }

        let mut linked = Vec::with_capacity(unique_class_ids.len());
        for class_id in unique_class_ids {
            if !class_map.contains_key(&class_id) {
                return Err(ApiError::NotFound(format!(
                    "Class {} was not found for this school",
                    class_id
                )));
            }

            let existing = SchoolPackageRepository::get_class_subject(
                db.pool(),
                request.school_id,
                class_id,
                request.school_subject_id,
            )
            .await?;

            if let Some(existing_link) = existing {
                linked.push(existing_link);
                continue;
            }

            let created = SchoolPackageRepository::create_class_subject(
                db.pool(),
                request.school_id,
                class_id,
                request.school_subject_id,
                request.staff_id,
                request.assigned_by,
            )
            .await?;

            linked.push(created);
        }

        Ok(linked)
    }

    pub async fn save_school_subjects(
        db: &Database,
        request: &SaveSchoolSubjectsRequest,
    ) -> Result<Vec<SchoolSubject>, ApiError> {
        if request.subject_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one subject_id is required".to_string(),
            ));
        }

        let unique_subject_ids = request
            .subject_ids
            .iter()
            .copied()
            .collect::<BTreeSet<_>>()
            .into_iter()
            .collect::<Vec<_>>();

        SchoolPackageRepository::save_school_subjects(db.pool(), request.school_id, &unique_subject_ids)
            .await
    }

    pub async fn unlink_class_from_subject(
        db: &Database,
        school_subject_id: Uuid,
        class_id: Uuid,
    ) -> Result<Vec<crate::models::academic::SchoolSubjectResponse>, ApiError> {
        let school_id = SchoolPackageRepository::unlink_class_from_subject(db.pool(), school_subject_id, class_id).await?;
        
        SchoolPackageRepository::get_school_subjects_with_classes(
            db.pool(),
            school_id,
            crate::models::academic::SchoolSubjectFilter {
                class_name: None,
                department_id: None,
                track_id: None,
            },
        )
        .await
    }

    pub async fn deactivate_school_subject(
        db: &Database,
        school_subject_id: Uuid,
    ) -> Result<Vec<crate::models::academic::SchoolSubjectResponse>, ApiError> {
        let school_id = SchoolPackageRepository::deactivate_school_subject(db.pool(), school_subject_id).await?;
        
        SchoolPackageRepository::get_school_subjects_with_classes(
            db.pool(),
            school_id,
            crate::models::academic::SchoolSubjectFilter {
                class_name: None,
                department_id: None,
                track_id: None,
            },
        )
        .await
    }
}
