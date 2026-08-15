import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { AlertCircle, CheckSquare, CircleDollarSign, Clock3, Handshake, Trophy, Users } from 'lucide-react'
import { fetchDashboardSummary } from '../api/dashboard'
import { getWorkqueue } from '../api/schedule'
import { useAuth } from '../auth/useAuth'
import { ChartCard } from '../components/analytics/ChartCard'
import { MetricCard } from '../components/MetricCard'
import type { DashboardSummary } from '../types/dashboard'
import type { WorkqueueItem, WorkqueueResponse } from '../types/schedule'
import { DEAL_STAGE_LABELS, DEAL_STAGE_ORDER } from '../utils/dealTransitions'
import { formatApiError } from '../utils/errors'
import { LEAD_STATUS_ORDER } from '../utils/leadTransitions'
import { formatMoney } from '../utils/money'
import { formatDateTime } from '../utils/taskDates'
import { relatedTypeLabel } from '../types/task'

const LEAD_LABELS: Record<string, string> = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  QUALIFIED: 'Qualified',
  CONVERTED: 'Converted',
  LOST: 'Lost',
}

export function DashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState<DashboardSummary | null>(null)
  const [queue, setQueue] = useState<WorkqueueResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [summary, workqueue] = await Promise.all([fetchDashboardSummary(), getWorkqueue()])
        if (!cancelled) {
          setData(summary)
          setQueue(workqueue)
        }
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

  const leadChart = useMemo(
    () =>
      LEAD_STATUS_ORDER.map((status) => ({
        name: LEAD_LABELS[status],
        count: data?.leadsByStatus[status] ?? 0,
      })),
    [data],
  )
  const dealChart = useMemo(
    () =>
      DEAL_STAGE_ORDER.map((stage) => ({
        name: DEAL_STAGE_LABELS[stage],
        count: data?.dealsByStage?.[stage] ?? 0,
      })),
    [data],
  )

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-64 animate-pulse rounded-lg bg-slate-200" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-slate-200" />
          ))}
        </div>
      </div>
    )
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

  const upcoming = queue
    ? [
        ...queue.overdueTasks,
        ...queue.dueTodayTasks,
        ...queue.todayMeetings,
        ...queue.todayCalls,
        ...queue.upcomingTasks,
        ...queue.upcomingMeetings,
        ...queue.upcomingCalls,
      ].slice(0, 8)
    : []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-ink">Welcome{user?.fullName ? `, ${user.fullName}` : ''}</h2>
          <p className="mt-1 text-sm text-muted">Role-aware snapshot of pipeline, follow-ups, and upcoming work.</p>
        </div>
        <Link to="/analytics?range=30d" className="text-sm font-medium text-brand-700 hover:underline">
          Open analytics
        </Link>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Leads" value={data.totalLeads} icon={<Users className="h-5 w-5" />} />
        <MetricCard
          label="Open pipeline"
          value={formatMoney(data.openPipelineValue)}
          hint="Open deal amounts"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
        <MetricCard
          label="Won value"
          value={formatMoney(data.wonDealValue)}
          hint={`${data.wonDeals} closed won`}
          icon={<Trophy className="h-5 w-5" />}
        />
        <MetricCard label="Open deals" value={data.openDeals} icon={<Handshake className="h-5 w-5" />} />
        <MetricCard label="Open tasks" value={data.openTasks} icon={<CheckSquare className="h-5 w-5" />} />
        <MetricCard
          label="Overdue tasks"
          value={data.overdueTasks}
          hint="OPEN tasks past due"
          icon={<AlertCircle className="h-5 w-5" />}
        />
        <MetricCard
          label="Upcoming follow-ups"
          value={data.upcomingFollowUps}
          hint="Next 7 days"
          icon={<Clock3 className="h-5 w-5" />}
        />
        <MetricCard
          label="Weighted pipeline"
          value={formatMoney(data.weightedPipelineValue)}
          hint="Amount × probability"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard title="Lead status" empty={data.totalLeads === 0 ? 'No leads yet' : null}>
          <ResponsiveContainer>
            <BarChart data={leadChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="Leads" fill="#2563eb" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Deal pipeline" empty={data.openDeals + data.wonDeals === 0 && dealChart.every((r) => r.count === 0) ? 'No deals yet' : null}>
          <ResponsiveContainer>
            <BarChart data={dealChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} height={60} textAnchor="end" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="Deals" fill="#059669" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      <section className="rounded-xl border border-border bg-surface p-5 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-ink">Upcoming work</h3>
            <p className="text-xs text-muted">From the workqueue — overdue first, then today and upcoming.</p>
          </div>
          <Link to="/workqueue" className="text-sm font-medium text-brand-700 hover:underline">
            Workqueue
          </Link>
        </div>
        {upcoming.length === 0 ? (
          <p className="text-sm text-muted">Nothing due right now.</p>
        ) : (
          <ul className="divide-y divide-border">
            {upcoming.map((item) => (
              <UpcomingRow key={`${item.itemType}-${item.id}`} item={item} />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function UpcomingRow({ item }: { item: WorkqueueItem }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
      <div>
        <div className="font-medium text-ink">{item.title}</div>
        <div className="text-xs text-muted">
          {item.itemType} · {item.urgency} · {relatedTypeLabel(item.relatedType)} · {item.relatedName}
        </div>
      </div>
      <div className="text-xs text-muted">{formatDateTime(item.timestamp)}</div>
    </li>
  )
}
