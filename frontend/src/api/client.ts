const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

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

async function parseErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return {}
  }
}

export async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, `Cannot reach the API at ${API_URL}. Is the backend running?`)
  }

  if (!response.ok) {
    const errorBody = await parseErrorBody(response)
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
