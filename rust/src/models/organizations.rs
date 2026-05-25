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
    pub school_id: Uuid,
    pub name: String,
    pub code: String,
    pub head_id: Option<Uuid>,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
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

/// SchoolCalendars table - School event calendars
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolCalendar {
    pub id: Uuid,
    pub school_id: Uuid,
    pub event_name: String,
    pub event_date: NaiveDate,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolTimetables table - School timetables
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolTimetable {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub day_of_week: String,
    pub start_time: String,
    pub end_time: String,
    pub subject_id: Option<Uuid>,
    pub staff_id: Option<Uuid>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// EducationTracks table - Education tracking
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct EducationTrack {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
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
