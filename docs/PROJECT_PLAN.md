# FlowCRM — Project Plan

**Product:** Mini CRM for a 10-day hackathon  
**Repo / monorepo root:** `flowcrm/` (this repository)  
**Status:** Planning only — no application code until plan approval

---

## 1. Goals

Build a production-style Mini CRM where sales teams can:

- Register / log in with JWT
- Manage leads and contacts with role-based access (`ADMIN`, `SALES_REP`)
- Move deals through a Kanban sales pipeline
- Track tasks, notes, and activity history
- View a simple analytics dashboard

Deliver a polished **MVP** first; treat advanced CRM features as stretch.

---

## 2. Technology Stack

| Layer | Choices |
|--------|---------|
| Backend | Java 21, Spring Boot, Spring Security, JWT, Spring Data JPA, Bean Validation, Flyway, Maven |
| Database | PostgreSQL (via Docker Compose) |
| Frontend | React + TypeScript, Vite, Tailwind CSS, React Router, TanStack Query, React Hook Form, Zod |
| Structure | Monorepo: `backend/`, `frontend/`, `docs/` |

---

## 3. Architecture

### 3.1 High-level

```text
┌─────────────────┐     HTTPS/JSON      ┌──────────────────────────┐
│  React (Vite)   │ ◄─────────────────► │  Spring Boot API         │
│  TanStack Query │   Bearer JWT        │  Security + JWT filter   │
│  RHF + Zod      │                     │  Services / Repositories │
└─────────────────┘                     │  Flyway migrations       │
                                        └────────────┬─────────────┘
                                                     │
                                                     ▼
                                            ┌────────────────┐
                                            │  PostgreSQL    │
                                            │  (Docker)      │
                                            └────────────────┘
```

### 3.2 Backend style

Classic layered Spring Boot app:

- **Controller** — HTTP, DTO mapping, validation annotations
- **Service** — business rules, authorization checks, activity logging
- **Repository** — Spring Data JPA
- **Entity** — JPA models
- **Security** — stateless JWT; method/URL rules by role
- **Flyway** — versioned SQL migrations (source of truth for schema)

### 3.3 Frontend style

- Feature-oriented folders under `src/features/`
- Shared UI/layout under `src/components/` and `src/layouts/`
- API client + auth token handling under `src/api/` and `src/auth/`
- Server state via TanStack Query; forms via RHF + Zod schemas
- Route guards for authenticated / role-restricted pages

### 3.4 Auth model

1. User registers or logs in → API returns access JWT (and optionally refresh later as stretch).
2. Frontend stores token (memory + `localStorage` for hackathon simplicity).
3. `Authorization: Bearer <token>` on API calls.
4. Roles: `ADMIN`, `SALES_REP`.

**Access rules (MVP):**

| Action | ADMIN | SALES_REP |
|--------|-------|-----------|
| Manage all users | ✓ | ✗ |
| CRUD own leads/contacts/deals/tasks/notes | ✓ | ✓ |
| View / edit all records | ✓ | Own only (or assigned) |
| Dashboard aggregates | Global | Own pipeline |

---

## 4. Monorepo Folder Structure

```text
flowcrm/
├── README.md
├── docker-compose.yml          # PostgreSQL (+ optional pgAdmin later)
├── docs/
│   ├── PROJECT_PLAN.md         # this file
│   ├── API.md                  # (later) endpoint reference
│   └── DOMAIN.md               # (later) entity notes if needed
├── backend/
│   ├── pom.xml
│   ├── Dockerfile              # optional / stretch
│   └── src/
│       ├── main/
│       │   ├── java/com/flowcrm/
│       │   │   ├── FlowcrmApplication.java
│       │   │   ├── config/           # Security, CORS, OpenAPI (stretch)
│       │   │   ├── security/         # JwtService, filters, UserDetails
│       │   │   ├── auth/             # register/login controllers + DTOs
│       │   │   ├── user/
│       │   │   ├── lead/
│       │   │   ├── contact/
│       │   │   ├── deal/             # pipeline / Kanban
│       │   │   ├── task/
│       │   │   ├── note/
│       │   │   ├── activity/
│       │   │   ├── dashboard/
│       │   │   ├── common/           # exceptions, pagination, base DTOs
│       │   │   └── enums/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/     # Flyway V1__, V2__, ...
│       └── test/java/com/flowcrm/
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tailwind.config.js
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── index.css
        ├── api/                  # axios/fetch client, interceptors
        ├── auth/                 # AuthContext, guards, token storage
        ├── routes/
        ├── layouts/              # AppShell, AuthLayout
        ├── components/           # shared UI (Button, Modal, Table, Kanban)
        ├── features/
        │   ├── auth/
        │   ├── dashboard/
        │   ├── leads/
        │   ├── contacts/
        │   ├── pipeline/         # Kanban board
        │   ├── tasks/
        │   └── notes/
        ├── hooks/
        ├── lib/                  # zod schemas, formatters
        └── types/
```

