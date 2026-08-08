import type { Lead, LeadStatus } from '../../types/lead'
import { LEAD_STATUS_ORDER } from '../../utils/leadTransitions'
import { LeadCard } from './LeadCard'

type LeadPipelineProps = {
  leads: Lead[]
  onOpenLead: (lead: Lead) => void
}

export function LeadPipeline({ leads, onOpenLead }: LeadPipelineProps) {
  const byStatus = LEAD_STATUS_ORDER.reduce(
    (acc, status) => {
      acc[status] = leads.filter((lead) => lead.status === status)
      return acc
    },
    {} as Record<LeadStatus, Lead[]>,
  )

  return (
    <div className="flex gap-4 overflow-x-auto pb-2">
      {LEAD_STATUS_ORDER.map((status) => {
        const columnLeads = byStatus[status]
        return (
          <section
            key={status}
            className="flex w-72 shrink-0 flex-col rounded-xl border border-border bg-canvas"
          >
            <header className="flex items-center justify-between border-b border-border px-3 py-3">
              <h3 className="text-sm font-semibold text-ink">{status}</h3>
              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-muted ring-1 ring-border">
                {columnLeads.length}
              </span>
            </header>
            <div className="flex flex-1 flex-col gap-2.5 p-2.5">
              {columnLeads.length === 0 ? (
                <div className="rounded-lg border border-dashed border-border px-3 py-8 text-center text-xs text-muted">
                  No leads
                </div>
              ) : (
                columnLeads.map((lead) => (
                  <LeadCard key={lead.id} lead={lead} onOpen={onOpenLead} />
                ))
              )}
            </div>
          </section>
        )
      })}
    </div>
  )
}
