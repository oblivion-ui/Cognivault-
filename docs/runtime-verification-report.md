# CogniVault Runtime Verification Report

## Scope

This phase was limited to sanity review, concrete source fixes, and the available frontend build check. The tool environment exposes file operations and the frontend build runner, but it does not expose a shell, Maven runner, Docker daemon, Python interpreter, MySQL instance, MongoDB instance, ChromaDB, Ollama, or an HTTP/STOMP test client.

No backend, database, ML, Docker, or HTTP runtime result is claimed as verified.

## Phase A — Repository Sanity Check

### Reviewed

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V1__identity_schema.sql`
- `backend/src/main/java/com/cognivault/` source tree
- `backend/src/test/java/com/cognivault/` source tree
- `docker-compose.yml`
- `backend/Dockerfile`
- `ml-service/Dockerfile`
- `ml-service/requirements.txt`
- `ml-service/app/main.py`
- `Dockerfile.frontend`
- `.env.example`
- `src/api/`
- `src/websocket/`

### Concrete issues fixed

1. Audit aspect passed the complete argument array as a snapshot for single-argument service methods. It now snapshots the actual request object and loads the existing employee entity before update/status/credential mutations.
2. Audit snapshot sanitization now accepts arbitrary map types safely and removes password, secret, and token fields.
3. Audit unit tests now mutate a field participating in the hash calculation for the tamper case.
4. Dashboard P0 endpoint was added using real MySQL/Mongo data. Unsupported anomaly and approval counts are returned as `Unavailable`, not fabricated zeroes.
5. Empty risk distributions now render an explicit unavailable state rather than a 0% risk chart.
6. Frontend STOMP handling now distinguishes `NOT_CONFIGURED` from a disconnected connection and supports relative WebSocket URLs for the Docker/Nginx deployment.
7. MongoDB audit event and chain position indexes were added.

### Sanity findings remaining

- Backend runtime cannot be compiled in the current tool environment.
- Docker Compose cannot be parsed or started in the current tool environment.
- No real API requests can be issued from the current tool environment.
- No frontend browser smoke test can be executed from the current tool environment.

## Phase B — Build Verification

### Frontend

```text
npm run build: PASS
```

Evidence: Vite production build completed successfully after the sanity fixes.

### Backend

```text
mvn compile: NOT RUN
mvn test: NOT RUN
mvn package: NOT RUN
```

Exact blocker: no Maven executable or shell execution tool is available in this environment, and the repository does not contain `backend/mvnw` or `backend/mvnw.cmd`.

Exact local commands:

```bash
cd backend
mvn clean compile
mvn test
mvn package
```

PowerShell equivalent:

```powershell
Set-Location backend
mvn clean compile
mvn test
mvn package
```

## Phase C — Docker P0 Startup

```text
MySQL container: NOT RUN
MongoDB container: NOT RUN
Spring Boot container: NOT RUN
Flyway migration: NOT RUN
MySQL connection: NOT RUN
MongoDB connection: NOT RUN
GET /api/health/readiness: NOT RUN
GET /api/health/services: NOT RUN
```

Exact local commands:

```bash
cp .env.example .env
docker compose up --build mysql mongodb backend
docker compose ps
curl http://localhost:8080/api/health/readiness
curl http://localhost:8080/api/health/services
```

The backend Docker health check expects:

```text
GET /actuator/health/readiness
```

and requires the response to contain `UP`.

## Phase D — Empty Database Test

```text
Fresh MySQL/Mongo volumes: NOT RUN
Flyway initialization: NOT VERIFIED
MongoDB index initialization: NOT VERIFIED
No fake business data inserted: SOURCE VERIFIED / RUNTIME UNVERIFIED
Dashboard empty state: SOURCE VERIFIED / RUNTIME UNVERIFIED
```

Exact clean-volume command:

```bash
docker compose down -v
docker compose up --build mysql mongodb backend
```

The `down -v` command deletes local development data. Do not run it against a non-disposable environment.

## Phase E — Bootstrap Admin

```text
Environment bootstrap source: IMPLEMENTED
Administrator created at runtime: NOT VERIFIED
BCrypt hash runtime check: NOT VERIFIED
Duplicate prevention runtime check: NOT VERIFIED
```

The bootstrap mechanism is opt-in and uses `.env` values. It does not expose credentials in source.

Local verification command sequence:

```bash
docker compose up --build mysql mongodb backend
docker compose logs backend
```

Then use the configured username/password from the local `.env` only. Do not include the password in logs, screenshots, commits, or reports.

## Phase F — Authentication and RBAC

```text
Valid login: NOT RUN
Invalid password: NOT RUN
Unknown user: NOT RUN
JWT claims: SOURCE VERIFIED / RUNTIME UNVERIFIED
/api/auth/me: NOT RUN
Missing token 401: NOT RUN
Invalid token 401: NOT RUN
Insufficient role 403: NOT RUN
```

Example local requests after startup:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<BOOTSTRAP_USERNAME>","password":"<BOOTSTRAP_PASSWORD>"}'

curl -i http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

curl -i http://localhost:8080/api/employees \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

## Phase G — Employee Lifecycle

```text
Create employee runtime: NOT RUN
Retrieve employee runtime: NOT RUN
Update employee runtime: NOT RUN
Account transition runtime: NOT RUN
Credential reset runtime: NOT RUN
Force-change runtime: NOT RUN
MySQL persistence verification: NOT RUN
```

The exact request body is defined in `EmployeeDtos.CreateEmployeeRequest` and `docs/frontend-backend-contract.md`.

## Phase H/I — Audit and Hash Chain

```text
Real audited operation: NOT RUN
MongoDB audit persistence: NOT RUN
Valid chain runtime result: NOT RUN
Tampered document runtime result: NOT RUN
Restored document runtime result: NOT RUN
Post-restore linkage runtime result: NOT RUN
```

Source-level test names:

```text
AuditLedgerServiceTest.validChainPassesVerification
AuditLedgerServiceTest.tamperedHashFailsVerification
AuditLedgerServiceTest.subsequentEventLinksToPreviousHash
```

Run:

```bash
cd backend
mvn -Dtest=AuditLedgerServiceTest test
```

## Phase J — STOMP/WebSocket

```text
/ws connection: NOT RUN
JWT connection authentication: NOT RUN
/app/ping: NOT RUN
/user/queue/pong: NOT RUN
Unauthenticated rejection: NOT RUN
```

Source configuration:

```text
Endpoint: /ws
Application prefix: /app
Broker prefixes: /topic, /queue
User prefix: /user
Test message: /app/ping
Expected response: /user/queue/pong
```

## Phase K/L — ML and SHAP

```text
FastAPI process: NOT RUN
ML readiness: NOT RUN
Isolation Forest runtime: NOT RUN
SHAP runtime: NOT RUN
Python tests: NOT RUN
```

Exact local commands:

```bash
cd ml-service
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Then:

