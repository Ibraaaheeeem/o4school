use chrono::{NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Attendance table - Student attendance records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Attendance {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub staff_id: Option<Uuid>,
    pub class_id: Option<Uuid>,
    pub attendance_date: NaiveDate,
    pub status: String,
    pub remarks: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Assessments table - Class assessments
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Assessment {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub subject_id: Uuid,
    pub staff_id: Uuid,
    pub name: String,
    pub assessment_type: String,
    pub total_score: f64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Exams table - Examinations
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Exam {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub subject_id: Uuid,
    pub staff_id: Uuid,
    pub term_id: Uuid,
    pub name: String,
    pub exam_date: NaiveDate,
    pub total_score: f64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// ExamResults table - Exam results for students
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ExamResult {
    pub id: Uuid,
    pub school_id: Uuid,
    pub exam_id: Uuid,
    pub student_id: Uuid,
    pub graded_by_staff_id: Option<Uuid>,
    pub score: f64,
    pub grade: Option<String>,
    pub remarks: Option<String>,
    pub submitted_at: Option<NaiveDateTime>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SubjectScores table - Subject scores for students
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SubjectScore {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub subject_id: Uuid,
    pub term_id: Uuid,
    pub session_id: Uuid,
    pub scores: Option<String>, // JSON storage of scores
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Questions table - Exam questions
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Question {
    pub id: Uuid,
    pub school_id: Uuid,
    pub exam_id: Uuid,
    pub question_text: String,
    pub question_type: String,
    pub points: f64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Examinations table - General examination info
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Examination {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// ExaminationSubmissions table - Exam submission records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ExaminationSubmission {
    pub id: Uuid,
    pub school_id: Uuid,
    pub exam_id: Uuid,
    pub student_id: Uuid,
    pub submitted_at: NaiveDateTime,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// StudentFeedback table - Student feedback/comments
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentFeedback {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub staff_id: Uuid,
    pub feedback_text: String,
    pub rating: Option<i32>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}
