import { Link } from 'react-router-dom'

export function TasksPage() {
  return (
    <div className="rounded-xl border border-border bg-surface p-8 shadow-sm">
      <h2 className="text-lg font-semibold text-ink">Tasks</h2>
      <p className="mt-2 max-w-xl text-sm text-muted">
        Task management UI will be added in the next frontend phase. Follow-up reminders continue
        to run on the backend outbox/RabbitMQ path.
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
