# School Management API Documentation

Base URL: `http://localhost:8000`

## Endpoints

### Health & Status

#### Get Server Health
```
GET /api/health

Response (200):
{
  "status": "ok",
  "database": "connected",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### Get API Information
```
GET /

Response (200):
{
  "message": "School Backend API",
  "version": "0.1.0",
  "endpoints": {
    "health": "/api/health",
    "tenants": "/api/tenants",
    "schools": "/api/schools"
  }
}
```

## Data Models

### HealthResponse
```json
{
  "status": "string",
  "database": "string",
  "timestamp": "datetime"
}
```

### User
```json
{
  "id": "uuid",
  "school_id": "uuid",
  "email": "string",
  "first_name": "string",
  "last_name": "string",
  "role": "string",
  "is_active": "boolean",
  "created_at": "datetime"
}
```

### Student
```json
{
  "id": "uuid",
  "school_id": "uuid",
  "first_name": "string",
  "last_name": "string",
  "email": "string",
  "enrollment_status": "string",
  "created_at": "datetime"
}
```

### School
```json
{
  "id": "uuid",
  "tenant_id": "uuid",
  "name": "string",
  "email": "string",
  "phone": "string",
  "created_at": "datetime"
}
```

## Error Responses

All errors follow this format:

```json
{
  "error": "error_code",
  "message": "Human readable error message",
  "timestamp": "datetime"
}
```

### Common Error Codes
- `INVALID_REQUEST`: Request validation failed
- `NOT_FOUND`: Resource not found
- `UNAUTHORIZED`: Authentication required
- `FORBIDDEN`: Insufficient permissions
- `INTERNAL_SERVER_ERROR`: Server error
- `DATABASE_ERROR`: Database operation failed

## Response Codes

- `200` - Success
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `500` - Internal Server Error
- `503` - Service Unavailable

## Authentication

Authentication is via JWT tokens in the `Authorization` header:

```
Authorization: Bearer <jwt_token>
```

## Rate Limiting

Currently no rate limiting is implemented. Rate limiting will be added in future versions.

## Implementation Status

The API structure is defined but handlers need to be implemented:

- [x] Health check endpoint
- [ ] User management endpoints
- [ ] Student management endpoints
- [ ] School management endpoints
- [ ] Authentication endpoints
- [ ] Course management endpoints
- [ ] Enrollment endpoints

## Testing Endpoints

### Using cURL

```bash
# Health check
curl http://localhost:8000/api/health

# Root endpoint
curl http://localhost:8000/
```

### Using HTTPie

```bash
# Health check
http GET http://localhost:8000/api/health

# Root endpoint
http GET http://localhost:8000/
```

### Using Postman

1. Create a new collection "School API"
2. Add requests:
   - Name: "Health Check"
     - Method: GET
     - URL: http://localhost:8000/api/health
   - Name: "API Info"
     - Method: GET
     - URL: http://localhost:8000/

## Notes

- All timestamps are in UTC (ISO 8601 format)
- All IDs are UUIDs (version 4)
- All requests use JSON content type
- Authentication will be implemented using JWT
- CORS is enabled for development
