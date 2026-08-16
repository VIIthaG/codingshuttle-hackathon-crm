# FlowCRM Frontend

React SPA for the FlowCRM Mini CRM backend.

## Stack

Versions from `package.json`:

- React **19.2**
- TypeScript
- Vite **8.2**
- React Router **7.18**
- Tailwind CSS **4.3**
- lucide-react (icons)
- Recharts **3.10** (dashboard + analytics charts)
- oxlint (lint)

## Requirements

- Node.js **20+** recommended (Vite 8 / current tooling)

## Setup

```powershell
cd frontend
npm install
copy .env.example .env
```

### Environment

| Variable | Purpose |
|----------|---------|
| `VITE_API_BASE_URL` | Backend origin for `fetch`. Leave **empty** for local Vite so `/api` is proxied to `http://localhost:8080` (see `vite.config.ts`). For a deployed SPA, set the public API URL (no trailing slash), e.g. `https://your-api.example.com`. |

Do **not** put JWT signing secrets in frontend env vars. Do not commit a filled `.env` with real production URLs if your team treats them as private.

## Scripts

```powershell
npm run dev      # http://localhost:5173
npm run build    # production build to dist/
npm run lint     # oxlint
npm run preview  # preview production build
```

## Auth / JWT storage (MVP tradeoff)

For this hackathon SPA, the access token and user profile are stored in **`localStorage`** so sessions survive refresh.

This is a **demo/MVP convenience**, not the strongest production browser security model (XSS can read `localStorage`). Prefer httpOnly cookies / tighter CSP for hardened production auth.

On 401 from `/api/v1/auth/me` during bootstrap, the SPA clears the stored session and redirects to login.

## Routes

| Path | Access |
|------|--------|
| `/` | Redirect to `/dashboard` or `/login` |
| `/login`, `/register` | Guests only |
| `/dashboard`, `/analytics`, `/workqueue`, `/calendar`, `/accounts`, `/contacts`, `/deals`, `/leads`, `/tasks`, `/meetings`, `/calls` | Authenticated (shell) |

Capabilities:

- **Dashboard** — live role-aware summary, compact lead/deal charts, upcoming work from the workqueue
- **Analytics** — date-range metrics and charts (`?range=7d|30d|90d|all`); ADMIN team workload table and optional assignee filter. No fabricated historical stage audit.
- **Accounts** — company list/search, create/edit/delete, owner assignment (ADMIN)
- **Contacts** — people list/search, optional account link, create/edit/delete
- **Deals** — kanban + table, create/edit/delete, validated stage transitions
- **Leads** — pipeline + list, create/edit/delete, validated status transitions, QUALIFIED convert, activity + Task/Meeting/Call
- **Accounts / Contacts / Deals** — details include the reusable activity timeline and contextual Task/Meeting/Call
- **Tasks / Meetings / Calls** — each related to exactly one CRM record
- **Calendar** — week agenda of OPEN tasks, SCHEDULED meetings, PLANNED calls
- **Workqueue** — overdue / today / upcoming next actions with complete/cancel
- **Header** — global search (Ctrl/Cmd+K), Quick Create, Flow AI drawer, notification bell
- **Flow AI** — optional read-only assistant. Header **Flow AI** and **Ask Flow AI** on Lead/Account/Contact/Deal details. Conversation is session-only (not persisted). If the provider is unconfigured, the UI shows an unavailable message; CRM still works.

Reminders are scheduled by sending `reminderAt` to the backend; the SPA does not deliver email/SMS or run local reminder timers. Assignment notifications are polled (~45s) and stored on the server.
