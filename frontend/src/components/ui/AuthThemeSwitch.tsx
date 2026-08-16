import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../../theme/useTheme'

export function AuthThemeSwitch() {
  const { theme, setTheme } = useTheme()
  return (
    <div className="flex justify-end">
      <div className="inline-flex rounded-lg border border-border p-0.5">
        <button
          type="button"
          className={`btn btn-sm ${theme === 'light' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTheme('light')}
          aria-label="Light theme"
        >
          <Sun className="h-3.5 w-3.5" aria-hidden />
        </button>
        <button
          type="button"
          className={`btn btn-sm ${theme === 'dark' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTheme('dark')}
          aria-label="Dark theme"
        >
          <Moon className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>
    </div>
  )
}