Package base: `com.flowcrm`.

---

## 5. Domain Model

### 5.1 Entity overview

```text
User 1──* Lead
User 1──* Contact
User 1──* Deal
User 1──* Task
User 1──* Note
User 1──* Activity

Lead 1──* Contact          (optional: contacts may also stand alone)
Lead 1──* Deal
Lead 1──* Note
Lead 1──* Activity

Contact *──* Deal          (MVP: Deal.primaryContact optional FK; many-to-many stretch)
Deal 1──* Task
Deal 1──* Note
Deal 1──* Activity

Task *──1 User (assignee)
Note *──1 User (author)
Activity *──1 User (actor)
```

### 5.2 Entities & fields

#### User
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| email | String | unique, login |
| passwordHash | String | BCrypt |
| fullName | String | |
| role | enum | `ADMIN`, `SALES_REP` |
| active | boolean | soft disable |
| createdAt / updatedAt | Instant | |

#### Lead
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| title / companyName | String | company or opportunity label |
| contactName | String | primary person name (quick capture) |
| email | String | nullable |
| phone | String | nullable |
| source | enum | `WEB`, `REFERRAL`, `COLD_CALL`, `EVENT`, `OTHER` |
| status | enum | `NEW`, `CONTACTED`, `QUALIFIED`, `LOST`, `CONVERTED` |
| owner | User | FK, required |
| createdAt / updatedAt | Instant | |

#### Contact
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| firstName / lastName | String | |
| email | String | nullable, indexed |
| phone | String | nullable |
| jobTitle | String | nullable |
| lead | Lead | nullable FK |
| owner | User | FK |
| createdAt / updatedAt | Instant | |

#### Deal (pipeline card)
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| name | String | |
| amount | BigDecimal | |
| currency | String | default `USD` (or INR if preferred) |
| stage | enum | `NEW`, `QUALIFICATION`, `PROPOSAL`, `NEGOTIATION`, `WON`, `LOST` |
| expectedCloseDate | LocalDate | nullable |
| lead | Lead | nullable FK |
| primaryContact | Contact | nullable FK |
| owner | User | FK |
| position | Integer | order within stage (Kanban) |
| createdAt / updatedAt | Instant | |

#### Task
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| title | String | |
| description | String | nullable |
| dueAt | Instant | |
| status | enum | `OPEN`, `DONE`, `CANCELLED` |
| priority | enum | `LOW`, `MEDIUM`, `HIGH` |
| assignee | User | FK |
| deal | Deal | nullable FK |
| lead | Lead | nullable FK |
| createdAt / updatedAt | Instant | |

#### Note
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| body | text | |
| author | User | FK |
| lead | Lead | nullable |
| contact | Contact | nullable |
| deal | Deal | nullable |
| createdAt / updatedAt | Instant | |
| Constraint: at least one of lead/contact/deal set |

#### Activity (append-only history)
| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| type | enum | `CREATED`, `UPDATED`, `STAGE_CHANGED`, `NOTE_ADDED`, `TASK_CREATED`, `TASK_COMPLETED`, `ASSIGNED` |
| message | String | human-readable summary |
| entityType | enum | `LEAD`, `CONTACT`, `DEAL`, `TASK`, `NOTE` |
| entityId | UUID | |
| actor | User | FK |
| metadata | JSON / text | optional small payload |
| createdAt | Instant | no updates |

### 5.3 Relationship summary

- **User** owns Leads, Contacts, Deals, Tasks; authors Notes; actors of Activities.
- **Lead** is top-of-funnel; may spawn Contacts and Deals.
- **Deal** is the Kanban item; stage + `position` drive the board.
- **Task** attaches to Deal and/or Lead for follow-ups.
- **Note** attaches to Lead / Contact / Deal.
- **Activity** is a write-mostly audit timeline for dashboard and detail pages.

