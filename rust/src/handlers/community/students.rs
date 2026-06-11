use actix_web::{web, HttpResponse};
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::Student;
use crate::services::StudentService;
use crate::middleware::UserContext;
use crate::models::auth::{CreateStudentClassInfo, CreateUserInfo, CreateStudentInfo};
use serde::Deserialize;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct CreateStudentRequest {
    pub user: CreateUserInfo,
    pub student: CreateStudentInfo,
    pub student_classes: Option<Vec<CreateStudentClassInfo>>,
}

#[derive(Debug, Deserialize)]
pub struct AssignStudentClassesRequest {
    pub school_id: Uuid,
    pub classes: Vec<CreateStudentClassInfo>,
}

pub async fn create_student(db: web::Data<Database>, payload: web::Json<CreateStudentRequest>) -> Result<HttpResponse, ApiError> {
    let p = payload.into_inner();

    let s = StudentService::create_student_with_user(&db, p.user, p.student, p.student_classes).await?;
    Ok(HttpResponse::Created().json(s))
}

pub async fn create_student_with_user(db: web::Data<Database>, payload: web::Json<CreateStudentRequest>) -> Result<HttpResponse, ApiError> {
    let p = payload.into_inner();

    let s = StudentService::create_student_with_user(&db, p.user, p.student, p.student_classes).await?;
    Ok(HttpResponse::Created().json(s))
}

pub async fn get_student(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let s = StudentService::get_student_by_id(&db, id).await?;
    let u = crate::services::UserService::get_user(&db, s.user_id).await?;

    let guardian_name = sqlx::query_scalar::<sqlx::Postgres, String>(
        r#"
        SELECT u_parent.first_name || ' ' || u_parent.last_name
        FROM parent_student_relationships ps
        JOIN parents p ON ps.parent_id = p.id
        JOIN users u_parent ON p.user_id = u_parent.id
        WHERE ps.student_id = $1 AND ps.is_active = true
        LIMIT 1
        "#
    )
    .bind(s.id)
    .fetch_optional(db.pool())
    .await
    .unwrap_or(None);

    let guardian_phone = sqlx::query_scalar::<sqlx::Postgres, String>(
        r#"
        SELECT u_parent.phone_number
        FROM parent_student_relationships ps
        JOIN parents p ON ps.parent_id = p.id
        JOIN users u_parent ON p.user_id = u_parent.id
        WHERE ps.student_id = $1 AND ps.is_active = true
        LIMIT 1
        "#
    )
    .bind(s.id)
    .fetch_optional(db.pool())
    .await
    .unwrap_or(None);

    let guardian_email = sqlx::query_scalar::<sqlx::Postgres, String>(
        r#"
        SELECT u_parent.email
        FROM parent_student_relationships ps
        JOIN parents p ON ps.parent_id = p.id
        JOIN users u_parent ON p.user_id = u_parent.id
        WHERE ps.student_id = $1 AND ps.is_active = true
        LIMIT 1
        "#
    )
    .bind(s.id)
    .fetch_optional(db.pool())
    .await
    .unwrap_or(None);

    let guardian_relationship = sqlx::query_scalar::<sqlx::Postgres, String>(
        r#"
        SELECT ps.relationship_type
        FROM parent_student_relationships ps
        WHERE ps.student_id = $1 AND ps.is_active = true
        LIMIT 1
        "#
    )
    .bind(s.id)
    .fetch_optional(db.pool())
    .await
    .unwrap_or(None);

    let resp = crate::models::StudentDetailResponse {
        id: s.id,
        school_id: s.school_id,
        user_id: s.user_id,
        student_id: s.admission_number.clone().unwrap_or_else(|| s.student_id.clone()),
        admission_number: s.admission_number,
        admission_date: s.admission_date.format("%Y-%m-%d").to_string(),
        graduation_date: s.graduation_date.map(|d| d.format("%Y-%m-%d").to_string()),
        academic_status: s.academic_status,
        current_grade_level: s.current_grade_level,
        date_of_birth: s.date_of_birth.map(|d| d.format("%Y-%m-%d").to_string()),
        gender: s.gender,
        previous_school: s.previous_school,
        special_needs_description: s.special_needs_description,
        transportation_method: s.transportation_method,
        passport_photo_url: s.passport_photo_url,
        is_new: s.is_new,
        has_special_needs: s.has_special_needs,
        created_at: s.created_at,
        updated_at: s.updated_at,
        is_active: s.is_active,

        first_name: u.first_name,
        last_name: u.last_name,
        email: Some(u.email),
        phone_number: u.phone_number,

        guardian_name,
        guardian_phone,
        guardian_email,
        guardian_relationship,
    };

    Ok(HttpResponse::Ok().json(resp))
}

#[derive(Debug, Deserialize, Clone)]
pub struct UpdateStudentRequest {
    pub admission_number: Option<String>,
    pub admission_date: Option<String>,
    pub graduation_date: Option<String>,
    pub academic_status: Option<String>,
    pub current_grade_level: Option<String>,
    pub date_of_birth: Option<String>,
    pub gender: Option<String>,
    pub previous_school: Option<String>,
    pub special_needs_description: Option<String>,
    pub transportation_method: Option<String>,
    pub passport_photo_url: Option<String>,
    pub has_special_needs: Option<bool>,
    pub is_active: Option<bool>,
    
