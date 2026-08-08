import { useCallback, useEffect, useMemo, useState } from 'react'
import { Plus, RefreshCw } from 'lucide-react'
import { listAllLeads } from '../api/leads'
import {
  completeTask,
  createTask,
  deleteTask,
  listAllTasks,
  updateTask,
  type ListTasksParams,
} from '../api/tasks'
import { useAuth } from '../auth/useAuth'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TaskDetails } from '../components/tasks/TaskDetails'
import { TaskForm } from '../components/tasks/TaskForm'
import { TaskTable } from '../components/tasks/TaskTable'
import type { Lead } from '../types/lead'
import type { Task, TaskCreateRequest, TaskStatus, TaskUpdateRequest } from '../types/task'
import { formatApiError } from '../utils/errors'

type StatusFilter = 'ALL' | TaskStatus | 'OVERDUE'

export function TasksPage() {
  const { user } = useAuth()
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('OPEN')
  const [leadFilter, setLeadFilter] = useState<string>('')

  const [tasks, setTasks] = useState<Task[]>([])
  const [leads, setLeads] = useState<Lead[]>([])
  const [loading, setLoading] = useState(true)
  const [leadsLoading, setLeadsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selected, setSelected] = useState<Task | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [formPending, setFormPending] = useState(false)

  const [actionPendingId, setActionPendingId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = useState<Task | null>(null)
  const [cancelTarget, setCancelTarget] = useState<Task | null>(null)
  const [confirmPending, setConfirmPending] = useState(false)

  const listParams = useMemo((): Omit<ListTasksParams, 'page' | 'size' | 'sort'> => {
    const params: Omit<ListTasksParams, 'page' | 'size' | 'sort'> = {}
    if (statusFilter === 'OVERDUE') {
      params.overdue = true
    } else if (statusFilter !== 'ALL') {
      params.status = statusFilter
    }
    if (leadFilter) params.leadId = leadFilter
    return params
  }, [statusFilter, leadFilter])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listAllTasks(listParams)
      setTasks(data)
      setSelected((prev) => {
        if (!prev) return null
        return data.find((t) => t.id === prev.id) ?? null
      })
    } catch (err) {
      setError(formatApiError(err, 'Failed to load tasks'))
    } finally {
      setLoading(false)
    }
  }, [listParams])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    let cancelled = false
    async function loadLeads() {
      setLeadsLoading(true)
      try {
        const data = await listAllLeads()
        if (!cancelled) setLeads(data)
      } catch {
        if (!cancelled) setLeads([])
      } finally {
        if (!cancelled) setLeadsLoading(false)
      }
    }
    void loadLeads()
    return () => {
      cancelled = true
    }
  }, [])

  function openTask(task: Task) {
    setSelected(task)
    setActionError(null)
    setDetailsOpen(true)
  }

  function openCreate() {
    setFormMode('create')
    setFormOpen(true)
  }

  function openEdit(task: Task) {
    setSelected(task)
    setFormMode('edit')
    setFormOpen(true)
  }

  function upsertLocal(task: Task) {
    setTasks((prev) => {
      const exists = prev.some((t) => t.id === task.id)
      if (!exists) return [task, ...prev]
      return prev.map((t) => (t.id === task.id ? task : t))
    })
    setSelected((prev) => (prev?.id === task.id ? task : prev))
  }

  async function handleCreate(body: TaskCreateRequest, idempotencyKey: string) {
    setFormPending(true)
    try {
      const created = await createTask(body, idempotencyKey)
      setFormOpen(false)
      upsertLocal(created)
      openTask(created)
      void refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleUpdate(id: string, body: TaskUpdateRequest) {
    setFormPending(true)
    try {
      const updated = await updateTask(id, body)
      setFormOpen(false)
      upsertLocal(updated)
      setDetailsOpen(true)
      void refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleComplete(task: Task) {
    setActionPendingId(task.id)
    setActionError(null)
    try {
      const updated = await completeTask(task.id)
      upsertLocal(updated)
      void refresh()
    } catch (err) {
      setActionError(formatApiError(err, 'Could not complete task'))
    } finally {
      setActionPendingId(null)
    }
  }

  async function handleCancelConfirm() {
    if (!cancelTarget) return
    setConfirmPending(true)
    setActionError(null)
    try {
      const body: TaskUpdateRequest = {
        leadId: cancelTarget.leadId,
        assignedToId: cancelTarget.assignedToId,
        title: cancelTarget.title,
        description: cancelTarget.description,
        dueAt: cancelTarget.dueAt,
        reminderAt: cancelTarget.reminderAt,
        status: 'CANCELLED',
      }
      const updated = await updateTask(cancelTarget.id, body)
      setCancelTarget(null)
      upsertLocal(updated)
      void refresh()
    } catch (err) {
      setActionError(formatApiError(err, 'Could not cancel task'))
      setCancelTarget(null)
    } finally {
      setConfirmPending(false)
    }
  }

  async function handleDeleteConfirm() {
    if (!deleteTarget) return
    setConfirmPending(true)
    try {
      await deleteTask(deleteTarget.id)
      const id = deleteTarget.id
      setDeleteTarget(null)
      setTasks((prev) => prev.filter((t) => t.id !== id))
      if (selected?.id === id) {
        setDetailsOpen(false)
        setSelected(null)
      }
      void refresh()
    } catch (err) {
      setError(formatApiError(err, 'Failed to delete task'))
      setDeleteTarget(null)
    } finally {
      setConfirmPending(false)
    }
  }

  const filters: { id: StatusFilter; label: string }[] = [
    { id: 'ALL', label: 'All' },
    { id: 'OPEN', label: 'Open' },
    { id: 'COMPLETED', label: 'Completed' },
    { id: 'CANCELLED', label: 'Cancelled' },
    { id: 'OVERDUE', label: 'Overdue' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink">Tasks</h2>
          <p className="mt-1 max-w-xl text-sm text-muted">
            Follow-ups linked to leads, with optional reminder scheduling on the backend.
            {user?.role === 'SALES_REP'
              ? ' You see tasks assigned to you.'
              : ' Admins see all tasks across the team.'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => void refresh()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            type="button"
            onClick={openCreate}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Add task
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="inline-flex flex-wrap gap-1 rounded-lg border border-border bg-white p-0.5 shadow-sm">
          {filters.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setStatusFilter(f.id)}
              className={[
                'rounded-md px-3 py-1.5 text-sm font-medium',
                statusFilter === f.id ? 'bg-brand-50 text-brand-700' : 'text-muted hover:text-ink',
              ].join(' ')}
            >
              {f.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-muted">
          <span className="whitespace-nowrap">Lead</span>
          <select
            value={leadFilter}
            onChange={(e) => setLeadFilter(e.target.value)}
            className="min-w-[12rem] rounded-lg border border-border bg-white px-3 py-2 text-sm text-ink outline-none focus:border-brand-500"
          >
            <option value="">All leads</option>
            {leads.map((lead) => (
              <option key={lead.id} value={lead.id}>
                {lead.fullName}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
          <button type="button" onClick={() => void refresh()} className="ml-3 font-medium underline">
            Retry
          </button>
        </div>
      ) : null}

      {loading && tasks.length === 0 ? (
        <div className="text-sm text-muted">Loading tasks…</div>
      ) : null}

      {!loading && !error && tasks.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
          <h3 className="text-base font-semibold text-ink">No tasks yet</h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Create a follow-up linked to a lead. Optional reminders are scheduled by the backend.
          </p>
          <button
            type="button"
            onClick={openCreate}
            disabled={leads.length === 0}
            className="mt-5 inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
          >
            <Plus className="h-4 w-4" />
            Add task
          </button>
          {leads.length === 0 && !leadsLoading ? (
            <p className="mt-3 text-xs text-muted">Create a lead first — tasks require a leadId.</p>
          ) : null}
        </div>
      ) : null}

      {tasks.length > 0 ? (
        <TaskTable
          tasks={tasks}
          onOpenTask={openTask}
          onCompleteTask={(t) => void handleComplete(t)}
          onCancelTask={setCancelTarget}
          onDeleteTask={setDeleteTarget}
          actionPendingId={actionPendingId}
        />
      ) : null}

      <TaskDetails
        open={detailsOpen}
        task={selected}
        actionPending={actionPendingId != null || confirmPending}
        actionError={actionError}
        onClose={() => {
          setDetailsOpen(false)
          setActionError(null)
        }}
        onEdit={openEdit}
        onComplete={(t) => void handleComplete(t)}
        onCancel={setCancelTarget}
        onDelete={setDeleteTarget}
      />

      <TaskForm
        open={formOpen}
        mode={formMode}
        task={formMode === 'edit' ? selected : null}
        leads={leads}
        leadsLoading={leadsLoading}
        pending={formPending}
        onClose={() => setFormOpen(false)}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
      />

      <ConfirmDialog
        open={cancelTarget != null}
        title="Cancel task?"
        message={
          cancelTarget
            ? `Mark “${cancelTarget.title}” as CANCELLED? Pending reminders will be superseded by the backend.`
            : ''
        }
        confirmLabel="Cancel task"
        danger
        pending={confirmPending}
        onCancel={() => setCancelTarget(null)}
        onConfirm={() => void handleCancelConfirm()}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        title="Delete task?"
        message={
          deleteTarget
            ? `Delete “${deleteTarget.title}”? This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        danger
        pending={confirmPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => void handleDeleteConfirm()}
      />
    </div>
  )
}
