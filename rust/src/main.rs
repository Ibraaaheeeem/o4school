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

