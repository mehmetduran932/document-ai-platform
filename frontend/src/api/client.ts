import { getToken, clearToken } from './authStorage'
import { emitUnauthorized } from './authEvents'
import type { ApiError } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiRequestError extends Error {
  status: number
  details: string[]
  path: string

  constructor(apiError: ApiError) {
    super(apiError.message)
    this.name = 'ApiRequestError'
    this.status = apiError.status
    this.details = apiError.details
    this.path = apiError.path
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  isFormData?: boolean
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, isFormData = false, signal } = options
  const token = getToken()
  const headers: Record<string, string> = {}

  if (!isFormData && body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: isFormData ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const isJson = response.headers.get('content-type')?.includes('application/json')
  const data = isJson ? await response.json() : await response.text()

  if (!response.ok) {
    const isAuthEndpoint = path.startsWith('/api/auth/')
    if (response.status === 401 && !isAuthEndpoint) {
      clearToken()
      emitUnauthorized()
    }
    if (isJson && data && typeof data === 'object' && 'status' in data) {
      throw new ApiRequestError(data as ApiError)
    }
    throw new ApiRequestError({
      timestamp: new Date().toISOString(),
      status: response.status,
      error: response.statusText,
      message: typeof data === 'string' && data ? data : 'Request failed',
      path,
      details: [],
    })
  }

  return data as T
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { method: 'GET', signal }),
  post: <T>(path: string, body?: unknown, signal?: AbortSignal) =>
    request<T>(path, { method: 'POST', body, signal }),
  postForm: <T>(path: string, formData: FormData, signal?: AbortSignal) =>
    request<T>(path, { method: 'POST', body: formData, isFormData: true, signal }),
  delete: <T>(path: string, signal?: AbortSignal) => request<T>(path, { method: 'DELETE', signal }),
}

export async function downloadDocument(documentId: string, filename: string) {
  const token = getToken()
  const response = await fetch(`${BASE_URL}/api/documents/${documentId}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error('Download failed')
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
