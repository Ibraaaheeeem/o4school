use chrono::{NaiveDate, NaiveDateTime};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// SchoolClasses table - School classes/grades
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolClass {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub track_id: Option<Uuid>,
    pub class_name: String,
    pub department_id: Option<Uuid>,
    pub class_code: Option<String>,
    pub classroom_location: Option<String>,
    pub current_enrollment: Option<i32>,
    pub grade_level: Option<i32>,
    pub max_capacity: Option<i32>,
    pub scoring_scheme: Option<String>,
    pub class_staff_id: Option<Uuid>,
    pub term: Option<String>,
}

/// Subjects table - Academic subjects
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Subject {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub subject_name: String,
    pub subject_code: Option<String>,
    pub description: Option<String>,
    pub credit_hours: Option<i32>,
    pub is_core_subject: Option<bool>,
    pub min_grade_level: i32,
    pub max_grade_level: i32,
    pub category: Option<String>,
    pub elearner_subject_id: Option<Uuid>,
}

/// AcademicSessions table - Academic years/sessions
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct AcademicSession {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
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
    pub academic_session_id: Uuid,
    pub term_id: Uuid,
    pub track_id: Uuid,
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
    pub school_subject_id: Option<Uuid>,
    pub staff_id: Option<Uuid>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolSubjects table - School-selected subjects from global subjects
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolSubject {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub subject_id: Uuid,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SchoolSubjectResponse {
    pub id: Uuid,
    pub subject_id: Uuid,
    pub name: String,
    pub code: Option<String>,
    pub description: Option<String>,
    pub linked_classes: Vec<LinkedClassResponse>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LinkedClassResponse {
    pub id: Uuid, // class_id
    pub name: String, // class_name
    pub department_name: Option<String>,
    pub track_name: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SchoolSubjectFilter {
    pub class_name: Option<String>,
    pub department_id: Option<Uuid>,
    pub track_id: Option<Uuid>,
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

/// Request payload for creating an academic session
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateAcademicSessionRequest {
    pub school_id: Uuid,
    pub name: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_current: bool,
}

/// Request payload for updating an academic session
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateAcademicSessionRequest {
    pub school_id: Uuid,
    pub name: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_current: bool,
}

/// Request payload for creating a term under an academic session
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TermStudentTransitionAction {
    Promote,
    Downgrade,
    Maintain,
}

/// Per-student transition instruction used when creating a new term
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateTermStudentTransitionRequest {
    pub student_id: Uuid,
    pub source_class_id: Uuid,
    pub action: TermStudentTransitionAction,
    pub next_applied_class_id: Option<Uuid>,
}

/// Request payload for creating a term under an academic session
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateTermRequest {
    pub school_id: Uuid,
    pub session_id: Uuid,
    pub name: String,
    pub term_number: i32,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_current: bool,
    pub source_term_id: Option<Uuid>,
    #[serde(default)]
    pub student_transitions: Vec<CreateTermStudentTransitionRequest>,
}

/// Request payload for updating a term
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateTermRequest {
    pub school_id: Uuid,
    pub session_id: Uuid,
    pub name: String,
    pub term_number: i32,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_current: bool,
}

/// Individual scoring component within a scoring scheme
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScoringComponent {
    pub id: i32,
    pub name: String,
    pub alias: String,
    pub max: i32,
}

/// ScoringSchemes table - Scoring/grading schemes per class and term
/// Each scoring scheme defines how grades are calculated for a specific class
/// e.g., CA I (20) + CA II (20) + Exam (60) = Total (100)
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ScoringScheme {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    #[sqlx(json)]
    pub scoring_scheme: Vec<ScoringComponent>,
    pub notes: Option<String>,
}

/// Request payload for creating a scoring scheme
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateScoringSchemeRequest {
    pub school_id: Uuid,
    #[serde(default)]
    pub class_ids: Vec<Uuid>,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    pub scoring_scheme: Vec<ScoringComponent>,
    pub notes: Option<String>,
}

/// Request payload for updating a scoring scheme
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateScoringSchemeRequest {
    pub scoring_scheme: Vec<ScoringComponent>,
}

#[cfg(test)]
mod tests {
    use super::{CreateTermRequest, TermStudentTransitionAction};
    use serde_json::json;
    use uuid::Uuid;

    #[test]
    fn term_student_transition_action_uses_screaming_snake_case() {
        let promote = serde_json::to_string(&TermStudentTransitionAction::Promote)
            .expect("promote should serialize");
        let downgrade = serde_json::to_string(&TermStudentTransitionAction::Downgrade)
            .expect("downgrade should serialize");
        let maintain = serde_json::to_string(&TermStudentTransitionAction::Maintain)
            .expect("maintain should serialize");

        assert_eq!(promote, "\"PROMOTE\"");
        assert_eq!(downgrade, "\"DOWNGRADE\"");
        assert_eq!(maintain, "\"MAINTAIN\"");
    }

    #[test]
    fn create_term_request_deserializes_student_transitions_for_all_actions() {
        let school_id = Uuid::new_v4();
        let session_id = Uuid::new_v4();
        let source_term_id = Uuid::new_v4();

        let student_1 = Uuid::new_v4();
        let student_2 = Uuid::new_v4();
        let student_3 = Uuid::new_v4();

        let source_class_1 = Uuid::new_v4();
        let source_class_2 = Uuid::new_v4();
        let source_class_3 = Uuid::new_v4();

        let next_class_1 = Uuid::new_v4();
        let next_class_3 = Uuid::new_v4();

        let payload = json!({
            "school_id": school_id,
            "session_id": session_id,
            "name": "Second Term",
            "term_number": 2,
            "start_date": "2026-09-01",
            "end_date": "2026-12-15",
            "is_current": false,
            "source_term_id": source_term_id,
            "student_transitions": [
                {
                    "student_id": student_1,
                    "source_class_id": source_class_1,
                    "action": "PROMOTE",
                    "next_applied_class_id": next_class_1
                },
                {
                    "student_id": student_2,
                    "source_class_id": source_class_2,
                    "action": "MAINTAIN",
                    "next_applied_class_id": null
                },
                {
                    "student_id": student_3,
                    "source_class_id": source_class_3,
                    "action": "DOWNGRADE",
                    "next_applied_class_id": next_class_3
                }
            ]
        });

        let request: CreateTermRequest =
            serde_json::from_value(payload).expect("payload should deserialize");

        assert_eq!(request.student_transitions.len(), 3);
        assert!(matches!(
            request.student_transitions[0].action,
            TermStudentTransitionAction::Promote
        ));
        assert!(matches!(
            request.student_transitions[1].action,
            TermStudentTransitionAction::Maintain
        ));
        assert!(matches!(
            request.student_transitions[2].action,
            TermStudentTransitionAction::Downgrade
        ));
    }

    #[test]
    fn create_term_request_defaults_student_transitions_to_empty() {
        let payload = json!({
            "school_id": Uuid::new_v4(),
            "session_id": Uuid::new_v4(),
            "name": "First Term",
            "term_number": 1,
            "start_date": "2026-01-01",
            "end_date": "2026-04-01",
            "is_current": true,
            "source_term_id": null
        });

        let request: CreateTermRequest =
            serde_json::from_value(payload).expect("payload should deserialize");

        assert!(request.student_transitions.is_empty());
    }
}
