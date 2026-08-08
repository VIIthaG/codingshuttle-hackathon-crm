export type TaskStatus = 'OPEN' | 'COMPLETED' | 'CANCELLED'

/** Matches backend TaskResponse */
export interface Task {
  id: string
  leadId: string
  leadName: string
  assignedToId: string
  assignedToName: string
  title: string
  description: string | null
  dueAt: string
  reminderAt: string | null
  status: TaskStatus
  createdAt: string
  updatedAt: string
}

/** Matches backend TaskCreateRequest */
export interface TaskCreateRequest {
  leadId: string
  assignedToId?: string | null
  title: string
  description?: string | null
  dueAt: string
  reminderAt?: string | null
}

/** Matches backend TaskUpdateRequest */
export interface TaskUpdateRequest {
  leadId: string
  assignedToId: string
  title: string
  description?: string | null
  dueAt: string
  reminderAt?: string | null
  status: TaskStatus
}
