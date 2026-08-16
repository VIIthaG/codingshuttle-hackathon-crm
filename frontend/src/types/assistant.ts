export type AssistantEntityType = 'LEAD' | 'ACCOUNT' | 'CONTACT' | 'DEAL'

export type AssistantContext = {
  entityType: AssistantEntityType
  entityId: string
  label?: string
}

export type AssistantChatResponse = {
  answer: string
  contextUsed: { entityType: AssistantEntityType; entityId: string; label?: string | null } | null
  suggestions: string[]
}

export type AssistantHistoryTurn = {
  role: 'user' | 'assistant'
  content: string
}
