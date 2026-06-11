use std::collections::{HashMap, HashSet};

use uuid::Uuid;

use crate::db::repositories::AcademicCalendarRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::{
    AcademicSession, CreateAcademicSessionRequest, CreateCalendarEventRequest, CreateTermRequest,
    CreateSchoolTimetableRequest, SchoolCalendar, SchoolTimetable, Term,
    TermStudentTransitionAction, UpdateAcademicSessionRequest, UpdateCalendarEventRequest,
    UpdateSchoolTimetableRequest, UpdateTermRequest,
};

pub struct ScheduleService;

impl ScheduleService {
    async fn ensure_school_admin(db: &Database, actor: Uuid, school_id: Uuid) -> Result<(), ApiError> {
        let school_admin_role_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT id FROM roles WHERE name = 'SCHOOL_ADMIN' AND is_active = true",
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

    pub async fn create_academic_session(
        db: &Database,
        actor: Uuid,
        req: CreateAcademicSessionRequest,
    ) -> Result<AcademicSession, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        if let Some(end_date) = req.end_date {
            if end_date < req.start_date {
                return Err(ApiError::ValidationError(
                    "Academic session end_date must be on or after start_date".to_string(),
                ));
            }
        }

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if req.is_current {
            AcademicCalendarRepository::unset_current_sessions_for_school(&mut tx, req.school_id, None)
                .await?;
        }

        let created = AcademicCalendarRepository::create_session(
            &mut tx,
            req.school_id,
            &req.name,
            req.start_date,
            req.end_date,
            req.is_current,
        )
        .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(created)
    }

