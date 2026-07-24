import { useCallback, useEffect, useRef, useState } from 'react'
import { RefreshCwIcon, UploadIcon } from 'lucide-react'

import { documentsApi } from '@/api/documents'
import { downloadDocument } from '@/api/client'
import { ErrorAlert } from '@/components/ErrorAlert'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { BulkUploadResponse, DocumentResponse, PageResponse } from '@/api/types'

const PAGE_SIZE = 20
const POLL_INTERVAL_MS = 3000

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function DocumentsPage() {
  const [page, setPage] = useState<PageResponse<DocumentResponse> | null>(null)
  const [pageIndex, setPageIndex] = useState(0)
  const [error, setError] = useState<unknown>(null)
  const [uploading, setUploading] = useState(false)
  const [bulkResult, setBulkResult] = useState<BulkUploadResponse | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const bulkInputRef = useRef<HTMLInputElement>(null)

  const loadPage = useCallback(async (index: number) => {
    try {
      const result = await documentsApi.list(index, PAGE_SIZE)
      setPage(result)
      setError(null)
    } catch (err) {
      setError(err)
    }
  }, [])

  useEffect(() => {
    loadPage(pageIndex)
  }, [loadPage, pageIndex])

  useEffect(() => {
    if (!page) return
    const hasInFlight = page.content.some(
      (doc) => doc.processingStatus === 'PENDING' || doc.processingStatus === 'PROCESSING'
    )
    if (!hasInFlight) return
    const timer = setInterval(() => loadPage(pageIndex), POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [page, pageIndex, loadPage])

  async function handleUpload(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setError(null)
    setBulkResult(null)
    try {
      if (files.length === 1) {
        await documentsApi.upload(files[0])
      } else {
        const result = await documentsApi.uploadBulk(Array.from(files))
        setBulkResult(result)
      }
      await loadPage(0)
      setPageIndex(0)
    } catch (err) {
      setError(err)
    } finally {
      setUploading(false)
    }
  }

  async function handleReprocess(documentId: string) {
    try {
      await documentsApi.reprocess(documentId)
      await loadPage(pageIndex)
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Documents</h1>
        <div className="flex items-center gap-2">
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.tiff,.tif,.bmp,.gif,.md"
            onChange={(e) => handleUpload(e.target.files)}
          />
          <input
            ref={bulkInputRef}
            type="file"
            multiple
            className="hidden"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.tiff,.tif,.bmp,.gif,.md"
            onChange={(e) => handleUpload(e.target.files)}
          />
          <Button variant="outline" disabled={uploading} onClick={() => bulkInputRef.current?.click()}>
            <UploadIcon /> Bulk upload
          </Button>
          <Button disabled={uploading} onClick={() => fileInputRef.current?.click()}>
            <UploadIcon /> {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </div>
      </div>

      {error ? <ErrorAlert error={error} /> : null}

      {bulkResult && bulkResult.failed.length > 0 && (
        <Card className="border-destructive/50">
          <CardHeader>
            <CardTitle className="text-destructive text-sm">
              {bulkResult.failed.length} file(s) failed to upload
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="text-muted-foreground list-disc pl-4 text-sm">
              {bulkResult.failed.map((f) => (
                <li key={f.filename}>
                  {f.filename}: {f.error}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="px-0">
          {page && page.content.length === 0 && (
            <p className="text-muted-foreground px-6 py-8 text-center text-sm">
              No documents yet. Upload one to get started.
            </p>
          )}
          {page && page.content.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left">
                  <th className="px-6 py-2 font-medium">Filename</th>
                  <th className="px-6 py-2 font-medium">Size</th>
                  <th className="px-6 py-2 font-medium">Uploaded</th>
                  <th className="px-6 py-2 font-medium">Status</th>
                  <th className="px-6 py-2 font-medium"></th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((doc) => (
                  <tr key={doc.id} className="border-b last:border-0">
                    <td className="px-6 py-3">
                      <div className="font-medium">{doc.filename}</div>
                      {doc.processingStatus === 'FAILED' && doc.processingError && (
                        <div className="text-destructive mt-1 text-xs">{doc.processingError}</div>
                      )}
                    </td>
                    <td className="px-6 py-3">{formatBytes(doc.size)}</td>
                    <td className="px-6 py-3">{new Date(doc.uploadDate).toLocaleString()}</td>
                    <td className="px-6 py-3">
                      <StatusBadge status={doc.processingStatus} />
                    </td>
                    <td className="px-6 py-3">
                      <div className="flex justify-end gap-2">
                        {(doc.processingStatus === 'FAILED' ||
                          doc.processingStatus === 'PENDING') && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleReprocess(doc.id)}
                            title="Reprocess"
                          >
                            <RefreshCwIcon />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => downloadDocument(doc.id, doc.filename)}
                        >
                          Download
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

      {page && page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            disabled={pageIndex === 0}
            onClick={() => setPageIndex((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <span className="text-muted-foreground text-sm">
            Page {page.page + 1} of {page.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page.last}
            onClick={() => setPageIndex((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  )
}
