# P0 Status

## Source implementation

| Capability | Status | Evidence |
|---|---|---|
| Spring Boot Maven project | IMPLEMENTED | `backend/pom.xml` and application class |
| Java 17 configuration | IMPLEMENTED | Maven property and Temurin 17 Docker image |
| MySQL schema | IMPLEMENTED | `V1__identity_schema.sql` |
| Flyway migrations | IMPLEMENTED | `spring.flyway` configuration |
| MongoDB audit collection | IMPLEMENTED | `AuditEventDocument` and repository |
| BCrypt | IMPLEMENTED | `BCryptPasswordEncoder` |
| JWT | IMPLEMENTED | `JwtService`, filter, security chain |
| Environment admin bootstrap | IMPLEMENTED | `DevelopmentAdminBootstrap` |
| Login/current user/password change | IMPLEMENTED | `AuthController` |
| Role authorization | IMPLEMENTED | method security and request rules |
| Employee creation/update/status/reset | IMPLEMENTED | `EmployeeService` and controller |
| AOP audit interception | IMPLEMENTED | `AuditAction` and `AuditAspect` |
| SHA-256 hash chain | IMPLEMENTED | `AuditLedgerService` |
| Integrity verification | IMPLEMENTED | `/api/integrity/latest` and verification endpoint |
| Readiness API | IMPLEMENTED | `/api/health/readiness` and `/api/health/services` |
| STOMP endpoint/auth interceptor | IMPLEMENTED | `/ws`, inbound JWT interceptor, ping mapping |
| Docker Compose | IMPLEMENTED | root `docker-compose.yml` |
| Runtime startup | UNVERIFIED | Docker/Maven runtime was not available in the tool environment |
| P0 integration tests | UNVERIFIED | Source test created; execution unavailable |

## Exact commands to run locally

```bash
cp .env.example .env
docker compose up --build mysql mongodb backend
curl http://localhost:8080/api/health/readiness
```

For the AI profile:

```bash
docker compose --profile ai up --build
```

For backend tests:

```bash
cd backend
mvn test
```

For ML tests:

```bash
cd ml-service
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest
```

## Remaining P0 verification

- Execute Maven compilation and tests.
- Start MySQL and MongoDB with Docker health checks.
- Bootstrap a local admin from environment variables.
- Test login, `/api/auth/me`, role rejection, and password change.
- Create an employee and inspect both MySQL rows and MongoDB audit event.
- Verify a valid hash chain.
- Mutate a MongoDB audit document and verify that the integrity endpoint returns `FAILED`.
- Connect a STOMP client with a JWT and test `/app/ping` to `/user/queue/pong`.