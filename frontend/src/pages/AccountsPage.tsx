import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Building2, Plus, RefreshCw, Search } from 'lucide-react'
import { createAccount, deleteAccount, listAccounts, updateAccount } from '../api/accounts'
import { listContacts } from '../api/contacts'
import { listUsers } from '../api/users'
import { useAuth } from '../auth/useAuth'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { AccountDetails } from '../components/accounts/AccountDetails'
import { AccountForm } from '../components/accounts/AccountForm'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import type { Contact } from '../types/contact'
import type { User } from '../types/auth'
import { formatApiError } from '../utils/errors'

export function AccountsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [users, setUsers] = useState<User[]>([])

  const [selected, setSelected] = useState<Account | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [relatedContacts, setRelatedContacts] = useState<Contact[]>([])
  const [contactsLoading, setContactsLoading] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [formPending, setFormPending] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Account | null>(null)
  const [deletePending, setDeletePending] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listAccounts({ search, page, size: 20, sort: 'name,asc' })
      setAccounts(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
      setSelected((prev) => (prev ? data.content.find((a) => a.id === prev.id) ?? prev : null))
    } catch (err) {
      setError(formatApiError(err, 'Failed to load accounts'))
    } finally {
      setLoading(false)
    }
  }, [search, page])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!isAdmin) return
    void listUsers()
      .then(setUsers)
      .catch(() => setUsers([]))
  }, [isAdmin])

  useEffect(() => {
    if (!detailsOpen || !selected) {
      setRelatedContacts([])
      return
    }
    let cancelled = false
    setContactsLoading(true)
    void listContacts({ accountId: selected.id, size: 50, sort: 'lastName,asc' })
      .then((pageData) => {
        if (!cancelled) setRelatedContacts(pageData.content)
      })
      .catch(() => {
        if (!cancelled) setRelatedContacts([])
      })
      .finally(() => {
        if (!cancelled) setContactsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [detailsOpen, selected])

  function applySearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  async function handleCreate(body: AccountCreateRequest, key: string) {
    setFormPending(true)
    try {
      const created = await createAccount(body, key)
      setFormOpen(false)
      setSelected(created)
      setDetailsOpen(true)
      await refresh()
    } finally {
      setFormPending(false)
    }
  }

  async function handleUpdate(id: string, body: AccountUpdateRequest) {
    setFormPending(true)
    try {
      const updated = await updateAccount(id, body)
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
      await deleteAccount(deleteTarget.id)
      if (selected?.id === deleteTarget.id) {
        setDetailsOpen(false)
        setSelected(null)
      }
      setDeleteTarget(null)
      await refresh()
    } catch (err) {
      setError(formatApiError(err, 'Failed to delete account'))
      setDeleteTarget(null)
    } finally {
      setDeletePending(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-ink">Accounts</h2>
          <p className="mt-1 max-w-xl text-sm text-muted">
            Companies you work with.
            {isAdmin ? ' Admins see every account and can change owners.' : ' You see accounts you own.'}
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
            Add account
          </button>
        </div>
      </div>

      <form onSubmit={applySearch} className="flex max-w-md gap-2">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
          <input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search name, website, industry"
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

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
          <button type="button" onClick={() => void refresh()} className="ml-3 font-medium underline">
            Retry
          </button>
        </div>
      ) : null}

      {loading && accounts.length === 0 ? <div className="text-sm text-muted">Loading accounts…</div> : null}

      {!loading && !error && accounts.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
          <Building2 className="mx-auto h-8 w-8 text-muted" />
          <h3 className="mt-3 text-base font-semibold text-ink">No accounts yet</h3>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Add a company to organize contacts and keep ownership clear.
          </p>
        </div>
      ) : null}

      {accounts.length > 0 ? (
        <>
          <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface shadow-sm lg:block">
            <table className="min-w-full divide-y divide-border text-left text-sm">
              <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Industry</th>
                  <th className="px-4 py-3 font-semibold">Website</th>
                  <th className="px-4 py-3 font-semibold">Phone</th>
                  <th className="px-4 py-3 font-semibold">Owner</th>
                  <th className="px-4 py-3 font-semibold">Contacts</th>
                  <th className="px-4 py-3 font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {accounts.map((account) => (
                  <tr key={account.id} className="hover:bg-slate-50/80">
                    <td className="px-4 py-3 font-medium text-ink">{account.name}</td>
                    <td className="px-4 py-3 text-slate-600">{account.industry || '—'}</td>
                    <td className="max-w-[12rem] truncate px-4 py-3 text-slate-600">{account.website || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{account.phone || '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{account.ownerName}</td>
                    <td className="px-4 py-3 text-slate-600">{account.contactCount}</td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setSelected(account)
                            setDetailsOpen(true)
                          }}
                          className="text-sm font-medium text-brand-600"
                        >
                          Open
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteTarget(account)}
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
            {accounts.map((account) => (
              <article key={account.id} className="rounded-xl border border-border bg-surface p-4 shadow-sm">
                <h3 className="font-semibold text-ink">{account.name}</h3>
                <p className="text-sm text-muted">{account.industry || 'No industry'}</p>
                <p className="mt-2 text-sm text-slate-600">{account.ownerName}</p>
                <div className="mt-3 flex gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setSelected(account)
                      setDetailsOpen(true)
                    }}
                    className="text-sm font-medium text-brand-600"
                  >
                    Open
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeleteTarget(account)}
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
                {totalElements} account{totalElements === 1 ? '' : 's'}
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

      <AccountDetails
        open={detailsOpen}
        account={selected}
        contacts={relatedContacts}
        contactsLoading={contactsLoading}
        onClose={() => setDetailsOpen(false)}
        onEdit={(account) => {
          setSelected(account)
          setFormMode('edit')
          setFormOpen(true)
        }}
        onDelete={setDeleteTarget}
      />

      <AccountForm
        open={formOpen}
        mode={formMode}
        account={formMode === 'edit' ? selected : null}
        users={users}
        isAdmin={isAdmin}
        pending={formPending}
        onClose={() => setFormOpen(false)}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        title="Delete account?"
        message={
          deleteTarget
            ? `Delete “${deleteTarget.name}”? Linked contacts stay, but lose this account link.`
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
