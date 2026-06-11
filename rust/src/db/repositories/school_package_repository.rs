use chrono::Utc;
use sqlx::PgPool;
use std::collections::HashMap;
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::{
    ClassSubject, CreateClassRequest, CreateDepartmentRequest, CreateEducationTrackRequest,
    Department, EducationTrack, InitializeDefaultStructureResponse, SchoolClass,
    SchoolSubject, Subject,
    UpdateSchoolDataRequest,
    AcademicStructureResponse, AcademicTrackNode, DepartmentNode, ClassNode,
    SchoolSubjectResponse, LinkedClassResponse, SchoolSubjectFilter,
};

pub struct SchoolPackageRepository;

impl SchoolPackageRepository {
    pub async fn initialize_default_structure_if_empty(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<InitializeDefaultStructureResponse, ApiError> {
        let school_exists = sqlx::query_scalar::<sqlx::Postgres, bool>(
            "SELECT EXISTS(SELECT 1 FROM schools WHERE id = $1 AND is_active = true)",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        if !school_exists {
            return Err(ApiError::NotFound("School not found".to_string()));
        }

        let has_structure = sqlx::query_scalar::<sqlx::Postgres, bool>(
            r#"
            SELECT EXISTS(
                SELECT 1
                FROM education_tracks et
                WHERE et.school_id = $1 AND et.is_active = true
            ) OR EXISTS(
                SELECT 1
                FROM departments d
                WHERE d.school_id = $1 AND d.is_active = true
            ) OR EXISTS(
                SELECT 1
                FROM classes c
                WHERE c.school_id = $1 AND c.is_active = true
            ) OR EXISTS(
                SELECT 1
                FROM class_subjects cs
                WHERE cs.school_id = $1 AND cs.is_active = true
            )
            "#,
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        if has_structure {
            return Ok(InitializeDefaultStructureResponse {
                school_id,
                created: false,
                message: "Academic structure already exists for this school".to_string(),
                track_id: None,
                department_id: None,
                class_id: None,
                class_subjects_created: 0,
            });
        }

        let mut tx = pool
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let now = Utc::now().naive_utc();
        let track_id = Uuid::new_v4();
        let school_code_suffix = &school_id.to_string()[..6];

        sqlx::query(
            r#"
            INSERT INTO education_tracks (id, created_at, is_active, updated_at, school_id, description, name)
            VALUES ($1, $2, true, $2, $3, $4, $5)
            "#,
        )
        .bind(track_id)
        .bind(now)
        .bind(school_id)
        .bind(Some("Conventional academic track".to_string()))
        .bind("Conventional")
        .execute(&mut *tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let department_definitions = [
            (
                "Nursery",
                vec![
                    ("Kindergarten", -3_i32),
                    ("Pre-Nursery", -2_i32),
                    ("Nursery 1", -1_i32),
                    ("Nursery 2", 0_i32),
                ],
            ),
            (
                "Primary",
                vec![
                    ("Primary 1", 1_i32),
                    ("Primary 2", 2_i32),
                    ("Primary 3", 3_i32),
                    ("Primary 4", 4_i32),
                    ("Primary 5", 5_i32),
                    ("Primary 6", 6_i32),
                ],
            ),
            (
                "Junior Secondary",
                vec![
                    ("JSS 1", 7_i32),
                    ("JSS 2", 8_i32),
                    ("JSS 3", 9_i32),
                ],
            ),
            (
                "Senior Secondary",
                vec![
                    ("SSS 1", 10_i32),
                    ("SSS 2", 11_i32),
                    ("SSS 3", 12_i32),
                ],
            ),
        ];

        let mut created_department_ids = Vec::with_capacity(department_definitions.len());
        let mut created_class_ids = Vec::new();
        let mut class_name_to_id = HashMap::new();

        for (department_name, classes) in department_definitions {
            let department_id = Uuid::new_v4();
            created_department_ids.push(department_id);

            sqlx::query(
                r#"
                INSERT INTO departments (
                    id, created_at, is_active, updated_at, school_id, description, name, track_id
                ) VALUES (
                    $1, $2, true, $2, $3, $4, $5, $6
                )
                "#,
            )
            .bind(department_id)
            .bind(now)
            .bind(school_id)
            .bind(Some(format!("{} department", department_name)))
            .bind(department_name)
            .bind(track_id)
            .execute(&mut *tx)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

            for (idx, (class_name, grade_level)) in classes.into_iter().enumerate() {
                let class_id = Uuid::new_v4();
                let class_code = format!("{}-{:02}-{}", department_name.replace(' ', "").to_uppercase(), idx + 1, school_code_suffix);

                sqlx::query(
                    r#"
                    INSERT INTO classes (
                        id, created_at, is_active, updated_at,
                        school_id, track_id, class_name, class_code, classroom_location,
                        current_enrollment, department_id, grade_level, max_capacity,
                        scoring_scheme, class_staff_id, term
                    ) VALUES (
                        $1, $2, true, $2,
                        $3, $4, $5, $6, NULL,
                        0, $7, $8, NULL,
                        NULL, NULL, NULL
                    )
                    "#,
                )
                .bind(class_id)
                .bind(now)
                .bind(school_id)
                .bind(track_id)
                .bind(class_name)
                .bind(class_code)
                .bind(department_id)
                .bind(grade_level)
                .execute(&mut *tx)
                .await
                .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

                created_class_ids.push(class_id);
                class_name_to_id.insert(class_name.to_string(), class_id);
            }
        }

        let subject_class_mappings: [(&str, &[&str]); 21] = [
            ("11f28946-9e04-4060-933f-0c09ff9c7345", &["SSS 1", "SSS 2", "SSS 3"]),
            (
                "68465bf1-141e-4985-bbd5-59d83c7b9b3c",
                &[
                    "JSS 1",
                    "JSS 2",
                    "JSS 3",
                    "Primary 1",
                    "Primary 2",
                    "Primary 3",
                    "Primary 4",
                    "Primary 5",
                ],
            ),
            ("9f0c7046-a3ee-43a7-a060-da7b7fc45915", &["SSS 1", "SSS 2", "SSS 3"]),
            ("939fbfe7-46d7-4502-9f03-b6bdc283176e", &["JSS 1", "JSS 2", "JSS 3"]),
            ("148a68a6-3636-4c73-8bb6-188ce97c0cf0", &["SSS 1", "SSS 2", "SSS 3"]),
            ("1d418fb2-3f1d-4a58-97d0-eaa8cc1b9c78", &["SSS 1", "SSS 2", "SSS 3"]),
            (
                "792151ec-c5b9-4992-8691-aae685dde847",
                &["Kindergarten", "Nursery 1", "Nursery 2", "Primary 1", "Primary 2", "Primary 3"],
            ),
            (
                "dd972b32-54de-4e02-a954-8798dad90deb",
                &[
                    "JSS 1",
                    "JSS 2",
                    "JSS 3",
                    "Primary 1",
                    "Primary 2",
                    "Primary 3",
                    "Primary 4",
                    "Primary 5",
                    "SSS 1",
                    "SSS 2",
                    "SSS 3",
                ],
            ),
            ("80663d79-70b0-43fc-9f8d-40d6ef261425", &["SSS 1", "SSS 2", "SSS 3"]),
            (
                "77293adf-ff2d-4320-b709-9f1105c56c7f",
                &["Nursery 1", "Nursery 2", "Pre-Nursery", "Primary 1", "Primary 2", "Primary 3"],
            ),
            (
                "65668091-16c2-40a5-9b4f-6376d2e065eb",
                &[
                    "JSS 1",
                    "JSS 2",
                    "JSS 3",
                    "Primary 1",
                    "Primary 2",
                    "Primary 3",
                    "Primary 4",
                    "Primary 5",
                    "SSS 1",
                    "SSS 2",
                    "SSS 3",
                ],
            ),
            ("2ead089a-3ec2-4afc-be82-00989c4d43c9", &["Kindergarten", "Nursery 1", "Nursery 2", "Pre-Nursery"]),
            (
                "4c1d5bb0-358f-44ff-8fa2-96366df5e8e6",
                &[
                    "JSS 1",
                    "JSS 2",
                    "JSS 3",
                    "Primary 1",
                    "Primary 2",
                    "Primary 3",
                    "Primary 4",
                    "Primary 5",
                    "SSS 1",
                    "SSS 2",
                    "SSS 3",
                ],
            ),
            (
                "94cbd980-1220-4a7e-be7f-590c8901f635",
                &[
                    "JSS 1",
                    "JSS 2",
                    "JSS 3",
                    "Kindergarten",
                    "Nursery 1",
                    "Nursery 2",
                    "Pre-Nursery",
                    "Primary 1",
                    "Primary 2",
                    "Primary 3",
                    "Primary 4",
                    "Primary 5",
                    "SSS 1",
                ],
            ),
            (
                "c9e2ed77-6133-46cc-a056-acd2684a7cc0",
                &["JSS 1", "JSS 2", "Primary 1", "Primary 2", "Primary 3", "Primary 4", "Primary 5"],
            ),
            ("96e5e34d-e703-4bad-8769-7ac4fc164540", &["Kindergarten", "Nursery 1", "Nursery 2", "Pre-Nursery"]),
            ("80685fc6-9c62-4c9d-ab14-868684b3073f", &["SSS 1", "SSS 2", "SSS 3"]),
            (
                "524a6fa9-f9e1-4d86-9eb7-a3f61c5e839b",
                &["JSS 1", "JSS 2", "JSS 3", "Primary 4", "Primary 5"],
            ),
            (
                "37c527de-2855-4067-8cbe-a4db7e0d0c5d",
                &["Primary 1", "Primary 2", "Primary 3", "Primary 4", "Primary 5"],
            ),
            ("4bedf173-54e6-4c8f-b73d-71258cb779c2", &["Kindergarten", "Nursery 1", "Nursery 2", "Pre-Nursery"]),
            (
                "0051183e-5c47-4d29-9c5f-05891616478f",
                &["Primary 1", "Primary 2", "Primary 3", "Primary 4", "Primary 5"],
            ),
        ];

        let mut class_subjects_created = 0_i64;
        for (subject_id_raw, class_names) in subject_class_mappings {
            let subject_id = Uuid::parse_str(subject_id_raw)
                .map_err(|error| ApiError::ValidationError(error.to_string()))?;

            let school_subject_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
                r#"
                SELECT id
                FROM school_subjects
                WHERE school_id = $1 AND subject_id = $2 AND is_active = true
                LIMIT 1
                "#,
            )
            .bind(school_id)
            .bind(subject_id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

            let Some(school_subject_id) = school_subject_id else {
                continue;
            };

            for class_name in class_names {
                if let Some(class_id) = class_name_to_id.get(*class_name) {
                    let result = sqlx::query(
                        r#"
                        INSERT INTO class_subjects (
                            id, created_at, is_active, updated_at, school_id,
                            assigned_at, assigned_by, class_id, staff_id, school_subject_id
                        )
                        SELECT
                            $1, $2, true, $2, $3,
                            $2, NULL, $4, NULL, $5
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM class_subjects
                            WHERE class_id = $4
                              AND school_subject_id = $5
                              AND is_active = true
                        )
                        "#,
                    )
                    .bind(Uuid::new_v4())
                    .bind(now)
                    .bind(school_id)
                    .bind(*class_id)
                    .bind(school_subject_id)
                    .execute(&mut *tx)
                    .await
                    .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

                    class_subjects_created += result.rows_affected() as i64;
                }
            }
        }

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(InitializeDefaultStructureResponse {
            school_id,
            created: true,
            message: "Default academic structure created".to_string(),
            track_id: Some(track_id),
            department_id: created_department_ids.first().copied(),
            class_id: created_class_ids.first().copied(),
            class_subjects_created,
        })
    }

    pub async fn update_school_data(
        pool: &PgPool,
        school_id: Uuid,
        request: &UpdateSchoolDataRequest,
    ) -> Result<bool, ApiError> {
        sqlx::query(
            r#"
            UPDATE schools
            SET
                name = COALESCE($1, name),
                slug = COALESCE($2, slug),
                address_line1 = COALESCE($3, address_line1),
                address_line2 = COALESCE($4, address_line2),
                admin_email = COALESCE($5, admin_email),
                admin_name = COALESCE($6, admin_name),
                admin_phone = COALESCE($7, admin_phone),
                banner_url = COALESCE($8, banner_url),
                city = COALESCE($9, city),
                country = COALESCE($10, country),
                currency = COALESCE($11, currency),
                language = COALESCE($12, language),
                logo_url = COALESCE($13, logo_url),
                primary_color = COALESCE($14, primary_color),
                school_motto = COALESCE($15, school_motto),
                secondary_color = COALESCE($16, secondary_color),
                state = COALESCE($17, state),
                status = COALESCE($18, status),
                timezone = COALESCE($19, timezone),
                website = COALESCE($20, website),
                admission_prefix = COALESCE($21, admission_prefix),
                staff_id_prefix = COALESCE($22, staff_id_prefix),
                postal_code = COALESCE($23, postal_code),
                updated_at = NOW()
            WHERE id = $24 AND is_active = true
            "#,
        )
        .bind(&request.name)
        .bind(&request.slug)
        .bind(&request.address_line1)
        .bind(&request.address_line2)
        .bind(&request.admin_email)
        .bind(&request.admin_name)
        .bind(&request.admin_phone)
        .bind(&request.banner_url)
        .bind(&request.city)
        .bind(&request.country)
        .bind(&request.currency)
        .bind(&request.language)
        .bind(&request.logo_url)
        .bind(&request.primary_color)
        .bind(&request.school_motto)
        .bind(&request.secondary_color)
        .bind(&request.state)
        .bind(&request.status)
        .bind(&request.timezone)
        .bind(&request.website)
        .bind(&request.admission_prefix)
        .bind(&request.staff_id_prefix)
        .bind(&request.postal_code)
        .bind(school_id)
        .execute(pool)
        .await
        .map(|done| done.rows_affected() > 0)
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_track(
        pool: &PgPool,
        request: &CreateEducationTrackRequest,
    ) -> Result<EducationTrack, ApiError> {
        let now = Utc::now().naive_utc();
        let id = Uuid::new_v4();

        sqlx::query_as::<sqlx::Postgres, EducationTrack>(
            r#"
            INSERT INTO education_tracks (
                id, created_at, is_active, updated_at, school_id, description, name
            ) VALUES (
                $1, $2, true, $2, $3, $4, $5
            )
            RETURNING *
            "#,
        )
        .bind(id)
        .bind(now)
        .bind(request.school_id)
        .bind(&request.description)
        .bind(&request.name)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_department(
        pool: &PgPool,
        request: &CreateDepartmentRequest,
    ) -> Result<Department, ApiError> {
        let now = Utc::now().naive_utc();
        let id = Uuid::new_v4();

        sqlx::query_as::<sqlx::Postgres, Department>(
            r#"
            INSERT INTO departments (
                id, created_at, is_active, updated_at, school_id, description, name, track_id
            ) VALUES (
                $1, $2, true, $2, $3, $4, $5, $6
            )
            RETURNING *
            "#,
        )
        .bind(id)
        .bind(now)
        .bind(request.school_id)
        .bind(&request.description)
        .bind(&request.name)
        .bind(request.track_id)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_class(
        pool: &PgPool,
        request: &CreateClassRequest,
        track_id: Uuid,
    ) -> Result<SchoolClass, ApiError> {
        let now = Utc::now().naive_utc();
        let id = Uuid::new_v4();

        sqlx::query_as::<sqlx::Postgres, SchoolClass>(
            r#"
            INSERT INTO classes (
                id, created_at, is_active, updated_at,
                school_id, track_id, class_name, class_code, classroom_location,
                current_enrollment, department_id, grade_level, max_capacity,
                scoring_scheme, class_staff_id, term
            ) VALUES (
                $1, $2, true, $2,
                $3, $4, $5, $6, $7,
                $8, $9, $10, $11,
                $12, $13, $14
            )
            RETURNING *
            "#,
        )
        .bind(id)
        .bind(now)
        .bind(request.school_id)
        .bind(track_id)
        .bind(&request.class_name)
        .bind(&request.class_code)
        .bind(&request.classroom_location)
        .bind(request.current_enrollment)
        .bind(request.department_id)
        .bind(request.grade_level)
        .bind(request.max_capacity)
        .bind(&request.scoring_scheme)
        .bind(request.class_staff_id)
        .bind(&request.term)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn get_track(
        pool: &PgPool,
        school_id: Uuid,
        track_id: Uuid,
    ) -> Result<Option<EducationTrack>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, EducationTrack>(
            "SELECT * FROM education_tracks WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(track_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn get_department(
        pool: &PgPool,
        school_id: Uuid,
        department_id: Uuid,
    ) -> Result<Option<Department>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Department>(
            "SELECT * FROM departments WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(department_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn get_subject_exists(
        pool: &PgPool,
        school_id: Uuid,
        subject_id: Uuid,
    ) -> Result<bool, ApiError> {
        let has_school_id_column = sqlx::query_scalar::<sqlx::Postgres, bool>(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'subjects' AND column_name = 'school_id')",
        )
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        if has_school_id_column {
            sqlx::query_scalar::<sqlx::Postgres, bool>(
                "SELECT EXISTS(SELECT 1 FROM subjects WHERE id = $1 AND school_id = $2 AND is_active = true)",
            )
            .bind(subject_id)
            .bind(school_id)
            .fetch_one(pool)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))
        } else {
            sqlx::query_scalar::<sqlx::Postgres, bool>(
                "SELECT EXISTS(SELECT 1 FROM subjects WHERE id = $1 AND is_active = true)",
            )
            .bind(subject_id)
            .fetch_one(pool)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))
        }
    }

    pub async fn list_active_classes(
        pool: &PgPool,
        school_id: Uuid,
        class_ids: &[Uuid],
    ) -> Result<Vec<SchoolClass>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolClass>(
            "SELECT * FROM classes WHERE school_id = $1 AND is_active = true AND id = ANY($2)",
        )
        .bind(school_id)
        .bind(class_ids)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn save_school_subjects(
        pool: &PgPool,
        school_id: Uuid,
        subject_ids: &[Uuid],
    ) -> Result<Vec<SchoolSubject>, ApiError> {
        let mut tx = pool
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        for subject_id in subject_ids {
            let exists = Self::get_subject_exists(pool, school_id, *subject_id).await?;
            if !exists {
                return Err(ApiError::ValidationError(format!(
                    "subject {} not found or inactive",
                    subject_id
                )));
            }

            let now = Utc::now().naive_utc();
            sqlx::query(
                r#"
                INSERT INTO school_subjects (
                    id, created_at, is_active, updated_at, school_id, subject_id
                ) VALUES (
                    $1, $2, true, $2, $3, $4
                )
                ON CONFLICT (school_id, subject_id)
                DO UPDATE SET
                    is_active = true,
                    updated_at = EXCLUDED.updated_at
                "#,
            )
            .bind(Uuid::new_v4())
            .bind(now)
            .bind(school_id)
            .bind(subject_id)
            .execute(&mut *tx)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;
        }

        let saved = sqlx::query_as::<sqlx::Postgres, SchoolSubject>(
            r#"
            SELECT id, created_at, is_active, updated_at, school_id, subject_id
            FROM school_subjects
            WHERE school_id = $1 AND is_active = true AND subject_id = ANY($2)
            ORDER BY created_at ASC
            "#,
        )
        .bind(school_id)
        .bind(subject_ids)
        .fetch_all(&mut *tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(saved)
    }

    pub async fn get_school_data(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<serde_json::Value, ApiError> {
        sqlx::query_scalar::<sqlx::Postgres, serde_json::Value>(
            "SELECT to_jsonb(s.*) FROM schools s WHERE s.id = $1 AND s.is_active = true",
        )
        .bind(school_id)
        .fetch_one(pool)
        .await
        .map_err(|error| {
            if error.to_string().contains("no rows") {
                ApiError::NotFound(format!("School with id {} not found", school_id))
            } else {
                ApiError::DatabaseError(error.to_string())
            }
        })
    }
    pub async fn get_school_subject(
        pool: &PgPool,
        school_id: Uuid,
        school_subject_id: Uuid,
    ) -> Result<Option<SchoolSubject>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, SchoolSubject>(
            r#"
            SELECT id, created_at, is_active, updated_at, school_id, subject_id
            FROM school_subjects
            WHERE id = $1 AND school_id = $2 AND is_active = true
            LIMIT 1
            "#,
        )
        .bind(school_subject_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn get_class_subject(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
        school_subject_id: Uuid,
    ) -> Result<Option<ClassSubject>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassSubject>(
            r#"
            SELECT id, created_at, is_active, updated_at, school_id, class_id, staff_id, school_subject_id
            FROM class_subjects
            WHERE school_id = $1 AND class_id = $2 AND school_subject_id = $3 AND is_active = true
            LIMIT 1
            "#,
        )
        .bind(school_id)
        .bind(class_id)
        .bind(school_subject_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_class_subject(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
        school_subject_id: Uuid,
        staff_id: Option<Uuid>,
        assigned_by: Option<Uuid>,
    ) -> Result<ClassSubject, ApiError> {
        let id = Uuid::new_v4();
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, ClassSubject>(
            r#"
            INSERT INTO class_subjects (
                id, created_at, is_active, updated_at, school_id,
                assigned_at, assigned_by, class_id, staff_id, school_subject_id
            ) VALUES (
                $1, $2, true, $2, $3,
                $2, $4, $5, $6, $7
            )
            RETURNING id, created_at, is_active, updated_at, school_id, class_id, staff_id, school_subject_id
            "#,
        )
        .bind(id)
        .bind(now)
        .bind(school_id)
        .bind(assigned_by)
        .bind(class_id)
        .bind(staff_id)
        .bind(school_subject_id)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn unlink_class_from_subject(
        pool: &PgPool,
        school_subject_id: Uuid,
        class_id: Uuid,
    ) -> Result<Uuid, ApiError> {
        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM school_subjects WHERE id = $1"
        )
        .bind(school_subject_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::NotFound(format!("School subject not found: {}", e)))?;

        sqlx::query(
            "DELETE FROM class_subjects WHERE school_subject_id = $1 AND class_id = $2"
        )
        .bind(school_subject_id)
        .bind(class_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(school_id)
    }

    pub async fn deactivate_school_subject(
        pool: &PgPool,
        school_subject_id: Uuid,
    ) -> Result<Uuid, ApiError> {
        let mut tx = pool
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM school_subjects WHERE id = $1"
        )
        .bind(school_subject_id)
        .fetch_one(&mut *tx)
        .await
        .map_err(|e| ApiError::NotFound(format!("School subject not found: {}", e)))?;

        let now = chrono::Utc::now().naive_utc();

        sqlx::query(
            "UPDATE school_subjects SET is_active = false, updated_at = $1 WHERE id = $2"
        )
        .bind(now)
        .bind(school_subject_id)
        .execute(&mut *tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        sqlx::query(
            "UPDATE class_subjects SET is_active = false, updated_at = $1 WHERE school_subject_id = $2"
        )
        .bind(now)
        .bind(school_subject_id)
        .execute(&mut *tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(school_id)
    }

    pub async fn get_academic_structure(
        pool: &PgPool,
        school_id: Uuid,
    ) -> Result<AcademicStructureResponse, ApiError> {
        let tracks = sqlx::query_as::<sqlx::Postgres, EducationTrack>(
            "SELECT * FROM education_tracks WHERE school_id = $1 AND is_active = true ORDER BY name ASC",
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let departments = sqlx::query_as::<sqlx::Postgres, Department>(
            "SELECT * FROM departments WHERE school_id = $1 AND is_active = true ORDER BY name ASC",
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let classes = sqlx::query_as::<sqlx::Postgres, SchoolClass>(
            "SELECT * FROM classes WHERE school_id = $1 AND is_active = true ORDER BY class_name ASC",
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let mut track_nodes: Vec<AcademicTrackNode> = tracks
            .into_iter()
            .map(|t| AcademicTrackNode {
                id: t.id,
                name: t.name,
                description: t.description,
                departments: Vec::new(),
            })
            .collect();

        let mut dept_to_classes: HashMap<Uuid, Vec<ClassNode>> = HashMap::new();
        for c in classes {
            if let Some(dept_id) = c.department_id {
                dept_to_classes
                    .entry(dept_id)
                    .or_default()
                    .push(ClassNode {
                        id: c.id,
                        class_name: c.class_name,
                        class_code: c.class_code,
                        grade_level: c.grade_level,
                    });
            }
        }

        let mut track_to_depts: HashMap<Uuid, Vec<DepartmentNode>> = HashMap::new();
        for d in departments {
            if let Some(track_id) = d.track_id {
                track_to_depts
                    .entry(track_id)
                    .or_default()
                    .push(DepartmentNode {
                        id: d.id,
                        name: d.name,
                        description: d.description,
                        classes: dept_to_classes.remove(&d.id).unwrap_or_default(),
                    });
            }
        }

        for track_node in &mut track_nodes {
            if let Some(depts) = track_to_depts.remove(&track_node.id) {
                track_node.departments = depts;
            }
        }

        Ok(AcademicStructureResponse {
            school_id,
            tracks: track_nodes,
        })
    }

    pub async fn list_all_subjects(pool: &PgPool) -> Result<Vec<Subject>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Subject>(
            "SELECT * FROM subjects WHERE is_active = true ORDER BY subject_name ASC",
        )
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn get_school_subjects_with_classes(
        pool: &PgPool,
        school_id: Uuid,
        filter: SchoolSubjectFilter,
    ) -> Result<Vec<SchoolSubjectResponse>, ApiError> {
        #[derive(sqlx::FromRow)]
        struct SubjectRow {
            id: Uuid,
            subject_id: Uuid,
            name: String,
            code: Option<String>,
            description: Option<String>,
        }

        #[derive(sqlx::FromRow)]
        struct AssignmentRow {
            school_subject_id: Option<Uuid>,
            class_id: Uuid,
            class_name: String,
            dept_name: Option<String>,
            track_name: Option<String>,
        }

        let subjects = sqlx::query_as::<sqlx::Postgres, SubjectRow>(
            r#"
            SELECT 
                ss.id,
                s.id AS subject_id,
                s.subject_name AS name,
                s.subject_code AS code,
                s.description
            FROM school_subjects ss
            JOIN subjects s ON ss.subject_id = s.id
            WHERE ss.school_id = $1 AND ss.is_active = true
            ORDER BY s.subject_name ASC
            "#,
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let assignments = sqlx::query_as::<sqlx::Postgres, AssignmentRow>(
            r#"
            SELECT 
                cs.school_subject_id,
                c.id AS class_id,
                c.class_name,
                d.name AS dept_name,
                t.name AS track_name
            FROM class_subjects cs
            JOIN classes c ON cs.class_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            LEFT JOIN education_tracks t ON c.track_id = t.id
            WHERE cs.school_id = $1 AND cs.is_active = true
            AND ($2::text IS NULL OR c.class_name ILIKE ('%' || $2::text || '%'))
            AND ($3::uuid IS NULL OR d.id = $3::uuid)
            AND ($4::uuid IS NULL OR t.id = $4::uuid)
            "#,
        )
        .bind(school_id)
        .bind(filter.class_name.as_deref())
        .bind(filter.department_id)
        .bind(filter.track_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        let mut assignments_map: HashMap<Uuid, Vec<LinkedClassResponse>> = HashMap::new();
        for row in assignments {
            if let Some(ss_id) = row.school_subject_id {
                assignments_map.entry(ss_id).or_default().push(LinkedClassResponse {
                    id: row.class_id,
                    name: row.class_name,
                    department_name: row.dept_name,
                    track_name: row.track_name,
                });
            }
        }

        let mut result = Vec::new();
        for s in subjects {
            let linked_classes = assignments_map.remove(&s.id).unwrap_or_default();
            let has_filter = filter.class_name.is_some() || filter.department_id.is_some() || filter.track_id.is_some();
            if has_filter && linked_classes.is_empty() {
                continue;
            }
            result.push(SchoolSubjectResponse {
                id: s.id,
                subject_id: s.subject_id,
                name: s.name,
                code: s.code,
                description: s.description,
                linked_classes,
            });
        }

        Ok(result)
    }
}
