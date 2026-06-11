use chrono::{NaiveDate, NaiveDateTime};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Schools table - Multi-tenant schools
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct School {
    pub id: Uuid,
    pub name: String,
    pub slug: String,
    pub address_line1: String,
    pub address_line2: Option<String>,
    pub city: String,
    pub state: String,
    pub postal_code: Option<String>,
    pub country: String,
    pub status: Option<String>,
    pub timezone: Option<String>,
    pub currency: Option<String>,
    pub language: Option<String>,
    pub website: Option<String>,
    pub admin_name: String,
    pub admin_email: String,
    pub admin_phone: String,
    pub banner_url: Option<String>,
    pub logo_url: Option<String>,
    pub primary_color: Option<String>,
    pub secondary_color: Option<String>,
    pub school_motto: Option<String>,
    pub admission_prefix: Option<String>,
    pub staff_id_prefix: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Departments table - School departments
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Department {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub description: Option<String>,
    pub name: String,
    pub track_id: Option<Uuid>,
}

/// Designations table - Job designations
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Designation {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub code: String,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolCalendar table - School event calendars
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolCalendar {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub color: Option<String>,
    pub description: Option<String>,
    pub end_date: Option<NaiveDate>,
    pub event_name: String,
    pub event_type: String,
    pub is_exam_period: Option<bool>,
    pub is_holiday: Option<bool>,
    pub start_date: NaiveDate,
    pub session_id: Uuid,
    pub term_id: Option<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateCalendarEventRequest {
    pub school_id: Uuid,
    pub session_id: Uuid,
    pub event_name: String,
    pub event_type: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub color: Option<String>,
    pub description: Option<String>,
    pub is_exam_period: Option<bool>,
    pub is_holiday: Option<bool>,
    pub term_id: Option<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateCalendarEventRequest {
    pub school_id: Uuid,
    pub session_id: Uuid,
    pub event_name: String,
    pub event_type: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub color: Option<String>,
    pub description: Option<String>,
    pub is_exam_period: Option<bool>,
    pub is_holiday: Option<bool>,
    pub term_id: Option<Uuid>,
}

/// SchoolTimetables table - School timetables
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolTimetable {
    pub day_of_week: String,
    pub activity_type: String,
    pub start_time: String,
    pub end_time: String,
    pub title: String,
    pub description: Option<String>,
    pub school_id: Uuid,
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
    pub class_id: Option<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateSchoolTimetableRequest {
    pub school_id: Uuid,
    pub class_id: Option<Uuid>,
    pub day_of_week: Option<String>,
    pub days_of_week: Option<Vec<String>>,
    pub activity_type: String,
    pub start_time: String,
    pub end_time: String,
    pub title: String,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateSchoolTimetableRequest {
    pub school_id: Uuid,
    pub class_id: Option<Uuid>,
    pub day_of_week: String,
    pub activity_type: String,
    pub start_time: String,
    pub end_time: String,
    pub title: String,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimetableSuccessResponse<T> {
    pub success: bool,
    pub message: String,
    pub data: T,
}

/// EducationTracks table - Education tracking
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct EducationTrack {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub description: Option<String>,
    pub name: String,
}

/// SubjectMappings table - Subject mappings across tracks/grades
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SubjectMapping {
    pub id: Uuid,
    pub school_id: Uuid,
    pub subject_id: Uuid,
    pub education_track_id: Uuid,
    pub grade_level: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateSchoolDataRequest {
    pub name: Option<String>,
    pub slug: Option<String>,
    pub address_line1: Option<String>,
    pub address_line2: Option<String>,
    pub admin_email: Option<String>,
    pub admin_name: Option<String>,
    pub admin_phone: Option<String>,
    pub banner_url: Option<String>,
    pub city: Option<String>,
    pub country: Option<String>,
    pub currency: Option<String>,
    pub language: Option<String>,
    pub logo_url: Option<String>,
    pub primary_color: Option<String>,
    pub school_motto: Option<String>,
    pub secondary_color: Option<String>,
    pub state: Option<String>,
    pub status: Option<String>,
    pub timezone: Option<String>,
    pub website: Option<String>,
    pub admission_prefix: Option<String>,
    pub staff_id_prefix: Option<String>,
    pub postal_code: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateEducationTrackRequest {
    pub school_id: Uuid,
    pub name: String,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateDepartmentRequest {
    pub school_id: Uuid,
    pub track_id: Uuid,
    pub name: String,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateClassRequest {
    pub school_id: Uuid,
    pub department_id: Uuid,
    pub class_name: String,
    pub class_code: Option<String>,
    pub classroom_location: Option<String>,
    pub current_enrollment: Option<i32>,
    pub grade_level: Option<i32>,
    pub max_capacity: Option<i32>,
    pub scoring_scheme: Option<String>,
    pub class_staff_id: Option<Uuid>,
    pub term: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaveSchoolSubjectsRequest {
    pub school_id: Uuid,
    #[serde(default)]
    pub subject_ids: Vec<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LinkSubjectClassesRequest {
    pub school_id: Uuid,
    pub school_subject_id: Uuid,
    #[serde(default)]
    pub class_ids: Vec<Uuid>,
    pub staff_id: Option<Uuid>,
    pub assigned_by: Option<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LinkSchoolSubjectClassesRequest {
    pub school_id: Uuid,
    #[serde(default)]
    pub class_ids: Vec<Uuid>,
    pub staff_id: Option<Uuid>,
    pub assigned_by: Option<Uuid>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InitializeDefaultStructureRequest {
    pub school_id: Uuid,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InitializeDefaultStructureResponse {
    pub school_id: Uuid,
    pub created: bool,
    pub message: String,
    pub track_id: Option<Uuid>,
    pub department_id: Option<Uuid>,
    pub class_id: Option<Uuid>,
    pub class_subjects_created: i64,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AcademicStructureResponse {
    pub school_id: Uuid,
    pub tracks: Vec<AcademicTrackNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AcademicTrackNode {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub departments: Vec<DepartmentNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DepartmentNode {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub classes: Vec<ClassNode>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassNode {
    pub id: Uuid,
    pub class_name: String,
    pub class_code: Option<String>,
    pub grade_level: Option<i32>,
}
