// The session lives in sessionStorage: it survives page reloads but is discarded when the tab closes.
const STORAGE_KEY = 'event-platform.session'

export interface Session {
  token: string
  username: string
  /** ISO-8601 instant returned by the API */
  expiresAt: string
}

function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.token === 'string' &&
    typeof candidate.username === 'string' &&
    typeof candidate.expiresAt === 'string'
  )
}

export function isExpired(session: Session): boolean {
  const expiresAt = Date.parse(session.expiresAt)
  return Number.isNaN(expiresAt) || expiresAt <= Date.now()
}

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isSession(parsed) || isExpired(parsed)) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function saveSession(session: Session): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage can be unavailable (private mode); the session then lasts until reload.
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to clear.
  }
}
