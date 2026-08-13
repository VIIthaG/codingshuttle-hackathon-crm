import type { Deal } from '../../types/deal'
import { DEAL_STAGE_ORDER, formatDealStage } from '../../utils/dealTransitions'
import { DealCard } from './DealCard'

type DealPipelineProps = {
  deals: Deal[]
  stagePending?: boolean
  onOpenDeal: (deal: Deal) => void
  onAdvance: (deal: Deal, stage: Deal['stage']) => void
}

export function DealPipeline({ deals, stagePending, onOpenDeal, onAdvance }: DealPipelineProps) {
  return (
    <div className="flex gap-3 overflow-x-auto pb-2 [-ms-overflow-style:none] [scrollbar-width:thin]">
      {DEAL_STAGE_ORDER.map((stage) => {
        const column = deals.filter((deal) => deal.stage === stage)
        return (
          <section
            key={stage}
            className="flex w-[min(18rem,85vw)] shrink-0 flex-col rounded-xl border border-border bg-canvas sm:w-72"
          >
            <header className="flex items-center justify-between border-b border-border px-3 py-3">
              <h3 className="text-sm font-semibold text-ink">{formatDealStage(stage)}</h3>
              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-muted ring-1 ring-border">
                {column.length}
              </span>
            </header>
            <div className="flex flex-1 flex-col gap-2.5 p-2.5">
              {column.length === 0 ? (
                <div className="rounded-lg border border-dashed border-border px-3 py-8 text-center text-xs text-muted">
                  No deals
                </div>
              ) : (
                column.map((deal) => (
                  <DealCard
                    key={deal.id}
                    deal={deal}
                    onOpen={onOpenDeal}
                    onAdvance={onAdvance}
                    stagePending={stagePending}
                  />
                ))
              )}
            </div>
          </section>
        )
      })}
    </div>
  )
}
