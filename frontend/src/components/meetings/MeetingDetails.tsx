import { relatedTypeLabel } from '../../types/task'
import type { Meeting } from '../../types/meeting'
import { formatDateTime } from '../../utils/taskDates'

export function MeetingDetails({
  open,
  meeting,
  pending,
  error,
  onClose,
  onEdit,
  onComplete,
  onCancel,
  onDelete,
}: {
  open: boolean
  meeting: Meeting | null
  pending?: boolean
  error?: string | null
  onClose: () => void
  onEdit: (meeting: Meeting) => void
  onComplete: (meeting: Meeting) => void
  onCancel: (meeting: Meeting) => void
  onDelete: (meeting: Meeting) => void
}) {
  if (!open || !meeting) return null
  const active = meeting.status === 'SCHEDULED'
  return (
    <div className="fixed inset-0 z-40 flex justify-end overlay-backdrop">
      <button type="button" className="flex-1" aria-label="Close meeting" onClick={onClose} />
      <aside className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl">
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-ink">{meeting.title}</h2>
            <p className="text-sm text-muted">
              {relatedTypeLabel(meeting.relatedType)} · {meeting.relatedName}
            </p>
          </div>
          <button type="button" onClick={onClose} className="text-sm text-muted">Close</button>
        </header>
        <div className="flex-1 space-y-3 overflow-y-auto px-5 py-5 text-sm">
          <div className="font-semibold">{meeting.status}</div>
          <p>Start: {formatDateTime(meeting.startAt)}</p>
          <p>End: {formatDateTime(meeting.endAt)}</p>
          <p>Location: {meeting.location || '—'}</p>
          <p>URL: {meeting.meetingUrl || '—'}</p>
          <p>Assigned: {meeting.assignedToName}</p>
          {meeting.description ? <p className="whitespace-pre-wrap">{meeting.description}</p> : null}
          {error ? <p className="text-[color:var(--app-danger-text)]">{error}</p> : null}
        </div>
        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {active ? (
            <>
              <button type="button" disabled={pending} onClick={() => onComplete(meeting)} className="btn btn-primary">Complete</button>
              <button type="button" disabled={pending} onClick={() => onCancel(meeting)} className="rounded-lg border px-3 py-2 text-sm">Cancel meeting</button>
            </>
          ) : null}
          <button type="button" disabled={pending} onClick={() => onEdit(meeting)} className="rounded-lg bg-brand-600 px-3 py-2 text-sm text-white">Edit</button>
          <button type="button" disabled={pending} onClick={() => onDelete(meeting)} className="rounded-lg border border-red-200 px-3 py-2 text-sm text-[color:var(--app-danger-text)]">Delete</button>
        </footer>
      </aside>
    </div>
  )
}
