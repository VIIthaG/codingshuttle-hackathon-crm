import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search } from 'lucide-react'
import { globalSearch } from '../api/search'
import type { SearchResult, SearchResultType } from '../types/search'
import { searchPath } from '../types/search'
import { formatApiError } from '../utils/errors'

const TYPE_LABEL: Record<SearchResultType, string> = {
  LEAD: 'Lead',
  ACCOUNT: 'Account',
  CONTACT: 'Contact',
  DEAL: 'Deal',
  TASK: 'Task',
  MEETING: 'Meeting',
  CALL: 'Call',
}

export function GlobalSearch() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(query.trim()), 300)
    return () => window.clearTimeout(t)
  }, [query])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen(true)
        window.setTimeout(() => inputRef.current?.focus(), 0)
      }

      if (event.key === 'Escape') {
        setOpen(false)
      }
    }

    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    if (!open) return

    function onClick(event: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  useEffect(() => {
    if (debounced.length < 2) {
      setResults([])
      setError(null)
      setLoading(false)
      return
    }

    let cancelled = false
    setLoading(true)

    void globalSearch(debounced)
      .then((res) => {
        if (cancelled) return

        setResults(res.results)
        setActive(0)
        setError(null)
      })
      .catch((err) => {
        if (cancelled) return

        setError(formatApiError(err, 'Search failed'))
        setResults([])
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [debounced])

  const empty = useMemo(
    () => !loading && !error && debounced.length >= 2 && results.length === 0,
    [loading, error, debounced, results.length],
  )

  function go(result: SearchResult) {
    setOpen(false)
    setQuery('')
    navigate(searchPath(result.type, result.id))
  }

  return (
    <div className="relative min-w-0 flex-1 max-w-md" ref={panelRef}>
      <button
        type="button"
        className="icon-btn sm:hidden"
        aria-label="Search"
        onClick={() => {
          setOpen(true)
          window.setTimeout(() => inputRef.current?.focus(), 0)
        }}
      >
        <Search className="h-4 w-4" />
      </button>

      <div
        className={`${
          open ? 'flex' : 'hidden'
        } sm:flex absolute right-0 top-0 z-30 w-[min(100vw-2rem,24rem)] sm:static sm:w-full`}
      >
        <label className="relative w-full">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />

          <input
            ref={inputRef}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setOpen(true)
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((i) =>
                  Math.min(i + 1, Math.max(results.length - 1, 0)),
                )
              }

              if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((i) => Math.max(i - 1, 0))
              }

              if (e.key === 'Enter' && results[active]) {
                e.preventDefault()
                go(results[active])
              }

              if (e.key === 'Escape') {
                setOpen(false)
                inputRef.current?.blur()
              }
            }}
            placeholder="Search... (Ctrl+K)"
            className="w-full rounded-lg border border-border bg-surface py-2 pl-8 pr-3 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            aria-label="Global search"
          />
        </label>
      </div>

      {open && (query.trim().length >= 2 || loading || error) ? (
        <div className="dropdown-panel absolute right-0 z-40 mt-12 w-[min(100vw-2rem,24rem)] sm:left-0 sm:right-auto sm:mt-1 sm:w-full">
          {loading ? (
            <p className="px-3 py-3 text-sm text-muted">Searching...</p>
          ) : null}

          {error ? (
            <p className="px-3 py-3 text-sm text-[color:var(--app-danger-text)]">
              {error}
            </p>
          ) : null}

          {empty ? (
            <p className="px-3 py-3 text-sm text-muted">No matches</p>
          ) : null}

          {results.length > 0 ? (
            <ul className="max-h-80 overflow-y-auto py-1">
              {results.map((result, index) => (
                <li key={`${result.type}-${result.id}`}>
                  <button
                    type="button"
                    className={`flex w-full flex-col items-start px-3 py-2 text-left ${
                      index === active ? 'bg-canvas' : ''
                    }`}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => go(result)}
                  >
                    <span className="text-[10px] font-semibold uppercase tracking-wide text-muted">
                      {TYPE_LABEL[result.type]}
                    </span>

                    <span className="text-sm font-medium text-ink">
                      {result.title}
                    </span>

                    {result.subtitle ? (
                      <span className="text-xs text-muted">
                        {result.subtitle}
                      </span>
                    ) : null}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}