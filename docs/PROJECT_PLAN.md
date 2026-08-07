# FlowCRM — Project Plan

**Product:** Mini CRM (Zoho-lite) for a coding hackathon  
**Repo / monorepo root:** `flowcrm/` (this repository)  
**Primary focus:** Lead pipeline, stage workflows, automated follow-up reminders  
**Status:** Backend core in progress — production patterns and reminders not yet implemented

---

## 1. Product goals

Build a production-minded Mini CRM where sales teams can:

- Register / log in with JWT (`ADMIN`, `SALES_REP`)
- Manage leads with role-aware access
- Move leads through a validated status pipeline
- Create follow-up tasks/reminders that are processed asynchronously
- View pipeline/dashboard aggregates (cached where useful)

**Priority:** Backend quality and production-readiness. Frontend is optional but desirable after the backend is complete.

---

## 2. Hackathon judging requirements

### 2.1 Hard requirements / criteria

| Requirement | Expectation |
|-------------|-------------|
| Core backend | Must remain **Spring Boot** |
| Idempotency | Apply where appropriate |
| Transactional outbox | Apply where appropriate |
| Async reliability | Retry + **dead-letter** handling for async processing |
| Caching | Use where it provides meaningful benefit |
| Rate limiting | Apply where appropriate |
| Distributed locking | Demonstrate where appropriate |
| Primary database | **PostgreSQL** |
| External services | Queues / caches / other services are allowed |
| Quality bar | Backend quality and production-readiness are the priority |
| Frontend | Optional but desirable after backend is complete |

### 2.2 Required deliverables

1. Working project  
2. Deployment if possible  
3. Detailed README (architecture, decisions, setup)  
4. Design documentation  
5. Architecture diagram  
6. Swagger / OpenAPI UI  
7. 2–5 minute demo video  

---

## 3. Current completed functionality

| Area | Status |
|------|--------|
| Spring Boot + PostgreSQL + Docker Compose | Done |
| Flyway migrations (baseline, users, leads) | Done |
| JWT authentication (register / login / me) | Done |
| Roles `ADMIN` and `SALES_REP` | Done |
| Lead CRUD + pagination + status filter | Done |
| Role-aware lead access (ADMIN all / SALES_REP assigned) | Done |
| Lead pipeline status workflow (`PATCH /leads/{id}/status`) | Done |
| Validated status transitions | Done |
| Automated / integration tests (auth + leads + pipeline) | Done |
| Health endpoint | Done |

**Implemented lead model (as built):**  
`id`, `fullName`, `email`, `phone`, `company`, `source`, `status`, `assignedTo`, `createdAt`, `updatedAt`  

**LeadStatus pipeline:**  
`NEW → CONTACTED → QUALIFIED → CONVERTED`, with `LOST` allowed from active stages; `LOST` / `CONVERTED` terminal.

---

## 4. Remaining implementation phases

### Phase A — Follow-up tasks & reminders (domain)
- Task / reminder entity linked to leads (and optionally assignee)
- CRUD APIs with validation and ownership rules
- Due-at scheduling fields used by async reminder processing
- Idempotent create for selected POSTs (`Idempotency-Key`)

### Phase B — Transactional outbox + RabbitMQ
- `outbox_events` table (Flyway)
- Write business/reminder events to outbox in the **same DB transaction** as business changes
- Outbox publisher → RabbitMQ
- Reminder / event consumers
- Retry strategy + **DLQ** for failed reminder processing
- Consumers must be safe under duplicate delivery

### Phase C — Redis: cache, rate limit, distributed lock
- Cache dashboard / pipeline aggregates in Redis
- Redis-backed rate limiting for sensitive/high-volume APIs (e.g. login)
- Redis distributed lock around scheduled outbox / reminder dispatch so multiple app instances do not process the same work concurrently

### Phase D — API docs & observability polish
- springdoc-openapi / Swagger UI with JWT bearer auth
- Harden error responses and operational logging as needed

### Phase E — Docs, deploy, demo
- Root README (architecture, decisions, env vars, runbook)
- Design doc + architecture diagram in `docs/`
- Docker Compose stack: Postgres + RabbitMQ + Redis (+ app if feasible)
- Deployment notes / attempt
- 2–5 minute demo video script + recording checklist

### Phase F — Optional frontend (after backend complete)
- React + TypeScript + Vite scaffold
- Auth, lead list/detail, pipeline view, tasks/reminders UI

