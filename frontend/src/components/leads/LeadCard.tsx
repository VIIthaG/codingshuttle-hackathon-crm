import type { Lead } from '../../types/lead'
import { LeadSourceBadge, LeadStatusBadge } from '../StatusBadge'

type LeadCardProps = {
  lead: Lead
  onOpen: (lead: Lead) => void
}

export function LeadCard({ lead, onOpen }: LeadCardProps) {
  return (
    <button
      type="button"
      onClick={() => onOpen(lead)}
      className="w-full rounded-xl border border-border bg-white p-3.5 text-left shadow-sm transition hover:border-brand-100 hover:shadow"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold text-ink">{lead.fullName}</div>
          <div className="mt-0.5 truncate text-xs text-muted">
            {lead.company?.trim() || 'No company'}
          </div>
        </div>
        <LeadStatusBadge status={lead.status} />
      </div>
      <div className="mt-3 space-y-1 text-xs text-slate-600">
        <div className="truncate">{lead.email?.trim() || 'No email'}</div>
        <div className="flex flex-wrap items-center gap-2">
          <LeadSourceBadge source={lead.source} />
          <span className="truncate text-muted">→ {lead.assignedToName}</span>
        </div>
      </div>
    </button>
  )
}
