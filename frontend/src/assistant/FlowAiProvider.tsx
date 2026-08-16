import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { FlowAiContext } from './flow-ai-context'
import { FlowAiDrawer } from './FlowAiDrawer'
import type { AssistantContext } from '../types/assistant'

export function FlowAiProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [context, setContext] = useState<AssistantContext | null>(null)
  const [sessionKey, setSessionKey] = useState(0)

  const openGlobal = useCallback(() => {
    setContext(null)
    setSessionKey((k) => k + 1)
    setOpen(true)
  }, [])

  const openRecord = useCallback((next: AssistantContext) => {
    setContext(next)
    setSessionKey((k) => k + 1)
    setOpen(true)
  }, [])

  const close = useCallback(() => setOpen(false), [])

  const value = useMemo(
    () => ({ open, context, openGlobal, openRecord, close }),
    [open, context, openGlobal, openRecord, close],
  )

  return (
    <FlowAiContext.Provider value={value}>
      {children}
      <FlowAiDrawer sessionKey={sessionKey} />
    </FlowAiContext.Provider>
  )
}
