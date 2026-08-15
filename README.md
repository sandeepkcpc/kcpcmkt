# KCPC Marketing Content Production Lifecycle MVP

Development Baseline **R3.5** implementation. Stack: Java 21, Spring Boot 3.3, Spring Security
(JWT-in-cookie + server-side token registry), Hibernate/JPA, PostgreSQL 16+, Spring MVC + JSP,
REST `/api/v1`, modular monolith, Nginx + Docker for deployment.

The 12 governing specification documents live in `Required_Documents/` (read-only). Governance,
traceability, technical decisions and known gaps are tracked in `docs/`:

- `docs/IMPLEMENTATION_STATUS.md` — phase-by-phase status and known gaps
- `docs/IMPLEMENTATION_TRACEABILITY.md` — governed-ID → implementation mapping
- `docs/IMPLEMENTATION_DECISIONS.md` — ordinary engineering choices and their rationale
- `docs/IMPLEMENTATION_DISCREPANCIES.md` — specification ambiguities encountered (currently empty)

## Prerequisites

- Java 21+ (JDK 26 also works; the build targets release 21)
- Maven 3.9+
- PostgreSQL 16+

## Local development

```bash
# One-time: create the dev/test databases and an app role (adjust as needed for your machine)
createuser -s kcpc_app
createdb -O kcpc_app kcpc_dev
createdb -O kcpc_app kcpc_test
psql -d kcpc_dev -c "ALTER USER kcpc_app WITH PASSWORD 'kcpc_app_dev_pw';"

# Run against kcpc_dev (also seeds demo users - see db/migration-demo/, dev-profile only)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on `http://localhost:8080`. Flyway applies all migrations automatically on
startup. A bootstrap CEO account is always seeded (core migration, every profile):

- **Email:** `ceo@kcpcbandhani.local`
- **Password:** `ChangeMe123!` (rotate immediately in any non-dev environment)

Under the `dev` profile only, additional demo accounts are seeded (all password `Demo@123`):
Marketing Manager, 2× Camera Person, 2× Video Editor, Publisher, Marketing Coordinator (holds a
delegated Idea Review permission grant, for exercising the self-review-conflict guard), HR
Manager. See `src/main/resources/db/migration-demo/V6__demo_users.sql`.

## Tests

```bash
mvn test
```

Runs against `kcpc_test` (the `test` Spring profile, defined in `application.yml`) - a real
PostgreSQL database, not an in-memory substitute, so the actual governed schema/constraints are
exercised. Includes `GoldenEndToEndFlowTest`, which drives the entire lifecycle (Idea →
Completed) over real HTTP exactly as build-prompt §40 specifies.

## API documentation

Swagger UI: `http://localhost:8080/api/v1/docs` (generated, non-authoritative - the governed
contract is `Required_Documents/API_Specification.md`).

## Docker / deployment

```bash
cp .env.example .env   # create this file - see docker-compose.yml for the required variables
docker compose up --build
```

This builds the app, starts PostgreSQL, and fronts everything with Nginx on port 80. Required
`.env` variables: `MIGRATOR_DB_PASSWORD` (schema-owning `kcpc_migrator` role, runs Flyway only),
`APP_DB_PASSWORD` (restricted runtime `kcpc_app` role - see DB-001 in
`docs/IMPLEMENTATION_DECISIONS.md`), `APP_SECURITY_JWT_SECRET` (≥64 bytes / 128 hex chars for
HS512 - generate with `openssl rand -hex 64`).

**Note:** the Docker/Nginx configuration was written against the governed stack (SAD §deployment,
ADR-010) but has not been build-verified in this development environment (no Docker daemon was
available here) - verify `docker compose up --build` succeeds before relying on it.

## Architecture

Spring MVC (JSP) controllers and REST controllers call the same application/service layer -
business logic lives in `*/service/`, never in a JSP and never duplicated between the two
controller families. See `docs/IMPLEMENTATION_TRACEABILITY.md` for which governed requirement
each package/class implements.
