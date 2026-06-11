// ============================================================================
// DASHBOARD SERVICE
// ============================================================================
// Aggregates role-scoped data for the authenticated user's dashboard.

use chrono::NaiveDate;
use serde::Serialize;
use sqlx::PgPool;
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;

// ============================================================================
// Response types
// ============================================================================

#[derive(Debug, Serialize)]
pub struct DashboardResponse {
    pub user_id: Uuid,
    pub school_id: Uuid,
    pub school_name: String,
    pub role: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub admin_overview: Option<AdminOverview>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub staff_overview: Option<StaffOverview>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub parent_overview: Option<ParentOverview>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub student_overview: Option<StudentOverview>,
    pub financial_health: FinancialHealth,
    pub critical_alerts: Vec<CriticalAlert>,
    pub upcoming_events: Vec<CalendarEventSummary>,
}

/// Dashboard data for SCHOOL_ADMIN / ADMIN roles
#[derive(Debug, Serialize)]
pub struct AdminOverview {
    pub total_students: i64,
    pub total_staff: i64,
    pub total_parents: i64,
    pub active_sessions: i64,
    pub pending_activations: i64,
    pub total_fee_items: i64,
    pub total_settlements: f64,
}

/// Dashboard data for STAFF role
#[derive(Debug, Serialize)]
pub struct StaffOverview {
    pub staff_record_id: String,
    pub designation: String,
    pub employment_status: String,
    pub is_class_teacher: bool,
    pub assigned_classes: Vec<ClassInfo>,
    pub assigned_subjects: Vec<SubjectInfo>,
}

/// Dashboard data for PARENT role
#[derive(Debug, Serialize)]
pub struct ParentOverview {
    pub children: Vec<ChildInfo>,
}

/// Dashboard data for STUDENT role
#[derive(Debug, Serialize)]
pub struct StudentOverview {
    pub student_record_id: String,
    pub academic_status: String,
    pub current_grade_level: Option<String>,
    pub current_classes: Vec<ClassInfo>,
}

