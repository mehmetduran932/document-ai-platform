import { useState, type FormEvent } from 'react'
import { SendIcon } from 'lucide-react'

import { searchApi } from '@/api/search'
import { ChunkViewerDialog } from '@/components/ChunkViewerDialog'
import { errorMessage } from '@/components/ErrorAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import type { SearchResultResponse } from '@/api/types'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'error'
  content: string
  sourceChunks?: SearchResultResponse[]
}

export function AskPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)
  const [activeChunk, setActiveChunk] = useState<SearchResultResponse | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = question.trim()
    if (!trimmed || asking) return

    const userMessage: ChatMessage = { id: crypto.randomUUID(), role: 'user', content: trimmed }
    setMessages((prev) => [...prev, userMessage])
    setQuestion('')
    setAsking(true)

    try {
      const response = await searchApi.ask(trimmed)
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: response.answer,
          sourceChunks: response.sourceChunks,
        },
      ])
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { id: crypto.randomUUID(), role: 'error', content: errorMessage(err).title },
      ])
    } finally {
      setAsking(false)
    }
  }

  return (
    <div className="flex h-[calc(100svh-10rem)] flex-col gap-4">
      <h1 className="text-2xl font-semibold">Ask</h1>

      <div className="flex-1 space-y-4 overflow-y-auto rounded-lg border p-4">
        {messages.length === 0 && (
          <p className="text-muted-foreground text-center text-sm">
            Ask a question grounded in your uploaded documents.
          </p>
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}
          >
            <div
              className={
                message.role === 'user'
                  ? 'bg-primary text-primary-foreground max-w-[75%] rounded-lg px-4 py-2 text-sm'
                  : message.role === 'error'
                    ? 'bg-destructive/10 text-destructive max-w-[75%] rounded-lg px-4 py-2 text-sm'
                    : 'bg-muted max-w-[75%] rounded-lg px-4 py-2 text-sm'
              }
            >
              <p className="whitespace-pre-wrap">{message.content}</p>
              {message.sourceChunks && message.sourceChunks.length > 0 && (
                <div className="mt-3 flex flex-col gap-1.5 border-t border-current/10 pt-2">
                  <span className="text-xs font-medium opacity-70">Sources</span>
                  {message.sourceChunks.map((chunk) => (
                    <button
                      key={chunk.chunkId}
                      type="button"
                      onClick={() => setActiveChunk(chunk)}
                      className="text-left text-xs underline underline-offset-2 opacity-80 hover:opacity-100"
                    >
                      {chunk.documentFilename}
                      {chunk.page !== null ? ` · p.${chunk.page}` : ''} — {chunk.content.slice(0, 80)}
                      {chunk.content.length > 80 ? '…' : ''}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        {asking && <p className="text-muted-foreground text-sm">Thinking…</p>}
      </div>

      <Card>
        <CardContent className="px-4 py-3">
          <form onSubmit={handleSubmit} className="flex items-end gap-2">
            <Textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  handleSubmit(e)
                }
              }}
              placeholder="Ask a question about your documents…"
              className="min-h-10 resize-none"
              rows={1}
            />
            <Button type="submit" disabled={asking || !question.trim()}>
              <SendIcon />
            </Button>
          </form>
        </CardContent>
      </Card>

      <ChunkViewerDialog
        chunkId={activeChunk?.chunkId ?? null}
        documentFilename={activeChunk?.documentFilename ?? ''}
        onOpenChange={(open) => !open && setActiveChunk(null)}
      />
    </div>
  )
}
