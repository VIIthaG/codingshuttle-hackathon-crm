import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../types/api'
import type { Meeting, MeetingCreateRequest, MeetingUpdateRequest } from '../../types/meeting'
import type { RelatedRecordType } from '../../types/task'
import { formatApiError } from '../../utils/errors'
import { newIdempotencyKey } from '../../utils/idempotency'
import { defaultDueLocal, fromDatetimeLocalValue, toDatetimeLocalValue } from '../../utils/taskDates'
import { RelatedRecordFields } from '../crm/RelatedRecordFields'
import { AssigneeSelect } from '../crm/AssigneeSelect'
import { applyRelatedIds } from '../../utils/relatedRecords'
import type { TaskRelatedPreset } from '../tasks/TaskForm'

type MeetingFormProps = {
  open: boolean
  mode: 'create' | 'edit'
  meeting?: Meeting | null
  initialRelated?: TaskRelatedPreset | null
  pending?: boolean
  onClose: () => void
  onCreate: (body: MeetingCreateRequest, key: string) => Promise<void>
  onUpdate: (id: string, body: MeetingUpdateRequest) => Promise<void>
}

export function MeetingForm({
  open,
  mode,
  meeting,
  initialRelated,
  pending = false,
  onClose,
  onCreate,
  onUpdate,
}: MeetingFormProps) {
  const [relatedType, setRelatedType] = useState<RelatedRecordType>(initialRelated?.type ?? 'LEAD')
  const [relatedId, setRelatedId] = useState(initialRelated?.id ?? '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startLocal, setStartLocal] = useState(defaultDueLocal())
  const [endLocal, setEndLocal] = useState('')
  const [location, setLocation] = useState('')
  const [meetingUrl, setMeetingUrl] = useState('')
  const [assignedToId, setAssignedToId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const keyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setFieldErrors({})
    if (mode === 'edit' && meeting) {
      setRelatedType(meeting.relatedType)
      setRelatedId(meeting.relatedId)
      setTitle(meeting.title)
      setDescription(meeting.description ?? '')
      setStartLocal(toDatetimeLocalValue(meeting.startAt))
      setEndLocal(toDatetimeLocalValue(meeting.endAt))
      setLocation(meeting.location ?? '')
      setMeetingUrl(meeting.meetingUrl ?? '')
      setAssignedToId(meeting.assignedToId)
    } else {
      setRelatedType(initialRelated?.type ?? 'LEAD')
      setRelatedId(initialRelated?.id ?? '')
      setTitle('')
      setDescription('')
      setStartLocal(defaultDueLocal())
      setEndLocal('')
      setLocation('')
      setMeetingUrl('')
      setAssignedToId('')
      keyRef.current = newIdempotencyKey()
    }
  }, [open, mode, meeting, initialRelated])

  if (!open) return null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!relatedId) next.relatedId = 'Related record is required'
    if (!title.trim()) next.title = 'Title is required'
    if (!startLocal) next.startAt = 'Start is required'
    if (!endLocal) next.endAt = 'End is required'
    const startIso = fromDatetimeLocalValue(startLocal)
    const endIso = fromDatetimeLocalValue(endLocal)
    if (startIso && endIso && new Date(endIso) <= new Date(startIso)) {
      next.endAt = 'End must be after start'
    }
    setFieldErrors(next)
    if (Object.keys(next).length > 0) return
    try {
      if (mode === 'create') {
        if (!keyRef.current) keyRef.current = newIdempotencyKey()
        const body = applyRelatedIds(
          {
            title: title.trim(),
            description: description.trim() || null,
            startAt: startIso,
            endAt: endIso,
            location: location.trim() || null,
            meetingUrl: meetingUrl.trim() || null,
            assignedToId: assignedToId || null,
          },
          relatedType,
          relatedId,
        ) as MeetingCreateRequest
        if (!assignedToId) body.assignedToId = undefined
        await onCreate(body, keyRef.current)
      } else if (meeting) {
        const body = applyRelatedIds(
          {
            assignedToId: assignedToId || meeting.assignedToId,
            title: title.trim(),
            description: description.trim() || null,
            startAt: startIso,
            endAt: endIso,
            location: location.trim() || null,
            meetingUrl: meetingUrl.trim() || null,
          },
          relatedType,
          relatedId,
        ) as MeetingUpdateRequest
        await onUpdate(meeting.id, body)
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      setError(formatApiError(err, 'Failed to save meeting'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg">
        <h2 className="text-lg font-semibold text-ink">{mode === 'create' ? 'Add meeting' : 'Edit meeting'}</h2>
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
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={pending}
              className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
            />
            {fieldErrors.title ? <span className="text-xs text-red-600">{fieldErrors.title}</span> : null}
          </label>
          <label className="block text-sm font-medium text-ink">
            Description
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={pending}
              rows={3}
              className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
            />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-medium text-ink">
              Start *
              <input
                type="datetime-local"
                value={startLocal}
                onChange={(e) => setStartLocal(e.target.value)}
                disabled={pending}
                className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
            </label>
            <label className="block text-sm font-medium text-ink">
              End *
              <input
                type="datetime-local"
                value={endLocal}
                onChange={(e) => setEndLocal(e.target.value)}
                disabled={pending}
                className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
              {fieldErrors.endAt ? <span className="text-xs text-red-600">{fieldErrors.endAt}</span> : null}
            </label>
          </div>
          <label className="block text-sm font-medium text-ink">
            Location
            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              disabled={pending}
              className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
            />
          </label>
          <label className="block text-sm font-medium text-ink">
            Meeting URL
            <input
              value={meetingUrl}
              onChange={(e) => setMeetingUrl(e.target.value)}
              disabled={pending}
              className="mt-1.5 w-full rounded-lg border border-border px-3 py-2 text-sm"
            />
          </label>
          <AssigneeSelect
            open={open}
            value={assignedToId}
            onChange={setAssignedToId}
            disabled={pending}
            currentAssigneeName={mode === 'edit' ? meeting?.assignedToName : null}
          />
          {error ? <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div> : null}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="rounded-lg border border-border px-3 py-2 text-sm">
              Cancel
            </button>
            <button type="submit" disabled={pending} className="rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white">
              {pending ? 'Saving…' : mode === 'create' ? 'Create meeting' : 'Save changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
