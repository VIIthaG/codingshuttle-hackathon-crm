import { useCallback, useEffect, useState } from 'react'
import { LayoutGrid, List, Plus, RefreshCw } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import {
  changeLeadStatus,
  createLead,
  deleteLead,
  getLead,
  listAllLeads,
  updateLead,
} from '../api/leads'
import { useAuth } from '../auth/useAuth'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ConvertLeadModal } from '../components/leads/ConvertLeadModal'
import { LeadDetails } from '../components/leads/LeadDetails'
import { RecordActivityModals, type ActivityKind } from '../components/crm/RecordActivityModals'
import { type TaskRelatedPreset } from '../components/tasks/TaskForm'
import { LeadForm } from '../components/leads/LeadForm'
import { LeadPipeline } from '../components/leads/LeadPipeline'
import { LeadTable } from '../components/leads/LeadTable'
import type { Lead, LeadCreateRequest, LeadStatus, LeadUpdateRequest } from '../types/lead'
import { formatApiError } from '../utils/errors'

type ViewMode = 'pipeline' | 'list'

export function LeadsPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const openId = searchParams.get('open')
  const [view, setView] = useState<ViewMode>('pipeline')
  const [leads, setLeads] = useState<Lead[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selected, setSelected] = useState<Lead | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [formPending, setFormPending] = useState(false)

  const [statusPending, setStatusPending] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = useState<Lead | null>(null)
  const [deletePending, setDeletePending] = useState(false)

  const [convertTarget, setConvertTarget] = useState<Lead | null>(null)
  const [taskPreset, setTaskPreset] = useState<TaskRelatedPreset | null>(null)
  const [activityKind, setActivityKind] = useState<ActivityKind | null>(null)
  const [activityKey, setActivityKey] = useState(0)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listAllLeads()
      setLeads(data)
      setSelected((prev) => {
        if (!prev) return null
        return data.find((l) => l.id === prev.id) ?? null
      })
    } catch (err) {
      setError(formatApiError(err, 'Failed to load leads'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!openId) return
    let cancelled = false
    void getLead(openId)
      .then(async (lead) => {
        if (cancelled) return
        setSelected(lead)
        setStatusError(null)
        setDetailsOpen(true)
        setLeads((prev) => {
          const exists = prev.some((row) => row.id === lead.id)
          if (exists) return prev.map((row) => (row.id === lead.id ? lead : row))
          return [lead, ...prev]
        })
        await refresh()
        if (cancelled) return
        setSelected(lead)
        setDetailsOpen(true)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [openId, refresh])

  function openLead(lead: Lead) {
    setSelected(lead)
    setStatusError(null)
    setDetailsOpen(true)
  }

  function openCreate() {
    setFormMode('create')
    setFormOpen(true)
  }

  function openEdit(lead: Lead) {
    setSelected(lead)
    setFormMode('edit')
    setFormOpen(true)
  }

  async function handleCreate(body: LeadCreateRequest, idempotencyKey: string) {
    setFormPending(true)
    try {
      const created = await createLead(body, idempotencyKey)
      // Optimistic local insert; full refresh keeps pagination/order consistent.
      setLeads((prev) => [created, ...prev.filter((l) => l.id !== created.id)])
      setFormOpen(false)
      openLead(created)
      void refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleUpdate(id: string, body: LeadUpdateRequest) {
    setFormPending(true)
    try {
      const updated = await updateLead(id, body)
      setLeads((prev) => prev.map((l) => (l.id === updated.id ? updated : l)))
      setSelected(updated)
      setFormOpen(false)
      setDetailsOpen(true)
      void refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleStatusChange(lead: Lead, status: LeadStatus) {
    setStatusPending(true)
    setStatusError(null)
    try {
      const updated = await changeLeadStatus(lead.id, { status })
      setSelected(updated)
      setLeads((prev) => prev.map((l) => (l.id === updated.id ? updated : l)))
    } catch (err) {
      setStatusError(formatApiError(err, 'Could not change lead status'))
    } finally {
      setStatusPending(false)
    }
  }

  async function handleDeleteConfirm() {
    if (!deleteTarget) return
    setDeletePending(true)
    try {
      await deleteLead(deleteTarget.id)
      setDeleteTarget(null)
      if (selected?.id === deleteTarget.id) {
        setDetailsOpen(false)
        setSelected(null)
      }
      await refresh()
    } catch (err) {
      setError(formatApiError(err, 'Failed to delete lead'))
      setDeleteTarget(null)
    } finally {
      setDeletePending(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink">Leads</h2>
          <p className="mt-1 max-w-xl text-sm text-muted">
            Manage your pipeline and contact details.
            {user?.role === 'SALES_REP'
              ? ' You see leads assigned to you.'
              : ' Admins see all leads across the team.'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex rounded-lg border border-border bg-surface p-0.5 shadow-sm">
            <button
              type="button"
              onClick={() => setView('pipeline')}
              className={[
                'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium',
                view === 'pipeline' ? 'bg-brand-50 text-brand-700' : 'text-muted hover:text-ink',
              ].join(' ')}
            >
              <LayoutGrid className="h-4 w-4" />
              Pipeline
            </button>
            <button
              type="button"
              onClick={() => setView('list')}
              className={[
                'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium',
                view === 'list' ? 'bg-brand-50 text-brand-700' : 'text-muted hover:text-ink',
              ].join(' ')}
            >
              <List className="h-4 w-4" />
              List
            </button>
          </div>
          <button
            type="button"
            onClick={() => void refresh()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 btn btn-secondary disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            type="button"
            onClick={openCreate}
            className="inline-flex items-center gap-1.5 btn btn-primary"
          >
            <Plus className="h-4 w-4" />
            Add lead
          </button>
        </div>
      </div>

      {error ? (
        <div className="alert alert-error">
          {error}
          <button
            type="button"
            onClick={() => void refresh()}
            className="ml-3 font-medium underline"
          >
            Retry
          </button>
        </div>
      ) : null}

      {loading && leads.length === 0 ? (
        <div className="text-sm text-muted">Loading leads…</div>
      ) : null}

      {!loading && !error && leads.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
          <h3 className="text-base font-semibold text-ink">No leads yet</h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Add a lead to start filling your pipeline. New leads land in the NEW column.
          </p>
          <button
            type="button"
            onClick={openCreate}
            className="mt-5 inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Add lead
          </button>
        </div>
      ) : null}

      {leads.length > 0 ? (
        view === 'pipeline' ? (
          <LeadPipeline leads={leads} onOpenLead={openLead} />
        ) : (
          <LeadTable
            leads={leads}
            onOpenLead={openLead}
            onDeleteLead={(lead) => setDeleteTarget(lead)}
            onConvertLead={(lead) => setConvertTarget(lead)}
          />
        )
      ) : null}

      <LeadDetails
        open={detailsOpen}
        lead={selected}
        statusPending={statusPending}
        statusError={statusError}
        onClose={() => {
          setDetailsOpen(false)
          setStatusError(null)
        }}
        onEdit={(lead) => {
          openEdit(lead)
        }}
        onDelete={(lead) => setDeleteTarget(lead)}
        onChangeStatus={handleStatusChange}
        onConvert={(lead) => setConvertTarget(lead)}
        onAddTask={(lead) => {
          setTaskPreset({ type: 'LEAD', id: lead.id })
          setActivityKind('task')
        }}
        onAddMeeting={(lead) => {
          setTaskPreset({ type: 'LEAD', id: lead.id })
          setActivityKind('meeting')
        }}
        onAddCall={(lead) => {
          setTaskPreset({ type: 'LEAD', id: lead.id })
          setActivityKind('call')
        }}
        activityRefreshKey={activityKey}
      />

      <ConvertLeadModal
        open={convertTarget != null}
        lead={convertTarget}
        onClose={() => setConvertTarget(null)}
        onConverted={(converted) => {
          setLeads((prev) => prev.map((l) => (l.id === converted.id ? converted : l)))
          setSelected(converted)
          setDetailsOpen(true)
        }}
      />

      <RecordActivityModals
        kind={activityKind}
        preset={taskPreset}
        pending={false}
        onClose={() => setActivityKind(null)}
        onCreated={async () => {
          setActivityKind(null)
          setActivityKey((k) => k + 1)
        }}
      />

      <LeadForm
        open={formOpen}
        mode={formMode}
        lead={formMode === 'edit' ? selected : null}
        pending={formPending}
        onClose={() => setFormOpen(false)}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        title="Delete lead?"
        message={
          deleteTarget
            ? `Delete “${deleteTarget.fullName}”? This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        danger
        pending={deletePending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => void handleDeleteConfirm()}
      />
    </div>
  )
}
