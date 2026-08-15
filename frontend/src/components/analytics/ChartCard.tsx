import type { ReactNode } from 'react'

export function ChartCard({
  title,
  hint,
  empty,
  children,
}: {
  title: string
  hint?: string
  empty?: string | null
  children: ReactNode
}) {
  return (
    <section className="rounded-xl border border-border bg-surface p-5 shadow-sm">
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-ink">{title}</h3>
        {hint ? <p className="text-xs text-muted">{hint}</p> : null}
      </div>
      {empty ? (
        <p className="py-10 text-center text-sm text-muted">{empty}</p>
      ) : (
        <div className="h-64 w-full">{children}</div>
      )}
    </section>
  )
}
