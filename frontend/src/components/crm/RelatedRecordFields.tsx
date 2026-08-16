import { useEffect, useState } from 'react'
import { listAllAccounts } from '../../api/accounts'
import { listAllContacts } from '../../api/contacts'
import { listAllDeals } from '../../api/deals'
import { listAllLeads } from '../../api/leads'
import type { RelatedRecordType } from '../../types/task'
import { RELATED_RECORD_TYPES, relatedTypeLabel } from '../../types/task'

export function RelatedRecordFields({
  relatedType,
  relatedId,
  disabled,
  error,
  onTypeChange,
  onIdChange,
}: {
  relatedType: RelatedRecordType
  relatedId: string
  disabled?: boolean
  error?: string
  onTypeChange: (type: RelatedRecordType) => void
  onIdChange: (id: string) => void
}) {
  const [options, setOptions] = useState<{ id: string; label: string }[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    void Promise.all([listAllLeads(), listAllAccounts(), listAllContacts(), listAllDeals()])
      .then(([leads, accounts, contacts, deals]) => {
        if (cancelled) return
        const map: Record<RelatedRecordType, { id: string; label: string }[]> = {
          LEAD: leads.map((l) => ({
            id: l.id,
            label: l.company ? `${l.fullName} — ${l.company}` : l.fullName,
          })),
          ACCOUNT: accounts.map((a) => ({ id: a.id, label: a.name })),
          CONTACT: contacts.map((c) => ({
            id: c.id,
            label: `${c.firstName} ${c.lastName}${c.accountName ? ` — ${c.accountName}` : ''}`,
          })),
          DEAL: deals.map((d) => ({
            id: d.id,
            label: d.accountName ? `${d.name} — ${d.accountName}` : d.name,
          })),
        }
        setOptions(map[relatedType])
      })
      .catch(() => {
        if (!cancelled) setOptions([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [relatedType])

  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-ink">
        Related to<span className="text-red-500"> *</span>
      </span>
      <div className="grid gap-2 sm:grid-cols-2">
        <select
          value={relatedType}
          onChange={(e) => onTypeChange(e.target.value as RelatedRecordType)}
          disabled={disabled || loading}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        >
          {RELATED_RECORD_TYPES.map((type) => (
            <option key={type} value={type}>
              {relatedTypeLabel(type)}
            </option>
          ))}
        </select>
        <select
          value={relatedId}
          onChange={(e) => onIdChange(e.target.value)}
          disabled={disabled || loading}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        >
          <option value="">{loading ? 'Loading…' : 'Select record'}</option>
          {options.map((opt) => (
            <option key={opt.id} value={opt.id}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>
      {error ? <span className="mt-1 block text-xs text-[color:var(--app-danger-text)]">{error}</span> : null}
    </div>
  )
}
