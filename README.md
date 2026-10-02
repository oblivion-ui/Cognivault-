# CogniVault

CogniVault is a local enterprise identity and audit application. The current stack is a React/Vite frontend, Java 17/Spring Boot API, MySQL for identity records, MongoDB for the audit ledger, and a FastAPI service for Isolation Forest and SHAP. ChromaDB is an optional local AI-profile service; Ollama runs natively on the host by default (an optional containerized Ollama is also available).

## Run Locally

Prerequisites: Docker Desktop with Compose, at least 8 GB RAM recommended, and free host ports `5173`, `8080`, `8000`, `27017`, and `3307`.

In PowerShell, copy `.env.example` to `.env` and replace the sample database passwords, JWT secret, and bootstrap password with local values. Keep `.env` private. The bootstrap administrator is created only when `COGNIVAULT_BOOTSTRAP_ADMIN_ENABLED=true`.

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up --build -d
docker compose ps
```

Open the frontend at `http://localhost:5173`. The API is at `http://localhost:8080/api`; ML endpoints are at `http://localhost:8000`. The frontend is built with mock mode disabled in Compose. To stop containers without deleting persisted database volumes:

```powershell
docker compose down
```

Do not use `docker compose down -v` unless intentionally deleting local MySQL, MongoDB, and ChromaDB data.

## Services and Readiness

Compose service-to-service URLs use Docker DNS names: `mysql`, `mongodb`, `ml-service`, `chroma`, and `ollama`. MySQL is published on host port `3307`; the other configured host ports are in `docker-compose.yml`.

Check backend dependency readiness and ML dependency readiness:

```powershell
Invoke-RestMethod http://localhost:8080/api/health/readiness | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8000/health/readiness | ConvertTo-Json -Depth 8
```

Readiness values include `READY`, `STARTING`, `DEGRADED`, `NOT_CONFIGURED`, `UNAVAILABLE`, and `ERROR`. ChromaDB is under the `ai` Compose profile; without that profile, ML readiness is expected to be degraded and retrieval/generation must report unavailable rather than fabricate a result.

To start the optional ChromaDB dependency:

```powershell
docker compose --profile ai up --build -d
```

### Ollama (local RAG generation)

Ollama runs **natively on the host** by default, outside Docker, so model loading does not compete with the containers for the limited Docker Desktop memory budget (a `llama3.2:3b` load needs roughly 2–3 GiB on its own). The ML service container reaches a native Ollama through `host.docker.internal`, which works on Docker Desktop out of the box and on Linux hosts via the `host-gateway` mapping already configured in `docker-compose.yml`.

On the host (Windows PowerShell example):

```powershell
ollama pull llama3.2:3b
ollama serve   # usually already running as a service
```

No `.env` change is required for this mode; `OLLAMA_URL` defaults to `http://host.docker.internal:11434`.

Optional full-Docker Ollama (only for hosts with enough spare RAM): set `OLLAMA_URL=http://ollama:11434` in `.env`, start the profile, and pull the model into the container:

```powershell
docker compose --profile ai --profile ollama up -d
docker compose exec ollama ollama pull llama3.2:3b
```

The configured Ollama model is `llama3.2:3b` (`OLLAMA_MODEL` in `.env`). Model availability is part of ML readiness; a running server without the configured model reports `NOT_CONFIGURED`, and RAG then returns its degraded response with retrieved evidence but no generated answer. The per-request generation timeout is `OLLAMA_TIMEOUT_SECONDS` (default 120) and must stay below the backend's 240-second RAG budget.

## Data and API

Flyway owns relational schema migrations. MySQL stores users and employee records; MongoDB stores append-only audit events and the SHA-256 hash chain. Business records are not automatically seeded. Optional bootstrap authentication is environment-configured.

Implemented API areas include login/current user/password change, employee create/read/list/update/lifecycle/credential reset, audit list/detail/employee history, integrity verification, dashboard summary, and readiness. Authentication uses bearer JWTs. Forced-password-change accounts can access only current-user/logout/password-change routes over HTTP and cannot establish STOMP sessions until their password is changed. See [frontend-backend-contract.md](docs/frontend-backend-contract.md) for the wider frontend/backend contract inventory; entries marked unverified or unavailable are not implemented claims.

## Tests and Verification

Backend tests (Java 17/Maven):

```powershell
Set-Location backend
mvn test
```

ML tests (Python 3.10/3.11):

```powershell
Set-Location ml-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
pytest
```

Frontend production build:

```powershell
npm ci
npm run build
```

Current runtime evidence and remaining gaps are recorded in [runtime-verification-report.md](docs/runtime-verification-report.md). Core container health, selected authentication/password-change flows, readiness reporting, audit lookup, and builds/tests have runtime evidence; this does not constitute complete end-to-end acceptance. STOMP messaging, authenticated browser workflows, Chroma indexing/RAG authorization, Ollama inference, and the planned approvals/remediation/rollback/lineage/forecasting modules remain unverified or unimplemented.