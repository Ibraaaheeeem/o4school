use chrono::{NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// SchoolClasses table - School classes/grades
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolClass {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub class_code: String,
    pub level: String,
    pub capacity: i32,
    pub current_strength: i32,
    pub class_teacher_id: Option<Uuid>,
    pub form_teacher_id: Option<Uuid>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Subjects table - Academic subjects
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Subject {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub code: String,
    pub description: Option<String>,
    pub credit_hours: i32,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// GlobalSubjects table - System-wide subjects
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct GlobalSubject {
    pub id: Uuid,
    pub name: String,
    pub code: String,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// AcademicSessions table - Academic years/sessions
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct AcademicSession {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub is_current: bool,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Terms table - School terms within academic session
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Term {
    pub id: Uuid,
    pub school_id: Uuid,
    pub session_id: Uuid,
    pub name: String,
    pub term_number: i32,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_current: bool,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// StudentClasses table - Student class enrollment
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentClass {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub class_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub enrollment_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// ClassSubjects table - Subject assignments to classes
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ClassSubject {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub subject_id: Uuid,
    pub staff_id: Option<Uuid>,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// ClassTeachers table - Class teacher assignments
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ClassTeacher {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub staff_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub assigned_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SubjectTeachers table - Subject teacher assignments
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SubjectTeacher {
    pub id: Uuid,
    pub school_id: Uuid,
    pub subject_id: Uuid,
    pub staff_id: Uuid,
    pub class_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub assigned_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}