#[derive(Debug, Serialize)]
pub struct ClassInfo {
    pub id: Uuid,
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct SubjectInfo {
    pub id: Uuid,
    pub name: String,
    pub code: String,
}

#[derive(Debug, Serialize)]
pub struct ChildInfo {
    pub student_id: Uuid,
    pub student_record_id: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub current_grade_level: Option<String>,
    pub academic_status: String,
    pub relationship: String,
}

#[derive(Debug, Serialize)]
pub struct CalendarEventSummary {
    pub id: Uuid,
    pub event_name: String,
    pub event_type: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub is_holiday: Option<bool>,
    pub is_exam_period: Option<bool>,
}

#[derive(Debug, Serialize)]
pub struct FinancialHealth {
    pub total_outstanding_fees: Option<f64>,
    pub collection_rate_percent: Option<f64>,
    pub monthly_revenue: Option<f64>,
    pub monthly_expense: Option<f64>,
    pub net_cash_flow: Option<f64>,
    pub last_updated: String,
}

impl FinancialHealth {
    fn blank() -> Self {
        Self {
            total_outstanding_fees: None,
            collection_rate_percent: None,
            monthly_revenue: None,
            monthly_expense: None,
            net_cash_flow: None,
            last_updated: String::new(),
        }
    }
}

#[derive(Debug, Serialize)]
pub struct CriticalAlert {
    pub alert_type: String,
    pub severity: String,
    pub title: String,
    pub description: String,
    pub affected_count: Option<i64>,
    pub action_required: String,
    pub created_at: String,
}

impl CriticalAlert {
    fn blank() -> Self {
        Self {
            alert_type: String::new(),
            severity: String::new(),
            title: String::new(),
            description: String::new(),
            affected_count: None,
            action_required: String::new(),
            created_at: String::new(),
        }
    }
}

// ============================================================================
// Internal row helpers (not part of public API)
// ============================================================================

#[derive(sqlx::FromRow)]
struct CalendarEventRow {
    id: Uuid,
    event_name: String,
    event_type: String,
    start_date: NaiveDate,
    end_date: Option<NaiveDate>,
    is_holiday: Option<bool>,
    is_exam_period: Option<bool>,
}

#[derive(sqlx::FromRow)]
struct ClassInfoRow {
    id: Uuid,
    name: String,
}

#[derive(sqlx::FromRow)]
struct SubjectInfoRow {
    id: Uuid,
    name: String,
    code: String,
}

// ============================================================================
// Service
// ============================================================================

pub struct DashboardService;

impl DashboardService {
    /// Build role-scoped dashboard data for the given user.
    ///
    /// `school_id` is optional; if omitted the user's first active school is used.
    pub async fn get_dashboard(
        db: &Database,
        user_id: Uuid,
        school_id: Option<Uuid>,
    ) -> Result<DashboardResponse, ApiError> {
        let pool = db.pool();

        // ---- resolve school -------------------------------------------------
        let school_id = match school_id {
            Some(sid) => sid,
            None => {
                sqlx::query_scalar::<sqlx::Postgres, Uuid>(
                    "SELECT school_id FROM user_school_roles \
                     WHERE user_id = $1 AND is_active = true \
                     ORDER BY created_at ASC LIMIT 1",
                )
                .bind(user_id)
                .fetch_optional(pool)
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?
                .ok_or_else(|| {
                    ApiError::Unauthorized(
                        "User does not belong to any school".to_string(),
                    )
                })?
            }
        };

        // ---- verify membership + get role name -----------------------------
        let role_name = sqlx::query_scalar::<sqlx::Postgres, String>(
            "SELECT r.name \
             FROM user_school_roles usr \
             JOIN roles r ON r.id = usr.role_id \
             WHERE usr.user_id = $1 AND usr.school_id = $2 AND usr.is_active = true \
             LIMIT 1",
        )
        .bind(user_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| {
            ApiError::Unauthorized(
                "User does not have a role in the specified school".to_string(),
            )
        })?;

        // ---- school name ---------------------------------------------------
        let school_name = sqlx::query_scalar::<sqlx::Postgres, String>(
            "SELECT name FROM schools WHERE id = $1",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // ---- upcoming calendar events (next 5) -----------------------------
        let upcoming_events: Vec<CalendarEventSummary> =
            sqlx::query_as::<sqlx::Postgres, CalendarEventRow>(
                "SELECT id, event_name, event_type, start_date, end_date, \
                        is_holiday, is_exam_period \
                 FROM school_calendar \
                 WHERE school_id = $1 AND start_date >= CURRENT_DATE AND is_active = true \
                 ORDER BY start_date ASC \
                 LIMIT 5",
            )
            .bind(school_id)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
            .into_iter()
            .map(|r| CalendarEventSummary {
                id: r.id,
                event_name: r.event_name,
                event_type: r.event_type,
                start_date: r.start_date,
                end_date: r.end_date,
                is_holiday: r.is_holiday,
                is_exam_period: r.is_exam_period,
            })
            .collect();

        // ---- role-specific data -------------------------------------------
        let (admin_overview, staff_overview, parent_overview, student_overview) =
            match role_name.as_str() {
                "SCHOOL_ADMIN" | "ADMIN" => {
                    let ov = Self::admin_overview(pool, school_id).await?;
                    (Some(ov), None, None, None)
                }
                "STAFF" => {
                    let ov = Self::staff_overview(pool, school_id, user_id).await?;
                    (None, Some(ov), None, None)
                }
                "PARENT" => {
                    let ov = Self::parent_overview(pool, school_id, user_id).await?;
                    (None, None, Some(ov), None)
                }
                "STUDENT" => {
                    let ov = Self::student_overview(pool, school_id, user_id).await?;
                    (None, None, None, Some(ov))
                }
                _ => (None, None, None, None),
            };

        Ok(DashboardResponse {
            user_id,
            school_id,
            school_name,
            role: role_name,
            admin_overview,
            staff_overview,
            parent_overview,
            student_overview,
            financial_health: FinancialHealth::blank(),
            critical_alerts: vec![CriticalAlert::blank()],
            upcoming_events,
        })
    }

    // -------------------------------------------------------------------------
    // Admin overview
    // -------------------------------------------------------------------------
    async fn admin_overview(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<AdminOverview, ApiError> {
        let total_students = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(*) FROM students WHERE school_id = $1 AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let total_staff = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(*) FROM staff WHERE school_id = $1 AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let total_parents = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(*) FROM parents WHERE school_id = $1 AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let active_sessions = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(*) FROM academic_sessions WHERE school_id = $1 AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // users in this school whose account is not yet ACTIVE
        let pending_activations = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(DISTINCT u.id) \
             FROM users u \
             JOIN user_school_roles usr ON usr.user_id = u.id AND usr.school_id = $1 \
             WHERE u.status != 'ACTIVE' AND u.is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let total_fee_items = sqlx::query_scalar::<sqlx::Postgres, i64>(
            "SELECT COUNT(*) FROM fee_items WHERE school_id = $1 AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let total_settlements = sqlx::query_scalar::<sqlx::Postgres, f64>(
            "SELECT COALESCE(SUM(amount), 0.0)::FLOAT8 \
             FROM settlements \
             WHERE school_id = $1 AND status = 'success' AND is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(AdminOverview {
            total_students,
            total_staff,
            total_parents,
            active_sessions,
            pending_activations,
            total_fee_items,
            total_settlements,
        })
    }

    // -------------------------------------------------------------------------
    // Staff overview
    // -------------------------------------------------------------------------
    async fn staff_overview(
        pool: &PgPool,
        school_id: Uuid,
        user_id: Uuid,
    ) -> Result<StaffOverview, ApiError> {
        #[derive(sqlx::FromRow)]
        struct StaffRow {
            id: Uuid,
            staff_id: String,
            designation: String,
            employment_status: Option<String>,
            is_class_teacher: Option<bool>,
        }

        let row = sqlx::query_as::<sqlx::Postgres, StaffRow>(
            "SELECT id, staff_id, designation, employment_status, is_class_teacher \
             FROM staff \
             WHERE user_id = $1 AND school_id = $2 AND is_active = true \
             LIMIT 1",
        )
        .bind(user_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let (staff_record_id, designation, employment_status, is_class_teacher, staff_db_id) =
            match row {
                Some(r) => (
                    r.staff_id,
                    r.designation,
                    r.employment_status.unwrap_or_else(|| "ACTIVE".to_string()),
                    r.is_class_teacher.unwrap_or(false),
                    Some(r.id),
                ),
                None => (
                    "N/A".to_string(),
                    "N/A".to_string(),
                    "N/A".to_string(),
                    false,
                    None,
                ),
            };

        let assigned_classes = if let Some(sid) = staff_db_id {
            sqlx::query_as::<sqlx::Postgres, ClassInfoRow>(
                "SELECT DISTINCT c.id, c.class_name AS name \
                 FROM class_teachers ct \
                 JOIN classes c ON c.id = ct.class_id AND c.is_active = true \
                 WHERE ct.staff_id = $1 AND ct.school_id = $2 AND ct.is_active = true",
            )
            .bind(sid)
            .bind(school_id)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
            .into_iter()
            .map(|r| ClassInfo { id: r.id, name: r.name })
            .collect()
        } else {
            vec![]
        };

        let assigned_subjects = if let Some(sid) = staff_db_id {
            sqlx::query_as::<sqlx::Postgres, SubjectInfoRow>(
                "SELECT DISTINCT s.id, s.subject_name AS name, s.subject_code AS code \
                 FROM subject_teachers st \
                 JOIN subjects s ON s.id = st.subject_id AND s.is_active = true \
                 WHERE st.staff_id = $1 AND st.school_id = $2 AND st.is_active = true",
            )
            .bind(sid)
            .bind(school_id)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
            .into_iter()
            .map(|r| SubjectInfo {
                id: r.id,
                name: r.name,
                code: r.code,
            })
            .collect()
        } else {
            vec![]
        };

        Ok(StaffOverview {
            staff_record_id,
            designation,
            employment_status,
            is_class_teacher,
            assigned_classes,
            assigned_subjects,
        })
    }

    // -------------------------------------------------------------------------
    // Parent overview
    // -------------------------------------------------------------------------
    async fn parent_overview(
        pool: &PgPool,
        school_id: Uuid,
        user_id: Uuid,
    ) -> Result<ParentOverview, ApiError> {
        #[derive(sqlx::FromRow)]
        struct ParentRow {
            id: Uuid,
        }

        let parent = sqlx::query_as::<sqlx::Postgres, ParentRow>(
            "SELECT id FROM parents \
             WHERE user_id = $1 AND school_id = $2 AND is_active = true \
             LIMIT 1",
        )
        .bind(user_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let children = match parent {
            None => vec![],
            Some(p) => {
                #[derive(sqlx::FromRow)]
                struct ChildRow {
                    student_id: Uuid,
                    student_record_id: String,
                    first_name: Option<String>,
                    last_name: Option<String>,
                    current_grade_level: Option<String>,
                    academic_status: String,
                    relationship: String,
                }

                sqlx::query_as::<sqlx::Postgres, ChildRow>(
                    "SELECT ps.student_id, \
                            COALESCE(NULLIF(st.admission_number, ''), st.student_id) AS student_record_id, \
                            u.first_name, \
                            u.last_name, \
                            st.current_grade_level, \
                            st.academic_status, \
                            ps.relationship_type AS relationship \
                     FROM parent_student_relationships ps \
                     JOIN students st ON st.id = ps.student_id AND st.is_active = true \
                     JOIN users u ON u.id = st.user_id \
                     WHERE ps.parent_id = $1 AND ps.school_id = $2 AND ps.is_active = true",
                )
                .bind(p.id)
                .bind(school_id)
                .fetch_all(pool)
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?
                .into_iter()
                .map(|r| ChildInfo {
                    student_id: r.student_id,
                    student_record_id: r.student_record_id,
                    first_name: r.first_name,
                    last_name: r.last_name,
                    current_grade_level: r.current_grade_level,
                    academic_status: r.academic_status,
                    relationship: r.relationship,
                })
                .collect()
            }
        };

        Ok(ParentOverview { children })
    }

    // -------------------------------------------------------------------------
    // Student overview
    // -------------------------------------------------------------------------
    async fn student_overview(
        pool: &PgPool,
        school_id: Uuid,
        user_id: Uuid,
    ) -> Result<StudentOverview, ApiError> {
        #[derive(sqlx::FromRow)]
        struct StudentRow {
            id: Uuid,
            student_id: String,
            academic_status: String,
            current_grade_level: Option<String>,
        }

        let student = sqlx::query_as::<sqlx::Postgres, StudentRow>(
            "SELECT id, COALESCE(NULLIF(admission_number, ''), student_id) AS student_id, academic_status, current_grade_level \
              FROM students \
              WHERE user_id = $1 AND school_id = $2 AND is_active = true \
              LIMIT 1",
        )
        .bind(user_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        match student {
            None => Ok(StudentOverview {
                student_record_id: "N/A".to_string(),
                academic_status: "N/A".to_string(),
                current_grade_level: None,
                current_classes: vec![],
            }),
            Some(s) => {
                let current_classes = sqlx::query_as::<sqlx::Postgres, ClassInfoRow>(
                    "SELECT DISTINCT c.id, c.class_name AS name \
                     FROM student_classes sc \
                     JOIN classes c ON c.id = sc.class_id AND c.is_active = true \
                     WHERE sc.student_id = $1 AND sc.school_id = $2 AND sc.is_active = true \
                     ORDER BY c.class_name",
                )
                .bind(s.id)
                .bind(school_id)
                .fetch_all(pool)
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?
                .into_iter()
                .map(|r| ClassInfo { id: r.id, name: r.name })
                .collect();

                Ok(StudentOverview {
                    student_record_id: s.student_id,
                    academic_status: s.academic_status,
                    current_grade_level: s.current_grade_level,
                    current_classes,
                })
            }
        }
    }
}
