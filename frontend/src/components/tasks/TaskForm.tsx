import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import type { Lead } from '../../types/lead'
import type { Task, TaskCreateRequest, TaskUpdateRequest } from '../../types/task'
import { ApiError } from '../../types/api'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatApiError } from '../../utils/errors'
import {
  defaultDueLocal,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from '../../utils/taskDates'

export type TaskFormMode = 'create' | 'edit'

type TaskFormProps = {
  open: boolean
  mode: TaskFormMode
  task?: Task | null
  leads: Lead[]
  leadsLoading?: boolean
  pending?: boolean
  onClose: () => void
  onCreate: (body: TaskCreateRequest, idempotencyKey: string) => Promise<void>
  onUpdate: (id: string, body: TaskUpdateRequest) => Promise<void>
}

type FormState = {
  leadId: string
  title: string
  description: string
  dueAtLocal: string
  reminderAtLocal: string
  clearReminder: boolean
}

function emptyForm(leads: Lead[]): FormState {
  return {
    leadId: leads[0]?.id ?? '',
    title: '',
    description: '',
    dueAtLocal: defaultDueLocal(),
    reminderAtLocal: '',
    clearReminder: false,
  }
}

export function TaskForm({
  open,
  mode,
  task,
  leads,
  leadsLoading = false,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: TaskFormProps) {
  const [form, setForm] = useState<FormState>(() => emptyForm(leads))
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && task) {
      setForm({
        leadId: task.leadId,
        title: task.title,
        description: task.description ?? '',
        dueAtLocal: toDatetimeLocalValue(task.dueAt),
        reminderAtLocal: toDatetimeLocalValue(task.reminderAt),
        clearReminder: false,
      })
      idempotencyKeyRef.current = null
    } else {
      setForm(emptyForm(leads))
      idempotencyKeyRef.current = newIdempotencyKey()
    }
  }, [open, mode, task, leads])

  if (!open) return null

  function validate(): boolean {
    const next: Record<string, string> = {}
    if (!form.leadId) next.leadId = 'Lead is required'
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
          leadId: form.leadId,
          title: form.title.trim(),
          description: form.description.trim() === '' ? null : form.description.trim(),
          dueAt: fromDatetimeLocalValue(form.dueAtLocal),
          reminderAt: resolveReminderIso(),
        }
        await onCreate(body, idempotencyKeyRef.current)
        idempotencyKeyRef.current = newIdempotencyKey()
      } else if (task) {
        const body: TaskUpdateRequest = {
          leadId: form.leadId,
          assignedToId: task.assignedToId,
          title: form.title.trim(),
          description: form.description.trim() === '' ? null : form.description.trim(),
          dueAt: fromDatetimeLocalValue(form.dueAtLocal),
          reminderAt: resolveReminderIso(),
          status: task.status,
        }
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
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
            <p className="mt-1 text-sm text-muted">
              {mode === 'create'
                ? 'Creates a follow-up via POST /api/v1/tasks. Assignee defaults to you.'
                : 'Updates fields via PUT. Status actions stay on Complete / Cancel.'}
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
          <Field label="Lead" error={fieldErrors.leadId} required>
            <select
              value={form.leadId}
              onChange={(e) => setForm((f) => ({ ...f, leadId: e.target.value }))}
              disabled={pending || leadsLoading}
              className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
            >
              {leads.length === 0 ? (
                <option value="">No accessible leads</option>
              ) : (
                leads.map((lead) => (
                  <option key={lead.id} value={lead.id}>
                    {lead.fullName}
                    {lead.company ? ` — ${lead.company}` : ''}
                  </option>
                ))
              )}
            </select>
          </Field>

          <Field label="Title" error={fieldErrors.title} required>
            <input
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
              autoFocus
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

          <Field label="Due" error={fieldErrors.dueAt || fieldErrors.dueAtLocal} required>
            <input
              type="datetime-local"
              value={form.dueAtLocal}
              onChange={(e) => setForm((f) => ({ ...f, dueAtLocal: e.target.value }))}
              disabled={pending}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
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
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
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
                className="mt-2 text-xs font-medium text-slate-600 hover:text-slate-800 disabled:opacity-60"
              >
                Clear reminder
              </button>
            ) : null}
          </Field>

          {mode === 'edit' && task ? (
            <div className="rounded-lg border border-border bg-canvas px-3 py-2 text-sm text-muted">
              Assigned to <span className="font-medium text-ink">{task.assignedToName}</span>
              <span className="mt-0.5 block text-xs">Status: {task.status}</span>
            </div>
          ) : null}

          {error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
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
              disabled={pending || (mode === 'create' && leads.length === 0)}
              className="rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
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
      {error ? <span className="mt-1 block text-xs text-red-600">{error}</span> : null}
    </label>
  )
}
