import { createContext, useContext } from 'react'
import type { AssistantContext } from '../types/assistant'

export type FlowAiApi = {
  open: boolean
  context: AssistantContext | null
  openGlobal: () => void
  openRecord: (context: AssistantContext) => void
  close: () => void
}

export const FlowAiContext = createContext<FlowAiApi | null>(null)

export function useFlowAi(): FlowAiApi {
  const value = useContext(FlowAiContext)
  if (!value) {
    throw new Error('useFlowAi must be used within FlowAiProvider')
  }
  return value
}
