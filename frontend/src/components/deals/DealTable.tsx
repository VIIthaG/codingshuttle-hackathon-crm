import type { Deal } from '../../types/deal'
import { formatMoney } from '../../utils/money'
import { DealStageBadge } from '../StatusBadge'

type DealTableProps = {
  deals: Deal[]
  onOpenDeal: (deal: Deal) => void
  onDeleteDeal: (deal: Deal) => void
}

export function DealTable({ deals, onOpenDeal, onDeleteDeal }: DealTableProps) {
  if (deals.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border bg-surface px-4 py-12 text-center text-sm text-muted">
        No deals match these filters.
      </div>
    )
  }

  return (
    <>
      <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface shadow-sm lg:block">
        <table className="min-w-full divide-y divide-border text-left text-sm">
          <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-4 py-3 font-semibold">Deal</th>
              <th className="px-4 py-3 font-semibold">Account</th>
              <th className="px-4 py-3 font-semibold">Contact</th>
              <th className="px-4 py-3 font-semibold">Stage</th>
              <th className="px-4 py-3 font-semibold">Amount</th>
              <th className="px-4 py-3 font-semibold">Probability</th>
              <th className="px-4 py-3 font-semibold">Expected close</th>
              <th className="px-4 py-3 font-semibold">Owner</th>
              <th className="px-4 py-3 font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {deals.map((deal) => (
              <tr key={deal.id} className="hover:bg-slate-50/80">
                <td className="px-4 py-3 font-medium text-ink">{deal.name}</td>
                <td className="px-4 py-3 text-slate-600">{deal.accountName}</td>
                <td className="px-4 py-3 text-slate-600">{deal.primaryContactName || '—'}</td>
                <td className="px-4 py-3">
                  <DealStageBadge stage={deal.stage} />
                </td>
                <td className="px-4 py-3 text-slate-600">{formatMoney(deal.amount, deal.currency)}</td>
                <td className="px-4 py-3 text-slate-600">{deal.probability}%</td>
                <td className="px-4 py-3 text-slate-600">{deal.expectedCloseDate || '—'}</td>
                <td className="px-4 py-3 text-slate-600">{deal.ownerName}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => onOpenDeal(deal)}
                      className="text-sm font-medium text-brand-600 hover:text-brand-700"
                    >
                      Open
                    </button>
                    <button
                      type="button"
                      onClick={() => onDeleteDeal(deal)}
                      className="text-sm font-medium text-red-600 hover:text-red-700"
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

      <div className="grid gap-3 lg:hidden">
        {deals.map((deal) => (
          <article key={deal.id} className="rounded-xl border border-border bg-surface p-4 shadow-sm">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h3 className="font-semibold text-ink">{deal.name}</h3>
                <p className="text-sm text-muted">{deal.accountName}</p>
              </div>
              <DealStageBadge stage={deal.stage} />
            </div>
            <dl className="mt-3 space-y-1 text-sm text-slate-600">
              <div>{deal.primaryContactName || 'No contact'}</div>
              <div>
                {formatMoney(deal.amount, deal.currency)} · {deal.probability}%
              </div>
              <div>{deal.expectedCloseDate || 'No close date'}</div>
              <div className="text-muted">{deal.ownerName}</div>
            </dl>
            <div className="mt-4 flex gap-3">
              <button type="button" onClick={() => onOpenDeal(deal)} className="text-sm font-medium text-brand-600">
                Open
              </button>
              <button type="button" onClick={() => onDeleteDeal(deal)} className="text-sm font-medium text-red-600">
                Delete
              </button>
            </div>
          </article>
        ))}
      </div>
    </>
  )
}
