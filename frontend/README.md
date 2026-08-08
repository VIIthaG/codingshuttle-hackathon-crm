# FlowCRM Frontend

React SPA for the FlowCRM Mini CRM backend.

## Stack

- React 19
- TypeScript
- Vite 8
- React Router 7
- Tailwind CSS 4
- lucide-react (icons)

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
| `VITE_API_BASE_URL` | Backend origin for `fetch`. Leave **empty** for local Vite so `/api` is proxied to `http://localhost:8080` (see `vite.config.ts`). For a deployed SPA, set the public API URL. |

Do **not** put JWT signing secrets in frontend env vars.

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
| `/dashboard`, `/leads`, `/tasks` | Authenticated (shell) |

Leads and Tasks pages are live against the Spring Boot API (pipeline/list for leads; follow-up tasks with due/reminder fields). Dashboard loads live summary data.

Reminders are scheduled by sending `reminderAt` to the backend; the SPA does not deliver email/SMS or run local reminder timers.
