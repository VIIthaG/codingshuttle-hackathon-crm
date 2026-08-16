import { relatedTypeLabel } from '../../types/task'
import type { Call } from '../../types/call'
import { formatDateTime } from '../../utils/taskDates'
import { useState } from 'react'

export function CallDetails({
  open,
  call,
  pending,
  error,
  onClose,
  onEdit,
  onComplete,
  onCancel,
  onDelete,
}: {
  open: boolean
  call: Call | null
  pending?: boolean
  error?: string | null
  onClose: () => void
  onEdit: (call: Call) => void
  onComplete: (call: Call, outcome: string | null) => void
  onCancel: (call: Call) => void
  onDelete: (call: Call) => void
}) {
  const [outcome, setOutcome] = useState('')
  if (!open || !call) return null
  const active = call.status === 'PLANNED'
  return (
    <div className="fixed inset-0 z-40 flex justify-end overlay-backdrop">
      <button type="button" className="flex-1" aria-label="Close call" onClick={onClose} />
      <aside className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl">
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-ink">{call.title}</h2>
            <p className="text-sm text-muted">
              {relatedTypeLabel(call.relatedType)} · {call.relatedName}
            </p>
          </div>
          <button type="button" onClick={onClose} className="text-sm text-muted">Close</button>
        </header>
        <div className="flex-1 space-y-3 overflow-y-auto px-5 py-5 text-sm">
          <div className="font-semibold">{call.status} · {call.direction}</div>
          <p>When: {formatDateTime(call.scheduledAt)}</p>
          <p>Duration: {call.durationMinutes ?? '—'} min</p>
          <p>Phone: {call.phoneNumber || '—'}</p>
          <p>Assigned: {call.assignedToName}</p>
          {call.description ? <p className="whitespace-pre-wrap">{call.description}</p> : null}
          {call.outcome ? <p>Outcome: {call.outcome}</p> : null}
          {active ? (
            <label className="block">
              Outcome (optional)
              <input value={outcome} onChange={(e) => setOutcome(e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2" />
            </label>
          ) : null}
          {error ? <p className="text-[color:var(--app-danger-text)]">{error}</p> : null}
        </div>
        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {active ? (
            <>
              <button type="button" disabled={pending} onClick={() => onComplete(call, outcome.trim() || null)} className="btn btn-primary">Complete</button>
              <button type="button" disabled={pending} onClick={() => onCancel(call)} className="rounded-lg border px-3 py-2 text-sm">Cancel call</button>
            </>
          ) : null}
          <button type="button" disabled={pending} onClick={() => onEdit(call)} className="rounded-lg bg-brand-600 px-3 py-2 text-sm text-white">Edit</button>
          <button type="button" disabled={pending} onClick={() => onDelete(call)} className="rounded-lg border border-red-200 px-3 py-2 text-sm text-[color:var(--app-danger-text)]">Delete</button>
        </footer>
      </aside>
    </div>
  )
}
