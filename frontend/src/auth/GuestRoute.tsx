import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/** Redirects authenticated users away from login/register. */
export function GuestRoute() {
  const { isAuthenticated, isBootstrapping } = useAuth()

  if (isBootstrapping) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas text-muted">
        Loading session…
      </div>
    )
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
