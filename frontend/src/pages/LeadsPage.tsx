import { Link } from 'react-router-dom'

export function LeadsPage() {
  return (
    <div className="rounded-xl border border-border bg-surface p-8 shadow-sm">
      <h2 className="text-lg font-semibold text-ink">Leads</h2>
      <p className="mt-2 max-w-xl text-sm text-muted">
        Lead list and pipeline actions will be added in the next frontend phase. The backend API
        is already available via Swagger.
      </p>
      <Link
        to="/dashboard"
        className="mt-6 inline-flex text-sm font-medium text-brand-600 hover:text-brand-700"
      >
        Back to dashboard
      </Link>
    </div>
  )
}
