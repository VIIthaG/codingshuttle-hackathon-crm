import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import type { Account } from '../../types/account'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../../types/contact'
import type { User } from '../../types/auth'
import { ApiError } from '../../types/api'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatApiError } from '../../utils/errors'

type ContactFormProps = {
  open: boolean
  mode: 'create' | 'edit'
  contact?: Contact | null
  accounts: Account[]
  users: User[]
  isAdmin: boolean
  pending?: boolean
  defaultAccountId?: string
  onClose: () => void
  onCreate: (body: ContactCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: ContactUpdateRequest) => Promise<void>
}

type FormState = {
  firstName: string
  lastName: string
  email: string
  phone: string
  jobTitle: string
  notes: string
  accountId: string
  ownerId: string
}

function optionalTrim(value: string): string | null {
  const t = value.trim()
  return t === '' ? null : t
}

function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

export function ContactForm({
  open,
  mode,
  contact,
  accounts,
  users,
  isAdmin,
  pending = false,
  defaultAccountId,
  onClose,
  onCreate,
  onUpdate,
}: ContactFormProps) {
  const [form, setForm] = useState<FormState>({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    jobTitle: '',
    notes: '',
    accountId: '',
    ownerId: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && contact) {
      setForm({
        firstName: contact.firstName,
        lastName: contact.lastName,
        email: contact.email ?? '',
        phone: contact.phone ?? '',
        jobTitle: contact.jobTitle ?? '',
        notes: contact.notes ?? '',
        accountId: contact.accountId ?? '',
        ownerId: contact.ownerId,
      })
      idempotencyKeyRef.current = null
    } else {
      setForm({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        jobTitle: '',
        notes: '',
        accountId: defaultAccountId ?? '',
        ownerId: '',
      })
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, contact, defaultAccountId])

  if (!open) return null

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.firstName.trim()) next.firstName = 'First name is required'
    if (!form.lastName.trim()) next.lastName = 'Last name is required'
    const email = form.email.trim()
    if (email && !isValidEmail(email)) next.email = 'Email must be valid'
    if (form.notes.trim().length > 2000) next.notes = 'Notes must be at most 2000 characters'
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
        const body: ContactCreateRequest = {
          firstName: form.firstName.trim(),
          lastName: form.lastName.trim(),
          email: optionalTrim(form.email),
          phone: optionalTrim(form.phone),
          jobTitle: optionalTrim(form.jobTitle),
          notes: optionalTrim(form.notes),
          accountId: form.accountId || null,
        }
        if (isAdmin && form.ownerId) body.ownerId = form.ownerId
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (contact) {
        const body: ContactUpdateRequest = {
          firstName: form.firstName.trim(),
          lastName: form.lastName.trim(),
          email: optionalTrim(form.email),
          phone: optionalTrim(form.phone),
          jobTitle: optionalTrim(form.jobTitle),
          notes: optionalTrim(form.notes),
          accountId: form.accountId || null,
          ownerId: isAdmin && form.ownerId ? form.ownerId : contact.ownerId,
        }
        await onUpdate(contact.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      setError(formatApiError(err, mode === 'create' ? 'Failed to create contact' : 'Failed to update contact'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="contact-form-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="contact-form-title" className="text-lg font-semibold text-ink">
              {mode === 'create' ? 'Add contact' : 'Edit contact'}
            </h2>
            <p className="mt-1 text-sm text-muted">Account is optional. Owner defaults to you.</p>
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
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="First name" error={fieldErrors.firstName} required>
              <input
                value={form.firstName}
                onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))}
                disabled={pending}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
                autoFocus
              />
            </Field>
            <Field label="Last name" error={fieldErrors.lastName} required>
              <input
                value={form.lastName}
                onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))}
                disabled={pending}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
              />
            </Field>
          </div>
          <Field label="Email" error={fieldErrors.email}>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
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
          <Field label="Job title" error={fieldErrors.jobTitle}>
            <input
              value={form.jobTitle}
              onChange={(e) => setForm((f) => ({ ...f, jobTitle: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          <Field label="Account">
            <select
              value={form.accountId}
              onChange={(e) => setForm((f) => ({ ...f, accountId: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            >
              <option value="">No account</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Notes" error={fieldErrors.notes}>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              disabled={pending}
              rows={3}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            />
          </Field>
          {isAdmin && users.length > 0 ? (
            <Field label="Owner">
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
              {pending ? 'Saving…' : mode === 'create' ? 'Create contact' : 'Save changes'}
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
