import { createContext, useContext } from 'react'
import type { Session } from './session'

export interface AuthContextValue {
  session: Session | null
  /** Message for the login page, e.g. after the session expired. */
  notice: string | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
