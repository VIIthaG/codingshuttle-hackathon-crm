import type { Deal } from '../../types/deal'
import { allowedDealTransitions, formatDealStage } from '../../utils/dealTransitions'
import { formatMoney } from '../../utils/money'
import { DealStageBadge } from '../StatusBadge'

type DealCardProps = {
  deal: Deal
  onOpen: (deal: Deal) => void
  onAdvance?: (deal: Deal, stage: Deal['stage']) => void
  stagePending?: boolean
}

export function DealCard({ deal, onOpen, onAdvance, stagePending = false }: DealCardProps) {
  const next = allowedDealTransitions(deal.stage).filter((s) => s !== 'CLOSED_LOST')
  const canLose = allowedDealTransitions(deal.stage).includes('CLOSED_LOST')

  return (
    <div className="rounded-xl border border-border bg-surface p-3.5 shadow-sm">
      <button type="button" onClick={() => onOpen(deal)} className="w-full text-left">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-ink">{deal.name}</div>
            <div className="mt-0.5 truncate text-xs text-muted">{deal.accountName}</div>
          </div>
          <DealStageBadge stage={deal.stage} />
        </div>
        <div className="mt-3 space-y-1 text-xs text-muted">
          <div className="font-medium text-ink">{formatMoney(deal.amount, deal.currency)}</div>
          <div>{deal.probability}% probability</div>
          <div>{deal.expectedCloseDate ? `Close ${deal.expectedCloseDate}` : 'No close date'}</div>
          <div className="truncate text-muted">{deal.ownerName}</div>
        </div>
      </button>
      {onAdvance && (next.length > 0 || canLose) ? (
        <div className="mt-3 flex flex-wrap gap-1.5 border-t border-border pt-2">
          {next.map((stage) => (
            <button
              key={stage}
              type="button"
              disabled={stagePending}
              onClick={() => onAdvance(deal, stage)}
              className="btn btn-primary btn-sm"
            >
              → {formatDealStage(stage)}
            </button>
          ))}
          {canLose ? (
            <button
              type="button"
              disabled={stagePending}
              onClick={() => onAdvance(deal, 'CLOSED_LOST')}
              className="btn btn-danger btn-sm"
            >
              Lost
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
