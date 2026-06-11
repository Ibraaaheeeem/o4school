use chrono::{NaiveDate, Utc};
use sqlx::{PgPool, Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::{AcademicSession, SchoolCalendar, SchoolTimetable, Term};

pub struct AcademicCalendarRepository;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct SourceTermStudentClass {
    pub student_id: Uuid,
    pub class_id: Uuid,
    pub track_id: Uuid,
    pub grade_level: Option<i32>,
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct ClassPlacement {
    pub class_id: Uuid,
    pub track_id: Uuid,
    pub grade_level: Option<i32>,
}

impl AcademicCalendarRepository {
    pub async fn list_active_source_term_student_classes(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        session_id: Uuid,
        term_id: Uuid,
    ) -> Result<Vec<SourceTermStudentClass>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SourceTermStudentClass>(
            r#"
            SELECT
                sc.student_id,
                sc.class_id,
                c.track_id,
                c.grade_level
            FROM student_classes sc
            INNER JOIN students s
                ON s.id = sc.student_id
               AND s.school_id = sc.school_id
               AND s.is_active = true
            INNER JOIN users u
                ON u.id = s.user_id
               AND u.is_active = true
            INNER JOIN classes c
                ON c.id = sc.class_id
               AND c.school_id = sc.school_id
               AND c.is_active = true
            WHERE sc.school_id = $1
              AND sc.academic_session_id = $2
              AND sc.term_id = $3
              AND sc.is_active = true
            "#,
        )
        .bind(school_id)
        .bind(session_id)
        .bind(term_id)
        .fetch_all(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_class_placement_by_id_and_school(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        class_id: Uuid,
    ) -> Result<ClassPlacement, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassPlacement>(
            r#"
            SELECT
                id AS class_id,
                track_id,
                grade_level
            FROM classes
            WHERE school_id = $1
              AND id = $2
              AND is_active = true
            "#,
        )
        .bind(school_id)
        .bind(class_id)
        .fetch_optional(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::ValidationError(format!("Class {} not found for this school", class_id)))
    }

    pub async fn upsert_student_class_for_term(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        student_id: Uuid,
        class_id: Uuid,
        session_id: Uuid,
        term_id: Uuid,
        track_id: Uuid,
        enrollment_date: NaiveDate,
    ) -> Result<(), ApiError> {
        sqlx::query(
            r#"
            INSERT INTO student_classes (
                id, school_id, student_id, class_id, academic_session_id, term_id, track_id,
                enrollment_date, created_at, updated_at, is_active
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW(), true)
            ON CONFLICT (student_id, track_id, academic_session_id, term_id)
            DO UPDATE SET
                class_id = EXCLUDED.class_id,
                school_id = EXCLUDED.school_id,
                enrollment_date = EXCLUDED.enrollment_date,
                updated_at = NOW(),
                is_active = true
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(school_id)
        .bind(student_id)
        .bind(class_id)
        .bind(session_id)
        .bind(term_id)
        .bind(track_id)
        .bind(enrollment_date)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn create_school_timetable_items(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Option<Uuid>,
        days_of_week: &[String],
        activity_type: &str,
        start_time: &str,
        end_time: &str,
        title: &str,
        description: Option<String>,
    ) -> Result<Vec<SchoolTimetable>, ApiError> {
        let mut created = Vec::with_capacity(days_of_week.len());
        for day in days_of_week {
            let item = Self::create_school_timetable_item(
                pool,
                school_id,
                class_id,
                day,
                activity_type,
                start_time,
                end_time,
                title,
                description.clone(),
            )
            .await?;
            created.push(item);
        }

        Ok(created)
    }

    pub async fn create_school_timetable_item(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Option<Uuid>,
        day_of_week: &str,
        activity_type: &str,
        start_time: &str,
        end_time: &str,
        title: &str,
        description: Option<String>,
    ) -> Result<SchoolTimetable, ApiError> {
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, SchoolTimetable>(
            r#"
            INSERT INTO school_timetables (
                day_of_week, activity_type, start_time, end_time, title, description,
                school_id, id, created_at, updated_at, is_active, class_id
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, true, $11)
            RETURNING
                day_of_week,
                activity_type,
                start_time,
                end_time,
                title,
                description,
                school_id,
                id,
                created_at,
                updated_at,
                is_active,
                class_id
            "#,
        )
        .bind(day_of_week)
        .bind(activity_type)
        .bind(start_time)
        .bind(end_time)
        .bind(title)
        .bind(description)
        .bind(school_id)
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(class_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn list_school_timetable_items_by_school(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<Vec<SchoolTimetable>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolTimetable>(
            r#"
            SELECT
                day_of_week,
                activity_type,
                start_time,
                end_time,
                title,
                description,
                school_id,
                id,
                created_at,
                updated_at,
                is_active,
                class_id
            FROM school_timetables
            WHERE school_id = $1 AND is_active = true
            ORDER BY day_of_week ASC, start_time ASC, created_at ASC
            "#,
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_school_timetable_item_by_id_and_school(
        pool: &PgPool,
        school_id: Uuid,
        item_id: Uuid,
    ) -> Result<SchoolTimetable, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolTimetable>(
            r#"
            SELECT
                day_of_week,
                activity_type,
                start_time,
                end_time,
                title,
                description,
                school_id,
                id,
                created_at,
                updated_at,
                is_active,
                class_id
            FROM school_timetables
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(item_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("School timetable item not found for this school".to_string()))
    }

    pub async fn update_school_timetable_item(
        pool: &PgPool,
        school_id: Uuid,
        item_id: Uuid,
        class_id: Option<Uuid>,
        day_of_week: &str,
        activity_type: &str,
        start_time: &str,
        end_time: &str,
        title: &str,
        description: Option<String>,
    ) -> Result<SchoolTimetable, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolTimetable>(
            r#"
            UPDATE school_timetables
            SET class_id = $1,
                day_of_week = $2,
                activity_type = $3,
                start_time = $4,
                end_time = $5,
                title = $6,
                description = $7,
                updated_at = NOW()
            WHERE id = $8 AND school_id = $9 AND is_active = true
            RETURNING
                day_of_week,
                activity_type,
                start_time,
                end_time,
                title,
                description,
                school_id,
                id,
                created_at,
                updated_at,
                is_active,
                class_id
            "#,
        )
        .bind(class_id)
        .bind(day_of_week)
        .bind(activity_type)
        .bind(start_time)
        .bind(end_time)
        .bind(title)
        .bind(description)
        .bind(item_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("School timetable item not found for this school".to_string()))
    }

    pub async fn soft_delete_school_timetable_item(
        pool: &PgPool,
        school_id: Uuid,
        item_id: Uuid,
    ) -> Result<(), ApiError> {
        let result = sqlx::query(
            "UPDATE school_timetables SET is_active = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(item_id)
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(ApiError::NotFound(
                "School timetable item not found for this school".to_string(),
            ));
        }

        Ok(())
    }

    pub async fn create_calendar_event(
        pool: &PgPool,
        school_id: Uuid,
        session_id: Uuid,
        event_name: &str,
        event_type: &str,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        color: Option<String>,
        description: Option<String>,
        is_exam_period: Option<bool>,
        is_holiday: Option<bool>,
        term_id: Option<Uuid>,
    ) -> Result<SchoolCalendar, ApiError> {
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, SchoolCalendar>(
            r#"
            INSERT INTO school_calendar (
                id, school_id, session_id, event_name, event_type, start_date, end_date,
                description, is_holiday, is_exam_period, color, term_id, created_at, updated_at, is_active
            )
            VALUES (
                $1, $2, $3, $4, $5, $6, $7,
                $8, $9, $10, $11, $12, $13, $14, true
            )
            RETURNING *
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(school_id)
        .bind(session_id)
        .bind(event_name)
        .bind(event_type)
        .bind(start_date)
        .bind(end_date)
        .bind(description)
        .bind(is_holiday)
        .bind(is_exam_period)
        .bind(color)
        .bind(term_id)
        .bind(now)
        .bind(now)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn list_calendar_events_by_school(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<Vec<SchoolCalendar>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolCalendar>(
            r#"
            SELECT *
            FROM school_calendar
            WHERE school_id = $1 AND is_active = true
            ORDER BY start_date ASC, created_at ASC
            "#,
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_calendar_event_by_id_and_school(
        pool: &PgPool,
        school_id: Uuid,
        event_id: Uuid,
    ) -> Result<SchoolCalendar, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolCalendar>(
            r#"
            SELECT *
            FROM school_calendar
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(event_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Calendar event not found for this school".to_string()))
    }

    pub async fn update_calendar_event(
        pool: &PgPool,
        school_id: Uuid,
        session_id: Uuid,
        event_id: Uuid,
        event_name: &str,
        event_type: &str,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        color: Option<String>,
        description: Option<String>,
        is_exam_period: Option<bool>,
        is_holiday: Option<bool>,
        term_id: Option<Uuid>,
    ) -> Result<SchoolCalendar, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolCalendar>(
            r#"
            UPDATE school_calendar
            SET event_name = $1,
                event_type = $2,
                start_date = $3,
                end_date = $4,
                color = $5,
                description = $6,
                is_exam_period = $7,
                is_holiday = $8,
                term_id = $9,
                session_id = $10,
                updated_at = NOW()
            WHERE id = $11 AND school_id = $12 AND is_active = true
            RETURNING *
            "#,
        )
        .bind(event_name)
        .bind(event_type)
        .bind(start_date)
        .bind(end_date)
        .bind(color)
        .bind(description)
        .bind(is_exam_period)
        .bind(is_holiday)
        .bind(term_id)
        .bind(session_id)
        .bind(event_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Calendar event not found for this school".to_string()))
    }

    pub async fn soft_delete_calendar_event(
        pool: &PgPool,
        school_id: Uuid,
        event_id: Uuid,
    ) -> Result<(), ApiError> {
        let result = sqlx::query(
            "UPDATE school_calendar SET is_active = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(event_id)
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(ApiError::NotFound(
                "Calendar event not found for this school".to_string(),
            ));
        }

        Ok(())
    }

    pub async fn list_terms_by_session_and_school(
        pool: &PgPool,
        school_id: Uuid,
        session_id: Uuid,
    ) -> Result<Vec<Term>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Term>(
            r#"
                        SELECT
                            id,
                            school_id,
                            academic_session_id AS session_id,
                            term_name AS name,
                            COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                            start_date,
                            end_date,
                            COALESCE(is_current_term, false) AS is_current,
                            created_at,
                            updated_at,
                            is_active
            FROM terms
            WHERE school_id = $1
                            AND academic_session_id = $2
              AND is_active = true
                        ORDER BY start_date ASC, COALESCE(term_number, COALESCE(term_order, 1)) ASC
            "#,
        )
        .bind(school_id)
        .bind(session_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    async fn get_overlapping_terms_in_tx(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        session_id: Uuid,
        start_date: NaiveDate,
        end_date: NaiveDate,
        exclude_term_id: Option<Uuid>,
    ) -> Result<Vec<Term>, ApiError> {
        let terms = if let Some(term_id) = exclude_term_id {
            sqlx::query_as::<sqlx::Postgres, Term>(
                r#"
                                SELECT
                                    id,
                                    school_id,
                                    academic_session_id AS session_id,
                                    term_name AS name,
                                    COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                                    start_date,
                                    end_date,
                                    COALESCE(is_current_term, false) AS is_current,
                                    created_at,
                                    updated_at,
                                    is_active
                FROM terms
                WHERE school_id = $1
                                    AND academic_session_id = $2
                  AND is_active = true
                  AND id <> $3
                  AND NOT (
                    COALESCE(end_date, start_date) < $4
                    OR start_date > $5
                  )
                ORDER BY start_date ASC
                "#,
            )
            .bind(school_id)
            .bind(session_id)
            .bind(term_id)
            .bind(start_date)
            .bind(end_date)
            .fetch_all(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        } else {
            sqlx::query_as::<sqlx::Postgres, Term>(
                r#"
                                SELECT
                                    id,
                                    school_id,
                                    academic_session_id AS session_id,
                                    term_name AS name,
                                    COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                                    start_date,
                                    end_date,
                                    COALESCE(is_current_term, false) AS is_current,
                                    created_at,
                                    updated_at,
                                    is_active
                FROM terms
                WHERE school_id = $1
                                    AND academic_session_id = $2
                  AND is_active = true
                  AND NOT (
                    COALESCE(end_date, start_date) < $3
                    OR start_date > $4
                  )
                ORDER BY start_date ASC
                "#,
            )
            .bind(school_id)
            .bind(session_id)
            .bind(start_date)
            .bind(end_date)
            .fetch_all(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        };

        Ok(terms)
    }

    fn format_overlap_message(overlapping_terms: &[Term]) -> String {
        let details = overlapping_terms
            .iter()
            .map(|t| {
                let end = t
                    .end_date
                    .map(|d: NaiveDate| d.to_string())
                    .unwrap_or_else(|| "null".to_string());
                format!("{} [{} to {}]", t.name, t.start_date, end)
            })
            .collect::<Vec<String>>()
            .join(", ");

        format!("Term date overlap detected with existing term(s): {}", details)
    }

    pub async fn get_session_by_id_and_school(
        pool: &PgPool,
        session_id: Uuid,
        school_id: Uuid,
    ) -> Result<AcademicSession, ApiError> {
        sqlx::query_as::<sqlx::Postgres, AcademicSession>(
            r#"
            SELECT
                id,
                school_id,
                session_name AS name,
                start_date,
                end_date,
                COALESCE(is_current_session, false) AS is_current,
                created_at,
                updated_at,
                is_active
            FROM academic_sessions
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(session_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Academic session not found for this school".to_string()))
    }

    pub async fn get_term_by_id_and_school(
        pool: &PgPool,
        term_id: Uuid,
        school_id: Uuid,
    ) -> Result<Term, ApiError> {
                sqlx::query_as::<sqlx::Postgres, Term>(
                        r#"
                        SELECT
                            id,
                            school_id,
                            academic_session_id AS session_id,
                            term_name AS name,
                            COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                            start_date,
                            end_date,
                            COALESCE(is_current_term, false) AS is_current,
                            created_at,
                            updated_at,
                            is_active
                        FROM terms
                        WHERE id = $1 AND school_id = $2 AND is_active = true
                        "#,
                )
        .bind(term_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Term not found for this school".to_string()))
    }

    pub async fn soft_delete_session_by_id_and_school(
        tx: &mut Transaction<'_, Postgres>,
        session_id: Uuid,
        school_id: Uuid,
    ) -> Result<(), ApiError> {
        let session_result = sqlx::query(
            "UPDATE academic_sessions SET is_active = false, is_current_session = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(session_id)
        .bind(school_id)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if session_result.rows_affected() == 0 {
            return Err(ApiError::NotFound(
                "Academic session not found for this school".to_string(),
            ));
        }

        // Keep data consistent by deactivating terms under the deleted session.
        sqlx::query(
            "UPDATE terms SET is_active = false, is_current_term = false, updated_at = NOW() WHERE school_id = $1 AND academic_session_id = $2 AND is_active = true",
        )
        .bind(school_id)
        .bind(session_id)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn soft_delete_term_by_id_and_school(
        tx: &mut Transaction<'_, Postgres>,
        term_id: Uuid,
        school_id: Uuid,
    ) -> Result<(), ApiError> {
        let term_result = sqlx::query(
            "UPDATE terms SET is_active = false, is_current_term = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(term_id)
        .bind(school_id)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if term_result.rows_affected() == 0 {
            return Err(ApiError::NotFound("Term not found for this school".to_string()));
        }

        Ok(())
    }

    pub async fn unset_current_sessions_for_school(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        except_session_id: Option<Uuid>,
    ) -> Result<(), ApiError> {
        if let Some(id) = except_session_id {
            sqlx::query(
                "UPDATE academic_sessions SET is_current_session = false, end_date = COALESCE(end_date, CURRENT_DATE), updated_at = NOW() WHERE school_id = $1 AND COALESCE(is_current_session, false) = true AND is_active = true AND id <> $2",
            )
            .bind(school_id)
            .bind(id)
            .execute(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        } else {
            sqlx::query(
                "UPDATE academic_sessions SET is_current_session = false, end_date = COALESCE(end_date, CURRENT_DATE), updated_at = NOW() WHERE school_id = $1 AND COALESCE(is_current_session, false) = true AND is_active = true",
            )
            .bind(school_id)
            .execute(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        }

        Ok(())
    }

    pub async fn create_session(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        name: &str,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        is_current: bool,
    ) -> Result<AcademicSession, ApiError> {
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, AcademicSession>(
            r#"
            INSERT INTO academic_sessions (
                id, school_id, session_name, session_year, start_date, end_date,
                is_current_session, status, notes, created_at, updated_at, is_active
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, 'ACTIVE', NULL, $8, $9, true)
            RETURNING
                id,
                school_id,
                session_name AS name,
                start_date,
                end_date,
                COALESCE(is_current_session, false) AS is_current,
                created_at,
                updated_at,
                is_active
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(school_id)
        .bind(name)
        .bind(name)
        .bind(start_date)
        .bind(end_date)
        .bind(is_current)
        .bind(now)
        .bind(now)
        .fetch_one(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn update_session(
        tx: &mut Transaction<'_, Postgres>,
        session_id: Uuid,
        school_id: Uuid,
        name: &str,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        is_current: bool,
    ) -> Result<AcademicSession, ApiError> {
        sqlx::query_as::<sqlx::Postgres, AcademicSession>(
            r#"
            UPDATE academic_sessions
            SET session_name = $1,
                session_year = $1,
                start_date = $2,
                end_date = $3,
                is_current_session = $4,
                updated_at = NOW()
            WHERE id = $5 AND school_id = $6 AND is_active = true
            RETURNING
                id,
                school_id,
                session_name AS name,
                start_date,
                end_date,
                COALESCE(is_current_session, false) AS is_current,
                created_at,
                updated_at,
                is_active
            "#,
        )
        .bind(name)
        .bind(start_date)
        .bind(end_date)
        .bind(is_current)
        .bind(session_id)
        .bind(school_id)
        .fetch_optional(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Academic session not found for this school".to_string()))
    }

    pub async fn unset_current_terms_for_session(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        session_id: Uuid,
        except_term_id: Option<Uuid>,
    ) -> Result<(), ApiError> {
        if let Some(id) = except_term_id {
            sqlx::query(
                "UPDATE terms SET is_current_term = false, end_date = COALESCE(end_date, CURRENT_DATE), updated_at = NOW() WHERE school_id = $1 AND academic_session_id = $2 AND COALESCE(is_current_term, false) = true AND is_active = true AND id <> $3",
            )
            .bind(school_id)
            .bind(session_id)
            .bind(id)
            .execute(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        } else {
            sqlx::query(
                "UPDATE terms SET is_current_term = false, end_date = COALESCE(end_date, CURRENT_DATE), updated_at = NOW() WHERE school_id = $1 AND academic_session_id = $2 AND COALESCE(is_current_term, false) = true AND is_active = true",
            )
            .bind(school_id)
            .bind(session_id)
            .execute(&mut **tx)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        }

        Ok(())
    }

    pub async fn create_term(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        session_id: Uuid,
        name: &str,
        term_number: i32,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        is_current: bool,
    ) -> Result<Term, ApiError> {
        let check_end = end_date.unwrap_or_else(|| {
            NaiveDate::from_ymd_opt(2099, 12, 31).unwrap()
        });
        let overlapping_terms = Self::get_overlapping_terms_in_tx(
            tx,
            school_id,
            session_id,
            start_date,
            check_end,
            None,
        )
        .await?;

        if !overlapping_terms.is_empty() {
            return Err(ApiError::BadRequest(Self::format_overlap_message(
                &overlapping_terms,
            )));
        }

        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, Term>(
            r#"
            INSERT INTO terms (
                id, school_id, academic_session_id, term_name, term_number, term_order,
                start_date, end_date, is_current_term, status, description, created_at, updated_at, is_active
            )
            VALUES ($1, $2, $3, $4, $5, $5, $6, $7, $8, 'ACTIVE', NULL, $9, $10, true)
            RETURNING
                id,
                school_id,
                academic_session_id AS session_id,
                term_name AS name,
                COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                start_date,
                end_date,
                COALESCE(is_current_term, false) AS is_current,
                created_at,
                updated_at,
                is_active
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(school_id)
        .bind(session_id)
        .bind(name)
        .bind(term_number)
        .bind(start_date)
        .bind(end_date)
        .bind(is_current)
        .bind(now)
        .bind(now)
        .fetch_one(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn update_term(
        tx: &mut Transaction<'_, Postgres>,
        term_id: Uuid,
        school_id: Uuid,
        session_id: Uuid,
        name: &str,
        term_number: i32,
        start_date: NaiveDate,
        end_date: Option<NaiveDate>,
        is_current: bool,
    ) -> Result<Term, ApiError> {
        let check_end = end_date.unwrap_or_else(|| {
            NaiveDate::from_ymd_opt(2099, 12, 31).unwrap()
        });
        let overlapping_terms = Self::get_overlapping_terms_in_tx(
            tx,
            school_id,
            session_id,
            start_date,
            check_end,
            Some(term_id),
        )
        .await?;

        if !overlapping_terms.is_empty() {
            return Err(ApiError::BadRequest(Self::format_overlap_message(
                &overlapping_terms,
            )));
        }

        sqlx::query_as::<sqlx::Postgres, Term>(
            r#"
            UPDATE terms
            SET academic_session_id = $1,
                term_name = $2,
                term_number = $3,
                term_order = $3,
                start_date = $4,
                end_date = $5,
                is_current_term = $6,
                updated_at = NOW()
            WHERE id = $7 AND school_id = $8 AND is_active = true
            RETURNING
                id,
                school_id,
                academic_session_id AS session_id,
                term_name AS name,
                COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                start_date,
                end_date,
                COALESCE(is_current_term, false) AS is_current,
                created_at,
                updated_at,
                is_active
            "#,
        )
        .bind(session_id)
        .bind(name)
        .bind(term_number)
        .bind(start_date)
        .bind(end_date)
        .bind(is_current)
        .bind(term_id)
        .bind(school_id)
        .fetch_optional(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Term not found for this school".to_string()))
    }

    pub async fn get_overlapping_terms(
        pool: &PgPool,
        school_id: Uuid,
        session_id: Uuid,
        start_date: NaiveDate,
        end_date: NaiveDate,
        exclude_term_id: Option<Uuid>,
    ) -> Result<Vec<Term>, ApiError> {
        let terms = if let Some(term_id) = exclude_term_id {
            sqlx::query_as::<sqlx::Postgres, Term>(
                r#"
                                SELECT
                                    id,
                                    school_id,
                                    academic_session_id AS session_id,
                                    term_name AS name,
                                    COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                                    start_date,
                                    end_date,
                                    COALESCE(is_current_term, false) AS is_current,
                                    created_at,
                                    updated_at,
                                    is_active
                FROM terms
                WHERE school_id = $1
                                    AND academic_session_id = $2
                  AND is_active = true
                  AND id <> $3
                  AND NOT (
                    COALESCE(end_date, start_date) < $4
                    OR start_date > $5
                  )
                ORDER BY start_date ASC
                "#,
            )
            .bind(school_id)
            .bind(session_id)
            .bind(term_id)
            .bind(start_date)
            .bind(end_date)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        } else {
            sqlx::query_as::<sqlx::Postgres, Term>(
                r#"
                                SELECT
                                    id,
                                    school_id,
                                    academic_session_id AS session_id,
                                    term_name AS name,
                                    COALESCE(term_number, COALESCE(term_order, 1)) AS term_number,
                                    start_date,
                                    end_date,
                                    COALESCE(is_current_term, false) AS is_current,
                                    created_at,
                                    updated_at,
                                    is_active
                FROM terms
                WHERE school_id = $1
                                    AND academic_session_id = $2
                  AND is_active = true
                  AND NOT (
                    COALESCE(end_date, start_date) < $3
                    OR start_date > $4
                  )
                ORDER BY start_date ASC
                "#,
            )
            .bind(school_id)
            .bind(session_id)
            .bind(start_date)
            .bind(end_date)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        };

        Ok(terms)
    }

    pub async fn list_sessions_by_school(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<Vec<AcademicSession>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, AcademicSession>(
            r#"
            SELECT
                id,
                school_id,
                session_name AS name,
                start_date,
                end_date,
                COALESCE(is_current_session, false) AS is_current,
                created_at,
                updated_at,
                is_active
            FROM academic_sessions
            WHERE school_id = $1 AND is_active = true
            ORDER BY start_date DESC
            "#,
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::AcademicCalendarRepository;
    use crate::models::Term;
    use chrono::{NaiveDate, Utc};
    use uuid::Uuid;

    fn build_term(name: &str, start: NaiveDate, end: Option<NaiveDate>) -> Term {
        let now = Utc::now().naive_utc();
        Term {
            id: Uuid::new_v4(),
            school_id: Uuid::new_v4(),
            session_id: Uuid::new_v4(),
            name: name.to_string(),
            term_number: 1,
            start_date: start,
            end_date: end,
            is_current: false,
            created_at: now,
            updated_at: now,
            is_active: true,
        }
    }

    #[test]
    fn format_overlap_message_includes_all_terms() {
        let first = build_term(
            "First Term",
            NaiveDate::from_ymd_opt(2026, 9, 1).expect("valid date"),
            Some(NaiveDate::from_ymd_opt(2026, 12, 10).expect("valid date")),
        );
        let second = build_term(
            "Second Term",
            NaiveDate::from_ymd_opt(2027, 1, 10).expect("valid date"),
            Some(NaiveDate::from_ymd_opt(2027, 4, 20).expect("valid date")),
        );

        let message = AcademicCalendarRepository::format_overlap_message(&[first, second]);

        assert!(
            message.contains("Term date overlap detected with existing term(s):"),
            "message should include fixed overlap prefix"
        );
        assert!(
            message.contains("First Term [2026-09-01 to 2026-12-10]"),
            "message should include first overlapping term details"
        );
        assert!(
            message.contains("Second Term [2027-01-10 to 2027-04-20]"),
            "message should include second overlapping term details"
        );
    }

    #[test]
    fn format_overlap_message_handles_missing_end_date() {
        let open_ended = build_term(
            "Open Ended",
            NaiveDate::from_ymd_opt(2026, 9, 1).expect("valid date"),
            None,
        );

        let message = AcademicCalendarRepository::format_overlap_message(&[open_ended]);

        assert!(
            message.contains("Open Ended [2026-09-01 to null]"),
            "message should render missing end date as null"
        );
    }
}
