import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Sparkles, X } from 'lucide-react'
import { chatWithAssistant } from '../api/assistant'
import { useAuth } from '../auth/useAuth'
import { useFlowAi } from './flow-ai-context'
import type { AssistantHistoryTurn } from '../types/assistant'
import { ApiError } from '../types/api'

const GLOBAL_PROMPTS = [
  'What should I focus on today?',
  'Summarize my pipeline',
  'Show my overdue follow-ups',
  'Which deals need attention?',
]

const ADMIN_PROMPTS = ['Summarize team workload', 'Which reps have the most open pipeline?']

const RECORD_PROMPTS = [
  'Summarize this record',
  'Suggest next action',
  'Draft a follow-up',
  'What should I pay attention to?',
]

type ChatMessage = { role: 'user' | 'assistant'; content: string }

export function FlowAiDrawer({ sessionKey }: { sessionKey: number }) {
  const { user } = useAuth()
  const { open, context, close } = useFlowAi()
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setInput('')
    setMessages([])
    setPending(false)
    setError(null)
    setSuggestions([])
  }, [sessionKey])

  useEffect(() => {
    if (!open) return
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, close])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages, pending])

  const prompts = context
    ? RECORD_PROMPTS
    : user?.role === 'ADMIN'
      ? [...GLOBAL_PROMPTS, ...ADMIN_PROMPTS]
      : GLOBAL_PROMPTS

  async function send(text: string) {
    const message = text.trim()
    if (!message || pending) return
    setError(null)
    setInput('')
    const nextMessages: ChatMessage[] = [...messages, { role: 'user', content: message }]
    setMessages(nextMessages)
    setPending(true)
    const history: AssistantHistoryTurn[] = nextMessages
      .slice(0, -1)
      .filter((turn) => turn.role === 'user' || turn.role === 'assistant')
      .slice(-8)
    try {
      const result = await chatWithAssistant({
        message,
        context,
        history,
      })
      setMessages((prev) => [...prev, { role: 'assistant', content: result.answer }])
      setSuggestions(result.suggestions ?? [])
    } catch (err) {
      const fallback = 'Flow AI is temporarily unavailable. Your CRM data is unaffected.'
      if (err instanceof ApiError && (err.status === 503 || err.status >= 500)) {
        setError(err.message || fallback)
      } else if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError(fallback)
      }
    } finally {
      setPending(false)
    }
  }

  function onEditorKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void send(input)
    }
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex justify-end overlay-backdrop">
      <button type="button" aria-label="Close Flow AI" className="flex-1 cursor-default" onClick={close} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="flow-ai-title"
        className="flex h-full max-h-dvh w-full max-w-md flex-col overflow-hidden border-l border-border bg-surface shadow-xl"
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-brand-600" aria-hidden />
              <h2 id="flow-ai-title" className="text-lg font-semibold text-ink">
                Flow AI
              </h2>
            </div>
            <p className="mt-0.5 text-sm text-muted">CRM-aware assistant · read-only</p>
            {context ? (
              <p className="badge badge-brand mt-2">
                Context: {context.entityType} · {context.label || context.entityId}
              </p>
            ) : null}
          </div>
          <button type="button" onClick={close} className="icon-btn" aria-label="Close Flow AI">
            <X className="h-5 w-5" />
          </button>
        </header>

        <div ref={listRef} className="min-h-0 flex-1 space-y-3 overflow-x-hidden overflow-y-auto px-5 py-4">
          {messages.length === 0 && !pending ? (
            <div className="space-y-3">
              <p className="text-sm text-muted">
                Ask about your pipeline, overdue work, or this record. Flow AI cannot change CRM data.
              </p>
              <div className="flex flex-wrap gap-2">
                {prompts.map((prompt) => (
                  <button
                    key={prompt}
                    type="button"
                    onClick={() => void send(prompt)}
                    className="chip"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg, index) => (
              <div
                key={`${msg.role}-${index}`}
                className={
                  msg.role === 'user'
                    ? 'ml-8 overflow-visible whitespace-pre-wrap break-words rounded-xl bg-brand-50 px-3 py-2 text-sm text-ink'
                    : 'mr-4 overflow-visible whitespace-pre-wrap break-words rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink'
                }
              >
                {msg.role === 'assistant' ? <SafeText text={msg.content} /> : msg.content}
              </div>
            ))
          )}
          {pending ? <p className="text-sm text-muted">Flow AI is thinking…</p> : null}
          {error ? (
            <div className="alert alert-error">{error}</div>
          ) : null}
          {suggestions.length > 0 && !pending ? (
            <div className="flex flex-wrap gap-2">
              {suggestions.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  onClick={() => void send(prompt)}
                  className="chip"
                >
                  {prompt}
                </button>
              ))}
            </div>
          ) : null}
        </div>

        <form
          className="shrink-0 border-t border-border p-4"
          onSubmit={(e) => {
            e.preventDefault()
            void send(input)
          }}
        >
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onEditorKey}
            rows={3}
            maxLength={2000}
            placeholder="Ask Flow AI…"
            className="ui-input min-h-[4.5rem] resize-none"
          />
          <div className="mt-2 flex items-center justify-between">
            <p className="text-xs text-muted">Enter to send · Shift+Enter for a new line</p>
            <button
              type="submit"
              disabled={pending || input.trim() === ''}
              className="btn btn-primary"
            >
              Send
            </button>
          </div>
        </form>
      </aside>
    </div>
  )
}

function SafeText({ text }: { text: string }) {
  const lines = stripFences(text).split('\n')
  return (
    <div className="overflow-visible whitespace-normal break-words">
      {lines.map((line, index) => {
        const trimmed = line.trimStart().replace(/^#{1,6}\s+/, '').replace(/^>\s+/, '')
        const bullet = /^[-*•]\s+/.test(trimmed) || /^\d+\.\s+/.test(trimmed)
        const content = bullet ? trimmed.replace(/^[-*•]\s+/, '').replace(/^\d+\.\s+/, '') : trimmed
        if (!content) {
          return <div key={index} className="h-2" />
        }
        return (
          <p key={index} className={bullet ? 'pl-3' : undefined}>
            {bullet ? <span aria-hidden>• </span> : null}
            {renderInline(content)}
          </p>
        )
      })}
    </div>
  )
}

function stripFences(text: string) {
  return text.replace(/```[\s\S]*?```/g, (block) =>
    block.replace(/```[^\n]*\n?/g, '').replace(/```/g, ''),
  )
}

function renderInline(text: string) {
  const withoutTicks = text.replace(/`+/g, '')
  const parts = withoutTicks.split(/(\*\*[^*]+\*\*|__[^_]+__)/g)
  return parts.map((part, index) => {
    if ((part.startsWith('**') && part.endsWith('**')) || (part.startsWith('__') && part.endsWith('__'))) {
      return <strong key={index}>{part.slice(2, -2)}</strong>
    }
    return <span key={index}>{part.replace(/\*\*/g, '')}</span>
  })
}
