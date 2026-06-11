use actix_web::{web, HttpResponse};
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::auth::{CreateParentWithUserRequest, CreateParentStudentInfo, UpdateParentStudentsRequest};
use crate::middleware::UserContext;
use crate::services::AuthService;
use crate::services::ParentService;

/// Lean DTO for updating a parent's contact details via PUT /api/auth/parents/{id}
#[derive(Debug, Clone, serde::Deserialize)]
pub struct UpdateParentRequest {
    pub full_name: String,
    pub email: String,
    pub phone_number: Option<String>,
    pub address: Option<String>,
}

pub async fn create_parent(db: web::Data<Database>, payload: web::Json<CreateParentWithUserRequest>) -> Result<HttpResponse, ApiError> {
    let p = ParentService::create_parent_with_user(&db, payload.into_inner()).await?;
    let mut val = serde_json::to_value(&p).unwrap_or_default();
    if let serde_json::Value::Object(ref mut map) = val {
        map.insert("message".to_string(), serde_json::Value::String("Parent created successfully".to_string()));
    }
    Ok(HttpResponse::Created().json(val))
}

pub async fn create_parent_with_user(db: web::Data<Database>, payload: web::Json<CreateParentWithUserRequest>) -> Result<HttpResponse, ApiError> {
    let p = ParentService::create_parent_with_user(&db, payload.into_inner()).await?;
    let mut val = serde_json::to_value(&p).unwrap_or_default();
    if let serde_json::Value::Object(ref mut map) = val {
        map.insert("message".to_string(), serde_json::Value::String("Parent created successfully".to_string()));
    }
    Ok(HttpResponse::Created().json(val))
}

pub async fn get_parent(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let p = ParentService::get_parent_by_id(&db, id).await?;
    Ok(HttpResponse::Ok().json(p))
}

pub async fn update_parent(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>, payload: web::Json<UpdateParentRequest>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let p = ParentService::update_parent_contact(&db, id, payload.into_inner()).await?;
    let mut val = serde_json::to_value(&p).unwrap_or_default();
    if let serde_json::Value::Object(ref mut map) = val {
        map.insert("message".to_string(), serde_json::Value::String("Parent updated successfully".to_string()));
    }
    Ok(HttpResponse::Ok().json(val))
}

pub async fn delete_parent(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    ParentService::delete_parent(&db, id).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"success": true, "message": "Parent deleted successfully"})))
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct AssignParentStudentsRequest {
    pub school_id: uuid::Uuid,
    pub parent_student_relationships: Vec<CreateParentStudentInfo>,
}

pub async fn assign_parent_students(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    payload: web::Json<AssignParentStudentsRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let parent_id = path.into_inner().0;
    let p = payload.into_inner();

    let parent = ParentService::get_parent_by_id(&db, parent_id).await?;
    let req = UpdateParentStudentsRequest {
        school_id: p.school_id,
        parent_user_id: parent.user_id,
        parent_student_relationships: p.parent_student_relationships,
    };

    AuthService::update_parent_student_relationships(&db, req, Some(user_ctx.user_id)).await?;

    Ok(HttpResponse::Ok().json(serde_json::json!({"success": true, "message": "Students assigned successfully"})))
}

pub async fn delete_parent_student_assignment(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let assignment_id = path.into_inner().0;
    ParentService::soft_delete_parent_student_relationship(&db, assignment_id, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"success": true, "message": "Student unlinked successfully"})))
}

#[derive(Debug, serde::Deserialize)]
pub struct ListParentsQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
    pub search: Option<String>,
    pub track_id: Option<uuid::Uuid>,
    pub class_id: Option<uuid::Uuid>,
    pub school_id: uuid::Uuid,
}

pub async fn list_parents(
    db: web::Data<Database>,
    query: web::Query<ListParentsQuery>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let page = q.page.unwrap_or(1);
    let per_page = q.per_page.unwrap_or(20);

    let res = ParentService::list_parents(
        &db,
        q.school_id,
        page,
        per_page,
        q.search,
        q.track_id,
        q.class_id,
    )
    .await?;

    Ok(HttpResponse::Ok().json(res))
}
