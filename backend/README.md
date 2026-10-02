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

Ollama runs natively on the Windows host by default. Pull the configured model with `ollama pull llama3.2:3b`, keep `ollama serve` running, and leave `OLLAMA_URL` at its default `http://host.docker.internal:11434` (set it in `.env` only to override). The ML service container resolves `host.docker.internal` on Docker Desktop automatically and on Linux through the configured `host-gateway` mapping. Do not expose Ollama publicly.

## Docker Ollama (optional)

For hosts with enough spare RAM, the Compose `ollama` service is profile-gated under `ollama`. Set `OLLAMA_URL=http://ollama:11434` in `.env`, start it with `docker compose --profile ai --profile ollama up -d ollama`, then pull a CPU-compatible model with `docker compose exec ollama ollama pull llama3.2:3b`. Model availability is part of ML readiness; a running container alone is not treated as READY.