import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { AlertCircle, CircleDollarSign, Percent, Trophy, UserPlus, Users } from 'lucide-react'
import { fetchAnalyticsSummary } from '../api/analytics'
import { listUsers } from '../api/users'
import { useAuth } from '../auth/useAuth'
import { ChartCard } from '../components/analytics/ChartCard'
import { MetricCard } from '../components/MetricCard'
import type { AnalyticsRangePreset, AnalyticsSummary } from '../types/analytics'
import type { User } from '../types/auth'
import { DEAL_STAGE_LABELS, DEAL_STAGE_ORDER } from '../utils/dealTransitions'
import { formatApiError } from '../utils/errors'
import { LEAD_STATUS_ORDER } from '../utils/leadTransitions'
import { formatMoney } from '../utils/money'
import { asNumber, formatPercent } from '../utils/percent'
import { useChartTheme } from '../components/ui/useChartTheme'

const PRESETS: { id: AnalyticsRangePreset; label: string }[] = [
  { id: '7d', label: 'Last 7 days' },
  { id: '30d', label: 'Last 30 days' },
  { id: '90d', label: 'Last 90 days' },
  { id: 'all', label: 'All time' },
]

const LEAD_LABELS: Record<string, string> = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  QUALIFIED: 'Qualified',
  CONVERTED: 'Converted',
  LOST: 'Lost',
}

function parseRange(value: string | null): AnalyticsRangePreset {
  if (value === '7d' || value === '90d' || value === 'all' || value === '30d') return value
  return '30d'
}

