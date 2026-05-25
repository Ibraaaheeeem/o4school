# Rust School Backend - Layered Architecture Guide

## Architecture Overview

This project follows a **4-tier layered architecture** pattern for clean, maintainable, and scalable code:

```
┌─────────────────────────────────────┐
│   API Layer (Handlers)              │
│   - Route handlers                  │
│   - Request/Response mapping        │
│   - HTTP status codes               │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Service Layer (Business Logic)    │
│   - Validation                      │
│   - Business rules                  │
│   - Transaction orchestration       │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Repository Layer (Data Access)    │
│   - Database queries                │
│   - CRUD operations                 │
│   - Query building                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Database Layer                    │
│   - PostgreSQL connection pool      │
│   - Health checks                   │
└─────────────────────────────────────┘
```

## Directory Structure

```
src/
├── models/              # Data structures, enums, request/response types
│   └── mod.rs
├── db/                  # Database layer
│   ├── mod.rs          # Database connection management
│   └── repositories/    # Repository pattern implementations
│       ├── mod.rs
│       ├── user_repository.rs
│       ├── school_repository.rs
│       └── student_repository.rs
├── services/            # Business logic layer
│   ├── mod.rs
│   ├── health_service.rs
│   ├── user_service.rs
│   ├── school_service.rs
│   └── student_service.rs
├── handlers/            # API endpoints layer
│   └── mod.rs
├── errors/              # Error types and handling
│   └── mod.rs
├── middleware/          # HTTP middleware (auth, logging, etc)
│   └── mod.rs
├── config/              # Configuration management
│   └── mod.rs
└── utils/               # Utility functions
    └── mod.rs
```

## Layer Responsibilities

### 1. **Models Layer** (`src/models/`)
- Define data structures
- Database entity models
- Request/response DTOs
- Enums for status/types

```rust
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct User {
    pub id: Uuid,
    pub email: String,
    // ... other fields
}
```

### 2. **Repository Layer** (`src/db/repositories/`)
- Pure data access logic
- SQL query construction
- CRUD operations
- No business logic

```rust
pub struct UserRepository;

impl UserRepository {
    pub async fn get_by_id(pool: &PgPool, user_id: Uuid) -> Result<User, ApiError> {
        // SQL query
    }
}
```

### 3. **Service Layer** (`src/services/`)
- Business logic implementation
- Input validation
- Orchestrate repository calls
- Handle transactions
- Business rule enforcement

```rust
pub struct UserService;

impl UserService {
    pub async fn create_user(db: &Database, user: User) -> Result<User, ApiError> {
        // Validate
        if user.email.is_empty() {
            return Err(ApiError::ValidationError("Email required".into()));
        }
        // Call repository
        UserRepository::create(db.pool(), &user).await
    }
}
```

### 4. **Handler Layer** (`src/handlers/`)
- HTTP request/response mapping
- Route definitions
- HTTP status code selection
- Parameter extraction

```rust
pub async fn create_user_handler(
    db: web::Data<Database>,
    Json(req): Json<CreateUserRequest>,
) -> Result<HttpResponse, ApiError> {
    let user = UserService::create_user(&db, user).await?;
    Ok(HttpResponse::Created().json(user))
}
```

## How to Add a New Feature

### Example: Adding a "Classes" Feature

#### Step 1: Add Model (in `src/models/mod.rs`)
```rust
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolClass {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub level: String,
    pub capacity: i32,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub is_active: bool,
}
```

#### Step 2: Create Repository (`src/db/repositories/class_repository.rs`)
```rust
pub struct ClassRepository;

impl ClassRepository {
    pub async fn get_by_id(pool: &PgPool, class_id: Uuid) -> Result<SchoolClass, ApiError> {
        // Implementation
    }

    pub async fn create(pool: &PgPool, class: &SchoolClass) -> Result<SchoolClass, ApiError> {
        // Implementation
    }
    
    pub async fn list_by_school(pool: &PgPool, school_id: Uuid) -> Result<Vec<SchoolClass>, ApiError> {
        // Implementation
    }
}
```

Update `src/db/repositories/mod.rs`:
```rust
pub mod class_repository;
pub use class_repository::ClassRepository;
```

