use actix_web::{web, HttpResponse};
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::auth::{CreateClassTeacherInfo, CreateStaffWithUserRequest, CreateSubjectTeacherInfo, UpdateClassTeacherRequest, UpdateSubjectTeacherRequest};
use crate::middleware::UserContext;
use crate::services::AuthService;
use crate::services::StaffService;

pub async fn create_staff(db: web::Data<Database>, payload: web::Json<CreateStaffWithUserRequest>) -> Result<HttpResponse, ApiError> {
    let s = StaffService::create_staff_with_user(&db, payload.into_inner()).await?;
    Ok(HttpResponse::Created().json(s))
}

pub async fn create_staff_with_user(db: web::Data<Database>, payload: web::Json<CreateStaffWithUserRequest>) -> Result<HttpResponse, ApiError> {
    let s = StaffService::create_staff_with_user(&db, payload.into_inner()).await?;
    Ok(HttpResponse::Created().json(s))
}

pub async fn get_staff(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let s = StaffService::get_staff_by_id(&db, id).await?;
    Ok(HttpResponse::Ok().json(s))
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct UpdateStaffRequest {
    pub employee_number: Option<String>,
    pub designation: Option<String>,
    pub position: Option<String>,
    pub hire_date: Option<String>,
    pub department: Option<String>,
    pub is_active: Option<bool>,
    
    // User fields
    pub full_name: Option<String>,
    pub email: Option<String>,
    pub phone_number: Option<String>,
}

pub async fn update_staff(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    payload: web::Json<UpdateStaffRequest>,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let p = payload.into_inner();

    // 1. Fetch existing staff database record
    let mut staff = StaffService::get_staff_by_id(&db, id).await?;

    // 2. Apply updates to staff record
    if let Some(ref emp_num) = p.employee_number {
        staff.employee_number = Some(emp_num.clone());
    }
    if let Some(ref desig) = p.designation {
        staff.designation = desig.clone();
    } else if let Some(ref pos) = p.position {
        staff.designation = pos.clone();
    }
    if let Some(ref dept) = p.department {
        staff.department = Some(dept.clone());
    }
    if let Some(act) = p.is_active {
        staff.is_active = act;
    }
    if let Some(ref hd_str) = p.hire_date {
        if let Ok(parsed_date) = chrono::NaiveDate::parse_from_str(hd_str, "%Y-%m-%d") {
            staff.hire_date = parsed_date;
        }
    }
    staff.updated_at = chrono::Utc::now().naive_utc();

    // 3. Save the updated staff record
    let updated_staff = StaffService::update_staff(&db, id, staff).await?;

    // 4. Update the associated user details if provided
    let mut user = crate::services::user_service::UserService::get_user(&db, updated_staff.user_id).await?;
    let mut user_updated = false;

    if let Some(ref email) = p.email {
        let clean_email = email.trim().to_lowercase();
        if !clean_email.is_empty() && clean_email != user.email {
            user.email = clean_email;
            user_updated = true;
        }
    }
    if let Some(ref phone) = p.phone_number {
        if user.phone_number.as_ref() != Some(phone) {
            user.phone_number = Some(phone.clone());
            user_updated = true;
        }
    }
    if let Some(ref full_name) = p.full_name {
        let name_parts: Vec<&str> = full_name.trim().split_whitespace().collect();
        if !name_parts.is_empty() {
            let first = name_parts[0].to_string();
            let last = if name_parts.len() > 1 { name_parts[1..].join(" ") } else { "".to_string() };
            if user.first_name.as_ref() != Some(&first) || user.last_name.as_ref() != Some(&last) {
                user.first_name = Some(first);
                user.last_name = Some(last);
                user_updated = true;
            }
        }
    }

    if user_updated {
        user.updated_at = chrono::Utc::now();
        crate::services::user_service::UserService::update_user(&db, updated_staff.user_id, user).await?;
    }

    // 5. Fetch the unified StaffListResponse to return to client
    let list_resp = crate::db::repositories::StaffRepository::get_list_response_by_id(db.pool(), id).await?;

    Ok(HttpResponse::Ok().json(list_resp))
}

pub async fn delete_staff(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    StaffService::delete_staff(&db, id).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct AssignStaffClassesRequest {
    pub school_id: uuid::Uuid,
    pub staff_class_assignments: Vec<CreateClassTeacherInfo>,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct AssignStaffSubjectsRequest {
    pub school_id: uuid::Uuid,
    pub staff_subject_assignments: Vec<CreateSubjectTeacherInfo>,
}

pub async fn assign_staff_classes(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    payload: web::Json<AssignStaffClassesRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let staff_id = path.into_inner().0;
    let p = payload.into_inner();

    let staff = StaffService::get_staff_by_id(&db, staff_id).await?;
    let req = UpdateClassTeacherRequest {
        school_id: p.school_id,
        staff_user_id: staff.user_id,
        staff_class_assignments: p.staff_class_assignments,
    };

    AuthService::update_class_teacher_assignments(&db, req, Some(user_ctx.user_id)).await?;

    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"assigned"})))
}

pub async fn assign_staff_subjects(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    payload: web::Json<AssignStaffSubjectsRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let staff_id = path.into_inner().0;
    let p = payload.into_inner();

    let staff = StaffService::get_staff_by_id(&db, staff_id).await?;
    let req = UpdateSubjectTeacherRequest {
        school_id: p.school_id,
        staff_user_id: staff.user_id,
        staff_subject_assignments: p.staff_subject_assignments,
    };

    AuthService::update_subject_teacher_assignments(&db, req, Some(user_ctx.user_id)).await?;

    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"assigned"})))
}

pub async fn delete_staff_class_assignment(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let assignment_id = path.into_inner().0;
    StaffService::soft_delete_staff_class_assignment(&db, assignment_id, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

pub async fn delete_staff_subject_assignment(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let assignment_id = path.into_inner().0;
    StaffService::soft_delete_staff_subject_assignment(&db, assignment_id, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

#[derive(Debug, serde::Deserialize)]
pub struct ListStaffQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
    pub search: Option<String>,
    pub track_id: Option<uuid::Uuid>,
    pub department_id: Option<uuid::Uuid>,
    pub class_id: Option<uuid::Uuid>,
    pub designation: Option<String>,
    pub school_id: uuid::Uuid,
}

pub async fn list_staff(
    db: web::Data<Database>,
    query: web::Query<ListStaffQuery>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let page = q.page.unwrap_or(1);
    let per_page = q.per_page.unwrap_or(20);

    let res = StaffService::list_staff(
        &db,
        q.school_id,
        page,
        per_page,
        q.search,
        q.track_id,
        q.department_id,
        q.class_id,
        q.designation,
    )
    .await?;

    Ok(HttpResponse::Ok().json(res))
}