    pub async fn create_calendar_event(
        db: &Database,
        actor: Uuid,
        req: CreateCalendarEventRequest,
    ) -> Result<SchoolCalendar, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        AcademicCalendarRepository::create_calendar_event(
            db.pool(),
            req.school_id,
            req.session_id,
            &req.event_name,
            &req.event_type,
            req.start_date,
            req.end_date,
            req.color,
            req.description,
            req.is_exam_period,
            req.is_holiday,
            req.term_id,
        )
        .await
    }

    pub async fn create_school_timetable_item(
        db: &Database,
        actor: Uuid,
        req: CreateSchoolTimetableRequest,
    ) -> Result<Vec<SchoolTimetable>, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        let mut days: Vec<String> = req.days_of_week.unwrap_or_default();
        if let Some(single_day) = req.day_of_week {
            days.push(single_day);
        }

        let mut normalized_days: Vec<String> = days
            .into_iter()
            .map(|d: String| d.trim().to_uppercase())
            .filter(|d: &String| !d.is_empty())
            .collect();

        normalized_days.sort();
        normalized_days.dedup();

        if normalized_days.is_empty() {
            return Err(ApiError::ValidationError(
                "Provide at least one day_of_week, either as day_of_week or days_of_week".to_string(),
            ));
        }

        const ALLOWED_DAYS: [&str; 7] = [
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
        ];

        if let Some(invalid) = normalized_days
            .iter()
            .find(|d: &&String| !ALLOWED_DAYS.contains(&d.as_str()))
        {
            return Err(ApiError::ValidationError(format!(
                "Invalid day_of_week '{}'. Allowed values: {}",
                invalid,
                ALLOWED_DAYS.join(", ")
            )));
        }

        AcademicCalendarRepository::create_school_timetable_items(
            db.pool(),
            req.school_id,
            req.class_id,
            &normalized_days,
            &req.activity_type,
            &req.start_time,
            &req.end_time,
            &req.title,
            req.description,
        )
        .await
    }

    pub async fn list_calendar_events(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
    ) -> Result<Vec<SchoolCalendar>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::list_calendar_events_by_school(db.pool(), school_id).await
    }

    pub async fn list_school_timetable_items(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
    ) -> Result<Vec<SchoolTimetable>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::list_school_timetable_items_by_school(db.pool(), school_id).await
    }

    pub async fn get_calendar_event(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        event_id: Uuid,
    ) -> Result<SchoolCalendar, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::get_calendar_event_by_id_and_school(db.pool(), school_id, event_id)
            .await
    }

    pub async fn get_school_timetable_item(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        item_id: Uuid,
    ) -> Result<SchoolTimetable, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::get_school_timetable_item_by_id_and_school(db.pool(), school_id, item_id)
            .await
    }

    pub async fn list_academic_sessions(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
    ) -> Result<Vec<AcademicSession>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::list_sessions_by_school(db.pool(), school_id).await
    }

    pub async fn get_terms_in_session(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        session_id: Uuid,
    ) -> Result<Vec<Term>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        let _ = AcademicCalendarRepository::get_session_by_id_and_school(db.pool(), session_id, school_id)
            .await?;

        AcademicCalendarRepository::list_terms_by_session_and_school(db.pool(), school_id, session_id)
            .await
    }

    pub async fn update_academic_session(
        db: &Database,
        actor: Uuid,
        session_id: Uuid,
        req: UpdateAcademicSessionRequest,
    ) -> Result<AcademicSession, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        if let Some(end_date) = req.end_date {
            if end_date < req.start_date {
                return Err(ApiError::ValidationError(
                    "Academic session end_date must be on or after start_date".to_string(),
                ));
            }
        }

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let _ = AcademicCalendarRepository::get_session_by_id_and_school(db.pool(), session_id, req.school_id)
            .await?;

        if req.is_current {
            AcademicCalendarRepository::unset_current_sessions_for_school(
                &mut tx,
                req.school_id,
                Some(session_id),
            )
            .await?;
        }

        let updated = AcademicCalendarRepository::update_session(
            &mut tx,
            session_id,
            req.school_id,
            &req.name,
            req.start_date,
            req.end_date,
            req.is_current,
        )
        .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(updated)
    }

    pub async fn update_calendar_event(
        db: &Database,
        actor: Uuid,
        event_id: Uuid,
        req: UpdateCalendarEventRequest,
    ) -> Result<SchoolCalendar, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        AcademicCalendarRepository::update_calendar_event(
            db.pool(),
            req.school_id,
            req.session_id,
            event_id,
            &req.event_name,
            &req.event_type,
            req.start_date,
            req.end_date,
            req.color,
            req.description,
            req.is_exam_period,
            req.is_holiday,
            req.term_id,
        )
        .await
    }

    pub async fn update_school_timetable_item(
        db: &Database,
        actor: Uuid,
        item_id: Uuid,
        req: UpdateSchoolTimetableRequest,
    ) -> Result<SchoolTimetable, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        AcademicCalendarRepository::update_school_timetable_item(
            db.pool(),
            req.school_id,
            item_id,
            req.class_id,
            &req.day_of_week,
            &req.activity_type,
            &req.start_time,
            &req.end_time,
            &req.title,
            req.description,
        )
        .await
    }

    pub async fn delete_academic_session(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        session_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        AcademicCalendarRepository::soft_delete_session_by_id_and_school(&mut tx, session_id, school_id)
            .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn create_term(
        db: &Database,
        actor: Uuid,
        req: CreateTermRequest,
    ) -> Result<Term, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        let session = AcademicCalendarRepository::get_session_by_id_and_school(
            db.pool(),
            req.session_id,
            req.school_id,
        )
        .await?;

        if req.start_date < session.start_date {
            return Err(ApiError::ValidationError(
                "Term dates must fall within the selected academic session date range".to_string(),
            ));
        }

        if let Some(end_date) = req.end_date {
            if end_date < req.start_date {
                return Err(ApiError::ValidationError(
                    "Term end_date must be on or after start_date".to_string(),
                ));
            }

            if let Some(session_end) = session.end_date {
                if end_date > session_end {
                    return Err(ApiError::ValidationError(
                        "Term dates must fall within the selected academic session date range".to_string(),
                    ));
                }
            }
        }

        let check_end = req.end_date.unwrap_or_else(|| {
            session.end_date.unwrap_or_else(|| {
                chrono::NaiveDate::from_ymd_opt(2099, 12, 31).unwrap()
            })
        });

        let overlapping_terms: Vec<Term> = AcademicCalendarRepository::get_overlapping_terms(
            db.pool(),
            req.school_id,
            req.session_id,
            req.start_date,
            check_end,
            None,
        )
        .await?;

        if !overlapping_terms.is_empty() {
            let details = overlapping_terms
                .iter()
                .map(|t| {
                    let end = t
                        .end_date
                        .map(|d: chrono::NaiveDate| d.to_string())
                        .unwrap_or_else(|| "null".to_string());
                    format!("{} [{} to {}]", t.name, t.start_date, end)
                })
                .collect::<Vec<String>>()
                .join(", ");

            return Err(ApiError::BadRequest(format!(
                "Term date overlap detected with existing term(s): {}",
                details
            )));
        }

        if req.source_term_id.is_none() && !req.student_transitions.is_empty() {
            return Err(ApiError::ValidationError(
                "source_term_id is required when student_transitions are provided".to_string(),
            ));
        }

        if let Some(source_term_id) = req.source_term_id {
            let source_term = AcademicCalendarRepository::get_term_by_id_and_school(
                db.pool(),
                source_term_id,
                req.school_id,
            )
            .await?;

            if source_term.session_id != req.session_id {
                return Err(ApiError::ValidationError(
                    "source_term_id must belong to the same session as the new term".to_string(),
                ));
            }
        }

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if req.is_current {
            AcademicCalendarRepository::unset_current_terms_for_session(
                &mut tx,
                req.school_id,
                req.session_id,
                None,
            )
            .await?;
        }

        let created = AcademicCalendarRepository::create_term(
            &mut tx,
            req.school_id,
            req.session_id,
            &req.name,
            req.term_number,
            req.start_date,
            req.end_date,
            req.is_current,
        )
        .await?;

        if let Some(source_term_id) = req.source_term_id {
            let source_student_classes =
                AcademicCalendarRepository::list_active_source_term_student_classes(
                    &mut tx,
                    req.school_id,
                    req.session_id,
                    source_term_id,
                )
                .await?;

            let mut source_lookup: HashMap<(Uuid, Uuid), (Uuid, Option<i32>)> = HashMap::new();
            for row in source_student_classes {
                source_lookup.insert((row.student_id, row.class_id), (row.track_id, row.grade_level));
            }

            if req.student_transitions.len() != source_lookup.len() {
                return Err(ApiError::ValidationError(format!(
                    "student_transitions must include all active source-term student enrollments (expected {}, got {})",
                    source_lookup.len(),
                    req.student_transitions.len()
                )));
            }

            let mut seen_source_pairs: HashSet<(Uuid, Uuid)> = HashSet::new();

            for transition in &req.student_transitions {
                let source_key = (transition.student_id, transition.source_class_id);

                if !seen_source_pairs.insert(source_key) {
                    return Err(ApiError::ValidationError(format!(
                        "Duplicate transition entry for student {} and source class {}",
                        transition.student_id, transition.source_class_id
                    )));
                }

                let (source_track_id, source_grade_level) = source_lookup.get(&source_key).copied().ok_or_else(|| {
                    ApiError::ValidationError(format!(
                        "Student {} is not actively enrolled in source class {} for source term {}",
                        transition.student_id, transition.source_class_id, source_term_id
                    ))
                })?;

                let target_class_id = match transition.action {
                    TermStudentTransitionAction::Maintain => {
                        if let Some(next_class_id) = transition.next_applied_class_id {
                            if next_class_id != transition.source_class_id {
                                return Err(ApiError::ValidationError(format!(
                                    "MAINTAIN transition must keep the same class for student {}",
                                    transition.student_id
                                )));
                            }
                        }
                        transition.source_class_id
                    }
                    TermStudentTransitionAction::Promote | TermStudentTransitionAction::Downgrade => transition
                        .next_applied_class_id
                        .ok_or_else(|| {
                            ApiError::ValidationError(format!(
                                "next_applied_class_id is required for {:?} transition for student {}",
                                transition.action, transition.student_id
                            ))
                        })?,
                };

                let target_class = AcademicCalendarRepository::get_class_placement_by_id_and_school(
                    &mut tx,
                    req.school_id,
                    target_class_id,
                )
                .await?;

                if target_class.track_id != source_track_id {
                    return Err(ApiError::ValidationError(format!(
                        "Target class {} must be in the same track as source class {} for student {}",
                        target_class_id, transition.source_class_id, transition.student_id
                    )));
                }

                match transition.action {
                    TermStudentTransitionAction::Promote => {
                        if let (Some(source_grade), Some(target_grade)) =
                            (source_grade_level, target_class.grade_level)
                        {
                            if target_grade <= source_grade {
                                return Err(ApiError::ValidationError(format!(
                                    "PROMOTE transition requires a higher grade level class for student {}",
                                    transition.student_id
                                )));
                            }
                        }
                    }
                    TermStudentTransitionAction::Downgrade => {
                        if let (Some(source_grade), Some(target_grade)) =
                            (source_grade_level, target_class.grade_level)
                        {
                            if target_grade >= source_grade {
                                return Err(ApiError::ValidationError(format!(
                                    "DOWNGRADE transition requires a lower grade level class for student {}",
                                    transition.student_id
                                )));
                            }
                        }
                    }
                    TermStudentTransitionAction::Maintain => {}
                }

                AcademicCalendarRepository::upsert_student_class_for_term(
                    &mut tx,
                    req.school_id,
                    transition.student_id,
                    target_class.class_id,
                    req.session_id,
                    created.id,
                    target_class.track_id,
                    created.start_date,
                )
                .await?;
            }

            if seen_source_pairs.len() != source_lookup.len() {
                return Err(ApiError::ValidationError(
                    "student_transitions must include every active source-term student enrollment exactly once"
                        .to_string(),
                ));
            }
        }

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(created)
    }

    pub async fn update_term(
        db: &Database,
        actor: Uuid,
        term_id: Uuid,
        req: UpdateTermRequest,
    ) -> Result<Term, ApiError> {
        Self::ensure_school_admin(db, actor, req.school_id).await?;

        let session = AcademicCalendarRepository::get_session_by_id_and_school(
            db.pool(),
            req.session_id,
            req.school_id,
        )
        .await?;

        if req.start_date < session.start_date {
            return Err(ApiError::ValidationError(
                "Term dates must fall within the selected academic session date range".to_string(),
            ));
        }

        if let Some(end_date) = req.end_date {
            if end_date < req.start_date {
                return Err(ApiError::ValidationError(
                    "Term end_date must be on or after start_date".to_string(),
                ));
            }

            if let Some(session_end) = session.end_date {
                if end_date > session_end {
                    return Err(ApiError::ValidationError(
                        "Term dates must fall within the selected academic session date range".to_string(),
                    ));
                }
            }
        }

        let _ = AcademicCalendarRepository::get_term_by_id_and_school(db.pool(), term_id, req.school_id)
            .await?;

        let check_end = req.end_date.unwrap_or_else(|| {
            session.end_date.unwrap_or_else(|| {
                chrono::NaiveDate::from_ymd_opt(2099, 12, 31).unwrap()
            })
        });

        let overlapping_terms: Vec<Term> = AcademicCalendarRepository::get_overlapping_terms(
            db.pool(),
            req.school_id,
            req.session_id,
            req.start_date,
            check_end,
            Some(term_id),
        )
        .await?;

        if !overlapping_terms.is_empty() {
            let details = overlapping_terms
                .iter()
                .map(|t| {
                    let end = t
                        .end_date
                        .map(|d: chrono::NaiveDate| d.to_string())
                        .unwrap_or_else(|| "null".to_string());
                    format!("{} [{} to {}]", t.name, t.start_date, end)
                })
                .collect::<Vec<String>>()
                .join(", ");

            return Err(ApiError::BadRequest(format!(
                "Term date overlap detected with existing term(s): {}",
                details
            )));
        }

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if req.is_current {
            AcademicCalendarRepository::unset_current_terms_for_session(
                &mut tx,
                req.school_id,
                req.session_id,
                Some(term_id),
            )
            .await?;
        }

        let updated = AcademicCalendarRepository::update_term(
            &mut tx,
            term_id,
            req.school_id,
            req.session_id,
            &req.name,
            req.term_number,
            req.start_date,
            req.end_date,
            req.is_current,
        )
        .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(updated)
    }

    pub async fn delete_term(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        term_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        AcademicCalendarRepository::soft_delete_term_by_id_and_school(&mut tx, term_id, school_id)
            .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn delete_calendar_event(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        event_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::soft_delete_calendar_event(db.pool(), school_id, event_id).await
    }

    pub async fn delete_school_timetable_item(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        item_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        AcademicCalendarRepository::soft_delete_school_timetable_item(db.pool(), school_id, item_id).await
    }
}
