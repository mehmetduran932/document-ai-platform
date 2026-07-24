import { api } from './client'
import type {
  AskHistoryResponse,
  AskResponse,
  PageResponse,
  SearchResponseWrapper,
} from './types'

export const searchApi = {
  search: (query: string) =>
    api.post<SearchResponseWrapper>('/api/search', { query }),
  ask: (question: string) => api.post<AskResponse>('/api/ask', { question }),
  askHistory: (page = 0, size = 50) =>
    api.get<PageResponse<AskHistoryResponse>>(`/api/ask/history?page=${page}&size=${size}`),
  clearAskHistory: () => api.delete<void>('/api/ask/history'),
}
