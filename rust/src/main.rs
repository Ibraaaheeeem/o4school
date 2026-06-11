use actix_web::{web, App, HttpServer, middleware::Logger};
use env_logger::Env;

use school_backend::{config::Config, db::Database, handlers};

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    // Initialize logger
    env_logger::Builder::from_env(Env::default().default_filter_or("info")).init();

    // Load configuration
    let config = Config::from_env();
    let addr = format!("{}:{}", config.server_host, config.server_port);

    log::info!("Starting School Backend Server on {}", addr);
    log::info!("Connecting to database: {}", config.database_url);

    // Initialize database
    let db = Database::new(&config.database_url)
        .await
        .expect("Failed to connect to database");

    log::info!("Database connection successful");

    // Health check
    db.health_check()
        .await
        .expect("Database health check failed");

    log::info!("Database health check passed");

    // Start HTTP server
    HttpServer::new(move || {
            App::new()
            .app_data(web::Data::new(db.clone()))
            .wrap(Logger::default())
            .wrap(school_backend::middleware::AuthMiddleware::new())
            .service(
                web::scope("/api")
                    .route("/health", web::get().to(handlers::health_check))
                    // Authentication routes
                    .service(
                        web::scope("/auth")
                            .route("/sign-up", web::post().to(handlers::auth::sign_up))
                            .route("/sign-in", web::post().to(handlers::auth::sign_in))
                            .route("/verify-email", web::post().to(handlers::auth::verify_email))
                            .route("/activate", web::post().to(handlers::auth::activate_account))
                            .route("/send-otp", web::post().to(handlers::auth::send_otp))
                            .route("/verify-otp", web::post().to(handlers::auth::verify_otp))
                            .route("/create-role-user", web::post().to(handlers::auth::create_role_user))
                            .route("/dashboard", web::get().to(handlers::dashboard::get_dashboard))
                            // Students
                            .service(
                                web::scope("/students")
                                    .route("", web::get().to(handlers::students::list_students))
                                    .route("", web::post().to(handlers::students::create_student))
                                    .route("/with-user", web::post().to(handlers::students::create_student_with_user))
                                    .route("/classes/assignments/{assignment_id}", web::delete().to(handlers::students::delete_student_class_assignment))
                                    .route("/{id}", web::get().to(handlers::students::get_student))
                                    .route("/{id}", web::put().to(handlers::students::update_student))
                                    .route("/{id}/classes/assign", web::post().to(handlers::students::assign_student_classes))
                                    .route("/{id}/classes", web::get().to(handlers::students::get_student_class_assignments))
                                    .route("/{id}", web::delete().to(handlers::students::delete_student))
                            )
                            // Parents
                            .service(
                                web::scope("/parents")
                                    .route("", web::get().to(handlers::parents::list_parents))
                                    .route("", web::post().to(handlers::parents::create_parent))
                                    .route("/with-user", web::post().to(handlers::parents::create_parent_with_user))
                                    .route("/students/assignments/{assignment_id}", web::delete().to(handlers::parents::delete_parent_student_assignment))
                                    .route("/{id}", web::get().to(handlers::parents::get_parent))
                                    .route("/{id}", web::put().to(handlers::parents::update_parent))
                                    .route("/{id}/students/assign", web::post().to(handlers::parents::assign_parent_students))
                                    .route("/{id}", web::delete().to(handlers::parents::delete_parent))
                            )
                            // Staff
                            .service(
                                web::scope("/staff")
                                    .route("", web::get().to(handlers::staff::list_staff))
                                    .route("", web::post().to(handlers::staff::create_staff))
                                    .route("/with-user", web::post().to(handlers::staff::create_staff_with_user))
                                    .route("/classes/assignments/{assignment_id}", web::delete().to(handlers::staff::delete_staff_class_assignment))
                                    .route("/subjects/assignments/{assignment_id}", web::delete().to(handlers::staff::delete_staff_subject_assignment))
                                    .route("/{id}", web::get().to(handlers::staff::get_staff))
                                    .route("/{id}", web::put().to(handlers::staff::update_staff))
                                    .route("/{id}/classes/assign", web::post().to(handlers::staff::assign_staff_classes))
                                    .route("/{id}/subjects/assign", web::post().to(handlers::staff::assign_staff_subjects))
                                    .route("/{id}", web::delete().to(handlers::staff::delete_staff))
                            )
                            // Users
                            .service(
                                web::scope("/users")
                                    .route("", web::get().to(handlers::users::list_school_users))
                                    .route("/{id}/deactivate", web::put().to(handlers::users::deactivate_user))
                                    .route("/{id}/activate", web::put().to(handlers::users::activate_user))
                                    .route("/{id}/deverify", web::put().to(handlers::users::deverify_user))
                                    .route("/{id}/activation-reminder", web::post().to(handlers::users::send_activation_reminder))
                            )
                                    // Schedule
                                    .service(
                                    web::scope("/schedule")
                                        .route("/current", web::get().to(handlers::schedule::get_current_schedule))
                                        .route("/sessions", web::get().to(handlers::schedule::list_academic_sessions))
                                        .route("/sessions", web::post().to(handlers::schedule::create_academic_session))
                                        .route("/sessions/{id}", web::put().to(handlers::schedule::update_academic_session))
                                        .route("/sessions/{id}", web::delete().to(handlers::schedule::delete_academic_session))
                                        .route("/sessions/{id}/terms", web::get().to(handlers::schedule::get_terms_in_session))
                                        .route("/terms", web::post().to(handlers::schedule::create_term))
                                        .route("/terms/{id}", web::put().to(handlers::schedule::update_term))
                                        .route("/terms/{id}", web::delete().to(handlers::schedule::delete_term))
                                        .route("/calendar-events", web::post().to(handlers::schedule::create_calendar_event))
                                        .route("/calendar-events", web::get().to(handlers::schedule::list_calendar_events))
                                        .route("/calendar-events/{id}", web::get().to(handlers::schedule::get_calendar_event))
                                        .route("/calendar-events/{id}", web::put().to(handlers::schedule::update_calendar_event))
                                        .route("/calendar-events/{id}", web::delete().to(handlers::schedule::delete_calendar_event))
                                        .route("/school-timetable-items", web::post().to(handlers::schedule::create_school_timetable_item))
                                        .route("/school-timetable-items", web::get().to(handlers::schedule::list_school_timetable_items))
                                        .route("/school-timetable-items/{id}", web::get().to(handlers::schedule::get_school_timetable_item))
                                        .route("/school-timetable-items/{id}", web::put().to(handlers::schedule::update_school_timetable_item))
                                        .route("/school-timetable-items/{id}", web::delete().to(handlers::schedule::delete_school_timetable_item))
                                    )
                                    // Finance
                                    .service(
                                        web::scope("/finance")
                                            .route("/fee-items", web::get().to(handlers::finance::list_fee_items))
                                            .route("/fee-items", web::post().to(handlers::finance::create_fee_item))
                                            .route("/settlements/manual", web::post().to(handlers::finance::create_manual_settlement))
                                            .route("/fee-items/{id}", web::put().to(handlers::finance::update_fee_item))
                                            .route("/fee-items/{id}", web::delete().to(handlers::finance::delete_fee_item))
                                            .route("/fee-items/{id}/class-assignments", web::get().to(handlers::finance::list_class_fee_item_assignments))
                                            .route("/fee-items/{id}/class-assignments", web::post().to(handlers::finance::upsert_class_fee_item_assignment))
                                            .route("/fee-items/{id}/class-assignments/{class_id}", web::delete().to(handlers::finance::delete_class_fee_item_assignment))
                                            .route("/fee-items/{id}/apply-to-student", web::post().to(handlers::finance::apply_optional_fee_item_to_student))
                                            .route("/student-optional-fees/{id}/lock", web::put().to(handlers::finance::lock_student_optional_fee))
                                            .route("/student-optional-fees/{id}/unlock", web::put().to(handlers::finance::unlock_student_optional_fee))
                                            .route("/student-optional-fees/{id}", web::delete().to(handlers::finance::delete_student_optional_fee))
                                    )
                                    // Assessment endpoints
                                    .configure(handlers::assessment::configure)
                                        // School package endpoints
                                        .configure(handlers::school_package::configure)
                            .route("/role/student-classes", web::put().to(handlers::auth::update_student_classes))
                            .route("/forgot-password", web::post().to(handlers::auth::forgot_password))
                            .route("/reset-password", web::post().to(handlers::auth::reset_password))
                            .route("/logout", web::post().to(handlers::auth::logout))
                    )
            )
            .route("/", web::get().to(handlers::root))
    })
    .bind(&addr)?
    // Limit workers to 1 to reduce memory usage in limited environments/tests
    .workers(1)
    .run()
    .await
}

