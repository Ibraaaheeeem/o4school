# School Management Backend - Rust

A high-performance, type-safe Rust backend for the school management system built with **Actix-web**, **SQLx**, and **PostgreSQL**.

## Project Structure

```
src/
├── main.rs                 # Application entry point
├── lib.rs                  # Library root
├── config/
│   └── mod.rs             # Configuration management
├── db/
│   └── mod.rs             # Database connection & utilities
├── errors/
│   └── mod.rs             # Error types & handling
├── handlers/
│   └── mod.rs             # HTTP request handlers
├── middleware/
│   └── mod.rs             # Request middleware
├── models/
│   └── mod.rs             # Data models & DTOs
└── utils/
    └── mod.rs             # Utility functions
```

## Features

- ✅ Async request handling with Actix-web
- ✅ Type-safe database queries with SQLx
- ✅ PostgreSQL support with connection pooling
- ✅ Comprehensive error handling
- ✅ JWT authentication ready (dependencies included)
- ✅ Request validation with Serde
- ✅ Structured logging
- ✅ CORS support
- ✅ Password hashing with bcrypt

## Prerequisites

- **Rust** 1.70+ ([Install](https://rustup.rs/))
- **PostgreSQL** 12+ ([Install](https://www.postgresql.org/download/))
- **Cargo** (comes with Rust)

## Setup Instructions

### 1. Clone or Initialize the Project

```bash
cd /home/abuhaneefayn/Desktop/4school/rust
```

### 2. Configure Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your database credentials
nano .env
```

**Required environment variables:**
- `DATABASE_URL`: PostgreSQL connection string
- `SERVER_HOST`: Server address (default: 127.0.0.1)
- `SERVER_PORT`: Server port (default: 8000)
- `RUST_LOG`: Log level (default: info)

### 3. Create Database

```bash
# Create the database (adjust connection string as needed)
createdb school_management

# If using different postgres user
createdb -U postgres school_management
```

### 4. Build the Project

```bash
# Download dependencies and build
cargo build

# Or build in release mode for better performance
cargo build --release
```

### 5. Run the Server

```bash
# Development mode
cargo run

# Release mode (optimized)
cargo run --release

# With specific environment
DATABASE_URL=postgresql://user:password@localhost/school_management cargo run
```

The server will start on `http://127.0.0.1:8000` by default.

## API Endpoints

### Health Check
- **GET** `/api/health` - Server and database health status
- Response: `{"status": "ok", "database": "connected", "timestamp": "..."}`

### Root Endpoint
- **GET** `/` - API information and available endpoints

## Development

### Running Tests
```bash
cargo test
```

### Code Formatting
```bash
cargo fmt
```

### Linting
```bash
cargo clippy
```

### Check for Compilation Issues
```bash
cargo check
```

## Database Setup (for manual PostgreSQL)

```sql
-- Create database if using raw SQL
CREATE DATABASE school_management;

-- Connect and create tables (structure defined in database schema)
\c school_management
-- Run your migration scripts here
```

## Docker Setup (Optional)

### Using Docker Compose

```bash
# Start PostgreSQL
docker-compose up -d

# Build Docker image
docker build -t school-backend .

# Run container
docker run -p 8000:8000 --env-file .env school-backend
```

## Dependencies

Key dependencies include:
- **actix-web** (4.x): Web framework
- **tokio** (1.x): Async runtime
- **sqlx** (0.7): Database driver
- **serde**: JSON serialization
- **jsonwebtoken**: JWT authentication
- **bcrypt**: Password hashing
- **chrono**: Date/time handling
- **uuid**: Unique identifier generation

## Project Configuration

All configuration is managed through:
- **.env file**: Environment-specific settings
- **src/config/mod.rs**: Configuration struct

## Error Handling

The project uses a custom error type with proper HTTP status code mapping. See `src/errors/mod.rs` for details.

## Logging

Structured logging is configured via `env_logger`. Control verbosity with `RUST_LOG`:
```bash
RUST_LOG=debug cargo run     # Detailed logging
RUST_LOG=info cargo run      # Normal logging
RUST_LOG=warn cargo run      # Warnings only
```

## Build Commands

```bash
# Check without building
cargo check

# Build (debug)
cargo build

# Build (release - optimized)
cargo build --release

# Clean build artifacts
cargo clean

# Generate documentation
cargo doc --open
```

## Database Models

The schema supports:
- **Schools**: Multi-tenant school management
- **Users**: Teachers, admins, support staff
- **Students**: Student records
- **Courses**: Academic courses
- **Enrollments**: Student enrollment tracking
- **Departments**: School departments
- **Roles**: User role management
- **Permissions**: Fine-grained access control

## Next Steps

1. **Database Setup**: Run migrations to create tables
2. **API Implementation**: Add handlers in `src/handlers/`
3. **Authentication**: Implement JWT in middleware
4. **Validation**: Add request validation rules
5. **Testing**: Create comprehensive test suite
6. **Documentation**: Generate API docs with OpenAPI/Swagger

## Troubleshooting

### Database Connection Issues
```
Error: failed to connect to database
Solution: 
- Check DATABASE_URL in .env
- Verify PostgreSQL is running
- Check database credentials
```

### Port Already in Use
```
Error: Address already in use
Solution:
- Change SERVER_PORT in .env
- Kill process using the port: lsof -i :8000 | kill -9 <PID>
```

### Build Errors
```bash
# Clear build cache
cargo clean

# Rebuild
cargo build
```

## License

[Your License Here]

## Support

For issues and questions, refer to:
- [Actix-web Documentation](https://actix.rs/)
- [SQLx Documentation](https://github.com/launchbadge/sqlx)
- [Tokio Documentation](https://tokio.rs/)
