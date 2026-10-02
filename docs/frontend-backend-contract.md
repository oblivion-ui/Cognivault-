# CogniVault Frontend/Backend Contract Verification

## Audit Scope

The initial audit found only the React/Vite frontend. This revision adds and inspects the new Spring Boot P0 implementation, Docker Compose configuration, and Python ML/RAG service files. The source contracts below are now implemented in this workspace, but runtime compatibility is still unverified because Maven, Docker, MySQL, MongoDB, and Python service execution were not available through the current tool environment.

Source-level endpoint names and response shapes below are now verified against the newly added backend implementation. They are not marked runtime verified until the services are started and exercised.

## Architecture Evidence

| Area | Evidence | Status |
| --- | --- | --- |
| React | `react` 19.x and React components in `src/` | VERIFIED |
| Vite | `vite` and `vite.config.ts` | VERIFIED |
| TypeScript | `typescript`, `tsconfig.json`, typed domain models | VERIFIED |
| React Router DOM | Router used in `src/main.tsx` and `src/App.tsx` | VERIFIED |
| STOMP | `@stomp/stompjs` in `src/websocket/` | VERIFIED |
| Spring Boot backend | `backend/pom.xml`, Java source | IMPLEMENTED / UNVERIFIED |
| Spring Security/JWT/BCrypt | Security config, JWT service, password encoder | IMPLEMENTED / UNVERIFIED |
| MySQL/MongoDB | `docker-compose.yml`, Flyway, Mongo repositories | CONFIGURED / UNVERIFIED |
| FastAPI/Isolation Forest/SHAP | `ml-service/app/main.py` | IMPLEMENTED / UNVERIFIED |
| ChromaDB/embeddings/Ollama | ML service and Compose profile | CONFIGURED / UNVERIFIED |
| Docker/Docker Compose | Root Compose, backend/ML/frontend Dockerfiles | CONFIGURED / UNVERIFIED |

## Contract Matrix

| Feature | Frontend API | Actual Backend Endpoint | Method | Request | Response | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Login | `authApi.login` | `/api/auth/login` | `POST` | `{ username, password }` | `{ accessToken, refreshToken, expiresIn, user }` | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Current user | `authApi.me` | `/api/auth/me` | `GET` | None | User response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Logout | `authApi.logout` | `/api/auth/logout` | `POST` | None | 204 | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Force password change | `authApi.changePassword` | `/api/auth/change-password` | `POST` | `{ currentPassword, newPassword }` | User response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Employee list | `employeeApi.list` | `/api/employees` | `GET` | Spring pageable query | Spring Page JSON | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Employee detail | `employeeApi.get` | `/api/employees/{id}` | `GET` | UUID path identifier | Employee response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Employee creation | `employeeApi.create` | `/api/employees` | `POST` | Personal, employment, account, credential payload | Employee plus optional temporary credential | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Employee update | `employeeApi.update` | `/api/employees/{id}` | `PUT` | Update employee DTO | Employee response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Account transition | `employeeApi.transition` | `/api/employees/{id}/account-actions` | `POST` | `{ action, reason }` | Employee response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Password reset | `employeeApi.resetPassword` | `/api/employees/{id}/credentials/reset` | `POST` | `{ generateTemporary, forceChange }` | Optional temporary credential | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Employee audit history | `employeeApi.auditHistory` | `/api/employees/{id}/audit-events` | `GET` | UUID path identifier | Page response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Audit explorer | `auditApi.list` | `/api/audit-events` | `GET` | No filters yet; page-shaped response | Page response | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Audit detail | `auditApi.get` | `/api/audit-events/{eventId}` | `GET` | Event ID path identifier | Audit response with snapshots/diffs | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Audit export | `auditApi.export` | Not available for inspection | `POST` assumed | Filter object | `{ downloadUrl }` assumed | UNVERIFIED |
| Dashboard | `dashboardApi.summary` | Not available for inspection | `GET` assumed | None | `DashboardSummary` | UNVERIFIED |
| Anomalies | `anomalyApi.list` | Not available for inspection | `GET` assumed | Filters and pagination | `PageResponse<Anomaly>` | UNVERIFIED |
| Anomaly summary | `anomalyApi.summary` | Not available for inspection | `GET` assumed | None | Generic summary object | UNVERIFIED |
| Integrity result | `integrityApi.latest` | `/api/integrity/latest` | `GET` | None | Integrity result | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Integrity verification | `integrityApi.verify` | `/api/integrity/verifications` | `POST` | None | Integrity result | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Investigator | `investigatorApi.investigate` | Not available for inspection | `POST` assumed | `{ query }` | `Investigation` with answer/evidence/limitations | UNVERIFIED |
| Investigation history | `investigatorApi.history` | Not available for inspection | `GET` assumed | None | Investigation array | UNVERIFIED |
| Approval list | `approvalApi.list` | Not available for inspection | `GET` assumed | Filters and pagination | `PageResponse<Approval>` | UNVERIFIED |
| Approval detail | `approvalApi.get` | Not available for inspection | `GET` assumed | Approval path identifier | `Approval` | UNVERIFIED |
| Approval decision | `approvalApi.decide` | Not available for inspection | `POST` assumed | `{ decision, comment }` | `Approval` | UNVERIFIED |
| Reports | `reportApi.list` | Not available for inspection | `GET` assumed | None | Report array | UNVERIFIED |
| Report generation | `reportApi.generate` | Not available for inspection | `POST` assumed | Report request object | `Report` | UNVERIFIED |
| Service health | `healthApi.get` | Not available for inspection | `GET` assumed | None | Service health array | UNVERIFIED |
| Notifications | `notificationApi.list` | Not available for inspection | `GET` assumed | None | `PageResponse<Notification>` | UNVERIFIED |
| Mark notification read | `notificationApi.markRead` | Not available for inspection | `PATCH` assumed | `{ read: true }` | Notification | UNVERIFIED |
| Mark all notifications read | `notificationApi.markAllRead` | Not available for inspection | `POST` assumed | None | Empty response | UNVERIFIED |
| Lineage | `lineageApi.get` | Not available for inspection | `GET` assumed | `entityType`, `entityId` query | Nodes and relationships | UNVERIFIED |
| Predictions | `predictiveApi.get` | Not available for inspection | `GET` assumed | Metric query | Prediction array | UNVERIFIED |
| Lifecycle | `lifecycleApi.list` | Not available for inspection | `GET` assumed | None | `PageResponse<LifecycleRecord>` | UNVERIFIED |
| Remediation | `remediationApi.create` | Not available for inspection | `POST` assumed | Generic remediation payload | Result with status/audit reference | UNVERIFIED |
| Rollback | `remediationApi.rollback` | Not available for inspection | `POST` assumed | Generic rollback payload | Result with status/audit reference | UNVERIFIED |
| User administration | `userApi.list` | Not available for inspection | `GET` assumed | None | `PageResponse<User>` | UNVERIFIED |
| Profile | `userApi.profile` | Not available for inspection | `GET` assumed | None | `User` | UNVERIFIED |
| Profile update | `userApi.updateProfile` | Not available for inspection | `PATCH` assumed | Partial User | `User` | UNVERIFIED |
| STOMP connection | `stompClient` | `/ws` with `/app`, `/topic`, `/queue`, and `/user` broker prefixes | WebSocket assumed | JWT `Authorization` native header | STOMP connection and ping/pong mapping | SOURCE VERIFIED / RUNTIME UNVERIFIED |

