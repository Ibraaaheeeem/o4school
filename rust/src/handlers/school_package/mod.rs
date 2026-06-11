use actix_web::{web, HttpResponse};
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;
use crate::models::{
    CreateClassRequest, CreateDepartmentRequest, CreateEducationTrackRequest,
    InitializeDefaultStructureRequest, LinkSchoolSubjectClassesRequest,
    LinkSubjectClassesRequest, SaveSchoolSubjectsRequest,
    UpdateSchoolDataRequest,
};
use crate::services::school_package_service::SchoolPackageService;

pub async fn save_edited_school_data(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
    request: web::Json<UpdateSchoolDataRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = SchoolPackageService::save_edited_school_data(
        &db,
        school_id.into_inner(),
        &request.into_inner(),
    )
    .await?;

    Ok(HttpResponse::Ok().json(response))
}

pub async fn get_school_data(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
) -> Result<HttpResponse, ApiError> {
    let response = SchoolPackageService::get_school_data(&db, school_id.into_inner()).await?;
    Ok(HttpResponse::Ok().json(response))
}

pub async fn create_track(
    db: web::Data<Database>,
    request: web::Json<CreateEducationTrackRequest>,
) -> Result<HttpResponse, ApiError> {
    let track = SchoolPackageService::create_track_in_school(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(track))
}

pub async fn create_department(
    db: web::Data<Database>,
    request: web::Json<CreateDepartmentRequest>,
) -> Result<HttpResponse, ApiError> {
    let department =
        SchoolPackageService::create_department_in_track(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(department))
}

pub async fn create_class(
    db: web::Data<Database>,
    request: web::Json<CreateClassRequest>,
) -> Result<HttpResponse, ApiError> {
    let class_record = SchoolPackageService::create_class_in_department(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(class_record))
}

pub async fn link_classes_to_subject(
    db: web::Data<Database>,
    request: web::Json<LinkSubjectClassesRequest>,
) -> Result<HttpResponse, ApiError> {
    let links = SchoolPackageService::link_classes_to_subject(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(links))
}

pub async fn link_classes_to_school_subject(
    db: web::Data<Database>,
    school_subject_id: web::Path<Uuid>,
    request: web::Json<LinkSchoolSubjectClassesRequest>,
) -> Result<HttpResponse, ApiError> {
    let school_id = request.school_id;
    let request_service = LinkSubjectClassesRequest {
        school_id,
        school_subject_id: school_subject_id.into_inner(),
        class_ids: request.class_ids.clone(),
        staff_id: request.staff_id,
        assigned_by: request.assigned_by,
    };

    let _links = SchoolPackageService::link_classes_to_subject(&db, &request_service).await?;
    let updated_subjects = SchoolPackageService::get_school_subjects_with_classes(
        &db,
        school_id,
        crate::models::academic::SchoolSubjectFilter {
            class_name: None,
            department_id: None,
            track_id: None,
        },
    )
    .await?;
    Ok(HttpResponse::Created().json(updated_subjects))
}

pub async fn save_school_subjects(
    db: web::Data<Database>,
    request: web::Json<SaveSchoolSubjectsRequest>,
) -> Result<HttpResponse, ApiError> {
    let school_id = request.school_id;
    let _saved = SchoolPackageService::save_school_subjects(&db, &request.into_inner()).await?;
    let updated_subjects = SchoolPackageService::get_school_subjects_with_classes(
        &db,
        school_id,
        crate::models::academic::SchoolSubjectFilter {
            class_name: None,
            department_id: None,
            track_id: None,
        },
    )
    .await?;
    Ok(HttpResponse::Created().json(updated_subjects))
}

pub async fn initialize_default_structure(
    db: web::Data<Database>,
    request: web::Json<InitializeDefaultStructureRequest>,
) -> Result<HttpResponse, ApiError> {
    let response =
        SchoolPackageService::initialize_default_structure(&db, request.school_id).await?;

    if response.created {
        Ok(HttpResponse::Created().json(response))
    } else {
        Ok(HttpResponse::Ok().json(response))
    }
}

pub async fn get_academic_structure(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
) -> Result<HttpResponse, ApiError> {
    let response = SchoolPackageService::get_academic_structure(&db, school_id.into_inner()).await?;
    Ok(HttpResponse::Ok().json(response))
}

pub async fn get_all_subjects(db: web::Data<Database>) -> Result<HttpResponse, ApiError> {
    let subjects = SchoolPackageService::list_all_subjects(&db).await?;
    Ok(HttpResponse::Ok().json(subjects))
}

pub async fn get_school_subjects(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
    filter: web::Query<crate::models::academic::SchoolSubjectFilter>,
) -> Result<HttpResponse, ApiError> {
    let response = SchoolPackageService::get_school_subjects_with_classes(
        &db,
        school_id.into_inner(),
        filter.into_inner(),
    )
    .await?;
    Ok(HttpResponse::Ok().json(response))
}

pub async fn unlink_class_from_school_subject(
    db: web::Data<Database>,
    path: web::Path<(Uuid, Uuid)>,
) -> Result<HttpResponse, ApiError> {
    let (school_subject_id, class_id) = path.into_inner();
    let updated_subjects = SchoolPackageService::unlink_class_from_subject(&db, school_subject_id, class_id).await?;
    Ok(HttpResponse::Ok().json(updated_subjects))
}

pub async fn deactivate_school_subject(
    db: web::Data<Database>,
    school_subject_id: web::Path<Uuid>,
) -> Result<HttpResponse, ApiError> {
    let school_subject_id = school_subject_id.into_inner();
    let updated_subjects = SchoolPackageService::deactivate_school_subject(&db, school_subject_id).await?;
    Ok(HttpResponse::Ok().json(updated_subjects))
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/school")
            .route("/subjects", web::get().to(get_all_subjects))
            .route("/{school_id}", web::get().to(get_school_data))
            .route(
                "/{school_id}/subjects",
                web::get().to(get_school_subjects),
            )
            .route(
                "/{school_id}/academic-structure",
                web::get().to(get_academic_structure),
            )
            .route("/schools/{school_id}", web::put().to(save_edited_school_data))
            .route("/tracks", web::post().to(create_track))
            .route("/departments", web::post().to(create_department))
            .route("/classes", web::post().to(create_class))
            .route("/school-subjects", web::post().to(save_school_subjects))
            .route(
                "/school-subjects/{school_subject_id}",
                web::delete().to(deactivate_school_subject),
            )
            .route(
                "/school-subjects/{school_subject_id}/classes/link",
                web::post().to(link_classes_to_school_subject),
            )
            .route(
                "/school-subjects/{school_subject_id}/classes/{class_id}/unlink",
                web::delete().to(unlink_class_from_school_subject),
            )
            .route("/class-subjects/link", web::post().to(link_classes_to_subject))
            .route(
                "/default-structure/initialize",
                web::post().to(initialize_default_structure),
            ),
    );
}
