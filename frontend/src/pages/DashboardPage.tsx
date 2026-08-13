import { useEffect, useState } from 'react'
import { AlertCircle, CheckSquare, CircleDollarSign, Clock3, Handshake, Trophy, Users } from 'lucide-react'
import { fetchDashboardSummary } from '../api/dashboard'
import { MetricCard } from '../components/MetricCard'
import { DealStageBadge, LeadStatusBadge } from '../components/StatusBadge'
import type { DashboardSummary } from '../types/dashboard'
import type { DealStage } from '../types/deal'
import type { LeadStatus } from '../types/lead'
import { DEAL_STAGE_ORDER } from '../utils/dealTransitions'
import { formatMoney } from '../utils/money'
import { formatApiError } from '../utils/errors'

const PIPELINE_ORDER: LeadStatus[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'LOST']

export function DashboardPage() {
  const [data, setData] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const summary = await fetchDashboardSummary()
        if (!cancelled) setData(summary)
      } catch (err) {
        if (!cancelled) setError(formatApiError(err, 'Failed to load dashboard'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  if (loading) {
    return <div className="text-sm text-muted">Loading dashboard…</div>
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        {error}
        <button
          type="button"
          onClick={() => setReloadKey((k) => k + 1)}
          className="ml-3 font-medium underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
        >
          Retry
        </button>
      </div>
    )
  }

  if (!data) {
    return <div className="text-sm text-muted">No dashboard data available.</div>
  }

  const totalPipeline = PIPELINE_ORDER.reduce(
    (sum, status) => sum + (data.leadsByStatus[status] ?? 0),
    0,
  )

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-base font-semibold text-ink">Overview</h2>
        <p className="mt-1 text-sm text-muted">
          Role-aware snapshot of your pipeline and follow-ups.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Total Leads"
          value={data.totalLeads}
          icon={<Users className="h-5 w-5" />}
        />
        <MetricCard
          label="Open Tasks"
          value={data.openTasks}
          icon={<CheckSquare className="h-5 w-5" />}
        />
        <MetricCard
          label="Overdue Tasks"
          value={data.overdueTasks}
          hint="OPEN tasks past due"
          icon={<AlertCircle className="h-5 w-5" />}
        />
        <MetricCard
          label="Upcoming Follow-ups"
          value={data.upcomingFollowUps}
          hint="Next 7 days"
          icon={<Clock3 className="h-5 w-5" />}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Open Deals"
          value={data.openDeals}
          icon={<Handshake className="h-5 w-5" />}
        />
        <MetricCard
          label="Pipeline Value"
          value={formatMoney(data.openPipelineValue)}
          hint="Open deal amounts"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
        <MetricCard
          label="Weighted Pipeline"
          value={formatMoney(data.weightedPipelineValue)}
          hint="Amount × probability"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
        <MetricCard
          label="Won Deals"
          value={data.wonDeals}
          hint={formatMoney(data.wonDealValue)}
          icon={<Trophy className="h-5 w-5" />}
        />
      </div>

      <section className="rounded-xl border border-border bg-surface p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-ink">Deal stages</h3>
            <p className="text-xs text-muted">Counts by opportunity stage</p>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
          {DEAL_STAGE_ORDER.map((stage: DealStage) => {
            const count = data.dealsByStage?.[stage] ?? 0
            return (
              <div key={stage} className="rounded-lg border border-border bg-canvas px-3 py-3">
                <DealStageBadge stage={stage} />
                <div className="mt-3 text-2xl font-semibold text-ink">{count}</div>
              </div>
            )
          })}
        </div>
      </section>

      <section className="rounded-xl border border-border bg-surface p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-ink">Lead pipeline</h3>
            <p className="text-xs text-muted">Lead counts by status</p>
          </div>
          <div className="text-xs text-muted">{totalPipeline} total in pipeline map</div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {PIPELINE_ORDER.map((status) => {
            const count = data.leadsByStatus[status] ?? 0
            return (
              <div
                key={status}
                className="rounded-lg border border-border bg-canvas px-3 py-3"
              >
                <LeadStatusBadge status={status} />
                <div className="mt-3 text-2xl font-semibold text-ink">{count}</div>
              </div>
            )
          })}
        </div>

        {data.totalLeads === 0 ? (
          <p className="mt-4 text-sm text-muted">
            No leads yet. Open Leads to add your first contact and start the pipeline.
          </p>
        ) : null}
      </section>
    </div>
  )
}
