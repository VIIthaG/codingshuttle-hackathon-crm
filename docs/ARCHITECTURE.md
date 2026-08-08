# FlowCRM Architecture & Design Decisions

This document expands on the root [README](../README.md). It describes **what exists in the repository today**, why those choices were made, and what is explicitly **not** claimed as implemented.

Related diagram source: [architecture.mmd](architecture.mmd)

---

## What exists now

FlowCRM is a **modular monolith**:

- One Spring Boot process (`backend/`)
- Package boundaries by domain (`auth`, `lead`, `task`, `dashboard`, `outbox`, `reminder`, `idempotency`, `ratelimit`, `lock`, …)
- Shared PostgreSQL as the durable system of record
- Redis for cache / rate-limit / distributed lock
- RabbitMQ for asynchronous reminder processing

There is **no** service mesh and **no** separate reminder microservice. A React/Vite SPA lives under `frontend/` and talks to the same JWT-protected REST API (local Vite proxies `/api`).

Reminder delivery is a **logging simulation** (`LoggingReminderDeliveryService`). Email/SMS providers are not integrated.

---

## Why a modular monolith (not microservices)

For a Mini CRM at hackathon scope, splitting “leads”, “tasks”, and “reminders” into separately deployable services would add:

- distributed transaction complexity without proportional product value
- operational overhead (multiple deploys, contracts, tracing)
- harder local demos

A modular Spring Boot app still demonstrates production patterns (outbox, Redis, RabbitMQ) while keeping consistency boundaries understandable: **business writes and outbox writes share one PostgreSQL transaction**.

---

## PostgreSQL as source of truth

PostgreSQL stores:

| Table / area | Role |
|--------------|------|
| `users` | Auth identity + role |
| `leads` / `tasks` | CRM domain state |
| `outbox_events` | Intended async side effects awaiting publish |
| `processed_messages` | Consumer receipts (message id = outbox event id) |
| `idempotency_records` | Durable HTTP create idempotency |

**Why:** CRM correctness depends on durable state. Caches and brokers are helpers; if Redis or RabbitMQ is down, domain data remains in Postgres. Flyway V1–V8 version that schema.

### Why not edit historical Flyway scripts

Applied migrations are checksummed. Rewriting `V1`–`V8` breaks existing databases and CI. Schema evolution must be additive (`V9__…`).

---

## Why RabbitMQ instead of synchronous reminder execution

Synchronous “send reminder inside the HTTP request” fails when:

- `reminderAt` is hours/days in the future
- the API process restarts before the timer fires
- reminder work is slow or flaky

FlowCRM instead:

1. Persists intent in `outbox_events` with `available_at = reminderAt`
2. Publishes only when due
3. Processes asynchronously with retry/DLQ

This keeps HTTP create/update fast and recoverable.

---

## Transactional outbox — tradeoff

**Guarantee sought:** never lose a reminder schedule that committed with a task, and never publish a schedule that rolled back with the task.

**Mechanism:** `OutboxEventRecorder` runs in the caller’s transaction (`TaskService` create/update/complete/delete).

**Tradeoff accepted:** Postgres commit and RabbitMQ publish are **not** one distributed transaction.

`OutboxPublisher` marks `PUBLISHED` only after a successful broker send. If the process crashes between broker ACK and the DB update, a later poll may republish the same event. That is why consumers must be **idempotent** via `processed_messages`.

The retry queue is **not** used as a delay scheduler for future `reminderAt` values; delay is enforced by leaving rows `PENDING` until `available_at`.

---

## Redis — why and where

| Use | Why Redis |
|-----|-----------|
| Dashboard cache | Hot read path; short TTL (~60s) is enough for demo UX |
| Login rate limit | Shared counters across instances; atomic Lua INCR/PEXPIRE |
| Outbox lock | Coordinate `@Scheduled` publishers across instances |

Redis is **not** the source of truth for leads/tasks/outbox.

### Consistency notes

- Dashboard cache is **eventually consistent** within TTL, tightened by eviction on mutations (`allEntries` so ADMIN aggregates are not left stale when another user’s lead changes).
- Rate-limit fail-open (default) prefers auth availability over strict limiting when Redis errors.
- Lock acquire fail-closed: if Redis cannot grant the lock, the publisher **skips** the cycle rather than running unlocked on every instance.

---

## Idempotency strategy (HTTP creates)

Applied only to:

- `POST /api/v1/leads` → operation `LEADS_CREATE`
- `POST /api/v1/tasks` → operation `TASKS_CREATE`

**Scope:** authenticated `user_id` + operation + `Idempotency-Key`.

**Fingerprint:** SHA-256 over deterministic JSON (sorted properties).

