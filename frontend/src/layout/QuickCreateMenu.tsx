import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { listAllAccounts, createAccount } from '../api/accounts'
import { createCall } from '../api/calls'
import { createContact } from '../api/contacts'
import { createDeal } from '../api/deals'
import { createLead } from '../api/leads'
import { createMeeting } from '../api/meetings'
import { createTask } from '../api/tasks'
import { listUsers } from '../api/users'
import { useAuth } from '../auth/useAuth'
import { AccountForm } from '../components/accounts/AccountForm'
import { CallForm } from '../components/calls/CallForm'
import { ContactForm } from '../components/contacts/ContactForm'
import { DealForm } from '../components/deals/DealForm'
import { LeadForm } from '../components/leads/LeadForm'
import { MeetingForm } from '../components/meetings/MeetingForm'
import { TaskForm } from '../components/tasks/TaskForm'
import type { Account } from '../types/account'
import type { User } from '../types/auth'
import { searchPath, type SearchResultType } from '../types/search'

type Kind = SearchResultType

const OPTIONS: { kind: Kind; label: string }[] = [
  { kind: 'LEAD', label: 'Lead' },
  { kind: 'ACCOUNT', label: 'Account' },
  { kind: 'CONTACT', label: 'Contact' },
  { kind: 'DEAL', label: 'Deal' },
  { kind: 'TASK', label: 'Task' },
  { kind: 'MEETING', label: 'Meeting' },
  { kind: 'CALL', label: 'Call' },
]

export function QuickCreateMenu({ onCreated }: { onCreated: (message: string) => void }) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [menuOpen, setMenuOpen] = useState(false)
  const [kind, setKind] = useState<Kind | null>(null)
  const [pending, setPending] = useState(false)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [users, setUsers] = useState<User[]>([])

  useEffect(() => {
    if (!kind) return
    void listAllAccounts().then(setAccounts).catch(() => setAccounts([]))
    if (isAdmin) {
      void listUsers().then(setUsers).catch(() => setUsers([]))
    }
  }, [kind, isAdmin])

  function finish(type: Kind, id: string, title: string) {
    setKind(null)
    setMenuOpen(false)
    onCreated(`${title} created`)
    navigate(searchPath(type, id))
  }

  return (
    <div className="relative">
      <button
        type="button"
        className="inline-flex items-center gap-1 rounded-lg bg-brand-600 px-2.5 py-2 text-sm font-semibold text-white hover:bg-brand-700 sm:px-3"
        aria-label="Create"
        onClick={() => setMenuOpen((v) => !v)}
      >
        <Plus className="h-4 w-4" />
        <span className="hidden sm:inline">Create</span>
      </button>
      {menuOpen && kind == null ? (
        <div className="absolute right-0 z-40 mt-2 w-40 overflow-hidden rounded-xl border border-border bg-white py-1 shadow-lg">
          {OPTIONS.map((opt) => (
            <button
              key={opt.kind}
              type="button"
              className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
              onClick={() => {
                setKind(opt.kind)
                setMenuOpen(false)
              }}
            >
              {opt.label}
            </button>
          ))}
        </div>
      ) : null}

      <LeadForm
        open={kind === 'LEAD'}
        mode="create"
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createLead(body, key)
            finish('LEAD', created.id, created.fullName)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <AccountForm
        open={kind === 'ACCOUNT'}
        mode="create"
        users={users}
        isAdmin={isAdmin}
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createAccount(body, key)
            finish('ACCOUNT', created.id, created.name)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <ContactForm
        open={kind === 'CONTACT'}
        mode="create"
        accounts={accounts}
        users={users}
        isAdmin={isAdmin}
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createContact(body, key)
            finish('CONTACT', created.id, `${created.firstName} ${created.lastName}`)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <DealForm
        open={kind === 'DEAL'}
        mode="create"
        accounts={accounts}
        users={users}
        isAdmin={isAdmin}
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createDeal(body, key)
            finish('DEAL', created.id, created.name)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <TaskForm
        open={kind === 'TASK'}
        mode="create"
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createTask(body, key)
            finish('TASK', created.id, created.title)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <MeetingForm
        open={kind === 'MEETING'}
        mode="create"
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createMeeting(body, key)
            finish('MEETING', created.id, created.title)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
      <CallForm
        open={kind === 'CALL'}
        mode="create"
        pending={pending}
        onClose={() => setKind(null)}
        onCreate={async (body, key) => {
          setPending(true)
          try {
            const created = await createCall(body, key)
            finish('CALL', created.id, created.title)
          } finally {
            setPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />
    </div>
  )
}
