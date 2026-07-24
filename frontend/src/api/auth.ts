import { api } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest } from './types'

export const authApi = {
  register: (body: RegisterRequest) => api.post<AuthResponse>('/api/auth/register', body),
  login: (body: LoginRequest) => api.post<AuthResponse>('/api/auth/login', body),
}