**Claim:** `INSERT … ON CONFLICT DO NOTHING` against unique `(user_id, operation, idempotency_key)`.

**Outcomes:**

- Owner runs business create; stores response JSON on success in the same transaction as the business write (claim row already STARTED via `REQUIRES_NEW` insert).
- Loser awaits/replays or returns 409 on fingerprint mismatch.
- Failed business work releases the STARTED claim so the client may retry the same key.

This is **request idempotency**, not “exactly-once messaging.”

---

## Retry / DLQ strategy

| Setting | Default |
|---------|---------|
| Max attempts | `app.reminders.max-attempts` = 3 |
| Retry delay | `app.reminders.retry-delay-ms` = 5000 (queue TTL) |

`ReminderConsumer` catches processing failures, republishes to `reminder.retry` with `x-attempt`, or to `reminder.dlq` when attempts are exhausted. Exceptions are swallowed after routing so the main queue’s DLX does not also dead-letter the same failure.

Stale reminders (task gone / not OPEN / reminder cleared / `reminderAt` mismatch) are **acknowledged without delivery** and do not enter retry/DLQ.

---

## Distributed locking

`OutboxPublisher.publishPendingBatch`:

1. `tryAcquire(lock:flowcrm:outbox-publisher, ttl)`
2. Publish due batch
3. Release with owner token (Lua compare-and-delete)

This reduces concurrent double-publish races across instances. It does **not** replace consumer idempotency.

---

## RBAC model

| Role | Behavior |
|------|----------|
| `ADMIN` | Sees all leads/tasks; may assign to other users |
| `SALES_REP` | Sees assigned leads/tasks; self-assign by default |

First registered user → `ADMIN`; subsequent → `SALES_REP`.

Security is **stateless JWT**; no server sessions.

---

## Failure scenarios (based on source behavior)

### RabbitMQ unavailable

- HTTP task create still succeeds if the DB transaction commits (outbox row remains `PENDING`).
- `OutboxPublisher` logs publish failure and leaves the row `PENDING` for a later poll.
- Consumers simply are not running or cannot connect when messaging is down.

### Reminder processing fails

- Consumer routes to retry (until max attempts), then DLQ.
- Dev/test hook: `app.reminders.fail-delivery=true` forces `LoggingReminderDeliveryService` to throw.

### Same RabbitMQ message delivered twice

- `processed_messages` primary key = event id.
- Second delivery is skipped; concurrent insert races are caught as integrity violations and treated as already processed.

### Two concurrent HTTP creates with the same Idempotency-Key

- Unique constraint + atomic claim ⇒ one owner creates the resource.
- The other waits/replays the stored 201 response (same payload) or gets 409 (different payload).
- For tasks, only one outbox schedule is written for that successful create.

### Redis unavailable for login rate limiting

- With default `fail-open=true`, requests are allowed and a warning is logged.
- With `fail-open=false`, limiter denies (fail-closed).

### Multiple app instances run the outbox scheduler

- Only instances that acquire the Redis lock publish in that cycle.
- If lock acquisition fails (contention or Redis error), that instance skips.
- Residual duplicate publish still possible across crash windows; consumers remain idempotent.

### Task reminder rescheduled after an old event exists

- Pending outbox rows for that task are marked `SUPERSEDED` and will not be published.
- A new PENDING row is recorded with the new `available_at` when appropriate.
- An already-published message whose payload `reminderAt` no longer matches the task is treated as **stale** and skipped.

---

## Scaling considerations (honest)

What helps multi-instance operation today:

- Stateless API + JWT
- Shared Postgres / Redis / RabbitMQ
- Outbox lock + consumer idempotency + HTTP idempotency records

What is **not** solved here:

- Horizontal scaling of every write path under extreme load
- Partitioned outbox claiming with SKIP LOCKED (possible future improvement)
- Exactly-once end-to-end delivery
- Multi-region active-active designs

---

## Possible future production improvements

**Not implemented today — do not treat as current features:**

- Real email/SMS/push providers behind `ReminderDeliveryService`
- Outbox row leasing / `SKIP LOCKED` claiming
- Metrics, tracing, alerting on DLQ depth
- Idempotency record TTL/cleanup jobs
- Hardened secrets management (no local JWT defaults in shared envs)
- Broader idempotency beyond selected POSTs

(The React/Vite SPA under `frontend/` and Railway-oriented backend packaging under `docs/DEPLOYMENT.md` are implemented; treat remaining items above as still future work.)

---

## Related planning doc

[PROJECT_PLAN.md](PROJECT_PLAN.md) captures the original phased build plan. Prefer this architecture document and the root README for the as-built system description after Phase G.
