import type { ReactNode } from 'react'

type MetricCardProps = {
  label: string
  value: number | string
  hint?: string
  icon?: ReactNode
}

export function MetricCard({ label, value, hint, icon }: MetricCardProps) {
  return (
    <div className="rounded-xl border border-border bg-surface p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-muted">{label}</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink">{value}</p>
          {hint ? <p className="mt-1 text-xs text-muted">{hint}</p> : null}
        </div>
        {icon ? (
          <div className="rounded-lg bg-brand-50 p-2 text-brand-600 dark:text-blue-300">{icon}</div>
        ) : null}
      </div>
    </div>
  )
}