## ML/RAG Service Source Contract

| Feature | Endpoint | Request | Response | Status |
|---|---|---|---|---|
| ML readiness | `GET /health/readiness` | None | Overall status plus ChromaDB/Ollama dependency statuses | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Isolation Forest score | `POST /v1/anomaly/score` | Baseline feature vectors, instance, contamination | Real model score, flag, model name, feature names | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| SHAP explanation | `POST /v1/anomaly/explain` | Same real baseline/instance input | SHAP TreeExplainer contributions or HTTP 503 | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| ChromaDB indexing | `POST /v1/rag/index` | Documents and metadata | Indexed count or HTTP 503 | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Permission-aware RAG | `POST /v1/rag/query` | Query plus required allowed document IDs | Retrieved evidence, actual Ollama answer when ready, or degraded response | SOURCE VERIFIED / RUNTIME UNVERIFIED |

## Option B Credential Verification

The frontend sends `credential.method = "GENERATE"` when the administrator chooses the temporary-password option. The new backend implements secure generation, BCrypt hashing, persistence, optional first-login enforcement, and one-time response behavior. Runtime behavior still requires Docker/Maven verification.

| Requirement | Frontend behavior | Backend evidence | Status |
| --- | --- | --- | --- |
| Backend generates credential | Requests `GENERATE`; backend uses `SecureRandom` | `EmployeeService.generatePassword()` | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| BCrypt hashing | Frontend does not generate or hash production credentials | `BCryptPasswordEncoder` in backend | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Plaintext is not persisted | Temporary value is held only in React state after response | Backend stores only encoded value; audit sanitizer removes password-like keys | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Force first-login change | Sends `forceChange`; JWT/user response exposes `mustChangePassword` | `UserAccount.mustChangePassword` and change-password service | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| One-time password response | Displays `temporaryPassword` only when returned | `CreateEmployeeResponse` and `CredentialResetResponse` | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Credential reset | Frontend abstraction matches new controller path | `POST /api/employees/{id}/credentials/reset` | SOURCE VERIFIED / RUNTIME UNVERIFIED |
| Credential audit reference | Frontend displays only if returned | Current backend returns `auditEventId: null`; ledger event exists but response linkage is not yet wired | MISMATCH / LIMITED |

## Readiness and Fallback Assessment

The frontend does not silently fall back to mock data after a real API failure. Mock data is selected only for an explicit demo session created when `VITE_ENABLE_MOCK=true`. Real API failures surface through page error states.

Current frontend readiness is therefore:

- Core API connectivity: IMPLEMENTED / UNVERIFIED
- Backend service readiness endpoint: IMPLEMENTED / UNVERIFIED
- AI/ML readiness semantics: IMPLEMENTED / UNVERIFIED
- STOMP readiness: IMPLEMENTED / UNVERIFIED
- Real backend runtime: UNVERIFIED
- Docker runtime: MISSING in this workspace

## Evidence Required To Upgrade Status

The following P2 source contracts are not implemented yet and remain intentionally unavailable rather than fabricated:

- approval queue and decisions;
- report generation/download;
- notifications persistence and read mutations;
- remediation and rollback execution;
- lineage and lifecycle APIs;
- predictive model proxy;
- backend publication of real-time audit/approval/anomaly notifications.

To continue contract verification, provide or implement the following:

1. Spring controller mappings and DTO classes.
2. Spring Security configuration and JWT claims.
3. Employee and credential service implementations.
4. Audit, snapshot, hash-chain, anomaly, approval, report, health, and notification controllers.
5. WebSocket/STOMP endpoint, broker, destination, and authentication configuration.
6. FastAPI routes and ML response models.
7. Docker Compose, health checks, and environment variable contracts.

Until that evidence is available, frontend endpoint names and DTOs must remain UNVERIFIED rather than being presented as working backend integration.