import { ActivityTimeline } from '../activity/ActivityTimeline'
import { AskFlowAiButton } from '../assistant/AskFlowAiButton'
import type { Account } from '../../types/account'
import type { Contact } from '../../types/contact'
import { formatDateTime } from '../../utils/taskDates'

type AccountDetailsProps = {
  open: boolean
  account: Account | null
  contacts: Contact[]
  contactsLoading?: boolean
  onClose: () => void
  onEdit: (account: Account) => void
  onDelete: (account: Account) => void
  onOpenContact?: (contact: Contact) => void
  onAddTask?: (account: Account) => void
  onAddMeeting?: (account: Account) => void
  onAddCall?: (account: Account) => void
  activityRefreshKey?: number
}

export function AccountDetails({
  open,
  account,
  contacts,
  contactsLoading = false,
  onClose,
  onEdit,
  onDelete,
  onOpenContact,
  onAddTask,
  onAddMeeting,
  onAddCall,
  activityRefreshKey = 0,
}: AccountDetailsProps) {
  if (!open || !account) return null

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/30">
      <button type="button" aria-label="Close account details" className="flex-1 cursor-default" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-details-title"
        className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="account-details-title" className="truncate text-lg font-semibold text-ink">
              {account.name}
            </h2>
            <p className="mt-0.5 text-sm text-muted">{account.industry || 'No industry'}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-slate-100">
            Close
          </button>
        </header>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <dl className="space-y-3 text-sm">
            <Row label="Website" value={account.website || '—'} />
            <Row label="Phone" value={account.phone || '—'} />
            <Row label="Owner" value={account.ownerName} />
            <Row label="Created" value={formatDateTime(account.createdAt)} />
          </dl>
          {account.description ? (
            <p className="whitespace-pre-wrap text-sm text-slate-700">{account.description}</p>
          ) : null}

          <section>
            <h3 className="text-sm font-semibold text-ink">Contacts ({account.contactCount})</h3>
            {contactsLoading ? (
              <p className="mt-2 text-sm text-muted">Loading contacts…</p>
            ) : contacts.length === 0 ? (
              <p className="mt-2 text-sm text-muted">No contacts linked to this account.</p>
            ) : (
              <ul className="mt-2 space-y-2">
                {contacts.map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => onOpenContact?.(c)}
                      className="w-full rounded-lg border border-border px-3 py-2 text-left text-sm hover:bg-slate-50"
                    >
                      <div className="font-medium text-ink">
                        {c.firstName} {c.lastName}
                      </div>
                      <div className="text-xs text-muted">{c.jobTitle || c.email || 'Contact'}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <ActivityTimeline entityType="ACCOUNT" entityId={account.id} refreshKey={activityRefreshKey} />
        </div>

        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {onAddTask ? (
            <button type="button" onClick={() => onAddTask(account)} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              + Task
            </button>
          ) : null}
          {onAddMeeting ? (
            <button type="button" onClick={() => onAddMeeting(account)} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              + Meeting
            </button>
          ) : null}
          {onAddCall ? (
            <button type="button" onClick={() => onAddCall(account)} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              + Call
            </button>
          ) : null}
          <AskFlowAiButton entityType="ACCOUNT" entityId={account.id} label={account.name} />
          <button
            type="button"
            onClick={() => onEdit(account)}
            className="flex-1 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => onDelete(account)}
            className="rounded-lg border border-red-200 bg-white px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
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
