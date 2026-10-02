# CogniVault P0 Implementation Plan

## Dependency order

1. Docker Compose with MySQL 8.4 and MongoDB 7.
2. Spring Boot 3.3 / Java 17 service.
3. Flyway-managed MySQL identity schema.
4. MongoDB audit ledger.
5. BCrypt-backed users and environment-driven admin bootstrap.
6. JWT login/current-user/password-change flow.
7. Role-based request authorization.
8. AOP audit interception for mutating identity operations.
9. Canonical SHA-256 hash-chain append and verification.
10. Readiness endpoints for backend, databases, ML dependencies, and STOMP endpoint.
11. Automated hash-chain valid/tamper tests.

## Contract alignment

The frontend API abstractions in `src/api/` were used as the initial contract reference. Endpoint paths and DTOs remain runtime-unverified because the original backend repository was not present. The backend now exposes the P0 paths documented in `docs/frontend-backend-contract.md` so the contract can be exercised locally.

## Security decisions

- JWT secret is required through `COGNIVAULT_JWT_SECRET` and is never committed.
- Bootstrap administrator creation is opt-in through environment variables.
- Bootstrap and generated passwords are BCrypt-hashed before persistence.
- Temporary passwords are returned only in the immediate create/reset response when the request explicitly asks for a generated temporary credential.
- Password-like fields are removed from audit snapshots before MongoDB persistence.
- Frontend demo mode is never used by backend runtime.