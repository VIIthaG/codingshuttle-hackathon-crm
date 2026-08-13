# FlowCRM — Railway Deployment Guide

This document describes how to deploy the **existing** Spring Boot backend to Railway with four services. It does **not** deploy the app for you and does not invent Railway-generated hostnames or passwords.

Related local setup: root [README.md](../README.md).

---

## Target topology (four Railway services)

| Railway service | Role |
|-----------------|------|
| **FlowCRM backend** | Spring Boot API (Dockerfile in `backend/`) |
| **PostgreSQL** | Durable source of truth + Flyway migrations on startup |
| **Redis** | Dashboard cache, login rate limiting, outbox distributed lock |
| **RabbitMQ** | Reminder exchange/queues (main, retry, DLQ) |

Do **not** expose PostgreSQL, Redis, or RabbitMQ credentials in any frontend code. Only the backend service should hold those environment variables.

---

## Backend: GitHub + Dockerfile

### Root directory / Dockerfile path

The monorepo keeps the Spring Boot app under `backend/`.

For the **backend** Railway service:

1. Connect the GitHub repository.
2. Set **Root Directory** to: `backend`
3. Use Dockerfile builder.
4. Dockerfile path (relative to that root): `Dockerfile`

Build context is therefore `backend/` (see `backend/.dockerignore`).

The Maven Wrapper script (`mvnw`) must use Unix (`LF`) line endings for Linux image builds. The repo includes `.gitattributes` for `backend/mvnw`, and the Dockerfile also strips any residual `\r` before running the wrapper.

Optional `backend/railway.toml` already sets:

- `builder = "DOCKERFILE"`
- `dockerfilePath = "Dockerfile"`
- `healthcheckPath = "/api/v1/health"`

### Image design (summary)

- **Build stage:** Eclipse Temurin **21 JDK**, Maven Wrapper (`./mvnw`), packages `flowcrm-backend.jar`
- **Runtime stage:** Eclipse Temurin **21 JRE**, non-root user `flowcrm`, runs `java -jar application.jar`
- No Maven in the runtime image
- No secrets baked into the image

---

## PORT handling

Railway injects `PORT`.

FlowCRM maps:

```text
server.port = ${PORT:${SERVER_PORT:8080}}
```

| Environment | Behavior |
|-------------|----------|
| Railway | Uses platform `PORT` |
| Local without `PORT` | Uses `SERVER_PORT` if set, else **8080** |

Do not hard-code Railway hostnames in config files.

---

## Required backend environment variables

Set these on the **backend** Railway service. Prefer Railway’s “reference variable” / service linking UI so Postgres/Redis/RabbitMQ values come from those services—do not paste fake sample hosts into git.

### Required for a working hosted API

| Variable | Purpose | Notes |
|----------|---------|--------|
| `DB_URL` | JDBC URL | Must be `jdbc:postgresql://…` (not `postgres://`). Build from the Postgres service host/port/db. |
| `DB_USERNAME` | DB user | Reference Postgres service credentials |
| `DB_PASSWORD` | DB password | Reference Postgres service credentials |
| `JWT_SECRET` | HMAC signing key | **Generate manually** as a long random secret. Do **not** use the local-dev default. |
| `REDIS_HOST` | Redis hostname | Reference Redis service |
| `REDIS_PORT` | Redis port | Reference Redis service (often `6379`) |
| `RABBITMQ_HOST` | RabbitMQ hostname | Reference RabbitMQ / AMQP service |
| `RABBITMQ_PORT` | AMQP port | Usually `5672` unless the provider documents otherwise |
| `RABBITMQ_USER` | AMQP username | Reference broker credentials |
| `RABBITMQ_PASSWORD` | AMQP password | Reference broker credentials |

### Optional / hosted-provider specifics

