import { AlertCircle, CheckCircle2, Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { initialsFromName } from '../../utils/initials'

export function Alert({
  tone = 'error',
  children,
}: {
  tone?: 'error' | 'success' | 'warning'
  children: ReactNode
}) {
  const Icon = tone === 'success' ? CheckCircle2 : AlertCircle
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <div className="mb-3 rounded-full border border-border bg-canvas p-2.5 text-muted">
        {icon ?? <Inbox className="h-5 w-5" aria-hidden />}
      </div>
      <p className="text-sm font-semibold text-ink">{title}</p>
      {description ? <p className="mt-1 max-w-sm text-sm text-muted">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const dim = size === 'lg' ? 'h-10 w-10 text-sm' : size === 'sm' ? 'h-7 w-7 text-[10px]' : 'h-9 w-9 text-xs'
  return (
    <span
      className={`inline-flex ${dim} shrink-0 items-center justify-center rounded-full bg-brand-600 font-semibold text-white`}
      aria-hidden
    >
      {initialsFromName(name)}
    </span>
  )
}