```bash
curl http://localhost:8000/health/readiness
```

## Phase M/N/O — ChromaDB, Ollama, and RAG

```text
ChromaDB process: NOT RUN
ChromaDB indexing: NOT RUN
Permission-filtered retrieval: NOT RUN
Ollama process: NOT RUN
Ollama model availability: NOT RUN
RAG end-to-end: NOT RUN
```

Exact local AI startup:

```bash
docker compose --profile ai up --build
docker compose exec ollama ollama pull llama3.2:3b
```

Native Windows Ollama alternative:

```powershell
ollama serve
ollama pull llama3.2:3b
```

Set `OLLAMA_URL=http://host.docker.internal:11434` for the containerized ML service when using native Windows Ollama.

## Phase P/Q — Frontend Integration and Security

```text
Frontend build: VERIFIED
Frontend against running backend: NOT RUN
Login browser flow: NOT RUN
Dashboard browser flow: NOT RUN
Employees browser flow: NOT RUN
Audit browser flow: NOT RUN
Integrity browser flow: NOT RUN
Service health browser flow: NOT RUN
STOMP browser status: NOT RUN
CORS browser verification: NOT RUN
Password leakage runtime scan: NOT RUN
```

The Docker frontend is configured with:

```text
VITE_API_BASE_URL=/api
VITE_WS_URL=/ws
VITE_ENABLE_MOCK=false
```

## Final Verification Matrix

| Component | Status | Evidence / blocker |
|---|---|---|
| Spring Boot | IMPLEMENTED / CONFIGURED | Source and Dockerfile exist; Maven runtime unavailable |
| MySQL | CONFIGURED | Compose health check exists; Docker unavailable |
| MongoDB | CONFIGURED | Compose health check exists; Docker unavailable |
| JWT | IMPLEMENTED | Source reviewed; runtime HTTP test not run |
| RBAC | IMPLEMENTED | Source reviewed; 401/403 tests not run |
| Audit | IMPLEMENTED | Source reviewed; Mongo runtime not run |
| Hash chain | IMPLEMENTED | Source tests exist; Maven tests not run |
| STOMP | IMPLEMENTED / CONFIGURED | Source reviewed; STOMP client not run |
| FastAPI | IMPLEMENTED / CONFIGURED | Source and requirements exist; Python unavailable |
| Isolation Forest | IMPLEMENTED | Endpoint/test source exists; runtime unavailable |
| SHAP | IMPLEMENTED | Endpoint/test source exists; runtime unavailable |
| ChromaDB | CONFIGURED | Compose and client source exist; runtime unavailable |
| RAG | IMPLEMENTED / CONFIGURED | Permission filter in source; retrieval runtime unavailable |
| Ollama | CONFIGURED | Compose/native instructions exist; runtime unavailable |
| Frontend integration | IMPLEMENTED | Build passes; backend/browser runtime unavailable |
| Docker | CONFIGURED | Compose/Dockerfiles reviewed; Docker daemon unavailable |

