import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { listAllAccounts } from '../../api/accounts'
import { listContacts } from '../../api/contacts'
import { convertLead } from '../../api/leads'
import type { Account } from '../../types/account'
import { ApiError } from '../../types/api'
import type { Contact } from '../../types/contact'
import type { Lead, LeadConvertRequest } from '../../types/lead'
import { formatApiError } from '../../utils/errors'
import { newIdempotencyKey } from '../../utils/idempotency'

type ConvertLeadModalProps = {
  open: boolean
  lead: Lead | null
  pending?: boolean
  onClose: () => void
  onConverted: (lead: Lead) => void
}

function splitName(fullName: string): { first: string; last: string } {
  const t = fullName.trim()
  const i = t.indexOf(' ')
  if (i < 0) return { first: t, last: t }
  const first = t.slice(0, i).trim()
  const last = t.slice(i + 1).trim()
  return { first: first || t, last: last || first || t }
}

export function ConvertLeadModal({ open, lead, pending = false, onClose, onConverted }: ConvertLeadModalProps) {
  const navigate = useNavigate()
  const [accountMode, setAccountMode] = useState<'new' | 'existing'>('new')
  const [contactMode, setContactMode] = useState<'new' | 'existing'>('new')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [contacts, setContacts] = useState<Contact[]>([])
  const [accountName, setAccountName] = useState('')
  const [accountWebsite, setAccountWebsite] = useState('')
  const [accountPhone, setAccountPhone] = useState('')
  const [accountIndustry, setAccountIndustry] = useState('')
  const [existingAccountId, setExistingAccountId] = useState('')
  const [contactFirstName, setContactFirstName] = useState('')
  const [contactLastName, setContactLastName] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [contactJobTitle, setContactJobTitle] = useState('')
  const [existingContactId, setExistingContactId] = useState('')
  const [createDeal, setCreateDeal] = useState(false)
  const [dealName, setDealName] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [expectedCloseDate, setExpectedCloseDate] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Lead | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const keyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open || !lead) return
    setError(null)
    setResult(null)
    setAccountMode('new')
    setContactMode('new')
    setAccountName(lead.company?.trim() || '')
    setAccountWebsite('')
    setAccountPhone('')
    setAccountIndustry('')
    setExistingAccountId('')
    const names = splitName(lead.fullName)
    setContactFirstName(names.first)
    setContactLastName(names.last)
    setContactEmail(lead.email ?? '')
    setContactPhone(lead.phone ?? '')
    setContactJobTitle('')
    setExistingContactId('')
    setCreateDeal(false)
    setDealName(lead.company?.trim() ? `${lead.company.trim()} Opportunity` : `${lead.fullName} Opportunity`)
    setAmount('')
    setCurrency('USD')
    setExpectedCloseDate('')
    setDescription('')
    keyRef.current = newIdempotencyKey()
    void listAllAccounts()
      .then(setAccounts)
      .catch(() => setAccounts([]))
  }, [open, lead])

  const selectedAccountId = accountMode === 'existing' ? existingAccountId : ''

  useEffect(() => {
    if (!open || !selectedAccountId) {
      setContacts([])
      return
    }
    let cancelled = false
    void listContacts({ accountId: selectedAccountId, size: 100, sort: 'lastName,asc' })
      .then((page) => {
        if (!cancelled) setContacts(page.content)
      })
      .catch(() => {
        if (!cancelled) setContacts([])
      })
    return () => {
      cancelled = true
    }
  }, [open, selectedAccountId])

  if (!open || !lead) return null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!lead) return
    setError(null)
    if (accountMode === 'new' && !accountName.trim()) {
      setError('Account name is required')
      return
    }
    if (accountMode === 'existing' && !existingAccountId) {
      setError('Select an existing account')
      return
    }
    if (contactMode === 'existing' && !existingContactId) {
      setError('Select an existing contact')
      return
    }
    if (createDeal && !dealName.trim()) {
      setError('Deal name is required when creating a deal')
      return
    }
    const body: LeadConvertRequest = {
      createDeal,
    }
    if (accountMode === 'existing') body.useExistingAccountId = existingAccountId
    else {
      body.accountName = accountName.trim()
      body.accountWebsite = accountWebsite.trim() || null
      body.accountPhone = accountPhone.trim() || null
      body.accountIndustry = accountIndustry.trim() || null
    }
    if (contactMode === 'existing') body.useExistingContactId = existingContactId
    else {
      body.contactFirstName = contactFirstName.trim()
      body.contactLastName = contactLastName.trim()
      body.contactEmail = contactEmail.trim() || null
      body.contactPhone = contactPhone.trim() || null
      body.contactJobTitle = contactJobTitle.trim() || null
    }
    if (createDeal) {
      body.dealName = dealName.trim()
      body.amount = amount.trim() === '' ? null : Number(amount)
      body.currency = currency
      body.expectedCloseDate = expectedCloseDate || null
      body.description = description.trim() || null
    }
    setSubmitting(true)
    try {
      if (!keyRef.current) keyRef.current = newIdempotencyKey()
      const converted = await convertLead(lead.id, body, keyRef.current)
      setResult(converted)
      onConverted(converted)
      keyRef.current = newIdempotencyKey()
    } catch (err) {
      if (err instanceof ApiError) setError(formatApiError(err, 'Conversion failed'))
      else setError(formatApiError(err, 'Conversion failed'))
    } finally {
      setSubmitting(false)
    }
  }

  const busy = pending || submitting

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overlay-backdrop p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="convert-lead-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        {result ? (
          <div>
            <h2 id="convert-lead-title" className="text-lg font-semibold text-ink">
              Lead converted successfully
            </h2>
            <p className="mt-1 text-sm text-muted">{lead.fullName} is now CONVERTED.</p>
            <dl className="mt-4 space-y-2 text-sm">
              <div>
                <dt className="text-muted">Account</dt>
                <dd className="font-medium text-ink">{result.convertedAccountName}</dd>
              </div>
              <div>
                <dt className="text-muted">Contact</dt>
                <dd className="font-medium text-ink">{result.convertedContactName}</dd>
              </div>
              {result.convertedDealId ? (
                <div>
                  <dt className="text-muted">Deal</dt>
                  <dd className="font-medium text-ink">{result.convertedDealName}</dd>
                </div>
              ) : null}
            </dl>
            <div className="mt-5 flex flex-wrap gap-2">
              {result.convertedAccountId ? (
                <button
                  type="button"
                  onClick={() => navigate(`/accounts?open=${result.convertedAccountId}`)}
                  className="btn btn-primary"
                >
                  View Account
                </button>
              ) : null}
              {result.convertedContactId ? (
                <button
                  type="button"
                  onClick={() => navigate(`/contacts?open=${result.convertedContactId}`)}
                  className="btn btn-secondary"
                >
                  View Contact
                </button>
              ) : null}
              {result.convertedDealId ? (
                <button
                  type="button"
                  onClick={() => navigate(`/deals?open=${result.convertedDealId}`)}
                  className="btn btn-secondary"
                >
                  View Deal
                </button>
              ) : null}
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg px-3 py-2 text-sm font-medium text-muted hover:bg-canvas"
              >
                Close
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 id="convert-lead-title" className="text-lg font-semibold text-ink">
                  Convert lead
                </h2>
                <p className="mt-1 text-sm text-muted">Creates account and contact in one transaction.</p>
              </div>
              <button type="button" onClick={onClose} className="rounded-lg px-2 py-1 text-sm text-muted hover:bg-canvas">
                Close
              </button>
            </div>

            <div className="mt-4 rounded-lg border border-border bg-canvas px-3 py-3 text-sm">
              <div className="font-medium text-ink">{lead.fullName}</div>
              <div className="mt-1 text-muted">{lead.company || 'No company'}</div>
              <div className="text-muted">{lead.email || 'No email'}</div>
              <div className="text-muted">{lead.phone || 'No phone'}</div>
            </div>

            <form className="mt-5 space-y-5" onSubmit={onSubmit}>
              <section>
                <h3 className="text-sm font-semibold text-ink">Account</h3>
                <div className="mt-2 flex gap-3 text-sm">
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={accountMode === 'new'}
                      onChange={() => setAccountMode('new')}
                      disabled={busy}
                    />
                    Create new
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={accountMode === 'existing'}
                      onChange={() => setAccountMode('existing')}
                      disabled={busy}
                    />
                    Use existing
                  </label>
                </div>
                {accountMode === 'new' ? (
                  <div className="mt-3 space-y-3">
                    <Field label="Account name" required>
                      <input
                        value={accountName}
                        onChange={(e) => {
                          setAccountName(e.target.value)
                          if (!dealName || dealName.endsWith(' Opportunity')) {
                            setDealName(e.target.value.trim() ? `${e.target.value.trim()} Opportunity` : dealName)
                          }
                        }}
                        disabled={busy}
                        className="ui-input"
                      />
                    </Field>
                    <Field label="Website">
                      <input
                        value={accountWebsite}
                        onChange={(e) => setAccountWebsite(e.target.value)}
                        disabled={busy}
                        className="ui-input"
                      />
                    </Field>
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field label="Phone">
                        <input
                          value={accountPhone}
                          onChange={(e) => setAccountPhone(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                      <Field label="Industry">
                        <input
                          value={accountIndustry}
                          onChange={(e) => setAccountIndustry(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                    </div>
                  </div>
                ) : (
                  <select
                    value={existingAccountId}
                    onChange={(e) => {
                      setExistingAccountId(e.target.value)
                      setExistingContactId('')
                      const acct = accounts.find((a) => a.id === e.target.value)
                      if (acct && createDeal) setDealName(`${acct.name} Opportunity`)
                    }}
                    disabled={busy}
                    className="mt-3 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm"
                  >
                    <option value="">Select account</option>
                    {accounts.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </select>
                )}
              </section>

              <section>
                <h3 className="text-sm font-semibold text-ink">Contact</h3>
                <div className="mt-2 flex gap-3 text-sm">
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={contactMode === 'new'}
                      onChange={() => setContactMode('new')}
                      disabled={busy}
                    />
                    Create new
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={contactMode === 'existing'}
                      onChange={() => setContactMode('existing')}
                      disabled={busy || accountMode !== 'existing'}
                    />
                    Use existing
                  </label>
                </div>
                {contactMode === 'new' ? (
                  <div className="mt-3 space-y-3">
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field label="First name">
                        <input
                          value={contactFirstName}
                          onChange={(e) => setContactFirstName(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                      <Field label="Last name">
                        <input
                          value={contactLastName}
                          onChange={(e) => setContactLastName(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                    </div>
                    <Field label="Email">
                      <input
                        value={contactEmail}
                        onChange={(e) => setContactEmail(e.target.value)}
                        disabled={busy}
                        className="ui-input"
                      />
                    </Field>
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field label="Phone">
                        <input
                          value={contactPhone}
                          onChange={(e) => setContactPhone(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                      <Field label="Job title">
                        <input
                          value={contactJobTitle}
                          onChange={(e) => setContactJobTitle(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                    </div>
                  </div>
                ) : (
                  <select
                    value={existingContactId}
                    onChange={(e) => setExistingContactId(e.target.value)}
                    disabled={busy || !existingAccountId}
                    className="mt-3 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm"
                  >
                    <option value="">Select contact</option>
                    {contacts.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.firstName} {c.lastName}
                      </option>
                    ))}
                  </select>
                )}
              </section>

              <section>
                <label className="flex items-center gap-2 text-sm font-semibold text-ink">
                  <input
                    type="checkbox"
                    checked={createDeal}
                    onChange={(e) => setCreateDeal(e.target.checked)}
                    disabled={busy}
                  />
                  Create a Deal
                </label>
                {createDeal ? (
                  <div className="mt-3 space-y-3">
                    <Field label="Deal name" required>
                      <input
                        value={dealName}
                        onChange={(e) => setDealName(e.target.value)}
                        disabled={busy}
                        className="ui-input"
                      />
                    </Field>
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field label="Amount">
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          value={amount}
                          onChange={(e) => setAmount(e.target.value)}
                          disabled={busy}
                          className="ui-input"
                        />
                      </Field>
                      <Field label="Currency">
                        <select
                          value={currency}
                          onChange={(e) => setCurrency(e.target.value)}
                          disabled={busy}
                          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm"
                        >
                          {['USD', 'EUR', 'GBP', 'INR', 'CAD'].map((c) => (
                            <option key={c} value={c}>
                              {c}
                            </option>
                          ))}
                        </select>
                      </Field>
                    </div>
                    <Field label="Expected close">
                      <input
                        type="date"
                        value={expectedCloseDate}
                        onChange={(e) => setExpectedCloseDate(e.target.value)}
                        disabled={busy}
                        className="ui-input"
                      />
                    </Field>
                    <Field label="Description">
                      <textarea
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        disabled={busy}
                        rows={2}
                        className="ui-input"
                      />
                    </Field>
                  </div>
                ) : null}
              </section>

              {error ? (
                <div className="alert alert-error">{error}</div>
              ) : null}

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={busy}
                  className="btn btn-secondary disabled:opacity-60"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="btn btn-primary disabled:opacity-60"
                >
                  {busy ? 'Converting…' : 'Convert lead'}
                </button>
              </div>
            </form>
          </>
        )}
      </div>
    </div>
  )
}

function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-ink">
        {label}
        {required ? <span className="text-red-500"> *</span> : null}
      </span>
      {children}
    </label>
  )
}
