import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

import { authApi } from '@/api/auth'
import { onUnauthorized } from '@/api/authEvents'
import {
  clearToken,
  getIdentity,
  getToken,
  setIdentity,
  setToken,
  type StoredIdentity,
} from '@/api/authStorage'
import type { LoginRequest, RegisterRequest } from '@/api/types'

interface AuthContextValue {
  identity: StoredIdentity | null
  isAuthenticated: boolean
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegisterRequest) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentityState] = useState<StoredIdentity | null>(() =>
    getToken() ? getIdentity() : null
  )

  useEffect(() => {
    return onUnauthorized(() => {
      setIdentityState(null)
    })
  }, [])

  const applyAuthResponse = (response: {
    accessToken: string
    userId: string
    workspaceId: string
    email: string
  }) => {
    setToken(response.accessToken)
    const nextIdentity: StoredIdentity = {
      userId: response.userId,
      workspaceId: response.workspaceId,
      email: response.email,
    }
    setIdentity(nextIdentity)
    setIdentityState(nextIdentity)
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      identity,
      isAuthenticated: identity !== null,
      login: async (body) => applyAuthResponse(await authApi.login(body)),
      register: async (body) => applyAuthResponse(await authApi.register(body)),
      logout: () => {
        clearToken()
        setIdentityState(null)
      },
    }),
    [identity]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