## Gate Result

The verification gate is **NOT PASSED**.

Reason: the execution environment does not provide Maven, Docker, Python, databases, HTTP runtime, or STOMP client execution. No runtime result has been fabricated.

P2 work remains intentionally blocked.

## Verification Update — 2026-09-25

The preceding sections record an earlier tool-environment run and are retained as historical evidence. Docker, HTTP access, browser access, and Maven through Docker were available for this update; the old blanket environment limitation and final matrix do not describe this later run.

### Implemented during this update

- Backend ML readiness now allows 10 seconds for the upstream dependency check. The ML service's real `DEGRADED` response is no longer misreported as `UNAVAILABLE` when optional ChromaDB and Ollama checks each time out.
- Employee role changes now require `ROLE_ADMIN` at the service mutation boundary. Managers may still update employee profile fields.
- Forced-password-change state is derived from the current database user details. HTTP permits only `/api/auth/me`, `/api/auth/logout`, and `/api/auth/change-password` until the password changes; STOMP CONNECT is rejected during that state.
- Password encoder injection was moved from an unannotated controller argument into `AuthService`. The old argument was treated as a model attribute by Spring MVC and caused a real HTTP 500.
- Audit detail links now use the ledger `eventId`, not MongoDB's document `id`, in the audit explorer, dashboard, and employee audit history.
- Added service/filter tests for manager role-change authorization and forced-password-change HTTP/STOMP behavior.

### Evidence collected

| Check | Result | Evidence |
| --- | --- | --- |
| Backend container | HEALTHY | Docker health check passed after rebuild/restart. |
| MySQL and MongoDB | RUNTIME-VERIFIED / READY | `/api/health/readiness` successfully opened a MySQL connection and pinged MongoDB. |
| ML readiness propagation | RUNTIME-VERIFIED / DEGRADED | Backend returned Python ML `DEGRADED` and ChromaDB/Ollama `UNAVAILABLE`; the ML readiness HTTP endpoint returned 200. |
| Unauthenticated employee list | RUNTIME-VERIFIED | Real request returned HTTP 401. |
| Login and current user | RUNTIME-VERIFIED | Bootstrap admin login and `/api/auth/me` succeeded. Credentials and tokens were not recorded. |
| Forced password change | RUNTIME-VERIFIED | API-created employee logged in with `mustChangePassword=true`; own employee GET returned 403; password change returned 200; `/auth/me` then returned false and employee GET returned 200 using the same token. |
| Temporary credential audit secrecy | RUNTIME-VERIFIED | Created event was fetched by its event ID; serialized event did not contain the generated temporary password. Chain verification returned `VERIFIED`. |
| Audit detail identifier | RUNTIME-VERIFIED | For a real persisted event, lookup by Mongo `id` returned 400 and lookup by ledger `eventId` returned 200. |
| Backend automated tests | PASS | 9 tests, 0 failures, 0 errors: audit ledger (3), forced password (3), JWT (1), employee service (2). Executed with Java 17/Maven in Docker. |
| Frontend production build | PASS | `docker compose build frontend` completed. |
| Frontend browser smoke | PARTIAL | Frontend served at `http://localhost:5173`; login route loaded after the corrected bundle with no page exception. Authenticated browser workflow was not run. |

The real verification employee created through the API remains in the local database because no employee-delete API exists. It is an explicitly created manual test record, not automatic seed data. No passwords or JWTs were written to this report.

### Not verified or remaining

- ChromaDB and Ollama are not running in the active stack; indexing, authorized retrieval, generation, and Ollama inference were not exercised. Their observed readiness is `UNAVAILABLE`.
- Isolation Forest and SHAP endpoint execution, ML pytest, complete employee CRUD/lifecycle, systematic role matrix/403 coverage, MongoDB controlled tampering against the live ledger, and audit pagination completeness were not rerun in this update.
- STOMP browser/client connection and message round-trip were not tested. Readiness currently reports STOMP configured, not a verified client session.
- Approval, remediation, rollback, notifications, lineage, forecasting, and several other frontend wrapper routes remain source-unverified or lack backend controllers; they are not complete acceptance claims.
- Authenticated browser login and employee workflow were not exercised. The browser check only established that the current frontend login route renders without a page error.

The overall acceptance gate remains **NOT PASSED**. The checks above are limited runtime evidence and do not establish that the full project is complete.

### Commands used

```powershell
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
docker compose ps
Invoke-RestMethod http://localhost:8080/api/health/readiness | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8000/health/readiness | ConvertTo-Json -Depth 8
```