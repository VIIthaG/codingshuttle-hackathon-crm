import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ThemeProvider } from './theme/ThemeProvider'
import { useAuth } from './auth/useAuth'
import { GuestRoute } from './auth/GuestRoute'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { DashboardPage } from './pages/DashboardPage'
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })))
import { AccountsPage } from './pages/AccountsPage'
import { ContactsPage } from './pages/ContactsPage'
import { DealsPage } from './pages/DealsPage'
import { LeadsPage } from './pages/LeadsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { TasksPage } from './pages/TasksPage'
import { MeetingsPage } from './pages/MeetingsPage'
import { CallsPage } from './pages/CallsPage'
import { CalendarPage } from './pages/CalendarPage'
import { WorkqueuePage } from './pages/WorkqueuePage'

function RootRedirect() {
  const { isAuthenticated, isBootstrapping } = useAuth()
  if (isBootstrapping) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas text-sm text-muted">
        Loading…
      </div>
    )
  }
  return <Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />
}

export default function App() {
  return (
    <ThemeProvider>
    <AuthProvider>
      <Routes>
        <Route path="/" element={<RootRedirect />} />

        <Route element={<GuestRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route
              path="/analytics"
              element={
                <Suspense
                  fallback={
                    <div className="space-y-4">
                      <div className="skeleton h-8 w-48" />
                      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                        {Array.from({ length: 6 }).map((_, i) => (
                          <div key={i} className="skeleton h-28" />
                        ))}
                      </div>
                    </div>
                  }
                >
                  <AnalyticsPage />
                </Suspense>
              }
            />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/contacts" element={<ContactsPage />} />
            <Route path="/deals" element={<DealsPage />} />
            <Route path="/leads" element={<LeadsPage />} />
            <Route path="/tasks" element={<TasksPage />} />
            <Route path="/meetings" element={<MeetingsPage />} />
            <Route path="/calls" element={<CallsPage />} />
            <Route path="/calendar" element={<CalendarPage />} />
            <Route path="/workqueue" element={<WorkqueuePage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
    </ThemeProvider>
  )
}
