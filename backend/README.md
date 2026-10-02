# CogniVault Backend P0

## Local bootstrap

Copy the repository `.env.example` to `.env`, replace every `replace-with-*` value, then run:

```bash
docker compose up --build mysql mongodb backend
```

The development admin is created only when `COGNIVAULT_BOOTSTRAP_ADMIN_ENABLED=true` and all bootstrap variables are provided. The password is read from the environment, BCrypt-hashed by the backend, and never logged or stored in source.

For the full local stack:

```bash
docker compose --profile ai up --build
```

The backend exposes `/api/health/readiness`, `/api/health/services`, and Spring Actuator health. MongoDB stores audit events; MySQL stores users and employees. Flyway applies `src/main/resources/db/migration` on startup.

## Windows/native Ollama

Run Ollama natively on Windows, pull the configured model with `ollama pull %OLLAMA_MODEL%`, and set `OLLAMA_URL=http://host.docker.internal:11434` in `.env` before starting the ML service. Do not expose Ollama publicly.

## Docker Ollama

The Compose `ollama` service is profile-gated under `ai`. Start it with `docker compose --profile ai up -d ollama`, then pull a CPU-compatible model with `docker compose exec ollama ollama pull llama3.2:3b`. Model availability is part of ML readiness; a running container alone is not treated as READY.