---

## 6. REST API Endpoints

Base URL: `/api/v1`  
Auth: Bearer JWT except register/login.  
Pagination: `page`, `size`, `sort` (Spring pageable).  
Filtering: query params per resource.

### 6.1 Auth

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/auth/register` | Public | Create user (default `SALES_REP`; first user or seed may be `ADMIN`) |
| POST | `/auth/login` | Public | Return JWT + user profile |
| GET | `/auth/me` | Auth | Current user |

### 6.2 Users (ADMIN)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/users` | ADMIN | List users (paginated) |
| GET | `/users/{id}` | ADMIN | Get user |
| PATCH | `/users/{id}` | ADMIN | Update role / active flag |

### 6.3 Leads

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/leads` | Auth | List; filter `status`, `source`, `q`, owner scoped |
| POST | `/leads` | Auth | Create |
| GET | `/leads/{id}` | Auth | Detail (+ recent activities optional) |
| PUT/PATCH | `/leads/{id}` | Auth | Update |
| DELETE | `/leads/{id}` | Auth | Delete (ADMIN or owner) |

### 6.4 Contacts

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/contacts` | Auth | List; filter `q`, `leadId` |
| POST | `/contacts` | Auth | Create |
| GET | `/contacts/{id}` | Auth | Detail |
| PUT/PATCH | `/contacts/{id}` | Auth | Update |
| DELETE | `/contacts/{id}` | Auth | Delete |

### 6.5 Deals / Pipeline

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/deals` | Auth | List; filter `stage`, `ownerId`, `q` |
| GET | `/deals/board` | Auth | Kanban payload grouped by stage |
| POST | `/deals` | Auth | Create |
| GET | `/deals/{id}` | Auth | Detail |
| PUT/PATCH | `/deals/{id}` | Auth | Update fields |
| PATCH | `/deals/{id}/stage` | Auth | Move stage + optional `position` |
| DELETE | `/deals/{id}` | Auth | Delete |

### 6.6 Tasks

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/tasks` | Auth | List; filter `status`, `dueBefore`, `assigneeId` |
| POST | `/tasks` | Auth | Create |
| GET | `/tasks/{id}` | Auth | Detail |
| PUT/PATCH | `/tasks/{id}` | Auth | Update |
| PATCH | `/tasks/{id}/complete` | Auth | Mark done |
| DELETE | `/tasks/{id}` | Auth | Delete |

### 6.7 Notes

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/notes` | Auth | List by `leadId` / `contactId` / `dealId` |
| POST | `/notes` | Auth | Create (writes Activity) |
| DELETE | `/notes/{id}` | Auth | Delete (author or ADMIN) |

### 6.8 Activities

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/activities` | Auth | Timeline; filter `entityType`, `entityId` |