| Variable | Default | When to set |
|----------|---------|-------------|
| `PORT` | injected by Railway | Do not set manually on Railway |
| `SERVER_PORT` | `8080` | Local override only |
| `REDIS_PASSWORD` | empty | Set if Redis requires auth |
| `REDIS_SSL` | `false` | Set `true` if the provider requires TLS (`rediss`) |
| `RABBITMQ_VHOST` | `/` | Set if the broker uses a non-default vhost |
| `RABBITMQ_SSL` | `false` | Set `true` if the provider requires AMQPS/TLS |
| `JWT_EXPIRATION_MS` | `86400000` | Optional |
| `CACHE_TYPE` | `redis` | Keep `redis` in production |
| `MESSAGING_ENABLED` | `true` | Keep `true` with RabbitMQ |
| `OUTBOX_PUBLISHER_ENABLED` | `true` | Keep `true` with RabbitMQ |
| `OUTBOX_LOCK_ENABLED` | `true` | Keep `true` with Redis |
| `LOGIN_RATE_LIMIT_ENABLED` | `true` | Recommended in production |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | **Required for a separate SPA origin.** Comma-separated exact origins, e.g. `https://flowcrm.up.railway.app`. Do **not** use `*`. |

Other `app.*` knobs (`DASHBOARD_CACHE_TTL_SECONDS`, rate-limit window, reminder attempts, etc.) may stay at defaults unless you intentionally tune them.

### JWT secret

- Generate a strong random value (for example 32+ random bytes, base64/hex).
- Store it only in Railway secrets / env vars.
- Never commit it to git, Dockerfile, Compose, or README.
- The value in `application.yml` (`LocalDevOnlySecretKeyThatIsAtLeast32BytesLong!!`) is a **local-development fallback only**.

---

## PostgreSQL

- Hibernate remains `ddl-auto: validate`.
- Flyway remains enabled and runs **automatically on backend startup** (same as local).
- Do not run separate one-off migration jobs unless you have an operational reason.
- Do not edit Flyway scripts `V1`–`V8` after they have been applied.

Wire `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` from the Railway Postgres service. Convert any `postgres://` / `postgresql://` connection string into JDBC form:

```text
jdbc:postgresql://<host>:<port>/<database>
```

(Exact host/port/db names come from Railway—do not invent them here.)

---

## Redis

Preserve existing semantics:

- dashboard cache (`dashboard-summary`)
- login rate limiting
- outbox publisher distributed lock

Configure with `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` / `REDIS_SSL` when required by the provider.

---

## RabbitMQ

Preserve exact topology names from the codebase:

| Kind | Name |
|------|------|
| Exchange | `flowcrm.reminders.exchange` |
| Main queue | `flowcrm.reminders.queue` |
| Retry queue | `flowcrm.reminders.retry.queue` |
| DLQ | `flowcrm.reminders.dlq` |
| Routing keys | `reminder.scheduled`, `reminder.retry`, `reminder.dlq` |

The app declares topology on startup when messaging is enabled. Retry/DLQ behavior is unchanged.

---

## Healthcheck

Use:

```text
GET /api/v1/health
```

This endpoint is public (no JWT). `railway.toml` points the deploy healthcheck at this path.

---

## Verification checklist (after deploy)

Replace `<BACKEND_PUBLIC_URL>` with the Railway-provided HTTPS URL for the backend service (do not invent one).

1. **Health**
   - `GET <BACKEND_PUBLIC_URL>/api/v1/health`
   - Expect JSON with `"status":"UP"`
2. **Swagger UI**
   - Open `<BACKEND_PUBLIC_URL>/swagger-ui.html`
3. **OpenAPI JSON**
   - `GET <BACKEND_PUBLIC_URL>/v3/api-docs`
4. **Register / login**
   - Use Swagger Authentication endpoints; copy JWT; click **Authorize**
5. **Dashboard**
   - Call `GET /api/v1/dashboard/summary` with Bearer token

If health fails: check Postgres connectivity, Flyway logs, Redis/RabbitMQ reachability, and that `PORT` is not overridden incorrectly.

---

## Local development compatibility

Local Docker Compose (`docker-compose.yml` at repo root) is unchanged:

- Postgres on host port `5433`
- Redis `6379`
- RabbitMQ `5672` / management `15672`

Local defaults in `application.yml` continue to work without Railway variables. Existing:

```powershell
docker compose up -d
cd backend
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

---

## What this phase does not do

- Does not deploy to Railway for you
- Does not add a frontend
- Does not change CRM business logic, Flyway V1–V8, or reminder semantics
- Does not embed production secrets in the repository