---

## 5. Planned production architecture

```text
                    ┌──────────────────────┐
                    │  Clients / Swagger   │
                    │  (optional React UI) │
                    └──────────┬───────────┘
                               │ HTTPS + JWT
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     Spring Boot API                          │
│  Controllers → Services → Repositories                       │
│  Idempotency interceptor (selected POSTs)                    │
│  Rate limit filter (login / sensitive routes) ← Redis        │
│  Cache layer (dashboard / pipeline aggregates) ← Redis       │
│  Outbox writer (same TX as business writes) → PostgreSQL     │
│  Scheduler + distributed lock ← Redis                        │
│  Outbox publisher → RabbitMQ                                 │
│  Reminder consumers (+ retry / DLQ) ← RabbitMQ               │
└───────────────┬───────────────────────┬──────────────────────┘
                │                       │
                ▼                       ▼
       ┌────────────────┐      ┌────────────────┐
       │  PostgreSQL    │      │  RabbitMQ      │
       │  users, leads  │      │  reminders     │
       │  tasks         │      │  retry + DLQ   │
       │  outbox_events │      └────────────────┘
       │  idempotency   │
       └────────────────┘
                ▲
                │
       ┌────────────────┐
       │  Redis         │
       │  cache         │
       │  rate limits   │
       │  dist. locks   │
       └────────────────┘
```

**Compose services (planned):** `postgres`, `rabbitmq`, `redis`, and optionally the Spring Boot app container.

---

## 6. Where each required engineering pattern will be demonstrated

| Pattern | Where / how |
|---------|-------------|
| **Idempotency** | Selected POST operations such as **lead creation** and **task/reminder creation**, using `Idempotency-Key` header; store request fingerprint + response for replay-safe retries |
| **Transactional outbox** | Business events and reminder events written to `outbox_events` in PostgreSQL in the **same transaction** as lead/task mutations |
| **Async messaging** | **RabbitMQ** for reminder and domain event processing |
| **Retry / dead-letter** | RabbitMQ retry strategy; failed reminder processing routed to a **DLQ** after exhaustion |
| **Caching** | **Redis** for dashboard / pipeline aggregate data (invalidate or TTL on lead status changes) |
| **Rate limiting** | **Redis-backed** limits for sensitive / high-volume APIs such as **login** |
| **Distributed locking** | **Redis** lock around scheduled reminder / outbox processing so multiple app instances cannot process the same scheduled work concurrently |
| **Swagger / OpenAPI** | **springdoc-openapi** UI with JWT bearer authentication support |

**Design rules for these patterns:**

- Do not add infrastructure only for appearance — only in the locations above.  
- Preserve transactional integrity between business writes and outbox writes.  
- Consumers must tolerate duplicate message delivery.  
- Do not perform asynchronous side effects directly inside HTTP controllers.  

---

## 7. Technology stack

| Layer | Choices |
|--------|---------|
| Backend | Java 21, Spring Boot, Spring Security, JWT, Spring Data JPA, Bean Validation, Flyway, Maven |
| Database | PostgreSQL (primary) |
| Messaging | RabbitMQ (planned) |
| Cache / locks / rate limit | Redis (planned) |
| API docs | springdoc-openapi (planned) |
| Frontend (optional) | React + TypeScript, Vite, Tailwind, React Router, TanStack Query, RHF, Zod |
| Structure | Monorepo: `backend/`, `frontend/` (optional), `docs/` |

---

## 8. Backend style

- **Controller** — HTTP, DTOs, validation only  
- **Service** — business rules, authorization, outbox writes  
- **Repository** — Spring Data JPA  
- **Async workers** — consumers / schedulers outside the HTTP path  
- **Security** — stateless JWT; role-aware service checks  
- **Flyway** — all schema changes as new migrations  

---

## 9. Auth model (implemented)

1. Register / login → JWT access token (no refresh tokens in current scope).  
2. `Authorization: Bearer <token>` on protected APIs.  
3. Roles: `ADMIN`, `SALES_REP`.  

| Action | ADMIN | SALES_REP |
|--------|-------|-----------|
| Manage all users | Planned | ✗ |
| Access all leads | ✓ | Assigned only |
| Change lead status (valid transitions) | ✓ | Assigned only |
| Dashboard aggregates | Global (planned) | Own pipeline (planned) |

