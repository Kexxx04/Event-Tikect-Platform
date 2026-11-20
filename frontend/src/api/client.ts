import { clearSession, loadSession } from '../auth/session'

const API_URL =(import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

/** Body returned by the backend GlobalExceptionHandler. */
interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  details?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly details: Record<string, string>

  constructor(status: number, message: string, details: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

let unauthorizedHandler: (() => void) | null = null

/** Called when an authenticated request gets 401 (token expired or revoked). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

function buildHeaders(body: unknown): Record<string, string> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const session = loadSession()
  if (session) headers.Authorization = `Bearer ${session.token}`
  return headers
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return {}
  }
}

export async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  const headers = buildHeaders(body)
  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, `Cannot reach the API at ${API_URL}. Is the backend running?`)
  }

  if (!response.ok) {
    const errorBody = await parseErrorBody(response)
    if (response.status === 401 && headers.Authorization) {
      clearSession()
      unauthorizedHandler?.()
    }
    throw new ApiError(
      response.status,
      errorBody.message ?? `Request failed with status ${response.status}`,
      errorBody.details ?? {},
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** Returns a readable message for any error thrown while calling the API. */
export function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  return 'Unexpected error'
}
