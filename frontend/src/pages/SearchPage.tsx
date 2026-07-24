import { useState, type FormEvent } from 'react'
import { SearchIcon } from 'lucide-react'

import { searchApi } from '@/api/search'
import { ChunkViewerDialog } from '@/components/ChunkViewerDialog'
import { ErrorAlert } from '@/components/ErrorAlert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import type { SearchResponseWrapper, SearchResultResponse } from '@/api/types'

export function SearchPage() {
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<SearchResponseWrapper | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [searching, setSearching] = useState(false)
  const [activeChunk, setActiveChunk] = useState<SearchResultResponse | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return
    setSearching(true)
    setError(null)
    try {
      const response = await searchApi.search(trimmed)
      setResult(response)
    } catch (err) {
      setError(err)
      setResult(null)
    } finally {
      setSearching(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Search</h1>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search your documents…"
        />
        <Button type="submit" disabled={searching || !query.trim()}>
          <SearchIcon /> {searching ? 'Searching…' : 'Search'}
        </Button>
      </form>

      {error ? <ErrorAlert error={error} /> : null}

      {result && (
        <div className="flex flex-col gap-4">
          {result.extractedKeywords.length > 0 && (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-muted-foreground text-sm">Keywords:</span>
              {result.extractedKeywords.map((keyword) => (
                <Badge key={keyword} variant="secondary">
                  {keyword}
                </Badge>
              ))}
            </div>
          )}

          {result.results.length === 0 && (
            <p className="text-muted-foreground text-sm">No matching chunks found.</p>
          )}

          <div className="flex flex-col gap-3">
            {result.results.map((item) => (
              <Card key={item.chunkId}>
                <CardContent className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">
                      {item.documentFilename}
                      {item.page !== null ? ` · page ${item.page}` : ''}
                    </span>
                    <Badge variant="outline">score {item.relevanceScore.toFixed(3)}</Badge>
                  </div>
                  <p className="text-muted-foreground text-sm">{item.content}</p>
                  <div>
                    <Button variant="link" className="h-auto p-0" onClick={() => setActiveChunk(item)}>
                      View full chunk
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      <ChunkViewerDialog
        chunkId={activeChunk?.chunkId ?? null}
        documentFilename={activeChunk?.documentFilename ?? ''}
        onOpenChange={(open) => !open && setActiveChunk(null)}
      />
    </div>
  )
}
