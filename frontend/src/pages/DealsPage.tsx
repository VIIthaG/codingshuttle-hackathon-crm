import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Handshake, LayoutGrid, List, Plus, RefreshCw, Search } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { listAllAccounts } from '../api/accounts'
import {
  changeDealStage,
  createDeal,
  deleteDeal,
  getDeal,
  listAllDeals,
  listDeals,
  updateDeal,
} from '../api/deals'
import { listUsers } from '../api/users'
import { useAuth } from '../auth/useAuth'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DealDetails } from '../components/deals/DealDetails'
import { DealForm } from '../components/deals/DealForm'
import { DealPipeline } from '../components/deals/DealPipeline'
import { DealTable } from '../components/deals/DealTable'
import { RecordActivityModals, type ActivityKind } from '../components/crm/RecordActivityModals'
import { type TaskRelatedPreset } from '../components/tasks/TaskForm'
import type { Account } from '../types/account'
import type { User } from '../types/auth'
import type { Deal, DealCreateRequest, DealStage, DealUpdateRequest } from '../types/deal'
import { DEAL_STAGE_ORDER, formatDealStage } from '../utils/dealTransitions'
import { formatApiError } from '../utils/errors'

type ViewMode = 'pipeline' | 'list'

export function DealsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [searchParams] = useSearchParams()
  const openId = searchParams.get('open')
  const [view, setView] = useState<ViewMode>('pipeline')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [stageFilter, setStageFilter] = useState<DealStage | ''>('')
  const [accountFilter, setAccountFilter] = useState('')
  const [ownerFilter, setOwnerFilter] = useState('')
  const [page, setPage] = useState(0)

  const [deals, setDeals] = useState<Deal[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [users, setUsers] = useState<User[]>([])

  const [selected, setSelected] = useState<Deal | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [formPending, setFormPending] = useState(false)
  const [stagePending, setStagePending] = useState(false)
  const [stageError, setStageError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Deal | null>(null)
  const [deletePending, setDeletePending] = useState(false)
  const [taskPreset, setTaskPreset] = useState<TaskRelatedPreset | null>(null)
  const [activityKind, setActivityKind] = useState<ActivityKind | null>(null)
  const [activityKey, setActivityKey] = useState(0)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const filters = {
        search: search || undefined,
        stage: stageFilter || undefined,
        accountId: accountFilter || undefined,
        ownerId: isAdmin && ownerFilter ? ownerFilter : undefined,
      }
      if (view === 'pipeline') {
        const data = await listAllDeals({ ...filters, sort: 'updatedAt,desc' })
        setDeals(data)
        setTotalPages(1)
        setTotalElements(data.length)
        setSelected((prev) => (prev ? data.find((d) => d.id === prev.id) ?? prev : null))
      } else {
        const data = await listDeals({ ...filters, page, size: 20, sort: 'updatedAt,desc' })
        setDeals(data.content)
        setTotalPages(data.totalPages)
        setTotalElements(data.totalElements)
        setSelected((prev) => (prev ? data.content.find((d) => d.id === prev.id) ?? prev : null))
      }
    } catch (err) {
      setError(formatApiError(err, 'Failed to load deals'))
    } finally {
      setLoading(false)
    }
  }, [search, stageFilter, accountFilter, ownerFilter, isAdmin, view, page])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!openId) return
    let cancelled = false
    void getDeal(openId)
      .then((deal) => {
        if (cancelled) return
        setSelected(deal)
        setDetailsOpen(true)
        setStageError(null)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [openId])

  useEffect(() => {
    void listAllAccounts()
      .then(setAccounts)
      .catch(() => setAccounts([]))
  }, [])

  useEffect(() => {
    if (!isAdmin) return
    void listUsers()
      .then(setUsers)
      .catch(() => setUsers([]))
  }, [isAdmin])

  function applySearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  function openDeal(deal: Deal) {
    setSelected(deal)
    setStageError(null)
    setDetailsOpen(true)
  }

  async function handleCreate(body: DealCreateRequest, key: string) {
    setFormPending(true)
    try {
      const created = await createDeal(body, key)
      setFormOpen(false)
      setSelected(created)
      setDetailsOpen(true)
      await refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleUpdate(id: string, body: DealUpdateRequest) {
    setFormPending(true)
    try {
      const updated = await updateDeal(id, body)
      setFormOpen(false)
      setSelected(updated)
      await refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleStageChange(deal: Deal, stage: DealStage, lostReason?: string | null) {
    setStagePending(true)
    setStageError(null)
    try {
      const updated = await changeDealStage(deal.id, {
        stage,
        lostReason: lostReason || undefined,
      })
      setSelected(updated)
      await refresh()
    } catch (err) {
      setStageError(formatApiError(err, 'Failed to change stage'))
    } finally {
      setStagePending(false)
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return
    setDeletePending(true)
    try {
      await deleteDeal(deleteTarget.id)
      if (selected?.id === deleteTarget.id) {
        setSelected(null)
        setDetailsOpen(false)
      }
      setDeleteTarget(null)
      await refresh()
    } catch (err) {
      setError(formatApiError(err, 'Failed to delete deal'))
      setDeleteTarget(null)
    } finally {
      setDeletePending(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-semibold text-ink">Deals</h2>
          <p className="mt-1 text-sm text-muted">
            {totalElements} deal{totalElements === 1 ? '' : 's'} · pipeline stages are validated by the API
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex rounded-lg border border-border bg-white p-0.5">
            <button
              type="button"
              onClick={() => {
                setView('pipeline')
                setPage(0)
              }}
              className={[
                'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium',
                view === 'pipeline' ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-50',
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
                view === 'list' ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-50',
              ].join(' ')}
            >
              <List className="h-4 w-4" />
              Table
            </button>
          </div>
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
            onClick={() => {
              setFormMode('create')
              setFormOpen(true)
            }}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Add deal
          </button>
        </div>
      </div>

      <form onSubmit={applySearch} className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end">
        <label className="min-w-[12rem] flex-1">
          <span className="mb-1 block text-xs font-medium text-muted">Search</span>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted" />
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Deal, account, or contact"
              className="w-full rounded-lg border border-border py-2 pl-9 pr-3 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
          </div>
        </label>
        <label className="w-full sm:w-44">
          <span className="mb-1 block text-xs font-medium text-muted">Stage</span>
          <select
            value={stageFilter}
            onChange={(e) => {
              setPage(0)
              setStageFilter((e.target.value || '') as DealStage | '')
            }}
            className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm"
          >
            <option value="">All stages</option>
            {DEAL_STAGE_ORDER.map((stage) => (
              <option key={stage} value={stage}>
                {formatDealStage(stage)}
              </option>
            ))}
          </select>
        </label>
        <label className="w-full sm:w-52">
          <span className="mb-1 block text-xs font-medium text-muted">Account</span>
          <select
            value={accountFilter}
            onChange={(e) => {
              setPage(0)
              setAccountFilter(e.target.value)
            }}
            className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm"
          >
            <option value="">All accounts</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </label>
        {isAdmin ? (
          <label className="w-full sm:w-52">
            <span className="mb-1 block text-xs font-medium text-muted">Owner</span>
            <select
              value={ownerFilter}
              onChange={(e) => {
                setPage(0)
                setOwnerFilter(e.target.value)
              }}
              className="w-full rounded-lg border border-border bg-white px-3 py-2 text-sm"
            >
              <option value="">All owners</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        <button
          type="submit"
          className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Apply
        </button>
      </form>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
          <button type="button" onClick={() => void refresh()} className="ml-3 font-medium underline">
            Retry
          </button>
        </div>
      ) : null}

      {loading && deals.length === 0 ? <div className="text-sm text-muted">Loading deals…</div> : null}

      {!loading && !error && deals.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
          <Handshake className="mx-auto h-8 w-8 text-muted" />
          <h3 className="mt-3 text-base font-semibold text-ink">No deals yet</h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Create a deal against an account to start the sales pipeline.
          </p>
          <button
            type="button"
            onClick={() => {
              setFormMode('create')
              setFormOpen(true)
            }}
            className="mt-5 inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Add deal
          </button>
        </div>
      ) : null}

      {deals.length > 0 ? (
        view === 'pipeline' ? (
          <DealPipeline
            deals={deals}
            stagePending={stagePending}
            onOpenDeal={openDeal}
            onAdvance={(deal, stage) => void handleStageChange(deal, stage)}
          />
        ) : (
          <>
            <DealTable
              deals={deals}
              onOpenDeal={openDeal}
              onDeleteDeal={(deal) => setDeleteTarget(deal)}
            />
            {totalPages > 1 ? (
              <div className="flex items-center justify-between text-sm text-muted">
                <span>
                  Page {page + 1} of {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    className="rounded-lg border border-border bg-white px-3 py-1.5 disabled:opacity-50"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                    className="rounded-lg border border-border bg-white px-3 py-1.5 disabled:opacity-50"
                  >
                    Next
                  </button>
                </div>
              </div>
            ) : null}
          </>
        )
      ) : null}

      <DealDetails
        open={detailsOpen}
        deal={selected}
        stagePending={stagePending}
        stageError={stageError}
        onClose={() => {
          setDetailsOpen(false)
          setStageError(null)
        }}
        onEdit={(deal) => {
          setSelected(deal)
          setFormMode('edit')
          setFormOpen(true)
        }}
        onDelete={(deal) => setDeleteTarget(deal)}
        onChangeStage={(deal, stage, lostReason) => void handleStageChange(deal, stage, lostReason)}
        onAddTask={(deal) => {
          setTaskPreset({ type: 'DEAL', id: deal.id })
          setActivityKind('task')
        }}
        onAddMeeting={(deal) => {
          setTaskPreset({ type: 'DEAL', id: deal.id })
          setActivityKind('meeting')
        }}
        onAddCall={(deal) => {
          setTaskPreset({ type: 'DEAL', id: deal.id })
          setActivityKind('call')
        }}
        activityRefreshKey={activityKey}
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

      <DealForm
        open={formOpen}
        mode={formMode}
        deal={formMode === 'edit' ? selected : null}
        accounts={accounts}
        users={users}
        isAdmin={isAdmin}
        pending={formPending}
        onClose={() => setFormOpen(false)}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        title="Delete deal?"
        message={deleteTarget ? `Delete “${deleteTarget.name}”? This cannot be undone.` : ''}
        confirmLabel="Delete"
        danger
        pending={deletePending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