#### Step 3: Create Service (`src/services/class_service.rs`)
```rust
pub struct ClassService;

impl ClassService {
    pub async fn create_class(db: &Database, school_id: Uuid, name: String) -> Result<SchoolClass, ApiError> {
        // Validate
        if name.is_empty() {
            return Err(ApiError::ValidationError("Class name required".into()));
        }
        
        // Create model
        let class = SchoolClass {
            id: Uuid::new_v4(),
            school_id,
            name,
            // ... other fields
            created_at: Utc::now(),
            updated_at: Utc::now(),
            is_active: true,
        };
        
        // Call repository
        ClassRepository::create(db.pool(), &class).await
    }

    pub async fn list_classes(db: &Database, school_id: Uuid) -> Result<Vec<SchoolClass>, ApiError> {
        ClassRepository::list_by_school(db.pool(), school_id).await
    }
}
```

Update `src/services/mod.rs`:
```rust
pub mod class_service;
pub use class_service::ClassService;
```

#### Step 4: Create Handlers (`src/handlers/mod.rs` or new file)
```rust
pub async fn create_class_handler(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
    Json(req): Json<CreateClassRequest>,
) -> Result<HttpResponse, ApiError> {
    let class = ClassService::create_class(&db, *school_id, req.name).await?;
    Ok(HttpResponse::Created().json(class))
}

pub async fn list_classes_handler(
    db: web::Data<Database>,
    school_id: web::Path<Uuid>,
) -> Result<HttpResponse, ApiError> {
    let classes = ClassService::list_classes(&db, *school_id).await?;
    Ok(HttpResponse::Ok().json(classes))
}
```

#### Step 5: Register Routes (`src/main.rs`)
```rust
HttpServer::new(move || {
    App::new()
        // ... existing routes
        .service(
            web::scope("/api/schools/{school_id}/classes")
                .route("", web::post().to(handlers::create_class_handler))
                .route("", web::get().to(handlers::list_classes_handler))
        )
})
```

## Data Flow Example: Creating a Student

```
HTTP POST /api/students
    ↓
Handler: student_create_handler()
    ├─ Extract request data
    ├─ Call StudentService::enroll_student()
    │   ├─ Validate input (check school exists, dates valid)
    │   ├─ Generate student_id
    │   ├─ Create Student model
    │   └─ Call StudentRepository::create()
    │       └─ Execute SQL INSERT
    └─ Return HttpResponse::Created(student)
```

## Key Principles

1. **Separation of Concerns**: Each layer has a single responsibility
2. **Dependency Direction**: Always flow downward (Handlers → Services → Repositories)
3. **No SQL in Services**: Services call repositories for data access
4. **Validation**: Both in services and handlers (handlers for HTTP, services for business)
5. **Error Handling**: Use `Result<T, ApiError>` throughout
6. **Immutability**: Prefer immutable data when possible
7. **Async/Await**: Use throughout for non-blocking operations

## Testing

Each layer can be tested independently:

```rust
#[cfg(test)]
mod tests {
    #[tokio::test]
    async fn test_user_service_validation() {
        let result = UserService::create_user(&db, invalid_user).await;
        assert!(result.is_err());
    }
}
```

## Error Handling

Consistent error handling across layers:

```rust
// Repository - map database errors
sqlx::query().execute(pool).await.map_err(|e| {
    ApiError::DatabaseError(e.to_string())
})?;

// Service - add business validation
if user.email.is_empty() {
    return Err(ApiError::ValidationError("Email required".into()));
}

// Handler - convert to HTTP response
let user = UserService::create_user(&db, user).await?;
Ok(HttpResponse::Created().json(user))
```

## Performance Considerations

1. **Connection Pooling**: Already configured via `PgPool`
2. **Pagination**: Implement in repository layer to limit data transfer
3. **Query Optimization**: Use indexes for frequently queried fields
4. **Caching**: Add service-level cache for read-heavy operations
5. **Async Database Calls**: Use `sqlx` for non-blocking database access

## Adding Authentication/Authorization

Middleware layer handles auth:

```rust
// src/middleware/auth.rs
pub async fn verify_token(req: ServiceRequest) -> Result<ServiceRequest, Error> {
    let token = extract_token(&req)?;
    verify_jwt(&token)?;
    Ok(req)
}

// In main.rs
.wrap(middleware::Logger::default())
.wrap(AuthMiddleware)
```

Then use in handlers to verify user context before calling services.
