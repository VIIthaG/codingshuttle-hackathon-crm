import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Plus, RefreshCw, Search, UserRound } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { listAllAccounts } from '../api/accounts'
import { createContact, deleteContact, getContact, listContacts, updateContact } from '../api/contacts'
import { listUsers } from '../api/users'
import { useAuth } from '../auth/useAuth'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ContactDetails } from '../components/contacts/ContactDetails'
import { ContactForm } from '../components/contacts/ContactForm'
import { TaskForm, type TaskRelatedPreset } from '../components/tasks/TaskForm'
import { createTask } from '../api/tasks'
import type { TaskCreateRequest } from '../types/task'
import type { Account } from '../types/account'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'
import type { User } from '../types/auth'
import { formatApiError } from '../utils/errors'

export function ContactsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [searchParams] = useSearchParams()
  const openId = searchParams.get('open')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [accountFilter, setAccountFilter] = useState('')
  const [page, setPage] = useState(0)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selected, setSelected] = useState<Contact | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [formPending, setFormPending] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Contact | null>(null)
  const [deletePending, setDeletePending] = useState(false)
  const [taskPreset, setTaskPreset] = useState<TaskRelatedPreset | null>(null)
  const [taskFormOpen, setTaskFormOpen] = useState(false)
  const [taskFormPending, setTaskFormPending] = useState(false)
  const [activityKey, setActivityKey] = useState(0)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listContacts({
        search,
        accountId: accountFilter || undefined,
        page,
        size: 20,
        sort: 'lastName,asc',
      })
      setContacts(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
      setSelected((prev) => (prev ? data.content.find((c) => c.id === prev.id) ?? prev : null))
    } catch (err) {
      setError(formatApiError(err, 'Failed to load contacts'))
    } finally {
      setLoading(false)
    }
  }, [search, accountFilter, page])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!openId) return
    let cancelled = false
    void getContact(openId)
      .then((contact) => {
        if (cancelled) return
        setSelected(contact)
        setDetailsOpen(true)
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

  async function handleCreate(body: ContactCreateRequest, key: string) {
    setFormPending(true)
    try {
      const created = await createContact(body, key)
      setFormOpen(false)
      setSelected(created)
      setDetailsOpen(true)
      await refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleUpdate(id: string, body: ContactUpdateRequest) {
    setFormPending(true)
    try {
      const updated = await updateContact(id, body)
      setFormOpen(false)
      setSelected(updated)
      await refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return
    setDeletePending(true)
    try {
      await deleteContact(deleteTarget.id)
      if (selected?.id === deleteTarget.id) {
        setDetailsOpen(false)
        setSelected(null)
      }
      setDeleteTarget(null)
      await refresh()
    } catch (err) {
      setError(formatApiError(err, 'Failed to delete contact'))
      setDeleteTarget(null)
    } finally {
      setDeletePending(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink">Contacts</h2>
          <p className="mt-1 max-w-xl text-sm text-muted">
            People at your accounts — or standalone contacts.
            {isAdmin ? ' Admins see everyone and can reassign owners.' : ' You see contacts you own.'}
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
            onClick={() => {
              setFormMode('create')
              setFormOpen(true)
            }}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Add contact
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <form onSubmit={applySearch} className="flex max-w-md flex-1 gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search name, email, phone, title"
              className="w-full rounded-lg border border-border bg-white py-2 pl-9 pr-3 text-sm outline-none focus:border-brand-500"
            />
          </div>
          <button
            type="submit"
            className="rounded-lg border border-border bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Search
          </button>
        </form>
        <label className="flex items-center gap-2 text-sm text-muted">
          <span>Account</span>
          <select
            value={accountFilter}
            onChange={(e) => {
              setPage(0)
              setAccountFilter(e.target.value)
            }}
            className="min-w-[12rem] rounded-lg border border-border bg-white px-3 py-2 text-sm text-ink outline-none focus:border-brand-500"
          >
            <option value="">All accounts</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
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

      {loading && contacts.length === 0 ? <div className="text-sm text-muted">Loading contacts…</div> : null}

      {!loading && !error && contacts.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
          <UserRound className="mx-auto h-8 w-8 text-muted" />
          <h3 className="mt-3 text-base font-semibold text-ink">No contacts yet</h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Add a person and optionally link them to an account.
          </p>
        </div>
      ) : null}

      {contacts.length > 0 ? (
        <>
          <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface shadow-sm lg:block">
            <table className="min-w-full divide-y divide-border text-left text-sm">
              <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Account</th>
                  <th className="px-4 py-3 font-semibold">Job title</th>
                  <th className="px-4 py-3 font-semibold">Email</th>
                  <th className="px-4 py-3 font-semibold">Phone</th>
                  <th className="px-4 py-3 font-semibold">Owner</th>
                  <th className="px-4 py-3 font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {contacts.map((contact) => (
                  <tr key={contact.id} className="hover:bg-slate-50/80">
                    <td className="px-4 py-3 font-medium text-ink">
                      {contact.firstName} {contact.lastName}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{contact.accountName || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{contact.jobTitle || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{contact.email || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{contact.phone || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{contact.ownerName}</td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setSelected(contact)
                            setDetailsOpen(true)
                          }}
                          className="text-sm font-medium text-brand-600"
                        >
                          Open
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteTarget(contact)}
                          className="text-sm font-medium text-red-600"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="grid gap-3 lg:hidden">
            {contacts.map((contact) => (
              <article key={contact.id} className="rounded-xl border border-border bg-surface p-4 shadow-sm">
                <h3 className="font-semibold text-ink">
                  {contact.firstName} {contact.lastName}
                </h3>
                <p className="text-sm text-muted">{contact.accountName || 'No account'}</p>
                <p className="mt-1 text-sm text-slate-600">{contact.email || contact.phone || contact.jobTitle}</p>
                <div className="mt-3 flex gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setSelected(contact)
                      setDetailsOpen(true)
                    }}
                    className="text-sm font-medium text-brand-600"
                  >
                    Open
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeleteTarget(contact)}
                    className="text-sm font-medium text-red-600"
                  >
                    Delete
                  </button>
                </div>
              </article>
            ))}
          </div>

          {totalPages > 1 ? (
            <div className="flex items-center justify-between text-sm text-muted">
              <span>
                {totalElements} contact{totalElements === 1 ? '' : 's'}
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
      ) : null}

      <ContactDetails
        open={detailsOpen}
        contact={selected}
        onClose={() => setDetailsOpen(false)}
        onEdit={(contact) => {
          setSelected(contact)
          setFormMode('edit')
          setFormOpen(true)
        }}
        onDelete={setDeleteTarget}
        onAddTask={(contact) => {
          setTaskPreset({ type: 'CONTACT', id: contact.id })
          setTaskFormOpen(true)
        }}
        activityRefreshKey={activityKey}
      />

      <TaskForm
        open={taskFormOpen}
        mode="create"
        initialRelated={taskPreset}
        pending={taskFormPending}
        onClose={() => setTaskFormOpen(false)}
        onCreate={async (body: TaskCreateRequest, key: string) => {
          setTaskFormPending(true)
          try {
            await createTask(body, key)
            setTaskFormOpen(false)
            setActivityKey((k) => k + 1)
          } finally {
            setTaskFormPending(false)
          }
        }}
        onUpdate={async () => undefined}
      />

      <ContactForm
        open={formOpen}
        mode={formMode}
        contact={formMode === 'edit' ? selected : null}
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
        title="Delete contact?"
        message={
          deleteTarget
            ? `Delete “${deleteTarget.firstName} ${deleteTarget.lastName}”? This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        danger
        pending={deletePending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
