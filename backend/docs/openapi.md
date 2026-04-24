# Again Spring OpenAPI Documentation

## Overview

The Again Spring backend exposes a comprehensive REST API documented via OpenAPI 3.0 (Swagger).

## Accessing Documentation

### Development Environment

When running the backend locally:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI YAML**: http://localhost:8080/v3/api-docs.yaml
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Export to Shared Schema

To export the OpenAPI specification to the shared schemas directory:

```bash
./backend/scripts/export-openapi.sh
```

This script:
1. Checks if backend is running
2. Fetches the OpenAPI YAML from `/v3/api-docs.yaml`
3. Writes to `../shared/schemas/openapi.yaml`

## API Structure

### Security

All authenticated endpoints require:

```
Authorization: Bearer {accessToken}
```

The `{accessToken}` is obtained via:
- POST `/api/auth/signup` — user registration
- POST `/api/auth/login` — user login
- POST `/api/auth/guest` — guest token (short-lived, 1 hour)

### Error Responses

Standard error format across all endpoints:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "timestamp": "2026-04-24T10:30:00Z",
    "requestId": "req_abc123"
  }
}
```

Common error codes:
- `INVALID_INPUT` (400) — Validation failed
- `UNAUTHORIZED` (401) — Missing or invalid token
- `FORBIDDEN` (403) — Permission denied
- `NOT_FOUND` (404) — Resource not found
- `CRISIS_DETECTED` (422) — Safety guard triggered
- `INTERNAL_ERROR` (500) — Server error

### Endpoints

See `API_SPEC.md` for comprehensive endpoint documentation:
- Auth
- User Management
- Session Management
- Mediation (Multi-turn)
- Reports
- Relationships (Neo4j Graph)

## Schema Generation

The OpenAPI schema is auto-generated from:
- Spring Boot controller annotations (`@RestController`, `@RequestMapping`)
- Jackson DTOs and annotations
- Javadoc comments

### Custom Enrichment

Phase 14 adds custom OpenAPI enhancements via `OpenApiExamples.java`:
- Reusable `ErrorResponse` schema
- Bearer JWT security scheme
- Common response definitions

## Development Workflow

1. **Make API changes** in controllers or DTOs
2. **Run backend**: `./gradlew bootRun`
3. **Test in Swagger UI**: Navigate to http://localhost:8080/swagger-ui.html
4. **Export updated spec**: Run `./backend/scripts/export-openapi.sh`
5. **Commit** `shared/schemas/openapi.yaml` to version control

## Integration with Frontend

The frontend can:
- Generate TypeScript client stubs from `openapi.yaml` using tools like `openapi-generator`
- Display interactive API docs in development UI
- Validate request/response shapes at build time

Example (TypeScript codegen):

```bash
openapi-generator-cli generate \
  -i shared/schemas/openapi.yaml \
  -g typescript-axios \
  -o frontend/src/api-client
```

## Further Reading

- [SpringDoc OpenAPI](https://springdoc.org/)
- [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.3)
- `API_SPEC.md` — Canonical API specification
