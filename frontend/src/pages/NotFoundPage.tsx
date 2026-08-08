import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function NotFoundPage() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-canvas px-6">
      <p className="text-sm font-semibold text-brand-600">404</p>
      <h1 className="mt-2 text-2xl font-semibold text-ink">Page not found</h1>
      <p className="mt-2 text-sm text-muted">That route does not exist in FlowCRM.</p>
      <Link
        to={isAuthenticated ? '/dashboard' : '/login'}
        className="mt-6 inline-flex rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
      >
        {isAuthenticated ? 'Go to dashboard' : 'Go to login'}
      </Link>
    </div>
  )
}
