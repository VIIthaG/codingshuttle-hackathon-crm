// import { useState, type FormEvent } from 'react'
// import { Link, useNavigate } from 'react-router-dom'
// import { useAuth } from '../auth/useAuth'
// import { ApiError } from '../types/api'
// import { formatApiError } from '../utils/errors'
// import { Alert } from '../components/ui/Feedback'
// import { AuthThemeSwitch } from '../components/ui/AuthThemeSwitch'

// export function LoginPage() {
//   const { login } = useAuth()
//   const navigate = useNavigate()
//   const [email, setEmail] = useState('')
//   const [password, setPassword] = useState('')
//   const [error, setError] = useState<string | null>(null)
//   const [loading, setLoading] = useState(false)

//   async function onSubmit(event: FormEvent) {
//     event.preventDefault()
//     setError(null)

//     const trimmedEmail = email.trim()
//     if (!trimmedEmail || !password) {
//       setError('Email and password are required.')
//       return
//     }

//     // Basic syntactic check so we do not call the API with obviously invalid emails.
//     const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
//     if (!emailPattern.test(trimmedEmail)) {
//       setError('Please enter a valid email address.')
//       return
//     }

//     setLoading(true)
//     try {
//       await login({ email: trimmedEmail, password })
//       navigate('/dashboard', { replace: true })
//     } catch (err) {
//       setError(formatApiError(err, 'Login failed'))
//       if (err instanceof ApiError && err.status === 401) {
//         setError('Invalid email or password.')
//       }
//     } finally {
//       setLoading(false)
//     }
//   }

//   return (
//     <div className="flex min-h-screen">
//       <div className="hidden w-[42%] flex-col justify-between bg-slate-900 px-10 py-12 text-white lg:flex">
//         <div>
//           <div className="text-2xl font-semibold tracking-tight">FlowCRM</div>
//           <p className="mt-2 text-sm text-slate-300">Mini CRM for pipeline and follow-ups</p>
//         </div>
//         <div>
//           <h2 className="text-3xl font-semibold leading-tight tracking-tight">
//             Track leads.
//             <br />
//             Close follow-ups.
//           </h2>
//           <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-300">
//             Sign in to manage your pipeline, tasks, and dashboard metrics.
//           </p>
//         </div>
//         <p className="text-xs text-slate-500">Coding Shuttle Build-A-Thon</p>
//       </div>

//       <div className="flex flex-1 items-center justify-center bg-canvas px-6 py-12">
//         <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-sm">
//           <AuthThemeSwitch />
//           <div className="mb-8 mt-4">
//             <p className="text-sm font-semibold text-brand-600 lg:hidden">FlowCRM</p>
//             <h1 className="text-2xl font-semibold text-ink">Sign in</h1>
//             <p className="mt-1 text-sm text-muted">Use your FlowCRM account credentials.</p>
//           </div>

//           <form className="space-y-4" onSubmit={onSubmit} noValidate>
//             <label className="block">
//               <span className="mb-1.5 block text-sm font-medium text-ink">Email</span>
//               <input
//                 type="email"
//                 autoComplete="email"
//                 value={email}
//                 onChange={(e) => setEmail(e.target.value)}
//                 disabled={loading}
//                 className="ui-input"
//                 placeholder="you@company.com"
//               />
//             </label>

//             <label className="block">
//               <span className="mb-1.5 block text-sm font-medium text-ink">Password</span>
//               <input
//                 type="password"
//                 autoComplete="current-password"
//                 value={password}
//                 onChange={(e) => setPassword(e.target.value)}
//                 disabled={loading}
//                 className="ui-input"
//                 placeholder="••••••••"
//               />
//             </label>

//             {error ? <Alert>{error}</Alert> : null}

//             <button
//               type="submit"
//               disabled={loading}
//               className="flex w-full items-center justify-center btn btn-primary disabled:cursor-not-allowed disabled:opacity-60"
//             >
//               {loading ? 'Signing in…' : 'Sign in'}
//             </button>
//           </form>

//           <p className="mt-6 text-center text-sm text-muted">
//             New here?{' '}
//             <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
//               Create an account
//             </Link>
//           </p>
//         </div>
//       </div>
//     </div>
//   )
// }
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../types/api'
import { Alert } from '../components/ui/Feedback'
import { AuthThemeSwitch } from '../components/ui/AuthThemeSwitch'

function getLoginErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 400 || err.status === 401) {
      return 'Incorrect email or password.'
    }

    if (err.status === 429) {
      const wait = err.retryAfterSeconds

      if (wait != null && wait > 0) {
        return `Too many login attempts. Please try again in about ${wait} seconds.`
      }

      return 'Too many login attempts. Please wait a moment and try again.'
    }

    if (err.status === 403) {
      return 'This account is not allowed to sign in.'
    }

    if (err.status >= 500) {
      return 'FlowCRM is temporarily unavailable. Please try again shortly.'
    }

    return 'Unable to sign in. Please check your credentials and try again.'
  }

  if (err instanceof Error) {
    return 'Unable to reach FlowCRM. Please check your connection and try again.'
  }

  return 'Unable to sign in. Please try again.'
}

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

    if (!trimmedEmail && !password) {
      setError('Email and password are required.')
      return
    }

    if (!trimmedEmail) {
      setError('Email is required.')
      return
    }

    if (!password) {
      setError('Password is required.')
      return
    }

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (!emailPattern.test(trimmedEmail)) {
      setError('Please enter a valid email address.')
      return
    }

    setLoading(true)

    try {
      await login({
        email: trimmedEmail,
        password,
      })

      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(getLoginErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      <div className="hidden w-[42%] flex-col justify-between bg-slate-900 px-10 py-12 text-white lg:flex">
        <div>
          <div className="text-2xl font-semibold tracking-tight">FlowCRM</div>
          <p className="mt-2 text-sm text-slate-300">
            Mini CRM for pipeline and follow-ups
          </p>
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

        <p className="text-xs text-slate-500">
          Coding Shuttle Build-A-Thon
        </p>
      </div>

      <div className="flex flex-1 items-center justify-center bg-canvas px-6 py-12">
        <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-sm">
          <AuthThemeSwitch />

          <div className="mb-8 mt-4">
            <p className="text-sm font-semibold text-brand-600 lg:hidden">
              FlowCRM
            </p>

            <h1 className="text-2xl font-semibold text-ink">
              Sign in
            </h1>

            <p className="mt-1 text-sm text-muted">
              Use your FlowCRM account credentials.
            </p>
          </div>

          <form
            className="space-y-4"
            onSubmit={onSubmit}
            noValidate
          >
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-ink">
                Email
              </span>

              <input
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
                className="ui-input"
                placeholder="you@company.com"
              />
            </label>

            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-ink">
                Password
              </span>

              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
                className="ui-input"
                placeholder="Enter your password"
              />
            </label>

            {error ? <Alert>{error}</Alert> : null}

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center btn btn-primary disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            New here?{' '}
            <Link
              to="/register"
              className="font-medium text-brand-600 hover:text-brand-700"
            >
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}