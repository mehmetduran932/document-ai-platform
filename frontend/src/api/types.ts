export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  workspaceName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  userId: string
  workspaceId: string
  email: string
}

export interface DocumentResponse {
  id: string
  filename: string
  extension: string
  size: number
  uploadDate: string
  processingStatus: ProcessingStatus
  processingError: string | null
  pageCount: number | null
}

export interface BulkUploadFailure {
  filename: string
  error: string
}

export interface BulkUploadResponse {
  uploaded: DocumentResponse[]
  failed: BulkUploadFailure[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface DocumentChunkResponse {
  id: string
  documentId: string
  page: number | null
  chunkIndex: number
  content: string
  wordCount: number
}

export interface SearchRequest {
  query: string
}

export interface SearchResultResponse {
  chunkId: string
  documentId: string
  documentFilename: string
  page: number | null
  chunkIndex: number
  content: string
  relevanceScore: number
}

export interface SearchResponseWrapper {
  query: string
  extractedKeywords: string[]
  results: SearchResultResponse[]
}

export interface AskRequest {
  question: string
}

export interface AskResponse {
  question: string
  answer: string
  sourceChunks: SearchResultResponse[]
}

export interface AskHistoryResponse {
  id: string
  question: string
  answer: string
  createdAt: string
}

export interface CreateApiKeyRequest {
  name: string
}

export interface WorkspaceApiKeyResponse {
  id: string
  name: string
  keyPrefix: string
  plaintextKey: string | null
  createdAt: string
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  details: string[]
}
