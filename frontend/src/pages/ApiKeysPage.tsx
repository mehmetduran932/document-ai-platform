import { useEffect, useState, type FormEvent } from 'react'
import { CopyIcon, KeyIcon, TrashIcon } from 'lucide-react'

import { apiKeysApi } from '@/api/apiKeys'
import { ErrorAlert } from '@/components/ErrorAlert'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import type { WorkspaceApiKeyResponse } from '@/api/types'

export function ApiKeysPage() {
  const [keys, setKeys] = useState<WorkspaceApiKeyResponse[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [creating, setCreating] = useState(false)
  const [justCreated, setJustCreated] = useState<WorkspaceApiKeyResponse | null>(null)

  async function loadKeys() {
    try {
      setKeys(await apiKeysApi.list())
    } catch (err) {
      setError(err)
    }
  }

  useEffect(() => {
    loadKeys()
  }, [])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return
    setCreating(true)
    setError(null)
    try {
      const created = await apiKeysApi.create(trimmed)
      setJustCreated(created)
      setName('')
      await loadKeys()
    } catch (err) {
      setError(err)
    } finally {
      setCreating(false)
    }
  }

  async function handleRevoke(keyId: string) {
    if (!confirm('Revoke this API key? Agents using it will lose access immediately.')) return
    try {
      await apiKeysApi.revoke(keyId)
      await loadKeys()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">API Keys</h1>
        <p className="text-muted-foreground text-sm">
          Used by external MCP agents (Claude Code, Codex, Gemini CLI…) to connect to your
          workspace via the <code>X-API-Key</code> header. Not used by this web app.
        </p>
      </div>

      {error ? <ErrorAlert error={error} /> : null}

      {justCreated && (
        <Alert>
          <KeyIcon />
          <AlertTitle>Copy your new key now — it won't be shown again</AlertTitle>
          <AlertDescription>
            <div className="flex w-full items-center gap-2">
              <code className="bg-muted flex-1 rounded px-2 py-1 text-xs break-all">
                {justCreated.plaintextKey}
              </code>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => navigator.clipboard.writeText(justCreated.plaintextKey ?? '')}
              >
                <CopyIcon />
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleCreate} className="flex gap-2">
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Key name (e.g. Claude Code – laptop)"
          maxLength={255}
        />
        <Button type="submit" disabled={creating || !name.trim()}>
          {creating ? 'Creating…' : 'Create key'}
        </Button>
      </form>

      <Card>
        <CardContent className="px-0">
          {keys.length === 0 && (
            <p className="text-muted-foreground px-6 py-8 text-center text-sm">
              No API keys yet.
            </p>
          )}
          {keys.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left">
                  <th className="px-6 py-2 font-medium">Name</th>
                  <th className="px-6 py-2 font-medium">Prefix</th>
                  <th className="px-6 py-2 font-medium">Created</th>
                  <th className="px-6 py-2 font-medium"></th>
                </tr>
              </thead>
              <tbody>
                {keys.map((key) => (
                  <tr key={key.id} className="border-b last:border-0">
                    <td className="px-6 py-3">{key.name}</td>
                    <td className="px-6 py-3">
                      <code className="text-xs">{key.keyPrefix}…</code>
                    </td>
                    <td className="px-6 py-3">{new Date(key.createdAt).toLocaleString()}</td>
                    <td className="px-6 py-3">
                      <div className="flex justify-end">
                        <Button variant="ghost" size="sm" onClick={() => handleRevoke(key.id)}>
                          <TrashIcon />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