### 6.9 Dashboard

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/dashboard/summary` | Auth | Counts: leads by status, deals by stage, open tasks, won amount (scoped by role) |

### 6.10 Common response shapes

- Success entity / page: `{ content, page, size, totalElements, totalPages }` for lists
- Errors: `{ timestamp, status, error, message, fieldErrors? }`
- Auth: `{ accessToken, tokenType, expiresIn, user: { id, email, fullName, role } }`

---

## 7. Frontend Routes (MVP)

| Path | Page | Access |
|------|------|--------|
| `/login` | Login | Public |
| `/register` | Register | Public |
| `/` | Dashboard | Auth |
| `/leads` | Lead list | Auth |
| `/leads/:id` | Lead detail | Auth |
| `/contacts` | Contact list | Auth |
| `/contacts/:id` | Contact detail | Auth |
| `/pipeline` | Kanban board | Auth |
| `/deals/:id` | Deal detail | Auth |
| `/tasks` | Task list / due reminders | Auth |
| `/users` | User admin | ADMIN |

---

## 8. Implementation Phases

Small, demoable increments. Each phase ends in something runnable.

### Phase 0 — Repo & tooling (Day 1)
- Monorepo folders, root README, `docker-compose.yml` (Postgres)
- Backend Spring Boot skeleton + Flyway empty/baseline
- Frontend Vite React TS + Tailwind + Router scaffold
- CORS + health check

### Phase 1 — Auth & users (Days 1–2)
- Flyway: `users` table
- Register / login / JWT / `/auth/me`
- Security config + role enums
- Frontend auth pages, token storage, route guards
- Seed or promote one ADMIN

### Phase 2 — Leads & contacts (Days 2–4)
- Migrations + entities for Lead, Contact
- CRUD APIs with validation, ownership, search/filter/pagination
- Frontend list + detail + forms (RHF + Zod)
- Basic activity logging on create/update

### Phase 3 — Pipeline / deals (Days 4–6)
- Deal entity + stage enums + position
- Board API + stage move endpoint
- Kanban UI (columns by stage, drag or move controls)
- Deal detail linked to lead/contact

### Phase 4 — Tasks & notes (Days 6–7)
- Task CRUD + complete + due filters
- Notes on lead/contact/deal
- Activity timeline on detail pages
- Simple “due today / overdue” on tasks page

### Phase 5 — Dashboard & polish (Days 7–8)
- `/dashboard/summary` + charts/stat cards
- Consistent loading/error empty states
- ADMIN user list (role/active)
- README run instructions; sample data script or migration seed

### Phase 6 — Hardening & demo (Days 9–10)
- Bugfix, validation messages, auth edge cases
- Basic automated tests (auth + one resource)
- Demo script / screenshots in `docs/`
- Stretch items only if MVP is solid

---

## 9. MVP vs Stretch

### Minimum viable hackathon version (must ship)

1. Register / login with JWT  
2. Roles `ADMIN` and `SALES_REP` enforced on API + UI  
3. Lead CRUD with search, filter, pagination  
4. Contact CRUD linked to leads  
5. Deal Kanban (list by stage + move stage)  
6. Tasks with due dates + mark complete  
7. Notes + activity timeline on detail views  
8. Dashboard summary counts  
9. Docker Compose Postgres + Flyway migrations  
10. README: how to run backend, frontend, DB  

### Stretch (only after MVP)

- Refresh tokens / logout denylist  
- Drag-and-drop Kanban with optimistic UI  
- Email notifications / reminder worker  
- File attachments on notes  
- Global full-text search  
- CSV import/export  
- Soft deletes everywhere  
- OpenAPI/Swagger UI  
- Backend Dockerfile + full compose stack  
- Audit of who viewed records  
- Many-to-many contacts ↔ deals  
- Unit/integration test suite beyond smoke  
- Dark mode / advanced design system  

---

## 10. Cross-cutting Concerns

| Concern | MVP approach |
|---------|----------------|
| IDs | UUID |
| Time | UTC `Instant` in API; format in UI |
| Validation | Bean Validation + Zod mirroring |
| Errors | `@ControllerAdvice` problem-style JSON |
| Ownership | Service-layer checks; ADMIN bypass |
| CORS | Allow Vite dev origin |
| Secrets | `application.yml` + env vars (`JWT_SECRET`, DB URL) |
| Migrations | Flyway only (no `ddl-auto=update` in prod profile) |
| API versioning | `/api/v1` prefix |

---

## 11. Day-by-day sketch (10 days)

| Day | Focus |
|-----|--------|
| 1 | Phase 0 + start Phase 1 (auth backend) |
| 2 | Finish auth + frontend login/register |
| 3 | Leads API + UI |
| 4 | Contacts + wire to leads |
| 5 | Deals + board API |
| 6 | Kanban UI + stage moves |
| 7 | Tasks + notes + activities |
| 8 | Dashboard + ADMIN users |
| 9 | Polish, seeds, README, bugfix |
| 10 | Demo prep + optional stretch |

---

## 12. Success criteria for demo

- Fresh machine: `docker compose up`, run backend, run frontend → register → use CRM
- Sales rep can manage own leads → contacts → deals on Kanban → tasks/notes
- Admin can see broader data / manage users
- Pipeline and dashboard look credible for a live walkthrough

---

## 13. Out of scope (explicit)

- Mobile native apps  
- Multi-tenancy / organizations  
- Billing / subscriptions  
- Real email/SMS providers  
- Advanced reporting warehouses  

---

## 14. Approval checkpoint

**No application scaffolding or feature code until this plan is approved.**

Please confirm or request changes on:

1. Domain fields / enums (especially Deal stages and currency default)  
2. Ownership rules (own-only vs ADMIN sees all)  
3. MVP boundary vs stretch list  
4. Phase ordering  

After approval, implementation starts at **Phase 0**.