    // User fields
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub phone_number: Option<String>,
}

pub async fn update_student(
    db: web::Data<Database>,
    path: web::Path<(uuid::Uuid,)>,
    payload: web::Json<UpdateStudentRequest>,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let p = payload.into_inner();

    // 1. Fetch existing student details
    let mut student = StudentService::get_student_by_id(&db, id).await?;

    // 2. Apply student table updates
    if let Some(ref adm_num) = p.admission_number {
        student.admission_number = Some(adm_num.clone());
    }
    if let Some(ref adm_date_str) = p.admission_date {
        if let Ok(parsed_date) = chrono::NaiveDate::parse_from_str(adm_date_str, "%Y-%m-%d") {
            student.admission_date = parsed_date;
        }
    }
    if let Some(ref grad_date_str) = p.graduation_date {
        student.graduation_date = chrono::NaiveDate::parse_from_str(grad_date_str, "%Y-%m-%d").ok();
    }
    if let Some(ref acad_status) = p.academic_status {
        student.academic_status = acad_status.clone();
    }
    if let Some(ref grade_level) = p.current_grade_level {
        student.current_grade_level = Some(grade_level.clone());
    }
    if let Some(ref dob_str) = p.date_of_birth {
        student.date_of_birth = chrono::NaiveDate::parse_from_str(dob_str, "%Y-%m-%d").ok();
    }
    if let Some(ref gen) = p.gender {
        student.gender = Some(gen.clone());
    }
    if let Some(ref prev_sch) = p.previous_school {
        student.previous_school = Some(prev_sch.clone());
    }
    if let Some(ref spec_needs) = p.special_needs_description {
        student.special_needs_description = Some(spec_needs.clone());
    }
    if let Some(ref trans_meth) = p.transportation_method {
        student.transportation_method = Some(trans_meth.clone());
    }
    if let Some(ref photo_url) = p.passport_photo_url {
        student.passport_photo_url = Some(photo_url.clone());
    }
    if let Some(has_spec) = p.has_special_needs {
        student.has_special_needs = has_spec;
    }
    if let Some(act) = p.is_active {
        student.is_active = act;
    }
    student.updated_at = chrono::Utc::now().naive_utc();

    // 3. Save student
    let updated_student = StudentService::update_student(&db, id, student).await?;

    // 4. Update associated user record — only first_name and last_name are editable.
    //    Email and phone are auto-generated for student accounts and must not change here.
    let mut user = crate::services::UserService::get_user(&db, updated_student.user_id).await?;
    let mut user_updated = false;

    if let Some(ref first) = p.first_name {
        let trimmed = first.trim().to_string();
        if !trimmed.is_empty() && user.first_name.as_deref() != Some(trimmed.as_str()) {
            user.first_name = Some(trimmed);
            user_updated = true;
        }
    }
    if let Some(ref last) = p.last_name {
        let trimmed = last.trim().to_string();
        if !trimmed.is_empty() && user.last_name.as_deref() != Some(trimmed.as_str()) {
            user.last_name = Some(trimmed);
            user_updated = true;
        }
    }

    if user_updated {
        user.updated_at = chrono::Utc::now();
        crate::services::UserService::update_user(&db, updated_student.user_id, user).await?;
    }

    Ok(HttpResponse::Ok().json(updated_student))
}

pub async fn delete_student(db: web::Data<Database>, path: web::Path<(uuid::Uuid,)>) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    StudentService::delete_student(&db, id).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

pub async fn assign_student_classes(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    payload: web::Json<AssignStudentClassesRequest>,
) -> Result<HttpResponse, ApiError> {
    let student_id = path.into_inner().0;
    let p = payload.into_inner();

    let assigned_count = StudentService::assign_classes_to_student(
        &db,
        student_id,
        p.school_id,
        p.classes,
    )
    .await?;

    Ok(HttpResponse::Ok().json(serde_json::json!({
        "status": "assigned",
        "student_id": student_id,
        "assigned_count": assigned_count
    })))
}

pub async fn delete_student_class_assignment(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let assignment_id = path.into_inner().0;
    StudentService::soft_delete_student_class_assignment(&db, assignment_id, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

#[derive(Debug, Deserialize)]
pub struct ListStudentsQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
    pub search: Option<String>,
    pub school_id: Uuid,
    pub track_id: Option<Uuid>,
    pub class_id: Option<Uuid>,
}

pub async fn list_students(
    db: web::Data<Database>,
    query: web::Query<ListStudentsQuery>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let page = q.page.unwrap_or(1);
    let per_page = q.per_page.unwrap_or(20);

    let res = StudentService::list_students(
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

pub async fn get_student_class_assignments(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
) -> Result<HttpResponse, ApiError> {
    let student_id = path.into_inner().0;
    let res = StudentService::get_student_class_assignments(&db, student_id).await?;
    Ok(HttpResponse::Ok().json(res))
}
