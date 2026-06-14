use uuid::Uuid;
use chrono::{Utc, NaiveDate};
use crate::db::Database;
use crate::db::repositories::StudentRepository;
use crate::errors::ApiError;
use crate::models::Student;
use crate::models::auth::CreateStudentClassInfo;
use sqlx::Postgres;
use crate::models::auth::CreateUserInfo;
use crate::models::auth::CreateStudentInfo;
use crate::models::users::User;
use crate::services::UserService;

pub struct StudentService;

impl StudentService {
    async fn ensure_school_admin(db: &Database, actor: Uuid, school_id: Uuid) -> Result<(), ApiError> {
        let school_admin_role_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT id FROM roles WHERE name = 'SCHOOL_ADMIN' AND is_active = true"
        )
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::DatabaseError("SCHOOL_ADMIN role not found".to_string()))?;

        let is_admin = crate::db::repositories::UserSchoolRoleRepository::exists(
            db.pool(),
            actor,
            school_id,
            school_admin_role_id,
        )
        .await?;

        if !is_admin {
            return Err(ApiError::Unauthorized(
                "Caller is not a SCHOOL_ADMIN for this school".to_string(),
            ));
        }

        Ok(())
    }

    /// Create a student and optionally assign to classes atomically
    pub async fn create_student_with_classes(
        db: &Database,
        student: Student,
        student_classes: Option<Vec<CreateStudentClassInfo>>,
    ) -> Result<Student, ApiError> {
        // Begin a transaction and use repository executor-capable helpers
        // Delegate to repository which will perform a transactional create using a single acquired connection
        let created = StudentRepository::create_with_classes(db.pool(), &student, student_classes).await?;

        Ok(created)
    }

    /// Create or lookup user, then create student and optional class enrollments
    pub async fn create_student_with_user(
        db: &Database,
        user_info: CreateUserInfo,
        student_info: CreateStudentInfo,
        student_classes: Option<Vec<CreateStudentClassInfo>>,
    ) -> Result<Student, ApiError> {
        // Fetch school details to get dynamic school slug and city variables
        let school = crate::db::repositories::SchoolRepository::get_by_id(db.pool(), student_info.school_id).await?;
        let school_slug = school.slug.trim().to_lowercase().replace(' ', "");
        let school_city = {
            let city = school.city.trim().to_lowercase().replace(' ', "");
            if city.is_empty() {
                "com".to_string()
            } else {
                city
            }
        };

        // Resolve student_id
        let resolved_student_id = student_info.student_id.clone().unwrap_or_else(|| {
            format!("STU-{}", uuid::Uuid::new_v4().to_string().split('-').next().unwrap_or("0000"))
        });

        // Resolve admission number or student ID
        let admission_number_or_id = if let Some(ref adm) = student_info.admission_number {
            let adm: &String = adm;
            if adm.trim().is_empty() {
                resolved_student_id.clone()
            } else {
                adm.trim().to_string()
            }
        } else {
            resolved_student_id.clone()
        };

        // Generate student email: firstname+lastname+admission_number@{school_slug}.{school_city}
        let name_part = format!("{}{}", user_info.first_name.trim(), user_info.last_name.trim())
            .to_lowercase()
            .replace(' ', "");
        let id_part = admission_number_or_id.to_lowercase().replace(' ', "");
        let email = format!("{}{}@{}.{}", name_part, id_part, school_slug, school_city);

        // Resolve phone number: if missing or empty, use admission_number_or_id
        let mut phone_number = user_info.phone_number.clone();
        if phone_number.as_ref().map(|p: &String| p.trim().is_empty()).unwrap_or(true) {
            phone_number = Some(admission_number_or_id.clone());
        }

        // try to find existing user by email
        let user = match crate::db::repositories::user_repository::UserRepository::get_by_email(db.pool(), &email).await {
            Ok(u) => u,
            Err(_) => {
                // create new user record
                let new_user = User {
                    id: Uuid::new_v4(),
                    email: email.clone(),
                    phone_number: phone_number.clone(),
                    phone_country_code: None,
                    password_hash: None,
                    first_name: Some(user_info.first_name.clone()),
                    last_name: Some(user_info.last_name.clone()),
                    middle_name: user_info.middle_name.clone(),
                    date_of_birth: user_info.date_of_birth,
                    gender: user_info.gender.clone(),
                    profile_picture_url: None,
                    address_line1: None,
                    address_line2: None,
                    city: None,
                    state: None,
                    postal_code: None,
                    country: "Unknown".to_string(),
                    status: "ACTIVE".to_string(),
                    is_verified: false,
                    is_approved: Some(false),
                    verified_at: None,
                    approved_at: None,
                    approved_by: None,
                    last_login_at: None,
                    otp_code: None,
                    otp_expires: None,
                    last_otp_sent: None,
                    created_at: chrono::Utc::now(),
                    updated_at: chrono::Utc::now(),
                    is_active: true,
                };

                // create user
                match UserService::create_user(db, new_user).await {
                    Ok(u) => u,
                    Err(e) => return Err(e),
                }
            }
        };

        // build Student from CreateStudentInfo
        let student = Student {
            id: Uuid::new_v4(),
            school_id: student_info.school_id,
            user_id: user.id,
            student_id: resolved_student_id,
            admission_number: student_info.admission_number.clone(),
            admission_date: student_info.admission_date.unwrap_or_else(|| chrono::Utc::now().date_naive()),
            graduation_date: student_info.graduation_date,
            academic_status: student_info.academic_status.unwrap_or_else(|| "ENROLLED".to_string()),
            current_grade_level: student_info.current_grade_level.clone(),
            date_of_birth: student_info.date_of_birth,
            gender: student_info.gender.as_ref().map(|g: &String| match g.trim().to_uppercase().as_str() {
                "M" | "MALE" => "MALE".to_string(),
                "F" | "FEMALE" => "FEMALE".to_string(),
                _ => "MALE".to_string(),
            }),
            previous_school: student_info.previous_school.clone(),
            special_needs_description: student_info.special_needs_description.clone(),
            transportation_method: student_info.transportation_method.clone(),
            passport_photo_url: student_info.passport_photo_url.clone(),
            is_new: true,
            has_special_needs: student_info.has_special_needs.unwrap_or(false),
            created_at: Utc::now().naive_utc(),
            updated_at: Utc::now().naive_utc(),
            is_active: true,
        };

        // call repository transactional create_with_classes
        let created = StudentRepository::create_with_classes(db.pool(), &student, student_classes).await?;

        Ok(created)
    }

    pub async fn create_student(db: &Database, payload: Student) -> Result<Student, ApiError> {
        StudentRepository::create(db.pool(), &payload).await
    }

    /// Get student by ID
    pub async fn get_student(db: &Database, student_id: Uuid) -> Result<Student, ApiError> {
        StudentRepository::get_by_id(db.pool(), student_id).await
    }

    pub async fn get_student_by_id(db: &Database, id: Uuid) -> Result<Student, ApiError> {
        StudentRepository::get_by_id(db.pool(), id).await
    }

    /// Get student by user ID
    pub async fn get_student_by_user_id(db: &Database, user_id: Uuid) -> Result<Student, ApiError> {
        StudentRepository::get_by_user_id(db.pool(), user_id).await
    }

    /// List all students in a school with pagination
    pub async fn list_students_by_school(
        db: &Database,
        school_id: Uuid,
        limit: i64,
        offset: i64,
    ) -> Result<Vec<Student>, ApiError> {
        if limit > 100 {
            return Err(ApiError::BadRequest("Limit cannot exceed 100".to_string()));
        }
        StudentRepository::get_by_school(db.pool(), school_id, limit, offset).await
    }

    /// Enroll a student
    pub async fn enroll_student(
        db: &Database,
        school_id: Uuid,
        user_id: Uuid,
        admission_date: NaiveDate,
        current_grade_level: Option<String>,
    ) -> Result<Student, ApiError> {
        let student = Student {
            id: Uuid::new_v4(),
            school_id,
            user_id,
            student_id: format!("STU-{}", Uuid::new_v4().to_string().split('-').next().unwrap_or("0000")),
            admission_number: None,
            admission_date,
            graduation_date: None,
            academic_status: "ENROLLED".to_string(),
            current_grade_level,
            date_of_birth: None,
            gender: None,
            previous_school: None,
            special_needs_description: None,
            transportation_method: None,
            passport_photo_url: None,
            is_new: true,
            has_special_needs: false,
            created_at: Utc::now().naive_utc(),
            updated_at: Utc::now().naive_utc(),
            is_active: true,
        };

        StudentRepository::create(db.pool(), &student).await
    }

    /// Update student information
    pub async fn update_student(db: &Database, student_id: Uuid, updates: Student) -> Result<Student, ApiError> {
        // Verify student exists
        StudentRepository::get_by_id(db.pool(), student_id).await?;

        StudentRepository::update(db.pool(), student_id, &updates).await
    }

    /// Graduate a student
    pub async fn graduate_student(db: &Database, student_id: Uuid, graduation_date: NaiveDate) -> Result<Student, ApiError> {
        let mut student = StudentRepository::get_by_id(db.pool(), student_id).await?;
        
        student.academic_status = "GRADUATED".to_string();
        student.graduation_date = Some(graduation_date);
        student.updated_at = Utc::now().naive_utc();

        StudentRepository::update(db.pool(), student_id, &student).await
    }

    /// Suspend a student
    pub async fn suspend_student(db: &Database, student_id: Uuid) -> Result<Student, ApiError> {
        let mut student = StudentRepository::get_by_id(db.pool(), student_id).await?;
        
        student.academic_status = "SUSPENDED".to_string();
        student.updated_at = Utc::now().naive_utc();

        StudentRepository::update(db.pool(), student_id, &student).await
    }

    /// Delete student (soft delete)
    pub async fn delete_student(db: &Database, student_id: Uuid) -> Result<(), ApiError> {
        StudentRepository::delete(db.pool(), student_id).await
    }

    /// Assign one or more classes to a student with one-class-per-track enforcement per session/term
    pub async fn assign_classes_to_student(
        db: &Database,
        student_id: Uuid,
        school_id: Uuid,
        classes: Vec<CreateStudentClassInfo>,
    ) -> Result<usize, ApiError> {
        if classes.is_empty() {
            return Err(ApiError::ValidationError("At least one class is required".to_string()));
        }

        let student_school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM students WHERE id = $1 AND is_active = true",
        )
        .bind(student_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Student not found".to_string()))?;

        if student_school_id != school_id {
            return Err(ApiError::ValidationError(
                "Student does not belong to the provided school".to_string(),
            ));
        }

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let mut assigned_count = 0usize;

        for sc in classes.into_iter() {
            let class_track = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
                "SELECT track_id FROM classes WHERE id = $1 AND school_id = $2 AND is_active = true",
            )
            .bind(sc.class_id)
            .bind(school_id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
            .ok_or_else(|| {
                ApiError::ValidationError(format!(
                    "Class {} not found for provided school",
                    sc.class_id
                ))
            })?;

            sqlx::query(
                "INSERT INTO student_classes (id, school_id, student_id, class_id, academic_session_id, term_id, track_id, enrollment_date, created_at, updated_at, is_active) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,NOW(),NOW(),true) ON CONFLICT (student_id, track_id, academic_session_id, term_id) DO UPDATE SET class_id = EXCLUDED.class_id, school_id = EXCLUDED.school_id, enrollment_date = EXCLUDED.enrollment_date, updated_at = NOW(), is_active = true",
            )
            .bind(Uuid::new_v4())
            .bind(school_id)
            .bind(student_id)
            .bind(sc.class_id)
            .bind(sc.session_id)
            .bind(sc.term_id)
            .bind(class_track)
            .bind(sc.enrollment_date.unwrap_or_else(|| chrono::Utc::now().date_naive()))
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

            assigned_count += 1;
        }

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(assigned_count)
    }

    pub async fn soft_delete_student_class_assignment(
        db: &Database,
        assignment_id: Uuid,
        performed_by: Option<Uuid>,
    ) -> Result<(), ApiError> {
        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM student_classes WHERE id = $1 AND is_active = true"
        )
        .bind(assignment_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Student-class assignment not found".to_string()))?;

        if let Some(actor) = performed_by {
            Self::ensure_school_admin(db, actor, school_id).await?;
        }

        sqlx::query("UPDATE student_classes SET is_active = false, updated_at = NOW() WHERE id = $1")
            .bind(assignment_id)
            .execute(db.pool())
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// List/Search students
    pub async fn list_students(
        db: &Database,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        class_id: Option<Uuid>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::StudentListResponse>, ApiError> {
        StudentRepository::list_students(db.pool(), school_id, page, per_page, search, track_id, class_id).await
    }

    pub async fn get_student_class_assignments(
        db: &Database,
        student_id: Uuid,
    ) -> Result<Vec<crate::models::StudentClassAssignmentResponse>, ApiError> {
        let rows = sqlx::query_as::<Postgres, crate::models::StudentClassAssignmentResponse>(
            r#"
            SELECT 
                sc.id,
                sc.school_id,
                sc.student_id,
                sc.class_id,
                c.class_name,
                sc.academic_session_id as session_id,
                sc.term_id,
                sc.enrollment_date,
                sc.is_active
            FROM student_classes sc
            JOIN classes c ON sc.class_id = c.id
            JOIN academic_sessions asess ON sc.academic_session_id = asess.id
            JOIN terms t ON sc.term_id = t.id
            WHERE sc.student_id = $1 
              AND sc.is_active = true 
              AND asess.is_current_session = true 
              AND t.is_current_term = true
            "#
        )
        .bind(student_id)
        .fetch_all(db.pool())
        .await
        .map_err(|e: sqlx::Error| ApiError::DatabaseError(e.to_string()))?;

        Ok(rows)
    }
}
