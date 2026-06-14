use chrono::{NaiveDate, NaiveDateTime};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use super::academic::{ClassSubject, ScoringScheme};

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

/// Examinations table - Detailed examination/test records
/// Represents a specific examination scheduled for a class and subject
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Examination {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub created_by: Uuid,
    pub duration_minutes: Option<i32>,
    pub end_time: Option<NaiveDateTime>,
    pub exam_type: String,                    // e.g., "PRACTICAL", "THEORY", "HYBRID"
    pub is_published: Option<bool>,
    pub start_time: Option<NaiveDateTime>,
    pub title: String,
    pub total_marks: Option<i32>,
    pub class_id: Uuid,
    pub subject_id: Uuid,
    pub is_online: bool,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub questions_json: Option<String>,
}

/// Assessments table - Behavioral and practical assessments
/// Stores individual student assessments including attendance, conduct, and behavioral grades
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Assessment {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub track_id: Uuid,
    pub admission_number: String,
    pub attendance: i32,
    pub attentiveness: i32,
    pub class_teacher_comment: Option<String>,
    pub critical_thinking: Option<i32>,
    pub fluency: i32,
    pub game: i32,
    pub handwriting: i32,
    pub head_teacher_comment: Option<String>,
    pub initiative: i32,
    pub neatness: i32,
    pub politeness: i32,
    pub punctuality: i32,
    pub self_discipline: Option<i32>,
    pub student_id: Option<Uuid>,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
}

/// SubjectScores table - Subject-specific scores for students
/// Stores aggregate scores for a subject including grade and position
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SubjectScore {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub grade: Option<String>,
    pub position: Option<i32>,
    pub remark: Option<String>,
    pub scores_json: Option<String>,         // JSON object with detailed scores breakdown
    pub assessment_id: Uuid,
    pub class_subject_id: Uuid,
    pub subject_id: Uuid,
}

/// ExaminationSubmissions table - Student exam submission records
/// Tracks when students submit exams, their attempts, and scores
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ExaminationSubmission {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub attempt_count: Option<i32>,
    pub score: Option<f64>,
    pub started_at: Option<NaiveDateTime>,
    pub status: Option<String>,              // e.g., "PENDING", "SUBMITTED", "GRADED"
    pub submitted_at: Option<NaiveDateTime>,
    pub examination_id: Uuid,
    pub student_id: Uuid,
    pub answers_json: Option<String>,        // JSON object with student's answers
}

/// Request payload for creating examinations.
/// One request can create multiple examinations across classes and subjects.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateExaminationRequest {
    pub school_id: Uuid,
    pub created_by: Uuid,
    #[serde(default)]
    pub class_ids: Vec<Uuid>,
    #[serde(default)]
    pub subject_ids: Vec<Uuid>,
    pub duration_minutes: Option<i32>,
    pub end_time: Option<NaiveDateTime>,
    pub exam_type: String,
    pub is_published: Option<bool>,
    pub start_time: Option<NaiveDateTime>,
    pub title: String,
    pub total_marks: Option<i32>,
    pub is_online: bool,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub questions_json: Option<String>,
}

/// Request payload for updating examinations.
/// The request can target one or many examinations by ID.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateExaminationRequest {
    pub school_id: Uuid,
    #[serde(default)]
    pub examination_ids: Vec<Uuid>,
    pub duration_minutes: Option<i32>,
    pub end_time: Option<NaiveDateTime>,
    pub exam_type: Option<String>,
    pub is_published: Option<bool>,
    pub start_time: Option<NaiveDateTime>,
    pub title: Option<String>,
    pub total_marks: Option<i32>,
    pub is_online: Option<bool>,
    pub session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    pub questions_json: Option<String>,
}

/// Request payload for creating placeholder assessments for all students in classes.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateAssessmentRequest {
    pub school_id: Uuid,
    #[serde(default)]
    pub class_ids: Vec<Uuid>,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
}

/// Request payload for saving modified assessment data.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateAssessmentRequest {
    pub school_id: Uuid,
    pub assessment_id: Uuid,
    pub attendance: Option<i32>,
    pub attentiveness: Option<i32>,
    pub class_teacher_comment: Option<String>,
    pub critical_thinking: Option<i32>,
    pub fluency: Option<i32>,
    pub game: Option<i32>,
    pub handwriting: Option<i32>,
    pub head_teacher_comment: Option<String>,
    pub initiative: Option<i32>,
    pub neatness: Option<i32>,
    pub politeness: Option<i32>,
    pub punctuality: Option<i32>,
    pub self_discipline: Option<i32>,
}

/// Individual subject score payload for a class_subject.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveSubjectScoreItem {
    pub class_subject_id: Uuid,
    pub grade: Option<String>,
    pub position: Option<i32>,
    pub remark: Option<String>,
    pub scores_json: Option<serde_json::Value>,
}

/// Request payload for saving subject scores against an assessment.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveSubjectScoresRequest {
    pub school_id: Uuid,
    pub assessment_id: Uuid,
    #[serde(default)]
    pub subject_scores: Vec<SaveSubjectScoreItem>,
}

/// Response payload for class assessment context.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassAssessmentContextResponse {
    pub class_subjects: Vec<ClassSubject>,
    pub scoring_scheme: Option<ScoringScheme>,
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
