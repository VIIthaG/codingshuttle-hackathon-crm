import { useEffect, useState } from 'react'
import { listUsers } from '../../api/users'
import { useAuth } from '../../auth/useAuth'
import type { User } from '../../types/auth'

export function AssigneeSelect({
  open,
  value,
  onChange,
  disabled,
  currentAssigneeName,
}: {
  open: boolean
  value: string
  onChange: (id: string) => void
  disabled?: boolean
  currentAssigneeName?: string | null
}) {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [users, setUsers] = useState<User[]>([])

  useEffect(() => {
    if (!open || !isAdmin) return
    let cancelled = false
    void listUsers()
      .then((rows) => {
        if (!cancelled) setUsers(rows)
      })
      .catch(() => {
        if (!cancelled) setUsers([])
      })
    return () => {
      cancelled = true
    }
  }, [open, isAdmin])

  if (isAdmin) {
    return (
      <label className="block text-sm font-medium text-ink">
        Assigned to
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="mt-1.5 ui-input"
        >
          <option value="">Current user (default)</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.fullName} ({u.role === 'ADMIN' ? 'Admin' : 'Sales Rep'})
            </option>
          ))}
        </select>
      </label>
    )
  }

  if (currentAssigneeName) {
    return (
      <p className="text-sm text-muted">
        Assigned to <span className="font-medium text-ink">{currentAssigneeName}</span>
      </p>
    )
  }

  return null
}
