import { ActivityTimeline } from '../activity/ActivityTimeline'
import { useState } from 'react'
import type { Deal, DealStage } from '../../types/deal'
import { allowedDealTransitions, formatDealStage, isTerminalDealStage } from '../../utils/dealTransitions'
import { formatMoney } from '../../utils/money'
import { DealStageBadge } from '../StatusBadge'

type DealDetailsProps = {
  open: boolean
  deal: Deal | null
  stagePending?: boolean
  stageError?: string | null
  onClose: () => void
  onEdit: (deal: Deal) => void
  onDelete: (deal: Deal) => void
  onChangeStage: (deal: Deal, stage: DealStage, lostReason?: string | null) => void
  onAddTask?: (deal: Deal) => void
  onAddMeeting?: (deal: Deal) => void
  onAddCall?: (deal: Deal) => void
  activityRefreshKey?: number
}

export function DealDetails({
  open,
  deal,
  stagePending = false,
  stageError = null,
  onClose,
  onEdit,
  onDelete,
  onChangeStage,
  onAddTask,
  onAddMeeting,
  onAddCall,
  activityRefreshKey = 0,
}: DealDetailsProps) {
  const [lostReason, setLostReason] = useState('')
  if (!open || !deal) return null

  const transitions = allowedDealTransitions(deal.stage)
  const terminal = isTerminalDealStage(deal.stage)

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/30">
      <button type="button" aria-label="Close deal details" className="flex-1 cursor-default" onClick={onClose} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="deal-details-title"
        className="flex h-full w-full max-w-md flex-col border-l border-border bg-surface shadow-xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="deal-details-title" className="truncate text-lg font-semibold text-ink">
              {deal.name}
            </h2>
            <p className="mt-0.5 text-sm text-muted">{deal.accountName}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-slate-100">
            Close
          </button>
        </header>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <DealStageBadge stage={deal.stage} />

          <dl className="space-y-3 text-sm">
            <Row label="Account" value={deal.accountName} />
            <Row label="Contact" value={deal.primaryContactName || '—'} />
            <Row label="Owner" value={deal.ownerName} />
            <Row label="Amount" value={formatMoney(deal.amount, deal.currency)} />
            <Row label="Probability" value={`${deal.probability}%`} />
            <Row label="Expected close" value={deal.expectedCloseDate || '—'} />
            <Row label="Description" value={deal.description || '—'} />
            {deal.stage === 'CLOSED_LOST' ? <Row label="Lost reason" value={deal.lostReason || '—'} /> : null}
            <Row label="Created" value={formatDate(deal.createdAt)} />
            <Row label="Updated" value={formatDate(deal.updatedAt)} />
          </dl>

          <section>
            <h3 className="text-sm font-semibold text-ink">Move stage</h3>
            {terminal ? (
              <p className="mt-2 text-sm text-muted">
                {formatDealStage(deal.stage)} is terminal — no further transitions.
              </p>
            ) : (
              <>
                {transitions.includes('CLOSED_LOST') ? (
                  <label className="mt-2 block">
                    <span className="mb-1 block text-xs font-medium text-muted">Lost reason (optional)</span>
                    <input
                      value={lostReason}
                      onChange={(e) => setLostReason(e.target.value)}
                      disabled={stagePending}
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
                    />
                  </label>
                ) : null}
                <div className="mt-2 flex flex-wrap gap-2">
                  {transitions.map((stage) => (
                    <button
                      key={stage}
                      type="button"
                      disabled={stagePending}
                      onClick={() =>
                        onChangeStage(deal, stage, stage === 'CLOSED_LOST' ? lostReason || null : null)
                      }
                      className={[
                        'rounded-lg px-3 py-1.5 text-sm font-medium disabled:opacity-60',
                        stage === 'CLOSED_LOST'
                          ? 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                          : 'bg-brand-600 text-white hover:bg-brand-700',
                      ].join(' ')}
                    >
                      {stagePending ? 'Updating…' : `→ ${formatDealStage(stage)}`}
                    </button>
                  ))}
                </div>
              </>
            )}
            {stageError ? (
              <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {stageError}
              </div>
            ) : null}
          </section>

          <ActivityTimeline entityType="DEAL" entityId={deal.id} refreshKey={activityRefreshKey} />
        </div>

        <footer className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {onAddTask ? (
            <button type="button" onClick={() => onAddTask(deal)} disabled={stagePending} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
              + Task
            </button>
          ) : null}
          {onAddMeeting ? (
            <button type="button" onClick={() => onAddMeeting(deal)} disabled={stagePending} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
              + Meeting
            </button>
          ) : null}
          {onAddCall ? (
            <button type="button" onClick={() => onAddCall(deal)} disabled={stagePending} className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
              + Call
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => onEdit(deal)}
            disabled={stagePending}
            className="flex-1 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => onDelete(deal)}
            disabled={stagePending}
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
    <div className="grid grid-cols-[7.5rem_1fr] gap-2">
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
