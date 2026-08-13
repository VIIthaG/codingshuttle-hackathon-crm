import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../../types/account'
import type { User } from '../../types/auth'
import { ApiError } from '../../types/api'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatApiError } from '../../utils/errors'

type AccountFormProps = {
  open: boolean
  mode: 'create' | 'edit'
  account?: Account | null
  users: User[]
  isAdmin: boolean
  pending?: boolean
  onClose: () => void
  onCreate: (body: AccountCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: AccountUpdateRequest) => Promise<void>
}

type FormState = {
  name: string
  website: string
  phone: string
  industry: string
  description: string
  ownerId: string
}

function optionalTrim(value: string): string | null {
  const t = value.trim()
  return t === '' ? null : t
}

export function AccountForm({
  open,
  mode,
  account,
  users,
  isAdmin,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: AccountFormProps) {
  const [form, setForm] = useState<FormState>({
    name: '',
    website: '',
    phone: '',
    industry: '',
    description: '',
    ownerId: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && account) {
      setForm({
        name: account.name,
        website: account.website ?? '',
        phone: account.phone ?? '',
        industry: account.industry ?? '',
        description: account.description ?? '',
        ownerId: account.ownerId,
      })
      idempotencyKeyRef.current = null
    } else {
      setForm({
        name: '',
        website: '',
        phone: '',
        industry: '',
        description: '',
        ownerId: '',
      })
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, account])

  if (!open) return null

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.name.trim()) next.name = 'Name is required'
    else if (form.name.trim().length > 255) next.name = 'Name must be at most 255 characters'
    if (form.description.trim().length > 2000) next.description = 'Description must be at most 2000 characters'
    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!validate()) return

    try {
      if (mode === 'create') {
        if (!idempotencyKeyRef.current) idempotencyKeyRef.current = newIdempotencyKey()
        const body: AccountCreateRequest = {
          name: form.name.trim(),
          website: optionalTrim(form.website),
          phone: optionalTrim(form.phone),
          industry: optionalTrim(form.industry),
          description: optionalTrim(form.description),
        }
        if (isAdmin && form.ownerId) body.ownerId = form.ownerId
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (account) {
        const body: AccountUpdateRequest = {
          name: form.name.trim(),
          website: optionalTrim(form.website),
          phone: optionalTrim(form.phone),
          industry: optionalTrim(form.industry),
          description: optionalTrim(form.description),
          ownerId: isAdmin && form.ownerId ? form.ownerId : account.ownerId,
        }
        await onUpdate(account.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      setError(formatApiError(err, mode === 'create' ? 'Failed to create account' : 'Failed to update account'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-form-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="account-form-title" className="text-lg font-semibold text-ink">
              {mode === 'create' ? 'Add account' : 'Edit account'}
            </h2>
            <p className="mt-1 text-sm text-muted">
              {mode === 'create'
                ? 'Creates via POST /api/v1/accounts. Owner defaults to you.'
                : 'Updates company fields. Owner change is ADMIN-only.'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-slate-100 disabled:opacity-60"
          >
            Close
          </button>
        </div>

        <form className="mt-5 space-y-4" onSubmit={onSubmit} noValidate>
          <Field label="Name" error={fieldErrors.name} required>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
              autoFocus
            />
          </Field>
          <Field label="Website" error={fieldErrors.website}>
            <input
              value={form.website}
              onChange={(e) => setForm((f) => ({ ...f, website: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          <Field label="Phone" error={fieldErrors.phone}>
            <input
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          <Field label="Industry" error={fieldErrors.industry}>
            <input
              value={form.industry}
              onChange={(e) => setForm((f) => ({ ...f, industry: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          <Field label="Description" error={fieldErrors.description}>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              disabled={pending}
              rows={3}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          {isAdmin && users.length > 0 ? (
            <Field label="Owner" error={fieldErrors.ownerId}>
              <select
                value={form.ownerId}
                onChange={(e) => setForm((f) => ({ ...f, ownerId: e.target.value }))}
                disabled={pending}
                className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
              >
                <option value="">Current user (default)</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName}
                  </option>
                ))}
              </select>
            </Field>
          ) : mode === 'edit' && account ? (
            <p className="text-sm text-muted">
              Owner: <span className="font-medium text-ink">{account.ownerName}</span>
            </p>
          ) : null}

          {error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
          ) : null}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={pending}
              className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={pending}
              className="rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
            >
              {pending ? 'Saving…' : mode === 'create' ? 'Create account' : 'Save changes'}
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
      {error ? <span className="mt-1 block text-xs text-red-600">{error}</span> : null}
    </label>
  )
}
