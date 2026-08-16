import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { listContacts } from '../../api/contacts'
import type { Account } from '../../types/account'
import type { User } from '../../types/auth'
import type { Contact } from '../../types/contact'
import type { Deal, DealCreateRequest, DealUpdateRequest } from '../../types/deal'
import { ApiError } from '../../types/api'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatApiError } from '../../utils/errors'

type DealFormProps = {
  open: boolean
  mode: 'create' | 'edit'
  deal?: Deal | null
  accounts: Account[]
  users: User[]
  isAdmin: boolean
  pending?: boolean
  onClose: () => void
  onCreate: (body: DealCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: DealUpdateRequest) => Promise<void>
}

type FormState = {
  name: string
  accountId: string
  primaryContactId: string
  ownerId: string
  amount: string
  currency: string
  expectedCloseDate: string
  description: string
}

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'CAD']

function optionalTrim(value: string): string | null {
  const t = value.trim()
  return t === '' ? null : t
}

export function DealForm({
  open,
  mode,
  deal,
  accounts,
  users,
  isAdmin,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: DealFormProps) {
  const [form, setForm] = useState<FormState>({
    name: '',
    accountId: '',
    primaryContactId: '',
    ownerId: '',
    amount: '',
    currency: 'USD',
    expectedCloseDate: '',
    description: '',
  })
  const [contacts, setContacts] = useState<Contact[]>([])
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && deal) {
      setForm({
        name: deal.name,
        accountId: deal.accountId,
        primaryContactId: deal.primaryContactId ?? '',
        ownerId: deal.ownerId,
        amount: deal.amount == null ? '' : String(deal.amount),
        currency: deal.currency || 'USD',
        expectedCloseDate: deal.expectedCloseDate ?? '',
        description: deal.description ?? '',
      })
      idempotencyKeyRef.current = null
    } else {
      setForm({
        name: '',
        accountId: '',
        primaryContactId: '',
        ownerId: '',
        amount: '',
        currency: 'USD',
        expectedCloseDate: '',
        description: '',
      })
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, deal])

  useEffect(() => {
    if (!open || !form.accountId) {
      setContacts([])
      return
    }
    let cancelled = false
    void listContacts({ accountId: form.accountId, size: 100, sort: 'lastName,asc' })
      .then((page) => {
        if (!cancelled) setContacts(page.content)
      })
      .catch(() => {
        if (!cancelled) setContacts([])
      })
    return () => {
      cancelled = true
    }
  }, [open, form.accountId])

  if (!open) return null

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.name.trim()) next.name = 'Name is required'
    if (!form.accountId) next.accountId = 'Account is required'
    if (form.amount.trim()) {
      const n = Number(form.amount)
      if (Number.isNaN(n) || n < 0) next.amount = 'Amount must be a non-negative number'
    }
    if (form.description.trim().length > 2000) next.description = 'Description must be at most 2000 characters'
    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!validate()) return

    const amount = form.amount.trim() === '' ? null : Number(form.amount)
    const contactId = form.primaryContactId || null

    try {
      if (mode === 'create') {
        if (!idempotencyKeyRef.current) idempotencyKeyRef.current = newIdempotencyKey()
        const body: DealCreateRequest = {
          name: form.name.trim(),
          accountId: form.accountId,
          primaryContactId: contactId,
          amount,
          currency: form.currency,
          expectedCloseDate: optionalTrim(form.expectedCloseDate),
          description: optionalTrim(form.description),
        }
        if (isAdmin && form.ownerId) body.ownerId = form.ownerId
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (deal) {
        const body: DealUpdateRequest = {
          name: form.name.trim(),
          accountId: form.accountId,
          primaryContactId: contactId,
          ownerId: isAdmin && form.ownerId ? form.ownerId : deal.ownerId,
          stage: deal.stage,
          amount,
          currency: form.currency,
          probability: deal.probability,
          expectedCloseDate: optionalTrim(form.expectedCloseDate),
          description: optionalTrim(form.description),
          lostReason: deal.lostReason,
        }
        await onUpdate(deal.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      setError(formatApiError(err, mode === 'create' ? 'Failed to create deal' : 'Failed to update deal'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overlay-backdrop p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="deal-form-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="deal-form-title" className="text-lg font-semibold text-ink">
              {mode === 'create' ? 'Add deal' : 'Edit deal'}
            </h2>
            <p className="mt-1 text-sm text-muted">
              {mode === 'create'
                ? 'Creates via POST /api/v1/deals. Stage defaults to Prospecting.'
                : 'Stage changes use the pipeline actions, not this form.'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-canvas disabled:opacity-60"
          >
            Close
          </button>
        </div>

        <form className="mt-5 space-y-4" onSubmit={onSubmit} noValidate>
          <Field label="Deal name" error={fieldErrors.name} required>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              disabled={pending}
              className="ui-input"
              autoFocus
            />
          </Field>
          <Field label="Account" error={fieldErrors.accountId} required>
            <select
              value={form.accountId}
              onChange={(e) => setForm((f) => ({ ...f, accountId: e.target.value, primaryContactId: '' }))}
              disabled={pending}
              className="ui-input"
            >
              <option value="">Select account</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Primary contact" error={fieldErrors.primaryContactId}>
            <select
              value={form.primaryContactId}
              onChange={(e) => setForm((f) => ({ ...f, primaryContactId: e.target.value }))}
              disabled={pending || !form.accountId}
              className="ui-input"
            >
              <option value="">None</option>
              {contacts.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.firstName} {c.lastName}
                </option>
              ))}
            </select>
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Amount" error={fieldErrors.amount}>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.amount}
                onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
                disabled={pending}
                className="ui-input"
              />
            </Field>
            <Field label="Currency" error={fieldErrors.currency}>
              <select
                value={form.currency}
                onChange={(e) => setForm((f) => ({ ...f, currency: e.target.value }))}
                disabled={pending}
                className="ui-input"
              >
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Expected close date" error={fieldErrors.expectedCloseDate}>
            <input
              type="date"
              value={form.expectedCloseDate}
              onChange={(e) => setForm((f) => ({ ...f, expectedCloseDate: e.target.value }))}
              disabled={pending}
              className="ui-input"
            />
          </Field>
          <Field label="Description" error={fieldErrors.description}>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              disabled={pending}
              rows={3}
              className="ui-input"
            />
          </Field>
          {isAdmin && users.length > 0 ? (
            <Field label="Owner" error={fieldErrors.ownerId}>
              <select
                value={form.ownerId}
                onChange={(e) => setForm((f) => ({ ...f, ownerId: e.target.value }))}
                disabled={pending}
                className="ui-input"
              >
                <option value="">Current user (default)</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName}
                  </option>
                ))}
              </select>
            </Field>
          ) : mode === 'edit' && deal ? (
            <p className="text-sm text-muted">
              Owner: <span className="font-medium text-ink">{deal.ownerName}</span>
            </p>
          ) : null}

          {error ? (
            <div className="alert alert-error">{error}</div>
          ) : null}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={pending}
              className="btn btn-secondary disabled:opacity-60"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={pending}
              className="btn btn-primary disabled:opacity-60"
            >
              {pending ? 'Saving…' : mode === 'create' ? 'Create deal' : 'Save changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  error,
  required,
  children,
}: {
  label: string
  error?: string
  required?: boolean
  children: ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-ink">
        {label}
        {required ? <span className="text-red-500"> *</span> : null}
      </span>
      {children}
      {error ? <span className="mt-1 block text-xs text-[color:var(--app-danger-text)]">{error}</span> : null}
    </label>
  )
}
