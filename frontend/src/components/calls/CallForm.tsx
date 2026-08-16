import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Call, CallCreateRequest, CallDirection, CallUpdateRequest } from '../../types/call'
import { ApiError } from '../../types/api'
import type { RelatedRecordType } from '../../types/task'
import { formatApiError } from '../../utils/errors'
import { newIdempotencyKey } from '../../utils/idempotency'
import { defaultDueLocal, fromDatetimeLocalValue, toDatetimeLocalValue } from '../../utils/taskDates'
import { RelatedRecordFields } from '../crm/RelatedRecordFields'
import { AssigneeSelect } from '../crm/AssigneeSelect'
import { applyRelatedIds } from '../../utils/relatedRecords'
import type { TaskRelatedPreset } from '../tasks/TaskForm'

type CallFormProps = {
  open: boolean
  mode: 'create' | 'edit'
  call?: Call | null
  initialRelated?: TaskRelatedPreset | null
  pending?: boolean
  onClose: () => void
  onCreate: (body: CallCreateRequest, key: string) => Promise<void>
  onUpdate: (id: string, body: CallUpdateRequest) => Promise<void>
}

export function CallForm({
  open,
  mode,
  call,
  initialRelated,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: CallFormProps) {
  const [relatedType, setRelatedType] = useState<RelatedRecordType>(initialRelated?.type ?? 'LEAD')
  const [relatedId, setRelatedId] = useState(initialRelated?.id ?? '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [scheduledLocal, setScheduledLocal] = useState(defaultDueLocal())
  const [duration, setDuration] = useState('')
  const [direction, setDirection] = useState<CallDirection>('OUTBOUND')
  const [phone, setPhone] = useState('')
  const [assignedToId, setAssignedToId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const keyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && call) {
      setRelatedType(call.relatedType)
      setRelatedId(call.relatedId)
      setTitle(call.title)
      setDescription(call.description ?? '')
      setScheduledLocal(toDatetimeLocalValue(call.scheduledAt))
      setDuration(call.durationMinutes != null ? String(call.durationMinutes) : '')
      setDirection(call.direction)
      setPhone(call.phoneNumber ?? '')
      setAssignedToId(call.assignedToId)
    } else {
      setRelatedType(initialRelated?.type ?? 'LEAD')
      setRelatedId(initialRelated?.id ?? '')
      setTitle('')
      setDescription('')
      setScheduledLocal(defaultDueLocal())
      setDuration('')
      setDirection('OUTBOUND')
      setPhone('')
      setAssignedToId('')
      keyRef.current = newIdempotencyKey()
    }
  }, [open, mode, call, initialRelated])

  if (!open) return null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!relatedId) next.relatedId = 'Related record is required'
    if (!title.trim()) next.title = 'Title is required'
    if (!scheduledLocal) next.scheduledAt = 'Scheduled time is required'
    setFieldErrors(next)
    if (Object.keys(next).length > 0) return
    const durationMinutes = duration.trim() === '' ? null : Number(duration)
    try {
      if (mode === 'create') {
        if (!keyRef.current) keyRef.current = newIdempotencyKey()
        const body = applyRelatedIds(
          {
            title: title.trim(),
            description: description.trim() || null,
            scheduledAt: fromDatetimeLocalValue(scheduledLocal),
            durationMinutes,
            direction,
            phoneNumber: phone.trim() || null,
            assignedToId: assignedToId || null,
          },
          relatedType,
          relatedId,
        ) as CallCreateRequest
        if (!assignedToId) body.assignedToId = undefined
        await onCreate(body, keyRef.current)
      } else if (call) {
        const body = applyRelatedIds(
          {
            assignedToId: assignedToId || call.assignedToId,
            title: title.trim(),
            description: description.trim() || null,
            scheduledAt: fromDatetimeLocalValue(scheduledLocal),
            durationMinutes,
            direction,
            phoneNumber: phone.trim() || null,
            outcome: call.outcome,
          },
          relatedType,
          relatedId,
        ) as CallUpdateRequest
        await onUpdate(call.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      setError(formatApiError(err, 'Failed to save call'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overlay-backdrop p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg">
        <h2 className="text-lg font-semibold text-ink">{mode === 'create' ? 'Add call' : 'Edit call'}</h2>
        <form className="mt-5 space-y-4" onSubmit={onSubmit} noValidate>
          <RelatedRecordFields
            relatedType={relatedType}
            relatedId={relatedId}
            disabled={pending}
            error={fieldErrors.relatedId}
            onTypeChange={(type) => {
              setRelatedType(type)
              setRelatedId('')
            }}
            onIdChange={setRelatedId}
          />
          <label className="block text-sm font-medium text-ink">
            Title *
            <input value={title} onChange={(e) => setTitle(e.target.value)} disabled={pending} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm" />
          </label>
          <label className="block text-sm font-medium text-ink">
            Direction
            <select value={direction} onChange={(e) => setDirection(e.target.value as CallDirection)} disabled={pending} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm">
              <option value="OUTBOUND">Outbound</option>
              <option value="INBOUND">Inbound</option>
            </select>
          </label>
          <label className="block text-sm font-medium text-ink">
            Scheduled *
            <input type="datetime-local" value={scheduledLocal} onChange={(e) => setScheduledLocal(e.target.value)} disabled={pending} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm" />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-medium text-ink">
              Duration (minutes)
              <input value={duration} onChange={(e) => setDuration(e.target.value)} disabled={pending} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm" />
            </label>
            <label className="block text-sm font-medium text-ink">
              Phone
              <input value={phone} onChange={(e) => setPhone(e.target.value)} disabled={pending} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm" />
            </label>
          </div>
          <label className="block text-sm font-medium text-ink">
            Notes
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} disabled={pending} rows={3} className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm" />
          </label>
          <AssigneeSelect
            open={open}
            value={assignedToId}
            onChange={setAssignedToId}
            disabled={pending}
            currentAssigneeName={mode === 'edit' ? call?.assignedToName : null}
          />
          {error ? <div className="alert alert-error">{error}</div> : null}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="rounded-lg border border-border px-3 py-2 text-sm">Cancel</button>
            <button type="submit" disabled={pending} className="rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white">
              {pending ? 'Saving…' : mode === 'create' ? 'Create call' : 'Save changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
