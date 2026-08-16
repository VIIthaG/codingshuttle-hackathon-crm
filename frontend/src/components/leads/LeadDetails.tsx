import { ActivityTimeline } from '../activity/ActivityTimeline'
import { AskFlowAiButton } from '../assistant/AskFlowAiButton'
import { Link } from 'react-router-dom'
import type { Lead, LeadStatus } from '../../types/lead'
import { allowedLeadTransitions, isTerminalLeadStatus } from '../../utils/leadTransitions'
import { LeadSourceBadge, LeadStatusBadge } from '../StatusBadge'

type LeadDetailsProps = {
  open: boolean
  lead: Lead | null
  statusPending?: boolean
  statusError?: string | null
  onClose: () => void
  onEdit: (lead: Lead) => void
  onDelete: (lead: Lead) => void
  onChangeStatus: (lead: Lead, status: LeadStatus) => void
  onConvert: (lead: Lead) => void
  onAddTask: (lead: Lead) => void
  onAddMeeting: (lead: Lead) => void
  onAddCall: (lead: Lead) => void
  activityRefreshKey?: number
}

export function LeadDetails({
  open,
  lead,
  statusPending = false,
  statusError = null,
  onClose,
  onEdit,
  onDelete,
  onChangeStatus,
  onConvert,
  onAddTask,
  onAddMeeting,
  onAddCall,
  activityRefreshKey = 0,
}: LeadDetailsProps) {
  if (!open || !lead) return null

  const transitions = allowedLeadTransitions(lead.status)
  const terminal = isTerminalLeadStatus(lead.status)

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/30">
      <button
        type="button"
        aria-label="Close lead details"
        className="flex-1 cursor-default"
        onClick={onClose}
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="lead-details-title"
        className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="lead-details-title" className="truncate text-lg font-semibold text-ink">
              {lead.fullName}
            </h2>
            <p className="mt-0.5 text-sm text-muted">{lead.company || 'No company'}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-slate-100"
          >
            Close
          </button>
        </header>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <div className="flex flex-wrap gap-2">
            <LeadStatusBadge status={lead.status} />
            <LeadSourceBadge source={lead.source} />
          </div>

          <dl className="space-y-3 text-sm">
            <Row label="Email" value={lead.email || '—'} />
            <Row label="Phone" value={lead.phone || '—'} />
            <Row label="Assigned to" value={lead.assignedToName} />
            <Row label="Created" value={formatDate(lead.createdAt)} />
            <Row label="Updated" value={formatDate(lead.updatedAt)} />
          </dl>

          {lead.status === 'QUALIFIED' ? (
            <button
              type="button"
              disabled={statusPending}
              onClick={() => onConvert(lead)}
              className="w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
            >
              Convert lead
            </button>
          ) : null}

          {lead.status === 'CONVERTED' ? (
            <section className="rounded-lg border border-border bg-canvas px-3 py-3">
              <h3 className="text-sm font-semibold text-ink">Conversion</h3>
              <dl className="mt-3 space-y-3 text-sm">
                <Row label="Converted at" value={lead.convertedAt ? formatDate(lead.convertedAt) : '—'} />
                <div className="grid grid-cols-[7rem_1fr] gap-2">
                  <dt className="text-muted">Account</dt>
                  <dd>
                    {lead.convertedAccountId ? (
                      <Link
                        to={`/accounts?open=${lead.convertedAccountId}`}
                        className="font-medium text-brand-700 hover:underline"
                      >
                        {lead.convertedAccountName || 'View account'}
                      </Link>
                    ) : (
                      '—'
                    )}
                  </dd>
                </div>
                <div className="grid grid-cols-[7rem_1fr] gap-2">
                  <dt className="text-muted">Contact</dt>
                  <dd>
                    {lead.convertedContactId ? (
                      <Link
                        to={`/contacts?open=${lead.convertedContactId}`}
                        className="font-medium text-brand-700 hover:underline"
                      >
                        {lead.convertedContactName || 'View contact'}
                      </Link>
                    ) : (
                      '—'
                    )}
                  </dd>
                </div>
                <div className="grid grid-cols-[7rem_1fr] gap-2">
                  <dt className="text-muted">Deal</dt>
                  <dd>
                    {lead.convertedDealId ? (
                      <Link
                        to={`/deals?open=${lead.convertedDealId}`}
                        className="font-medium text-brand-700 hover:underline"
                      >
                        {lead.convertedDealName || 'View deal'}
                      </Link>
                    ) : (
                      'None'
                    )}
                  </dd>
                </div>
              </dl>
            </section>
          ) : null}

          <section>
            <h3 className="text-sm font-semibold text-ink">Move status</h3>
            {terminal ? (
              <p className="mt-2 text-sm text-muted">
                {lead.status} is a terminal status — no further transitions.
              </p>
            ) : (
              <div className="mt-2 flex flex-wrap gap-2">
                {transitions.map((status) => (
                  <button
                    key={status}
                    type="button"
                    disabled={statusPending}
                    onClick={() => onChangeStatus(lead, status)}
                    className={[
                      'rounded-lg px-3 py-1.5 text-sm font-medium disabled:opacity-60',
                      status === 'LOST'
                        ? 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                        : 'bg-brand-600 text-white hover:bg-brand-700',
                    ].join(' ')}
                  >
                    {statusPending ? 'Updating…' : `→ ${status}`}
                  </button>
                ))}
              </div>
            )}
            {statusError ? (
              <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {statusError}
              </div>
            ) : null}
          </section>

          <ActivityTimeline entityType="LEAD" entityId={lead.id} refreshKey={activityRefreshKey} />
        </div>

        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          <button
            type="button"
            onClick={() => onAddTask(lead)}
            disabled={statusPending}
            className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            + Task
          </button>
          <button
            type="button"
            onClick={() => onAddMeeting(lead)}
            disabled={statusPending}
            className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            + Meeting
          </button>
          <button
            type="button"
            onClick={() => onAddCall(lead)}
            disabled={statusPending}
            className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            + Call
          </button>
          <AskFlowAiButton entityType="LEAD" entityId={lead.id} label={lead.fullName} />
          <button
            type="button"
            onClick={() => onEdit(lead)}
            disabled={statusPending}
            className="flex-1 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => onDelete(lead)}
            disabled={statusPending}
            className="rounded-lg border border-red-200 bg-white px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"
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

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}
