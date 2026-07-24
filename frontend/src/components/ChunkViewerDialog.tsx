import { useEffect, useState } from 'react'

import { documentsApi } from '@/api/documents'
import { ErrorAlert } from '@/components/ErrorAlert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { DocumentChunkResponse } from '@/api/types'

export function ChunkViewerDialog({
  chunkId,
  documentFilename,
  onOpenChange,
}: {
  chunkId: string | null
  documentFilename: string
  onOpenChange: (open: boolean) => void
}) {
  const [chunk, setChunk] = useState<DocumentChunkResponse | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!chunkId) {
      setChunk(null)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    documentsApi
      .getChunk(chunkId)
      .then(setChunk)
      .catch((err) => {
        if (!controller.signal.aborted) setError(err)
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [chunkId])

  return (
    <Dialog open={chunkId !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{documentFilename}</DialogTitle>
          <DialogDescription>
            {chunk ? `Chunk ${chunk.chunkIndex}${chunk.page !== null ? ` · page ${chunk.page}` : ''} · ${chunk.wordCount} words` : 'Loading full chunk…'}
          </DialogDescription>
        </DialogHeader>
        {loading && <p className="text-muted-foreground text-sm">Loading…</p>}
        {error ? <ErrorAlert error={error} /> : null}
        {chunk && <p className="text-sm whitespace-pre-wrap">{chunk.content}</p>}
      </DialogContent>
    </Dialog>
  )
}
