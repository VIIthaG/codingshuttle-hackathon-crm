import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../types/api'
import { formatApiError } from '../utils/errors'
import { Alert } from '../components/ui/Feedback'
import { AuthThemeSwitch } from '../components/ui/AuthThemeSwitch'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const nextField: Record<string, string> = {}
    if (!fullName.trim()) nextField.fullName = 'Full name is required'
    if (!email.trim()) nextField.email = 'Email is required'
    if (password.length < 8) nextField.password = 'Password must be at least 8 characters'
    if (Object.keys(nextField).length > 0) {
      setFieldErrors(nextField)
      return
    }

    setLoading(true)
    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.fieldErrors) setFieldErrors(err.fieldErrors)
        setError(formatApiError(err, 'Registration failed'))
      } else {
        setError(formatApiError(err, 'Registration failed'))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-6 py-12">
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-sm">
        <AuthThemeSwitch />
        <div className="mb-2 mt-4 text-sm font-semibold text-brand-600">FlowCRM</div>
        <h1 className="text-2xl font-semibold text-ink">Create account</h1>
        <p className="mt-1 text-sm text-muted">
          Register with email, password, and full name.
        </p>

        <form className="mt-8 space-y-4" onSubmit={onSubmit} noValidate>
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium text-ink">Full name</span>
            <input
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              disabled={loading}
              className="ui-input"
              placeholder="Alex Morgan"
            />
            {fieldErrors.fullName ? (
              <p className="mt-1 text-xs text-[color:var(--app-danger-text)]">{fieldErrors.fullName}</p>
            ) : null}
          </label>

          <label className="block">
            <span className="mb-1.5 block text-sm font-medium text-ink">Email</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              className="ui-input"
              placeholder="you@company.com"
            />
            {fieldErrors.email ? (
              <p className="mt-1 text-xs text-[color:var(--app-danger-text)]">{fieldErrors.email}</p>
            ) : null}
          </label>

          <label className="block">
            <span className="mb-1.5 block text-sm font-medium text-ink">Password</span>
            <input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              className="ui-input"
              placeholder="At least 8 characters"
            />
            {fieldErrors.password ? (
              <p className="mt-1 text-xs text-[color:var(--app-danger-text)]">{fieldErrors.password}</p>
            ) : null}
          </label>

          {error ? <Alert>{error}</Alert> : null}

          <button
            type="submit"
            disabled={loading}
            className="flex w-full items-center justify-center btn btn-primary disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-muted">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
