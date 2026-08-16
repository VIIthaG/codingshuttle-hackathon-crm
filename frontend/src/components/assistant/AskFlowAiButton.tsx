import { Sparkles } from 'lucide-react'
import { useFlowAi } from '../../assistant/flow-ai-context'
import type { AssistantEntityType } from '../../types/assistant'

export function AskFlowAiButton({
  entityType,
  entityId,
  label,
}: {
  entityType: AssistantEntityType
  entityId: string
  label: string
}) {
  const { openRecord } = useFlowAi()
  return (
    <button
      type="button"
      onClick={() => openRecord({ entityType, entityId, label })}
      className="inline-flex items-center gap-1.5 rounded-lg border border-brand-100 bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700 hover:bg-brand-100"
    >
      <Sparkles className="h-4 w-4" aria-hidden />
      Ask Flow AI
    </button>
  )
}
