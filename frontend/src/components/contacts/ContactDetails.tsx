import { ActivityTimeline } from '../activity/ActivityTimeline'
import { AskFlowAiButton } from '../assistant/AskFlowAiButton'
import type { Contact } from '../../types/contact'
import { formatDateTime } from '../../utils/taskDates'

type ContactDetailsProps = {
  open: boolean
  contact: Contact | null
  onClose: () => void
  onEdit: (contact: Contact) => void
  onDelete: (contact: Contact) => void
  onAddTask?: (contact: Contact) => void
  onAddMeeting?: (contact: Contact) => void
  onAddCall?: (contact: Contact) => void
  activityRefreshKey?: number
}

export function ContactDetails({
  open,
  contact,
  onClose,
  onEdit,
  onDelete,
  onAddTask,
  onAddMeeting,
  onAddCall,
  activityRefreshKey = 0,
}: ContactDetailsProps) {
  if (!open || !contact) return null

  return (
    <div className="fixed inset-0 z-40 flex justify-end overlay-backdrop">
      <button type="button" aria-label="Close contact details" className="flex-1 cursor-default" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="contact-details-title"
        className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="contact-details-title" className="truncate text-lg font-semibold text-ink">
              {contact.firstName} {contact.lastName}
            </h2>
            <p className="mt-0.5 text-sm text-muted">{contact.jobTitle || contact.accountName || 'Contact'}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-canvas">
            Close
          </button>
        </header>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <dl className="space-y-3 text-sm">
            <Row label="Account" value={contact.accountName || 'No account'} />
            <Row label="Email" value={contact.email || '—'} />
            <Row label="Phone" value={contact.phone || '—'} />
            <Row label="Owner" value={contact.ownerName} />
            <Row label="Updated" value={formatDateTime(contact.updatedAt)} />
          </dl>
          {contact.notes ? (
            <p className="whitespace-pre-wrap text-sm text-ink">{contact.notes}</p>
          ) : null}

          <ActivityTimeline entityType="CONTACT" entityId={contact.id} refreshKey={activityRefreshKey} />
        </div>

        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {onAddTask ? (
            <button type="button" onClick={() => onAddTask(contact)} className="btn btn-secondary">
              + Task
            </button>
          ) : null}
          {onAddMeeting ? (
            <button type="button" onClick={() => onAddMeeting(contact)} className="btn btn-secondary">
              + Meeting
            </button>
          ) : null}
          {onAddCall ? (
            <button type="button" onClick={() => onAddCall(contact)} className="btn btn-secondary">
              + Call
            </button>
          ) : null}
          <AskFlowAiButton entityType="CONTACT" entityId={contact.id} label={`${contact.firstName} ${contact.lastName}`} />
          <button
            type="button"
            onClick={() => onEdit(contact)}
            className="flex-1 btn btn-primary"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => onDelete(contact)}
            className="rounded-lg border border-red-200 bg-surface px-3 py-2 text-sm font-medium text-[color:var(--app-danger-text)] hover:bg-red-50"
          >
            Delete
          </button>
        </footer>
      </aside>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] gap-2">
      <dt className="text-muted">{label}</dt>
      <dd className="break-words text-ink">{value}</dd>
    </div>
  )
}
