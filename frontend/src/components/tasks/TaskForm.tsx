import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { listAllAccounts } from '../../api/accounts'
import { listAllContacts } from '../../api/contacts'
import { listAllDeals } from '../../api/deals'
import { listAllLeads } from '../../api/leads'
import type { Account } from '../../types/account'
import { ApiError } from '../../types/api'
import type { Contact } from '../../types/contact'
import type { Deal } from '../../types/deal'
import type { Lead } from '../../types/lead'
import type { RelatedRecordType, Task, TaskCreateRequest, TaskUpdateRequest } from '../../types/task'
import { RELATED_RECORD_TYPES, relatedTypeLabel } from '../../types/task'
import { formatApiError } from '../../utils/errors'
import { newIdempotencyKey } from '../../utils/idempotency'
import {
  defaultDueLocal,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from '../../utils/taskDates'
import { AssigneeSelect } from '../crm/AssigneeSelect'

export type TaskFormMode = 'create' | 'edit'

export type TaskRelatedPreset = {
  type: RelatedRecordType
  id: string
}

type TaskFormProps = {
  open: boolean
  mode: TaskFormMode
  task?: Task | null
  initialRelated?: TaskRelatedPreset | null
  pending?: boolean
  onClose: () => void
  onCreate: (body: TaskCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: TaskUpdateRequest) => Promise<void>
}

type FormState = {
  relatedType: RelatedRecordType
  relatedId: string
  assignedToId: string
  title: string
  description: string
  dueAtLocal: string
  reminderAtLocal: string
  clearReminder: boolean
}

function emptyForm(preset?: TaskRelatedPreset | null): FormState {
  return {
    relatedType: preset?.type ?? 'LEAD',
    relatedId: preset?.id ?? '',
    assignedToId: '',
    title: '',
    description: '',
    dueAtLocal: defaultDueLocal(),
    reminderAtLocal: '',
    clearReminder: false,
  }
}

function taskToForm(task: Task): FormState {
  return {
    relatedType: task.relatedType,
    relatedId: task.relatedId,
    assignedToId: task.assignedToId,
    title: task.title,
    description: task.description ?? '',
    dueAtLocal: toDatetimeLocalValue(task.dueAt),
    reminderAtLocal: toDatetimeLocalValue(task.reminderAt),
    clearReminder: false,
  }
}

function applyRelatedId(
  body: TaskCreateRequest | TaskUpdateRequest,
  type: RelatedRecordType,
  id: string,
) {
  body.leadId = null
  body.accountId = null
  body.contactId = null
  body.dealId = null
  if (type === 'LEAD') body.leadId = id
  if (type === 'ACCOUNT') body.accountId = id
  if (type === 'CONTACT') body.contactId = id
  if (type === 'DEAL') body.dealId = id
}

export function TaskForm({
  open,
  mode,
  task,
  initialRelated = null,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: TaskFormProps) {
  const [form, setForm] = useState<FormState>(() => emptyForm(initialRelated))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [leads, setLeads] = useState<Lead[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [contacts, setContacts] = useState<Contact[]>([])
  const [deals, setDeals] = useState<Deal[]>([])
  const [optionsLoading, setOptionsLoading] = useState(false)
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && task) {
      setForm(taskToForm(task))
      idempotencyKeyRef.current = null
    } else {
      setForm(emptyForm(initialRelated))
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, task, initialRelated])

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setOptionsLoading(true)
    void Promise.all([listAllLeads(), listAllAccounts(), listAllContacts(), listAllDeals()])
      .then(([leadRows, accountRows, contactRows, dealRows]) => {
        if (cancelled) return
        setLeads(leadRows)
        setAccounts(accountRows)
        setContacts(contactRows)
        setDeals(dealRows)
      })
      .catch(() => {
        if (cancelled) return
        setLeads([])
        setAccounts([])
        setContacts([])
        setDeals([])
      })
      .finally(() => {
        if (!cancelled) setOptionsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open])

  if (!open) return null

  const relatedOptions: { id: string; label: string }[] = (() => {
    switch (form.relatedType) {
      case 'LEAD':
        return leads.map((l) => ({
          id: l.id,
          label: l.company ? `${l.fullName} — ${l.company}` : l.fullName,
        }))
      case 'ACCOUNT':
        return accounts.map((a) => ({ id: a.id, label: a.name }))
      case 'CONTACT':
        return contacts.map((c) => ({
          id: c.id,
          label: `${c.firstName} ${c.lastName}${c.accountName ? ` — ${c.accountName}` : ''}`,
        }))
      case 'DEAL':
        return deals.map((d) => ({
          id: d.id,
          label: d.accountName ? `${d.name} — ${d.accountName}` : d.name,
        }))
      default:
        return []
    }
  })()

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.relatedId) next.relatedId = 'Related record is required'
    if (!form.title.trim()) next.title = 'Title is required'
    else if (form.title.trim().length > 255) next.title = 'Title must be at most 255 characters'
    if (form.description.trim().length > 2000) {
      next.description = 'Description must be at most 2000 characters'
    }
    if (!form.dueAtLocal) next.dueAt = 'Due date is required'
    else if (Number.isNaN(new Date(form.dueAtLocal).getTime())) {
      next.dueAt = 'Due date is invalid'
    }

    const reminderLocal =
      form.clearReminder || !form.reminderAtLocal.trim() ? null : form.reminderAtLocal
    if (reminderLocal) {
      const reminder = new Date(reminderLocal)
      const due = new Date(form.dueAtLocal)
      if (Number.isNaN(reminder.getTime())) {
        next.reminderAt = 'Reminder time is invalid'
      } else if (!Number.isNaN(due.getTime()) && reminder.getTime() > due.getTime()) {
        next.reminderAt = 'Reminder must not be after due date'
      }
    }

    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  function resolveReminderIso(): string | null {
    if (form.clearReminder || !form.reminderAtLocal.trim()) return null
    return fromDatetimeLocalValue(form.reminderAtLocal)
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
        const body: TaskCreateRequest = {
          title: form.title.trim(),
          description: form.description.trim() === '' ? null : form.description.trim(),
          dueAt: fromDatetimeLocalValue(form.dueAtLocal),
          reminderAt: resolveReminderIso(),
        }
        if (form.assignedToId) body.assignedToId = form.assignedToId
        applyRelatedId(body, form.relatedType, form.relatedId)
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (task) {
        const body: TaskUpdateRequest = {
          assignedToId: form.assignedToId || task.assignedToId,
          title: form.title.trim(),
          description: form.description.trim() === '' ? null : form.description.trim(),
          dueAt: fromDatetimeLocalValue(form.dueAtLocal),
          reminderAt: resolveReminderIso(),
          status: task.status,
        }
        applyRelatedId(body, form.relatedType, form.relatedId)
        await onUpdate(task.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        setFieldErrors(err.fieldErrors)
      }
      setError(formatApiError(err, mode === 'create' ? 'Failed to create task' : 'Failed to update task'))
    }
  }

  const showClearReminder =
    mode === 'edit' && task?.reminderAt != null && !form.clearReminder

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overlay-backdrop p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-form-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="task-form-title" className="text-lg font-semibold text-ink">
              {mode === 'create' ? 'Add task' : 'Edit task'}
            </h2>
            <p className="mt-1 text-sm text-muted">Related to exactly one Lead, Account, Contact, or Deal.</p>
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
          <div>
            <span className="mb-1.5 block text-sm font-medium text-ink">
              Related to<span className="text-red-500"> *</span>
            </span>
            <div className="grid gap-2 sm:grid-cols-2">
              <select
                value={form.relatedType}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    relatedType: e.target.value as RelatedRecordType,
                    relatedId: '',
                  }))
                }
                disabled={pending || optionsLoading}
                className="ui-input"
              >
                {RELATED_RECORD_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {relatedTypeLabel(type)}
                  </option>
                ))}
              </select>
              <select
                value={form.relatedId}
                onChange={(e) => setForm((f) => ({ ...f, relatedId: e.target.value }))}
                disabled={pending || optionsLoading}
                className="ui-input"
              >
                <option value="">{optionsLoading ? 'Loading…' : 'Select record'}</option>
                {relatedOptions.map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            {fieldErrors.relatedId ? (
              <span className="mt-1 block text-xs text-[color:var(--app-danger-text)]">{fieldErrors.relatedId}</span>
            ) : null}
          </div>

          <Field label="Title" error={fieldErrors.title} required>
            <input
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              disabled={pending}
              className="ui-input"
              autoFocus
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

          <Field label="Due" error={fieldErrors.dueAt || fieldErrors.dueAtLocal} required>
            <input
              type="datetime-local"
              value={form.dueAtLocal}
              onChange={(e) => setForm((f) => ({ ...f, dueAtLocal: e.target.value }))}
              disabled={pending}
              className="ui-input"
            />
          </Field>

          <Field label="Reminder (optional)" error={fieldErrors.reminderAt || fieldErrors.reminderAtLocal}>
            {form.clearReminder ? (
              <div className="rounded-lg border border-dashed border-border bg-canvas px-3 py-2 text-sm text-muted">
                Reminder will be cleared on save.
                <button
                  type="button"
                  className="ml-2 font-medium text-brand-600"
                  onClick={() =>
                    setForm((f) => ({
                      ...f,
                      clearReminder: false,
                      reminderAtLocal: task ? toDatetimeLocalValue(task.reminderAt) : '',
                    }))
                  }
                >
                  Undo
                </button>
              </div>
            ) : (
              <input
                type="datetime-local"
                value={form.reminderAtLocal}
                onChange={(e) =>
                  setForm((f) => ({ ...f, reminderAtLocal: e.target.value, clearReminder: false }))
                }
                disabled={pending}
                className="ui-input"
              />
            )}
            <p className="mt-1 text-xs text-muted">
              Must not be after due. Backend schedules the reminder; this UI does not deliver notifications.
            </p>
            {showClearReminder ? (
              <button
                type="button"
                disabled={pending}
                onClick={() => setForm((f) => ({ ...f, clearReminder: true, reminderAtLocal: '' }))}
                className="mt-2 text-xs font-medium text-muted hover:text-slate-800 disabled:opacity-60"
              >
                Clear reminder
              </button>
            ) : null}
          </Field>

          {mode === 'edit' && task ? (
            <div className="rounded-lg border border-border bg-canvas px-3 py-2 text-sm text-muted">
              <span className="block text-xs">Status: {task.status}</span>
            </div>
          ) : null}

          <AssigneeSelect
            open={open}
            value={form.assignedToId}
            onChange={(id) => setForm((f) => ({ ...f, assignedToId: id }))}
            disabled={pending}
            currentAssigneeName={mode === 'edit' ? task?.assignedToName : null}
          />

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
              {pending ? 'Saving…' : mode === 'create' ? 'Create task' : 'Save changes'}
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
