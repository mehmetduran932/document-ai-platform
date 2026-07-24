import { api } from './client'
import type {
  BulkUploadResponse,
  DocumentChunkResponse,
  DocumentResponse,
  PageResponse,
} from './types'

export const documentsApi = {
  list: (page = 0, size = 20) =>
    api.get<PageResponse<DocumentResponse>>(`/api/documents?page=${page}&size=${size}`),

  get: (documentId: string) => api.get<DocumentResponse>(`/api/documents/${documentId}`),

  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.postForm<DocumentResponse>('/api/documents', formData)
  },

  uploadBulk: (files: File[]) => {
    const formData = new FormData()
    for (const file of files) formData.append('files', file)
    return api.postForm<BulkUploadResponse>('/api/documents/bulk', formData)
  },

  reprocess: (documentId: string) => api.post<void>(`/api/documents/${documentId}/reprocess`),

  getChunk: (chunkId: string) =>
    api.get<DocumentChunkResponse>(`/api/documents/chunks/${chunkId}`),
}
