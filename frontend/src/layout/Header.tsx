import { Menu, Sparkles } from 'lucide-react'
import { useFlowAi } from '../assistant/flow-ai-context'
import { GlobalSearch } from './GlobalSearch'
import { NotificationBell } from './NotificationBell'
import { QuickCreateMenu } from './QuickCreateMenu'
import { UserMenu } from './UserMenu'

type HeaderProps = {
  title: string
  onMenuClick: () => void
}

export function Header({ title, onMenuClick }: HeaderProps) {
  const { openGlobal } = useFlowAi()

  return (
    <header className="flex h-14 items-center gap-2 border-b border-border bg-surface px-3 sm:h-16 sm:gap-3 sm:px-6">
      <div className="flex min-w-0 shrink-0 items-center gap-2">
        <button
          type="button"
          onClick={onMenuClick}
          className="icon-btn md:hidden"
          aria-label="Open navigation"
          aria-controls="app-sidebar"
        >
          <Menu className="h-5 w-5" aria-hidden />
        </button>
        <h1 className="hidden truncate text-base font-semibold text-ink sm:block sm:text-lg">{title}</h1>
      </div>
      <GlobalSearch />
      <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
        <QuickCreateMenu />
        <button
          type="button"
          onClick={openGlobal}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-brand-100 bg-brand-50 px-2.5 text-sm font-medium text-brand-700 hover:bg-brand-100 sm:px-3"
          title="Flow AI"
          aria-label="Open Flow AI"
        >
          <Sparkles className="h-4 w-4" aria-hidden />
          <span className="hidden sm:inline">Flow AI</span>
        </button>
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  )
}
