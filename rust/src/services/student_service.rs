use uuid::Uuid;
use chrono::{Utc, NaiveDate};
use crate::db::Database;
use crate::db::repositories::StudentRepository;
use crate::errors::ApiError;
use crate::models::Student;

pub struct StudentService;

impl StudentService {
    /// Get student by ID
    pub async fn get_student(db: &Database, student_id: Uuid) -> Result<Student, ApiError> {
        StudentRepository::get_by_id(db.pool(), student_id).await
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
}
