import { api } from './client'
import type { AskResponse, SearchResponseWrapper } from './types'

export const searchApi = {
  search: (query: string) =>
    api.post<SearchResponseWrapper>('/api/search', { query }),
  ask: (question: string) => api.post<AskResponse>('/api/ask', { question }),
}
