const TOKEN_KEY = 'document-ai:accessToken'
const IDENTITY_KEY = 'document-ai:identity'

export interface StoredIdentity {
  userId: string
  workspaceId: string
  email: string
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(IDENTITY_KEY)
}

export function getIdentity(): StoredIdentity | null {
  const raw = localStorage.getItem(IDENTITY_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredIdentity
  } catch {
    return null
  }
}

export function setIdentity(identity: StoredIdentity) {
  localStorage.setItem(IDENTITY_KEY, JSON.stringify(identity))
}
