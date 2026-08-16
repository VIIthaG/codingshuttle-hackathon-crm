import type { Lead } from '../../types/lead'
import { LeadSourceBadge, LeadStatusBadge } from '../StatusBadge'

type LeadTableProps = {
  leads: Lead[]
  onOpenLead: (lead: Lead) => void
  onDeleteLead: (lead: Lead) => void
  onConvertLead: (lead: Lead) => void
}

export function LeadTable({ leads, onOpenLead, onDeleteLead, onConvertLead }: LeadTableProps) {
  if (leads.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border bg-surface px-4 py-12 text-center text-sm text-muted">
        No leads yet. Add your first lead to start the pipeline.
      </div>
    )
  }

  return (
    <>
      {/* Desktop table */}
      <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface shadow-sm lg:block">
        <table className="min-w-full divide-y divide-border text-left text-sm">
          <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-4 py-3 font-semibold">Name</th>
              <th className="px-4 py-3 font-semibold">Company</th>
              <th className="px-4 py-3 font-semibold">Email</th>
              <th className="px-4 py-3 font-semibold">Phone</th>
              <th className="px-4 py-3 font-semibold">Source</th>
              <th className="px-4 py-3 font-semibold">Status</th>
              <th className="px-4 py-3 font-semibold">Assigned To</th>
              <th className="px-4 py-3 font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {leads.map((lead) => (
              <tr key={lead.id} className="hover:bg-canvas/80">
                <td className="px-4 py-3 font-medium text-ink">{lead.fullName}</td>
                <td className="px-4 py-3 text-muted">{lead.company || '—'}</td>
                <td className="px-4 py-3 text-muted">{lead.email || '—'}</td>
                <td className="px-4 py-3 text-muted">{lead.phone || '—'}</td>
                <td className="px-4 py-3">
                  <LeadSourceBadge source={lead.source} />
                </td>
                <td className="px-4 py-3">
                  <LeadStatusBadge status={lead.status} />
                </td>
                <td className="px-4 py-3 text-muted">{lead.assignedToName}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => onOpenLead(lead)}
                      className="text-sm font-medium text-brand-600 hover:text-brand-700"
                    >
                      Open
                    </button>
                    {lead.status === 'QUALIFIED' ? (
                      <button
                        type="button"
                        onClick={() => onConvertLead(lead)}
                        className="text-sm font-medium text-brand-600 hover:text-brand-700"
                      >
                        Convert
                      </button>
                    ) : null}
                    <button
                      type="button"
                      onClick={() => onDeleteLead(lead)}
                      className="text-sm font-medium text-[color:var(--app-danger-text)] hover:text-[color:var(--app-danger-text)]"
                    >
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile cards */}
      <div className="grid gap-3 lg:hidden">
        {leads.map((lead) => (
          <article
            key={lead.id}
            className="rounded-xl border border-border bg-surface p-4 shadow-sm"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <h3 className="font-semibold text-ink">{lead.fullName}</h3>
                <p className="text-sm text-muted">{lead.company || 'No company'}</p>
              </div>
              <LeadStatusBadge status={lead.status} />
            </div>
            <dl className="mt-3 space-y-1 text-sm text-muted">
              <div>{lead.email || 'No email'}</div>
              <div>{lead.phone || 'No phone'}</div>
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <LeadSourceBadge source={lead.source} />
                <span className="text-muted">{lead.assignedToName}</span>
              </div>
            </dl>
            <div className="mt-4 flex gap-3">
              <button
                type="button"
                onClick={() => onOpenLead(lead)}
                className="text-sm font-medium text-brand-600"
              >
                Open
              </button>
              {lead.status === 'QUALIFIED' ? (
                <button
                  type="button"
                  onClick={() => onConvertLead(lead)}
                  className="text-sm font-medium text-brand-600"
                >
                  Convert
                </button>
              ) : null}
              <button
                type="button"
                onClick={() => onDeleteLead(lead)}
                className="text-sm font-medium text-[color:var(--app-danger-text)]"
              >
                Delete
              </button>
            </div>
          </article>
        ))}
      </div>
    </>
  )
}
