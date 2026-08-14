import { useCallback, useEffect, useState } from 'react'
import { Plus, RefreshCw } from 'lucide-react'
import { changeCallStatus, createCall, deleteCall, listAllCalls, updateCall } from '../api/calls'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { CallDetails } from '../components/calls/CallDetails'
import { CallForm } from '../components/calls/CallForm'
import type { Call, CallStatus } from '../types/call'
import { RELATED_RECORD_TYPES, relatedTypeLabel, type RelatedRecordType } from '../types/task'
import { formatApiError } from '../utils/errors'
import { formatDateTime } from '../utils/taskDates'

export function CallsPage() {
  const [status, setStatus] = useState<CallStatus | ''>('PLANNED')
  const [relatedType, setRelatedType] = useState<RelatedRecordType | ''>('')
  const [rows, setRows] = useState<Call[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Call | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [pending, setPending] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Call | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await listAllCalls({ status: status || undefined, relatedType: relatedType || undefined }))
    } catch (err) {
      setError(formatApiError(err, 'Failed to load calls'))
    } finally {
      setLoading(false)
    }
  }, [status, relatedType])

  useEffect(() => { void refresh() }, [refresh])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          <select value={status} onChange={(e) => setStatus(e.target.value as CallStatus | '')} className="rounded-lg border border-border px-3 py-2 text-sm">
            <option value="">All statuses</option>
            <option value="PLANNED">Planned</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <select value={relatedType} onChange={(e) => setRelatedType(e.target.value as RelatedRecordType | '')} className="rounded-lg border border-border px-3 py-2 text-sm">
            <option value="">All types</option>
            {RELATED_RECORD_TYPES.map((t) => <option key={t} value={t}>{relatedTypeLabel(t)}</option>)}
          </select>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => void refresh()} className="rounded-lg border border-border px-3 py-2 text-sm"><RefreshCw className="mr-1 inline h-4 w-4" />Refresh</button>
          <button type="button" onClick={() => { setFormMode('create'); setFormOpen(true) }} className="rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white"><Plus className="mr-1 inline h-4 w-4" />Add call</button>
        </div>
      </div>
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      {loading ? <p className="text-sm text-muted">Loading…</p> : (
        <div className="overflow-x-auto rounded-xl border border-border bg-surface">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-border text-xs uppercase text-muted"><tr><th className="px-4 py-3">Title</th><th className="px-4 py-3">Related</th><th className="px-4 py-3">When</th><th className="px-4 py-3">Direction</th><th className="px-4 py-3">Status</th></tr></thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="cursor-pointer border-b border-border hover:bg-slate-50" onClick={() => setSelected(row)}>
                  <td className="px-4 py-3 font-medium">{row.title}</td>
                  <td className="px-4 py-3">{relatedTypeLabel(row.relatedType)} · {row.relatedName}</td>
                  <td className="px-4 py-3">{formatDateTime(row.scheduledAt)}</td>
                  <td className="px-4 py-3">{row.direction}</td>
                  <td className="px-4 py-3">{row.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {rows.length === 0 ? <p className="px-4 py-6 text-sm text-muted">No calls.</p> : null}
        </div>
      )}
      <CallDetails
        open={selected != null}
        call={selected}
        pending={pending}
        onClose={() => setSelected(null)}
        onEdit={(c) => { setSelected(c); setFormMode('edit'); setFormOpen(true) }}
        onComplete={async (c, outcome) => { setPending(true); try { await changeCallStatus(c.id, 'COMPLETED', outcome); setSelected(null); await refresh() } finally { setPending(false) } }}
        onCancel={async (c) => { setPending(true); try { await changeCallStatus(c.id, 'CANCELLED'); setSelected(null); await refresh() } finally { setPending(false) } }}
        onDelete={(c) => setDeleteTarget(c)}
      />
      <CallForm
        open={formOpen}
        mode={formMode}
        call={formMode === 'edit' ? selected : null}
        pending={pending}
        onClose={() => setFormOpen(false)}
        onCreate={async (body, key) => { setPending(true); try { await createCall(body, key); setFormOpen(false); await refresh() } finally { setPending(false) } }}
        onUpdate={async (id, body) => { setPending(true); try { await updateCall(id, body); setFormOpen(false); await refresh() } finally { setPending(false) } }}
      />
      <ConfirmDialog open={deleteTarget != null} title="Delete call?" message={deleteTarget ? `Delete “${deleteTarget.title}”?` : ''} confirmLabel="Delete" danger pending={pending} onCancel={() => setDeleteTarget(null)} onConfirm={async () => { if (!deleteTarget) return; setPending(true); try { await deleteCall(deleteTarget.id); setDeleteTarget(null); setSelected(null); await refresh() } finally { setPending(false) } }} />
    </div>
  )
}
