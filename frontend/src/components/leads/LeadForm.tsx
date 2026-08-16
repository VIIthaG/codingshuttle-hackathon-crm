import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import type { Lead, LeadCreateRequest, LeadSource, LeadUpdateRequest } from '../../types/lead'
import { LEAD_SOURCES, formatLeadSource } from '../../utils/leadTransitions'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatApiError } from '../../utils/errors'
import { ApiError } from '../../types/api'

export type LeadFormMode = 'create' | 'edit'

type LeadFormProps = {
  open: boolean
  mode: LeadFormMode
  lead?: Lead | null
  pending?: boolean
  onClose: () => void
  onCreate: (body: LeadCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: LeadUpdateRequest) => Promise<void>
}

type FormState = {
  fullName: string
  email: string
  phone: string
  company: string
  source: LeadSource
}

const emptyForm: FormState = {
  fullName: '',
  email: '',
  phone: '',
  company: '',
  source: 'WEB',
}

function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function optionalTrim(value: string): string | null {
  const t = value.trim()
  return t === '' ? null : t
}

export function LeadForm({
  open,
  mode,
  lead,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: LeadFormProps) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && lead) {
      setForm({
        fullName: lead.fullName,
        email: lead.email ?? '',
        phone: lead.phone ?? '',
        company: lead.company ?? '',
        source: lead.source,
      })
      idempotencyKeyRef.current = null
    } else {
      setForm(emptyForm)
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, lead])

  if (!open) return null

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.fullName.trim()) {
      next.fullName = 'Full name is required'
    }
    if (!form.source) {
      next.source = 'Source is required'
    }
    const email = form.email.trim()
    if (email && !isValidEmail(email)) {
      next.email = 'Email must be valid'
    }
    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!validate()) return

    try {
      if (mode === 'create') {
        if (!idempotencyKeyRef.current) {
          idempotencyKeyRef.current = newIdempotencyKey()
        }
        const body: LeadCreateRequest = {
          fullName: form.fullName.trim(),
          email: optionalTrim(form.email),
          phone: optionalTrim(form.phone),
          company: optionalTrim(form.company),
          source: form.source,
        }
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (lead) {
        const body: LeadUpdateRequest = {
          fullName: form.fullName.trim(),
          email: optionalTrim(form.email),
          phone: optionalTrim(form.phone),
          company: optionalTrim(form.company),
          source: form.source,
          status: lead.status,
          assignedToId: lead.assignedToId,
        }
        await onUpdate(lead.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        setFieldErrors(err.fieldErrors)
      }
      setError(formatApiError(err, mode === 'create' ? 'Failed to create lead' : 'Failed to update lead'))
      // Keep same idempotency key on failure for create retries
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overlay-backdrop p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="lead-form-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="lead-form-title" className="text-lg font-semibold text-ink">
              {mode === 'create' ? 'Add lead' : 'Edit lead'}
            </h2>
            <p className="mt-1 text-sm text-muted">
              {mode === 'create'
                ? 'Creates via POST /api/v1/leads. Assignee defaults to you.'
                : 'Updates contact fields. Use status actions for pipeline moves.'}
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
          <Field
            label="Full name"
            error={fieldErrors.fullName}
            required
          >
            <input
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
              disabled={pending}
              className="ui-input"
              autoFocus
            />
          </Field>

          <Field label="Email" error={fieldErrors.email}>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              disabled={pending}
              className="ui-input"
            />
          </Field>

          <Field label="Phone" error={fieldErrors.phone}>
            <input
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
              disabled={pending}
              className="ui-input"
            />
          </Field>

          <Field label="Company" error={fieldErrors.company}>
            <input
              value={form.company}
              onChange={(e) => setForm((f) => ({ ...f, company: e.target.value }))}
              disabled={pending}
              className="ui-input"
            />
          </Field>

          <Field label="Source" error={fieldErrors.source} required>
            <select
              value={form.source}
              onChange={(e) => setForm((f) => ({ ...f, source: e.target.value as LeadSource }))}
              disabled={pending}
              className="ui-input"
            >
              {LEAD_SOURCES.map((source) => (
                <option key={source} value={source}>
                  {formatLeadSource(source)}
                </option>
              ))}
            </select>
          </Field>

          {mode === 'edit' && lead ? (
            <div className="rounded-lg border border-border bg-canvas px-3 py-2 text-sm text-muted">
              Assigned to <span className="font-medium text-ink">{lead.assignedToName}</span>
              <span className="block text-xs mt-0.5">
                Status stays {lead.status} here — use Move status actions to change the pipeline.
              </span>
            </div>
          ) : null}

          {error ? (
            <div className="alert alert-error">
              {error}
            </div>
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
              {pending ? 'Saving…' : mode === 'create' ? 'Create lead' : 'Save changes'}
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
