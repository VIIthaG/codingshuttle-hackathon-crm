import { createCall } from '../../api/calls'
import { createMeeting } from '../../api/meetings'
import { createTask } from '../../api/tasks'
import { CallForm } from '../calls/CallForm'
import { MeetingForm } from '../meetings/MeetingForm'
import { TaskForm, type TaskRelatedPreset } from '../tasks/TaskForm'

export type ActivityKind = 'task' | 'meeting' | 'call'

export function RecordActivityModals({
  kind,
  preset,
  pending,
  onClose,
  onCreated,
}: {
  kind: ActivityKind | null
  preset: TaskRelatedPreset | null
  pending: boolean
  onClose: () => void
  onCreated: () => Promise<void> | void
}) {
  return (
    <>
      <TaskForm
        open={kind === 'task'}
        mode="create"
        initialRelated={preset}
        pending={pending}
        onClose={onClose}
        onCreate={async (body, key) => {
          await createTask(body, key)
          await onCreated()
        }}
        onUpdate={async () => undefined}
      />
      <MeetingForm
        open={kind === 'meeting'}
        mode="create"
        initialRelated={preset}
        pending={pending}
        onClose={onClose}
        onCreate={async (body, key) => {
          await createMeeting(body, key)
          await onCreated()
        }}
        onUpdate={async () => undefined}
      />
      <CallForm
        open={kind === 'call'}
        mode="create"
        initialRelated={preset}
        pending={pending}
        onClose={onClose}
        onCreate={async (body, key) => {
          await createCall(body, key)
          await onCreated()
        }}
        onUpdate={async () => undefined}
      />
    </>
  )
}
