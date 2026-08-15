import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Header } from './Header'
import { Sidebar } from './Sidebar'

const titles: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/analytics': 'Analytics',
  '/accounts': 'Accounts',
  '/contacts': 'Contacts',
  '/deals': 'Deals',
  '/leads': 'Leads',
  '/tasks': 'Tasks',
  '/meetings': 'Meetings',
  '/calls': 'Calls',
  '/calendar': 'Calendar',
  '/workqueue': 'Workqueue',
}

export function AppShell() {
  const location = useLocation()
  const title = titles[location.pathname] ?? 'FlowCRM'
  const [navOpen, setNavOpen] = useState(false)

  useEffect(() => {
    setNavOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!navOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setNavOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [navOpen])

  return (
    <div className="flex min-h-screen bg-canvas">
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={title} onMenuClick={() => setNavOpen(true)} />
        <main className="flex-1 px-4 py-4 sm:px-6 sm:py-6">{<Outlet />}</main>
      </div>
    </div>
  )
}
