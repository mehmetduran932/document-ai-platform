import { api } from './client'
import type { WorkspaceApiKeyResponse } from './types'

export const apiKeysApi = {
  list: () => api.get<WorkspaceApiKeyResponse[]>('/api/workspace/api-keys'),
  create: (name: string) =>
    api.post<WorkspaceApiKeyResponse>('/api/workspace/api-keys', { name }),
  revoke: (keyId: string) => api.delete<void>(`/api/workspace/api-keys/${keyId}`),
}
