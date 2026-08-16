import { apiRequest } from './client'
import type { AssistantChatResponse, AssistantContext, AssistantHistoryTurn } from '../types/assistant'

export function chatWithAssistant(payload: {
  message: string
  context?: AssistantContext | null
  history?: AssistantHistoryTurn[]
}): Promise<AssistantChatResponse> {
  const body: Record<string, unknown> = { message: payload.message }
  if (payload.context?.entityType && payload.context.entityId) {
    body.context = { entityType: payload.context.entityType, entityId: payload.context.entityId }
  }
  if (payload.history && payload.history.length > 0) {
    body.history = payload.history
      .filter((turn) => turn.role === 'user' || turn.role === 'assistant')
      .filter((turn) => !isProviderErrorHistory(turn.content))
      .slice(-8)
  }
  return apiRequest<AssistantChatResponse>('/api/v1/assistant/chat', { method: 'POST', body })
}

function isProviderErrorHistory(content: string): boolean {
  const text = content.toLowerCase()
  return (
    text.includes('flow ai is temporarily unavailable') || text.includes('your crm data is unaffected')
  )
}
