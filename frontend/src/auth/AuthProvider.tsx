import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { clearSession, loadSession, saveSession, type Session } from './session'

const EXPIRED_NOTICE = 'Your session has expired. Please log in again.'
// Browsers fire setTimeout immediately for delays above 2^31 - 1 ms (about 24.8 days).
const MAX_TIMEOUT_MS = 2_147_483_647

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession)
  const [notice, setNotice] = useState<string | null>(null)

  const expire = useCallback(() => {
    clearSession()
    setSession(null)
    setNotice(EXPIRED_NOTICE)
  }, [])

  // The API answers 401 when the token is no longer valid.
  useEffect(() => {
    setUnauthorizedHandler(expire)
    return () => setUnauthorizedHandler(null)
  }, [expire])

  // Log out automatically when the token reaches its expiration time.
  useEffect(() => {
    if (!session) return
    const remaining = Date.parse(session.expiresAt) - Date.now()
    const timer = window.setTimeout(expire, Math.min(Math.max(remaining, 0), MAX_TIMEOUT_MS))
    return () => window.clearTimeout(timer)
  }, [session, expire])

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password)
    const newSession: Session = {
      token: response.token,
      username: response.username,
      expiresAt: response.expiresAt,
    }
    saveSession(newSession)
    setNotice(null)
    setSession(newSession)
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setNotice(null)
    setSession(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ session, notice, login, logout }),
    [session, notice, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
