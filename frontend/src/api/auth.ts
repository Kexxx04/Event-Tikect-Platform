import { request } from './client'

/** Mirrors com.eventplatform.api.dto.auth.LoginResponse */
export interface LoginResponse {
  token: string
  tokenType: string
  expiresAt: string
  username: string
}

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { username, password }),
}
