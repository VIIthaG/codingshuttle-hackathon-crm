import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../types/api'
import { formatApiError } from '../utils/errors'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const trimmedEmail = email.trim()
    if (!trimmedEmail || !password) {
      setError('Email and password are required.')
      return
    }

    // Basic syntactic check so we do not call the API with obviously invalid emails.
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(trimmedEmail)) {
      setError('Please enter a valid email address.')
      return
    }

    setLoading(true)
    try {
      await login({ email: trimmedEmail, password })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(formatApiError(err, 'Login failed'))
      if (err instanceof ApiError && err.status === 401) {
        setError('Invalid email or password.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      <div className="hidden w-[42%] flex-col justify-between bg-slate-900 px-10 py-12 text-white lg:flex">
        <div>
          <div className="text-2xl font-semibold tracking-tight">FlowCRM</div>
          <p className="mt-2 text-sm text-slate-300">Mini CRM for pipeline and follow-ups</p>
        </div>
        <div>
          <h2 className="text-3xl font-semibold leading-tight tracking-tight">
            Track leads.
            <br />
            Close follow-ups.
          </h2>
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-300">
            Sign in to manage your pipeline, tasks, and dashboard metrics.
          </p>
        </div>
        <p className="text-xs text-slate-500">Coding Shuttle Build-A-Thon</p>
      </div>

      <div className="flex flex-1 items-center justify-center bg-canvas px-6 py-12">
        <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-sm">
          <div className="mb-8">
            <h1 className="text-2xl font-semibold text-ink">Sign in</h1>
            <p className="mt-1 text-sm text-muted">Use your FlowCRM account credentials.</p>
          </div>

          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-slate-700">Email</span>
              <input
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-border bg-white px-3 py-2.5 text-sm outline-none ring-brand-500 focus:ring-2"
                placeholder="you@company.com"
              />
            </label>

            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-slate-700">Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-border bg-white px-3 py-2.5 text-sm outline-none ring-brand-500 focus:ring-2"
                placeholder="••••••••"
              />
            </label>

            {error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {error}
              </div>
            ) : null}

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            New here?{' '}
            <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