export function AnalyticsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [searchParams, setSearchParams] = useSearchParams()
  const range = parseRange(searchParams.get('range'))
  const assignedTo = isAdmin ? searchParams.get('assignedTo') ?? '' : ''
  const [data, setData] = useState<AnalyticsSummary | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const chart = useChartTheme()

  useEffect(() => {
    if (!isAdmin) return
    void listUsers()
      .then(setUsers)
      .catch(() => setUsers([]))
  }, [isAdmin])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const summary = await fetchAnalyticsSummary({
          range,
          assignedTo: assignedTo || undefined,
        })
        if (!cancelled) setData(summary)
      } catch (err) {
        if (!cancelled) setError(formatApiError(err, 'Failed to load analytics'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [range, assignedTo, reloadKey])

  const leadFunnel = useMemo(() => {
    const counts = new Map((data?.leads.byStatus ?? []).map((row) => [row.status, row.count]))
    return LEAD_STATUS_ORDER.map((status) => ({
      name: LEAD_LABELS[status] ?? status,
      count: counts.get(status) ?? 0,
    }))
  }, [data])

  const dealCounts = useMemo(() => {
    const byStage = new Map((data?.deals.byStage ?? []).map((row) => [row.stage, row]))
    return DEAL_STAGE_ORDER.map((stage) => ({
      name: DEAL_STAGE_LABELS[stage],
      count: byStage.get(stage)?.count ?? 0,
      value: asNumber(byStage.get(stage)?.totalAmount),
    }))
  }, [data])

  function setRange(next: AnalyticsRangePreset) {
    const nextParams = new URLSearchParams(searchParams)
    nextParams.set('range', next)
    setSearchParams(nextParams)
  }

  function setAssigned(next: string) {
    const nextParams = new URLSearchParams(searchParams)
    if (next) nextParams.set('assignedTo', next)
    else nextParams.delete('assignedTo')
    setSearchParams(nextParams)
  }

  if (loading && !data) {
    return (
      <div className="space-y-4">
        <div className="skeleton h-8 w-48" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="skeleton h-28" />
          ))}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="alert alert-error">
        {error}
        <button type="button" onClick={() => setReloadKey((k) => k + 1)} className="ml-3 font-medium underline">
          Retry
        </button>
      </div>
    )
  }

  if (!data) return <p className="text-sm text-muted">No analytics data available.</p>

  const leadCreatedSum = data.trends.leads.reduce((sum, row) => sum + row.count, 0)
  const dealValueSum = dealCounts.reduce((sum, row) => sum + row.value, 0)
  const activityCreated =
    data.activities.tasks.created + data.activities.meetings.created + data.activities.calls.created

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink">Analytics</h2>
          <p className="mt-1 text-sm text-muted">
            UTC windows use from inclusive and to exclusive. Pipeline values are the current snapshot, not reconstructed
            stage history.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.id}
              type="button"
              onClick={() => setRange(preset.id)}
              className={[
                'rounded-lg border px-3 py-1.5 text-sm',
                range === preset.id
                  ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700'
                  : 'border-border text-muted hover:bg-canvas',
              ].join(' ')}
            >
              {preset.label}
            </button>
          ))}
          {isAdmin ? (
            <select
              value={assignedTo}
              onChange={(e) => setAssigned(e.target.value)}
              className="ui-input w-auto min-w-[12rem]"
            >
              <option value="">All team members</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <MetricCard label="Leads created" value={data.leads.created} hint="In selected range" icon={<UserPlus className="h-5 w-5" />} />
        <MetricCard
          label="Conversion rate"
          value={formatPercent(data.leads.conversionRate)}
          hint="Converted / (converted + lost)"
          icon={<Percent className="h-5 w-5" />}
        />
        <MetricCard
          label="Open pipeline"
          value={formatMoney(data.deals.openPipelineValue)}
          hint="Current open deals"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
        <MetricCard
          label="Weighted pipeline"
          value={formatMoney(data.deals.weightedPipelineValue)}
          hint="Amount × probability"
          icon={<CircleDollarSign className="h-5 w-5" />}
        />
        <MetricCard
          label="Won value"
          value={formatMoney(data.deals.wonValue)}
          hint={`${data.deals.wonCount} closed won`}
          icon={<Trophy className="h-5 w-5" />}
        />
        <MetricCard
          label="Overdue tasks"
          value={data.activities.tasks.overdueNow}
          hint="Open and past due now"
          icon={<AlertCircle className="h-5 w-5" />}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard title="Leads over time" hint="Created in range" empty={leadCreatedSum === 0 ? 'No lead data in this period' : null}>
          <ResponsiveContainer>
            <AreaChart data={data.trends.leads}>
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="period" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Area type="monotone" dataKey="count" name="Leads" stroke={chart.brand} fill={chart.brandSoft} />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard
          title="Lead status mix"
          hint="Current status of leads created in range"
          empty={data.leads.created === 0 ? 'No lead data in this period' : null}
        >
          <ResponsiveContainer>
            <BarChart data={leadFunnel}>
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="Leads" fill={chart.brand} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Deal pipeline by stage" hint="Current snapshot" empty={data.deals.total === 0 ? 'No deal data in this period' : null}>
          <ResponsiveContainer>
            <BarChart data={dealCounts}>
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} height={60} textAnchor="end" />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="Deals" fill={chart.brand} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Deal value by stage" hint="USD amounts, no conversion" empty={dealValueSum === 0 ? 'No deal data in this period' : null}>
          <ResponsiveContainer>
            <BarChart data={dealCounts}>
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} height={60} textAnchor="end" />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip formatter={(value) => formatMoney(typeof value === 'number' ? value : Number(value))} />
              <Bar dataKey="value" name="Value" fill={chart.success} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title="Activity mix" hint="Created in range, current status" empty={activityCreated === 0 ? 'No activities recorded' : null}>
          <ResponsiveContainer>
            <BarChart
              data={[
                { name: 'Tasks', count: data.activities.tasks.created },
                { name: 'Meetings', count: data.activities.meetings.created },
                { name: 'Calls', count: data.activities.calls.created },
              ]}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="Created" fill={chart.warning} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard
          title="Activity trend"
          hint="Created over time"
          empty={activityCreated === 0 ? 'No activities recorded' : null}
        >
          <ResponsiveContainer>
            <LineChart data={data.trends.activities}>
              <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
              <XAxis dataKey="period" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="tasks" name="Tasks" stroke={chart.brand} dot={false} />
              <Line type="monotone" dataKey="meetings" name="Meetings" stroke={chart.success} dot={false} />
              <Line type="monotone" dataKey="calls" name="Calls" stroke={chart.warning} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      {isAdmin ? (
        <section className="panel p-5">
          <div className="mb-4 flex items-center gap-2">
            <Users className="h-4 w-4 text-brand-600" />
            <div>
              <h3 className="text-sm font-semibold text-ink">Team workload</h3>
              <p className="text-xs text-muted">Current pipeline and open work. Not a performance score.</p>
            </div>
          </div>
          {data.team.length === 0 ? (
            <p className="text-sm text-muted">No team members to show.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead className="text-xs uppercase text-muted">
                  <tr>
                    <th className="px-2 py-2">Name</th>
                    <th className="px-2 py-2">Open deals</th>
                    <th className="px-2 py-2">Open pipeline</th>
                    <th className="px-2 py-2">Won</th>
                    <th className="px-2 py-2">Open tasks</th>
                    <th className="px-2 py-2">Overdue</th>
                    <th className="px-2 py-2">Meetings</th>
                    <th className="px-2 py-2">Calls</th>
                  </tr>
                </thead>
                <tbody>
                  {data.team.map((row) => (
                    <tr key={row.userId} className="border-t border-border">
                      <td className="px-2 py-2 font-medium text-ink">{row.displayName}</td>
                      <td className="px-2 py-2">{row.openDeals}</td>
                      <td className="px-2 py-2">{formatMoney(row.openPipelineValue)}</td>
                      <td className="px-2 py-2">
                        {row.wonDeals} · {formatMoney(row.wonValue)}
                      </td>
                      <td className="px-2 py-2">{row.openTasks}</td>
                      <td className="px-2 py-2">{row.overdueTasks}</td>
                      <td className="px-2 py-2">{row.scheduledMeetings}</td>
                      <td className="px-2 py-2">{row.plannedCalls}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  )
}