---

## 10. Domain model (target)

### 10.1 Implemented now

```text
User 1──* Lead (assignedTo)
```

**LeadStatus:** `NEW`, `CONTACTED`, `QUALIFIED`, `LOST`, `CONVERTED`  
**LeadSource:** `WEB`, `REFERRAL`, `COLD_CALL`, `EVENT`, `OTHER`

### 10.2 Planned next (reminders / reliability)

```text
User 1──* Task/Reminder
Lead 1──* Task/Reminder
OutboxEvent (append-only publish log)
IdempotencyRecord (for selected POSTs)
```

Contacts, separate Deal Kanban entities, notes, and rich activity history remain **optional / later** relative to judging focus (lead pipeline + reminders + production patterns).

---

## 11. REST API (current + planned)

Base URL: `/api/v1`  
Auth: Bearer JWT except register/login/health.

### Implemented

| Method | Path | Notes |
|--------|------|-------|
| GET | `/health` | Public |
| POST | `/auth/register` | Public |
| POST | `/auth/login` | Public (rate limit planned) |
| GET | `/auth/me` | Auth |
| GET/POST | `/leads` | Auth; list supports `status` + pageable; create idempotency planned |
| GET/PUT/DELETE | `/leads/{id}` | Auth + ownership |
| PATCH | `/leads/{id}/status` | Auth + validated transitions |

### Planned

| Method | Path | Notes |
|--------|------|-------|
| CRUD | `/tasks` (or `/reminders`) | Follow-ups; idempotent create |
| GET | `/dashboard/summary` | Cached aggregates |
| GET | `/swagger-ui` / OpenAPI JSON | springdoc |

---

## 12. Monorepo structure (evolving)

```text
flowcrm/
├── README.md                      # required deliverable (detailed)
├── docker-compose.yml             # postgres (+ rabbitmq, redis planned)
├── docs/
│   ├── PROJECT_PLAN.md            # this file
│   ├── DESIGN.md                  # planned design doc
│   └── architecture diagram       # planned
├── backend/                       # Spring Boot (source of truth)
└── frontend/                      # optional after backend complete
```

---

## 13. Cross-cutting concerns

| Concern | Approach |
|---------|----------|
| IDs | UUID |
| Time | UTC `Instant` |
| Validation | Bean Validation on request DTOs |
| Errors | `@RestControllerAdvice` JSON errors |
| Ownership | Service-layer checks; ADMIN bypass where defined |
| Secrets / infra | Environment variables only |
| Schema | New Flyway migrations only (`ddl-auto=validate`) |
| API versioning | `/api/v1` |
| Tests | Integration tests for auth, leads, pipeline; add failure/retry/idempotency tests when those features land |

---

## 14. MVP for judging vs optional

### Must demonstrate for judging

1. Spring Boot core CRM APIs (auth + leads + pipeline) — **done**  
2. Follow-up reminders with async processing  
3. Idempotency, outbox, retry/DLQ, Redis cache, rate limit, distributed lock — in mapped locations  
4. PostgreSQL as system of record  
5. Swagger/OpenAPI UI  
6. README + design docs + architecture diagram  
7. Working run path (Compose); deployment if possible  
8. Demo video (2–5 min)  

### Optional / later

- React frontend  
- Contacts, notes, separate Deal board entities  
- Real email/SMS providers  
- Multi-tenancy, billing  

---

## 15. Success criteria for demo

- `docker compose up` brings up Postgres (+ RabbitMQ/Redis when added); backend starts with env-based config  
- Register/login → create leads → move pipeline stages with validation  
- Create a follow-up reminder → outbox → RabbitMQ → consumer (show retry/DLQ story briefly)  
- Show Swagger UI authenticated call  
- Mention Redis cache, rate limit, and lock in architecture walkthrough  
- Point judges at README + design docs  

---

## 16. Out of scope (explicit)

- Mobile native apps  
- Multi-tenancy / organizations  
- Billing / subscriptions  
- Production email/SMS vendor integration (stub/log sinks are fine for demo)  
- Adding infra patterns that are not wired into the mapped use cases above  

---

## 17. Documentation checkpoint

This plan is the source of truth for remaining work.  
**Do not implement Redis, RabbitMQ, tasks/reminders, Swagger, or new features until the next explicit implementation prompt.**
